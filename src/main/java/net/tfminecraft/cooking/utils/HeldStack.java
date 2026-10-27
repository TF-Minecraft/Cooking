package net.tfminecraft.cooking.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Uses one item of the held stack, so a stack of ladles or filled ladles never loses the rest. */
public final class HeldStack {

    private HeldStack() {
    }

    /** The result goes to the hand and the rest of the held stack moves to the inventory (scooped soup is plated next). */
    public static void useOneIntoHand(Player p, ItemStack result) {
        PlayerInventory inv = p.getInventory();
        ItemStack rest = rest(inv.getItemInMainHand());
        inv.setItemInMainHand(result);
        if (rest != null) give(p, rest);
    }

    /** The rest of the held stack stays in hand and the result goes to the inventory (a plated ladle's empty ladle). */
    public static void useOneKeepStack(Player p, ItemStack result) {
        PlayerInventory inv = p.getInventory();
        ItemStack rest = rest(inv.getItemInMainHand());
        if (rest == null) {
            inv.setItemInMainHand(result);
            return;
        }
        inv.setItemInMainHand(rest);
        give(p, result);
    }

    /** A copy of the held item minus the one used, or null when that was the last one. */
    private static ItemStack rest(ItemStack held) {
        if (held == null || held.getAmount() <= 1) return null;
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        return rest;
    }

    private static void give(Player p, ItemStack item) {
        ItemStack leftover = InventoryAdder.addItem(p, item);
        if (leftover != null) p.getWorld().dropItemNaturally(p.getLocation(), leftover);
    }
}
