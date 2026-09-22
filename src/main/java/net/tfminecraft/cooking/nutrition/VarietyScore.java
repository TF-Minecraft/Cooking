package net.tfminecraft.cooking.nutrition;

public record VarietyScore(
        int meals,
        double effectiveIngredients,
        int progressPercent,
        int penaltyPercent,
        double penaltyFraction,
        double divisor,
        String tierId
) {
    public static VarietyScore from(VarietyHistory history, VarietyMath.Result result, String tierId) {
        int meals = history == null ? 0 : history.meals().size();
        return new VarietyScore(
                meals,
                result.effectiveIngredients(),
                result.progressPercent(),
                result.penaltyPercent(),
                result.penaltyFraction(),
                result.divisor(),
                tierId == null ? "terrible" : tierId);
    }
}
