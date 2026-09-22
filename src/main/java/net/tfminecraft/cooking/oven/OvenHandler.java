package net.tfminecraft.cooking.oven;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class OvenHandler implements Listener {
    private final OvenBurnManager burnManager;

    public OvenHandler(OvenBurnManager burnManager) {
        this.burnManager = burnManager;
    }

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isOvenBottom(furniture)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;

        if (emptyHand) {
            if (OvenState.isLit(furniture) || OvenState.getWoodCount(furniture) > 0) {
                event.setCancelled(true);
            }
            return;
        }

        if (hand.getType() == Material.FLINT_AND_STEEL) {
            handleLight(furniture, player, hand, event);
            return;
        }

        if (ItemCache.matchesOvenFuel(hand)) {
            handleAddFuel(furniture, player, hand, event);
            return;
        }
    }

    private void handleAddFuel(Furniture furniture, Player player, ItemStack hand, FurnitureInteractEvent event) {
        if (OvenState.isFull(furniture)) {
            event.setCancelled(true);
            player.sendMessage("§cThe oven is full of wood.");
            return;
        }

        String slotId = OvenState.findNextFillSlot(furniture);
        if (slotId == null) {
            event.setCancelled(true);
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        OvenState.setStage(furniture, slotId, OvenSlots.WoodStage.FRESH);
        OvenDisplay.syncAll(furniture);
        markDirty(furniture);

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.BLOCK_WOOD_PLACE, 1f, 1f);
        event.setCancelled(true);
    }

    private void handleLight(Furniture furniture, Player player, ItemStack hand, FurnitureInteractEvent event) {
        if (OvenState.isLit(furniture)) {
            event.setCancelled(true);
            return;
        }

        if (!OvenState.hasFuel(furniture)) {
            event.setCancelled(true);
            player.sendMessage("§cAdd wood before lighting the oven.");
            return;
        }

        damageFlintAndSteel(hand, player);
        OvenState.setLit(furniture, true);
        OvenDisplay.syncAll(furniture);
        burnManager.startBurning(furniture);
        markDirty(furniture);

        player.swingMainHand();
        OvenEffects.playIgnite(furniture);
        event.setCancelled(true);
    }

    private static void damageFlintAndSteel(ItemStack hand, Player player) {
        if (hand.getItemMeta() instanceof Damageable damageable) {
            int damage = damageable.getDamage() + 1;
            int max = hand.getType().getMaxDurability();
            if (max > 0 && damage >= max) {
                player.getInventory().setItemInMainHand(null);
            } else {
                damageable.setDamage(damage);
                hand.setItemMeta(damageable);
            }
        }
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
