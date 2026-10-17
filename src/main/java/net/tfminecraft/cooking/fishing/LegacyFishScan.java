package net.tfminecraft.cooking.fishing;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.manager.TagManager;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

/** Converts leftover CustomFishing fish, and clears CustomFishing data that older versions left on whole fish. */
public final class LegacyFishScan implements ItemScanHandler {

    @Override
    public boolean matches(ItemStack stack) {
        if (stack == null || !Tag.ITEMS_FISHES.isTagged(stack.getType())) {
            return false;
        }
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        return isFood(data) ? SeafoodWholeItems.carriesCatchData(data) : stack.getType() == Material.COD;
    }

    @Override
    public void update(Player player, Inventory inventory, int slot, ItemStack stack) {
        if (inventory == null || slot < 0) {
            return;
        }
        ItemStack built;
        if (!isFood(stack.getItemMeta().getPersistentDataContainer())) {
            built = LegacyFishConversion.convert(player, stack);
        } else if (TagManager.isHeldSlot(player, inventory, slot)) {
            // Like TagManager, leave a held fish alone so eating or using it isn't interrupted.
            return;
        } else {
            // Earlier handlers in this scan (TagManager) may have rewritten the slot, so clean what is there now.
            built = SeafoodWholeItems.withoutCatchData(inventory.getItem(slot));
        }
        if (built != null) {
            inventory.setItem(slot, built);
        }
    }

    private static boolean isFood(PersistentDataContainer data) {
        return data.has(Keys.FOOD_ID, PersistentDataType.STRING);
    }
}
