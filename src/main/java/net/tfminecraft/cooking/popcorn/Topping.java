package net.tfminecraft.cooking.popcorn;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/**
 * A topping put in a popcorn machine (popcorn-machines.yml): every piece it pops gets the tags.yml track. Any of its
 * paths will do (butter from the churn is dairy, butter picked up is an ingredient). A topping with a look (a step of
 * the machine's look track) colours the popcorn, when it's the first such topping put in.
 */
public record Topping(String id, List<String> paths, String track, String name, String look) {
    /** True when this food is the topping. */
    public boolean matches(FoodItem food) {
        for (String path : paths) {
            if (CookingPathHandler.matches(food, path)) return true;
        }
        return false;
    }

    /** The toppings in a toppings section: item (or items, a list), track (default the key), name and look. */
    static List<Topping> load(ConfigurationSection section) {
        List<Topping> toppings = new ArrayList<>();
        if (section == null) return toppings;
        for (String key : section.getKeys(false)) {
            ConfigurationSection topping = section.getConfigurationSection(key);
            if (topping == null) continue;
            List<String> paths = topping.getStringList("items");
            if (paths.isEmpty()) paths = List.of(topping.getString("item", ""));
            toppings.add(new Topping(key, paths, topping.getString("track", key), topping.getString("name", key),
                    topping.getString("look")));
        }
        return toppings;
    }
}
