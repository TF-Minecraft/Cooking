package net.tfminecraft.cooking.heat;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class HeatLoader {
    private HeatLoader() {}

    public static void load(FileConfiguration config) {
        Map<String, HeatSourceDefinition> sources = new HashMap<>();
        Map<String, HeatConsumerDefinition> consumers = new HashMap<>();

        ConfigurationSection heatSection = config.getConfigurationSection("heat");
        if (heatSection != null) {
            ConfigurationSection sourcesSection = heatSection.getConfigurationSection("sources");
            if (sourcesSection != null) {
                for (String furnitureId : sourcesSection.getKeys(false)) {
                    String typeName = sourcesSection.getString(furnitureId + ".type");
                    HeatSourceType type = HeatSourceType.fromConfig(typeName);
                    if (type == null) {
                        Bukkit.getLogger().warning("[Cooking] Unknown heat source type '" + typeName
                                + "' for furniture '" + furnitureId + "'");
                        continue;
                    }
                    sources.put(furnitureId.toLowerCase(java.util.Locale.ROOT), new HeatSourceDefinition(furnitureId, type));
                }
            }

            ConfigurationSection consumersSection = heatSection.getConfigurationSection("consumers");
            if (consumersSection != null) {
                for (String furnitureId : consumersSection.getKeys(false)) {
                    String base = furnitureId + ".";
                    String sourceFurnitureId = consumersSection.getString(base + "source");
                    String lookupName = consumersSection.getString(base + "lookup");
                    HeatLookup lookup = HeatLookup.fromConfig(lookupName);
                    if (sourceFurnitureId == null || lookup == null) {
                        Bukkit.getLogger().warning("[Cooking] Invalid heat consumer config for furniture '"
                                + furnitureId + "'");
                        continue;
                    }
                    consumers.put(furnitureId.toLowerCase(java.util.Locale.ROOT),
                            new HeatConsumerDefinition(furnitureId, sourceFurnitureId, lookup));
                }
            }
        }

        HeatSources.load(sources, consumers);
    }
}
