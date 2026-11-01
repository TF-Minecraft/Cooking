package net.tfminecraft.cooking.pot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.sandwich.DishBonus;


/**
 * pot.yml: what the soup pot takes besides a water bucket (stock), the dishes it mashes without water, and the tags a
 * soup earns from what's in the pot (bonuses, such as a poison), and the named soups its pieces make (kinds: Borscht).
 */
public final class PotConfig {
    private static List<PotLiquid> liquids = List.of();
    private static List<MashRecipe> mashes = List.of();
    private static List<DishBonus> bonuses = List.of();
    private static List<SaladKind> kinds = List.of();

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
        bonuses = DishBonus.load(config.getConfigurationSection("bonuses"));
        List<SaladKind> named = new ArrayList<>();
        ConfigurationSection kindSection = config.getConfigurationSection("kinds");
        if (kindSection != null) {
            for (String origin : kindSection.getKeys(false)) {
                ConfigurationSection kind = kindSection.getConfigurationSection(origin);
                if (kind != null) named.add(new SaladKind(origin, kind));
            }
        }
        kinds = named;
    }

    /**
     * The first kind of soup these pot pieces make (Borscht), or null. Seasonings and add-ons (salt, pepper, spices)
     * don't count, so a pinch of salt doesn't stop a beetroot soup being Borscht.
     */
    public static SaladKind kindFor(List<FoodItem> pieces) {
        List<FoodItem> counted = new ArrayList<>();
        for (FoodItem food : pieces) {
            String category = food.getCategory();
            if (NamingConfig.isCategory("seasoning", category) || NamingConfig.isCategory("addon", category)) continue;
            counted.add(food);
        }
        if (counted.isEmpty()) return null;
        for (SaladKind kind : kinds) {
            if (kind.matches(counted)) return kind;
        }
        return null;
    }

    /** The tags a ladled soup earns from what's in the pot, written like sandwiches.yml's bonuses (count: origin). */
    public static List<DishBonus> bonuses() {
        return bonuses;
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
