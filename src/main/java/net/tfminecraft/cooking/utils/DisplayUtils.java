package net.tfminecraft.cooking.utils;

import java.util.List;

public class DisplayUtils {
    public static String getDisplayString(String name, double foodMultiplier, double nutritionMultiplier) {
        return getDisplayString(name, foodMultiplier, nutritionMultiplier, 0.0);
    }

    public static String getDisplayString(String name, double foodMultiplier, double nutritionMultiplier,
            double craftQualityPct) {
        return getDisplayString(name, foodMultiplier, nutritionMultiplier, craftQualityPct, 0);
    }

    public static String getDisplayString(String name, double foodMultiplier, double nutritionMultiplier,
            double craftQualityPct, int stars) {
        StringBuilder extra = new StringBuilder();

        // Food modifier
        if (foodMultiplier != 1.0) {
            double pct = (foodMultiplier - 1.0) * 100.0;
            String color = pct > 0 ? "§a" : "§c"; // green or red

            extra.append(color)
                .append(String.format("%+.0f%% Food", pct));
        }

        // Nutrition modifier
        if (nutritionMultiplier != 1.0) {
            if (extra.length() > 0) extra.append("§7, ");

            double pct = (nutritionMultiplier - 1.0) * 100.0;
            String color = pct > 0 ? "§a" : "§c";

            extra.append(color)
                .append(String.format("%+.0f%% Nutrition", pct));
        }

        if (craftQualityPct > 0) {
            if (extra.length() > 0) extra.append("§7, ");
            int pct = (int) Math.round(craftQualityPct * 100);
            extra.append("§aCraft Quality +").append(pct).append('%');
        }

        if (stars != 0) {
            if (extra.length() > 0) extra.append("§7, ");
            extra.append(stars > 0 ? "§a" : "§c").append(String.format("%+d★", stars));
        }

        // No modifiers → just return the name
        if (extra.length() == 0) {
            return name;
        }

        // Combine name + modifiers
        return name + " §8(" + extra.toString() + "§8)";
    }

    public static String getMergedColour(List<String> colours) {
        if (colours.size() == 0) return "000000";
        if (colours.size() == 1) return colours.get(0);

        int r = 0, g = 0, b = 0;

        for (String col : colours) {
            col = col.replace("#", ""); // safety
            r += Integer.parseInt(col.substring(0, 2), 16);
            g += Integer.parseInt(col.substring(2, 4), 16);
            b += Integer.parseInt(col.substring(4, 6), 16);
        }

        int size = colours.size();
        r /= size;
        g /= size;
        b /= size;

        // format back to hex, always 2 digits
        return String.format("#%02x%02x%02x", r, g, b);
    }

    public static String getSauceStatString(double food, double nutrition) {
        StringBuilder sb = new StringBuilder();

        // Food entry
        if (food != 0) {
            String color = food > 0 ? "§a" : "§c";
            sb.append(color)
            .append(String.format("%+.1f Food", food)); // +1.7 Food
        }

        // Nutrition entry
        if (nutrition != 0) {
            if (sb.length() > 0) sb.append("§7, ");
            String color = nutrition > 0 ? "§a" : "§c";
            sb.append(color)
            .append(String.format("%+.1f Nutrition", nutrition)); // +3.1 Nutrition
        }

        // If nothing to show, return empty
        if (sb.length() == 0)
            return "§7No effect";

        return sb.toString();
    }
}
