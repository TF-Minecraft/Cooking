package net.tfminecraft.cooking.liquid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class LiquidContainerAging {
    private static final Map<UUID, BukkitTask> tasks = new HashMap<>();

    private LiquidContainerAging() {}

    public static void start(Furniture furniture) {
        if (furniture == null) {
            return;
        }
        if (!LiquidContainerState.TYPE_MILK.equalsIgnoreCase(LiquidContainerState.getType(furniture))
                || LiquidContainerState.isEmpty(furniture)) {
            stop(furniture);
            return;
        }

        UUID id = furniture.getEntityId();
        if (tasks.containsKey(id)) {
            return;
        }

        LiquidContainerState.touchLastUpdate(furniture);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shouldKeepAging(furniture)) {
                    stop(furniture);
                    return;
                }
                LiquidContainerState.tickAge(furniture);
                markDirty(furniture);
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
        tasks.put(id, task);
    }

    public static void stop(Furniture furniture) {
        if (furniture == null) {
            return;
        }
        BukkitTask task = tasks.remove(furniture.getEntityId());
        if (task != null) {
            task.cancel();
        }
    }

    public static void stopAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    private static boolean shouldKeepAging(Furniture furniture) {
        return LiquidContainerState.TYPE_MILK.equalsIgnoreCase(LiquidContainerState.getType(furniture))
                && !LiquidContainerState.isEmpty(furniture);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
