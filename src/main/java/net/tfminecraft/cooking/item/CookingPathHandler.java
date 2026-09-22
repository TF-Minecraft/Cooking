package net.tfminecraft.cooking.item;

import java.util.Locale;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.objects.api.subapi.ItemPathHandler;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

public final class CookingPathHandler implements ItemPathHandler {

    public static final CookingPathHandler INSTANCE = new CookingPathHandler();

    private CookingPathHandler() {}

    public static String stripPrefix(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.length() >= 2 && trimmed.regionMatches(true, 0, "c.", 0, 2)) {
            return trimmed.substring(2);
        }
        return trimmed;
    }

    public static String toShortPath(FoodItem food) {
        if (food == null) {
            return null;
        }
        String origin = food.getOrigin() == null ? "" : food.getOrigin();
        String category = food.getCategory() == null ? "" : food.getCategory();
        return "c." + category + "(type=" + food.getId() + ";origin=" + origin + ")";
    }

    @Override
    public ItemStack create(String fullPath) {
        String rest = stripPrefix(fullPath);
        if (rest == null || rest.isBlank() || !rest.contains("(")) {
            return null;
        }
        FoodParser.Result parsed = FoodParser.parse(rest);
        if (parsed == null || parsed.template == null) {
            return null;
        }
        ItemStack stack;
        if (parsed.explicitQuality) {
            stack = ItemBuilder.buildSingle(parsed.template, null);
        } else {
            stack = ItemBuilder.buildSingleString(rest, null);
        }
        if (stack == null) {
            return null;
        }
        stack.setAmount(1);
        return stack;
    }

    @Override
    public boolean matches(ItemStack item, String fullPath) {
        return matches(FoodItem.fromItem(item), fullPath);
    }

    static boolean matches(FoodItem food, String fullPath) {
        if (food == null || fullPath == null) {
            return false;
        }
        String raw = stripPrefix(fullPath);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String category;
        Map<String, String> fields = Map.of();
        int paren = raw.indexOf('(');
        if (paren >= 0 && raw.endsWith(")")) {
            category = raw.substring(0, paren).trim();
            String inside = raw.substring(paren + 1, raw.length() - 1);
            fields = FoodParser.extractFields(inside);
        } else {
            category = raw.trim();
        }
        if (category.isEmpty() || food.getCategory() == null
                || !food.getCategory().equalsIgnoreCase(category)) {
            return false;
        }
        if (fields.isEmpty()) {
            return true;
        }
        String type = field(fields, "type");
        if (type != null && !type.equalsIgnoreCase(food.getId())) {
            return false;
        }
        String origin = field(fields, "origin");
        if (origin != null) {
            String actual = food.getOrigin() == null ? "" : food.getOrigin();
            if (!actual.equalsIgnoreCase(origin)) {
                return false;
            }
        }
        String quality = field(fields, "quality");
        if (quality != null && !qualityMatches(food.getQualityMin(), quality)) {
            return false;
        }
        String tags = field(fields, "tags");
        if (tags != null && !tagsMatch(food, tags)) {
            return false;
        }
        return true;
    }

    private static String field(Map<String, String> fields, String key) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().trim())) {
                String value = entry.getValue();
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
            }
        }
        return null;
    }

    private static boolean qualityMatches(int itemQuality, String spec) {
        try {
            int min;
            int max;
            if (spec.contains("-")) {
                String[] parts = spec.split("-", 2);
                min = Integer.parseInt(parts[0].trim());
                max = Integer.parseInt(parts[1].trim());
                if (min > max) {
                    int swap = min;
                    min = max;
                    max = swap;
                }
            } else {
                min = max = Integer.parseInt(spec.trim());
            }
            return itemQuality >= min && itemQuality <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean tagsMatch(FoodItem food, String spec) {
        for (String rawTag : spec.split("[,:]")) {
            String t = rawTag.trim();
            if (t.isEmpty() || !t.contains(".")) {
                continue;
            }
            String[] kv = t.split("\\.", 2);
            if (kv.length != 2) {
                continue;
            }
            int expected;
            try {
                expected = Integer.parseInt(kv[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
            String trackId = AgeScale.migrateTrackId(kv[0].trim());
            int migratedValue = AgeScale.migrateTrackValue(kv[0].trim(), expected);
            TagTrack track = findTrack(food, trackId);
            if (track == null || track.getValue() != migratedValue) {
                return false;
            }
        }
        return true;
    }

    private static TagTrack findTrack(FoodItem food, String trackId) {
        if (trackId == null) {
            return null;
        }
        TagTrack direct = food.getTagTrack(trackId);
        if (direct != null) {
            return direct;
        }
        String lower = trackId.toLowerCase(Locale.ROOT);
        TagTrack lowerTrack = food.getTagTrack(lower);
        if (lowerTrack != null) {
            return lowerTrack;
        }
        for (TagTrack track : food.getTagTracks()) {
            if (track.getId() != null && track.getId().equalsIgnoreCase(trackId)) {
                return track;
            }
        }
        return null;
    }
}
