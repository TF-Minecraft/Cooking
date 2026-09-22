package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.cooking.Cooking;

public final class NutritionDrainTask {

    private static final long TICK_PERIOD = 20L;

    private static int taskId = -1;
    private static int elapsedSeconds;

    private NutritionDrainTask() {}

    public static void start() {
        if (taskId != -1) {
            return;
        }
        elapsedSeconds = 0;
        taskId = Bukkit.getScheduler().runTaskTimer(
                Cooking.plugin, NutritionDrainTask::tick, TICK_PERIOD, TICK_PERIOD).getTaskId();
        NutritionLog.append("DRAIN_TASK", null, null,
                "action=start periodTicks=" + TICK_PERIOD
                + " intervalSeconds=" + NutritionConfig.drainIntervalSeconds()
                + " amount=" + NutritionConfig.drainAmount());
    }

    public static void stop() {
        if (taskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        elapsedSeconds = 0;
        NutritionLog.append("DRAIN_TASK", null, null, "action=stop");
    }

    private static void tick() {
        elapsedSeconds += 1;
        int interval = Math.max(1, NutritionConfig.drainIntervalSeconds());
        boolean due = elapsedSeconds >= interval;
        int elapsedAtPulse = elapsedSeconds;
        if (due) {
            elapsedSeconds -= interval;
        }
        NutritionLog.append("DRAIN_PULSE", null, null,
                "elapsed=" + elapsedAtPulse
                + " interval=" + interval
                + " due=" + due
                + " carry=" + elapsedSeconds
                + " online=" + Bukkit.getOnlinePlayers().size());
        inspectPlayers(due, elapsedAtPulse, interval);
    }

    private static void inspectPlayers(boolean due, int elapsedAtPulse, int interval) {
        if (!Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            NutritionLog.append("DRAIN_SKIP", null, null,
                    "elapsed=" + elapsedAtPulse + " interval=" + interval
                    + " cause=rpcharacters-unavailable");
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            RPCharacter character = RPCharacters.getActiveCharacter(player);
            if (character == null) {
                NutritionLog.append("DRAIN_SKIP", player, null,
                        "elapsed=" + elapsedAtPulse + " interval=" + interval
                        + " cause=no-active-character"
                        + " hud=" + player.getFoodLevel()
                        + " saturation=" + player.getSaturation());
                continue;
            }
            int currentFood = character.getFoodValue();
            int mappedBefore = NutritionDisplayService.toFoodLevel(currentFood);
            if (!due) {
                NutritionLog.append("DRAIN_WAIT", player, character,
                        "elapsed=" + elapsedAtPulse + " interval=" + interval
                        + " mapped=" + mappedBefore
                        + " hud=" + player.getFoodLevel()
                        + " saturation=" + player.getSaturation());
                continue;
            }

            if (currentFood <= 0) {
                NutritionLog.append("DRAIN_SKIP", player, character,
                        "elapsed=" + elapsedAtPulse + " interval=" + interval
                        + " cause=empty"
                        + " mapped=" + mappedBefore
                        + " hud=" + player.getFoodLevel());
                continue;
            }

            int newValue = Math.max(0, currentFood - NutritionConfig.drainAmount());
            if (newValue == currentFood) {
                NutritionLog.append("DRAIN_SKIP", player, character,
                        "elapsed=" + elapsedAtPulse + " interval=" + interval
                        + " cause=no-change amount=" + NutritionConfig.drainAmount());
                continue;
            }

            character.setFoodValue(newValue);
            NutritionLog.append("DRAIN", player, character,
                    "elapsed=" + elapsedAtPulse
                    + " interval=" + interval
                    + " amount=" + NutritionConfig.drainAmount()
                    + " foodBefore=" + currentFood
                    + " foodAfter=" + character.getFoodValue()
                    + " mappedBefore=" + mappedBefore
                    + " mappedAfter=" + NutritionDisplayService.toFoodLevel(character.getFoodValue())
                    + " hudBefore=" + player.getFoodLevel()
                    + " saturation=" + player.getSaturation());
            NutritionDisplayService.sync(player, character, "drain");
            RPCharacters.getPlayerManager().savePlayer(player);
            NutritionLog.append("SAVE", player, character, "reason=drain");
        }
    }
}
