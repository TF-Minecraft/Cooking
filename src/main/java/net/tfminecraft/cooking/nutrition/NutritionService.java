package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;

public final class NutritionService {

    private NutritionService() {}

    public static void tryApplyEat(Player player, FoodItem food) {
        if (player == null || food == null) {
            NutritionLog.append("EAT_SKIP", player, null, "cause=null-input");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            NutritionLog.append("EAT_SKIP", player, null,
                    "food=" + food.getId() + " cause=rpcharacters-unavailable");
            return;
        }

        RPCharacter character = RPCharacters.getActiveCharacter(player);
        if (character == null) {
            NutritionLog.append("EAT_SKIP", player, null,
                    "food=" + food.getId() + " cause=no-active-character");
            return;
        }

        int gained = (int) Math.ceil(food.getFinalFood());
        int foodBefore = character.getFoodValue();
        int dietBefore = character.getDietScore();
        int rawBefore = character.getRawDietScore();
        int actualGain = applyFoodGain(character, gained);
        if (actualGain <= 0) {
            NutritionLog.append("EAT_SKIP", player, character,
                    "food=" + food.getId()
                    + " calculatedGain=" + gained
                    + " actualGain=0"
                    + " foodBefore=" + foodBefore
                    + " cause=no-gain");
            return;
        }

        int rawAfter = DietMath.lerpRaw(
                rawBefore,
                food.getFinalNutrition(),
                actualGain,
                NutritionConfig.maxFood(),
                NutritionConfig.lerpStepRate());
        character.setRawDietScore(rawAfter);
        VarietyService.recordMeal(player, IngredientLineage.forEat(food));
        VarietyService.applyEffective(player, character);
        VarietyScore variety = VarietyService.current(player);
        boolean dietChanged = character.getDietScore() != dietBefore;
        if (dietChanged) {
            DietTierService.checkAndNotify(player, character, variety);
        }

        NutritionLog.append("EAT", player, character,
                "food=" + food.getId()
                + " calculatedGain=" + gained
                + " actualGain=" + actualGain
                + " foodBefore=" + foodBefore
                + " foodAfter=" + character.getFoodValue()
                + " foodNutrition=" + food.getFinalNutrition()
                + " rawBefore=" + rawBefore
                + " rawAfter=" + character.getRawDietScore()
                + " dietBefore=" + dietBefore
                + " dietAfter=" + character.getDietScore()
                + " dietChanged=" + dietChanged
                + " varietyMeals=" + variety.meals()
                + " varietyIngredients=" + variety.effectiveIngredients()
                + " varietyTier=" + variety.tierId()
                + " varietyProgress=" + variety.progressPercent()
                + " varietyDivisor=" + variety.divisor()
                + " varietyPenalty=" + variety.penaltyPercent());
        RPCharacters.getPlayerManager().savePlayer(player);
        NutritionLog.append("SAVE", player, character, "reason=eat");
        NutritionDisplayService.sync(player, character, "eat");
        NutritionAttributeBridge.apply(player, character);
    }

    private static int applyFoodGain(RPCharacter character, int gained) {
        if (gained <= 0) {
            return 0;
        }
        if (character.getFoodValue() >= NutritionConfig.maxFood()) {
            return 0;
        }

        int priorFood = character.getFoodValue();
        int newValue = Math.min(priorFood + gained, NutritionConfig.maxFood());
        if (newValue == priorFood) {
            return 0;
        }

        character.setFoodValue(newValue);
        return newValue - priorFood;
    }

    static int foodAfterDeath(int current, int respawnFood, boolean inBattle) {
        if (inBattle) {
            return current;
        }
        return NutritionConfig.clampFood(respawnFood);
    }
}
