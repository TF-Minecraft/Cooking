package net.tfminecraft.cooking.trough;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class TroughHandler implements Listener {

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isTrough(furniture)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;

        if (emptyHand && player.isSneaking()) {
            return;
        }

        event.setCancelled(true);

        if (emptyHand) {
            if (isFull(furniture)) {
                craft(furniture, player);
            }
            return;
        }

        FoodItem food = TroughIngredients.resolveHand(player, hand);
        hand = player.getInventory().getItemInMainHand();
        if (!TroughIngredients.isAllowed(food)) {
            if (food != null && TroughIngredients.isRotten(food)) {
                player.sendMessage("§cRotten food cannot go in the trough.");
            } else {
                player.sendMessage("§cOnly wheat or vegetables can go in the trough.");
            }
            return;
        }

        int perClick = ItemCache.troughItemsPerClick;
        if (hand.getAmount() < perClick) {
            player.sendMessage("§cAdd " + perClick + " at a time.");
            return;
        }

        String slotId = findFirstEmpty(furniture);
        if (slotId == null) {
            player.sendMessage("§cThe trough is full.");
            return;
        }

        ItemStack toPlace = hand.clone();
        toPlace.setAmount(perClick);
        PlacedSlot slot = furniture.getOrCreatePlacedSlot(slotId);
        slot.setCurrentItem(toPlace);
        slot.forceModel(toPlace);
        hand.setAmount(hand.getAmount() - perClick);
        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        markDirty(furniture);
    }

    private static void craft(Furniture furniture, Player player) {
        ItemStack feed = mintFeed();
        if (feed == null) {
            player.sendMessage("§cCould not create feed.");
            return;
        }

        for (String slotId : TroughSlots.ALL) {
            furniture.removeActiveSlot(slotId);
        }

        // Snapshot before inventory insertion, which may mutate the stack.
        DishCookedEvent completed = new DishCookedEvent(player, feed, "trough");
        ItemStack leftover = InventoryAdder.addItem(player, feed);
        if (leftover != null) {
            furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), leftover);
        }
        markDirty(furniture);
        Bukkit.getPluginManager().callEvent(completed);

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        furniture.getLoc().getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                furniture.getLoc().clone().add(0.5, 0.35, 0.5),
                10,
                0.25, 0.1, 0.25,
                0.02);
    }

    private static ItemStack mintFeed() {
        String path = ItemCache.troughFeed;
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            ItemStack stack = TLibs.getItemAPI().getCreator().getItemFromPath(path);
            if (stack == null) {
                return null;
            }
            stack.setAmount(1);
            return stack;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static boolean isFull(Furniture furniture) {
        for (String slotId : TroughSlots.ALL) {
            if (!isOccupied(furniture, slotId)) {
                return false;
            }
        }
        return true;
    }

    static String findFirstEmpty(Furniture furniture) {
        for (String slotId : TroughSlots.ALL) {
            if (!isOccupied(furniture, slotId)) {
                return slotId;
            }
        }
        return null;
    }

    static boolean isOccupied(Furniture furniture, String slotId) {
        if (!furniture.hasActiveSlot(slotId)) {
            return false;
        }
        ItemStack stack = furniture.getActiveSlot(slotId).map(PlacedSlot::getCurrentItem).orElse(null);
        return stack != null && !stack.getType().isAir();
    }

    static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
