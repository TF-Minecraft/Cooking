package net.tfminecraft.cooking.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

public final class LegacyFishScan implements ItemScanHandler {

    @Override
    public boolean matches(ItemStack stack) {
        return stack != null
                && stack.getType() == Material.COD
                && FoodItem.fromItem(stack) == null;
    }

    @Override
    public void update(Player player, Inventory inventory, int slot, ItemStack stack) {
        if (inventory == null || slot < 0) {
            return;
        }
        ItemStack built = LegacyFishConversion.convert(player, stack);
        if (built != null) {
            inventory.setItem(slot, built);
        }
    }
}
