package net.tfminecraft.cooking.hook;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.carve.CarveHandler;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurniturePunchEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class MeatHookHandler implements Listener {

    private static final String CONTENT_SLOT = "content";

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Furniture> entry : InteractibleFurniture.getInstance()
                        .getFurnitureManager().getPlacedFurniture().entrySet()) {
                    Furniture furniture = entry.getValue();
                    if (FurnitureCache.isMeatHook(furniture)) {
                        tickHook(furniture);
                    }
                }
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
    }

    public void resumeLoadedHooks() {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            resumeHook(furniture);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> resumeHooksInChunk(chunk), 1L);
    }

    @EventHandler
    public void onRoastAdd(FurnitureSlotItemAddEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isMeatHook(furniture)) {
            return;
        }
        if (!event.getSlot().getId().equals(CONTENT_SLOT)) {
            return;
        }
        Bukkit.getScheduler().runTask(Cooking.plugin, () -> syncRoastDisplay(furniture));
    }

    @EventHandler
    public void onPunch(FurniturePunchEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isMeatHook(furniture)) {
            return;
        }
        var contentSlot = furniture.getActiveSlot(CONTENT_SLOT);
        if (contentSlot.isEmpty()) {
            return;
        }
        if (CarveHandler.tryCarve(event.getPlayer(), furniture, contentSlot.get(), event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private void resumeHook(Furniture furniture) {
        if (!FurnitureCache.isMeatHook(furniture)) {
            return;
        }
        if (!furniture.hasActiveSlot(CONTENT_SLOT)) {
            return;
        }
        syncRoastDisplay(furniture);
    }

    private void resumeHooksInChunk(Chunk chunk) {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            if (!furniture.getLoc().getChunk().equals(chunk)) {
                continue;
            }
            resumeHook(furniture);
        }
    }

    private static void tickHook(Furniture furniture) {
        if (!furniture.hasActiveSlot(CONTENT_SLOT)) {
            return;
        }
        PlacedSlot slot = furniture.getActiveSlot(CONTENT_SLOT).orElse(null);
        if (slot == null) {
            return;
        }
        ItemStack stack = slot.getCurrentItem();
        if (stack == null) {
            return;
        }
        FoodItem foodItem = FoodItem.fromItem(stack);
        if (foodItem == null) {
            return;
        }
        ItemStack updated = ItemUpdater.updateItem(stack, foodItem, furniture.getId());
        if (updated == null) {
            return;
        }
        slot.setCurrentItem(updated);
        syncRoastDisplay(furniture);
    }

    private static void syncRoastDisplay(Furniture furniture) {
        furniture.getActiveSlot(CONTENT_SLOT).ifPresent(slot -> {
            ItemStack stack = slot.getCurrentItem();
            if (stack == null) {
                return;
            }
            FoodItem foodItem = FoodItem.fromItem(stack);
            if (foodItem == null) {
                return;
            }
            CarvableRoastUtils.readCarveState(foodItem, stack);
            slot.applyDisplayData(CarvableRoastUtils.getStageModelData(foodItem).getDisplayData(furniture.getId()));
        });
    }
}
