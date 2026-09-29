package net.tfminecraft.cooking.husbandry;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HusbandryMountListener implements Listener {

    @EventHandler
    public void onMount(EntityMountEvent event) {
        Entity mount = event.getMount();
        if (!HusbandryMounts.isMount(mount)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (mount instanceof LivingEntity living) {
            HusbandryAnimal animal = HusbandryMounts.enrollIfNeeded(living);
            if (animal != null) {
            HusbandryMounts.applyStats(living, animal);
            }
        }
        if (!HusbandryOwnershipService.hasAnyOwner(mount.getUniqueId())) {
            HusbandryClaimHint.remind(player, mount);
            return;
        }
        if (HusbandryOwnershipService.isOwner(player, mount.getUniqueId())
                || HusbandryOwnershipService.isStaff(player)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cThis is not your animal.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            HusbandryClaimHint.send(player, event.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HusbandryClaimHint.forget(event.getPlayer().getUniqueId());
    }
}
