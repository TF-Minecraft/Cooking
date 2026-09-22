package net.tfminecraft.cooking.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.item.IngredientLineage;

class VarietyRulesTest {

    @Test
    void historyKeepsTheNewestMealsAndRoundTrips() {
        VarietyHistory history = VarietyHistory.empty();
        for (int i = 0; i < 33; i++) {
            history = history.append(IngredientLineage.ofMain("Origin" + i), 32);
        }
        assertEquals(32, history.meals().size());
        assertEquals(List.of("Origin1"), history.meals().get(0).mains());
        assertEquals(List.of("Origin32"), history.meals().get(31).mains());

        VarietyHistory decoded = VarietyHistoryCodec.decode(VarietyHistoryCodec.encode(history));
        assertEquals(history.meals().get(0).mains(), decoded.meals().get(0).mains());
        assertEquals(history.meals().get(31).mains(), decoded.meals().get(31).mains());
        assertTrue(VarietyHistoryCodec.decode("9").isEmpty());
    }

    @Test
    void penaltyUsesWeightedIngredientsBeforeTheCap() {
        VarietyMath.Result narrow = VarietyMath.evaluate(historyOf("Carrot"), 1, 0.25, 8, 60);
        assertEquals(0, narrow.progressPercent());
        assertEquals(60, narrow.penaltyPercent());
        assertEquals(2.5, narrow.divisor(), 0.0001);
        assertEquals(32, DietMath.effectiveDiet(80, narrow.penaltyFraction(), 40));
        assertEquals(40, DietMath.effectiveDiet(100, narrow.penaltyFraction(), 40));

        VarietyMath.Result broad = VarietyMath.evaluate(historyOf(
                "Carrot", "Potato", "Beef", "Chicken", "Apple", "Tomato", "Onion", "Fish"), 1, 0.25, 8, 60);
        assertEquals(100, broad.progressPercent());
        assertEquals(0, broad.penaltyPercent());
        assertEquals(40, DietMath.effectiveDiet(100, broad.penaltyFraction(), 40));

        VarietyHistory seasoned = VarietyHistory.empty()
                .append(IngredientLineage.ofMain("Carrot").withExtra("Salt"), 32);
        VarietyMath.Result partial = VarietyMath.evaluate(seasoned, 1, 0.25, 8, 60);
        assertEquals(1.4706, partial.effectiveIngredients(), 0.001);
        assertTrue(partial.penaltyPercent() < narrow.penaltyPercent());
        assertTrue(partial.penaltyPercent() > 0);
    }

    @Test
    void emptyHistoryDoesNotPenalizeAndActivationUsesRaw() {
        VarietyMath.Result unmeasured = VarietyMath.evaluate(VarietyHistory.empty(), 1, 0.25, 8, 60);
        assertEquals(0, unmeasured.penaltyPercent());
        assertEquals(40, DietMath.effectiveDiet(80, unmeasured.penaltyFraction(), 40));
        assertEquals(32, VarietyService.effectiveFor(80, historyOf("Carrot")));
        assertEquals(40, VarietyService.effectiveFor(80, VarietyHistory.empty()));
    }

    @Test
    void varietyTiersUseTheDietBands() {
        assertEquals("terrible", NutritionConfig.resolveTierPercent(0).getId());
        assertEquals("terrible", NutritionConfig.resolveTierPercent(9).getId());
        assertEquals("bad", NutritionConfig.resolveTierPercent(10).getId());
        assertEquals("bad", NutritionConfig.resolveTierPercent(24).getId());
        assertEquals("decent", NutritionConfig.resolveTierPercent(25).getId());
        assertEquals("decent", NutritionConfig.resolveTierPercent(44).getId());
        assertEquals("good", NutritionConfig.resolveTierPercent(45).getId());
        assertEquals("good", NutritionConfig.resolveTierPercent(64).getId());
        assertEquals("very_good", NutritionConfig.resolveTierPercent(65).getId());
        assertEquals("very_good", NutritionConfig.resolveTierPercent(84).getId());
        assertEquals("fantastic", NutritionConfig.resolveTierPercent(85).getId());
        assertEquals("fantastic", NutritionConfig.resolveTierPercent(100).getId());
    }

    @Test
    void rawLerpIsNotCapped() {
        assertEquals(80, DietMath.lerpRaw(80, 80, 20, 200, 1));
        assertEquals(25, DietMath.lerpRaw(40, 10, 100, 200, 1));
    }

    private static VarietyHistory historyOf(String... origins) {
        List<IngredientLineage> meals = new ArrayList<>();
        for (String origin : origins) {
            meals.add(IngredientLineage.ofMain(origin));
        }
        return VarietyHistory.of(meals, 32);
    }
}
