package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.carve.CarveCut;
import net.tfminecraft.cooking.carve.CarveSequence;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;

class RoastPortionTest {

    @Test
    void redMeatFloorIsOneSteakThenBoneAndStartsWhole() {
        CarveSequence sequence = redMeat();
        FoodItem roast = place(sequence, 1);

        assertEquals(0, roast.getCarveNextIndex());
        assertEquals(2, roast.getCarveRemaining());
        assertEquals(1, CarvableRoastUtils.visualCarveStage(roast, sequence));
        assertEquals(18, CarvableRoastUtils.remainingFood(roast, sequence));
        assertEquals(List.of("meat_red_meat", "v.bone"), carve(roast, sequence));
    }

    @Test
    void poultryFloorIsTwoLegsOneFiletThenBone() {
        CarveSequence sequence = poultry();
        FoodItem roast = place(sequence, 1);

        assertEquals(0, roast.getCarveNextIndex());
        assertEquals(4, roast.getCarveRemaining());
        assertEquals(1, CarvableRoastUtils.visualCarveStage(roast, sequence));
        assertEquals(58, CarvableRoastUtils.remainingFood(roast, sequence));
        assertEquals(List.of(
                "meat_poultry_leg",
                "meat_poultry_leg",
                "meat_poultry",
                "v.bone"), carve(roast, sequence));
    }

    @Test
    void higherGeneticsAddsMeatButStillClosesOnBone() {
        FoodItem beef = place(redMeat(), 4);
        assertEquals(5, beef.getCarveRemaining());
        assertEquals(List.of(
                "meat_red_meat",
                "meat_red_meat",
                "meat_red_meat",
                "meat_red_meat",
                "v.bone"), carve(beef, redMeat()));

        FoodItem chicken = place(poultry(), 4);
        assertEquals(5, chicken.getCarveRemaining());
        assertEquals(List.of(
                "meat_poultry_leg",
                "meat_poultry_leg",
                "meat_poultry",
                "meat_poultry",
                "v.bone"), carve(chicken, poultry()));
    }

    @Test
    void fullSequencesKeepOneStagePerCut() {
        assertStages(redMeat(), 8);
        assertStages(poultry(), 6);
        assertStages(sausage(), 5);
    }

    private static void assertStages(CarveSequence sequence, int cuts) {
        FoodItem roast = new FoodItem("roast", "Roast", true);
        CarvableRoastUtils.RoastPortion portion = CarvableRoastUtils.portion(sequence, cuts);
        roast.setCarveState(sequence.getId(), portion.nextIndex(), portion.remaining());
        for (int taken = 0; taken < cuts; taken++) {
            int expected = sequence.getCut(roast.getCarveNextIndex()).isFoodCut()
                    ? taken + 1
                    : cuts;
            assertEquals(expected, CarvableRoastUtils.visualCarveStage(roast, sequence),
                    sequence.getId() + " after " + taken);
            CarvableRoastUtils.advanceAfterCarve(roast, sequence);
        }
        assertEquals(0, roast.getCarveRemaining());
    }

    private static FoodItem place(CarveSequence sequence, int requestedMeat) {
        FoodItem roast = new FoodItem("roast", "Roast", true);
        CarvableRoastUtils.RoastPortion portion = CarvableRoastUtils.portion(sequence, requestedMeat);
        roast.setCarveState(sequence.getId(), portion.nextIndex(), portion.remaining());
        return roast;
    }

    private static List<String> carve(FoodItem roast, CarveSequence sequence) {
        List<String> yields = new ArrayList<>();
        while (roast.getCarveRemaining() > 0) {
            CarveCut cut = sequence.getCut(roast.getCarveNextIndex());
            yields.add(cut.isFoodCut() ? cut.getOutput() : cut.getItemRef());
            if (yields.size() == 1) {
                assertEquals(1, CarvableRoastUtils.visualCarveStage(roast, sequence));
            }
            if (!cut.isFoodCut()) {
                assertEquals(sequence.getCuts().size(), CarvableRoastUtils.visualCarveStage(roast, sequence));
            }
            CarvableRoastUtils.advanceAfterCarve(roast, sequence);
        }
        return yields;
    }

    private static CarveSequence poultry() {
        return new CarveSequence("poultry", 6, 3, List.of(
                CarveCut.food("meat_poultry_leg", 20, 8),
                CarveCut.food("meat_poultry_leg", 20, 8),
                CarveCut.food("meat_poultry", 18, 8),
                CarveCut.food("meat_poultry", 18, 8),
                CarveCut.food("meat_poultry", 18, 8),
                CarveCut.item("v.bone", 2, 0, 0)));
    }

    private static CarveSequence redMeat() {
        return new CarveSequence("red_meat", 8, 1, List.of(
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.food("meat_red_meat", 18, 8),
                CarveCut.item("v.bone", 4, 0, 0)));
    }

    private static CarveSequence sausage() {
        return new CarveSequence("sausage", 5, 1, List.of(
                CarveCut.food("sausage", 18, 8),
                CarveCut.food("sausage", 18, 8),
                CarveCut.food("sausage", 18, 8),
                CarveCut.food("sausage", 18, 8),
                CarveCut.food("sausage", 18, 8)));
    }
}
