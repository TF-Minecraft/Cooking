package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.quality.QualityConfig;

public class QualityConfigLoader {
    public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        int min = config.getInt("pickup.min", 1);
        int max = config.getInt("pickup.max", 5);

        Set<String> excludes = new HashSet<>();
        for (String entry : config.getStringList("composition.exclude-categories")) {
            if (entry != null && !entry.isBlank()) {
                excludes.add(entry.toLowerCase(Locale.ROOT));
            }
        }

        QualityConfig.apply(min, max, excludes, parseNutritionFromQuality(config));
    }

    private static Map<Integer, Double> parseNutritionFromQuality(FileConfiguration config) {
        Map<Integer, Double> parsed = new HashMap<>();
        if (!config.isConfigurationSection("nutrition-from-quality")) {
            return parsed;
        }
        for (String key : config.getConfigurationSection("nutrition-from-quality").getKeys(false)) {
            try {
                parsed.put(Integer.parseInt(key), config.getDouble("nutrition-from-quality." + key));
            } catch (NumberFormatException ignored) {
            }
        }
        return parsed;
    }
}
