package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.carve.CarveCut;
import net.tfminecraft.cooking.carve.CarveSequence;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.sausagemaker.SausageItems;

class PortionRulesTest {

    @Test
    void storedBatchSplitsFoodAndKeepsNutrition() {
        FoodItem roast = new FoodItem("sausage_chain", "Chain", true);
        roast.setBaseFood(90);
        roast.setBaseNutrition(40);
        roast.setCarveState("sausage", 0, 6);
        CarveSequence sequence = chainWithBone();

        int edible = CarvableRoastUtils.countFoodCuts(sequence);
        FoodItem piece = new FoodItem("sausage", "Link", true);
        piece.setBaseFood(CarvableRoastUtils.portionFood(roast.getBaseFood(), edible));
        piece.setBaseNutrition(roast.getBaseNutrition());

        assertEquals(18, piece.getBaseFood());
        assertEquals(40, piece.getBaseNutrition());
        assertEquals(90, CarvableRoastUtils.remainingFood(roast, sequence));
        assertEquals(40, CarvableRoastUtils.getRemainingNutrition(roast));

        roast.setCarveNextIndex(1);
        assertEquals(72, CarvableRoastUtils.remainingFood(roast, sequence));
        assertEquals(40, CarvableRoastUtils.getRemainingNutrition(roast));
    }

    @Test
    void plainRoastFoodFollowsCutsAndNutritionStaysAtBase() throws Exception {
        FoodItem roast = new FoodItem("roast", "Chicken", true);
        setTemplateNutrition(roast, 8);
        roast.setCarveState("poultry", 0, 2);
        CarveSequence sequence = new CarveSequence("poultry", 2, List.of(
                CarveCut.food("meat(type=meat_poultry_leg;origin=Chicken)", 20, 8),
                CarveCut.food("meat(type=meat_poultry;origin=Chicken)", 18, 8)));

        assertFalse(roast.hasBaseOverride());
        assertEquals(38, CarvableRoastUtils.remainingFood(roast, sequence));
        assertEquals(8, CarvableRoastUtils.getRemainingNutrition(roast));

        roast.setCarveNextIndex(1);
        assertEquals(18, CarvableRoastUtils.remainingFood(roast, sequence));
        assertEquals(8, CarvableRoastUtils.getRemainingNutrition(roast));
    }

    @Test
    void slaughterYieldKeepsMeatInsteadOfTheBone() {
        CarveSequence redMeat = redMeatSequence();
        CarvableRoastUtils.EdibleWindow one = CarvableRoastUtils.keepEdibleCuts(redMeat, 1);
        assertEquals(6, one.nextIndex());
        assertEquals(2, one.remaining());
        assertEquals(18, redMeat.sumRemainingFood(one.nextIndex()));
        assertEquals(7, one.nextIndex() + 1);

        CarvableRoastUtils.EdibleWindow full = CarvableRoastUtils.keepEdibleCuts(redMeat, 8);
        assertEquals(0, full.nextIndex());
        assertEquals(8, full.remaining());
        assertEquals(126, redMeat.sumRemainingFood(full.nextIndex()));

        CarveSequence poultry = poultrySequence();
        CarvableRoastUtils.EdibleWindow drumstick = CarvableRoastUtils.keepEdibleCuts(poultry, 1);
        assertEquals(4, drumstick.nextIndex());
        assertEquals(2, drumstick.remaining());
        assertEquals(18, poultry.sumRemainingFood(drumstick.nextIndex()));
        assertTrue(poultry.getCut(drumstick.nextIndex()).isFoodCut());
    }

    @Test
    void soupScoopDividesFoodOnly() {
        FoodItem soup = new FoodItem("soup", "Soup", true);
        soup.setBaseNutrition(8);
        soup.setBaseFood(PotReference.scoopFood(26, 3));

        assertEquals(26.0 / 3, soup.getBaseFood());
        assertEquals(8, soup.getBaseNutrition());
    }

    @Test
    void sausageSumsFoodAndAveragesNutrition() throws Exception {
        FoodItem first = new FoodItem("meat", "Beef", true);
        first.setBaseFood(10);
        first.setBaseNutrition(8);
        FoodItem second = new FoodItem("meat", "Pork", true);
        second.setBaseFood(20);
        second.setBaseNutrition(16);

        FoodItem chain = new FoodItem("sausage_chain", "Chain", true);
        SausageItems.applyBatchTotals(chain, List.of(first, second));
        assertEquals(30, chain.getBaseFood());
        assertEquals(12, chain.getBaseNutrition());

        FoodItem untouched = new FoodItem("sausage_chain", "Chain", true);
        setTemplateNutrition(untouched, 7);
        SausageItems.applyBatchTotals(untouched, List.of());
        assertEquals(7, untouched.getBaseNutrition());
    }

    private static CarveSequence redMeatSequence() {
        return new CarveSequence("red_meat", 8, List.of(
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.food("meat(type=meat_red_meat;origin=Beef)", 18, 8),
                CarveCut.item("v.bone", 4, 0, 0)));
    }

    private static CarveSequence poultrySequence() {
        return new CarveSequence("poultry", 6, List.of(
                CarveCut.food("meat(type=meat_poultry_leg;origin=Chicken)", 20, 8),
                CarveCut.food("meat(type=meat_poultry_leg;origin=Chicken)", 20, 8),
                CarveCut.food("meat(type=meat_poultry;origin=Chicken)", 18, 8),
                CarveCut.food("meat(type=meat_poultry;origin=Chicken)", 18, 8),
                CarveCut.food("meat(type=meat_poultry;origin=Chicken)", 18, 8),
                CarveCut.item("v.bone", 2, 0, 0)));
    }

    private static CarveSequence chainWithBone() {
        return new CarveSequence("sausage", 6, List.of(
                CarveCut.food("meat(type=sausage;origin=Mixed)", 18, 8),
                CarveCut.food("meat(type=sausage;origin=Mixed)", 18, 8),
                CarveCut.food("meat(type=sausage;origin=Mixed)", 18, 8),
                CarveCut.food("meat(type=sausage;origin=Mixed)", 18, 8),
                CarveCut.food("meat(type=sausage;origin=Mixed)", 18, 8),
                CarveCut.item("v.bone", 1, 0, 0)));
    }

    private static void setTemplateNutrition(FoodItem item, double nutrition) throws Exception {
        Field field = FoodItem.class.getDeclaredField("baseNutrition");
        field.setAccessible(true);
        field.set(item, nutrition);
    }
}
