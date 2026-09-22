package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

public class ConversionLoader {
    public static Map<String, String> conversions = new HashMap<>();

    public static Map<String, String> get() {
        return conversions;
    }

    public static String getByItem(ItemStack item) {
        for(Map.Entry<String, String> entry : conversions.entrySet()) {
            String key = entry.getKey();
            if(TLibs.getItemAPI().getChecker().checkItemWithPath(item, key)) return entry.getValue();
        }
        return null;
    }

    public static String getByString(String string) {
        return conversions.containsKey(string) ? conversions.get(string) : null;
    }

    public void load(File file) {
        conversions.clear();

        FileConfiguration config = new YamlConfiguration();

        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        for (String s : config.getStringList("conversions")) {
            if (s == null || s.isBlank()) {
                continue;
            }

            String trimmed = s.trim();
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length < 2) {
                Bukkit.getLogger().warning("[Cooking] Invalid conversion line (missing food string): " + trimmed);
                continue;
            }

            String input = parts[0].trim();
            String output = parts[1].trim();
            if (input.isEmpty() || output.isEmpty()) {
                Bukkit.getLogger().warning("[Cooking] Invalid conversion line (empty input or output): " + trimmed);
                continue;
            }

            conversions.put(input, output);
        }
    }
}
