package net.tfminecraft.cooking.churn;

import net.tfminecraft.cooking.cup.DairyOrigin;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.IngredientLineageCodec;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.furniture.Furniture;

public final class ButterChurnState {
    public static final String VAR_CHURN_COUNT = "butter.churnCount";
    public static final String VAR_MILK_QUALITY = "butter.milkQuality";
    public static final String VAR_MILK_ORIGIN = "butter.milkOrigin";
    public static final String VAR_DAIRY_FRESHNESS = "butter.dairyFreshness";
    public static final String VAR_FRESHNESS_REMAINDER = "butter.freshnessRemainder";
    public static final String VAR_LAST_UPDATE = "butter.lastUpdate";
    public static final String VAR_HAS_SALT = "butter.hasSalt";
    public static final String VAR_SALT_QUALITY = "butter.saltQuality";
    public static final String VAR_SPICE_ORIGIN = "butter.spiceOrigin";
    public static final String VAR_SPICE_QUALITY = "butter.spiceQuality";
    public static final String VAR_SPICE_FRESHNESS = "butter.spiceFreshness";
    public static final String VAR_MILK_LINEAGE = "butter.milkLineage";
    public static final String VAR_SALT_LINEAGE = "butter.saltLineage";
    public static final String VAR_SPICE_LINEAGE = "butter.spiceLineage";

    private ButterChurnState() {}

    public static int getChurnCount(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_CHURN_COUNT);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public static int getMilkQuality(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_MILK_QUALITY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 1;
    }

    public static String getMilkOrigin(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_MILK_ORIGIN);
        if (value instanceof String origin) {
            return DairyOrigin.orCow(origin);
        }
        return DairyOrigin.COW;
    }

    public static int getDairyFreshness(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_DAIRY_FRESHNESS);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    public static long getLastUpdate(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_LAST_UPDATE);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return System.currentTimeMillis();
    }

    public static boolean hasSalt(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_HAS_SALT);
        return value instanceof Boolean bool && bool;
    }

    public static int getSaltQuality(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_SALT_QUALITY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 1;
    }

    public static boolean hasSpice(Furniture furniture) {
        String origin = getSpiceOrigin(furniture);
        return origin != null && !origin.isBlank();
    }

    public static String getSpiceOrigin(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_SPICE_ORIGIN);
        return value instanceof String origin ? origin : null;
    }

    public static int getSpiceQuality(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_SPICE_QUALITY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 1;
    }

    public static int getSpiceFreshness(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_SPICE_FRESHNESS);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    public static boolean hasExtras(Furniture furniture) {
        return hasSalt(furniture) || hasSpice(furniture);
    }

    public static void setChurnCount(Furniture furniture, int count) {
        furniture.getVariables().put(VAR_CHURN_COUNT, count);
    }

    public static void setMilkSnapshot(Furniture furniture, int quality, int dairyFreshness, String origin) {
        clearExtras(furniture);
        furniture.getVariables().put(VAR_MILK_QUALITY, quality);
        furniture.getVariables().put(VAR_MILK_ORIGIN, DairyOrigin.orCow(origin));
        furniture.getVariables().put(VAR_DAIRY_FRESHNESS, Math.max(0, dairyFreshness));
        furniture.getVariables().remove(VAR_MILK_LINEAGE);
        touchLastUpdate(furniture);
    }

    public static void setMilkLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_MILK_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getMilkLineage(Furniture furniture) {
        return IngredientLineageCodec.decode(readString(furniture, VAR_MILK_LINEAGE));
    }

    public static void setSaltLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_SALT_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getSaltLineage(Furniture furniture) {
        return IngredientLineageCodec.decode(readString(furniture, VAR_SALT_LINEAGE));
    }

    public static void setSpiceLineage(Furniture furniture, IngredientLineage lineage) {
        furniture.getVariables().put(VAR_SPICE_LINEAGE, IngredientLineageCodec.encode(lineage));
    }

    public static IngredientLineage getSpiceLineage(Furniture furniture) {
        return IngredientLineageCodec.decode(readString(furniture, VAR_SPICE_LINEAGE));
    }

    public static void setSalt(Furniture furniture, int quality) {
        furniture.getVariables().put(VAR_HAS_SALT, true);
        furniture.getVariables().put(VAR_SALT_QUALITY, quality);
    }

    public static void setSpice(Furniture furniture, String origin, int quality, int freshness) {
        furniture.getVariables().put(VAR_SPICE_ORIGIN, origin);
        furniture.getVariables().put(VAR_SPICE_QUALITY, quality);
        furniture.getVariables().put(VAR_SPICE_FRESHNESS, Math.max(0, freshness));
    }

    public static void touchLastUpdate(Furniture furniture) {
        furniture.getVariables().put(VAR_LAST_UPDATE, System.currentTimeMillis());
    }

    public static int incrementChurnCount(Furniture furniture) {
        int count = getChurnCount(furniture) + 1;
        setChurnCount(furniture, count);
        return count;
    }

    public static boolean isReady(Furniture furniture, int required) {
        return getChurnCount(furniture) >= required;
    }

    public static void tickAge(Furniture furniture) {
        if (!furniture.hasActiveSlot("input_1")) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = getLastUpdate(furniture);
        long elapsedMs = now - last;
        if (elapsedMs <= 0) {
            return;
        }
        int seconds = (int) (elapsedMs / 1000L);
        if (seconds <= 0) {
            return;
        }
        AgeScale.Scaled scaled = AgeScale.apply(
                getDairyFreshness(furniture),
                seconds,
                AgeScale.forFood("milk_bucket", "freshness"),
                getFreshnessRemainder(furniture));
        furniture.getVariables().put(VAR_DAIRY_FRESHNESS, scaled.value());
        furniture.getVariables().put(VAR_FRESHNESS_REMAINDER, scaled.leftover());
        furniture.getVariables().put(VAR_LAST_UPDATE, now);
    }

    private static double getFreshnessRemainder(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_FRESHNESS_REMAINDER);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    private static String readString(Furniture furniture, String key) {
        Object value = furniture.getVariables().get(key);
        return value instanceof String text ? text : "";
    }

    public static void clearExtras(Furniture furniture) {
        furniture.getVariables().remove(VAR_HAS_SALT);
        furniture.getVariables().remove(VAR_SALT_QUALITY);
        furniture.getVariables().remove(VAR_SPICE_ORIGIN);
        furniture.getVariables().remove(VAR_SPICE_QUALITY);
        furniture.getVariables().remove(VAR_SPICE_FRESHNESS);
        furniture.getVariables().remove(VAR_SALT_LINEAGE);
        furniture.getVariables().remove(VAR_SPICE_LINEAGE);
    }

    public static void clear(Furniture furniture) {
        furniture.getVariables().remove(VAR_CHURN_COUNT);
        furniture.getVariables().remove(VAR_MILK_QUALITY);
        furniture.getVariables().remove(VAR_MILK_ORIGIN);
        furniture.getVariables().remove(VAR_MILK_LINEAGE);
        furniture.getVariables().remove(VAR_DAIRY_FRESHNESS);
        furniture.getVariables().remove(VAR_FRESHNESS_REMAINDER);
        furniture.getVariables().remove(VAR_LAST_UPDATE);
        clearExtras(furniture);
    }
}
