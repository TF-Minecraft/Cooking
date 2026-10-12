package net.tfminecraft.cooking.utils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/** One player action, including standalone play without a character provider. */
public record PlayerActionContext(Player player, CharacterSession session) {
    public static final String NO_CHARACTER_MESSAGE = "§cYou need an active character to do that.";
    private static final int REFUSAL_COOLDOWN_TICKS = 60;
    private static final Map<UUID, Refusal> lastRefusal = new HashMap<>();

    /** Per connection, so a rejoin is told again; weak so it never holds an offline player. */
    private record Refusal(WeakReference<Player> player, int tick) {}

    public static PlayerActionContext capture(Player player) {
        if (player == null) return null;
        CharacterSession session = Characters.currentSession(player).orElse(null);
        if (session == null && Characters.available()) return null;
        PlayerActionContext context = new PlayerActionContext(player, session);
        return context.isCurrent() ? context : null;
    }

    /** Captures for an action the player started, telling them (at most every 3 s) when it is refused. */
    public static PlayerActionContext captureOrRefuse(Player player) {
        PlayerActionContext context = capture(player);
        if (context == null && player != null) {
            int now = Bukkit.getCurrentTick();
            Refusal last = lastRefusal.get(player.getUniqueId());
            if (last == null || last.player().get() != player || now - last.tick() >= REFUSAL_COOLDOWN_TICKS) {
                lastRefusal.put(player.getUniqueId(), new Refusal(new WeakReference<>(player), now));
                player.sendMessage(NO_CHARACTER_MESSAGE);
            }
        }
        return context;
    }

    public boolean isCurrent() {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                && (session == null ? !Characters.available()
                    : session.ref().ownerId().equals(player.getUniqueId()) && Characters.isCurrent(session));
    }
}
