package net.tfminecraft.cooking.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class NamingConfig {
    private static List<String> prefixTracks = List.of("sweet");
    private static int fillerMax = 3;
    private static int addonMax = 2;
    private static int doughFillerMax = 2;
    private static Map<String, Set<String>> categories = defaultCategories();
    private static Map<String, Map<String, Map<String, String>>> tagLabelDefaults = Map.of();
    private static Map<String, String> originAdjectives = Map.of();

    private NamingConfig() {}

    private static Map<String, Set<String>> defaultCategories() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("seasoning", Set.of("salt", "pepper"));
        map.put("sweetener", Set.of("sweetener"));
        map.put("addon", Set.of("garnish", "spice"));
        map.put("dough-filler", Set.of("fruit"));
        return map;
    }

    public static void apply(List<String> tracks, int maxFillers, int maxAddons, int maxDoughFillers,
            Map<String, Set<String>> categoryMap,
            Map<String, Map<String, Map<String, String>>> labelDefaults,
            Map<String, String> originAdjectiveMap) {
        prefixTracks = tracks == null ? List.of() : List.copyOf(tracks);
        fillerMax = Math.max(1, maxFillers);
        addonMax = Math.max(1, maxAddons);
        doughFillerMax = Math.max(1, maxDoughFillers);
        categories = categoryMap == null ? defaultCategories() : Collections.unmodifiableMap(categoryMap);
        tagLabelDefaults = labelDefaults == null ? Map.of() : Collections.unmodifiableMap(labelDefaults);
        originAdjectives = originAdjectiveMap == null ? Map.of() : Collections.unmodifiableMap(originAdjectiveMap);
    }

    public static List<String> getPrefixTracks() {
        return prefixTracks;
    }

    public static int getFillerMax() {
        return fillerMax;
    }

    public static int getAddonMax() {
        return addonMax;
    }

    public static int getDoughFillerMax() {
        return doughFillerMax;
    }

    public static Set<String> getCategories(String bucket) {
        Set<String> set = categories.get(bucket);
        return set == null ? Set.of() : set;
    }

    public static boolean isCategory(String bucket, String category) {
        if (category == null) {
            return false;
        }
        return getCategories(bucket).contains(category.toLowerCase(Locale.ROOT));
    }

    public static Set<String> allCategoryBuckets() {
        return new HashSet<>(categories.keySet());
    }

    public static String getTagLabelDefault(String category, String trackId, String stepId) {
        if (category == null || trackId == null || stepId == null) {
            return null;
        }
        Map<String, Map<String, String>> byTrack = tagLabelDefaults.get(category.toLowerCase(Locale.ROOT));
        if (byTrack == null) {
            return null;
        }
        Map<String, String> byStep = byTrack.get(trackId.toLowerCase(Locale.ROOT));
        if (byStep == null) {
            return null;
        }
        return byStep.get(stepId.toLowerCase(Locale.ROOT));
    }

    public static String getOriginAdjective(String origin) {
        if (origin == null) {
            return null;
        }
        return originAdjectives.get(origin.toLowerCase(Locale.ROOT));
    }
}
