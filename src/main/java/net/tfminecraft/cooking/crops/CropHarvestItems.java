package net.tfminecraft.cooking.crops;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.QualityUtils;

public final class CropHarvestItems {

    private CropHarvestItems() {}

    /**
     * Hoe harvest: convert produce at quality H. Wheat/beetroot/melon/pumpkin seeds stay vanilla.
     * Potato and carrot are produce even when they are also the replant item.
     * Nether wart has no conversion, so a harvested drop stays vanilla.
     */
    public static ItemStack convertDrop(ItemStack drop, CropDefinition crop, int quality) {
        if (drop == null || drop.getType().isAir()) {
            return drop;
        }
        if (isSeedOnly(drop.getType())) {
            return drop;
        }
        return convertProduce(drop, quality);
    }

    public static ItemStack rewriteCustomDrop(ItemStack drop, CropDefinition crop, int quality) {
        if (drop == null || drop.getType().isAir()) {
            return drop;
        }
        if (crop != null && isConfiguredSeed(drop, crop)) {
            return drop;
        }
        if (FoodItem.fromItem(drop) != null) {
            return drop;
        }
        return convertProduce(drop, quality);
    }

    private static ItemStack convertProduce(ItemStack drop, int quality) {
        String result = ConversionLoader.getByItem(drop);
        if (result == null) {
            return drop;
        }
        FoodParser.Result parsed = FoodParser.parse(result);
        if (parsed == null || parsed.template == null) {
            return drop;
        }
        ItemStack food = ItemBuilder.buildSingleWithQuality(parsed.template, QualityUtils.clamp(quality));
        if (food == null) {
            return drop;
        }
        food.setAmount(drop.getAmount());
        return food;
    }

    public static boolean isConfiguredSeed(ItemStack stack, CropDefinition crop) {
        if (stack == null || crop == null || crop.seed() == null || crop.seed().isBlank()) {
            return false;
        }
        try {
            return TLibs.getItemAPI().getChecker().checkItemWithPath(stack, crop.seed());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isSeedOnly(Material material) {
        return material == Material.WHEAT_SEEDS
                || material == Material.BEETROOT_SEEDS
                || material == Material.MELON_SEEDS
                || material == Material.PUMPKIN_SEEDS;
    }
}
