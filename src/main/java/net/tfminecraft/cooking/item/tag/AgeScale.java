package net.tfminecraft.cooking.item.tag;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.FoodLoader;

public final class AgeScale {
    public static final double DEFAULT = 1.0;
    public static final int DAIRY_SCALE = 4;

    private AgeScale() {}

    public static double clamp(double multiplier) {
        return Double.isFinite(multiplier) && multiplier > 0 ? multiplier : DEFAULT;
    }

    public static double forFood(String foodId, String trackId) {
        FoodItem item = FoodLoader.getByString(foodId);
        if (item == null) {
            return DEFAULT;
        }
        return item.resolveAgeMultiplier(trackId);
    }

    public static Scaled apply(int value, long deltaSeconds, double multiplier, double leftover) {
        if (deltaSeconds <= 0) {
            return new Scaled(value, leftover);
        }
        double scaled = leftover + deltaSeconds / clamp(multiplier);
        if (scaled >= (long) Integer.MAX_VALUE - value) {
            return new Scaled(Integer.MAX_VALUE, 0);
        }
        long whole = (long) Math.floor(scaled);
        return new Scaled((int) (value + whole), scaled - whole);
    }

    public static String migrateTrackId(String trackId) {
        if (trackId == null) {
            return null;
        }
        if ("dairy_freshness".equalsIgnoreCase(trackId) || "butter_freshness".equalsIgnoreCase(trackId)) {
            return "freshness";
        }
        return trackId;
    }

    public static int migrateTrackValue(String trackId, int value) {
        if (trackId != null && "dairy_freshness".equalsIgnoreCase(trackId)) {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value) * DAIRY_SCALE);
        }
        return Math.max(0, value);
    }

    public record Scaled(int value, double leftover) {}
}
