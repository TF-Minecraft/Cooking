package net.tfminecraft.cooking.nutrition;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.cooking.item.IngredientLineage;

public final class VarietyHistory {
    private final List<IngredientLineage> meals;

    private VarietyHistory(List<IngredientLineage> meals) {
        this.meals = List.copyOf(meals);
    }

    public static VarietyHistory empty() {
        return new VarietyHistory(List.of());
    }

    public static VarietyHistory of(List<IngredientLineage> meals, int limit) {
        return empty().appendAll(meals, limit);
    }

    public List<IngredientLineage> meals() {
        return meals;
    }

    public boolean isEmpty() {
        return meals.isEmpty();
    }

    public VarietyHistory append(IngredientLineage meal, int limit) {
        List<IngredientLineage> next = new ArrayList<>(meals);
        next.add(meal == null ? IngredientLineage.empty() : meal);
        int cap = Math.max(1, limit);
        while (next.size() > cap) {
            next.remove(0);
        }
        return new VarietyHistory(next);
    }

    private VarietyHistory appendAll(List<IngredientLineage> incoming, int limit) {
        VarietyHistory result = this;
        if (incoming == null) {
            return result;
        }
        for (IngredientLineage meal : incoming) {
            result = result.append(meal, limit);
        }
        return result;
    }
}
