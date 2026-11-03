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
 * fill first holds an equal food that only aged differently, both take the more aged copy's data so
 * vanilla stacks them there. Food never gets fresher this way, and vanilla still picks the slot.
 */
public class HopperFoodStacker implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        // Hoppers take the item out of the source before this event and add whatever it carries
        // after, so a changed copy moves cleanly. A dropper keeps the source item when it changes.
        if (event.getInitiator().getType() != InventoryType.HOPPER) return;
        Inventory destination = event.getDestination();
        if (!storage(destination)) return;
        ItemStack stackable = stackable(destination, event.getItem());
        if (stackable != null) event.setItem(stackable);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        Inventory destination = event.getInventory();
        if (!storage(destination)) return;
        // The hopper copies the ground item after this event, so it takes the changed data.
        Item entity = event.getItem();
        ItemStack stackable = stackable(destination, entity.getItemStack());
        if (stackable != null) entity.setItemStack(stackable);
    }

    /** Containers that take an item in any slot from any side, in slot order. */
    private static boolean storage(Inventory inventory) {
        return switch (inventory.getType()) {
            case CHEST, BARREL, HOPPER, SHULKER_BOX, DISPENSER, DROPPER -> true;
            default -> false;
        };
    }

    /**
     * The moved food as it should enter destination to stack, or null when it can stay as it is.
     * Vanilla fills the first slot that is empty or holds an exact copy with room. When the first
     * slot it could fill instead holds an equal food that aged differently, the fresher side takes
     * the older one's data: the slot is rewritten in place, or a copy of it is returned to move.
     * If another plugin cancels the move after this, a rewritten slot has only aged to the moved
     * food's age within the same freshness step; nothing is gained or lost.
     */
    static ItemStack stackable(Inventory destination, ItemStack moved) {
        // Most hopper traffic is not food; read the food id without copying the item's meta.
        if (moved == null || moved.isEmpty() || !moved.hasItemMeta()
                || !moved.getPersistentDataContainer().has(Keys.FOOD_ID, PersistentDataType.STRING)) {
            return null;
        }
        FoodItem food = null;
        for (int slot = 0; slot < destination.getSize(); slot++) {
            ItemStack current = destination.getItem(slot);
            if (current == null || current.isEmpty()) return null;
            if (current.getType() != moved.getType()) continue;
            int limit = Math.min(current.getMaxStackSize(), destination.getMaxStackSize());
            if (current.getAmount() >= limit) continue;
            if (current.isSimilar(moved)) return null;

            if (food == null) {
                food = FoodItem.fromItem(moved);
                if (food == null) return null;
            }
            FoodItem other = FoodItem.fromItem(current);
            if (other == null || !InventoryAdder.equalsFood(food, other)
                    || !ConversionManager.sameApartFromAging(moved, current)) continue;

            // Equal age keeps the stored stack, so only the moving item changes.
            if (BundleFoodStacker.moreAged(current, moved) == current) {
                ItemStack out = current.clone();
                out.setAmount(moved.getAmount());
                return out;
            }
            ItemStack older = moved.clone();
            older.setAmount(current.getAmount());
            destination.setItem(slot, older);
            return null;
        }
        return null;
    }
}
