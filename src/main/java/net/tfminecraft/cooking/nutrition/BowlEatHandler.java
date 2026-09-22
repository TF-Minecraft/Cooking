package net.tfminecraft.cooking.nutrition;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.manager.PlateManager;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class BowlEatHandler implements Listener {

    private final PlateManager plateManager;

    public BowlEatHandler(PlateManager plateManager) {
        this.plateManager = plateManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isBowl(furniture)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() != Material.AIR) {
            return;
        }

        if (!furniture.hasActiveSlot("food_item")) {
            return;
        }

        PlacedSlot foodSlot = furniture.getActiveSlots().get("food_item");
        if (foodSlot == null) {
            return;
        }

        ItemStack soupStack = foodSlot.getCurrentItem();
        if (soupStack == null) {
            return;
        }

        FoodItem food = FoodItem.fromItem(soupStack);
        if (food == null || !food.getCategory().equalsIgnoreCase("soup")) {
            return;
        }

        event.setCancelled(true);
        food.updateAge();
        NutritionService.tryApplyEat(player, food);

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);

        plateManager.clear(furniture);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
