package net.tfminecraft.cooking.quality;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;

public final class CompositionApplier {
    private CompositionApplier() {}

    public static void apply(FoodItem output, CompositionResult result) {
        if (output == null || result == null) {
            return;
        }
        int quality = result.getFinalQuality();
        output.setQualityRange(quality, quality);
        CompositionFreshnessApplier.applyTracks(output, result.getFreshnessTracks());
        IngredientLineage lineage = result.getLineage();
        output.setLineage(lineage == null ? IngredientLineage.empty() : lineage);
    }
}
