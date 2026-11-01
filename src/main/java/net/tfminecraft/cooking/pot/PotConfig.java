package net.tfminecraft.cooking.pot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.sandwich.DishBonus;
import net.tfminecraft.cooking.sandwich.SandwichLayer;

/**
 * pot.yml: what the soup pot takes besides a water bucket (stock), the dishes it mashes without water, the kinds of
 * soup its pieces make (a Seafood Chowder) and the tags a soup earns from what's in the pot (bonuses, such as a poison).
 */
public final class PotConfig {
    private static List<PotLiquid> liquids = List.of();
    private static List<MashRecipe> mashes = List.of();
    private static List<DishBonus> bonuses = List.of();
    private static List<SandwichLayer> ingredients = List.of();
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
        List<SandwichLayer> layers = new ArrayList<>();
        ConfigurationSection layerSection = config.getConfigurationSection("ingredients");
        if (layerSection != null) {
            for (String id : layerSection.getKeys(false)) {
                ConfigurationSection layer = layerSection.getConfigurationSection(id);
                if (layer != null) layers.add(new SandwichLayer(id, layer));
            }
        }
        List<SaladKind> kinded = new ArrayList<>();
        ConfigurationSection kindSection = config.getConfigurationSection("kinds");
        if (kindSection != null) {
            for (String origin : kindSection.getKeys(false)) {
                ConfigurationSection kind = kindSection.getConfigurationSection(origin);
                if (kind != null) kinded.add(new SaladKind(origin, kind));
            }
        }
        liquids = poured;
        mashes = recipes;
        bonuses = DishBonus.load(config.getConfigurationSection("bonuses"));
        ingredients = layers;
        kinds = kinded;
    }

    /** The ingredient (pot.yml ingredients) this boiled piece is to a soup's kind, or null. */
    public static SandwichLayer ingredientFor(FoodItem food) {
        for (SandwichLayer layer : ingredients) {
            if (layer.matches(food)) return layer;
        }
        return null;
    }

    /**
     * The first kind of soup (pot.yml kinds) these pieces make, or null when none takes them or a piece is no
     * ingredient of any kind (so "only" means nothing else is in the pot). Extras (salt, stock) don't count.
     */
    public static SaladKind kindFor(List<FoodItem> pieces) {
        if (pieces.isEmpty()) return null;
        List<SandwichLayer> made = new ArrayList<>();
        for (FoodItem piece : pieces) {
            SandwichLayer layer = ingredientFor(piece);
            if (layer == null) return null;
            made.add(layer);
        }
        for (SaladKind kind : kinds) {
            if (kind.matches(pieces, made)) return kind;
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
