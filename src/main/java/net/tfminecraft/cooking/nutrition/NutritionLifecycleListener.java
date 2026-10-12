package net.tfminecraft.cooking.nutrition;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

public final class NutritionLifecycleListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCharacterStart(CharacterSessionStartEvent event) {
        NutritionContext context = NutritionContext.capture(event.getPlayer());
        if (context == null || !context.session().equals(event.getSession())) return;
        initialize(context);
    }

    public static void initializeCurrent(Player player) {
        NutritionContext context = NutritionContext.capture(player);
        if (context != null) initialize(context);
    }

    private static void initialize(NutritionContext context) {
        Player owner = context.player();
        RPCharacter character = context.character();
        NutritionLog.append("ACTIVATE", owner, character,
                "hud=" + owner.getFoodLevel() + " saturation=" + owner.getSaturation());
        VarietyService.applyEffective(owner, character);
        DietTierService.seedIfAbsent(context);
        if (!context.isCurrent()) return;
        DietTierService.checkAndNotify(context, VarietyService.current(owner));
        NutritionDisplayService.sync(context, "activate");
        NutritionAttributeBridge.apply(context);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        NutritionLog.append("RESPAWN", player, null,
                "hud=" + player.getFoodLevel() + " saturation=" + player.getSaturation());
        NutritionDisplayService.syncFromPlayer(player, "respawn");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        NutritionContext context = NutritionContext.capture(player);
        if (context == null) {
            NutritionLog.append("DEATH", player, null, "action=skip cause=no-active-character");
            return;
        }
        RPCharacter character = context.character();
        boolean inBattle = BattleFoodGate.inStartedBattle(player);
        if (!context.isCurrent()) return;
        if (inBattle) {
            NutritionLog.append("DEATH", player, character,
                    "hud=" + player.getFoodLevel() + " saturation=" + player.getSaturation()
                    + " food=" + character.getFoodValue()
                    + " action=preserve-battle");
            return;
        }
        int before = character.getFoodValue();
        int after = NutritionService.foodAfterDeath(before, NutritionConfig.respawnFood(), false);
        character.setFoodValue(after);
        NutritionLog.append("DEATH", player, character,
                "hud=" + player.getFoodLevel() + " saturation=" + player.getSaturation()
                + " foodBefore=" + before
                + " foodAfter=" + after
                + " action=respawn-food");
        context.save();
    }
}
