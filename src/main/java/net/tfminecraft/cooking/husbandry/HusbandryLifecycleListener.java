package net.tfminecraft.cooking.husbandry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import net.tfminecraft.tlibs.database.SqliteDatabaseException;

public final class HusbandryLifecycleListener implements Listener {

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            handleLoad(entity);
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            handleUnload(entity);
        }
    }

    public static void applyStatsRevision() {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        String revision = HusbandryConfig.statsRevision();
        int count;
        try {
            count = repository.resetStaleStats(revision, ThreadLocalRandom.current());
        } catch (SqliteDatabaseException ex) {
            Bukkit.getLogger().severe("[Cooking] Failed to reset husbandry stats: " + ex.getMessage());
            return;
        }
        if (count > 0) {
            Bukkit.getLogger().info(
                    "[Cooking] Reset " + count + " husbandry animals to wild stats (revision " + revision + ").");
        }
        refreshLoadedAfterReset(repository);
    }

    private static void refreshLoadedAfterReset(HusbandryRepository repository) {
        List<UUID> loaded = new ArrayList<>(HusbandryEntities.loadedIds());
        if (loaded.isEmpty()) {
            return;
        }
        Map<UUID, LivingEntity> livingById = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living) {
                    livingById.put(living.getUniqueId(), living);
                }
            }
        }
        for (UUID uuid : loaded) {
            Optional<HusbandryAnimal> stored = repository.getAnimal(uuid);
            if (stored.isEmpty()) {
                HusbandryEntities.evict(uuid);
                continue;
            }
            HusbandryAnimal animal = stored.get();
            HusbandryEntities.putLoaded(animal);
            LivingEntity living = livingById.get(uuid);
            if (living != null) {
                HusbandryMounts.applyStats(living, animal);
            }
        }
    }

    public static void resumeLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                handleLoad(entity);
            }
        }
    }

    public static void flushLoadedForDisable() {
        HusbandryRepository repository = HusbandryEntities.repository();
        List<HusbandryAnimal> snapshot = HusbandryEntities.snapshotLoaded();
        HusbandryEntities.clearLoaded();
        if (repository == null || snapshot.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<HusbandryAnimal> toSave = new ArrayList<>(snapshot.size());
        for (HusbandryAnimal animal : snapshot) {
            animal.setUnloadedAt(now);
            toSave.add(animal);
        }
        try {
            repository.upsertAnimals(toSave);
        } catch (SqliteDatabaseException ex) {
            Bukkit.getLogger().severe("[Cooking] Failed to flush husbandry animals on disable: " + ex.getMessage());
        }
    }

    static void handleLoad(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        boolean hasRow = repository.exists(uuid);

        if (HusbandryMounts.shouldWipeUnowned(
                HusbandryConfig.isRemoveUnowned(entity.getType()),
                HusbandryOwnershipService.hasAnyOwner(uuid),
                hasRow,
                HusbandryMounts.hasConfiguredStats(entity))) {
            if (hasRow) {
                repository.deleteAnimal(uuid);
            }
            HusbandryEntities.evict(uuid);
            entity.remove();
            return;
        }
        if (!hasRow) {
            return;
        }

        HusbandryEntities.applyPersistFlags(living);
        HusbandryEntities.stampManaged(living);

        Optional<HusbandryAnimal> stored = repository.getAnimal(uuid);
        if (stored.isEmpty()) {
            return;
        }
        HusbandryAnimal animal = stored.get();
        long now = System.currentTimeMillis();
        HusbandrySimulator.catchUp(animal, now, java.util.concurrent.ThreadLocalRandom.current());
        HusbandryGrowth.applyMaturity(living, animal, now);
        HusbandryMounts.applyStats(living, animal);
        animal.setUnloadedAt(null);
        animal.setLoadedVisitStart(now);
        HusbandryEntities.putLoaded(animal);
        repository.upsertAnimal(animal);
        HusbandryStateDisplay.sync(living, animal);
    }

    static void handleUnload(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        HusbandryRepository repository = HusbandryEntities.repository();
        Optional<HusbandryAnimal> stored = HusbandryEntities.getLoaded(uuid);
        if (stored.isEmpty() && repository != null) {
            stored = repository.getAnimal(uuid);
        }
        HusbandryEntities.evict(uuid);
        if (repository == null || stored.isEmpty()) {
            return;
        }
        HusbandryAnimal animal = stored.get();
        animal.setUnloadedAt(System.currentTimeMillis());
        repository.upsertAnimal(animal);
    }
}
