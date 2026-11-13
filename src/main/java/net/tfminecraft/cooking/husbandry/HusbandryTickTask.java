package net.tfminecraft.cooking.husbandry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import net.tfminecraft.tlibs.database.SqliteDatabaseException;
import net.tfminecraft.cooking.Cooking;

public final class HusbandryTickTask {

    private static final long PERIOD_TICKS = 20L * 60L;

    private static int taskId = -1;

    private HusbandryTickTask() {}

    public static void start() {
        if (taskId != -1 || Cooking.plugin == null) {
            return;
        }
        taskId = Bukkit.getScheduler().runTaskTimer(
                Cooking.plugin, HusbandryTickTask::tick, PERIOD_TICKS, PERIOD_TICKS).getTaskId();
    }

    public static void stop() {
        if (taskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    private static void tick() {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<HusbandryAnimal> dirty = new ArrayList<>();
        Map<UUID, byte[]> snapshots = new HashMap<>();
        for (HusbandryAnimal animal : HusbandryEntities.snapshotLoaded()) {
            Entity entity = Bukkit.getEntity(animal.uuid());
            if (!(entity instanceof LivingEntity) || entity.isDead()) {
                HusbandryEntities.evict(animal.uuid());
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (HusbandrySimulator.visitLongEnough(animal, now)) {
                HusbandrySimulator.tickLoaded(animal, now);
            }
            HusbandryLocation.remember(animal, living);
            HusbandryGrowth.applyMaturity(living, animal, now);
            HusbandryMounts.applyStats(living, animal);
            HusbandryShed.tryShed(living, animal, now);
            HusbandryEggs.tryLay(living, animal, now);
            dirty.add(animal);
            HusbandryStateDisplay.sync(living, animal);
            if (HusbandrySnapshots.shouldCapture(animal)) {
                byte[] snapshot = HusbandrySnapshots.capture(living, false);
                if (snapshot != null) {
                    snapshots.put(animal.uuid(), snapshot);
                }
            }
        }
        if (dirty.isEmpty()) {
            // Nothing loaded to save: the load and unload saves since the last tick still get synced.
            syncRelaxed(repository);
            return;
        }
        try {
            // One synced commit a minute: it also puts the relaxed load and unload saves on disk.
            repository.saveLoaded(dirty, snapshots, now, false);
            return;
        } catch (SqliteDatabaseException together) {
            // A failing snapshot must not cost the animals their save: write the two apart.
        }
        try {
            repository.upsertAnimals(dirty);
        } catch (SqliteDatabaseException ex) {
            Bukkit.getLogger().severe("[Cooking] Failed to persist husbandry tick: " + ex.getMessage());
        }
        HusbandrySnapshots.save(repository, snapshots);
        // Whatever these writes managed, the relaxed saves before them must not wait on the next tick.
        syncRelaxed(repository);
    }

    private static void syncRelaxed(HusbandryRepository repository) {
        try {
            repository.syncRelaxed();
        } catch (SqliteDatabaseException ex) {
            Bukkit.getLogger().severe("[Cooking] Failed to sync husbandry saves: " + ex.getMessage());
        }
    }
}
