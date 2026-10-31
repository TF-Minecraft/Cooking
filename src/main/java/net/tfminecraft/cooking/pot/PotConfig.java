package net.tfminecraft.cooking.pot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.FoodItem;

/** pot.yml: what the soup pot takes besides a water bucket (stock), and the dishes it mashes without water. */
public final class PotConfig {
    private static List<PotLiquid> liquids = List.of();
    private static List<MashRecipe> mashes = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<PotLiquid> poured = new ArrayList<>();
        ConfigurationSection liquidSection = config.getConfigurationSection("liquids");
        if (liquidSection != null) {
            for (String id : liquidSection.getKeys(false)) {
                ConfigurationSection liquid = liquidSection.getConfigurationSection(id);
                if (liquid != null) poured.add(PotLiquid.load(id, liquid));
            }
        }
        List<MashRecipe> recipes = new ArrayList<>();
        ConfigurationSection mashSection = config.getConfigurationSection("mashes");
        if (mashSection != null) {
            for (String id : mashSection.getKeys(false)) {
                ConfigurationSection mash = mashSection.getConfigurationSection(id);
                if (mash != null) recipes.add(new MashRecipe(id, mash));
            }
        }
        liquids = poured;
        mashes = recipes;
    }

    /** The liquid this food pours into the pot, or null. */
    public static PotLiquid liquidFor(FoodItem food) {
        for (PotLiquid liquid : liquids) {
            if (liquid.pours(food)) return liquid;
        }
        return null;
    }

    /** The liquid saved on a pot by its id, or null when pot.yml no longer has it. */
    public static PotLiquid liquid(String id) {
        for (PotLiquid liquid : liquids) {
            if (liquid.id().equals(id)) return liquid;
        }
        return null;
    }

    public static List<MashRecipe> mashes() {
        return mashes;
    }

    /** The mash saved on a pot by its id, or null when pot.yml no longer has it. */
    public static MashRecipe mash(String id) {
        for (MashRecipe recipe : mashes) {
            if (recipe.getId().equals(id)) return recipe;
        }
        return null;
    }

    /** True for a food some mash takes (butter, milk), which a pot of water refuses with a hint. */
    public static boolean isMashIngredient(FoodItem food) {
        for (MashRecipe recipe : mashes) {
            if (recipe.uses(food)) return true;
        }
        return false;
    }
}
