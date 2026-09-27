package net.tfminecraft.cooking.husbandry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

/**
 * Finds owned animals sitting in unloaded chunks by reading the saved entity chunks,
 * so {@code /animals} can point at them. An owned animal that is not in any saved chunk
 * of a fully scanned world is a ghost: its row is deleted and logged.
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
                Bukkit.getScheduler().runTask(Cooking.plugin, () -> apply(targets, result, worlds));
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

    private static void apply(
            Map<UUID, Long> targets,
            HusbandryEntityScan.Result result,
            List<HusbandryEntityScan.WorldDir> worlds) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        int located = 0;
        int dropped = 0;
        int missing = 0;
        List<String> scannedWorlds = worlds.stream().map(HusbandryEntityScan.WorldDir::world).toList();
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
                HusbandryAnimal animal = stored.get();
                // An animal last seen in a world that was not scanned may still be there.
                if (!isConfirmedGhost(result.complete(), animal.world(), scannedWorlds)) {
                    continue;
                }
                try {
                    String line = deleteGhost(repository, animal);
                    Bukkit.getLogger().warning(line);
                    dropped++;
                } catch (SqliteDatabaseException ex) {
                    Bukkit.getLogger().severe("[Cooking] Failed to drop ghost animal " + uuid
                            + ": " + ex.getMessage());
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
        if (located > 0 || dropped > 0 || missing > 0 || !result.complete()) {
            String failed = missing > 0
                    ? ", " + missing + " still marked missing after a failed drop"
                    : "";
            Bukkit.getLogger().info("[Cooking] Animal scan: updated " + located + " locations, dropped "
                    + dropped + " ghost animals" + failed
                    + (result.complete() ? "." : " (some chunks could not be read, none dropped)."));
        }
    }

    /**
     * A ghost is an owned animal absent from every saved chunk after a complete scan of its world.
     * An incomplete scan, or a last world that was not scanned, is not enough to drop the row.
     */
    static boolean isConfirmedGhost(boolean scanComplete, String storedWorld, Collection<String> scannedWorlds) {
        if (!scanComplete) {
            return false;
        }
        if (storedWorld == null || storedWorld.isBlank()) {
            return true;
        }
        return scannedWorlds != null && scannedWorlds.contains(storedWorld);
    }

    /** Deletes the animal and its owners. Returns the log line for the dropped record. */
    static String deleteGhost(HusbandryRepository repository, HusbandryAnimal animal) {
        List<HusbandryOwner> owners = repository.listOwners(animal.uuid());
        repository.deleteAnimal(animal.uuid());
        for (HusbandryOwner owner : owners) {
            repository.deleteOwner(animal.uuid(), owner.playerUuid());
        }
        HusbandryEntities.evict(animal.uuid());
        MISSING.remove(animal.uuid());
        return ghostLog(animal, owners);
    }

    static String ghostLog(HusbandryAnimal animal, List<HusbandryOwner> owners) {
        String name = animal.name() == null || animal.name().isBlank() ? "(unnamed)" : animal.name();
        String type = animal.type() == null || animal.type().isBlank() ? "unknown" : animal.type();
        String place = animal.hasLocation()
                ? animal.world() + " " + animal.x() + ", " + animal.y() + ", " + animal.z()
                : "unknown";
        String ownerText = owners == null || owners.isEmpty()
                ? "none"
                : owners.stream()
                        .sorted((left, right) -> left.playerUuid().compareTo(right.playerUuid()))
                        .map(owner -> owner.playerUuid() + " (" + owner.role() + ")")
                        .collect(Collectors.joining(", "));
        return "[Cooking] Dropped ghost animal " + name + " (" + type + ") " + animal.uuid()
                + " owners=" + ownerText + " last seen " + place;
    }

    private static boolean sameLocation(HusbandryAnimal animal, HusbandryEntityScan.Found found) {
        return animal.hasLocation()
                && found.world().equals(animal.world())
                && found.x() == animal.x()
                && found.y() == animal.y()
                && found.z() == animal.z();
    }
}
