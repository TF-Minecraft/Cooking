package net.tfminecraft.cooking.manager;

import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

/**
 * Bowl ingredient slots share one centered offset in the furniture definition.
 * Spread input_1..input_5 around the bowl interior when the soup is poured.
 *
 * <p>The bowl's 0.6 slot scale lets the larger cut vegetables and steaks reach
 * through the bowl wall. SCALE shrinks them to 0.42 and the slot drops by
 * DROP so they still rest on the soup surface. With RADIUS this keeps every
 * pot ingredient inside the bowl's inner wall in all five slots.
 *
 * <p>A salad's ingredients are drawn as small heaps for the bowl (salads.yml models), so they spread wider
 * ({@link #offsetFor(String, float)}, salads.yml spread-radius) and fill it.
 */
public final class BowlIngredientLayout {
    public static final float RADIUS = 0.06f;
    static final float SCALE = 0.7f;
    /** The bowl slot scale (0.6) times half the shrink, so the model's base stays put. */
    static final float DROP = 0.6f * (1f - SCALE) / 2f;
    /**
     * How much higher each salad slot lies than the one before, in blocks: too little to see, but enough that the
     * same heap in two slots never has faces at one height where they overlap (they would flicker).
     */
    static final float STAGGER = 0.002f;
    private static final int INPUTS = 5;

    private BowlIngredientLayout() {}

    /** Where a soup's ingredient in this slot lies, or null for a slot that isn't input_1..input_5. */
    public static DisplayData offsetFor(String slotId) {
        return place(slotId, RADIUS, 0f);
    }

    /** As {@link #offsetFor(String)}, on a circle of this radius (blocks), each slot STAGGER above the one before. */
    public static DisplayData offsetFor(String slotId, float radius) {
        return place(slotId, radius, STAGGER);
    }

    private static DisplayData place(String slotId, float radius, float stagger) {
        Integer index = inputIndex(slotId);
        if (index == null) return null;
        double angle = -Math.PI / 2d + index * (2d * Math.PI / INPUTS);
        DisplayData data = new DisplayData();
        data.setxPos((float) (Math.cos(angle) * radius));
        data.setyPos(-DROP + index * stagger);
        data.setzPos((float) (Math.sin(angle) * radius));
        data.setxScale(SCALE);
        data.setyScale(SCALE);
        data.setzScale(SCALE);
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
