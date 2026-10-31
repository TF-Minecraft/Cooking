package net.tfminecraft.cooking.pot;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/**
 * A pot.yml liquid: a bottled food that fills the soup pot instead of a water bucket (stock), the tags.yml track the
 * soup made from it earns (Rich), the colour it gives the soup and what the player gets back for the bottle.
 */
public record PotLiquid(String id, String input, String track, String colour, String returns) {

    static PotLiquid load(String id, ConfigurationSection config) {
        return new PotLiquid(id, config.getString("input", ""), config.getString("track", ""),
                config.getString("colour", "3d85c6"), config.getString("returns", ""));
    }

    /** True for a food this liquid is poured from. */
    public boolean pours(FoodItem food) {
        return CookingPathHandler.matches(food, input);
    }
}
