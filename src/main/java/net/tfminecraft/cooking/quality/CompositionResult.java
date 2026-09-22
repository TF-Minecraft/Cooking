package net.tfminecraft.cooking.quality;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;

public final class CompositionResult {
    private final int baselineQuality;
    private final int finalQuality;
    private final Map<String, Integer> freshnessTracks;
    private final List<FoodItem> mains;
    private final List<FoodItem> extras;
    private final List<FoodItem> neutral;
    private final IngredientLineage lineage;

    public CompositionResult(
            int baselineQuality,
            int finalQuality,
            Map<String, Integer> freshnessTracks,
            List<FoodItem> mains,
            List<FoodItem> extras,
            List<FoodItem> neutral,
            IngredientLineage lineage
    ) {
        this.baselineQuality = baselineQuality;
        this.finalQuality = finalQuality;
        this.freshnessTracks = freshnessTracks == null ? Map.of() : Map.copyOf(freshnessTracks);
        this.mains = mains == null ? List.of() : List.copyOf(mains);
        this.extras = extras == null ? List.of() : List.copyOf(extras);
        this.neutral = neutral == null ? List.of() : List.copyOf(neutral);
        this.lineage = lineage == null ? IngredientLineage.empty() : lineage;
    }

    public int getBaselineQuality() {
        return baselineQuality;
    }

    public int getFinalQuality() {
        return finalQuality;
    }

    public Map<String, Integer> getFreshnessTracks() {
        return freshnessTracks;
    }

    public List<FoodItem> getMains() {
        return mains;
    }

    public List<FoodItem> getExtras() {
        return extras;
    }

    public List<FoodItem> getNeutral() {
        return neutral;
    }

    public IngredientLineage getLineage() {
        return lineage;
    }
}
