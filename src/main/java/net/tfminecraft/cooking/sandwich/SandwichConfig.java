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
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/** sandwiches.yml: where sandwiches are built, what they make and the layers they take. */
public final class SandwichConfig {
    private static Set<String> furniture = Set.of();
    private static int maxFillings = 6;
    private static String output = "meal(type=sandwich;origin=Mixed)";
    private static List<SandwichLayer> layers = List.of();

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

    public static int maxFillings() {
        return maxFillings;
    }

    /** The finished sandwich's food line (types.yml type, origin, tags). */
    public static String output() {
        return output;
    }
}
