package net.tfminecraft.cooking.cache;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public class FurnitureCache {
    public static String fryingPan;
    public static String saucePan;
    public static String pot;

    public static String butterChurn;
    public static String butterPlate;
    public static String firePit;
    public static String meatHook;
    public static String sausageMaker;
    public static String mixingBowl;
    public static String millingStone;
    public static String ovenBottom;
    public static String ovenTop;
    public static String breadTray;
    public static String liquidContainer;
    public static String trough;

    public static String plate;
    public static String bowl;

    public static Method getByFurniture(Furniture f) {
        if(f.getId().equalsIgnoreCase(fryingPan)) return Method.FRYING_PAN;
        if(f.getId().equalsIgnoreCase(saucePan)) return Method.SAUCEPAN;
        if(f.getId().equalsIgnoreCase(pot)) return Method.POT;
        return Method.NONE;
    }

    public static boolean isButterChurn(Furniture f) {
        return f.getId().equalsIgnoreCase(butterChurn);
    }

    public static boolean isButterPlate(Furniture f) {
        return f.getId().equalsIgnoreCase(butterPlate);
    }

    public static boolean isFirePit(Furniture f) {
        return f.getId().equalsIgnoreCase(firePit);
    }

    public static boolean isMeatHook(Furniture f) {
        return f.getId().equalsIgnoreCase(meatHook);
    }

    public static boolean isSausageMaker(Furniture f) {
        return f.getId().equalsIgnoreCase(sausageMaker);
    }

    public static boolean isMixingBowl(Furniture f) {
        return f.getId().equalsIgnoreCase(mixingBowl);
    }

    public static boolean isMillingStone(Furniture f) {
        return f.getId().equalsIgnoreCase(millingStone);
    }

    public static boolean isOvenBottom(Furniture f) {
        return f.getId().equalsIgnoreCase(ovenBottom);
    }

    public static boolean isOvenTop(Furniture f) {
        return f.getId().equalsIgnoreCase(ovenTop);
    }

    public static boolean isBreadTray(Furniture f) {
        return f.getId().equalsIgnoreCase(breadTray);
    }

    public static boolean isLiquidContainer(Furniture f) {
        return f.getId().equalsIgnoreCase(liquidContainer);
    }

    public static boolean isTrough(Furniture f) {
        return f.getId().equalsIgnoreCase(trough);
    }

    public static boolean isPlate(Furniture f) {
        return f.getId().equalsIgnoreCase(plate);
    }
    
    public static boolean isBowl(Furniture f) {
        return f.getId().equalsIgnoreCase(bowl);
    }

    public static boolean isMealHolder(Furniture f) {
        return isBowl(f) || isPlate(f);
    }
}
