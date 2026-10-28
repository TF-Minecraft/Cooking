package net.tfminecraft.cooking.brewery;

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import com.dre.brewery.Brew;
import com.dre.brewery.api.BreweryApi;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.recipe.PotionColor;

/** Reads BreweryX brews for the saucepan. Only loaded while BreweryX is enabled. */
final class BrewLiquidReader {
    private BrewLiquidReader() {}

    static BrewLiquid read(ItemStack item) {
        Brew brew = BreweryApi.getBrew(item);
        return brew == null ? null : describe(brew.getCurrentRecipe(), brew.getQuality());
    }

    /** A brew still fermenting has no recipe yet, so it is not a finished drink. */
    static BrewLiquid describe(BRecipe recipe, int quality) {
        if (recipe == null) return null;
        return new BrewLiquid(BrewLiquid.plainName(recipe.getName(quality)), BrewLiquid.starsFor(quality),
                hex(recipe.getColor()), recipe.getAlcohol() > 0);
    }

    private static String hex(PotionColor colour) {
        Color rgb = colour == null ? null : colour.getColor();
        return rgb == null ? null : String.format("%06x", rgb.asRGB());
    }
}
