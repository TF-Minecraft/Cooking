package net.tfminecraft.cooking.nutrition;

import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.IngredientLineageCodec;

public final class VarietyHistoryCodec {
    private static final String VERSION = "1";
    private static final char SEPARATOR = '\u001e';

    private VarietyHistoryCodec() {}

    public static String encode(VarietyHistory history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(VERSION);
        for (IngredientLineage meal : history.meals()) {
            builder.append(SEPARATOR);
            builder.append(IngredientLineageCodec.encode(meal));
        }
        return builder.toString();
    }

    public static VarietyHistory decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return VarietyHistory.empty();
        }
        String[] parts = raw.split(String.valueOf(SEPARATOR), -1);
        if (parts.length == 0 || !VERSION.equals(parts[0])) {
            return VarietyHistory.empty();
        }
        VarietyHistory history = VarietyHistory.empty();
        for (int i = 1; i < parts.length; i++) {
            history = history.append(IngredientLineageCodec.decode(parts[i]), Integer.MAX_VALUE);
        }
        return history;
    }
}
