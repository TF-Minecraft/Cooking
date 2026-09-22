package net.tfminecraft.cooking.oven;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class OvenState {
    public static final String VAR_LIT = "oven.lit";
    public static final String VAR_STAGES = "oven.stages";

    private static final int SLOT_COUNT = OvenSlots.FILL_ORDER.length;
    private static final String EMPTY_STAGES = "EEEEEE";

    private OvenState() {}

    public static boolean isLit(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_LIT);
        if (value instanceof Boolean lit) {
            return lit;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    public static void setLit(Furniture furniture, boolean lit) {
        furniture.getVariables().put(VAR_LIT, lit);
    }

    public static OvenSlots.WoodStage getStage(Furniture furniture, String slotId) {
        int index = slotIndex(slotId);
        if (index < 0) {
            return OvenSlots.WoodStage.EMPTY;
        }
        return getStages(furniture)[index];
    }

    public static void setStage(Furniture furniture, String slotId, OvenSlots.WoodStage stage) {
        int index = slotIndex(slotId);
        if (index < 0) {
            return;
        }
        OvenSlots.WoodStage[] stages = getStages(furniture);
        stages[index] = stage;
        saveStages(furniture, stages);
    }

    public static OvenSlots.WoodStage[] getStages(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_STAGES);
        if (!(value instanceof String encoded) || encoded.length() != SLOT_COUNT) {
            return emptyStages();
        }
        OvenSlots.WoodStage[] stages = new OvenSlots.WoodStage[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            stages[i] = OvenSlots.WoodStage.fromCode(encoded.charAt(i));
        }
        return stages;
    }

    public static int getWoodCount(Furniture furniture) {
        int count = 0;
        for (OvenSlots.WoodStage stage : getStages(furniture)) {
            if (stage != OvenSlots.WoodStage.EMPTY) {
                count++;
            }
        }
        return count;
    }

    public static boolean isFull(Furniture furniture) {
        return getWoodCount(furniture) >= SLOT_COUNT;
    }

    public static String findNextFillSlot(Furniture furniture) {
        if (isFull(furniture)) {
            return null;
        }
        OvenSlots.WoodStage[] stages = getStages(furniture);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (stages[i] == OvenSlots.WoodStage.EMPTY) {
                return OvenSlots.FILL_ORDER[i];
            }
        }
        return null;
    }

    public static boolean hasFuel(Furniture furniture) {
        for (OvenSlots.WoodStage stage : getStages(furniture)) {
            if (stage == OvenSlots.WoodStage.FRESH || stage == OvenSlots.WoodStage.BURNT) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldShowFire(Furniture furniture) {
        return hasHeat(furniture);
    }

    public static boolean hasHeat(Furniture furniture) {
        return isLit(furniture) && hasFuel(furniture);
    }

    /**
     * Highest layer that still has wood; lower layers do not burn until upper layers are empty.
     */
    public static String[] getActiveBurnLayer(Furniture furniture) {
        for (String[] layer : OvenSlots.BURN_LAYERS) {
            for (String slotId : layer) {
                if (getStage(furniture, slotId) != OvenSlots.WoodStage.EMPTY) {
                    return layer;
                }
            }
        }
        return null;
    }

    public static void clear(Furniture furniture) {
        furniture.getVariables().remove(VAR_LIT);
        furniture.getVariables().remove(VAR_STAGES);
    }

    private static void saveStages(Furniture furniture, OvenSlots.WoodStage[] stages) {
        StringBuilder encoded = new StringBuilder(SLOT_COUNT);
        for (OvenSlots.WoodStage stage : stages) {
            encoded.append(stage.code());
        }
        furniture.getVariables().put(VAR_STAGES, encoded.toString());
    }

    private static OvenSlots.WoodStage[] emptyStages() {
        OvenSlots.WoodStage[] stages = new OvenSlots.WoodStage[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            stages[i] = OvenSlots.WoodStage.EMPTY;
        }
        return stages;
    }

    private static int slotIndex(String slotId) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (OvenSlots.FILL_ORDER[i].equals(slotId)) {
                return i;
            }
        }
        return -1;
    }
}
