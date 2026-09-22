package net.tfminecraft.cooking.baking;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;
import net.tfminecraft.interactiblefurniture.utils.CoordinateUtils;

public final class BakingTraySlots {
    private BakingTraySlots() {}

    public static String findClosestSlot(Furniture furniture, BakingTrayRecipe recipe, Vector clickPoint) {
        if (recipe == null || furniture == null) {
            return null;
        }

        ItemDisplay display = getDisplay(furniture);
        if (display == null) {
            return fallbackSlot(furniture, recipe, clickPoint == null);
        }

        if (clickPoint == null) {
            return fallbackSlot(furniture, recipe, true);
        }

        String closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (String slotId : recipe.getAllSlotIds()) {
            SlotDefinition slot = getSlotDefinition(furniture, slotId);
            if (slot == null) {
                continue;
            }
            Location slotLoc = slot.computeDisplayLocation(furniture.getLoc(), display, new DisplayData());
            double distance = CoordinateUtils.distance3D(slotLoc.toVector(), clickPoint);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = slotId;
            }
        }

        return closest != null ? closest : fallbackSlot(furniture, recipe, true);
    }

    public static String findClosestOccupiedSlot(Furniture furniture, BakingTrayRecipe recipe, Vector clickPoint) {
        String closest = findClosestSlot(furniture, recipe, clickPoint);
        if (closest != null && isSlotOccupied(furniture, closest)) {
            return closest;
        }

        String best = null;
        double bestDistance = Double.MAX_VALUE;
        ItemDisplay display = getDisplay(furniture);

        for (String slotId : recipe.getAllSlotIds()) {
            if (!isSlotOccupied(furniture, slotId)) {
                continue;
            }
            if (clickPoint == null || display == null) {
                return slotId;
            }
            SlotDefinition slot = getSlotDefinition(furniture, slotId);
            if (slot == null) {
                continue;
            }
            Location slotLoc = slot.computeDisplayLocation(furniture.getLoc(), display, new DisplayData());
            double distance = CoordinateUtils.distance3D(slotLoc.toVector(), clickPoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = slotId;
            }
        }

        return best;
    }

    public static String findClosestEmptySlot(Furniture furniture, BakingTrayRecipe recipe, Vector clickPoint) {
        String closest = findClosestSlot(furniture, recipe, clickPoint);
        if (closest != null && !isSlotOccupied(furniture, closest)) {
            return closest;
        }

        String best = null;
        double bestDistance = Double.MAX_VALUE;
        ItemDisplay display = getDisplay(furniture);

        for (String slotId : recipe.getAllSlotIds()) {
            if (isSlotOccupied(furniture, slotId)) {
                continue;
            }
            if (clickPoint == null || display == null) {
                return slotId;
            }
            SlotDefinition slot = getSlotDefinition(furniture, slotId);
            if (slot == null) {
                continue;
            }
            Location slotLoc = slot.computeDisplayLocation(furniture.getLoc(), display, new DisplayData());
            double distance = CoordinateUtils.distance3D(slotLoc.toVector(), clickPoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = slotId;
            }
        }

        return best;
    }

    public static boolean isMoldEmpty(Furniture furniture, BakingTrayRecipe recipe, String moldId) {
        for (String slotId : recipe.getMoldSlotIds(moldId)) {
            if (isSlotOccupied(furniture, slotId)) {
                return false;
            }
        }
        return true;
    }

    public static String findFillableMold(Furniture furniture, BakingTrayRecipe recipe, String preferredMoldId) {
        if (preferredMoldId != null && isMoldEmpty(furniture, recipe, preferredMoldId)) {
            return preferredMoldId;
        }
        for (String moldId : recipe.getMoldIds()) {
            if (!moldId.equalsIgnoreCase(preferredMoldId) && isMoldEmpty(furniture, recipe, moldId)) {
                return moldId;
            }
        }
        return null;
    }

    public static String findFirstFillableMold(Furniture furniture, BakingTrayRecipe recipe) {
        for (String moldId : recipe.getMoldIds()) {
            if (isMoldEmpty(furniture, recipe, moldId)) {
                return moldId;
            }
        }
        return null;
    }

    private static String fallbackSlot(Furniture furniture, BakingTrayRecipe recipe, boolean preferEmpty) {
        if (preferEmpty) {
            String moldId = findFirstFillableMold(furniture, recipe);
            if (moldId != null) {
                List<String> slots = recipe.getMoldSlotIds(moldId);
                return slots.isEmpty() ? null : slots.get(0);
            }
        }

        for (String slotId : recipe.getAllSlotIds()) {
            if (isSlotOccupied(furniture, slotId)) {
                return slotId;
            }
        }
        List<String> allSlots = recipe.getAllSlotIds();
        return allSlots.isEmpty() ? null : allSlots.get(0);
    }

    private static boolean isSlotOccupied(Furniture furniture, String slotId) {
        if (!furniture.hasActiveSlot(slotId)) {
            return false;
        }
        return furniture.getActiveSlot(slotId)
                .map(PlacedSlot::getCurrentItem)
                .map(BakingTraySlots::hasItem)
                .orElse(false);
    }

    private static boolean hasItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private static SlotDefinition getSlotDefinition(Furniture furniture, String slotId) {
        if (furniture.getType() == null) {
            return null;
        }
        return furniture.getType().getSlot(slotId);
    }

    private static ItemDisplay getDisplay(Furniture furniture) {
        var entity = Bukkit.getEntity(furniture.getEntityId());
        return entity instanceof ItemDisplay itemDisplay ? itemDisplay : null;
    }
}
