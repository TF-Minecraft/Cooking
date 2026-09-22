package net.tfminecraft.cooking.nutrition;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.cooking.item.IngredientLineage;

public final class VarietyMath {
    private VarietyMath() {}

    public record Result(
            double effectiveIngredients,
            int progressPercent,
            int penaltyPercent,
            double penaltyFraction,
            double divisor
    ) {}

    public static Result evaluate(
            VarietyHistory history,
            double mainWeight,
            double extraWeight,
            int targetIngredients,
            double maxPenaltyPercent
    ) {
        double maxPenalty = Math.max(0, Math.min(99, maxPenaltyPercent)) / 100.0;
        if (history == null || history.isEmpty()) {
            return unmeasured();
        }
        Map<String, Double> weights = weights(history, Math.max(0, mainWeight), Math.max(0, extraWeight));
        double sum = 0;
        double sumSquares = 0;
        for (double weight : weights.values()) {
            if (weight <= 0) {
                continue;
            }
            sum += weight;
            sumSquares += weight * weight;
        }
        if (sum <= 0 || sumSquares <= 0) {
            return unmeasured();
        }
        double effective = (sum * sum) / sumSquares;
        int target = Math.max(2, targetIngredients);
        double progress = (effective - 1.0) / (target - 1.0);
        progress = Math.max(0, Math.min(1, progress));
        double penalty = maxPenalty * (1.0 - progress);
        return new Result(
                effective,
                (int) Math.round(progress * 100.0),
                (int) Math.round(penalty * 100.0),
                penalty,
                1.0 / (1.0 - penalty));
    }

    public static Result unmeasured() {
        return new Result(0, 100, 0, 0, 1);
    }

    private static Map<String, Double> weights(VarietyHistory history, double mainWeight, double extraWeight) {
        Map<String, Double> weights = new HashMap<>();
        for (IngredientLineage meal : history.meals()) {
            if (meal == null) {
                continue;
            }
            for (String origin : meal.mains()) {
                add(weights, origin, mainWeight);
            }
            for (String origin : meal.extras()) {
                add(weights, origin, extraWeight);
            }
        }
        return weights;
    }

    private static void add(Map<String, Double> weights, String origin, double weight) {
        if (origin == null || origin.isBlank() || weight <= 0) {
            return;
        }
        weights.merge(origin.toLowerCase(Locale.ROOT), weight, Double::sum);
    }
}
