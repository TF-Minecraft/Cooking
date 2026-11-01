package net.tfminecraft.cooking.milling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MillingRecipeRegistry {
    private static Map<String, List<MillingRecipe>> byFurnitureId = Map.of();
    private static List<MillingRecipe> all = List.of();

    private MillingRecipeRegistry() {}

    public static void load(Map<String, MillingRecipe> recipes) {
        Map<String, List<MillingRecipe>> byFurniture = new LinkedHashMap<>();
        for (MillingRecipe recipe : recipes.values()) {
            byFurniture.computeIfAbsent(recipe.getFurnitureId().toLowerCase(Locale.ROOT), id -> new ArrayList<>())
                    .add(recipe);
        }
        byFurniture.replaceAll((id, list) -> List.copyOf(list));
        byFurnitureId = Collections.unmodifiableMap(byFurniture);
        all = List.copyOf(recipes.values());
    }

    /** The first recipe of this stone (flour), or null when it has none. */
    public static MillingRecipe getByFurnitureId(String furnitureId) {
        List<MillingRecipe> recipes = getAllForFurniture(furnitureId);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    /** Every recipe of this stone, in milling-recipes.yml order. */
    public static List<MillingRecipe> getAllForFurniture(String furnitureId) {
        if (furnitureId == null) {
            return List.of();
        }
        return byFurnitureId.getOrDefault(furnitureId.toLowerCase(Locale.ROOT), List.of());
    }

    /**
     * The recipe a stone was loaded with (saved by id), or its first recipe when the id is missing or no longer one of
     * its recipes (a stone loaded before recipes were saved).
     */
    public static MillingRecipe getForFurniture(String furnitureId, String recipeId) {
        List<MillingRecipe> recipes = getAllForFurniture(furnitureId);
        for (MillingRecipe recipe : recipes) {
            if (recipe.getId().equals(recipeId)) return recipe;
        }
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    public static Collection<MillingRecipe> getAll() {
        return all;
    }
}
