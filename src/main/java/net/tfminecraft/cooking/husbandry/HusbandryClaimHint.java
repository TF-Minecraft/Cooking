package net.tfminecraft.cooking.husbandry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/** Tells players that animals they tame or ride vanish unless claimed in Cooking. */
public final class HusbandryClaimHint {

    static final long COOLDOWN_MILLIS = 60_000L;

    private static final Map<UUID, Long> LAST_SENT = new ConcurrentHashMap<>();

    private HusbandryClaimHint() {}

    public static boolean needsClaim(Entity entity) {
        return entity != null
                && HusbandryConfig.isRemoveUnowned(entity.getType())
                && !HusbandryOwnershipService.hasAnyOwner(entity.getUniqueId());
    }

    /** Sends the hint unless this player saw one within the cooldown. */
    public static void remind(Player player, Entity entity) {
        long now = System.currentTimeMillis();
        Long last = LAST_SENT.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return;
        }
        send(player, entity);
    }

    public static void send(Player player, Entity entity) {
        if (player == null || !needsClaim(entity)) {
            return;
        }
        LAST_SENT.put(player.getUniqueId(), System.currentTimeMillis());
        boolean staff = HusbandryOwnershipService.isStaff(player);
        HusbandryRepository repository = HusbandryEntities.repository();
        int owned = repository == null ? 0 : repository.countForPlayer(player.getUniqueId());
        boolean untamed = entity instanceof Tameable tameable && !tameable.isTamed();
        for (String line : lines(HusbandryEntities.displayName(entity.getType()),
                untamed, owned, HusbandryConfig.maxAnimals(), staff)) {
            player.sendMessage(line);
        }
    }

    public static void forget(UUID playerUuid) {
        LAST_SENT.remove(playerUuid);
    }

    static List<String> lines(String species, boolean untamed, int owned, int cap, boolean staff) {
        String label = species == null || species.isBlank() ? "animal" : species.toLowerCase(Locale.ROOT);
        String claim = (untamed ? "Tame it, then right-click" : "Right-click")
                + " it with an Ownership Token renamed on an anvil";
        String warning = "§eThis " + label + " is not claimed. Unclaimed animals disappear when their area unloads.";
        if (staff) {
            return List.of(warning,
                    "§7" + claim + ". §8(staff: no animal limit)");
        }
        if (owned >= cap) {
            return List.of(warning,
                    "§cYou already own " + owned + "/" + cap + " animals, so you cannot claim it.",
                    "§7Release one first: shift-right-click your animal with an empty hand. See §f/animals§7.");
        }
        return List.of(warning,
                "§7" + claim + " to keep it.",
                "§7You own §f" + owned + "/" + cap + "§7 animals. See §f/animals§7.");
    }
}
