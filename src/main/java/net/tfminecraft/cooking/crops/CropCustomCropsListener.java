package net.tfminecraft.cooking.crops;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.momirealms.customcrops.api.core.block.CropBlock;
import net.momirealms.customcrops.api.core.mechanic.crop.CropConfig;
import net.momirealms.customcrops.api.core.world.CustomCropsBlockState;
import net.momirealms.customcrops.api.event.CropBreakEvent;
import net.momirealms.customcrops.api.event.CropInteractEvent;
import net.momirealms.customcrops.api.event.CropPlantEvent;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.utils.InventoryAdder;

public final class CropCustomCropsListener implements Listener {

    private static final double MATCH_RANGE = 1.25;
    private static final int PENDING_TICKS = 2;

    private final Map<String, PendingHarvest> pending = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlant(CropPlantEvent event) {
        if (CropPlantingRule.customRequiresOpenSky(event.cropConfig().id())
                && !CropPlantingRule.hasOpenSky(event.location())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(CropPlantingListener.DENIAL_MESSAGE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(CropBreakEvent event) {
        CropDefinition crop = customCrop(event.cropConfig());
        if (crop == null) {
            return;
        }
        Player player = event.entityBreaker() instanceof Player p ? p : null;
        armHarvest(event.location(), crop, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(CropInteractEvent event) {
        CropDefinition crop = customCrop(event.cropConfig());
        if (crop == null) {
            return;
        }
        ItemStack hand = event.itemInHand();
        if (hand != null && hand.getType() == org.bukkit.Material.BONE_MEAL) {
            return;
        }
        if (!isMature(event.cropConfig(), event.blockState())) {
            return;
        }
        armHarvest(event.location(), crop, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        Location location = entity.getLocation();
        PendingHarvest harvest = findPending(location);
        if (harvest == null) {
            return;
        }
        ItemStack rewritten = CropHarvestItems.rewriteCustomDrop(stack, harvest.crop(), harvest.quality());
        if (rewritten != null && rewritten != stack) {
            entity.setItemStack(rewritten);
        }
    }

    private void armHarvest(Location location, CropDefinition crop, Player player) {
        if (location == null || location.getWorld() == null || Cooking.plugin == null) {
            return;
        }
        expirePending();
        int quality = CropHarvestQuality.roll(crop.id(), location, player);
        String key = key(location);
        long expireAt = System.currentTimeMillis() + PENDING_TICKS * 50L + 50L;
        pending.put(key, new PendingHarvest(crop, quality, location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), expireAt));
        if (player != null) {
            scheduleInventoryRewrite(player, crop, quality);
        }
        Cooking.plugin.getServer().getScheduler().runTaskLater(Cooking.plugin, this::expirePending, PENDING_TICKS);
    }

    private void scheduleInventoryRewrite(Player player, CropDefinition crop, int quality) {
        UUID uuid = player.getUniqueId();
        ItemStack[] before = cloneStorage(player.getInventory());
        Cooking.plugin.getServer().getScheduler().runTaskLater(Cooking.plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                return;
            }
            PlayerInventory inventory = online.getInventory();
            ItemStack[] now = inventory.getStorageContents();
            int length = Math.min(before.length, now.length);
            java.util.List<ItemStack> additions = new java.util.ArrayList<>();
            for (int i = 0; i < length; i++) {
                if (sameStack(before[i], now[i])) {
                    continue;
                }
                ItemStack candidate = now[i];
                boolean grewExisting = before[i] != null && candidate != null && before[i].isSimilar(candidate);
                if (grewExisting) {
                    int gained = candidate.getAmount() - before[i].getAmount();
                    if (gained <= 0) continue;
                    candidate = candidate.clone();
                    candidate.setAmount(gained);
                }
                ItemStack rewritten = CropHarvestItems.rewriteCustomDrop(candidate, crop, quality);
                if (rewritten == null || rewritten == candidate || rewritten.getType().isAir()) continue;
                // Take the harvest back out and add it as food, so it joins produce that only aged differently.
                inventory.setItem(i, grewExisting ? before[i].clone() : null);
                additions.add(rewritten);
            }
            for (ItemStack addition : additions) {
                ItemStack leftover = InventoryAdder.addItem(online, addition);
                if (leftover != null) {
                    online.getWorld().dropItemNaturally(online.getLocation(), leftover);
                }
            }
        }, 1L);
    }

    private PendingHarvest findPending(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        expirePending();
        double rangeSq = MATCH_RANGE * MATCH_RANGE;
        PendingHarvest best = null;
        double bestDist = rangeSq;
        for (PendingHarvest harvest : pending.values()) {
            if (!harvest.worldId().equals(location.getWorld().getUID())) {
                continue;
            }
            double dx = (harvest.x() + 0.5) - location.getX();
            double dy = (harvest.y() + 0.5) - location.getY();
            double dz = (harvest.z() + 0.5) - location.getZ();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = harvest;
            }
        }
        return best;
    }

    private void expirePending() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingHarvest>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expireAt() <= now) {
                iterator.remove();
            }
        }
    }

    private static CropDefinition customCrop(CropConfig config) {
        if (config == null || config.id() == null) {
            return null;
        }
        CropDefinition crop = CropsConfig.crop(config.id());
        if (crop == null || !CropsConfig.SOURCE_CUSTOMCROPS.equals(crop.source())) {
            return null;
        }
        return crop;
    }

    private static boolean isMature(CropConfig config, CustomCropsBlockState state) {
        if (config == null || state == null) {
            return false;
        }
        if (!(state.type() instanceof CropBlock cropBlock)) {
            return false;
        }
        return cropBlock.point(state) >= config.maxPoints();
    }

    private static String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static ItemStack[] cloneStorage(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == null || a.getType().isAir()) {
            return b == null || b.getType().isAir();
        }
        return a.equals(b);
    }

    private record PendingHarvest(
            CropDefinition crop,
            int quality,
            UUID worldId,
            int x,
            int y,
            int z,
            long expireAt) {
    }
}
