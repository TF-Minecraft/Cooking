package net.tfminecraft.cooking.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cooking.CookingReference;
import net.tfminecraft.cooking.cooking.FryingReference;
import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.cooking.SauceReference;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public class CookingManager implements Listener {

    private Map<UUID, CookingReference> stations = new HashMap<>();

    public void start() {
        tickCycle();
    }

    public void resumeLoadedStations() {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            resumeStation(furniture);
        }
    }

    private void resumeStation(Furniture furniture) {
        if (furniture == null || furniture.getType() == null) {
            return;
        }
        if (FurnitureCache.getByFurniture(furniture) == Method.NONE) {
            return;
        }
        boolean hasCookingSlot = false;
        for (String slotId : furniture.getType().getSlots().keySet()) {
            if (furniture.hasActiveSlot(slotId)) {
                hasCookingSlot = true;
                break;
            }
        }
        if (!hasCookingSlot) {
            return;
        }
        getOrCreateReference(furniture);
    }

    private void resumeStationsInChunk(Chunk chunk) {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            if (!furniture.getLoc().getChunk().equals(chunk)) {
                continue;
            }
            resumeStation(furniture);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> resumeStationsInChunk(chunk), 1L);
    }

    private CookingReference createReference(Furniture furniture, Method method) {
        if (method == Method.FRYING_PAN) {
            return new FryingReference(furniture, method);
        }
        if (method == Method.SAUCEPAN) {
            return new SauceReference(furniture, method);
        }
        if (method == Method.POT) {
            return new PotReference(furniture, method);
        }
        return new CookingReference(furniture, method);
    }

    private CookingReference getOrCreateReference(Furniture furniture) {
        CookingReference existing = stations.get(furniture.getEntityId());
        if (existing != null) {
            return existing;
        }
        Method method = FurnitureCache.getByFurniture(furniture);
        if (method == Method.NONE) {
            return null;
        }
        CookingReference ref = createReference(furniture, method);
        ref.rebuildFromFurniture();
        stations.put(furniture.getEntityId(), ref);
        return ref;
    }

    public void tickCycle() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for(Map.Entry<UUID, CookingReference> entry : stations.entrySet()) {
                    entry.getValue().tick();
                }
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
    }

    @EventHandler
    public void empty(PlayerInteractEvent e) {
        if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if(item == null) return;
        if(!e.getClickedBlock().getType().equals(Material.CAULDRON)) return;
        FoodItem fi = FoodItem.fromItem(item);
        if(fi == null) return;
        if(fi.getCategory().equalsIgnoreCase("sauce") || fi.getCategory().equalsIgnoreCase("soup")) {
            e.setCancelled(true);
            p.getInventory().setItemInMainHand(TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.ladle));
        }
    }
    @EventHandler
    public void interact(FurnitureInteractEvent e) {
        Furniture f = e.getFurniture();
        CookingReference ref = getOrCreateReference(f);
        if (ref != null) {
            ref.interact(e);
        }
    }

    @EventHandler
    public void breakEvent(FurnitureBreakEvent e) {
        Furniture f = e.getFurniture();
        if(stations.containsKey(f.getEntityId())) {
            CookingReference ref = stations.remove(f.getEntityId());
            if(ref instanceof SauceReference) ref.clear();
            if(ref instanceof PotReference) ref.clear();
            else ref.remove();
        }
    }

    @EventHandler
    public void addItem(FurnitureSlotItemAddEvent e) {
        CookingReference ref = getOrCreateReference(e.getFurniture());
        if (ref != null) {
            ref.slotAdd(e);
        }
    }

    @EventHandler
    public void takeItem(FurnitureSlotItemTakeEvent e) {
        CookingReference ref = getOrCreateReference(e.getFurniture());
        if (ref != null) {
            ref.slotRemove(e);
        }
    }
}
