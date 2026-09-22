package net.tfminecraft.cooking.oven;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.cooking.baking.BakingTrayRegistry;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotFurnitureAddEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.SlotType;

public final class OvenCavityHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnitureAdd(FurnitureSlotFurnitureAddEvent event) {
        Furniture parent = event.getFurniture();
        if (!HeatSources.isConsumer(parent)) {
            return;
        }
        if (event.getSlot().getSlotType() != SlotType.FURNITURE) {
            return;
        }

        Furniture nested = event.getNested();
        if (nested == null || BakingTrayRegistry.isTray(nested)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cOnly baking trays can go in the oven.");
    }
}
