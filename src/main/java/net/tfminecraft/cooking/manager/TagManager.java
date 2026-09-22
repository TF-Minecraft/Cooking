package net.tfminecraft.cooking.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.tfmccore.itemscan.ItemScanHandler;

public class TagManager implements ItemScanHandler {

    private static final int OFF_HAND_SLOT = 40;

    @Override
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(Keys.FOOD_ID, PersistentDataType.STRING);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @Override
    public void update(Player player, Inventory inventory, int slot, ItemStack stack) {
        FoodItem food = FoodItem.fromItem(stack);
        if (food == null) {
            return;
        }
        if (!food.shouldUpdate()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && !ItemUpdater.needsLoreRebuild(
                    meta.getLore(),
                    meta.getPersistentDataContainer().has(Keys.LORE_INDEX_MAP, PersistentDataType.STRING))) {
                return;
            }
        }
        boolean held = isHeldSlot(player, inventory, slot);
        ItemStack updated = ItemUpdater.updateItem(stack, food, null, held);
        if (updated != null && inventory != null && slot >= 0) {
            inventory.setItem(slot, updated);
        }
    }

    static boolean isHeldSlot(Player player, Inventory inventory, int slot) {
        if (player == null || !(inventory instanceof PlayerInventory playerInventory)) {
            return false;
        }
        if (playerInventory != player.getInventory()) {
            return false;
        }
        return slot == playerInventory.getHeldItemSlot() || slot == OFF_HAND_SLOT;
    }
}
