package net.tfminecraft.cooking.glaze;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/** glazes.yml: which roasts can be glazed, and the glazes (honey) with the tags.yml track each gives. */
public final class GlazeConfig {
    private static final Logger LOGGER = Logger.getLogger("Cooking");

    /** A glaze: the foods that are it (Cooking food paths) and the track it puts on the roast. */
    public record Glaze(String id, List<String> items, String track) {
        public boolean matches(FoodItem food) {
            for (String path : items) {
                if (CookingPathHandler.matches(food, path)) return true;
            }
            return false;
        }
    }

    private static List<String> roasts = List.of();
    private static List<Glaze> glazes = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        roasts = List.copyOf(config.getStringList("roasts"));
        List<Glaze> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("glazes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection glaze = section.getConfigurationSection(id);
                if (glaze == null) continue;
                List<String> items = new ArrayList<>(glaze.getStringList("items"));
                String item = glaze.getString("item", "");
                if (!item.isBlank()) items.add(0, item);
                String track = glaze.getString("track", "");
                if (items.isEmpty() || track.isBlank()) {
                    LOGGER.warning("[Cooking] glazes.yml glaze '" + id + "' needs an item and a track; skipped.");
                    continue;
                }
                loaded.add(new Glaze(id, List.copyOf(items), track));
            }
        }
        glazes = List.copyOf(loaded);
    }

    public static List<Glaze> glazes() {
        return glazes;
    }

    /** The glaze this food is (honey), or null. */
    public static Glaze glazeFor(FoodItem food) {
        if (food == null) return null;
        for (Glaze glaze : glazes) {
            if (glaze.matches(food)) return glaze;
        }
        return null;
    }

    /** True for a food glazes.yml roasts lists. */
    public static boolean isRoast(FoodItem food) {
        if (food == null) return false;
        for (String path : roasts) {
            if (CookingPathHandler.matches(food, path)) return true;
        }
        return false;
    }

    /** The glaze already on this food (it has the glaze's track), or null. */
    public static Glaze glazeOn(FoodItem food) {
        if (food == null) return null;
        for (Glaze glaze : glazes) {
            if (food.getTagTrack(glaze.track()) != null) return glaze;
        }
        return null;
    }

    /** Every glaze's track id, for carving to pass on to the cuts. */
    public static List<String> trackIds() {
        List<String> ids = new ArrayList<>();
        for (Glaze glaze : glazes) ids.add(glaze.track());
        return ids;
    }
}
