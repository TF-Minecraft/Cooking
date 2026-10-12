package net.tfminecraft.cooking.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/** One player action, including standalone play without a character provider. */
public record PlayerActionContext(Player player, CharacterSession session) {
    public static PlayerActionContext capture(Player player) {
        if (player == null) return null;
        CharacterSession session = Characters.currentSession(player).orElse(null);
        if (session == null && Characters.available()) return null;
        PlayerActionContext context = new PlayerActionContext(player, session);
        return context.isCurrent() ? context : null;
    }

    public boolean isCurrent() {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                && (session == null ? !Characters.available()
                    : session.ref().ownerId().equals(player.getUniqueId()) && Characters.isCurrent(session));
    }
}
