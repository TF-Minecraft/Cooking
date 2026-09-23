package net.tfminecraft.cooking.sausagemaker;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.util.LegacyModelData;

/**
 * Sausage casing is plain vanilla paper. Cooking tools and scooped soup use
 * paper as their base item, and those must stay in the player's hand.
 */
final class CasingPaper {

    private CasingPaper() {}

    static boolean isCasing(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || stack.getAmount() <= 0) {
            return false;
        }
        if (hasCustomIdentity(stack)) {
            return false;
        }
        return FoodItem.fromItem(stack) == null;
    }

    private static boolean hasCustomIdentity(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName() || meta.hasItemModel() || LegacyModelData.has(meta)) {
            return true;
        }
        return !meta.getPersistentDataContainer().isEmpty();
    }
}
