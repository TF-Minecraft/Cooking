package net.tfminecraft.cooking.crops;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CropsLoader {

    public void load(File file) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Bukkit.getLogger().warning("[Cooking] Failed to load crops.yml: " + ex.getMessage());
            CropsConfig.apply(Map.of(), Map.of(), Map.of(), true);
            CropPlantingRule.configure(null);
            return;
        }

        CropPlantingRule.configure(config.getConfigurationSection("planting"));
        ConfigurationSection harvest = config.getConfigurationSection("harvest-quality");
        boolean growthGate = true;
        ConfigurationSection growth = config.getConfigurationSection("growth-gate");
        if (growth != null) {
            growthGate = growth.getBoolean("enabled", true);
        }
        CropsConfig.apply(
                starWeights(harvest == null ? null : harvest.getConfigurationSection("rich")),
                starWeights(harvest == null ? null : harvest.getConfigurationSection("poor")),
                parseCrops(config.getConfigurationSection("crops")),
                growthGate);
    }

    private static Map<Integer, Double> starWeights(ConfigurationSection section) {
        Map<Integer, Double> weights = new HashMap<>();
        if (section == null) {
            return weights;
        }
        for (String key : section.getKeys(false)) {
            try {
                int star = Integer.parseInt(key);
                if (star >= 1 && star <= 5) {
                    weights.put(star, section.getDouble(key));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return weights;
    }

    private static Map<String, CropDefinition> parseCrops(ConfigurationSection section) {
        Map<String, CropDefinition> crops = new HashMap<>();
        if (section == null) {
            return crops;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            Material block = null;
            String blockName = entry.getString("block");
            if (blockName != null && !blockName.isBlank()) {
                try {
                    block = Material.valueOf(blockName.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    Bukkit.getLogger().warning("[Cooking] Invalid crop block for " + key + ": " + blockName);
                }
            }
            crops.put(key, new CropDefinition(
                    key,
                    CropsConfig.normalizeSource(entry.getString("source")),
                    entry.getString("seed", ""),
                    clampAffection(entry.getDouble("affection", 0.5)),
                    block));
        }
        return crops;
    }

    private static double clampAffection(double affection) {
        if (affection <= 0.0 || affection > 1.0) {
            return 0.5;
        }
        return affection;
    }
}
