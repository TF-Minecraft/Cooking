package net.tfminecraft.cooking.oven;

import java.util.Map;

import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.baking.BakingTrayBake;
import net.tfminecraft.cooking.baking.BakingTrayBakeApplier;
import net.tfminecraft.cooking.baking.BakingTrayRecipe;
import net.tfminecraft.cooking.baking.BakingTrayRegistry;
import net.tfminecraft.cooking.baking.BakingTrayState;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedFurnitureSlot;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class OvenCavityManager {

    private static final String COOKED_TRACK = "cooked";
    private static final int RAW = 0;
    private static final int COOKED = 1;
    private static final int BURNT = 2;

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
    }

    private void tick() {
        Map<java.util.UUID, Furniture> placed = InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture();

        for (Furniture consumer : placed.values()) {
            if (!HeatSources.isConsumer(consumer)) {
                continue;
            }

            for (PlacedFurnitureSlot slot : consumer.getActiveFurnitureSlots().values()) {
                Furniture tray = slot.getNested();
                if (tray == null || !BakingTrayRegistry.isTray(tray)) {
                    continue;
                }
                tickTray(consumer, tray);
            }
        }
    }

    private void tickTray(Furniture consumer, Furniture tray) {
        BakingTrayRecipe recipe = BakingTrayRegistry.getByFurniture(tray);
        if (recipe == null) {
            return;
        }

        if (!hasBakeableContent(tray, recipe)) {
            return;
        }

        if (!HeatSources.consumerHasHeat(consumer)) {
            return;
        }

        BakingTrayBake bake = recipe.getBake();
        boolean changed = false;

        for (String slotId : recipe.getAllSlotIds()) {
            if (!tray.hasActiveSlot(slotId)) {
                continue;
            }
            PlacedSlot slot = tray.getActiveSlot(slotId).orElse(null);
            if (slot == null) {
                continue;
            }

            var item = slot.getCurrentItem();
            if (item == null || item.getType().isAir()) {
                continue;
            }

            FoodItem foodItem = FoodItem.fromItem(item);
            if (foodItem == null) {
                continue;
            }

            TagTrack cooked = foodItem.getTagTrack(COOKED_TRACK);
            if (cooked == null || cooked.getValue() >= BURNT) {
                continue;
            }

            BakingTrayState.incrementSlotElapsed(tray, slotId);
            int elapsed = BakingTrayState.getSlotElapsed(tray, slotId);
            int cookedValue = cooked.getValue();

            if (cookedValue == RAW && elapsed >= bake.getCookSeconds()) {
                if (BakingTrayBakeApplier.applyCookSlot(tray, slotId)) {
                    changed = true;
                }
            } else if (cookedValue == COOKED && elapsed >= bake.getBurnSeconds()) {
                if (BakingTrayBakeApplier.applyBurnSlot(tray, slotId)) {
                    changed = true;
                }
            }
        }

        if (changed) {
            InteractibleFurniture.getInstance().getFurnitureManager().markDirty(tray);
        }
    }

    private boolean hasBakeableContent(Furniture tray, BakingTrayRecipe recipe) {
        for (String slotId : recipe.getAllSlotIds()) {
            if (!tray.hasActiveSlot(slotId)) {
                continue;
            }
            PlacedSlot slot = tray.getActiveSlot(slotId).orElse(null);
            if (slot == null) {
                continue;
            }
            var item = slot.getCurrentItem();
            if (item == null || item.getType().isAir()) {
                continue;
            }
            FoodItem foodItem = FoodItem.fromItem(item);
            if (foodItem == null) {
                continue;
            }
            TagTrack cooked = foodItem.getTagTrack(COOKED_TRACK);
            if (cooked == null || cooked.getValue() < BURNT) {
                return true;
            }
        }
        return false;
    }
}
