package net.tfminecraft.cooking.popcorn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/** popcorn-machines.yml: the popcorn machines, keyed by id, each naming the furniture it is. */
public final class PopcornMachineConfig {
    private static List<PopcornMachine> machines = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<PopcornMachine> loaded = new ArrayList<>();
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section != null) loaded.add(new PopcornMachine(id, section));
        }
        machines = loaded;
    }

    public static List<PopcornMachine> machines() {
        return machines;
    }

    /** The machine this furniture is, or null. */
    public static PopcornMachine machineFor(Furniture furniture) {
        if (furniture == null || furniture.getId() == null) return null;
        String id = furniture.getId().toLowerCase(Locale.ROOT);
        for (PopcornMachine machine : machines) {
            if (machine.getFurniture().equals(id)) return machine;
        }
        return null;
    }
}
