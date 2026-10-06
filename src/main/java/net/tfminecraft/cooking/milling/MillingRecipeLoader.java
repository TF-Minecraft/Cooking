package net.tfminecraft.cooking.milling;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MillingRecipeLoader {
    public void load(File file) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            MillingRecipeRegistry.load(Map.of());
            return;
        }

        Map<String, MillingRecipe> recipes = new LinkedHashMap<>();
        for (String recipeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(recipeId);
            if (section == null) {
                continue;
            }

            String furnitureId = section.getString("furniture");
            String outputFood = section.getString("output-food");
            if (furnitureId == null || furnitureId.isBlank() || outputFood == null || outputFood.isBlank()) {
                Bukkit.getLogger().warning("[Cooking] milling-recipes entry '" + recipeId + "' missing furniture or output-food");
                continue;
            }

            int inputCount = section.getInt("input-count", 8);
            int durationTicks = section.getInt("duration-ticks", 60);
            if (inputCount <= 0 || durationTicks <= 0) {
                Bukkit.getLogger().warning("[Cooking] milling-recipes entry '" + recipeId + "' requires positive input-count and duration-ticks");
                continue;
            }

            recipes.put(recipeId, new MillingRecipe(
                    recipeId,
                    furnitureId,
                    inputCount,
                    section.getString("input-food"),
                    section.getString("input"),
                    section.getString("vanilla-fallback"),
                    outputFood,
                    section.getInt("revolutions", 4),
                    durationTicks));
        }

        MillingRecipeRegistry.load(recipes);
    }
}
