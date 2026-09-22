package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.fishing.CatchMapping;
import net.tfminecraft.cooking.fishing.SeafoodPortions;
import net.tfminecraft.cooking.fishing.SeafoodWholeItems;
import net.tfminecraft.cooking.fishing.SeafoodYield;

class SeafoodWholeItemTest {

    @Test
    void wholeCatchCopiesSizeCutIdLineageAndStaysEdible() {
        FoodItem template = new FoodItem("seafood_whole", "{inherit}", true);
        template.setBaseFood(6);
        template.setBaseNutrition(6);
        FoodItem caught = SeafoodWholeItems.describe(
                template,
                new CatchMapping("tfmc_blue_jellyfish", "Blue Jellyfish", "jellyfish"),
                18,
                4);
        FoodItem copy = new FoodItem(caught);

        assertTrue(copy.isEdible());
        assertEquals(18, copy.getCatchSizeCm());
        assertEquals("jellyfish", copy.getSeafoodCutType());
        assertEquals("tfmc_blue_jellyfish", copy.getCustomFishingId());
        assertEquals("Blue Jellyfish", copy.getOrigin());
        assertEquals("seafood", copy.getCategory());
        assertEquals(4, copy.getQualityMin());
        assertEquals(4, copy.getQualityMax());
        assertEquals(java.util.List.of("Blue Jellyfish"), copy.getLineage().mains());
        assertTrue(copy.getLineage().extras().isEmpty());
        assertEquals(6.0, copy.getBaseFood(), 0.0001);
        assertEquals(6.0, copy.getBaseNutrition(), 0.0001);
    }

    @Test
    void vanillaWholeKeepsDefaultSizeAndLeavesCustomFishingIdEmpty() {
        FoodItem template = new FoodItem("seafood_whole", "{inherit}", false);
        FoodItem caught = SeafoodWholeItems.describe(template, "Cod", "fish", 45, 2, null);
        FoodItem copy = new FoodItem(caught);

        assertTrue(copy.isEdible());
        assertEquals(45, copy.getCatchSizeCm());
        assertEquals("fish", copy.getSeafoodCutType());
        assertEquals("Cod", copy.getOrigin());
        assertNull(copy.getCustomFishingId());
        assertEquals(java.util.List.of("Cod"), copy.getLineage().mains());
    }

    @Test
    void cutPortionKeepsSpeciesQualitySizeAndTypeNutrition() {
        FoodItem template = new FoodItem("seafood_fish_filet", "{inherit} Filet", true);
        template.setBaseFood(6);
        template.setBaseNutrition(6);
        FoodItem whole = new FoodItem("seafood_whole", "{inherit}", false);
        whole.setEdible(false);
        whole.setOrigin("Tuna");
        whole.setLineage(IngredientLineage.ofMain("Tuna"));
        whole.setQualityRange(4, 4);
        whole.setCatchSizeCm(45);
        whole.setSeafoodCutType("fish");
        whole.setCustomFishingId("tfmc_tuna_fish");

        FoodItem portion = SeafoodPortions.describe(template, whole, new SeafoodYield("seafood_fish_filet", 1, 0));
        FoodItem copy = new FoodItem(portion);

        assertTrue(copy.isEdible());
        assertEquals("seafood_fish_filet", copy.getId());
        assertEquals(1, copy.getAmount());
        assertEquals(6.0, copy.getBaseFood(), 0.001);
        assertEquals(6.0, copy.getBaseNutrition(), 0.001);
        assertEquals(4, copy.getQualityMin());
        assertEquals(45, copy.getCatchSizeCm());
        assertEquals("fish", copy.getSeafoodCutType());
        assertEquals("tfmc_tuna_fish", copy.getCustomFishingId());
        assertEquals("Tuna", copy.getOrigin());
        assertEquals(java.util.List.of("Tuna"), copy.getLineage().mains());
    }

    @Test
    void ordinaryFoodStaysEdibleThroughCopy() {
        FoodItem food = new FoodItem("fruit_1", "Apple", true);
        assertTrue(food.isEdible());
        assertTrue(new FoodItem(food).isEdible());
    }
}
