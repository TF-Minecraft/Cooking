package net.tfminecraft.cooking.condiment;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/** condiments.yml: souring wine into vinegar, tapping sap and bottling from the saucepan. */
public final class CondimentConfig {
    private static Map<String, String> vinegarBrews = Map.of();
    private static long vinegarMillis = 3_600_000L;
    private static String vinegarOutput = "";
    private static String souringModel = "";
    private static String vinegarModel = "";
    private static Set<Material> sapLogs = Set.of();
    private static long sapCooldownMillis = 1_200_000L;
    private static String sapOutput = "";
    private static List<String> saucepanLiquids = List.of();
    private static List<BottleRecipe> bottles = List.of();

    public void load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, String> brews = new LinkedHashMap<>();
        ConfigurationSection brewSection = config.getConfigurationSection("vinegar.brews");
        if (brewSection != null) {
            for (String key : brewSection.getKeys(false)) brews.put(key.toLowerCase(Locale.ROOT), brewSection.getString(key));
        }
        Set<Material> logs = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("sap.logs")) {
            Material material = Material.matchMaterial(name);
            if (material != null) logs.add(material);
        }
        List<BottleRecipe> recipes = new ArrayList<>();
        ConfigurationSection recipeSection = config.getConfigurationSection("saucepan.bottles");
        if (recipeSection != null) {
            for (String id : recipeSection.getKeys(false)) {
                ConfigurationSection recipe = recipeSection.getConfigurationSection(id);
                if (recipe != null) recipes.add(new BottleRecipe(id, recipe));
            }
        }
        vinegarBrews = brews;
        vinegarMillis = Math.max(0, config.getLong("vinegar.minutes", 60)) * 60_000L;
        vinegarOutput = config.getString("vinegar.output", "");
        souringModel = config.getString("vinegar.models.souring", "");
        vinegarModel = config.getString("vinegar.models.vinegar", "");
        sapLogs = logs;
        sapCooldownMillis = Math.max(0, config.getLong("sap.cooldown-minutes", 20)) * 60_000L;
        sapOutput = config.getString("sap.output", "");
        saucepanLiquids = config.getStringList("saucepan.liquids");
        bottles = recipes;
    }

    /** The vinegar a brew sours into (Wine, Cider): the first key its name contains. Null when it doesn't sour. */
    public static String vinegarOrigin(String brewName) {
        if (brewName == null) return null;
        String name = brewName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : vinegarBrews.entrySet()) {
            if (name.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    /** How long poured wine takes to become vinegar. */
    public static long vinegarMillis() {
        return vinegarMillis;
    }

    /** The vinegar's food line; {origin} becomes Wine or Cider. */
    public static String vinegarOutput() {
        return vinegarOutput;
    }

    public static String souringModel() {
        return souringModel;
    }

    public static String vinegarModel() {
        return vinegarModel;
    }

    public static boolean isSapLog(Material material) {
        return sapLogs.contains(material);
    }

    /** How long a tapped log takes to give sap again. */
    public static long sapCooldownMillis() {
        return sapCooldownMillis;
    }

    public static String sapOutput() {
        return sapOutput;
    }

    /** True for a bottled food the saucepan takes as its liquid (vinegar, sap). */
    public static boolean isSaucepanLiquid(FoodItem food) {
        for (String path : saucepanLiquids) {
            if (CookingPathHandler.matches(food, path)) return true;
        }
        return false;
    }

    public static List<BottleRecipe> bottles() {
        return bottles;
    }
}
