package net.tfminecraft.cooking.baking;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class BakingTrayBakeApplier {
    private static final String COOKED_TRACK = "cooked";
    private static final int RAW = 0;
    private static final int COOKED = 1;
    private static final int BURNT = 2;

    private BakingTrayBakeApplier() {}

    public static boolean applyCook(Furniture tray, BakingTrayRecipe recipe) {
        return applyToSlots(tray, recipe, RAW, COOKED);
    }

    public static boolean applyBurn(Furniture tray, BakingTrayRecipe recipe) {
        return applyToSlots(tray, recipe, COOKED, BURNT);
    }

    public static boolean applyCookSlot(Furniture tray, String slotId) {
        return applyToSlot(tray, slotId, RAW, COOKED);
    }

    public static boolean applyBurnSlot(Furniture tray, String slotId) {
        return applyToSlot(tray, slotId, COOKED, BURNT);
    }

    private static boolean applyToSlots(Furniture tray, BakingTrayRecipe recipe, int fromValue, int toValue) {
        boolean changed = false;
        for (String slotId : recipe.getAllSlotIds()) {
            if (applyToSlot(tray, slotId, fromValue, toValue)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean applyToSlot(Furniture tray, String slotId, int fromValue, int toValue) {
        if (!tray.hasActiveSlot(slotId)) {
            return false;
        }
        PlacedSlot slot = tray.getActiveSlot(slotId).orElse(null);
        if (slot == null) {
            return false;
        }

        ItemStack item = slot.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }

        FoodItem foodItem = FoodItem.fromItem(item);
        if (foodItem == null) {
            return false;
        }

        TagTrack cookedTrack = foodItem.getTagTrack(COOKED_TRACK);
        if (cookedTrack == null) {
            TagTrack baseTrack = TrackLoader.getByString(COOKED_TRACK);
            if (baseTrack == null) {
                return false;
            }
            cookedTrack = new TagTrack(baseTrack);
            foodItem.addOrModifyTrack(cookedTrack);
        }

        if (cookedTrack.getValue() != fromValue) {
            return false;
        }

        cookedTrack.setValue(toValue);
        ItemStack updated = ItemUpdater.applyItemUpdate(item, foodItem, tray.getId());
        if (updated == null) {
            return false;
        }

        slot.setCurrentItem(updated);
        slot.forceModel(updated);
        return true;
    }
}
