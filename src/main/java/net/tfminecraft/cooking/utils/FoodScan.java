package net.tfminecraft.cooking.utils;

import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

/**
 * The inventory scan's update of a food (TagManager). A food ages by the wall clock from its stored clock, value and
 * remainder, so writing the aged numbers back changes nothing a player sees and the food ages the same either way.
 * The scan therefore only rewrites a food that would look different (a new tag step, warmth gone, lore or model built
 * differently, as after a reload or an update), whose clock must be set, or that has a sauce (below). It also says
 * when the unchanged food could next look different, so TLibs can skip the slot until then. SlotAging does the same
 * for food lying in furniture.
 */
public final class FoodScan {

    // Further than this the next step never comes: a food aging at a huge multiplier, or a track with no next step.
    private static final double MAX_SECONDS = 1e12;

    private FoodScan() {}

    /**
     * What the scan puts in the slot (null leaves it as it is) and the {@link ItemScanHandler#scan} answer: when the
     * unchanged food needs the scan again.
     */
    public record Result(ItemStack item, long recheckAt) {}

    /**
     * Ages the food (parsed from stack, whose meta is meta) to now. A held food is only rewritten when today's held
     * rule says so (a new step, warmth gone or broken lore), so eating it isn't interrupted.
     */
    public static Result update(ItemStack stack, ItemMeta meta, FoodItem fi, boolean held) {
        return update(stack, meta, fi, held, null);
    }

    /**
     * As {@link #update(ItemStack, ItemMeta, FoodItem, boolean)} for food in a slot of the furniture whose id is
     * furniture (null in an inventory), which picks the model the food is built with there.
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static Result update(ItemStack stack, ItemMeta meta, FoodItem fi, boolean held, String furniture) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean broken = ItemUpdater.needsLoreRebuild(meta.getLore(), pdc.has(Keys.LORE_INDEX_MAP, PersistentDataType.STRING));
        if (!fi.shouldUpdate()) {
            return new Result(broken ? ItemUpdater.applyItemUpdate(stack, fi, furniture) : null, ItemScanHandler.WHEN_CHANGED);
        }
        if (fi.hasSauce()) {
            // A sauce keeps no clock or remainder of its own, so it only ages by whole steps each time the food is
            // rewritten. Rewrite it on every scan as before, so sauces age exactly as they always have.
            return new Result(ItemUpdater.updateItem(stack, fi, furniture, held), ItemScanHandler.EVERY_SCAN);
        }
        long now = StackNormalizer.quantizedNow();
        Long stored = pdc.get(Keys.LAST_UPDATE, PersistentDataType.LONG);
        long deltaSeconds = stored == null ? 0 : (now - stored) / 1000;
        boolean expired = WarmthUtils.expireIfRoomTemp(fi);
        boolean changed = deltaSeconds > 0 && ItemUpdater.applyAging(fi, deltaSeconds);
        fi.setLastUpdate(now);
        long recheckAt = held ? ItemScanHandler.EVERY_SCAN : nextChange(fi, now);
        if (changed || expired || broken || (!held && clockNeedsWriting(stored, now))) {
            return new Result(ItemUpdater.applyItemUpdate(stack, fi, furniture), recheckAt);
        }
        if (held) {
            return new Result(null, recheckAt);
        }
        ItemStack rebuilt = ItemUpdater.applyItemUpdate(stack.clone(), fi, furniture);
        return new Result(looksTheSame(stack, meta, rebuilt) ? null : rebuilt, recheckAt);
    }

    /**
     * True when the clock must be written: missing, ahead of now, or not on a whole second (as a newly built food's
     * is). The scan used to set it to the second on every pass; once it is, leaving it gives the same aging.
     */
    static boolean clockNeedsWriting(Long stored, long now) {
        return stored == null || stored > now || Math.floorMod(stored, 1000L) != 0;
    }

    /**
     * The time from which this food, aged to now and left unchanged, can show its next tag step or lose its warmth.
     * It is a second early, so rounding never makes the scan late; a check that comes early just finds nothing.
     */
    static long nextChange(FoodItem fi, long now) {
        double seconds = Double.POSITIVE_INFINITY;
        for (TagTrack track : fi.getTagTracks()) {
            if (!track.isAgeable()) continue;
            long target = nextStepValue(track);
            if (target == Long.MAX_VALUE) continue;
            double units = target - track.getValue() - fi.getAgeRemainder(track.getId());
            seconds = Math.min(seconds, units * fi.resolveAgeMultiplier(track.getId()));
        }
        if (!(seconds <= MAX_SECONDS)) {
            return ItemScanHandler.WHEN_CHANGED;
        }
        long whole = Math.max(0L, (long) Math.ceil(seconds) - 1L);
        return now + whole * 1000L;
    }

    /**
     * The track value at which its shown step next changes, or Long.MAX_VALUE if it never does: the first step it
     * hasn't reached (TagTrack.getCurrentStep), and room temperature for warmth. Other tracks stop aging once they
     * reach their last step (TagTrack.setValue).
     */
    static long nextStepValue(TagTrack track) {
        boolean warmth = "warmth".equals(track.getId());
        long next = warmth ? WarmthUtils.ROOM_TEMP_AGE : Long.MAX_VALUE;
        List<TagStep> steps = track.getSteps();
        int value = track.getValue();
        if (steps.isEmpty() || (!warmth && value >= steps.get(steps.size() - 1).getRequiredValue())) {
            return next;
        }
        for (TagStep step : steps) {
            if (value < step.getRequiredValue()) {
                return Math.min(next, step.getRequiredValue());
            }
        }
        return next;
    }

    /** True when rebuilt differs from the current stack only in its aging clock, values and remainder. */
    static boolean looksTheSame(ItemStack current, ItemMeta currentMeta, ItemStack rebuilt) {
        ItemStack probe = rebuilt.clone();
        ItemMeta probeMeta = probe.getItemMeta();
        if (probeMeta == null) {
            return false;
        }
        PersistentDataContainer from = currentMeta.getPersistentDataContainer();
        PersistentDataContainer to = probeMeta.getPersistentDataContainer();
        copy(from, to, Keys.LAST_UPDATE, PersistentDataType.LONG);
        copy(from, to, Keys.AGE_REMAINDER, PersistentDataType.STRING);
        copy(from, to, Keys.TAGS, PersistentDataType.STRING);
        probe.setItemMeta(probeMeta);
        return current.isSimilar(probe);
    }

    private static <T> void copy(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key,
            PersistentDataType<?, T> type) {
        T value = from.get(key, type);
        if (value == null) {
            to.remove(key);
        } else {
            to.set(key, type, value);
        }
    }
}
