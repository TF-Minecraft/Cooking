package net.tfminecraft.cooking.liquid;

import net.tfminecraft.cooking.cup.DairyOrigin;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class LiquidContainerState {
    public static final String VAR_TYPE = "liquid.type";
    public static final String VAR_BLOCKS = "liquid.blocks";
    public static final String VAR_MILK_QUALITY = "liquid.milkQuality";
    public static final String VAR_MILK_ORIGIN = "liquid.milkOrigin";
    public static final String VAR_DAIRY_FRESHNESS = "liquid.dairyFreshness";
    public static final String VAR_FRESHNESS_REMAINDER = "liquid.freshnessRemainder";
    public static final String VAR_LAST_UPDATE = "liquid.lastUpdate";

    public static final String TYPE_WATER = "water";
    public static final String TYPE_MILK = "milk";
    /** Wine or cider souring into vinegar (condiments.yml). */
    public static final String TYPE_WINE = "wine";
    public static final String VAR_VINEGAR_ORIGIN = "liquid.vinegarOrigin";
    public static final String VAR_VINEGAR_QUALITY = "liquid.vinegarQuality";
    public static final String VAR_SOURED_AT = "liquid.souredAt";

    private LiquidContainerState() {}

    public static String getType(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_TYPE);
        return value instanceof String type ? type : null;
    }

    public static int getBlocks(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_BLOCKS);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
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

    public static boolean isEmpty(Furniture furniture) {
        return getBlocks(furniture) <= 0;
    }

    public static boolean canAccept(Furniture furniture, String type) {
        String current = getType(furniture);
        if (current == null || isEmpty(furniture)) {
            return true;
        }
        return current.equalsIgnoreCase(type);
    }

    public static void setType(Furniture furniture, String type) {
        if (type == null) {
            furniture.getVariables().remove(VAR_TYPE);
        } else {
            furniture.getVariables().put(VAR_TYPE, type);
        }
    }

    public static void setBlocks(Furniture furniture, int blocks) {
        furniture.getVariables().put(VAR_BLOCKS, Math.max(0, blocks));
    }

    public static int addBlocks(Furniture furniture, int amount, int max) {
        int newTotal = Math.min(max, getBlocks(furniture) + amount);
        setBlocks(furniture, newTotal);
        return newTotal;
    }

    public static int removeBlock(Furniture furniture) {
        int blocks = Math.max(0, getBlocks(furniture) - 1);
        setBlocks(furniture, blocks);
        if (blocks <= 0) {
            clear(furniture);
        }
        return blocks;
    }

    public static void setMilkSnapshot(Furniture furniture, int quality, int dairyFreshness, String origin) {
        furniture.getVariables().put(VAR_MILK_QUALITY, quality);
        furniture.getVariables().put(VAR_MILK_ORIGIN, DairyOrigin.orCow(origin));
        furniture.getVariables().put(VAR_DAIRY_FRESHNESS, Math.max(0, dairyFreshness));
        touchLastUpdate(furniture);
    }

    public static void touchLastUpdate(Furniture furniture) {
        furniture.getVariables().put(VAR_LAST_UPDATE, System.currentTimeMillis());
    }

    public static void tickAge(Furniture furniture) {
        tickAge(furniture, System.currentTimeMillis());
    }

    static void tickAge(Furniture furniture, long now) {
        if (!TYPE_MILK.equalsIgnoreCase(getType(furniture)) || isEmpty(furniture)) {
            return;
        }
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

    /** Starts the souring clock again: wine poured in now turns everything in the container to vinegar later. */
    public static void setSouring(Furniture furniture, String origin, int quality, long souredAt) {
        furniture.getVariables().putIfAbsent(VAR_VINEGAR_ORIGIN, origin);
        furniture.getVariables().putIfAbsent(VAR_VINEGAR_QUALITY, quality);
        furniture.getVariables().put(VAR_SOURED_AT, souredAt);
    }

    /** The vinegar the souring wine makes (Wine, Cider), or null. */
    public static String getVinegarOrigin(Furniture furniture) {
        return furniture.getVariables().get(VAR_VINEGAR_ORIGIN) instanceof String origin ? origin : null;
    }

    public static int getVinegarQuality(Furniture furniture) {
        return furniture.getVariables().get(VAR_VINEGAR_QUALITY) instanceof Number quality ? quality.intValue() : 1;
    }

    /** When the wine is vinegar (epoch milliseconds). */
    public static long getSouredAt(Furniture furniture) {
        return furniture.getVariables().get(VAR_SOURED_AT) instanceof Number at ? at.longValue() : 0L;
    }

    public static boolean isSoured(Furniture furniture, long now) {
        return now >= getSouredAt(furniture);
    }

    public static void clear(Furniture furniture) {
        furniture.getVariables().remove(VAR_VINEGAR_ORIGIN);
        furniture.getVariables().remove(VAR_VINEGAR_QUALITY);
        furniture.getVariables().remove(VAR_SOURED_AT);
        furniture.getVariables().remove(VAR_TYPE);
        furniture.getVariables().remove(VAR_BLOCKS);
        furniture.getVariables().remove(VAR_MILK_QUALITY);
        furniture.getVariables().remove(VAR_MILK_ORIGIN);
        furniture.getVariables().remove(VAR_DAIRY_FRESHNESS);
        furniture.getVariables().remove(VAR_FRESHNESS_REMAINDER);
        furniture.getVariables().remove(VAR_LAST_UPDATE);
    }
}
