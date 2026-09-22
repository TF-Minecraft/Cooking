package net.tfminecraft.cooking.oven;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class OvenBurnManager {
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Random random = new Random();

    public void startBurning(Furniture furniture) {
        if (furniture == null || !FurnitureCache.isOvenBottom(furniture)) {
            return;
        }
        if (!OvenState.hasHeat(furniture)) {
            return;
        }
        if (activeTasks.containsKey(furniture.getEntityId())) {
            return;
        }

        long interval = Math.max(1, ItemCache.ovenBurnIntervalTicks);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                Cooking.plugin,
                () -> tick(furniture),
                interval,
                interval);
        activeTasks.put(furniture.getEntityId(), task);
    }

    public void stopBurning(UUID furnitureId) {
        if (furnitureId == null) {
            return;
        }
        BukkitTask task = activeTasks.remove(furnitureId);
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    public void resume(Furniture furniture) {
        if (furniture == null || !FurnitureCache.isOvenBottom(furniture)) {
            return;
        }
        OvenDisplay.syncAll(furniture);
        if (OvenState.hasHeat(furniture)) {
            startBurning(furniture);
        } else if (OvenState.isLit(furniture) && !OvenState.hasFuel(furniture)) {
            OvenState.setLit(furniture, false);
            OvenDisplay.syncAll(furniture);
            markDirty(furniture);
        }
    }

    public void resumeAll() {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            resume(furniture);
        }
    }

    private void tick(Furniture furniture) {
        if (furniture == null || !FurnitureCache.isOvenBottom(furniture)) {
            stopBurning(furniture != null ? furniture.getEntityId() : null);
            return;
        }
        if (!InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture()
                .containsKey(furniture.getEntityId())) {
            stopBurning(furniture.getEntityId());
            return;
        }

        boolean changed = false;
        String[] activeLayer = OvenState.getActiveBurnLayer(furniture);
        if (activeLayer != null) {
            for (String slotId : activeLayer) {
                OvenSlots.WoodStage stage = OvenState.getStage(furniture, slotId);
                if (stage == OvenSlots.WoodStage.FRESH && random.nextFloat() < ItemCache.ovenBurnChanceFresh) {
                    OvenState.setStage(furniture, slotId, OvenSlots.WoodStage.BURNT);
                    changed = true;
                } else if (stage == OvenSlots.WoodStage.BURNT && random.nextFloat() < ItemCache.ovenBurnChanceBurnt) {
                    OvenState.setStage(furniture, slotId, OvenSlots.WoodStage.EMPTY);
                    changed = true;
                }
            }
        }

        if (OvenState.isLit(furniture) && !OvenState.hasFuel(furniture)) {
            OvenState.setLit(furniture, false);
            changed = true;
        }

        if (changed) {
            OvenDisplay.syncAll(furniture);
            markDirty(furniture);
        }

        if (!OvenState.hasHeat(furniture)) {
            stopBurning(furniture.getEntityId());
            if (changed) {
                OvenDisplay.syncAll(furniture);
            }
            return;
        }

        // Ambience rolls every burn tick while lit, independent of fuel stage changes.
        OvenEffects.playAmbience(furniture, random);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
