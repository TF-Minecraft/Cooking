package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

class BowlIngredientLayoutTest {

    @Test
    void ingredientsSitAroundTheBowlInsteadOfTheCenter() {
        DisplayData[] placed = new DisplayData[5];
        for (int i = 1; i <= 5; i++) {
            placed[i - 1] = BowlIngredientLayout.offsetFor("input_" + i);
            double radius = Math.hypot(placed[i - 1].getxPos(), placed[i - 1].getzPos());
            assertEquals(BowlIngredientLayout.RADIUS, radius, 0.001);
        }
        for (int i = 0; i < placed.length; i++) {
            for (int j = i + 1; j < placed.length; j++) {
                double apart = Math.hypot(
                        placed[i].getxPos() - placed[j].getxPos(),
                        placed[i].getzPos() - placed[j].getzPos());
                assertTrue(apart > 0.1, "input slots overlap at the center");
            }
        }
    }

    @Test
    void soupLiquidAndHiddenFoodStayCentered() {
        assertNull(BowlIngredientLayout.offsetFor("liquid"));
        assertNull(BowlIngredientLayout.offsetFor("food_item"));
        assertNull(BowlIngredientLayout.offsetFor("input_6"));
    }
}
