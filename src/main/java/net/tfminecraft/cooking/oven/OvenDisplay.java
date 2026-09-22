package net.tfminecraft.cooking.oven;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class OvenDisplay {
    private OvenDisplay() {}

    public static void syncAll(Furniture furniture) {
        if (furniture.getType() == null) {
            return;
        }

        for (String slotId : OvenSlots.FILL_ORDER) {
            syncWoodSlot(furniture, slotId, OvenState.getStage(furniture, slotId));
        }

        if (OvenState.shouldShowFire(furniture)) {
            showModel(furniture, OvenSlots.FIRE, ItemCache.ovenFireModel);
        } else {
            clearSlot(furniture, OvenSlots.FIRE);
        }
    }

    public static void clearAll(Furniture furniture) {
        if (furniture.getType() == null) {
            return;
        }
        for (String slotId : OvenSlots.FILL_ORDER) {
            clearSlot(furniture, slotId);
        }
        clearSlot(furniture, OvenSlots.FIRE);
    }

    private static void syncWoodSlot(Furniture furniture, String slotId, OvenSlots.WoodStage stage) {
        switch (stage) {
            case FRESH -> showModel(furniture, slotId, ItemCache.ovenWoodModel);
            case BURNT -> showModel(furniture, slotId, ItemCache.ovenWoodBurntModel);
            case EMPTY -> clearSlot(furniture, slotId);
        }
    }

    private static boolean showModel(Furniture furniture, String slotKey, String modelPath) {
        if (modelPath == null || furniture.getType().getSlot(slotKey) == null) {
            return false;
        }

        ItemStack model = TLibs.getItemAPI().getCreator().getItemFromPath(modelPath);
        if (model == null) {
            return false;
        }

        PlacedSlot slot = furniture.getOrCreatePlacedSlot(slotKey);
        slot.forceModel(model);
        return true;
    }

    private static void clearSlot(Furniture furniture, String slotKey) {
        if (furniture.getType().getSlot(slotKey) == null) {
            return;
        }
        furniture.getOrCreatePlacedSlot(slotKey).clearModel();
    }
}
