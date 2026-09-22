package net.tfminecraft.cooking.item;

/**
 * Versioned lineage text used by item PDC and FoodParser.
 * Sections are separated with `|` because FoodParser splits fields on `;`.
 */
public final class IngredientLineageCodec {
    private static final String VERSION = "1";

    private IngredientLineageCodec() {}

    public static String encode(IngredientLineage lineage) {
        if (lineage == null || lineage.isEmpty()) {
            return "";
        }
        return VERSION + "|" + join(lineage.mains()) + "|" + join(lineage.extras());
    }

    public static IngredientLineage decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return IngredientLineage.empty();
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return IngredientLineage.empty();
        }
        IngredientLineage lineage = IngredientLineage.empty();
        lineage = addAll(lineage, parts[1], true);
        lineage = addAll(lineage, parts[2], false);
        return lineage;
    }

    private static IngredientLineage addAll(IngredientLineage lineage, String encoded, boolean mains) {
        if (encoded == null || encoded.isEmpty()) {
            return lineage;
        }
        IngredientLineage result = lineage;
        for (String piece : encoded.split(",", -1)) {
            String origin = unescape(piece);
            result = mains ? result.withMain(origin) : result.withExtra(origin);
        }
        return result;
    }

    private static String join(java.util.List<String> origins) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String origin : origins) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(escape(origin));
        }
        return builder.toString();
    }

    static String escape(String origin) {
        StringBuilder builder = new StringBuilder(origin.length());
        for (int i = 0; i < origin.length(); i++) {
            char c = origin.charAt(i);
            switch (c) {
                case '%' -> builder.append("%25");
                case ';' -> builder.append("%3B");
                case ',' -> builder.append("%2C");
                case '=' -> builder.append("%3D");
                case '(' -> builder.append("%28");
                case ')' -> builder.append("%29");
                case '|' -> builder.append("%7C");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    static String unescape(String encoded) {
        StringBuilder builder = new StringBuilder(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%' && i + 2 < encoded.length()) {
                int value = hex(encoded.charAt(i + 1), encoded.charAt(i + 2));
                if (value >= 0) {
                    builder.append((char) value);
                    i += 2;
                    continue;
                }
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static int hex(char high, char low) {
        int hi = Character.digit(high, 16);
        int lo = Character.digit(low, 16);
        if (hi < 0 || lo < 0) {
            return -1;
        }
        return (hi << 4) + lo;
    }
}
