package net.tfminecraft.cooking.milling;

import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.IngredientLineageCodec;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.furniture.Furniture;

public final class MillingStoneState {
    public static final String VAR_STAGE = "milling.stage";
    public static final String VAR_OUTPUT_QUALITY = "milling.outputQuality";
    public static final String VAR_LINEAGE = "milling.lineage";

    private MillingStoneState() {}

    public static MillingStoneStage getStage(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_STAGE);
        if (value instanceof String stageName) {
            try {
                return MillingStoneStage.valueOf(stageName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return MillingStoneStage.fromFurniture(furniture);
    }

    public static int getOutputQuality(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_OUTPUT_QUALITY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return QualityConfig.getPickupMin();
    }

    public static void setStage(Furniture furniture, MillingStoneStage stage) {
        furniture.getVariables().put(VAR_STAGE, stage.name());
    }

    public static void setOutputQuality(Furniture furniture, int quality) {
        furniture.getVariables().put(VAR_OUTPUT_QUALITY, quality);
    }

    public static void setLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getLineage(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_LINEAGE);
        if (value instanceof String encoded) {
            return IngredientLineageCodec.decode(encoded);
        }
        return IngredientLineage.empty();
    }

    public static void clear(Furniture furniture) {
        furniture.getVariables().remove(VAR_STAGE);
        furniture.getVariables().remove(VAR_OUTPUT_QUALITY);
        furniture.getVariables().remove(VAR_LINEAGE);
    }
}
