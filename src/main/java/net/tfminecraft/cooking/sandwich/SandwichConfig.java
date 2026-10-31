package net.tfminecraft.cooking.sandwich;

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
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/** sandwiches.yml: where sandwiches are built, what they make, the layers they take and the kinds they make. */
public final class SandwichConfig {
    private static Set<String> furniture = Set.of();
    private static int maxFillings = 6;
    private static String output = "meal(type=sandwich;origin=Mixed)";
    private static List<SandwichLayer> layers = List.of();
    private static List<DishBonus> bonuses = List.of();
    private static List<SaladKind> kinds = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Set<String> boards = new HashSet<>();
        for (String id : config.getStringList("furniture")) boards.add(id.toLowerCase(Locale.ROOT));
        List<SandwichLayer> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("layers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection layer = section.getConfigurationSection(id);
                if (layer != null) loaded.add(new SandwichLayer(id, layer));
            }
        }
        furniture = boards;
        maxFillings = Math.max(1, config.getInt("max-fillings", 6));
        output = config.getString("output", "meal(type=sandwich;origin=Mixed)");
        layers = loaded;
        bonuses = DishBonus.load(config.getConfigurationSection("bonuses"));
        List<SaladKind> kinded = new ArrayList<>();
        ConfigurationSection kindSection = config.getConfigurationSection("kinds");
        if (kindSection != null) {
            for (String origin : kindSection.getKeys(false)) {
                ConfigurationSection kind = kindSection.getConfigurationSection(origin);
                if (kind != null) kinded.add(new SaladKind(origin, kind));
            }
        }
        kinds = kinded;
    }

    public static boolean isSandwichFurniture(Furniture f) {
        return f != null && f.getType() != null && furniture.contains(f.getType().getId().toLowerCase(Locale.ROOT));
    }

    /** The layer this food makes, or null when it doesn't go in a sandwich. */
    public static SandwichLayer layerFor(FoodItem food) {
        for (SandwichLayer layer : layers) {
            if (layer.matches(food)) return layer;
        }
        return null;
    }

    /**
     * The first kind (Hamburger, BLT) these fillings make, with the layers they make, or null when none takes them.
     * Kinds work like salads.yml's.
     */
    public static SaladKind kindFor(List<FoodItem> fillings, List<SandwichLayer> made) {
        for (SaladKind kind : kinds) {
            if (kind.matches(fillings, made)) return kind;
        }
        return null;
    }

    public static int maxFillings() {
        return maxFillings;
    }

    /** The tags a sandwich earns from its layers (Hearty, Buttered...). */
    public static List<DishBonus> bonuses() {
        return bonuses;
    }

    /** The finished sandwich's food line (types.yml type, origin, tags). */
    public static String output() {
        return output;
    }
}
