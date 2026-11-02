package net.tfminecraft.cooking.pan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.DishBonus;

/**
 * frying-pan.yml: the dishes that stir in the other fried pieces in the pan when they're taken out (fried rice), and
 * the tags such a dish earns from what it stirred in (bonuses, such as a poison).
 */
public final class PanConfig {
    private static List<StirIn> stirIns = List.of();
    private static List<DishBonus> bonuses = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<StirIn> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("stir-ins");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection stirIn = section.getConfigurationSection(id);
                if (stirIn != null) loaded.add(new StirIn(id, stirIn));
            }
        }
        stirIns = loaded;
        bonuses = DishBonus.load(config.getConfigurationSection("bonuses"));
    }

    /** The stir-in this food is the dish of, or null. */
    public static StirIn stirInFor(FoodItem dish) {
        for (StirIn stirIn : stirIns) {
            if (stirIn.isDish(dish)) return stirIn;
        }
        return null;
    }

    /** The tags a dish earns from what it stirred in, written like pot.yml's bonuses (count: origin). */
    public static List<DishBonus> bonuses() {
        return bonuses;
    }
}
