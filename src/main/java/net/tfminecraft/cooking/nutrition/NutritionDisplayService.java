package net.tfminecraft.cooking.nutrition;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

public final class NutritionDisplayService {

    private static final Map<CharacterSession, Integer> syncingSessions = new HashMap<>();

    private NutritionDisplayService() {}

    public static int toFoodLevel(int foodValue) {
        int max = NutritionConfig.maxFood();
        int clamped = Math.max(0, Math.min(foodValue, max));
        double scale = max / 20.0;
        return Math.min(20, Math.max(0, (int) Math.round(clamped / scale)));
    }

    public static boolean isSyncing(Player player) {
        return player != null && Characters.currentSession(player)
                .map(syncingSessions::containsKey).orElse(false);
    }

    public static void sync(Player player, RPCharacter character, String reason) {
        if (player == null || character == null) {
            NutritionLog.append("SYNC_SKIP", player, character, "reason=" + reason + " cause=null-input");
            return;
        }
        NutritionContext context = NutritionContext.capture(player, character);
        if (context == null) {
            NutritionLog.append("SYNC_SKIP", player, character,
                    "reason=" + reason + " cause=not-ready-or-character-mismatch");
            return;
        }
        sync(context, reason);
    }

    public static void sync(NutritionContext context, String reason) {
        if (context == null || !context.isCurrent()) return;
        Player player = context.player();
        RPCharacter character = context.character();
        CharacterSession session = context.session();
        syncingSessions.merge(session, 1, Integer::sum);
        try {
            int mapped = toFoodLevel(character.getFoodValue());
            int foodBefore = player.getFoodLevel();
            float saturationBefore = player.getSaturation();
            boolean wroteFood = foodBefore != mapped;
            if (!context.isCurrent()) return;
            if (wroteFood) {
                player.setFoodLevel(mapped);
            }
            if (!context.isCurrent()) return;
            player.setSaturation(0f);
            if (!context.isCurrent()) return;
            NutritionLog.append("SYNC", player, character,
                    "reason=" + reason
                    + " mapped=" + mapped
                    + " hudBefore=" + foodBefore
                    + " hudAfter=" + player.getFoodLevel()
                    + " saturationBefore=" + saturationBefore
                    + " saturationAfter=" + player.getSaturation()
                    + " wroteFood=" + wroteFood);
        } finally {
            syncingSessions.computeIfPresent(session, (key, count) -> count > 1 ? count - 1 : null);
        }
    }

    public static void sync(Player player, RPCharacter character) {
        sync(player, character, "unspecified");
    }

    public static void syncFromPlayer(Player player, String reason) {
        NutritionContext context = NutritionContext.capture(player);
        if (context == null) {
            NutritionLog.append("SYNC_SKIP", player, null,
                    "reason=" + reason + " cause=no-ready-character");
            return;
        }
        sync(context, reason);
    }

    public static void syncFromPlayer(Player player) {
        syncFromPlayer(player, "unspecified");
    }
}
