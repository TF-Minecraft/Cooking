package net.tfminecraft.cooking.farming;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class FarmingLoader {

    public void load(File file) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            ex.printStackTrace();
            applyDefaults();
            return;
        }

        ConfigurationSection farming = config.getConfigurationSection("farming");
        if (farming == null) {
            applyDefaults();
            return;
        }

        ConfigurationSection effects = farming.getConfigurationSection("effects");
        double harvestParticles = effects != null ? effects.getDouble("harvest-particle-multiplier", 1.0) : 1.0;
        double replantParticles = effects != null ? effects.getDouble("replant-particle-multiplier", 1.0) : 1.0;
        boolean swingParticle = effects == null || effects.getBoolean("tool-swing-particle", true);

        ConfigurationSection antiTrample = farming.getConfigurationSection("anti-trample");
        boolean antiTrampleEnabled = antiTrample == null || antiTrample.getBoolean("enabled", true);
        boolean trampleByWalking = antiTrample != null && antiTrample.getBoolean("trample-by-walking", false);
        boolean dryEmptyFarmland = antiTrample == null || antiTrample.getBoolean("dry-empty-farmland", true);
        double trampleParticles = antiTrample != null ? antiTrample.getDouble("trample-particle-multiplier", 1.0) : 1.0;
        Set<Material> trampleableCrops = antiTrample != null
                ? parseTrampleableCrops(antiTrample.getStringList("crops"))
                : defaultTrampleableCrops();

        List<FarmingToolDefinition> tools = parseTools(farming.getMapList("tools"));
        Map<Material, FarmingCropDefinition> crops = parseCrops(farming.getMapList("crops"));

        FarmingConfig.apply(
                farming.getBoolean("enabled", true),
                harvestParticles,
                replantParticles,
                swingParticle,
                farming.getInt("replant-delay-min", 10),
                farming.getInt("replant-delay-max", 20),
                farming.getBoolean("only-harvest-mature", true),
                farming.getInt("tool-damage-per-harvest", 1),
                farming.getBoolean("apply-unbreaking", true),
                tools,
                crops,
                antiTrampleEnabled,
                trampleByWalking,
                dryEmptyFarmland,
                trampleParticles,
                trampleableCrops);
    }

    private static List<FarmingToolDefinition> parseTools(List<Map<?, ?>> raw) {
        List<FarmingToolDefinition> tools = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Object pathRaw = entry.get("path");
            if (pathRaw == null || String.valueOf(pathRaw).isBlank()) {
                continue;
            }
            int radius = 0;
            Object radiusRaw = entry.get("radius");
            if (radiusRaw instanceof Number number) {
                radius = number.intValue();
            } else if (radiusRaw != null) {
                try {
                    radius = Integer.parseInt(String.valueOf(radiusRaw));
                } catch (NumberFormatException ignored) {
                    Bukkit.getLogger().warning("[Cooking] Invalid farming tool radius for path " + pathRaw);
                }
            }
            double qualityBonus = 0.0;
            Object bonusRaw = entry.get("quality-bonus-percent");
            if (bonusRaw instanceof Number number) {
                qualityBonus = number.doubleValue();
            } else if (bonusRaw != null) {
                try {
                    qualityBonus = Double.parseDouble(String.valueOf(bonusRaw));
                } catch (NumberFormatException ignored) {
                    Bukkit.getLogger().warning("[Cooking] Invalid quality-bonus-percent for path " + pathRaw);
                }
            }
            tools.add(new FarmingToolDefinition(String.valueOf(pathRaw).trim(), radius, qualityBonus));
        }
        return tools;
    }

    private static Map<Material, FarmingCropDefinition> parseCrops(List<Map<?, ?>> raw) {
        Map<Material, FarmingCropDefinition> crops = new HashMap<>();
        for (Map<?, ?> entry : raw) {
            Material crop = parseMaterial(entry.get("crop"));
            Material seed = parseMaterial(entry.get("seed"));
            if (crop == null || seed == null) {
                continue;
            }
            crops.put(crop, new FarmingCropDefinition(crop, seed));
        }
        return crops;
    }

    private static Set<Material> parseTrampleableCrops(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return defaultTrampleableCrops();
        }
        Set<Material> crops = new HashSet<>();
        for (String entry : raw) {
            Material material = parseMaterial(entry);
            if (material != null) {
                crops.add(material);
            }
        }
        return crops.isEmpty() ? defaultTrampleableCrops() : crops;
    }

    private static Set<Material> defaultTrampleableCrops() {
        Set<Material> crops = new HashSet<>();
        addIfPresent(crops, "WHEAT");
        addIfPresent(crops, "POTATOES");
        addIfPresent(crops, "CARROTS");
        addIfPresent(crops, "BEETROOTS");
        addIfPresent(crops, "NETHER_WART");
        addIfPresent(crops, "MELON_STEM");
        addIfPresent(crops, "PUMPKIN_STEM");
        return crops;
    }

    private static void addIfPresent(Set<Material> crops, String name) {
        Material material = Material.matchMaterial(name);
        if (material != null) {
            crops.add(material);
        }
    }

    private static Material parseMaterial(Object raw) {
        if (raw == null) {
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(raw));
        if (material == null) {
            Bukkit.getLogger().warning("[Cooking] Invalid farming material: " + raw);
        }
        return material;
    }

    private static void applyDefaults() {
        FarmingConfig.apply(
                true,
                1.0,
                1.0,
                true,
                10,
                20,
                true,
                1,
                true,
                List.of(),
                Map.of(),
                true,
                false,
                true,
                1.0,
                defaultTrampleableCrops());
    }
}
