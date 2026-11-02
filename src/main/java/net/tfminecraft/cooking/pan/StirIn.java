package net.tfminecraft.cooking.pan;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/**
 * A frying-pan.yml stir-in: a dish taken out of the pan fried (fried rice) stirs in the other fried pieces in the pan
 * that it takes (a fried egg, cut vegetables, meat), at most max of them. Paths are Cooking food paths.
 */
public final class StirIn {
    private final String id;
    private final String dish;
    private final List<String> takes = new ArrayList<>();
    private final int max;

    public StirIn(String id, ConfigurationSection config) {
        this.id = id;
        this.dish = config.getString("dish", "");
        this.takes.addAll(config.getStringList("takes"));
        this.max = Math.max(1, config.getInt("max", 3));
    }

    public String getId() {
        return id;
    }

    /** At most this many pieces go into one dish. */
    public int getMax() {
        return max;
    }

    /** True for the food that stirs the others in (fried rice). */
    public boolean isDish(FoodItem food) {
        return CookingPathHandler.matches(food, dish);
    }

    /** True for a piece this dish stirs in (a fried egg). */
    public boolean takes(FoodItem piece) {
        for (String path : takes) {
            if (CookingPathHandler.matches(piece, path)) return true;
        }
        return false;
    }
}
