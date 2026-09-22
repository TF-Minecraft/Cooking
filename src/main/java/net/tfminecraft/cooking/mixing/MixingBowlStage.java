package net.tfminecraft.cooking.mixing;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public enum MixingBowlStage {
    EMPTY,
    HAS_FLOUR,
    HAS_WATER,
    HAS_YEAST,
    DOUGH_READY;

    public String nextIngredientSlot() {
        return switch (this) {
            case EMPTY -> MixingBowlSlots.FLOUR;
            case HAS_FLOUR -> MixingBowlSlots.WATER;
            case HAS_WATER -> MixingBowlSlots.YEAST;
            case HAS_YEAST, DOUGH_READY -> null;
        };
    }

    public MixingBowlStage advance() {
        return switch (this) {
            case EMPTY -> HAS_FLOUR;
            case HAS_FLOUR -> HAS_WATER;
            case HAS_WATER -> HAS_YEAST;
            case HAS_YEAST, DOUGH_READY -> this;
        };
    }

    public static MixingBowlStage fromFurniture(Furniture furniture) {
        if (furniture.hasActiveSlot(MixingBowlSlots.DOUGH)) {
            return DOUGH_READY;
        }
        if (furniture.hasActiveSlot(MixingBowlSlots.YEAST)) {
            return HAS_YEAST;
        }
        if (furniture.hasActiveSlot(MixingBowlSlots.WATER)) {
            return HAS_WATER;
        }
        if (furniture.hasActiveSlot(MixingBowlSlots.FLOUR)) {
            return HAS_FLOUR;
        }
        return EMPTY;
    }
}
