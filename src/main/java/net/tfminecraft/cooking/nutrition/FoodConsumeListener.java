package net.tfminecraft.cooking.nutrition;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;

public final class FoodConsumeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null) {
            return;
        }

        if (ItemCache.isCupOfWater(stack) || ItemCache.isCupOfMilk(stack)
                || stack.getType() == Material.MILK_BUCKET) {
            return;
        }

        FoodItem food = FoodItem.fromItem(stack);
        if (food == null) {
            return;
        }

        if (!food.isEdible()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThis needs to be prepared before you can eat it.");
            return;
        }

        food.updateAge();
        NutritionService.tryApplyEat(event.getPlayer(), food);
    }
}
