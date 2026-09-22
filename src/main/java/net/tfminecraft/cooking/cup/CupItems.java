package net.tfminecraft.cooking.cup;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

public final class CupItems {
    private CupItems() {}

    public static ItemStack emptyCup() {
        return TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.emptyCup).clone();
    }

    public static ItemStack cupOfWater() {
        return TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.cupOfWater).clone();
    }

    public static ItemStack cupOfMilk(Player player, int quality, int dairyFreshnessValue) {
        return cupOfMilk(player, quality, dairyFreshnessValue, DairyOrigin.COW);
    }

    public static ItemStack cupOfMilk(Player player, int quality, int dairyFreshnessValue, String origin) {
        String itemString = "ingredient(type=cup_of_milk;origin="
                + DairyOrigin.orCow(origin)
                + ";tags=freshness."
                + dairyFreshnessValue + ")";
        FoodParser.Result parsed = FoodParser.parse(itemString);
        if (parsed == null || parsed.template == null) {
            return null;
        }
        return ItemBuilder.buildSingleWithQuality(parsed.template, null, quality);
    }

    public static ItemStack cupOfMilk(Player player, int dairyFreshnessValue) {
        return cupOfMilk(player, dairyFreshnessValue, DairyOrigin.COW);
    }

    public static ItemStack cupOfMilk(Player player, int dairyFreshnessValue, String origin) {
        FoodParser.Result parsed = FoodParser.parse(
                "ingredient(type=cup_of_milk;origin=" + DairyOrigin.orCow(origin) + ";tags=freshness.0)");
        if (parsed == null || parsed.template == null) {
            return null;
        }
        int quality = OriginQualityResolver.resolve(player, parsed.template);
        return cupOfMilk(player, quality, dairyFreshnessValue, origin);
    }
}
