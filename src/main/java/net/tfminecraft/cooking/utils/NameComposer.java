package net.tfminecraft.cooking.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;

public final class NameComposer {
    private NameComposer() {}

    public static String compose(FoodItem item, Map<String, String> extras) {
        if (item == null) {
            return "";
        }

        String template = item.getName();
        if (template == null) {
            template = "";
        }

        String colour = extras == null ? "" : extras.getOrDefault("colour", "");
        if (colour == null) {
            colour = "";
        }

        String prefixes = formatPrefixes(item);
        String fillers = formatFillerPhrase(item.getIngredients());
        String ingredients = formatFillerPhrase(item.getIngredients(), inferTypeLabel(template));

        String result = template
                .replace("{colour}", colour)
                .replace("{prefixes}", prefixes)
                .replace("{fillers}", fillers)
                .replace("{ingredients}", ingredients);

        return StringFormatter.formatHex(result);
    }

    public static String formatPrefixes(FoodItem item) {
        if (item == null) {
            return "";
        }

        StringBuilder prefixes = new StringBuilder();
        for (String trackId : NamingConfig.getPrefixTracks()) {
            if (!item.hasTagTrack(trackId)) {
                continue;
            }
            TagTrack track = item.getTagTrack(trackId);
            if (track == null) {
                continue;
            }
            TagStep step = track.getCurrentStep();
            if (step == null) {
                continue;
            }
            String plain = stripHexFromTaggedName(TagDisplayNames.resolve(item, track, step));
            if (plain.isEmpty()) {
                continue;
            }
            prefixes.append(plain).append(' ');
        }
        return prefixes.toString();
    }

    public static String formatFillerPhrase(List<String> origins) {
        return formatFillerPhrase(origins, null);
    }

    public static String formatFillerPhrase(List<String> origins, String typeLabel) {
        List<String> ingredients = trimAndCap(origins);
        if (ingredients.isEmpty()) {
            if (typeLabel == null || typeLabel.isEmpty()) {
                return "";
            }
            return "Mixed " + typeLabel;
        }

        if (ingredients.size() == 1) {
            String first = ingredients.get(0);
            if (typeLabel == null || typeLabel.isEmpty()) {
                return first + " ";
            }
            return first + " " + typeLabel;
        }

        if (ingredients.size() == 2) {
            String phrase = ingredients.get(0) + " and " + ingredients.get(1);
            if (typeLabel == null || typeLabel.isEmpty()) {
                return phrase + " ";
            }
            return phrase + " " + typeLabel;
        }

        String first = ingredients.get(0);
        String second = ingredients.get(1);
        String last = ingredients.get(2);
        String phrase = first + ", " + second + " and " + last;
        if (typeLabel == null || typeLabel.isEmpty()) {
            return phrase + " ";
        }
        return phrase + " " + typeLabel;
    }

    private static List<String> trimAndCap(List<String> origins) {
        List<String> ingredients = new ArrayList<>();
        if (origins == null) {
            return ingredients;
        }
        int max = NamingConfig.getFillerMax();
        for (String origin : origins) {
            if (origin == null) {
                continue;
            }
            String trimmed = origin.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String mapped = NamingConfig.getOriginAdjective(trimmed);
            if (mapped != null) {
                trimmed = mapped;
            }
            if (!ingredients.contains(trimmed)) {
                ingredients.add(trimmed);
            }
            if (ingredients.size() >= max) {
                break;
            }
        }
        return ingredients;
    }

    private static String stripHexFromTaggedName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.charAt(0) == '#' && name.length() > 7) {
            String hex = name.substring(1, 7);
            if (hex.matches("[0-9a-fA-F]{6}")) {
                return name.substring(7);
            }
        }
        return name;
    }

    private static String inferTypeLabel(String template) {
        if (template == null) {
            return "";
        }
        int idx = template.lastIndexOf('}');
        if (idx >= 0 && idx + 1 < template.length()) {
            return template.substring(idx + 1);
        }
        return "";
    }
}
