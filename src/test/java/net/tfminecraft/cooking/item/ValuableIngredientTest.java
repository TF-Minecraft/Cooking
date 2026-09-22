package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.utils.StationAddonRules;

class ValuableIngredientTest {

    @Test
    void detectsValuableIngredientAndIgnoresVanilla() throws Exception {
        FoodItem tomato = new FoodItem("vegetable_cut_2", "Tomato", true);
        setValuable(tomato, true);
        FoodItem carrot = new FoodItem("vegetable_cut", "Carrot", true);

        assertTrue(StationAddonRules.hasValuable(Map.of("a", tomato)));
        assertFalse(StationAddonRules.hasValuable(Map.of("a", carrot)));
        assertTrue(StationAddonRules.hasValuable(Map.of("a", carrot, "b", tomato)));
        assertFalse(StationAddonRules.hasValuable(Map.of()));
    }

    @Test
    void soupScoopFoodIsUnchangedByValuableFlag() {
        assertEquals(26.0 / 3, PotReference.scoopFood(26, 3));
    }

    private static void setValuable(FoodItem item, boolean valuable) throws Exception {
        Field field = FoodItem.class.getDeclaredField("valuable");
        field.setAccessible(true);
        field.set(item, valuable);
    }
}
