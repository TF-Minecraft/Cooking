package net.tfminecraft.cooking.baking;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class BakingTrayState {
    private static final String SLOT_ELAPSED_PREFIX = "bake.slot.";
    private static final String SLOT_ELAPSED_SUFFIX = ".elapsed";

    private static final String LEGACY_ELAPSED = "bake.elapsed";
    private static final String LEGACY_COOK_APPLIED = "bake.cook_applied";
    private static final String LEGACY_BURN_APPLIED = "bake.burn_applied";

    private BakingTrayState() {}

    public static int getSlotElapsed(Furniture furniture, String slotId) {
        Object value = furniture.getVariables().get(slotKey(slotId));
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public static void setSlotElapsed(Furniture furniture, String slotId, int elapsed) {
        furniture.getVariables().put(slotKey(slotId), elapsed);
    }

    public static void incrementSlotElapsed(Furniture furniture, String slotId) {
        setSlotElapsed(furniture, slotId, getSlotElapsed(furniture, slotId) + 1);
    }

    public static void clearSlot(Furniture furniture, String slotId) {
        furniture.getVariables().remove(slotKey(slotId));
    }

    public static void clearSlots(Furniture furniture, Collection<String> slotIds) {
        for (String slotId : slotIds) {
            clearSlot(furniture, slotId);
        }
    }

    public static void clear(Furniture furniture) {
        Map<String, Object> variables = furniture.getVariables();
        Iterator<String> keys = variables.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(SLOT_ELAPSED_PREFIX) || isLegacyKey(key)) {
                keys.remove();
            }
        }
    }

    private static String slotKey(String slotId) {
        return SLOT_ELAPSED_PREFIX + slotId + SLOT_ELAPSED_SUFFIX;
    }

    private static boolean isLegacyKey(String key) {
        return LEGACY_ELAPSED.equals(key)
                || LEGACY_COOK_APPLIED.equals(key)
                || LEGACY_BURN_APPLIED.equals(key);
    }
}
