package net.tfminecraft.cooking.mixing;



import java.util.ArrayList;

import java.util.Collections;

import java.util.List;

import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.IngredientLineageCodec;



import net.tfminecraft.interactiblefurniture.furniture.Furniture;



public final class MixingBowlState {

    public static final String VAR_STAGE = "mixing.stage";

    public static final String VAR_MIX_COUNT = "mixing.mixCount";

    public static final String VAR_FLOUR_QUALITY = "mixing.flourQuality";

    public static final String VAR_YEAST_QUALITY = "mixing.yeastQuality";

    public static final String VAR_HAS_SUGAR = "mixing.hasSugar";

    public static final String VAR_SUGAR_QUALITY = "mixing.sugarQuality";

    public static final String VAR_FRUIT_ORIGINS = "mixing.fruitOrigins";

    public static final String VAR_FRUIT_QUALITIES = "mixing.fruitQualities";

    public static final String VAR_FLOUR_FRESHNESS = "mixing.flourFreshness";

    public static final String VAR_YEAST_FRESHNESS = "mixing.yeastFreshness";

    public static final String VAR_SUGAR_FRESHNESS = "mixing.sugarFreshness";

    public static final String VAR_FRUIT_FRESHNESS = "mixing.fruitFreshness";

    public static final String VAR_FLOUR_ORIGIN = "mixing.flourOrigin";

    public static final String VAR_FLOUR_LINEAGE = "mixing.flourLineage";

    public static final String VAR_YEAST_ORIGIN = "mixing.yeastOrigin";

    public static final String VAR_YEAST_LINEAGE = "mixing.yeastLineage";

    public static final String VAR_SUGAR_ORIGIN = "mixing.sugarOrigin";

    public static final String VAR_SUGAR_LINEAGE = "mixing.sugarLineage";

    public static final String VAR_FRUIT_LINEAGE = "mixing.fruitLineage";



    private MixingBowlState() {}



    public static MixingBowlStage getStage(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_STAGE);

        if (value instanceof String stageName) {

            try {

                return MixingBowlStage.valueOf(stageName);

            } catch (IllegalArgumentException ignored) {

            }

        }

