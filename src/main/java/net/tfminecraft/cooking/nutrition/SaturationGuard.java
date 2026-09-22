package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.cooking.Cooking;

public final class SaturationGuard implements Listener {

    private static int taskId = -1;

    public static void start() {
        if (taskId != -1) {
            return;
        }
        taskId = Bukkit.getScheduler().runTaskTimer(Cooking.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getSaturation() != 0f) {
                    float before = player.getSaturation();
                    player.setSaturation(0f);
                    NutritionLog.append("SATURATION", player, activeCharacter(player),
                            "reason=timer before=" + before + " after=" + player.getSaturation());
                }
            }
        }, 40L, 40L).getTaskId();
    }

    public static void stop() {
        if (taskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        float before = player.getSaturation();
        player.setSaturation(0f);
        NutritionLog.append("SATURATION", player, null,
                "reason=join before=" + before + " after=" + player.getSaturation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        float eventSaturation = player.getSaturation();
        Bukkit.getScheduler().runTask(Cooking.plugin, () -> {
            float before = player.getSaturation();
            player.setSaturation(0f);
            NutritionLog.append("SATURATION", player, activeCharacter(player),
                    "reason=food-event eventValue=" + eventSaturation
                    + " before=" + before + " after=" + player.getSaturation());
        });
    }

    private static RPCharacter activeCharacter(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            return null;
        }
        return RPCharacters.getActiveCharacter(player);
    }
}
