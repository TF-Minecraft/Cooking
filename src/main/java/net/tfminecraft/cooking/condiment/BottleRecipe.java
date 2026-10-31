package net.tfminecraft.cooking.condiment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/**
 * A saucepan bottling from condiments.yml: what the saucepan holds (its liquid and exactly these ingredients) and the
 * food an empty glass bottle takes out once it has boiled long enough. Paths are Cooking food paths (c.paste(origin=Tomato)).
 */
public final class BottleRecipe {
    private final String id;
    private final String liquid;
    private final List<String> ingredients;
    private final int boilSeconds;
    private final String output;

    public BottleRecipe(String id, ConfigurationSection config) {
        this.id = id;
        this.liquid = config.getString("liquid", "");
        this.ingredients = config.getStringList("ingredients");
        this.boilSeconds = Math.max(0, config.getInt("boil-seconds", 30));
        this.output = config.getString("output", "");
    }

    public String getId() {
        return id;
    }

    public int getBoilSeconds() {
        return boilSeconds;
    }

    /** The bottled food's line (condiment(type=catsup;...)). */
    public String getOutput() {
        return output;
    }

    public boolean usesLiquid(FoodItem liquidFood) {
        return CookingPathHandler.matches(liquidFood, liquid);
    }

    /** True when this food is one of the recipe's ingredients not already in the saucepan. */
    public boolean takes(FoodItem candidate, Collection<FoodItem> present) {
        List<String> open = unmatched(present);
        if (open == null) return false;
        for (String path : open) {
            if (CookingPathHandler.matches(candidate, path)) return true;
        }
        return false;
    }

    /** The saucepan holds this liquid and exactly these ingredients, each once. */
    public boolean matches(FoodItem liquidFood, Collection<FoodItem> present) {
        if (!usesLiquid(liquidFood)) return false;
        List<String> open = unmatched(present);
        return open != null && open.isEmpty();
    }

    /** The ingredient paths no food in the saucepan fills yet, or null when something in it isn't part of the recipe. */
    private List<String> unmatched(Collection<FoodItem> present) {
        List<String> open = new ArrayList<>(ingredients);
        for (FoodItem food : present) {
            String filled = null;
            for (String path : open) {
                if (CookingPathHandler.matches(food, path)) {
                    filled = path;
                    break;
                }
            }
            if (filled == null) return null;
            open.remove(filled);
        }
        return open;
    }
}
