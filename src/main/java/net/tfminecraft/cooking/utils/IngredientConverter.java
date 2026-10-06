package net.tfminecraft.cooking.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.crops.CropsConfig;
import net.tfminecraft.cooking.farming.HoeQualityBonus;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.quality.OriginQualityResolver;

public final class IngredientConverter {
    private IngredientConverter() {}

    public static ItemStack convertIfNeeded(Player player, ItemStack stack) {
        return convertHarvestDrop(player, stack, 0.0);
    }

    public static ItemStack convertHarvestDrop(Player player, ItemStack stack, double hoeQualityBonusPercent) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        if (FoodItem.fromItem(stack) != null) {
            return stack;
        }

        String result = ConversionLoader.getByItem(stack);
        if (result == null) {
            return stack;
        }

        FoodParser.Result parsed = FoodParser.parse(result);
        if (parsed == null || parsed.template == null) {
            return stack;
        }

        int quality = CropsConfig.isFarmFood(result)
                ? 1
                : OriginQualityResolver.resolve(player, parsed.template);
        quality = HoeQualityBonus.apply(quality, hoeQualityBonusPercent);
        ItemStack converted = ItemBuilder.buildSingleWithQuality(parsed.template, stack, quality);
        if (converted == null || converted.getType().isAir()) {
            return stack;
        }
        converted.setAmount(stack.getAmount());
        return converted;
    }
}
