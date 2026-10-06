package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.cache.NamingConfig;

public class NamingLoader {
    public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        List<String> prefixTracks = config.getStringList("prefix-tracks");
        int fillerMax = config.getInt("fillers.max", 3);
        int addonMax = config.getInt("addons.max", 2);
        int doughFillerMax = config.getInt("dough-fillers.max", 2);

        Map<String, Set<String>> categories = new HashMap<>();
        ConfigurationSection categorySection = config.getConfigurationSection("categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                Set<String> values = new HashSet<>();
                for (String entry : categorySection.getStringList(key)) {
                    if (entry != null && !entry.isBlank()) {
                        values.add(entry.toLowerCase(Locale.ROOT));
                    }
                }
                categories.put(key, values);
            }
        }

        Map<String, Map<String, Map<String, String>>> tagLabelDefaults = new HashMap<>();
        ConfigurationSection defaultsSection = config.getConfigurationSection("tag-label-defaults");
        if (defaultsSection != null) {
            for (String categoryKey : defaultsSection.getKeys(false)) {
                ConfigurationSection trackSection = defaultsSection.getConfigurationSection(categoryKey);
                if (trackSection == null) {
                    continue;
                }
                Map<String, Map<String, String>> byTrack = new HashMap<>();
                for (String trackKey : trackSection.getKeys(false)) {
                    ConfigurationSection stepSection = trackSection.getConfigurationSection(trackKey);
                    if (stepSection == null) {
                        continue;
                    }
                    Map<String, String> byStep = new HashMap<>();
                    for (String stepKey : stepSection.getKeys(false)) {
                        String label = stepSection.getString(stepKey);
                        if (label != null && !label.isBlank()) {
                            byStep.put(stepKey.toLowerCase(Locale.ROOT), label);
                        }
                    }
                    if (!byStep.isEmpty()) {
                        byTrack.put(trackKey.toLowerCase(Locale.ROOT), byStep);
                    }
                }
                if (!byTrack.isEmpty()) {
                    tagLabelDefaults.put(categoryKey.toLowerCase(Locale.ROOT), byTrack);
                }
            }
        }

        Map<String, String> originAdjectives = new HashMap<>();
        ConfigurationSection adjectiveSection = config.getConfigurationSection("origin-adjectives");
        if (adjectiveSection != null) {
            for (String key : adjectiveSection.getKeys(false)) {
                String value = adjectiveSection.getString(key);
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    originAdjectives.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        }

        NamingConfig.apply(prefixTracks, fillerMax, addonMax, doughFillerMax, categories, tagLabelDefaults,
                originAdjectives);
    }
}
