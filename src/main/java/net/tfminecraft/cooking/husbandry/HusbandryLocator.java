package net.tfminecraft.cooking.husbandry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

/**
 * Finds owned animals sitting in unloaded chunks by reading the saved entity chunks,
 * so {@code /animals} can point at them. Animals that are not in any saved chunk are marked missing.
 */
public final class HusbandryLocator {

    private static final Set<UUID> MISSING = ConcurrentHashMap.newKeySet();

    private HusbandryLocator() {}

    public static boolean isMissing(UUID uuid) {
        return uuid != null && MISSING.contains(uuid);
    }

    static void markFound(UUID uuid) {
        if (uuid != null) {
            MISSING.remove(uuid);
        }
    }

    /** Scans once in the background. Call on the main thread after loaded worlds have resumed. */
    public static void scanUnloaded() {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || Cooking.plugin == null) {
            return;
        }
        Map<UUID, Long> targets = new HashMap<>();
        for (HusbandryAnimal animal : repository.listOwnedAnimals()) {
            if (HusbandryEntities.getLoaded(animal.uuid()).isEmpty()) {
                targets.put(animal.uuid(), animal.unloadedAt());
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        List<HusbandryEntityScan.WorldDir> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(new HusbandryEntityScan.WorldDir(world.getName(), entitiesFolder(world)));
        }
        Bukkit.getScheduler().runTaskAsynchronously(Cooking.plugin, () -> {
            HusbandryEntityScan.Result result = HusbandryEntityScan.scan(worlds, targets.keySet());
            if (Cooking.plugin != null && Cooking.plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(Cooking.plugin, () -> apply(targets, result));
            }
        });
    }

    private static File entitiesFolder(World world) {
        File folder = world.getWorldFolder();
        return switch (world.getEnvironment()) {
            case NETHER -> new File(folder, "DIM-1/entities");
            case THE_END -> new File(folder, "DIM1/entities");
            default -> new File(folder, "entities");
        };
    }

    private static void apply(Map<UUID, Long> targets, HusbandryEntityScan.Result result) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        int located = 0;
        int missing = 0;
        for (Map.Entry<UUID, Long> target : targets.entrySet()) {
            UUID uuid = target.getKey();
            if (HusbandryEntities.getLoaded(uuid).isPresent()) {
                continue;
            }
            Optional<HusbandryAnimal> stored = repository.getAnimal(uuid);
            if (stored.isEmpty() || !Objects.equals(stored.get().unloadedAt(), target.getValue())) {
                // Loaded or unloaded again during the scan; the stored location is newer.
                continue;
            }
            HusbandryEntityScan.Found found = result.found().get(uuid);
            if (found == null) {
                if (result.complete()) {
                    MISSING.add(uuid);
                    missing++;
                }
                continue;
            }
            MISSING.remove(uuid);
            if (sameLocation(stored.get(), found)) {
                continue;
            }
            HusbandryAnimal animal = stored.get();
            animal.setLastLocation(found.world(), found.x(), found.y(), found.z());
            try {
                repository.upsertAnimal(animal);
                located++;
            } catch (SqliteDatabaseException ex) {
                Bukkit.getLogger().severe("[Cooking] Failed to save husbandry animal location: " + ex.getMessage());
            }
        }
        if (located > 0 || missing > 0 || !result.complete()) {
            Bukkit.getLogger().info("[Cooking] Animal scan: updated " + located + " locations, " + missing
                    + " owned animals not found in saved chunks"
                    + (result.complete() ? "." : " (some chunks could not be read, none marked missing)."));
        }
    }

    private static boolean sameLocation(HusbandryAnimal animal, HusbandryEntityScan.Found found) {
        return animal.hasLocation()
                && found.world().equals(animal.world())
                && found.x() == animal.x()
                && found.y() == animal.y()
                && found.z() == animal.z();
    }
}
