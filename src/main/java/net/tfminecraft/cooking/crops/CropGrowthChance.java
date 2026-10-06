package net.tfminecraft.cooking.crops;

import java.util.Random;

public final class CropGrowthChance {

    private CropGrowthChance() {}

    public static double growChance(int fertility, double affection) {
        validateAffection(affection);
        if (fertility <= 0) {
            return 0.0;
        }
        if (fertility >= 100) {
            return 1.0;
        }
        return Math.pow(fertility / 100.0, affection);
    }

    public static boolean rollGrows(int fertility, double affection, Random random) {
        double chance = growChance(fertility, affection);
        if (chance >= 1.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return random.nextDouble() < chance;
    }

    static void validateAffection(double affection) {
        if (!Double.isFinite(affection) || affection <= 0.0 || affection > 1.0) {
            throw new IllegalArgumentException("affection must be in (0, 1], got " + affection);
        }
    }
}
