package net.tfminecraft.cooking.pie;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.sandwich.DishBonus;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/**
 * pies.yml: the tins pies are baked in, the dough that lines them, the fillings they take, the kinds of pie those make,
 * how long they bake and the bonuses they earn.
 */
public final class PieConfig {
    private static Set<String> furniture = Set.of();
    private static String slot = "pie";
    private static int minFillings = 1;
    private static int maxFillings = 3;
    private static String output = "meal(type=pie;origin=Mixed)";
    private static int cookSeconds = 5;
    private static int burnSeconds = 10;
    private static SandwichLayer crust;
    private static List<SandwichLayer> fillings = List.of();
    private static List<SaladKind> kinds = List.of();
    private static List<DishBonus> bonuses = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Set<String> tins = new HashSet<>();
        for (String id : config.getStringList("furniture")) tins.add(id.toLowerCase(Locale.ROOT));
        furniture = tins;
        slot = config.getString("slot", "pie");
        maxFillings = Math.max(1, config.getInt("max-fillings", 3));
        minFillings = Math.max(1, Math.min(maxFillings, config.getInt("min-fillings", 1)));
        output = config.getString("output", "meal(type=pie;origin=Mixed)");
        cookSeconds = Math.max(1, config.getInt("bake.cook-seconds", 5));
        burnSeconds = Math.max(cookSeconds + 1, config.getInt("bake.burn-seconds", 10));
        ConfigurationSection crustSection = config.getConfigurationSection("crust");
        crust = crustSection == null ? null : new SandwichLayer("crust", crustSection);
        List<SandwichLayer> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("fillings");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection filling = section.getConfigurationSection(id);
                if (filling != null) loaded.add(new SandwichLayer(id, filling));
            }
        }
        fillings = loaded;
        List<SaladKind> kindsLoaded = new ArrayList<>();
        ConfigurationSection kindSection = config.getConfigurationSection("kinds");
        if (kindSection != null) {
            for (String origin : kindSection.getKeys(false)) {
                ConfigurationSection kind = kindSection.getConfigurationSection(origin);
                if (kind != null) kindsLoaded.add(new SaladKind(origin, kind));
            }
        }
        kinds = kindsLoaded;
        bonuses = DishBonus.load(config.getConfigurationSection("bonuses"));
    }

    public static boolean isTin(Furniture f) {
        return f != null && f.getType() != null && furniture.contains(f.getType().getId().toLowerCase(Locale.ROOT));
    }

    /** The tin's slot the dough lies in, showing the pie. */
    public static String slot() {
        return slot;
    }

    /** The dough that lines the tin, or null when this food isn't it. */
    public static SandwichLayer crustFor(FoodItem food) {
        return crust != null && crust.matches(food) ? crust : null;
    }

    /** The dough lining the tin, not a filling. */
    public static boolean isCrust(SandwichLayer layer) {
        return layer != null && layer == crust;
    }

    /** The filling this food makes, or null when it doesn't go in a pie. */
    public static SandwichLayer fillingFor(FoodItem food) {
        for (SandwichLayer filling : fillings) {
            if (filling.matches(food)) return filling;
        }
        return null;
    }

    /** The crust or filling this food makes, or null. */
    public static SandwichLayer layerFor(FoodItem food) {
        SandwichLayer layer = crustFor(food);
        return layer != null ? layer : fillingFor(food);
    }

    /** The first kind these fillings make, or null when none takes them. */
    public static SaladKind kindFor(List<FoodItem> foods, List<SandwichLayer> made) {
        for (SaladKind kind : kinds) {
            if (kind.matches(foods, made)) return kind;
        }
        return null;
    }

    /** Fillings a pie needs before it bakes. */
    public static int minFillings() {
        return minFillings;
    }

    public static int maxFillings() {
        return maxFillings;
    }

    /** The whole pie (types.yml type, origin, tags); its food and nutrition are replaced by what's in it. */
    public static String output() {
        return output;
    }

    /** Seconds in a hot oven until the pie is cooked. */
    public static int cookSeconds() {
        return cookSeconds;
    }

    /** Seconds in a hot oven, counted from when it went in, until the pie burns. */
    public static int burnSeconds() {
        return burnSeconds;
    }

    /** The tags a pie earns from its fillings (Hearty...). */
    public static List<DishBonus> bonuses() {
        return bonuses;
    }
}
