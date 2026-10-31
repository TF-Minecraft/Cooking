package net.tfminecraft.cooking.sandwich;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemRef;

/**
 * One kind of sandwich layer from sandwiches.yml: the foods it takes, how it lies on the board and the name it adds.
 * Pizzas (pizzas.yml) describe their layers the same way.
 */
public final class SandwichLayer {
    private final String id;
    private final Set<String> types = new HashSet<>();
    private final boolean bread;
    private final String name;
    private final Map<String, String> names = new HashMap<>();
    private final double thickness;
    private final List<String> models;
    private final String sandwichOrigin;

    public SandwichLayer(String id, ConfigurationSection config) {
        this.id = id;
        for (String type : config.getStringList("types")) types.add(type.toLowerCase(Locale.ROOT));
        this.bread = config.getBoolean("bread", false);
        this.name = config.getString("name", "{origin}");
        ConfigurationSection named = config.getConfigurationSection("names");
        if (named != null) {
            for (String origin : named.getKeys(false)) names.put(origin.toLowerCase(Locale.ROOT), named.getString(origin));
        }
        this.thickness = config.getDouble("thickness", 0.5);
        this.models = config.getStringList("models");
        this.sandwichOrigin = config.getString("sandwich-origin");
    }

    public String getId() {
        return id;
    }

    /** A slice of bread: it starts and closes a sandwich instead of filling it. */
    public boolean isBread() {
        return bread;
    }

    /** How high the layer lies, in model pixels (1/16 block). */
    public double getThickness() {
        return thickness;
    }

    /** The origin a sandwich with this layer takes (Jam), which picks its look through types.yml overrides; or null. */
    public String getSandwichOrigin() {
        return sandwichOrigin;
    }

    public boolean matches(FoodItem food) {
        return food != null && food.getId() != null && types.contains(food.getId().toLowerCase(Locale.ROOT));
    }

    /** The name this filling gives the sandwich: Strawberry Jam, or Goat Cheese for a goat's cheese. */
    public String fillerName(FoodItem food) {
        String origin = food.getOrigin() == null ? "" : food.getOrigin();
        String named = names.get(origin.toLowerCase(Locale.ROOT));
        return named != null ? named : name.replace("{origin}", origin).trim();
    }

    /**
     * The layer's look on the board, keeping the food's data. Each models entry is a model, or a tag and a model that
     * applies only while the food has that tag (rotten); the first that exists wins, so later entries are fallbacks.
     * {origin} becomes the origin in lower case with _ for spaces (melon_slice). Null when none exists.
     */
    public ItemStack model(FoodItem food, ItemStack source) {
        return model(food, source, List.of());
    }

    /** As {@link #model(FoodItem, ItemStack)}, with extra tags that count as the food's own (a pizza's bake). */
    public ItemStack model(FoodItem food, ItemStack source, List<String> extraTags) {
        return model(food, source, extraTags, List.of());
    }

    /**
     * As {@link #model(FoodItem, ItemStack, List)}, trying each model with these suffixes first, in order (a pizza's
     * third topping on a cut pizza: _t2_left3, _t2, _left3), and then as it is.
     */
    public ItemStack model(FoodItem food, ItemStack source, List<String> extraTags, List<String> suffixes) {
        List<String> tags = new ArrayList<>(food.getCurrentTagIds());
        tags.addAll(extraTags);
        String origin = food.getOrigin() == null ? "none" : food.getOrigin().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (String entry : models) {
            String[] parts = entry.trim().split("\\s+", 2);
            if (parts.length == 2 && !tags.contains(parts[0])) continue;
            String path = parts[parts.length - 1].replace("{origin}", origin);
            for (String suffix : suffixes) {
                ItemStack shown = existing(ItemRef.apply(path + suffix, source));
                if (shown != null) return shown;
            }
            ItemStack shown = existing(ItemRef.apply(path, source));
            if (shown != null) return shown;
        }
        return null;
    }

    // TLibs hands out dirt for an ItemsAdder item that doesn't exist.
    private static ItemStack existing(ItemStack shown) {
        return shown != null && !shown.getType().isAir() && shown.getType() != Material.DIRT ? shown : null;
    }
}
