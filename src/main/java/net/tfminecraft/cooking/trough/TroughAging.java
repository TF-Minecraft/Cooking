package net.tfminecraft.cooking.trough;

import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class TroughAging {

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
    }

    private void tick() {
        Map<UUID, Furniture> placed = InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture();

        for (Furniture furniture : placed.values()) {
            if (!FurnitureCache.isTrough(furniture)) {
                continue;
            }
            ageTrough(furniture);
        }
    }

    private void ageTrough(Furniture furniture) {
        boolean changed = false;
        for (String slotId : TroughSlots.ALL) {
            if (!furniture.hasActiveSlot(slotId)) {
                continue;
            }
            PlacedSlot slot = furniture.getActiveSlot(slotId).orElse(null);
            if (slot == null) {
                continue;
            }
            if (ageSlot(furniture, slot, slotId)) {
                changed = true;
            }
        }
        if (changed) {
            TroughHandler.markDirty(furniture);
        }
    }

    private boolean ageSlot(Furniture furniture, PlacedSlot slot, String slotId) {
        ItemStack item = slot.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }

        FoodItem foodItem = FoodItem.fromItem(item);
        if (foodItem == null) {
            eject(furniture, slotId, item);
            return true;
        }

        ItemStack updated = ItemUpdater.updateItem(item, foodItem, furniture.getId());
        if (updated != null) {
            item = updated;
            slot.setCurrentItem(updated);
            slot.forceModel(updated);
            foodItem = FoodItem.fromItem(updated);
        }

        if (foodItem == null || TroughIngredients.isRotten(foodItem)) {
            eject(furniture, slotId, item);
            return true;
        }
        return updated != null;
    }

    private static void eject(Furniture furniture, String slotId, ItemStack item) {
        furniture.removeActiveSlot(slotId);
        if (item != null && !item.getType().isAir() && furniture.getLoc() != null && furniture.getLoc().getWorld() != null) {
            furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), item);
        }
    }
}
