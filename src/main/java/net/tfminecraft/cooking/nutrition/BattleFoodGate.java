package net.tfminecraft.cooking.nutrition;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;

public final class BattleFoodGate {

    private BattleFoodGate() {}

    public static boolean inStartedBattle(Player player) {
        if (player == null) {
            return false;
        }
        Plugin simpleFactions = Bukkit.getPluginManager().getPlugin("SimpleFactions");
        if (simpleFactions == null || !simpleFactions.isEnabled()) {
            return false;
        }
        try {
            return lookup(player.getUniqueId());
        } catch (NoClassDefFoundError | NoSuchMethodError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean lookup(UUID memberId) {
        Battle battle = BattleManager.getBattleByMemberId(memberId);
        return battle != null && battle.hasStarted();
    }
}
