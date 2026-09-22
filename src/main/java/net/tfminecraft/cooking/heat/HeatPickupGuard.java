package net.tfminecraft.cooking.heat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;

public final class HeatPickupGuard implements Listener {

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        if (!wouldAttemptPickup(event.getPlayer(), event.getFurniture())) {
            return;
        }

        Furniture furniture = event.getFurniture();
        if (!HeatSources.isSource(furniture) || !HeatSources.hasBlockingConsumerAbove(furniture)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cRemove the piece above before picking this up.");
    }

    private static boolean wouldAttemptPickup(Player player, Furniture furniture) {
        if (player == null || furniture == null) {
            return false;
        }
        if (player.isSneaking()) {
            return false;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return false;
        }

        FurnitureType type = furniture.getType();
        if (type == null || !type.canPickup()) {
            return false;
        }
        if (!furniture.getActiveSlots().isEmpty()) {
            return false;
        }
        if (!furniture.getActiveFurnitureSlots().isEmpty()) {
            return false;
        }

        return true;
    }
}