        return MixingBowlStage.fromFurniture(furniture);

    }



    public static int getMixCount(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_MIX_COUNT);

        if (value instanceof Number number) {

            return number.intValue();

        }

        return 0;

    }



    public static Integer getFlourQuality(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_FLOUR_QUALITY);

        if (value instanceof Number number) {

            return number.intValue();

        }

        return null;

    }



    public static Integer getYeastQuality(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_YEAST_QUALITY);

        if (value instanceof Number number) {

            return number.intValue();

        }

        return null;

    }



    public static boolean hasSugar(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_HAS_SUGAR);

        if (value instanceof Boolean bool) {

            return bool;

        }

        return false;

    }



    public static Integer getSugarQuality(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_SUGAR_QUALITY);

        if (value instanceof Number number) {

            return number.intValue();

        }

        return null;

    }



    public static List<String> getFruitOrigins(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_FRUIT_ORIGINS);

        if (!(value instanceof String raw) || raw.isBlank()) {

            return Collections.emptyList();

        }

        List<String> origins = new ArrayList<>();

        for (String part : raw.split(":")) {

            if (part != null && !part.isBlank()) {

                origins.add(part);

            }

        }

        return origins;

    }



    public static Integer getFlourFreshness(Furniture furniture) {
        return readInteger(furniture, VAR_FLOUR_FRESHNESS);
    }

    public static Integer getYeastFreshness(Furniture furniture) {
        return readInteger(furniture, VAR_YEAST_FRESHNESS);
    }

    public static Integer getSugarFreshness(Furniture furniture) {
        return readInteger(furniture, VAR_SUGAR_FRESHNESS);
    }

    public static List<Integer> getFruitFreshness(Furniture furniture) {
        return readIntegerList(furniture, VAR_FRUIT_FRESHNESS);
    }

    public static List<Integer> getFruitQualities(Furniture furniture) {

        Object value = furniture.getVariables().get(VAR_FRUIT_QUALITIES);

        if (!(value instanceof String raw) || raw.isBlank()) {

            return Collections.emptyList();

        }

        List<Integer> qualities = new ArrayList<>();

        for (String part : raw.split(":")) {

            if (part == null || part.isBlank()) {

                continue;

            }

            try {

                qualities.add(Integer.parseInt(part));

            } catch (NumberFormatException ignored) {

            }

        }

        return qualities;

    }



    public static void setStage(Furniture furniture, MixingBowlStage stage) {

        furniture.getVariables().put(VAR_STAGE, stage.name());

    }



    public static void setMixCount(Furniture furniture, int count) {

        furniture.getVariables().put(VAR_MIX_COUNT, count);

    }



    public static void setFlourQuality(Furniture furniture, int quality) {
        furniture.getVariables().put(VAR_FLOUR_QUALITY, quality);
    }

    public static void setFlourFreshness(Furniture furniture, int freshness) {
        furniture.getVariables().put(VAR_FLOUR_FRESHNESS, freshness);
    }

    public static void setYeastQuality(Furniture furniture, int quality) {
        furniture.getVariables().put(VAR_YEAST_QUALITY, quality);
    }

    public static void setYeastFreshness(Furniture furniture, int freshness) {
        furniture.getVariables().put(VAR_YEAST_FRESHNESS, freshness);
    }



    public static void setHasSugar(Furniture furniture, boolean hasSugar) {

        furniture.getVariables().put(VAR_HAS_SUGAR, hasSugar);

    }



    public static void setSugarQuality(Furniture furniture, int quality) {
        furniture.getVariables().put(VAR_SUGAR_QUALITY, quality);
    }

    public static void setSugarFreshness(Furniture furniture, int freshness) {
        furniture.getVariables().put(VAR_SUGAR_FRESHNESS, freshness);
    }

    public static void addFruit(Furniture furniture, String origin, int quality, int freshness, IngredientLineage lineage) {
        List<String> origins = new ArrayList<>(getFruitOrigins(furniture));
        List<Integer> qualities = new ArrayList<>(getFruitQualities(furniture));
        List<Integer> freshnessValues = new ArrayList<>(getFruitFreshness(furniture));
        List<String> lineages = new ArrayList<>(readStringList(furniture, VAR_FRUIT_LINEAGE));

        origins.add(origin);
        qualities.add(quality);
        freshnessValues.add(freshness);
        lineages.add(IngredientLineageCodec.encode(lineage));

        furniture.getVariables().put(VAR_FRUIT_ORIGINS, joinColon(origins));
        furniture.getVariables().put(VAR_FRUIT_QUALITIES, joinColonInts(qualities));
        furniture.getVariables().put(VAR_FRUIT_FRESHNESS, joinColonInts(freshnessValues));
        furniture.getVariables().put(VAR_FRUIT_LINEAGE, joinPipe(lineages));
    }

    public static void setFlourOrigin(Furniture furniture, String origin) {
        furniture.getVariables().put(VAR_FLOUR_ORIGIN, origin);
    }

    public static String getFlourOrigin(Furniture furniture) {
        return readString(furniture, VAR_FLOUR_ORIGIN, "Wheat");
    }

    public static void setFlourLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_FLOUR_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getFlourLineage(Furniture furniture) {
        return IngredientLineageCodec.decode(readString(furniture, VAR_FLOUR_LINEAGE, ""));
    }

    public static void setYeastOrigin(Furniture furniture, String origin) {
        furniture.getVariables().put(VAR_YEAST_ORIGIN, origin);
    }

    public static String getYeastOrigin(Furniture furniture) {
        return readString(furniture, VAR_YEAST_ORIGIN, "Yeast");
    }

    public static void setYeastLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_YEAST_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getYeastLineage(Furniture furniture) {
        return IngredientLineageCodec.decode(readString(furniture, VAR_YEAST_LINEAGE, ""));
    }

    public static void setSugarOrigin(Furniture furniture, String origin) {
        furniture.getVariables().put(VAR_SUGAR_ORIGIN, origin);
    }

    public static String getSugarOrigin(Furniture furniture) {
        return readString(furniture, VAR_SUGAR_ORIGIN, "Sugar");
    }

    public static void setSugarLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_SUGAR_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getSugarLineage(Furniture furniture) {
        return IngredientLineageCodec.decode(readString(furniture, VAR_SUGAR_LINEAGE, ""));
    }

    public static List<IngredientLineage> getFruitLineages(Furniture furniture) {
        List<IngredientLineage> lineages = new ArrayList<>();
        for (String encoded : readStringList(furniture, VAR_FRUIT_LINEAGE)) {
            lineages.add(IngredientLineageCodec.decode(encoded));
        }
        return lineages;
    }



    public static void clear(Furniture furniture) {

        furniture.getVariables().remove(VAR_STAGE);

        furniture.getVariables().remove(VAR_MIX_COUNT);

        furniture.getVariables().remove(VAR_FLOUR_QUALITY);

        furniture.getVariables().remove(VAR_YEAST_QUALITY);

        furniture.getVariables().remove(VAR_HAS_SUGAR);

        furniture.getVariables().remove(VAR_SUGAR_QUALITY);

        furniture.getVariables().remove(VAR_FRUIT_ORIGINS);

        furniture.getVariables().remove(VAR_FRUIT_QUALITIES);
        furniture.getVariables().remove(VAR_FLOUR_FRESHNESS);
        furniture.getVariables().remove(VAR_YEAST_FRESHNESS);
        furniture.getVariables().remove(VAR_SUGAR_FRESHNESS);
        furniture.getVariables().remove(VAR_FRUIT_FRESHNESS);
        furniture.getVariables().remove(VAR_FLOUR_ORIGIN);
        furniture.getVariables().remove(VAR_FLOUR_LINEAGE);
        furniture.getVariables().remove(VAR_YEAST_ORIGIN);
        furniture.getVariables().remove(VAR_YEAST_LINEAGE);
        furniture.getVariables().remove(VAR_SUGAR_ORIGIN);
        furniture.getVariables().remove(VAR_SUGAR_LINEAGE);
        furniture.getVariables().remove(VAR_FRUIT_LINEAGE);

    }

    private static Integer readInteger(Furniture furniture, String key) {
        Object value = furniture.getVariables().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static List<Integer> readIntegerList(Furniture furniture, String key) {
        Object value = furniture.getVariables().get(key);
        if (!(value instanceof String raw) || raw.isBlank()) {
            return Collections.emptyList();
        }

        List<Integer> values = new ArrayList<>();
        for (String part : raw.split(":")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private static String readString(Furniture furniture, String key, String fallback) {
        Object value = furniture.getVariables().get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static List<String> readStringList(Furniture furniture, String key) {
        Object value = furniture.getVariables().get(key);
        if (!(value instanceof String raw) || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String part : raw.split("\u001e", -1)) {
            values.add(part);
        }
        return values;
    }

    private static String joinPipe(List<String> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append('\u001e');
            }
            sb.append(value == null ? "" : value);
            first = false;
        }
        return sb.toString();
    }



    private static String joinColon(List<String> values) {

        StringBuilder sb = new StringBuilder();

        boolean first = true;

        for (String value : values) {

            if (!first) sb.append(':');

            sb.append(value);

            first = false;

        }

        return sb.toString();

    }



    private static String joinColonInts(List<Integer> values) {

        StringBuilder sb = new StringBuilder();

        boolean first = true;

        for (Integer value : values) {

            if (!first) sb.append(':');

            sb.append(value);

            first = false;

        }

        return sb.toString();

    }

}


