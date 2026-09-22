package net.tfminecraft.cooking.baking;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedFurnitureSlot;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class BakingTrayAging {

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
    }

    private void tick() {
        Set<UUID> visited = new HashSet<>();
        Map<UUID, Furniture> placed = InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture();

        for (Furniture furniture : placed.values()) {
            if (BakingTrayRegistry.isTray(furniture) && !furniture.isAttached()) {
                if (visited.add(furniture.getEntityId())) {
                    ageTray(furniture);
                }
            }

            if (!HeatSources.isConsumer(furniture)) {
                continue;
            }

            for (PlacedFurnitureSlot slot : furniture.getActiveFurnitureSlots().values()) {
                Furniture nested = slot.getNested();
                if (nested == null || !BakingTrayRegistry.isTray(nested)) {
                    continue;
                }
                if (visited.add(nested.getEntityId())) {
                    ageTray(nested);
                }
            }
        }
    }

    private void ageTray(Furniture tray) {
        BakingTrayRecipe recipe = BakingTrayRegistry.getByFurniture(tray);
        if (recipe == null) {
            return;
        }

        boolean changed = false;
        for (String slotId : recipe.getAllSlotIds()) {
            if (!tray.hasActiveSlot(slotId)) {
                continue;
            }
            PlacedSlot slot = tray.getActiveSlot(slotId).orElse(null);
            if (slot == null) {
                continue;
            }
            if (ageSlot(slot, tray)) {
                changed = true;
            }
        }

        if (changed) {
            InteractibleFurniture.getInstance().getFurnitureManager().markDirty(tray);
        }
    }

    private boolean ageSlot(PlacedSlot slot, Furniture tray) {
        ItemStack item = slot.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }

        FoodItem foodItem = FoodItem.fromItem(item);
        if (foodItem == null) {
            return false;
        }

        ItemStack updated = ItemUpdater.updateItem(item, foodItem, tray.getId());
        if (updated == null) {
            return false;
        }

        slot.setCurrentItem(updated);
        slot.forceModel(updated);
        return true;
    }
}
