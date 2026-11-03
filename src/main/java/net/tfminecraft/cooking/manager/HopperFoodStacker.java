package net.tfminecraft.cooking.manager;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.Keys;

/**
 * Aging foods carry their own clock, so hoppers, which only stack exact copies, give every piece
 * of the same food its own slot: eggs laid an hour apart fill a hopper five slots at a time. When a
 * hopper moves food into a storage block, or picks it up off the ground, and the slot vanilla would
 * fill first holds an equal food that only aged differently, the fresher side takes the older one's
 * data so vanilla stacks them there. Food never gets fresher, and vanilla still does the moving.
 *
 * <p>Other plugins may cancel these events, so nothing outside the event changes before they are
 * done. A fresher moved item takes the stored data through the event at HIGHEST, which a cancelled
 * move throws away. Changes to the stored stack or to a ground item wait for MONITOR, which only
 * runs for an event nothing cancelled; vanilla adds the item straight after it.
 */
public class HopperFoodStacker implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (!hopperIntoStorage(event)) return;
        Merge merge = merge(event.getDestination(), event.getItem());
        if (merge != null && !merge.incomingIsOlder()) event.setItem(merge.carried());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterMove(InventoryMoveItemEvent event) {
        if (!hopperIntoStorage(event)) return;
        // A fresher item already carries the stored data, so only an older one still merges here.
        Merge merge = merge(event.getDestination(), event.getItem());
        if (merge != null && merge.incomingIsOlder()) merge.ageStoredStack();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        Inventory destination = event.getInventory();
        if (!storage(destination)) return;
        // The hopper copies the ground item after this event, and all of it fits in the slot.
        Item entity = event.getItem();
        Merge merge = merge(destination, entity.getItemStack());
        if (merge == null) return;
        if (merge.incomingIsOlder()) {
            merge.ageStoredStack();
        } else {
            entity.setItemStack(merge.carried());
        }
    }

    /**
     * Hoppers take the item out of the source before the event and add whatever it carries after,
     * so a changed copy moves cleanly. A dropper keeps the source item when it changes.
     */
    private static boolean hopperIntoStorage(InventoryMoveItemEvent event) {
        return event.getInitiator().getType() == InventoryType.HOPPER && storage(event.getDestination());
    }

    /** Containers that take an item in any slot from any side, in slot order. */
    private static boolean storage(Inventory inventory) {
        return switch (inventory.getType()) {
            case CHEST, BARREL, HOPPER, SHULKER_BOX, DISPENSER, DROPPER -> true;
            default -> false;
        };
    }

    /**
     * The incoming food meets an equal food that aged differently in the first slot vanilla would
     * fill, with room for all of it.
     */
    record Merge(Inventory destination, int slot, ItemStack stored, ItemStack incoming, boolean incomingIsOlder) {

        /** The incoming food carrying the stored stack's data, so vanilla stacks it there. */
        ItemStack carried() {
            ItemStack out = stored.clone();
            out.setAmount(incoming.getAmount());
            return out;
        }

        /** The stored stack carrying the older incoming food's data, so vanilla stacks it there. */
        void ageStoredStack() {
            ItemStack older = incoming.clone();
            older.setAmount(stored.getAmount());
            destination.setItem(slot, older);
        }
    }

    /**
     * Where the incoming food can stack after all, or null when vanilla handles it as it is. Vanilla
     * fills the first slot that is empty or holds an exact copy with room; a merge is offered when
     * that slot instead holds an equal food that aged differently and has room for all of it, and no
     * later slot holds an exact copy with room.
     */
    static Merge merge(Inventory destination, ItemStack incoming) {
        // Most hopper traffic is not food; read the food id without copying the item's meta.
        if (incoming == null || incoming.isEmpty() || !incoming.hasItemMeta()
                || !incoming.getPersistentDataContainer().has(Keys.FOOD_ID, PersistentDataType.STRING)) {
            return null;
        }
        FoodItem food = null;
        for (int slot = 0; slot < destination.getSize(); slot++) {
            ItemStack current = destination.getItem(slot);
            if (current == null || current.isEmpty()) return null;
            if (current.getType() != incoming.getType()) continue;
            int room = room(destination, current);
            if (room <= 0) continue;
            if (current.isSimilar(incoming)) return null;

            if (food == null) {
                food = FoodItem.fromItem(incoming);
                if (food == null) return null;
            }
            FoodItem other = FoodItem.fromItem(current);
            if (other == null || !InventoryAdder.equalsFood(food, other)
                    || !ConversionManager.sameApartFromAging(incoming, current)) continue;
            // Part of it would spill into later slots with the stored data; leave that to vanilla.
            if (incoming.getAmount() > room || laterExactCopy(destination, slot, incoming)) return null;
            // Equal age keeps the stored stack's data.
            boolean older = BundleFoodStacker.moreAged(current, incoming) != current;
            return new Merge(destination, slot, current.clone(), incoming.clone(), older);
        }
        return null;
    }

    private static int room(Inventory inventory, ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()) - stack.getAmount();
    }

    /** True when a slot after this one already stacks the item exactly; vanilla puts it there. */
    private static boolean laterExactCopy(Inventory destination, int after, ItemStack incoming) {
        for (int slot = after + 1; slot < destination.getSize(); slot++) {
            ItemStack later = destination.getItem(slot);
            if (later != null && later.isSimilar(incoming) && room(destination, later) > 0) return true;
        }
        return false;
    }
}
