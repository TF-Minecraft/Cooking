package net.tfminecraft.cooking.carve;

import java.util.ArrayList;
import java.util.List;

public class CarveSequence {
    private final String id;
    private final int startRemaining;
    private final int minFoodCuts;
    private final List<CarveCut> cuts = new ArrayList<>();

    public CarveSequence(String id, int startRemaining, List<CarveCut> cuts) {
        this(id, startRemaining, 1, cuts);
    }

    public CarveSequence(String id, int startRemaining, int minFoodCuts, List<CarveCut> cuts) {
        this.id = id;
        this.startRemaining = startRemaining;
        this.minFoodCuts = minFoodCuts;
        this.cuts.addAll(cuts);
    }

    public String getId() {
        return id;
    }

    public int getStartRemaining() {
        return startRemaining;
    }

    /** Meat portions a slaughtered animal of this sequence always keeps before the bone. */
    public int getMinFoodCuts() {
        return minFoodCuts;
    }

    public List<CarveCut> getCuts() {
        return cuts;
    }

    public CarveCut getCut(int index) {
        if (index < 0 || index >= cuts.size()) return null;
        return cuts.get(index);
    }

    public double sumRemainingFood(int fromIndex) {
        double total = 0;
        for (int i = fromIndex; i < cuts.size(); i++) {
            total += cuts.get(i).getFood();
        }
        return total;
    }

    public double sumRemainingNutrition(int fromIndex) {
        double total = 0;
        for (int i = fromIndex; i < cuts.size(); i++) {
            total += cuts.get(i).getNutrition();
        }
        return total;
    }
}
