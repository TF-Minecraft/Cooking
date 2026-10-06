package net.tfminecraft.cooking.crops;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.tfminecraft.cooking.quality.OriginQualityResolver;

public final class CropHarvestQuality {

    private CropHarvestQuality() {}

    public static double[] weights(int fertility, double affection) {
        double aff = affection;
        if (!Double.isFinite(aff) || aff <= 0.0 || aff > 1.0) {
            aff = 0.5;
        }
        int fert = Math.max(0, Math.min(100, fertility));
        double stress = (1.0 - fert / 100.0) * aff;
        Map<Integer, Double> rich = CropsConfig.harvestRich();
        Map<Integer, Double> poor = CropsConfig.harvestPoor();
        double[] raw = new double[6];
        double total = 0.0;
        for (int star = 1; star <= 5; star++) {
            double interpolated = rich.get(star) * (1.0 - stress) + poor.get(star) * stress;
            raw[star] = Math.max(0.0001, interpolated);
            total += raw[star];
        }
        double[] normalized = new double[6];
        for (int star = 1; star <= 5; star++) {
            normalized[star] = raw[star] / total;
        }
        return normalized;
    }

    public static int roll(int fertility, double affection, Random random) {
        double[] weights = weights(fertility, affection);
        Random rng = random == null ? new Random() : random;
        double draw = rng.nextDouble();
        double acc = 0.0;
        for (int star = 1; star <= 5; star++) {
            acc += weights[star];
            if (draw < acc) {
                return star;
            }
        }
        return 5;
    }

    public static int roll(String cropId, Location location, Player player) {
        CropDefinition crop = CropsConfig.crop(cropId);
        double affection = crop == null ? 0.5 : crop.affection();
        int fertility = CropFertility.at(location);
        int rolled = roll(fertility, affection, ThreadLocalRandom.current());
        return OriginQualityResolver.applyPickupPermissions(player, rolled);
    }
}
