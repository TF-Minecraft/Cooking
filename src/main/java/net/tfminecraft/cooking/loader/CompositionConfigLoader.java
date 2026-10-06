package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.quality.CompositionConfig;
import net.tfminecraft.cooking.quality.CompositionContext;

public class CompositionConfigLoader {
    public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        boolean legacyMode = config.getBoolean("legacy-mode", true);
        Set<String> mains = readCategorySet(config, "roles.main");
        Set<String> extras = readCategorySet(config, "roles.extra");
        Set<String> neutrals = readCategorySet(config, "roles.neutral");
        Map<CompositionContext, CompositionConfig.RoleSets> overrides = readContextOverrides(config);

        boolean modifiersEnabled = config.getBoolean("modifiers.enabled", true);
        double gapUp = config.getDouble("modifiers.gap-up-chance-per-star", 0.12);
        double gapDown = config.getDouble("modifiers.gap-down-chance-per-star", 0.15);
        boolean craftPctBonus = config.getBoolean("modifiers.craft-quality-pct-bonus", true);

        CompositionConfig.apply(legacyMode, mains, extras, neutrals, overrides, modifiersEnabled, gapUp, gapDown, craftPctBonus);
    }

    private static Map<CompositionContext, CompositionConfig.RoleSets> readContextOverrides(FileConfiguration config) {
        Map<CompositionContext, CompositionConfig.RoleSets> overrides = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("context-overrides");
        if (section == null) {
            return overrides;
        }

        for (String key : section.getKeys(false)) {
            try {
                CompositionContext context = CompositionContext.valueOf(key.toUpperCase(Locale.ROOT));
                ConfigurationSection contextSection = section.getConfigurationSection(key);
                if (contextSection == null) {
                    continue;
                }
                Set<String> mains = readCategorySet(contextSection, "main");
                Set<String> extras = readCategorySet(contextSection, "extra");
                Set<String> neutrals = readCategorySet(contextSection, "neutral");
                overrides.put(context, new CompositionConfig.RoleSets(mains, extras, neutrals));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return overrides;
    }

    private static Set<String> readCategorySet(ConfigurationSection config, String path) {
        Set<String> set = new HashSet<>();
        for (String entry : config.getStringList(path)) {
            if (entry != null && !entry.isBlank()) {
                set.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private static Set<String> readCategorySet(FileConfiguration config, String path) {
        Set<String> set = new HashSet<>();
        for (String entry : config.getStringList(path)) {
            if (entry != null && !entry.isBlank()) {
                set.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }
}
