package net.tfminecraft.cooking.quality;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class QualityConfig {
    private static int pickupMin = 1;
    private static int pickupMax = 5;
    private static Set<String> excludeCategories = defaultExcludes();
    private static Map<Integer, Double> nutritionFromQuality = defaultNutritionFromQuality();

    private QualityConfig() {}

    private static Set<String> defaultExcludes() {
        Set<String> set = new HashSet<>();
        set.add("seasoning");
        set.add("sweetener");
        set.add("flour");
        set.add("salt");
        set.add("pepper");
        return set;
    }

    private static Map<Integer, Double> defaultNutritionFromQuality() {
        Map<Integer, Double> map = new HashMap<>();
        map.put(1, 0.28);
        map.put(2, 0.50);
        map.put(3, 0.75);
        map.put(4, 1.05);
        map.put(5, 1.70);
        return map;
    }

    public static void apply(int min, int max, Set<String> excludes, Map<Integer, Double> nutrition) {
        pickupMin = Math.clamp(min, 1, 5);
        pickupMax = Math.clamp(max, pickupMin, 5);
        excludeCategories = excludes == null || excludes.isEmpty()
                ? defaultExcludes()
                : Set.copyOf(excludes);
        if (nutrition == null || nutrition.isEmpty()) {
            nutritionFromQuality = defaultNutritionFromQuality();
        } else {
            nutritionFromQuality = Map.copyOf(nutrition);
        }
    }

    public static int getPickupMin() {
        return pickupMin;
    }

    public static int getPickupMax() {
        return pickupMax;
    }

    public static Set<String> getExcludeCategories() {
        return excludeCategories;
    }

    public static boolean isExcludedCategory(String category) {
        if (category == null) {
            return false;
        }
        return excludeCategories.contains(category.toLowerCase(Locale.ROOT));
    }

    public static double nutritionMultiplier(int quality) {
        int stars = Math.max(1, Math.min(5, quality));
        Double value = nutritionFromQuality.get(stars);
        if (value == null) {
            return 1.0 + (stars - 1) * 0.20;
        }
        return value;
    }
}
