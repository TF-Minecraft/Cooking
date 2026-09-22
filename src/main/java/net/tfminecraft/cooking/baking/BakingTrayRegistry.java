package net.tfminecraft.cooking.baking;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class BakingTrayRegistry {
    private static Map<String, BakingTrayRecipe> byFurnitureId = Map.of();
    private static Map<String, BakingTrayRecipe> byRecipeId = Map.of();

    private BakingTrayRegistry() {}

    static void load(Map<String, BakingTrayRecipe> recipes) {
        Map<String, BakingTrayRecipe> furnitureMap = new HashMap<>();
        Map<String, BakingTrayRecipe> recipeMap = new HashMap<>();
        for (BakingTrayRecipe recipe : recipes.values()) {
            recipeMap.put(recipe.getId().toLowerCase(), recipe);
            furnitureMap.put(recipe.getFurnitureId().toLowerCase(), recipe);
        }
        byRecipeId = recipeMap;
        byFurnitureId = furnitureMap;
    }

    public static BakingTrayRecipe getByFurniture(Furniture furniture) {
        if (furniture == null) {
            return null;
        }
        return byFurnitureId.get(furniture.getId().toLowerCase());
    }

    public static BakingTrayRecipe getById(String recipeId) {
        if (recipeId == null) {
            return null;
        }
        return byRecipeId.get(recipeId.toLowerCase());
    }

    public static boolean isTray(Furniture furniture) {
        return getByFurniture(furniture) != null;
    }

    public static Collection<BakingTrayRecipe> getAll() {
        return byRecipeId.values();
    }
}
