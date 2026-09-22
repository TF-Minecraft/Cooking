package net.tfminecraft.cooking.utils;

import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemUpdater {

    /** Ages the FoodItem. Returns true if any TagStep changed (including nested sauce). */
    public static boolean applyAging(FoodItem fi, long deltaSeconds) {
        if (fi == null || deltaSeconds <= 0) return false;

        boolean changed = false;

        for (TagTrack track : fi.getTagTracks()) {
            if (!track.isAgeable()) continue;

            TagStep before = track.getCurrentStep();
            int oldValue = track.getValue();
            AgeScale.Scaled scaled = AgeScale.apply(
                    oldValue,
                    deltaSeconds,
                    fi.resolveAgeMultiplier(track.getId()),
                    fi.getAgeRemainder(track.getId()));
            int newAge = scaled.value();
            fi.setAgeRemainder(track.getId(), scaled.leftover());

            if ("warmth".equals(track.getId())) {
                track.forceSetValue(newAge);
                if (WarmthUtils.expireIfRoomTemp(fi)) {
                    changed = true;
                    continue;
                }
            } else {
                track.setValue(newAge);
            }
            TagStep after = track.getCurrentStep();

            if (before != null && after != null && !before.getTag().equals(after.getTag())) {
                changed = true;
            }
        }

        if (fi.hasSauce()) {
            FoodItem sauce = fi.getSauce();
            boolean sauceChanged = applyAging(sauce, deltaSeconds);

            if (sauceChanged) changed = true;
        }

        return changed;
    }

    public static ItemStack applyItemUpdate(ItemStack stack, FoodItem fi, String furniture) {
        if (stack == null || fi == null) {
            return null;
        }
        int amount = stack.getAmount();
        String displayName = existingDisplayName(stack);
        if (displayName == null) {
            displayName = fi.getName();
        }

        ItemStack updated = stack;
        ModelData newModel = fi.getModel() != null ? fi.getModelData() : null;
        if (newModel != null) {
            updated = newModel.apply(furniture, stack);
            if (updated == null) {
                updated = stack;
            }
        }
        updated.setAmount(amount);
        ItemBuilder.stamp(updated, fi, displayName);
        CarvableRoastUtils.writeCarveState(updated, fi);
        return updated;
    }

    public static ItemStack updateItem(ItemStack stack, FoodItem fi, String furniture) {
        return updateItem(stack, fi, furniture, false);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static ItemStack updateItem(ItemStack stack, FoodItem fi, String furniture, boolean held) {
        if (stack == null || fi == null) {
            return null;
        }

        long now = StackNormalizer.quantizedNow();

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        var pdc = meta.getPersistentDataContainer();
        boolean broken = needsLoreRebuild(meta.getLore(), pdc.has(Keys.LORE_INDEX_MAP, PersistentDataType.STRING));

        if (!fi.shouldUpdate()) {
            if (!broken) {
                return null;
            }
            if (!shouldWriteToSlot(held, true, false)) {
                return null;
            }
            return applyItemUpdate(stack, fi, furniture);
        }

        Long lastUpdate = pdc.get(Keys.LAST_UPDATE, PersistentDataType.LONG);
        if (lastUpdate == null) {
            lastUpdate = now;
        }

        long deltaSeconds = (now - lastUpdate) / 1000;
        boolean expired = WarmthUtils.expireIfRoomTemp(fi);
        if (fi.hasSauce()) {
            expired |= WarmthUtils.expireIfRoomTemp(fi.getSauce());
        }

        boolean changed = false;
        if (deltaSeconds > 0) {
            changed = applyAging(fi, deltaSeconds);
        }
        boolean visual = changed || expired || broken;
        boolean normalizeNeeded = StackNormalizer.needsNormalize(fi);
        boolean clockOff = fi.getLastUpdate() != now;
        boolean silent = normalizeNeeded || clockOff;
        if (!shouldWriteToSlot(held, visual, silent)) {
            return null;
        }
        StackNormalizer.normalize(fi);
        fi.setLastUpdate(now);
        return applyItemUpdate(stack, fi, furniture);
    }

    static boolean shouldWriteToSlot(boolean held, boolean visual, boolean silent) {
        if (visual) {
            return true;
        }
        if (held) {
            return false;
        }
        return silent;
    }

    public static boolean needsLoreRebuild(List<String> lore, boolean hasLoreIndex) {
        if (lore == null || lore.isEmpty()) {
            return true;
        }
        if (!hasLoreIndex) {
            return true;
        }
        boolean nutrition = false;
        boolean food = false;
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String stripped = line.replaceAll("§.", "");
            if (stripped.contains("Nutrition")) {
                nutrition = true;
            }
            if (stripped.contains("Food") && !stripped.contains("% Food") && !stripped.contains("+")) {
                food = true;
            }
        }
        return !nutrition || !food;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static String existingDisplayName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        return meta.getDisplayName();
    }
}
