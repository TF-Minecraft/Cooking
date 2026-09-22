package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.cooking.Cooking;

public final class FoodLevelChangeGuard implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        RPCharacter character = Bukkit.getPluginManager().isPluginEnabled("RPCharacters")
                ? RPCharacters.getActiveCharacter(player)
                : null;
        if (NutritionDisplayService.isSyncing(player)) {
            NutritionLog.append("VANILLA_FOOD_EVENT", player, character,
                    "current=" + player.getFoodLevel()
                    + " proposed=" + event.getFoodLevel()
                    + " action=allow-sync-origin");
            return;
        }
        NutritionLog.append("VANILLA_FOOD_EVENT", player, character,
                "current=" + player.getFoodLevel()
                + " proposed=" + event.getFoodLevel()
                + " action=cancel-and-defer");
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(Cooking.plugin,
                () -> NutritionDisplayService.syncFromPlayer(player, "vanilla-change"));
    }
}
