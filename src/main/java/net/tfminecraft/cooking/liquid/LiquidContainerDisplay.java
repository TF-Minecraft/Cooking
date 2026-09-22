package net.tfminecraft.cooking.liquid;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class LiquidContainerDisplay {
    private LiquidContainerDisplay() {}

    public static void sync(Furniture furniture) {
        int blocks = LiquidContainerState.getBlocks(furniture);
        String type = LiquidContainerState.getType(furniture);
        String modelPath = LiquidContainerState.TYPE_MILK.equalsIgnoreCase(type)
                ? ItemCache.liquidBlockMilk
                : ItemCache.liquidBlockWater;

        ItemStack model = TLibs.getItemAPI().getCreator().getItemFromPath(modelPath);
        if (model == null) {
            return;
        }

        for (int i = 1; i <= ItemCache.maxBlocks; i++) {
            String slotKey = LiquidContainerSlots.slotForLevel(i);
            if (slotKey == null) {
                continue;
            }
            if (i <= blocks) {
                showSlot(furniture, slotKey, model);
            } else {
                clearSlot(furniture, slotKey);
            }
        }
    }

    public static void clearAll(Furniture furniture) {
        for (String slotKey : LiquidContainerSlots.all()) {
            clearSlot(furniture, slotKey);
        }
    }

    private static void showSlot(Furniture furniture, String slotKey, ItemStack model) {
        if (furniture.getType() == null || furniture.getType().getSlot(slotKey) == null) {
            return;
        }
        PlacedSlot slot = furniture.getOrCreatePlacedSlot(slotKey);
        slot.forceModel(model.clone());
    }

    private static void clearSlot(Furniture furniture, String slotKey) {
        if (furniture.getType() == null || furniture.getType().getSlot(slotKey) == null) {
            return;
        }
        furniture.getOrCreatePlacedSlot(slotKey).clearModel();
    }
}
