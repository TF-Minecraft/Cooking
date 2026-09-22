package net.tfminecraft.cooking.nutrition;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;

public final class NutritionDisplayService {

    private static final Set<UUID> syncingPlayers = new HashSet<>();

    private NutritionDisplayService() {}

    public static int toFoodLevel(int foodValue) {
        int max = NutritionConfig.maxFood();
        int clamped = Math.max(0, Math.min(foodValue, max));
        double scale = max / 20.0;
        return Math.min(20, Math.max(0, (int) Math.round(clamped / scale)));
    }

    public static boolean isSyncing(Player player) {
        return player != null && syncingPlayers.contains(player.getUniqueId());
    }

    public static void sync(Player player, RPCharacter character, String reason) {
        if (player == null || character == null) {
            NutritionLog.append("SYNC_SKIP", player, character, "reason=" + reason + " cause=null-input");
            return;
        }
        UUID id = player.getUniqueId();
        syncingPlayers.add(id);
        try {
            int mapped = toFoodLevel(character.getFoodValue());
            int foodBefore = player.getFoodLevel();
            float saturationBefore = player.getSaturation();
            boolean wroteFood = foodBefore != mapped;
            if (wroteFood) {
                player.setFoodLevel(mapped);
            }
            player.setSaturation(0f);
            NutritionLog.append("SYNC", player, character,
                    "reason=" + reason
                    + " mapped=" + mapped
                    + " hudBefore=" + foodBefore
                    + " hudAfter=" + player.getFoodLevel()
                    + " saturationBefore=" + saturationBefore
                    + " saturationAfter=" + player.getSaturation()
                    + " wroteFood=" + wroteFood);
        } finally {
            syncingPlayers.remove(id);
        }
    }

    public static void sync(Player player, RPCharacter character) {
        sync(player, character, "unspecified");
    }

    public static void syncFromPlayer(Player player, String reason) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            NutritionLog.append("SYNC_SKIP", player, null,
                    "reason=" + reason + " cause=rpcharacters-unavailable");
            return;
        }
        RPCharacter character = RPCharacters.getActiveCharacter(player);
        if (character == null) {
            NutritionLog.append("SYNC_SKIP", player, null,
                    "reason=" + reason + " cause=no-active-character"
                    + " hud=" + player.getFoodLevel()
                    + " saturation=" + player.getSaturation());
            return;
        }
        sync(player, character, reason);
    }

    public static void syncFromPlayer(Player player) {
        syncFromPlayer(player, "unspecified");
    }
}
