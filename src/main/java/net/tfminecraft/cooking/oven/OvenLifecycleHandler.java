package net.tfminecraft.cooking.oven;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.utils.StationChunks;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class OvenLifecycleHandler implements Listener {
    private final OvenBurnManager burnManager;

    public OvenLifecycleHandler(OvenBurnManager burnManager) {
        this.burnManager = burnManager;
    }

    public void resumeLoadedOvens() {
        burnManager.resumeAll();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> resumeOvensInChunk(chunk), 1L);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Furniture furniture : StationChunks.furnitureIn(chunk)) {
            if (FurnitureCache.isOvenBottom(furniture)) {
                burnManager.stopBurning(furniture.getEntityId());
            }
        }
    }

    @EventHandler
    public void onBreak(FurnitureBreakEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isOvenBottom(furniture)) {
            return;
        }
        burnManager.stopBurning(furniture.getEntityId());
        OvenDisplay.clearAll(furniture);
        OvenState.clear(furniture);
    }

    private void resumeOvensInChunk(Chunk chunk) {
        for (Furniture furniture : StationChunks.furnitureIn(chunk)) {
            if (FurnitureCache.isOvenBottom(furniture)) {
                burnManager.resume(furniture);
            }
        }
    }
}
