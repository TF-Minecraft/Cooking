package net.tfminecraft.cooking.milling;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public enum MillingStoneStage {
    EMPTY,
    LOADED,
    READY;

    public static MillingStoneStage fromFurniture(Furniture furniture) {
        if (furniture.hasActiveSlot(MillingStoneSlots.FLOUR)) {
            return READY;
        }
        for (String slotId : MillingStoneSlots.WHEAT) {
            if (furniture.hasActiveSlot(slotId)) {
                return LOADED;
            }
        }
        return EMPTY;
    }
}
