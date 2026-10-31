package net.tfminecraft.cooking.pot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/**
 * A pot.yml mash (mashed potatoes): exactly these ingredients go into an empty pot, without water or heat, plus any of
 * its extras once each; the masher mashes them and the ladle serves them out. Paths are Cooking food paths.
 */
public final class MashRecipe {
    /** An optional addition: the food it takes, the track it gives the dish and what the player gets back for it. */
    public record Extra(String id, String path, String track, String returns) {}

    private final String id;
    private final List<String> ingredients = new ArrayList<>();
    private final List<Extra> extras = new ArrayList<>();
    private final int servings;
    private final String output;
    private final String display;
    private final String hint;

    public MashRecipe(String id, ConfigurationSection config) {
        this.id = id;
        ConfigurationSection lines = config.getConfigurationSection("ingredients");
        if (lines != null) {
            for (String key : lines.getKeys(false)) {
                ConfigurationSection line = lines.getConfigurationSection(key);
                if (line == null) continue;
                for (int i = Math.max(1, line.getInt("amount", 1)); i > 0; i--) ingredients.add(line.getString("path", ""));
            }
        }
        ConfigurationSection added = config.getConfigurationSection("extras");
        if (added != null) {
            for (String key : added.getKeys(false)) {
                ConfigurationSection extra = added.getConfigurationSection(key);
                if (extra == null) continue;
                extras.add(new Extra(key, extra.getString("path", ""), extra.getString("track", ""), extra.getString("returns", "")));
            }
        }
        this.servings = Math.max(1, config.getInt("servings", 2));
        this.output = config.getString("output", "");
        this.display = config.getString("display", "");
        this.hint = config.getString("hint", "the ingredients pot.yml lists");
    }

    public String getId() {
        return id;
    }

    /** How many ladles one pot serves; the ingredients' food is split between them. */
    public int getServings() {
        return servings;
    }

    /** The dish's food line (mash(type=mashed_potatoes;...)). */
    public String getOutput() {
        return output;
    }

    /** The item the mashed pot shows in its liquid slot. */
    public String getDisplay() {
        return display;
    }

    /** What the pot needs, for the player told it can't be mashed yet ("4 boiled cut potatoes and a butter"). */
    public String getHint() {
        return hint;
    }

    public List<Extra> getExtras() {
        return extras;
    }

    /** True when the pot's ingredients and extras could all be this mash's. */
    public boolean holds(Collection<FoodItem> present, Collection<String> extrasIn) {
        if (unmatched(present) == null) return false;
        for (String extra : extrasIn) {
            if (extras.stream().noneMatch(e -> e.id().equals(extra))) return false;
        }
        return true;
    }

    /** True when this food is one of the ingredients the pot still needs. */
    public boolean takesIngredient(FoodItem candidate, Collection<FoodItem> present) {
        List<String> open = unmatched(present);
        if (open == null) return false;
        for (String path : open) {
            if (CookingPathHandler.matches(candidate, path)) return true;
        }
        return false;
    }

    /** The extra this food is, or null. */
    public Extra extraFor(FoodItem candidate) {
        for (Extra extra : extras) {
            if (CookingPathHandler.matches(candidate, extra.path())) return extra;
        }
        return null;
    }

    /** The pot holds exactly the ingredients, each once per line. */
    public boolean complete(Collection<FoodItem> present) {
        List<String> open = unmatched(present);
        return open != null && open.isEmpty();
    }

    /** True for a food that goes in this mash as an ingredient or an extra. */
    public boolean uses(FoodItem food) {
        return extraFor(food) != null || ingredients.stream().anyMatch(path -> CookingPathHandler.matches(food, path));
    }

    /** The ingredient lines nothing in the pot fills yet, or null when something in it isn't one of them. */
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
