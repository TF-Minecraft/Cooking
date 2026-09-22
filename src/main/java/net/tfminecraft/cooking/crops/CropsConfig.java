package net.tfminecraft.cooking.crops;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.loader.ConversionLoader;

public final class CropsConfig {

    public static final String SOURCE_VANILLA = "vanilla";
    public static final String SOURCE_CUSTOMCROPS = "customcrops";

    private static final Set<String> FARM_VANILLA_INPUTS = Set.of(
            "v.wheat",
            "v.carrot",
            "v.potato",
            "v.beetroot",
            "v.apple");
    private static final Set<String> NON_FARM_COOKING_IDS = Set.of(
            "dough",
            "salt",
            "butter",
            "cup_of_milk");
    private static final Map<Integer, Double> DEFAULT_RICH = Map.of(1, 8.0, 2, 16.0, 3, 28.0, 4, 28.0, 5, 20.0);
    private static final Map<Integer, Double> DEFAULT_POOR = Map.of(1, 40.0, 2, 28.0, 3, 18.0, 4, 10.0, 5, 4.0);

    private static Map<Integer, Double> harvestRich = DEFAULT_RICH;
    private static Map<Integer, Double> harvestPoor = DEFAULT_POOR;
    private static boolean growthGateEnabled = true;
    private static Map<String, CropDefinition> crops = Map.of();
    private static Map<Material, CropDefinition> cropsByBlock = Map.of();

    private CropsConfig() {}

    public static void apply(
            Map<Integer, Double> richWeights,
            Map<Integer, Double> poorWeights,
            Map<String, CropDefinition> cropDefinitions) {
        apply(richWeights, poorWeights, cropDefinitions, true);
    }

    public static void apply(
            Map<Integer, Double> richWeights,
            Map<Integer, Double> poorWeights,
            Map<String, CropDefinition> cropDefinitions,
            boolean growthGate) {
        harvestRich = copyStarWeights(richWeights, DEFAULT_RICH);
        harvestPoor = copyStarWeights(poorWeights, DEFAULT_POOR);
        growthGateEnabled = growthGate;
        crops = cropDefinitions == null ? Map.of() : Map.copyOf(cropDefinitions);
        Map<Material, CropDefinition> byBlock = new HashMap<>();
        for (CropDefinition crop : crops.values()) {
            if (crop.block() != null) {
                byBlock.put(crop.block(), crop);
            }
        }
        cropsByBlock = Map.copyOf(byBlock);
    }

    private static Map<Integer, Double> copyStarWeights(Map<Integer, Double> source, Map<Integer, Double> fallback) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Map<Integer, Double> copy = new HashMap<>();
        for (int star = 1; star <= 5; star++) {
            copy.put(star, Math.max(0.0, source.getOrDefault(star, fallback.get(star))));
        }
        return Map.copyOf(copy);
    }

    public static Map<Integer, Double> harvestRich() {
        return harvestRich;
    }

    public static Map<Integer, Double> harvestPoor() {
        return harvestPoor;
    }

    public static boolean growthGateEnabled() {
        return growthGateEnabled;
    }

    public static Map<String, CropDefinition> crops() {
        return crops;
    }

    public static CropDefinition crop(String id) {
        if (id == null) {
            return null;
        }
        return crops.get(id.toLowerCase(Locale.ROOT));
    }

    public static CropDefinition byBlock(Material block) {
        if (block == null) {
            return null;
        }
        return cropsByBlock.get(block);
    }

    public static String normalizeSource(String source) {
        if (source != null && SOURCE_CUSTOMCROPS.equalsIgnoreCase(source.trim())) {
            return SOURCE_CUSTOMCROPS;
        }
        return SOURCE_VANILLA;
    }

    public static boolean isFarmFood(String parserString) {
        if (parserString == null || parserString.isBlank()) {
            return false;
        }
        String stripped = CookingPathHandler.stripPrefix(parserString).trim();
        for (Map.Entry<String, String> entry : ConversionLoader.get().entrySet()) {
            if (!isFarmConversionInput(entry.getKey())) {
                continue;
            }
            String output = CookingPathHandler.stripPrefix(entry.getValue());
            if (output != null && output.trim().equals(stripped)) {
                return true;
            }
        }
        return false;
    }

    static boolean isFarmConversionInput(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String key = input.trim();
        if (FARM_VANILLA_INPUTS.contains(key)) {
            return true;
        }
        String prefix = "ia.tfmc_cooking:";
        if (!key.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        String id = key.substring(prefix.length());
        return !NON_FARM_COOKING_IDS.contains(id.toLowerCase(Locale.ROOT));
    }
}
