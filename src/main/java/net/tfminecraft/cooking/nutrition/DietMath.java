package net.tfminecraft.cooking.nutrition;

public final class DietMath {
    private DietMath() {}

    public static int lerpRaw(int currentRaw, double targetNutrition, int actualFoodGain, int maxFood, double stepRate) {
        int current = Math.max(0, currentRaw);
        if (actualFoodGain <= 0 || maxFood <= 0) {
            return current;
        }
        double weight = actualFoodGain / (double) maxFood;
        double delta = (targetNutrition - current) * weight * stepRate;
        if (Math.abs(delta) < 1e-9) {
            return current;
        }
        return Math.max(0, Math.round((float) (current + delta)));
    }

    public static int effectiveDiet(int raw, double penaltyFraction, int maxDiet) {
        double penalty = Math.max(0, Math.min(0.99, penaltyFraction));
        int cap = Math.max(0, maxDiet);
        long scaled = Math.round(Math.max(0, raw) * (1.0 - penalty));
        if (scaled > cap) {
            return cap;
        }
        return (int) scaled;
    }
}
