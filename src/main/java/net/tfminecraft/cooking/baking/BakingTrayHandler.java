package net.tfminecraft.cooking.baking;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.WarmthUtils;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class BakingTrayHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        BakingTrayRecipe recipe = BakingTrayRegistry.getByFurniture(furniture);
        if (recipe == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;

        if (furniture.isAttached()) {
            if (!emptyHand && matchesFillInput(recipe, hand)) {
                event.setCancelled(true);
                player.sendMessage("§cRemove the tray from the oven before adding dough.");
                return;
            }
            if (emptyHand) {
                if (player.isSneaking()) {
                    return;
                }
                if (handleTake(furniture, recipe, player, event)) {
                    event.setCancelled(true);
                }
                return;
            }
            if (matchesPlaceableLoaf(recipe, hand)) {
                handlePlaceLoaf(furniture, recipe, player, hand, event);
            }
            return;
        }

        if (emptyHand) {
            if (player.isSneaking()) {
                return;
            }
            if (handleTake(furniture, recipe, player, event)) {
                event.setCancelled(true);
            }
            return;
        }

        if (matchesFillInput(recipe, hand)) {
            handleFill(furniture, recipe, player, hand, event);
            return;
        }

        if (matchesPlaceableLoaf(recipe, hand)) {
            handlePlaceLoaf(furniture, recipe, player, hand, event);
        }
    }

    private void handleFill(Furniture furniture, BakingTrayRecipe recipe, Player player, ItemStack hand,
            FurnitureInteractEvent event) {
        String closestSlot = BakingTraySlots.findClosestSlot(furniture, recipe, event.getClickPoint());
        String preferredMold = closestSlot != null ? recipe.getMoldForSlot(closestSlot) : null;
        String moldId = BakingTraySlots.findFillableMold(furniture, recipe, preferredMold);
        if (moldId == null) {
            event.setCancelled(true);
            player.sendMessage("§cThe tray molds are full.");
            return;
        }

        BakingTrayFill fill = recipe.getFill();
        FoodItem doughItem = FoodItem.fromItem(hand);
        if (doughItem == null) {
            event.setCancelled(true);
            player.sendMessage("§cFailed to read dough from hand.");
            return;
        }

        FoodItem loafItem = BakingTrayTransform.doughToLoaf(doughItem, fill);
        if (loafItem == null) {
            event.setCancelled(true);
            player.sendMessage("§cFailed to create bread from recipe.");
            return;
        }

        int quality = doughItem.getQualityMin();
        ItemStack loaf = ItemBuilder.buildComposedWithQuality(loafItem, quality);
        if (loaf == null || FoodItem.fromItem(loaf) == null) {
            event.setCancelled(true);
            player.sendMessage("§cFailed to create bread from recipe.");
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        for (String slotId : recipe.getMoldSlotIds(moldId)) {
            ItemStack slotItem = loaf.clone();
            slotItem.setAmount(1);
            PlacedSlot slot = furniture.getOrCreatePlacedSlot(slotId);
            slot.setCurrentItem(slotItem);
            slot.forceModel(slotItem);
        }

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), "block.loom.use", 1f, 1f);
        BakingTrayState.clearSlots(furniture, recipe.getMoldSlotIds(moldId));
        markDirty(furniture);
        event.setCancelled(true);
    }

    private void handlePlaceLoaf(Furniture furniture, BakingTrayRecipe recipe, Player player, ItemStack hand,
            FurnitureInteractEvent event) {
        String slotId = BakingTraySlots.findClosestEmptySlot(furniture, recipe, event.getClickPoint());
        if (slotId == null) {
            event.setCancelled(true);
            player.sendMessage("§cThe tray is full.");
            return;
        }

        ItemStack toPlace = hand.clone();
        toPlace.setAmount(1);
        PlacedSlot slot = furniture.getOrCreatePlacedSlot(slotId);
        slot.setCurrentItem(toPlace);
        slot.forceModel(toPlace);
        hand.setAmount(hand.getAmount() - 1);

        BakingTrayState.setSlotElapsed(furniture, slotId, 0);
        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        markDirty(furniture);
        event.setCancelled(true);
    }

    private boolean handleTake(Furniture furniture, BakingTrayRecipe recipe, Player player,
            FurnitureInteractEvent event) {
        String slotId = BakingTraySlots.findClosestOccupiedSlot(furniture, recipe, event.getClickPoint());
        if (slotId == null) {
            return false;
        }

        PlacedSlot slot = furniture.getActiveSlot(slotId).orElse(null);
        if (slot == null) {
            return false;
        }

        ItemStack item = slot.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }

        FoodItem foodItem = FoodItem.fromItem(item);
        if (foodItem == null) {
            return false;
        }

        ItemStack toGive;
        if (WarmthUtils.isHeated(foodItem, 1)) {
            WarmthUtils.applyHot(foodItem);
            toGive = ItemUpdater.applyItemUpdate(item, foodItem, furniture.getId());
            if (toGive == null) {
                return false;
            }
        } else {
            toGive = item.clone();
        }
        toGive.setAmount(1);
        ItemStack leftover = InventoryAdder.addItem(player, toGive);
        if (leftover != null) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            slot.setCurrentItem(item);
            slot.forceModel(item);
        } else {
            slot.clearModel();
            BakingTrayState.clearSlot(furniture, slotId);
        }

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        markDirty(furniture);
        return true;
    }

    private static boolean matchesFillInput(BakingTrayRecipe recipe, ItemStack hand) {
        if (hand == null) {
            return false;
        }
        BakingTrayFill fill = recipe.getFill();
        if (fill.getInputFood() != null) {
            FoodItem foodItem = FoodItem.fromItem(hand);
            if (foodItem != null && foodItem.getId().equalsIgnoreCase(fill.getInputFood())) {
                return true;
            }
        }
        String input = fill.getInput();
        if (input == null) {
            return false;
        }
        return TLibs.getItemAPI().getChecker().checkItemWithPath(hand, input);
    }

    private static boolean matchesPlaceableLoaf(BakingTrayRecipe recipe, ItemStack hand) {
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        FoodItem foodItem = FoodItem.fromItem(hand);
        if (foodItem == null) {
            return false;
        }
        String expectedFood = recipe.getFill().getFood();
        return expectedFood != null && foodItem.getId().equalsIgnoreCase(expectedFood);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
