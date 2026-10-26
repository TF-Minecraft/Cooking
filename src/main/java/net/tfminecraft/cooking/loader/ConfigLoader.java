package net.tfminecraft.cooking.loader;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.cooking.cache.CategoryDictionary;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cache.MixingIngredient;
import net.tfminecraft.cooking.heat.HeatLoader;
import net.tfminecraft.cooking.nutrition.NutritionConfig;
import net.tfminecraft.cooking.nutrition.NutritionLog;

public class ConfigLoader {
    public void loadConfig(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        ItemCache.liquidModels.clear();
        ItemCache.colourMap.clear();
        CategoryDictionary.dictionary.clear();
        CategoryDictionary.sauceDict.clear();
        FurnitureCache.fryingPan = config.getString("frying_pan", "none");
        FurnitureCache.saucePan = config.getString("saucepan", "none");
        FurnitureCache.plate = config.getString("plate", "none");
        FurnitureCache.bowl = config.getString("bowl", "none");
        FurnitureCache.pot = config.getString("pot", "none");

        FurnitureCache.butterChurn = config.getString("butter-churn", "none");
        FurnitureCache.butterPlate = config.getString("butter-plate", "none");
        FurnitureCache.firePit = config.getString("fire-pit", "none");
        FurnitureCache.meatHook = config.getString("meat-hook", "none");
        FurnitureCache.sausageMaker = config.getString("sausage-maker", "none");
        FurnitureCache.mixingBowl = config.getString("mixing-bowl", "none");
        FurnitureCache.millingStone = config.getString("milling-stone", "none");
        FurnitureCache.ovenBottom = config.getString("oven-bottom", "none");
        FurnitureCache.ovenTop = config.getString("oven-top", "none");
        FurnitureCache.breadTray = config.getString("bread-tray", "none");
        FurnitureCache.liquidContainer = config.getString("liquid-container", "none");
        FurnitureCache.trough = config.getString("trough", "none");

        ItemCache.butter = config.getString("butter", "none");
        ItemCache.butterPan = config.getString("butter-pan", "ia.tfmc_cooking:butter_pan");
        ItemCache.butterPiece = config.getString("butter-piece", "ia.tfmc_cooking:butter_piece");
        ItemCache.firePitTurner = config.getString("fire-pit-turner", "ia.tfmc_cooking:fire_pit_turner");
        ItemCache.firePitVisualY = (float) config.getDouble("fire-pit-visual-y", 0.5);
        ItemCache.firePitPivotY = (float) config.getDouble("fire-pit-pivot-y", 0.5);
        ItemCache.firePitSpinAxis = config.getString("fire-pit-spin-axis", "x").toLowerCase();
        ItemCache.carveTool = config.getString("carve-tool", "ia.tfmc_cooking:cutting_knife");
        ItemCache.liquidFallback = config.getString("liquid-fallback", "ia.tfmc_cooking:water");
        ItemCache.platedLiquidFallback = config.getString("plated-liquid-fallback", "ia.tfmc_cooking:water_plated");

        ItemCache.water = config.getString("water", "v.water_bucket");
        ItemCache.emptyCup = config.getString("empty-cup", "ia.tfmc_cooking:empty_cup");
        ItemCache.cupOfWater = config.getString("cup-of-water", "ia.tfmc_cooking:cup_of_water");
        ItemCache.cupOfMilk = config.getString("cup-of-milk", "ia.tfmc_cooking:cup_of_milk");
        ItemCache.liquidBlockWater = config.getString("liquid-block-water", "ia.tfmc_cooking:water_block");
        ItemCache.liquidBlockMilk = config.getString("liquid-block-milk", "ia.tfmc_cooking:milk_block");
        ItemCache.blocksPerBucket = config.getInt("liquid-blocks-per-bucket", 3);
        ItemCache.maxBlocks = config.getInt("liquid-max-blocks", 6);
        ItemCache.blocksPerCup = config.getInt("liquid-blocks-per-cup", 1);
        ItemCache.potWaterInput = config.getString("pot-water-input", "v.water_bucket");
        ItemCache.potLiquidDisplay = config.getString("pot-liquid-display", "ia.tfmc_cooking:water");
        ItemCache.potSoupScoops = Math.max(1, config.getInt("pot-soup-scoops", 3));
        ItemCache.potSoupHeightDivisor = Math.max(1, config.getInt("pot-soup-height-divisor", 4));

        ItemCache.ladle = config.getString("ladle", "none");
        ItemCache.masher = config.getString("masher", "none");

        ItemCache.flour = config.getString("flour-item", "none");
        ItemCache.bag = config.getString("bag", "none");
        ItemCache.troughFeed = config.getString("trough-feed", "m.pets.universal_feed");
        ItemCache.troughItemsPerClick = Math.max(1, config.getInt("trough-items-per-click", 4));

        ItemCache.butterChurnCount = config.getInt("butter-churn-count", 3);
        ItemCache.mixingStirCount = config.getInt("mixing-stir-count", 3);
        ItemCache.mixingStirDurationTicks = config.getInt("mixing-stir-duration-ticks", 10);
        ItemCache.mixingStirTiltDegrees = (float) config.getDouble("mixing-stir-tilt-degrees", 20);
        ItemCache.mixingStirPivotY = (float) config.getDouble("mixing-stir-pivot-y", 0);

        ItemCache.sausageMakerDurationTicks = config.getInt("sausage-maker-duration-ticks", 6);
        ItemCache.sausageMakerWobbleDegrees = (float) config.getDouble("sausage-maker-wobble-degrees", 4);
        ItemCache.sausageMakerCooldownTicks = config.getInt("sausage-maker-cooldown-ticks", 10);

        ItemCache.ovenFuel.clear();
        ItemCache.ovenFuel.addAll(config.getStringList("oven-fuel"));
        ItemCache.ovenWoodModel = config.getString("oven-wood-model", "ia.tfmc_cooking:wood_model");
        ItemCache.ovenWoodBurntModel = config.getString("oven-wood-burnt-model", "ia.tfmc_cooking:wood_burnt_model");
        ItemCache.ovenFireModel = config.getString("oven-fire-model", "ia.tfmc_cooking:oven_fire_model");
        ItemCache.ovenBurnIntervalTicks = config.getInt("oven-burn-interval-ticks", 20);
        ItemCache.ovenBurnChanceFresh = (float) config.getDouble("oven-burn-chance-fresh", 0.08);
        ItemCache.ovenBurnChanceBurnt = (float) config.getDouble("oven-burn-chance-burnt", 0.12);

        ItemCache.mixingIngredients.clear();
        if (config.isConfigurationSection("mixing-ingredients")) {
            for (String key : config.getConfigurationSection("mixing-ingredients").getKeys(false)) {
                String base = "mixing-ingredients." + key + ".";
                ItemCache.mixingIngredients.put(
                        key,
                        new MixingIngredient(
                                config.getString(base + "input"),
                                config.getString(base + "input-food"),
                                config.getString(base + "model"),
                                config.getString(base + "output")));
            }
        }

        if(config.contains("liquids")) {
            for (String key : config.getStringList("liquids")) {
                String[] parts = key.split("\\s+"); 
                if(parts.length != 2) continue;
                String path = parts[0];
                String model = parts[1];
                ItemCache.liquidModels.put(path, model);
            }
        }
        if(config.contains("colours")) {
            for (String key : config.getStringList("colours")) {
                String[] parts = key.split("\\s+"); 
                if(parts.length != 2) continue;
                String path = parts[0];
                String model = parts[1];
                ItemCache.colourMap.put(path, model);
            }
        }

        if(config.isConfigurationSection("dictionary")) {
            for (String key : config.getConfigurationSection("dictionary").getKeys(false)) {
                String value = config.getString("dictionary." + key, "none");
                if(value.equals("none")) continue;
                CategoryDictionary.dictionary.put(key, value);
            }
        }
        if(config.contains("sauce-dict")) {
            for (String key : config.getStringList("sauce-dict")) {
                String[] parts = key.split("\\s+"); 
                if(parts.length != 2) continue;
                String hex = parts[0];
                String model = parts[1];
                CategoryDictionary.sauceDict.put(hex, model);
            }
        }

        HeatLoader.load(config);
        NutritionConfig.load(config);
        NutritionLog.configure(
                config.getBoolean("logging", true),
                config.getBoolean("wipe-log", true),
                configFile.getParentFile());
	}
}
