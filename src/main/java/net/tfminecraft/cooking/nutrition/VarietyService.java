package net.tfminecraft.cooking.nutrition;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.rpcharacters.objects.RPCharacter;

public final class VarietyService {
    private VarietyService() {}

    public static VarietyScore current(Player player) {
        return score(load(player));
    }

    public static VarietyScore score(VarietyHistory history) {
        VarietyMath.Result result = VarietyMath.evaluate(
                history,
                NutritionConfig.varietyMainWeight(),
                NutritionConfig.varietyExtraWeight(),
                NutritionConfig.varietyTargetIngredients(),
                NutritionConfig.varietyMaxPenaltyPercent());
        return VarietyScore.from(history, result, NutritionConfig.resolveTierPercent(result.progressPercent()).getId());
    }

    public static int effectiveFor(int rawDiet, VarietyHistory history) {
        VarietyScore score = NutritionConfig.varietyEnabled() ? score(history) : score(VarietyHistory.empty());
        return DietMath.effectiveDiet(rawDiet, score.penaltyFraction(), NutritionConfig.maxDiet());
    }

    public static void recordMeal(Player player, IngredientLineage meal) {
        if (!NutritionConfig.varietyEnabled() || player == null) {
            return;
        }
        VarietyHistory next = load(player).append(meal, NutritionConfig.varietyHistoryMeals());
        player.getPersistentDataContainer().set(
                Keys.VARIETY_HISTORY,
                PersistentDataType.STRING,
                VarietyHistoryCodec.encode(next));
    }

    public static void applyEffective(Player player, RPCharacter character) {
        if (character == null) {
            return;
        }
        VarietyHistory history = NutritionConfig.varietyEnabled() ? load(player) : VarietyHistory.empty();
        character.setDietScore(effectiveFor(character.getRawDietScore(), history));
    }

    private static VarietyHistory load(Player player) {
        if (player == null) {
            return VarietyHistory.empty();
        }
        String raw = player.getPersistentDataContainer().get(Keys.VARIETY_HISTORY, PersistentDataType.STRING);
        return VarietyHistoryCodec.decode(raw);
    }
}
