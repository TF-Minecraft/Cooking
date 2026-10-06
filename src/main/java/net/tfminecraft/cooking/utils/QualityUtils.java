package net.tfminecraft.cooking.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;

public final class QualityUtils {
    private static final int MIN_QUALITY = 1;
    private static final int MAX_QUALITY = 5;

    private QualityUtils() {}

    public static int average(int... qualities) {
        if (qualities == null || qualities.length == 0) {
            return MIN_QUALITY;
        }
        long sum = 0;
        for (int quality : qualities) {
            sum += quality;
        }
        int rounded = (int) Math.round(sum / (double) qualities.length);
        return clamp(rounded);
    }

    public static int fromStack(ItemStack stack, int legacyDefault) {
        if (stack == null) {
            return clamp(legacyDefault);
        }
        FoodItem foodItem = FoodItem.fromItem(stack);
        if (foodItem == null) {
            return clamp(legacyDefault);
        }
        return clamp(foodItem.getQualityMin());
    }

    public static int fromSeedStack(ItemStack stack) {
        if (stack == null) {
            return MIN_QUALITY;
        }
        FoodItem foodItem = FoodItem.fromItem(stack);
        if (foodItem != null) {
            return clamp(foodItem.getQualityMin());
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return MIN_QUALITY;
        }
        Integer stored = meta.getPersistentDataContainer().get(Keys.QUALITY, PersistentDataType.INTEGER);
        if (stored == null) {
            return MIN_QUALITY;
        }
        return clamp(stored);
    }

    public static int clamp(int quality) {
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, quality));
    }
}
