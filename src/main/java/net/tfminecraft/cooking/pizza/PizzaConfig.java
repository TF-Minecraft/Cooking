package net.tfminecraft.cooking.pizza;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/**
 * pizzas.yml: the peels pizzas are built on, the slices they're cut into, how long they bake and the layers they take.
 */
public final class PizzaConfig {
    private static Set<String> furniture = Set.of();
    private static int maxToppings = 6;
    private static String output = "meal(type=pizza_slice;origin=Mixed)";
    private static int slices = 4;
    private static int cookSeconds = 20;
    private static int burnSeconds = 60;
    private static SandwichLayer base;
    private static SandwichLayer sauce;
    private static List<SandwichLayer> toppings = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Set<String> peels = new HashSet<>();
        for (String id : config.getStringList("furniture")) peels.add(id.toLowerCase(Locale.ROOT));
        List<SandwichLayer> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("toppings");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection topping = section.getConfigurationSection(id);
                if (topping != null) loaded.add(new SandwichLayer(id, topping));
            }
        }
        furniture = peels;
        maxToppings = Math.max(1, config.getInt("max-toppings", 6));
        output = config.getString("output", "meal(type=pizza_slice;origin=Mixed)");
        slices = Math.max(1, config.getInt("slices", 4));
        cookSeconds = Math.max(1, config.getInt("bake.cook-seconds", 20));
        burnSeconds = Math.max(cookSeconds + 1, config.getInt("bake.burn-seconds", 60));
        base = layer(config, "base");
        sauce = layer(config, "sauce");
        toppings = loaded;
    }

    private static SandwichLayer layer(YamlConfiguration config, String id) {
        ConfigurationSection section = config.getConfigurationSection(id);
        return section == null ? null : new SandwichLayer(id, section);
    }

    public static boolean isPeel(Furniture f) {
        return f != null && f.getType() != null && furniture.contains(f.getType().getId().toLowerCase(Locale.ROOT));
    }

    /** The layer this food makes (the base, the sauce or a topping), or null when it doesn't go on a pizza. */
    public static SandwichLayer layerFor(FoodItem food) {
        if (base != null && base.matches(food)) return base;
        if (sauce != null && sauce.matches(food)) return sauce;
        for (SandwichLayer topping : toppings) {
            if (topping.matches(food)) return topping;
        }
        return null;
    }

    /** The dough every pizza starts with. */
    public static boolean isBase(SandwichLayer layer) {
        return layer != null && layer == base;
    }

    /** The one spread that goes straight on the dough. */
    public static boolean isSauce(SandwichLayer layer) {
        return layer != null && layer == sauce;
    }

    public static int maxToppings() {
        return maxToppings;
    }

    /** The food line of one slice of the finished pizza (types.yml type, origin, tags). */
    public static String output() {
        return output;
    }

    /** Slices a baked pizza is cut into; 1 takes it off the peel whole. */
    public static int slices() {
        return slices;
    }

    /** Seconds in a hot oven until the pizza is cooked. */
    public static int cookSeconds() {
        return cookSeconds;
    }

    /** Seconds in a hot oven, counted from when it went in, until the pizza burns. */
    public static int burnSeconds() {
        return burnSeconds;
    }
}
