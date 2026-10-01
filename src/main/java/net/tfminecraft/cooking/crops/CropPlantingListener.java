package net.tfminecraft.cooking.crops;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class CropPlantingListener implements Listener {
    public static final String DENIAL_MESSAGE = ChatColor.RED + "Crops must be planted outdoors with open sky above them.";

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        Block crop = event.getBlockPlaced();
        if (CropPlantingRule.requiresOpenSky(crop.getType())
                && !CropPlantingRule.hasOpenSky(crop.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(DENIAL_MESSAGE);
        }
    }
}
