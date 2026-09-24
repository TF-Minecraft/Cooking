package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.carve.CarveCut;
import net.tfminecraft.cooking.carve.CarveSequence;

public class CarveSequenceLoader {

    private static final Map<String, CarveSequence> sequences = new HashMap<>();

    public static CarveSequence get(String id) {
        if (id == null) return null;
        return sequences.get(id.toLowerCase());
    }

    public void load(File file) {
        sequences.clear();
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            return;
        }

        for (String key : config.getKeys(false)) {
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec == null) continue;

            int startRemaining = sec.getInt("start-remaining", 0);
            int minFoodCuts = sec.contains("min-food-cuts")
                    ? sec.getInt("min-food-cuts")
                    : defaultMinFoodCuts(key);
            List<CarveCut> cuts = new ArrayList<>();

            for (Map<?, ?> map : sec.getMapList("cuts")) {
                double food = map.containsKey("food")
                        ? ((Number) map.get("food")).doubleValue() : 1.0;
                double nutrition = map.containsKey("nutrition")
                        ? ((Number) map.get("nutrition")).doubleValue() : 1.0;

                Object outputObj = map.get("output");
                Object itemObj = map.get("item");

                if (outputObj != null && itemObj != null) {
                    Cooking.plugin.getLogger().log(Level.WARNING,
                            "Carve sequence '" + key + "' has a cut with both output and item; skipping.");
                    continue;
                }

                if (outputObj != null) {
                    cuts.add(CarveCut.food(String.valueOf(outputObj), food, nutrition));
                } else if (itemObj != null) {
                    int amount = map.containsKey("amount")
                            ? ((Number) map.get("amount")).intValue() : 1;
                    cuts.add(CarveCut.item(String.valueOf(itemObj), amount, food, nutrition));
                } else {
                    Cooking.plugin.getLogger().log(Level.WARNING,
                            "Carve sequence '" + key + "' has a cut with no output or item; skipping.");
                }
            }

            sequences.put(key.toLowerCase(), new CarveSequence(key, startRemaining, minFoodCuts, cuts));
        }
    }

    /** Used when an older carve-sequences.yml has no min-food-cuts key. */
    static int defaultMinFoodCuts(String key) {
        if ("poultry".equalsIgnoreCase(key)) {
            return 3;
        }
        return 1;
    }
}
