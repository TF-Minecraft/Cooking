package net.tfminecraft.cooking.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.tfminecraft.cooking.item.FoodItem;

/**
 * What a food leaves behind once a station uses it up: its types.yml {@code returns} item, such as the empty glass
 * bottle of honey stirred into dough or a sauce.
 */
public final class FoodReturns {

    private FoodReturns() {
    }

    /** A fresh copy of the item this food gives back, or null when it gives nothing (or the path is unknown). */
    public static ItemStack returnedBy(FoodItem food) {
        String path = food == null ? null : food.getReturns();
        if (path == null || path.isBlank()) return null;
        ItemStack back = ItemRef.resolve(path.trim());
        if (back == null || back.getType().isAir()) return null;
        back.setAmount(1);
        return back;
    }

    /** Uses one of the held food; its returns item, if it has one, goes back to the player. */
    public static void useOne(Player p, FoodItem food) {
        ItemStack back = returnedBy(food);
        if (back != null) {
            HeldStack.useOneKeepStack(p, back);
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack held = inv.getItemInMainHand();
        held.setAmount(held.getAmount() - 1);
        inv.setItemInMainHand(held.getAmount() > 0 ? held : null);
    }

    /**
     * For a held food a station has already taken one of: its returns item goes into the hand if that is now empty,
     * else into the inventory (dropped when full).
     */
    public static void giveBack(Player p, FoodItem food) {
        ItemStack back = returnedBy(food);
        if (back == null) return;
        PlayerInventory inv = p.getInventory();
        ItemStack held = inv.getItemInMainHand();
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) {
            inv.setItemInMainHand(back);
            return;
        }
        ItemStack leftover = InventoryAdder.addItem(p, back);
        if (leftover != null) p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }
}
