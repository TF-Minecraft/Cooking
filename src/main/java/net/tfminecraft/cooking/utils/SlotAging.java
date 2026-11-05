package net.tfminecraft.cooking.utils;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

/**
 * Ages the food lying in a furniture slot (a meat hook, a baking tray, a trough) on its station's one-second timer,
 * the way the inventory scan ages unheld food (FoodScan). The timers used to rebuild and rewrite every such food each
 * second only to store its new clock and aged numbers; the food ages the same from its stored clock, values and
 * remainder, so the slot is now only written when the food would look different.
 *
 * <p>Like TLibs' scan, it keeps a copy of each slot's food that it left as it was, and when that food can next look
 * different. While the slot holds an equal item and that time hasn't come, the timer skips the slot. Any change to the
 * item (taken, replaced, carved, glazed, changed in place) ends the skip, as does {@link #forgetAll()}.
 */
public final class SlotAging {

    /** A slot's food as the timer last left it, when it can next look different, and the generation it rests in. */
    private record Resting(ItemStack item, long until, int generation) {}

    // By slot identity; a slot dropped by its furniture drops out on its own. Only the server thread touches it.
    private static final Map<PlacedSlot, Resting> RESTING = new WeakHashMap<>();

    // forgetAll can come from an ItemsAdder event on any thread, so it only moves this on and rests from before end.
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private SlotAging() {}

    /** True while the slot still holds the item the timer last left there and that item can't look different yet. */
    public static boolean isResting(PlacedSlot slot, ItemStack stack) {
        Resting resting = RESTING.get(slot);
        if (resting == null) {
            return false;
        }
        if (resting.generation() == GENERATION.get() && resting.until() > StackNormalizer.quantizedNow()
                && resting.item().equals(stack)) {
            return true;
        }
        RESTING.remove(slot);
        return false;
    }

    /**
     * Ages the food (parsed from stack) in a slot of the furniture whose id is furniture, and returns what to put in
     * the slot, or null to leave it. A food left as it was rests until it can look different; a written one is looked
     * at again on the next pass, as the scan does.
     */
    public static ItemStack age(PlacedSlot slot, ItemStack stack, FoodItem food, String furniture) {
        // Read first: a rest must not outlive a forgetAll that came while this food was being built.
        int generation = GENERATION.get();
        ItemMeta meta = stack.getItemMeta();
        FoodScan.Result result = FoodScan.update(stack, meta, food, false, furniture);
        if (result.item() == null && result.recheckAt() != ItemScanHandler.EVERY_SCAN) {
            RESTING.put(slot, new Resting(stack.clone(), result.recheckAt(), generation));
        } else {
            RESTING.remove(slot);
        }
        return result.item();
    }

    /** Ends every skip, so each slot's food is looked at again: its config or item templates may have changed. */
    public static void forgetAll() {
        GENERATION.incrementAndGet();
    }
}
