package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.PlayerActionContext;

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

        PlayerActionContext context = PlayerActionContext.captureOrRefuse(event.getPlayer());
        if (context == null) {
            event.setCancelled(true);
            return;
        }
        food.updateAge();
        // Vanilla spends the food after this event; never grant nutrition before that commit.
        Bukkit.getScheduler().runTask(Cooking.plugin, () -> {
            if (!event.isCancelled() && context.isCurrent()) {
                NutritionService.tryApplyEat(context.player(), food);
            }
        });
    }
}
