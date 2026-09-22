package net.tfminecraft.cooking.mixing;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class MixingBowlDisplay {
    private MixingBowlDisplay() {}

    public static boolean showLayer(Furniture furniture, String slotKey) {
        String modelPath = ItemCache.getMixingModel(slotKey);
        if (modelPath == null || furniture.getType() == null || furniture.getType().getSlot(slotKey) == null) {
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

    public static void clearLayer(Furniture furniture, String slotKey) {
        if (furniture.getType() == null || furniture.getType().getSlot(slotKey) == null) {
            return;
        }
        furniture.getOrCreatePlacedSlot(slotKey).clearModel();
    }
}
