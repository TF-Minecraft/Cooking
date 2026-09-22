package net.tfminecraft.cooking.fishing;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;

public final class SeafoodWholeItems {
    private SeafoodWholeItems() {}

    public static FoodItem describe(FoodItem template, CatchMapping mapping, int sizeCm, int quality) {
        return describe(template, mapping.origin(), mapping.cut(), sizeCm, quality, mapping.id());
    }

    public static FoodItem describe(FoodItem template, String origin, String cut, int sizeCm, int quality,
            String customFishingId) {
        FoodItem item = new FoodItem(template);
        item.setCategory("seafood");
        item.setOrigin(origin);
        item.setLineage(IngredientLineage.ofMain(origin));
        item.setQualityRange(quality, quality);
        item.setCatchSizeCm(sizeCm);
        item.setSeafoodCutType(cut);
        item.setCustomFishingId(customFishingId);
        item.setAmount(1);
        return item;
    }

    public static ItemStack build(ItemStack caught, CatchMapping mapping, int sizeCm, int quality) {
        FoodItem template = templateOrNull();
        if (template == null || caught == null || mapping == null) {
            return null;
        }
        return build(caught, describe(template, mapping.origin(), mapping.cut(), sizeCm, quality, mapping.id()), quality);
    }

    public static ItemStack build(ItemStack caught, VanillaFish fish, int quality) {
        FoodItem template = templateOrNull();
        if (template == null || caught == null || fish == null) {
            return null;
        }
        return build(caught,
                describe(template, fish.origin(), fish.cut(), fish.sizeCm(), quality, null),
                quality);
    }

    private static FoodItem templateOrNull() {
        return FoodLoader.getByString(CustomFishingCatalog.WHOLE_TYPE);
    }

    private static ItemStack build(ItemStack caught, FoodItem item, int quality) {
        if (item == null || caught == null) {
            return null;
        }
        return ItemBuilder.buildSingleWithQuality(item, caught, quality);
    }
}
