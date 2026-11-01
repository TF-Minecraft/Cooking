package net.tfminecraft.cooking.nutrition;

import java.util.List;

import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.tag.TagEffect;

public final class NutritionService {

    private NutritionService() {}

    public static void tryApplyEat(Player player, FoodItem food) {
        if (player == null || food == null) {
            NutritionLog.append("EAT_SKIP", player, null, "cause=null-input");
            return;
        }
        // The food is eaten whatever it gives in nutrition, so its effects (a hidden poison's too) always apply.
        giveEatEffects(player, food);
        NutritionContext context = NutritionContext.capture(player);
        if (context == null) {
            NutritionLog.append("EAT_SKIP", player, null,
                    "food=" + food.getId() + " cause=no-ready-character");
            return;
        }
        RPCharacter character = context.character();

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
            DietTierService.checkAndNotify(context, variety);
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
        if (!context.save()) return;
        NutritionLog.append("SAVE", player, character, "reason=eat");
        NutritionDisplayService.sync(context, "eat");
        NutritionAttributeBridge.apply(context);
    }

    /** Gives the player the status effects of the food's tag steps (tags.yml effects), hidden tracks included. */
    static void giveEatEffects(Player player, FoodItem food) {
        List<TagEffect> effects = food.getEatEffects();
        if (effects.isEmpty()) return;
        StringBuilder given = new StringBuilder();
        for (TagEffect effect : effects) {
            player.addPotionEffect(effect.toPotionEffect());
            if (given.length() > 0) given.append(',');
            given.append(effect.type().getKey().getKey()).append(':').append(effect.seconds()).append("s:")
                    .append(effect.level());
        }
        NutritionLog.append("EAT_EFFECTS", player, null, "food=" + food.getId() + " effects=" + given);
    }

    private static int applyFoodGain(RPCharacter character, int gained) {
        if (gained <= 0) {
            return 0;
        }
        if (character.getFoodValue() >= NutritionConfig.maxFood()) {
            return 0;
        }

        int priorFood = character.getFoodValue();
        int newValue = (int) Math.min((long) priorFood + gained, NutritionConfig.maxFood());
        character.setFoodValue(newValue);
        return character.getFoodValue() - priorFood;
    }

    static int foodAfterDeath(int current, int respawnFood, boolean inBattle) {
        if (inBattle) {
            return current;
        }
        return NutritionConfig.clampFood(respawnFood);
    }
}
