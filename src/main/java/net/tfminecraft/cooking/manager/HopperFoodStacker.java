package net.tfminecraft.cooking.manager;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
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

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.Keys;

/**
 * Aging foods carry their own clock, so hoppers, which only stack exact copies, give every piece
 * of the same food its own slot: eggs laid an hour apart fill a hopper five slots at a time. When a
 * hopper moves food into a storage block, or picks it up off the ground, and the slot vanilla would
 * fill first holds an equal food that only aged differently, the incoming food takes the stored
 * stack's data so vanilla stacks it there. Vanilla still picks the slot.
 *
 * <p>Other plugins may cancel the event after this listener, so it changes only what a cancelled
 * event leaves untouched: the moved item, which a cancelled move discards, or the ground item, which
 * is put back on the next tick if it is still there. When the incoming food was the older one, the
 * stack takes its data on the next tick, once the slot has really grown, so food never gets fresher.
 */
public class HopperFoodStacker implements Listener {

    private final Consumer<Runnable> nextTick;

    public HopperFoodStacker() {
        this(task -> Bukkit.getScheduler().runTask(Cooking.plugin, task));
    }

    HopperFoodStacker(Consumer<Runnable> nextTick) {
        this.nextTick = nextTick;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        // Hoppers take the item out of the source before this event and add whatever it carries
        // after, so a changed copy moves cleanly. A dropper keeps the source item when it changes.
        if (event.getInitiator().getType() != InventoryType.HOPPER) return;
        Inventory destination = event.getDestination();
        if (!storage(destination)) return;
        Merge merge = merge(destination, event.getItem());
        if (merge == null) return;
        event.setItem(merge.carried());
        if (merge.incomingIsOlder()) nextTick.accept(merge::ageStack);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        Inventory destination = event.getInventory();
        if (!storage(destination)) return;
        // The hopper copies the ground item after this event, so it takes the changed data.
        Item entity = event.getItem();
        Merge merge = merge(destination, entity.getItemStack());
        if (merge == null) return;
        entity.setItemStack(merge.carried());
        nextTick.accept(() -> {
            // A merge only happens when the whole item fits, so an item still on the ground was
            // not picked up: give it its own data back.
            if (entity.isValid()) {
                if (entity.getItemStack().isSimilar(merge.stored())) entity.setItemStack(merge.incoming());
            } else if (merge.incomingIsOlder()) {
                merge.ageStack();
            }
        });
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

        /** Once the incoming food has joined the stack, give the stack its older data. */
        void ageStack() {
            ItemStack current = destination.getItem(slot);
            if (current == null || !current.isSimilar(stored)
                    || current.getAmount() < stored.getAmount() + incoming.getAmount()) return;
            ItemStack older = incoming.clone();
            older.setAmount(current.getAmount());
            destination.setItem(slot, older);
        }
    }

    /**
     * Where the incoming food can stack after all, or null when vanilla handles it as it is. Vanilla
     * fills the first slot that is empty or holds an exact copy with room; a merge is offered when
     * that slot instead holds an equal food that aged differently and has room for all of it.
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
            int room = Math.min(current.getMaxStackSize(), destination.getMaxStackSize()) - current.getAmount();
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
            if (incoming.getAmount() > room) return null;
            // Equal age keeps the stored stack's data.
            boolean older = BundleFoodStacker.moreAged(current, incoming) != current;
            return new Merge(destination, slot, current.clone(), incoming.clone(), older);
        }
        return null;
    }
}
