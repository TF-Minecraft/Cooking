package net.tfminecraft.cooking.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.FoodScan;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

public class TagManager implements ItemScanHandler {

    private static final int OFF_HAND_SLOT = 40;

    // Runs on every new or changed slot: read the key through the stack's data view instead of copying its whole meta.
    @Override
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        return stack.getPersistentDataContainer().has(Keys.FOOD_ID, PersistentDataType.STRING);
    }

    @Override
    public void update(Player player, Inventory inventory, int slot, ItemStack stack) {
        scan(player, inventory, slot, stack);
    }

    /**
     * Ages a food and rewrites its slot only when it would look different (FoodScan), reading its meta once. Answers
     * when TLibs must show it the unchanged item again: when it can next look different, or every scan while it is
     * held, since held food waits for a visible change.
     */
    @Override
    public long scan(Player player, Inventory inventory, int slot, ItemStack stack) {
        if (!matches(stack)) {
            return WHEN_CHANGED;
        }
        ItemMeta meta = stack.getItemMeta();
        FoodItem food = FoodItem.fromItem(stack, meta);
        if (food == null) {
            // An unknown food id: only a reload can change that, and Cooking's reload rescans every slot.
            return WHEN_CHANGED;
        }
        FoodScan.Result result = FoodScan.update(stack, meta, food, isHeldSlot(player, inventory, slot));
        ItemStack updated = result.item();
        if (updated != null && !updated.getType().isAir() && inventory != null && slot >= 0) {
            inventory.setItem(slot, updated);
        }
        return result.recheckAt();
    }

    public static boolean isHeldSlot(Player player, Inventory inventory, int slot) {
        if (player == null || !(inventory instanceof PlayerInventory playerInventory)) {
            return false;
        }
        if (playerInventory != player.getInventory()) {
            return false;
        }
        return slot == playerInventory.getHeldItemSlot() || slot == OFF_HAND_SLOT;
    }
}
