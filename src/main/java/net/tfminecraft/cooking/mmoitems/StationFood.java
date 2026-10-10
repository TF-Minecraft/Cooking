package net.tfminecraft.cooking.mmoitems;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.fishing.CustomFishingCatalog;
import net.tfminecraft.cooking.fishing.SeafoodWholeItems;
import net.tfminecraft.cooking.fishing.VanillaFish;
import net.tfminecraft.cooking.fishing.VanillaFishAdapter;
import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.tlibs.TLibs;

/**
 * The food an inventory item counts as at an MMOItems crafting station.
 * Cooking food is itself. A plain vanilla item (TLibs path {@code v.<material>}) counts as the
 * food Cooking turns it into on pickup, so a fresh catch and a picked-up one match the same
 * {@code cooking{...}} ingredient.
 */
public final class StationFood {
    /** Plain items carry no Cooking quality; they count as the lowest. */
    static final int PLAIN_QUALITY = 1;

    private StationFood() {}

    public static FoodItem describe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        FoodItem food = FoodItem.fromItem(item);
        if (food != null) {
            return food;
        }
        String path = TLibs.getItemAPI().getChecker().getAsStringPath(item);
        if (path == null || !path.toLowerCase(Locale.ROOT).startsWith("v.")) {
            return null;
        }
        FoodItem converted = vanillaFish(item);
        if (converted == null) {
            converted = conversion(path);
        }
        if (converted != null) {
            converted.setQualityRange(PLAIN_QUALITY, PLAIN_QUALITY);
        }
        return converted;
    }

    private static FoodItem vanillaFish(ItemStack item) {
        VanillaFishAdapter.Decision decision = VanillaFishAdapter.decide(item.getType().name(), false);
        FoodItem template = FoodLoader.getByString(CustomFishingCatalog.WHOLE_TYPE);
        if (!decision.replaces() || template == null) {
            return null;
        }
        return SeafoodWholeItems.describe(template, decision.fish().origin(), decision.fish().cut(),
                decision.fish().sizeCm(), PLAIN_QUALITY, null);
    }

    /**
     * The plain item that becomes food matching {@code path}, e.g. a salmon for a whole salmon.
     * Foods without a model of their own (whole fish) are built on that item. Null when none.
     */
    public static ItemStack plainSource(String path) {
        for (VanillaFish fish : CustomFishingCatalog.vanillaFish()) {
            ItemStack source = source(fish.material());
            if (source != null && CookingPathHandler.matches(vanillaFish(source), path)) {
                return source;
            }
        }
        for (Map.Entry<String, String> entry : ConversionLoader.get().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            ItemStack source = key.startsWith("v.") ? source(key.substring(2)) : null;
            if (source != null && CookingPathHandler.matches(conversion(key), path)) {
                return source;
            }
        }
        return null;
    }

    private static ItemStack source(String material) {
        Material type = Material.matchMaterial(material);
        return type == null || !type.isItem() || type.isAir() ? null : new ItemStack(type);
    }

    /** Plain items only have a {@code v.} path, so compare it to the conversion keys directly. */
    private static FoodItem conversion(String path) {
        for (Map.Entry<String, String> entry : ConversionLoader.get().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(path)) {
                FoodParser.Result parsed = FoodParser.parse(entry.getValue());
                return parsed == null ? null : parsed.template;
            }
        }
        return null;
    }
}
