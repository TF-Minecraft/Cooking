package net.tfminecraft.cooking.cache;

import java.util.HashMap;
import java.util.Set;

import org.apache.commons.lang.WordUtils;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class CategoryDictionary {
    public static HashMap<String, String> dictionary = new HashMap<>();
    public static HashMap<String, String> sauceDict = new HashMap<>();

    public static final Set<String> UNIMPORTANT_CATEGORIES = Set.of(
        "seasoning",
        "sweetener",
        "flour",
        "salt",
        "pepper"
    );


    public static String getName(String key) {
        return StringFormatter.formatHex(dictionary.getOrDefault(key, "#8f8f8f" + WordUtils.capitalize(new String(key).replace("_", " "))));
    }

    public static String getSauceItemPath(String colour, int index) {
        int rTotal = 0, gTotal = 0, bTotal = 0, count = 0;
        String hex = colour.replace("#", "");
        if (hex.equalsIgnoreCase("000000")) return ItemCache.liquidFallback;

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);

            rTotal += r;
            gTotal += g;
            bTotal += b;
            count++;
        } catch (Exception ignored) {}

        if (count == 0)
            return ItemCache.liquidFallback;

        int rAvg = rTotal / count;
        int gAvg = gTotal / count;
        int bAvg = bTotal / count;

        double bestDistance = Double.MAX_VALUE;
        String bestKey = null;

        for (String key : sauceDict.keySet()) {

            if (key.equalsIgnoreCase("000000")) continue;

            try {
                int r = Integer.parseInt(key.substring(0, 2), 16);
                int g = Integer.parseInt(key.substring(2, 4), 16);
                int b = Integer.parseInt(key.substring(4, 6), 16);

                double dist = Math.sqrt(
                    Math.pow(r - rAvg, 2) +
                    Math.pow(g - gAvg, 2) +
                    Math.pow(b - bAvg, 2)
                );

                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestKey = key;
                }

            } catch (Exception ignored) {}
        }

        if (bestKey != null) {
            String[] paths = sauceDict.get(bestKey).split("\\|", 2);
            if (index >= 0 && index < paths.length && !paths[index].isBlank()) {
                return paths[index];
            }
        }

        return ItemCache.liquidFallback;
    }
}
