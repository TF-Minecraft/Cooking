package net.tfminecraft.cooking.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import net.tfminecraft.cooking.item.FoodItem;

import net.tfminecraft.tlibs.TLibs;

public class ItemCache {
    public static String butter;
    public static String butterPan;
    public static String butterPiece;
    public static String firePitTurner;
    public static float firePitVisualY;
    public static float firePitPivotY;
    public static String firePitSpinAxis;
    public static String carveTool;

    public static String water;

    public static String emptyCup;
    public static String cupOfWater;
    public static String cupOfMilk;

    public static String liquidBlockWater;
    public static String liquidBlockMilk;
    public static int blocksPerBucket = 3;
    public static int maxBlocks = 6;
    public static int blocksPerCup = 1;

    public static String potWaterInput;
    public static String potLiquidDisplay;
    public static int potSoupScoops = 3;
    public static int potSoupHeightDivisor = 4;

    public static String flour;
    public static String bag;
    public static String troughFeed = "m.pets.universal_feed";
    public static int troughItemsPerClick = 4;

    public static HashMap<String, String> liquidModels = new HashMap<>();
    public static HashMap<String, String> colourMap = new HashMap<>();
    public static String liquidFallback;

    //TOOLS
    public static String ladle;
    public static String masher;

    public static Map<String, MixingIngredient> mixingIngredients = new HashMap<>();

    public static int butterChurnCount = 3;
    public static int mixingStirCount = 3;
    public static int mixingStirDurationTicks = 10;
    public static float mixingStirTiltDegrees = 20f;
    public static float mixingStirPivotY = 0f;

    public static int sausageMakerDurationTicks = 6;
    public static float sausageMakerWobbleDegrees = 4f;
    public static int sausageMakerCooldownTicks = 10;

    public static List<String> ovenFuel = new ArrayList<>();
    public static String ovenWoodModel;
    public static String ovenWoodBurntModel;
    public static String ovenFireModel;
    public static int ovenBurnIntervalTicks = 20;
    public static float ovenBurnChanceFresh = 0.08f;
    public static float ovenBurnChanceBurnt = 0.12f;

    public static boolean isLadle(ItemStack i) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, ladle);
    }
    public static boolean isMasher(ItemStack i) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, masher);
    }   

    public static boolean isButter(ItemStack i) {
        if (i == null) {
            return false;
        }
        FoodItem food = FoodItem.fromItem(i);
        if (food != null && "butter".equalsIgnoreCase(food.getId())) {
            return true;
        }
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, butter);
    }

    public static boolean isFlour(ItemStack i) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, flour);
    }

    public static boolean isBag(ItemStack i) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, bag);
    }

    public static boolean isWater(ItemStack i) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, water);
    }

    public static boolean isPotWaterInput(ItemStack i) {
        if (i == null) {
            return false;
        }
        if (i.getType() == Material.WATER_BUCKET) {
            return true;
        }
        return potWaterInput != null
                && TLibs.getItemAPI().getChecker().checkItemWithPath(i, potWaterInput);
    }

    public static boolean isEmptyCup(ItemStack i) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, emptyCup);
    }

    public static boolean isCupOfWater(ItemStack i) {
        if (i == null || FoodItem.fromItem(i) != null) {
            return false;
        }
        return TLibs.getItemAPI().getChecker().checkItemWithPath(i, cupOfWater);
    }

    public static boolean isCupOfMilk(ItemStack i) {
        if (i == null) {
            return false;
        }
        FoodItem food = FoodItem.fromItem(i);
        if (food != null && "cup_of_milk".equalsIgnoreCase(food.getId())) {
            return true;
        }
        return cupOfMilk != null
                && TLibs.getItemAPI().getChecker().checkItemWithPath(i, cupOfMilk);
    }

    public static boolean isMilkBucket(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        FoodItem food = FoodItem.fromItem(stack);
        if (food != null && "milk_bucket".equalsIgnoreCase(food.getId())) {
            return true;
        }
        return stack.getType() == Material.MILK_BUCKET;
    }

    public static boolean isLiquid(ItemStack i) {
        for (String path : liquidModels.keySet()) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(i, path)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        return origin.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    public static boolean originsMatch(String foodOrigin, String keyPart) {
        if (foodOrigin == null || foodOrigin.isBlank() || keyPart == null || keyPart.isBlank()) {
            return false;
        }
        return normalizeOrigin(foodOrigin).equals(normalizeOrigin(keyPart));
    }

    public static String getColour(ItemStack i) {
        if (i == null) {
            return "000000";
        }
        FoodItem food = FoodItem.fromItem(i);
        for (String path : colourMap.keySet()) {
            int dot = path.indexOf('.');
            if (dot > 0 && path.substring(0, dot).equalsIgnoreCase("origin")) {
                if (food == null) {
                    continue;
                }
                String keyPart = path.substring(dot + 1);
                if (originsMatch(food.getOrigin(), keyPart)) {
                    return colourMap.get(path);
                }
                continue;
            }
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(i, path)) {
                return colourMap.get(path);
            }
        }
        return "000000";
    }

    public static String getLiquidModel(String key) {
        return liquidModels.get(key);
    }

    public static String getLiquidModel(ItemStack i) {
        for (String path : liquidModels.keySet()) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(i, path)) {
                return getLiquidModel(path);
            }
        }
        return null;
    }

    public static MixingIngredient getMixingIngredient(String key) {
        return mixingIngredients.get(key);
    }

    public static boolean matchesMixingInput(String key, ItemStack stack) {
        MixingIngredient ingredient = mixingIngredients.get(key);
        if (ingredient == null || stack == null) {
            return false;
        }
        if (ingredient.getInputFood() != null) {
            FoodItem foodItem = FoodItem.fromItem(stack);
            if (foodItem != null && foodItem.getId().equalsIgnoreCase(ingredient.getInputFood())) {
                return true;
            }
        }
        if (ingredient.getInput() == null) {
            return false;
        }
        return TLibs.getItemAPI().getChecker().checkItemWithPath(stack, ingredient.getInput());
    }

    public static String getMixingModel(String key) {
        MixingIngredient ingredient = mixingIngredients.get(key);
        return ingredient != null ? ingredient.getModel() : null;
    }

    public static String getMixingOutput(String key) {
        MixingIngredient ingredient = mixingIngredients.get(key);
        return ingredient != null ? ingredient.getOutput() : null;
    }

    public static boolean matchesOvenFuel(ItemStack stack) {
        if (stack == null || ovenFuel.isEmpty()) {
            return false;
        }
        for (String path : ovenFuel) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(stack, path)) {
                return true;
            }
        }
        return false;
    }
}
