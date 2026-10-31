package net.tfminecraft.cooking.salad;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.DishBonus;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/** salads.yml: the bowls salads are tossed in, what they take, the kinds they make and the bonuses they earn. */
public final class SaladConfig {
    private static final Pattern TYPE = Pattern.compile("type=([^;)]+)");

    private static Set<String> furniture = Set.of();
    private static int maxIngredients = 5;
    private static int minIngredients = 2;
    private static int maxExtras = 3;
    private static String output = "meal(type=salad;origin=Mixed)";
    private static String outputType = "salad";
    private static List<SandwichLayer> ingredients = List.of();
    private static List<SandwichLayer> extras = List.of();
    private static Set<String> once = Set.of();
    private static Set<String> returnsBottle = Set.of();
    private static List<SaladKind> kinds = List.of();
    private static List<DishBonus> bonuses = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Set<String> bowls = new HashSet<>();
        for (String id : config.getStringList("furniture")) bowls.add(id.toLowerCase(Locale.ROOT));
        furniture = bowls;
        maxIngredients = Math.max(1, config.getInt("max-ingredients", 5));
        minIngredients = Math.max(1, Math.min(maxIngredients, config.getInt("min-ingredients", 2)));
        maxExtras = Math.max(0, config.getInt("max-extras", 3));
        output = config.getString("output", "meal(type=salad;origin=Mixed)");
        Matcher type = TYPE.matcher(output);
        outputType = type.find() ? type.group(1).trim().toLowerCase(Locale.ROOT) : "salad";
        ingredients = layers(config.getConfigurationSection("ingredients"), null, null);
        Set<String> single = new HashSet<>();
        Set<String> bottles = new HashSet<>();
        extras = layers(config.getConfigurationSection("extras"), single, bottles);
        once = single;
        returnsBottle = bottles;
        List<SaladKind> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("kinds");
        if (section != null) {
            for (String origin : section.getKeys(false)) {
                ConfigurationSection kind = section.getConfigurationSection(origin);
                if (kind != null) loaded.add(new SaladKind(origin, kind));
            }
        }
        kinds = loaded;
        bonuses = DishBonus.load(config.getConfigurationSection("bonuses"));
    }

    private static List<SandwichLayer> layers(ConfigurationSection section, Set<String> single, Set<String> bottles) {
        List<SandwichLayer> loaded = new ArrayList<>();
        if (section == null) return loaded;
        for (String id : section.getKeys(false)) {
            ConfigurationSection layer = section.getConfigurationSection(id);
            if (layer == null) continue;
            loaded.add(new SandwichLayer(id, layer));
            if (single != null && layer.getBoolean("once", false)) single.add(id.toLowerCase(Locale.ROOT));
            if (bottles != null && layer.getBoolean("returns-bottle", false)) bottles.add(id.toLowerCase(Locale.ROOT));
        }
        return loaded;
    }

    public static boolean isSaladFurniture(Furniture f) {
        return f != null && f.getType() != null && furniture.contains(f.getType().getId().toLowerCase(Locale.ROOT));
    }

    /** The ingredient layer this food makes (shown in the bowl), or null. */
    public static SandwichLayer ingredientFor(FoodItem food) {
        for (SandwichLayer layer : ingredients) {
            if (layer.matches(food)) return layer;
        }
        return null;
    }

    /** The extra (dressing, seasoning) this food makes, or null. Extras don't show and don't name the salad. */
    public static SandwichLayer extraFor(FoodItem food) {
        for (SandwichLayer layer : extras) {
            if (layer.matches(food)) return layer;
        }
        return null;
    }

    /** The ingredient or extra this food makes, or null. */
    public static SandwichLayer layerFor(FoodItem food) {
        SandwichLayer layer = ingredientFor(food);
        return layer != null ? layer : extraFor(food);
    }

    public static boolean isExtra(SandwichLayer layer) {
        return extras.contains(layer);
    }

    /** Only one food of this extra goes in a salad (one dressing). */
    public static boolean isOnce(SandwichLayer layer) {
        return once.contains(layer.getId().toLowerCase(Locale.ROOT));
    }

    /** This extra comes in a bottle the player keeps (olive oil, vinegar). */
    public static boolean returnsBottle(SandwichLayer layer) {
        return returnsBottle.contains(layer.getId().toLowerCase(Locale.ROOT));
    }

    /** A finished salad: the output's type. */
    public static boolean isSalad(FoodItem food) {
        return food != null && food.getId() != null && food.getId().equalsIgnoreCase(outputType);
    }

    /** The first kind these ingredients make, or null when none takes them. */
    public static SaladKind kindFor(List<FoodItem> foods, List<SandwichLayer> made) {
        for (SaladKind kind : kinds) {
            if (kind.matches(foods, made)) return kind;
        }
        return null;
    }

    public static int maxIngredients() {
        return maxIngredients;
    }

    public static int minIngredients() {
        return minIngredients;
    }

    public static int maxExtras() {
        return maxExtras;
    }

    public static String output() {
        return output;
    }

    public static List<DishBonus> bonuses() {
        return bonuses;
    }
}
