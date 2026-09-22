package net.tfminecraft.cooking.milling;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class MillingStoneDisplay {
    private static final String TOP_MODEL = "ia.tfmc_cooking:milling_stone_top";

    private MillingStoneDisplay() {}

    public static void showTop(Furniture furniture) {
        if (furniture.getType() == null || furniture.getType().getSlot(MillingStoneSlots.TOP) == null) {
            return;
        }
        ItemStack model = TLibs.getItemAPI().getCreator().getItemFromPath(TOP_MODEL);
        if (model == null) {
            return;
        }
        furniture.getOrCreatePlacedSlot(MillingStoneSlots.TOP).forceModel(model);
    }

    public static void showWheat(Furniture furniture) {
        ItemStack wheat = new ItemStack(Material.WHEAT);
        for (String slotId : MillingStoneSlots.WHEAT) {
            if (furniture.getType() == null || furniture.getType().getSlot(slotId) == null) {
                continue;
            }
            furniture.getOrCreatePlacedSlot(slotId).forceModel(wheat);
        }
    }

    public static void showFlour(Furniture furniture) {
        String modelPath = ItemCache.getMixingModel("flour");
        if (modelPath == null) {
            modelPath = "ia.tfmc_cooking:flour_model";
        }
        if (furniture.getType() == null || furniture.getType().getSlot(MillingStoneSlots.FLOUR) == null) {
            return;
        }
        ItemStack model = TLibs.getItemAPI().getCreator().getItemFromPath(modelPath);
        if (model == null) {
            return;
        }
        furniture.getOrCreatePlacedSlot(MillingStoneSlots.FLOUR).forceModel(model);
    }

    public static void clearWheat(Furniture furniture) {
        for (String slotId : MillingStoneSlots.WHEAT) {
            clearSlot(furniture, slotId);
        }
    }

    public static void clearFlour(Furniture furniture) {
        clearSlot(furniture, MillingStoneSlots.FLOUR);
    }

    public static void clearAll(Furniture furniture) {
        clearWheat(furniture);
        clearFlour(furniture);
    }

    public static void syncVisuals(Furniture furniture, MillingStoneStage stage) {
        showTop(furniture);
        switch (stage) {
            case LOADED -> {
                clearFlour(furniture);
                showWheat(furniture);
            }
            case READY -> {
                clearWheat(furniture);
                showFlour(furniture);
            }
            default -> clearAll(furniture);
        }
    }

    private static void clearSlot(Furniture furniture, String slotId) {
        if (furniture.getType() == null || furniture.getType().getSlot(slotId) == null) {
            return;
        }
        furniture.getOrCreatePlacedSlot(slotId).clearModel();
    }
}
