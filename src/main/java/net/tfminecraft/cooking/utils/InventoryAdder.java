package net.tfminecraft.cooking.utils;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.manager.ConversionManager;

public class InventoryAdder {
    public static ItemStack addItem(Player p, ItemStack in) {
        FoodItem fIn = FoodItem.fromItem(in);
        PlayerInventory inv = p.getInventory();
        int amount = in.getAmount();

        if (fIn == null) {
            HashMap<Integer, ItemStack> left = inv.addItem(in);
            if (left.isEmpty()) return null;
            return left.values().iterator().next();
        }

        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack cur = inv.getItem(slot);
            int space = room(inv, fIn, in, cur);
            if (space <= 0) continue;

            int add = Math.min(space, amount);
            cur.setAmount(cur.getAmount() + add);
            amount -= add;

            if (amount <= 0) return null;
        }

        ItemStack rest = in.clone();
        rest.setAmount(amount);

        HashMap<Integer, ItemStack> left = inv.addItem(rest);
        if (left.isEmpty()) return null;
        return left.values().iterator().next();
    }

    /** True when the player holds an equal food, differing at most in aging, with room for more. */
    public static boolean hasStackFor(Player p, ItemStack in) {
        FoodItem fIn = FoodItem.fromItem(in);
        if (fIn == null) return false;
        PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (room(inv, fIn, in, inv.getItem(slot)) > 0) return true;
        }
        return false;
    }

    private static int room(PlayerInventory inv, FoodItem fIn, ItemStack in, ItemStack cur) {
        if (cur == null) return 0;

        FoodItem fCur = FoodItem.fromItem(cur);
        if (fCur == null) return 0;

        if (!equalsFood(fIn, fCur)) return 0;
        if (!ConversionManager.sameApartFromAging(in, cur)) return 0;

        return Math.min(cur.getMaxStackSize(), inv.getMaxStackSize()) - cur.getAmount();
    }

    public static boolean equalsFood(FoodItem a, FoodItem b) {
        if (!a.getCategory().equals(b.getCategory())) return false;
        if (!a.getId().equals(b.getId())) return false;
        // Don't stack different origins (Carrot vs Potato, etc.)
        String originA = a.getOrigin();
        String originB = b.getOrigin();
        if (originA == null ? originB != null : !originA.equalsIgnoreCase(originB)) return false;
        if (a.getQualityMin() != b.getQualityMin()) return false;
        if (a.getTagTracks().size() != b.getTagTracks().size()) return false;
        if (!a.sameTags(b)) return false;
        if (a.hasSauce() || b.hasSauce()) return false;
        return true;
    }
}
