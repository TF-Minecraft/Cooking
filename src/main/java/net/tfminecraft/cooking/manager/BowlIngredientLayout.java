package net.tfminecraft.cooking.manager;

import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

/**
 * Bowl ingredient slots share one centered offset in the furniture definition.
 * Spread input_1..input_5 around the bowl interior when the soup is poured.
 */
public final class BowlIngredientLayout {
    static final float RADIUS = 0.12f;
    private static final int INPUTS = 5;

    private BowlIngredientLayout() {}

    public static DisplayData offsetFor(String slotId) {
        Integer index = inputIndex(slotId);
        if (index == null) return null;
        double angle = -Math.PI / 2d + index * (2d * Math.PI / INPUTS);
        DisplayData data = new DisplayData();
        data.setxPos((float) (Math.cos(angle) * RADIUS));
        data.setzPos((float) (Math.sin(angle) * RADIUS));
        return data;
    }

    static Integer inputIndex(String slotId) {
        if (slotId == null || !slotId.startsWith("input_")) return null;
        try {
            int index = Integer.parseInt(slotId.substring("input_".length()));
            if (index < 1 || index > INPUTS) return null;
            return index - 1;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
