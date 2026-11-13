package net.tfminecraft.cooking.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.tlibs.database.SqliteDatabaseException;

class HusbandryRepositoryTest {

    @Test
    void batchUpsertInsertsAndUpdates(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal first = animal(UUID.randomUUID(), "COW", "Bess", 12);
            HusbandryAnimal second = animal(UUID.randomUUID(), "SHEEP", "Wool", 4);
            HusbandryAnimal third = animal(UUID.randomUUID(), "PIG", "Ham", 8);
            repository.upsertAnimals(List.of(first, second, third));

            assertEquals(12, repository.getAnimal(first.uuid()).orElseThrow().care());
            assertEquals("Wool", repository.getAnimal(second.uuid()).orElseThrow().name());
            assertEquals("PIG", repository.getAnimal(third.uuid()).orElseThrow().type());

            first.setCare(40);
            first.setGenetics(250);
            first.setHungrySince(1_700_000_000_000L);
            first.setCareUpRemainderSeconds(123);
            first.setCareDownRemainderSeconds(45);
            repository.upsertAnimals(Arrays.asList(first, null));

            HusbandryAnimal reloaded = repository.getAnimal(first.uuid()).orElseThrow();
            assertEquals(40, reloaded.care());
            assertEquals(250, reloaded.genetics());
            assertEquals(1_700_000_000_000L, reloaded.hungrySince());
            assertEquals(123, reloaded.careUpRemainderSeconds());
            assertEquals(45, reloaded.careDownRemainderSeconds());
            assertEquals(4, repository.getAnimal(second.uuid()).orElseThrow().care());
            assertTrue(repository.exists(third.uuid()));
        } finally {
            repository.close();
        }
    }

    @Test
    void missingRevisionResetsToWildAndStamps(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal animal = animal(UUID.randomUUID(), "SHEEP", "Bess", 40);
            animal.setGenetics(900);
            animal.setNeutered(true);
            animal.setMatureAt(12_345L);
            animal.setCareUpRemainderSeconds(9);
            animal.setCareDownRemainderSeconds(3);
            repository.upsertAnimal(animal);

            Random random = new Random(1L);
            int expectedGenes = new Random(1L).nextInt(HusbandryConfig.initialGeneticMax() + 1);
            int reset = repository.resetStaleStats("1", random);
            assertEquals(1, reset);

            HusbandryAnimal reloaded = repository.getAnimal(animal.uuid()).orElseThrow();
            assertEquals(expectedGenes, reloaded.genetics());
            assertEquals(0, reloaded.care());
            assertEquals(0, reloaded.careUpRemainderSeconds());
            assertEquals(0, reloaded.careDownRemainderSeconds());
            assertEquals("1", reloaded.statsRevision());
            assertEquals("Bess", reloaded.name());
            assertTrue(reloaded.neutered());
            assertEquals(12_345L, reloaded.matureAt());
        } finally {
            repository.close();
        }
    }

    @Test
    void matchingRevisionIsUnchanged(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal animal = animal(UUID.randomUUID(), "COW", "Bess", 40);
            animal.setGenetics(250);
            animal.setStatsRevision("1");
            repository.upsertAnimal(animal);

            int reset = repository.resetStaleStats("1", new Random(1L));
            assertEquals(0, reset);

            HusbandryAnimal reloaded = repository.getAnimal(animal.uuid()).orElseThrow();
            assertEquals(250, reloaded.genetics());
            assertEquals(40, reloaded.care());
            assertEquals("1", reloaded.statsRevision());
        } finally {
            repository.close();
        }
    }

    @Test
    void bumpedRevisionResetsMatchingRows(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal animal = animal(UUID.randomUUID(), "PIG", "Ham", 80);
            animal.setGenetics(700);
            animal.setStatsRevision("1");
            repository.upsertAnimal(animal);

            Random random = new Random(2L);
            int expectedGenes = new Random(2L).nextInt(HusbandryConfig.initialGeneticMax() + 1);
            int reset = repository.resetStaleStats("2", random);
            assertEquals(1, reset);

            HusbandryAnimal reloaded = repository.getAnimal(animal.uuid()).orElseThrow();
            assertEquals(expectedGenes, reloaded.genetics());
            assertEquals(0, reloaded.care());
            assertEquals("2", reloaded.statsRevision());
            assertEquals("Ham", reloaded.name());
        } finally {
            repository.close();
        }
    }

    @Test
    void locationRoundTripsAndListsOnlyThatPlayersAnimals(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            UUID player = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 10);
            cow.setLastLocation("world", 120, 64, -340);
            HusbandryAnimal sheep = animal(UUID.randomUUID(), "SHEEP", "Woolly", 4);
            HusbandryAnimal pig = animal(UUID.randomUUID(), "PIG", "Ham", 1);
            repository.upsertAnimals(List.of(cow, sheep, pig));
            repository.upsertOwner(new HusbandryOwner(cow.uuid(), player, player, "owner"));
            repository.upsertOwner(new HusbandryOwner(sheep.uuid(), player, player, "coowner"));
            repository.upsertOwner(new HusbandryOwner(sheep.uuid(), other, other, "owner"));
            repository.upsertOwner(new HusbandryOwner(pig.uuid(), other, other, "owner"));

            HusbandryAnimal reloaded = repository.getAnimal(cow.uuid()).orElseThrow();
            assertEquals("world", reloaded.world());
            assertEquals(120, reloaded.x());
            assertEquals(64, reloaded.y());
            assertEquals(-340, reloaded.z());
            assertFalse(repository.getAnimal(sheep.uuid()).orElseThrow().hasLocation());

            List<HusbandryOwned> mine = repository.listForPlayer(player);
            assertEquals(2, mine.size());
            assertEquals("owner", roleOf(mine, cow.uuid()));
            assertEquals("coowner", roleOf(mine, sheep.uuid()));
            assertEquals(1, repository.listForPlayer(other).stream()
                    .filter(row -> row.animal().uuid().equals(pig.uuid()))
                    .count());
            assertTrue(repository.listForPlayer(UUID.randomUUID()).isEmpty());

            HusbandryAnimal wild = animal(UUID.randomUUID(), "COW", "", 0);
            repository.upsertAnimal(wild);
            assertEquals(
                    java.util.Set.of(cow.uuid(), sheep.uuid(), pig.uuid()),
                    repository.listOwnedAnimals().stream()
                            .map(HusbandryAnimal::uuid)
                            .collect(java.util.stream.Collectors.toSet()));
        } finally {
            repository.close();
        }
    }

    @Test
    void versionSevenGainsLocationColumnsWithoutLosingRows(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("legacy.db").toFile();
        UUID uuid = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            connection.createStatement().execute("""
                    CREATE TABLE animals (
                        uuid TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        state TEXT NOT NULL DEFAULT 'untamed',
                        genetics INTEGER NOT NULL DEFAULT 0,
                        care INTEGER NOT NULL DEFAULT 0,
                        hungry_since INTEGER,
                        dirty_since INTEGER,
                        last_processed_at INTEGER NOT NULL,
                        unloaded_at INTEGER,
                        affliction_elapsed REAL NOT NULL DEFAULT 0,
                        affliction_at REAL NOT NULL DEFAULT 0,
                        last_milk_at INTEGER,
                        wool_ready_at INTEGER,
                        neutered INTEGER NOT NULL DEFAULT 0,
                        loaded_visit_start INTEGER,
                        mature_at INTEGER,
                        shed_ready_at INTEGER,
                        egg_ready_at INTEGER,
                        care_up_remainder INTEGER NOT NULL DEFAULT 0,
                        care_down_remainder INTEGER NOT NULL DEFAULT 0,
                        stats_revision TEXT
                    )
                    """);
            connection.createStatement().execute("PRAGMA user_version = 7");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO animals (uuid, type, name, state, genetics, care, last_processed_at, stats_revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, "GOAT");
                insert.setString(3, "Nanny");
                insert.setString(4, "owned");
                insert.setInt(5, 440);
                insert.setInt(6, 80);
                insert.setLong(7, 50L);
                insert.setString(8, "1");
                insert.executeUpdate();
            }
        }

        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal loaded = repository.getAnimal(uuid).orElseThrow();
            assertEquals("Nanny", loaded.name());
            assertEquals(440, loaded.genetics());
            assertEquals(80, loaded.care());
            assertFalse(loaded.hasLocation());
            assertNull(loaded.world());
            loaded.setLastLocation("tfmc_world", 3, 70, 9);
            repository.upsertAnimal(loaded);
        } finally {
            repository.close();
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            assertEquals(8, connection.createStatement().executeQuery("PRAGMA user_version").getInt(1));
        }
        HusbandryRepository again = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal loaded = again.getAnimal(uuid).orElseThrow();
            assertEquals("tfmc_world", loaded.world());
            assertEquals(3, loaded.x());
            assertEquals(70, loaded.y());
            assertEquals(9, loaded.z());
            assertEquals(440, loaded.genetics());
        } finally {
            again.close();
        }
    }

    private static String roleOf(List<HusbandryOwned> rows, UUID animalUuid) {
        return rows.stream()
                .filter(row -> animalUuid.equals(row.animal().uuid()))
                .findFirst()
                .orElseThrow()
                .role();
    }

    @Test
    void snapshotsRoundTripAndGoWithTheirAnimal(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal kept = animal(UUID.randomUUID(), "HORSE", "Drake Maye", 200);
            HusbandryAnimal dropped = animal(UUID.randomUUID(), "HORSE", "gregor", 200);
            UUID noRow = UUID.randomUUID();
            repository.upsertAnimals(List.of(kept, dropped));

            repository.upsertSnapshots(
                    java.util.Map.of(kept.uuid(), new byte[] {1, 2, 3}, dropped.uuid(), new byte[] {4}, noRow,
                            new byte[] {5}),
                    100L);
            HusbandrySnapshot stored = repository.getSnapshot(kept.uuid()).orElseThrow();
            assertTrue(Arrays.equals(new byte[] {1, 2, 3}, stored.data()));
            assertEquals(100L, stored.savedAt());
            assertTrue(repository.getSnapshot(noRow).isEmpty());

            repository.upsertSnapshots(java.util.Map.of(kept.uuid(), new byte[] {9}), 200L);
            stored = repository.getSnapshot(kept.uuid()).orElseThrow();
            assertTrue(Arrays.equals(new byte[] {9}, stored.data()));
            assertEquals(200L, stored.savedAt());

            repository.deleteAnimal(dropped.uuid());
            assertTrue(repository.getSnapshot(dropped.uuid()).isEmpty());

            repository.deleteSnapshot(kept.uuid());
            assertTrue(repository.getSnapshot(kept.uuid()).isEmpty());
            assertTrue(repository.exists(kept.uuid()));
            assertTrue(repository.getSnapshot(null).isEmpty());
        } finally {
            repository.close();
        }
    }

    @Test
    void loadedSaveStoresAnimalsAndTheirSnapshotsTogetherWhetherRelaxedOrSynced(@TempDir Path tempDir) {
        HusbandryRepository repository = HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile());
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            HusbandryAnimal horse = animal(UUID.randomUUID(), "HORSE", "Drake Maye", 200);
            UUID noRow = UUID.randomUUID();
            // The horse's row is new in this very save: its snapshot must still be stored.
            repository.saveLoaded(List.of(cow, horse), Map.of(horse.uuid(), new byte[] {7}, noRow, new byte[] {5}), 100L, true);
            assertEquals(12, repository.getAnimal(cow.uuid()).orElseThrow().care());
            assertTrue(Arrays.equals(new byte[] {7}, repository.getSnapshot(horse.uuid()).orElseThrow().data()));
            assertEquals(100L, repository.getSnapshot(horse.uuid()).orElseThrow().savedAt());
            assertTrue(repository.getSnapshot(noRow).isEmpty());
            assertEquals(2, repository.synchronousLevel(), "a relaxed save must hand the connection back fully synced");

            cow.setCare(40);
            repository.saveLoaded(List.of(cow), Map.of(horse.uuid(), new byte[] {8}), 200L, false);
            assertEquals(40, repository.getAnimal(cow.uuid()).orElseThrow().care());
            assertTrue(Arrays.equals(new byte[] {8}, repository.getSnapshot(horse.uuid()).orElseThrow().data()));
            assertEquals(2, repository.synchronousLevel());

            // Nothing to write, null collections and rows without an id are all quiet no-ops.
            repository.saveLoaded(null, null, 300L, true);
            repository.saveLoaded(List.of(), Map.of(), 300L, false);
            repository.saveLoaded(Arrays.asList((HusbandryAnimal) null), null, 300L, true);
            repository.saveLoaded(null, Map.of(horse.uuid(), new byte[] {9}), 300L, true);
            assertTrue(Arrays.equals(new byte[] {9}, repository.getSnapshot(horse.uuid()).orElseThrow().data()));
        } finally {
            repository.close();
        }
    }

    /** One snapshot in a map that reports the connection's sync level when the save comes to write it. */
    private static Map<UUID, byte[]> reportingLevel(HusbandryRepository repository, List<Integer> levels, UUID animal) {
        return new HashMap<UUID, byte[]>(Map.of(animal, new byte[] {1})) {
            @Override
            public Set<Map.Entry<UUID, byte[]>> entrySet() {
                levels.add(repository.synchronousLevel());
                return super.entrySet();
            }
        };
    }

    @Test
    void aRelaxedSaveCommitsAtNormalAndHandsBackFullWhileASyncedSaveStaysAtFull(@TempDir Path tempDir) {
        HusbandryRepository repository = HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile());
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            List<Integer> levels = new ArrayList<>();
            repository.saveLoaded(List.of(cow), reportingLevel(repository, levels, cow.uuid()), 100L, true);
            assertEquals(List.of(1), levels, "the transaction of a relaxed save runs at NORMAL");
            assertEquals(2, repository.synchronousLevel(), "and the connection is back at FULL once it returns");
            assertTrue(repository.relaxedPending());
            assertTrue(repository.getSnapshot(cow.uuid()).isPresent());

            repository.saveLoaded(List.of(cow), reportingLevel(repository, levels, cow.uuid()), 200L, false);
            assertEquals(List.of(1, 2), levels, "a synced save never leaves FULL");
        } finally {
            repository.close();
        }
    }

    @Test
    void anErrorInsideARelaxedSaveStillRestoresSyncedCommits(@TempDir Path tempDir) {
        HusbandryRepository repository = HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile());
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            repository.upsertAnimal(cow);
            cow.setCare(77);
            Map<UUID, byte[]> failing = new HashMap<UUID, byte[]>(Map.of(cow.uuid(), new byte[] {1})) {
                @Override
                public Set<Map.Entry<UUID, byte[]>> entrySet() {
                    throw new AssertionError("out of memory, say");
                }
            };
            AssertionError error = assertThrows(AssertionError.class,
                    () -> repository.saveLoaded(List.of(cow), failing, 100L, true));
            assertEquals("out of memory, say", error.getMessage());
            assertEquals(2, repository.synchronousLevel(), "an Error must not leave the connection at NORMAL");
            assertEquals(12, repository.getAnimal(cow.uuid()).orElseThrow().care(), "and the save rolled back");
            assertTrue(repository.relaxedPending(), "marked before the commit, whatever became of it");
        } finally {
            repository.close();
        }
    }

    @Test
    void noOtherThreadsWriteRunsBetweenTheLevelChangesAndTheTransaction(@TempDir Path tempDir) throws Exception {
        HusbandryRepository repository = spy(HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile()));
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            HusbandryAnimal early = animal(UUID.randomUUID(), "PIG", "Ham", 8);
            HusbandryAnimal late = animal(UUID.randomUUID(), "SHEEP", "Wool", 4);
            // The two moments the database's own lock is free while the level is still NORMAL:
            // after the level is lowered and before the transaction begins, and after the commit
            // and before the level is raised again. A write that got in there would commit unsynced.
            Thread beforeTransaction = new Thread(() -> repository.upsertAnimal(early));
            Thread afterCommit = new Thread(() -> repository.upsertAnimal(late));
            boolean[] waited = new boolean[2];
            doAnswer(call -> {
                Object result = call.callRealMethod();
                beforeTransaction.start();
                beforeTransaction.join(500);
                waited[0] = beforeTransaction.isAlive() && !repository.exists(early.uuid());
                return result;
            }).when(repository).synchronous("NORMAL");
            doAnswer(call -> {
                afterCommit.start();
                afterCommit.join(500);
                waited[1] = afterCommit.isAlive() && beforeTransaction.isAlive()
                        && !repository.exists(early.uuid()) && !repository.exists(late.uuid());
                return call.callRealMethod();
            }).when(repository).synchronous("FULL");

            repository.saveLoaded(List.of(cow), Map.of(), 100L, true);

            assertTrue(waited[0], "a write that arrives before the transaction begins must wait");
            assertTrue(waited[1], "and so must one that arrives after the commit, until the level is FULL again");
            beforeTransaction.join(5_000);
            afterCommit.join(5_000);
            assertFalse(beforeTransaction.isAlive());
            assertFalse(afterCommit.isAlive());
            assertTrue(repository.exists(early.uuid()));
            assertTrue(repository.exists(late.uuid()));
            assertEquals(2, repository.synchronousLevel());
        } finally {
            repository.close();
        }
    }

    @Test
    void aFailedRelaxedSaveWritesNothingAndStillRestoresSyncedCommits(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = spy(HusbandryRepository.open(dbFile));
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            repository.upsertAnimal(cow);
            db.createStatement().execute(
                    "CREATE TRIGGER reject_snapshots BEFORE INSERT ON snapshots BEGIN SELECT RAISE(FAIL,'no snapshots'); END");
            cow.setCare(77);
            assertThrows(SqliteDatabaseException.class,
                    () -> repository.saveLoaded(List.of(cow), Map.of(cow.uuid(), new byte[] {1}), 100L, true));
            assertEquals(12, repository.getAnimal(cow.uuid()).orElseThrow().care(), "the animal rolled back with its snapshot");
            assertEquals(2, repository.synchronousLevel());
            // It is marked all the same, since a save can fail after its commit went through.
            assertTrue(repository.relaxedPending());
            assertTrue(repository.syncRelaxed());
            verify(repository, times(1)).checkpointedAll();
            assertFalse(repository.relaxedPending());
        } finally {
            repository.close();
        }
    }

    @Test
    void relaxedSavesStayMarkedUntilACheckpointHasCopiedThem(@TempDir Path tempDir) throws Exception {
        HusbandryRepository repository = spy(HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile()));
        Path log = tempDir.resolve("husbandry.db-wal");
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            assertTrue(repository.syncRelaxed(), "nothing is waiting");
            verify(repository, never()).checkpointedAll();

            repository.saveLoaded(List.of(cow), Map.of(), 100L, true);
            assertTrue(repository.syncRelaxed());
            assertFalse(repository.relaxedPending());
            repository.syncRelaxed();
            verify(repository, times(1)).checkpointedAll();

            // A synced save afterwards is no proof. This one writes the same row again, which
            // changes no page: SQLite then writes nothing, so there is nothing for it to sync.
            repository.saveLoaded(List.of(cow), Map.of(), 100L, true);
            byte[] before = Files.readAllBytes(log);
            repository.saveLoaded(List.of(cow), Map.of(), 100L, false);
            assertTrue(Arrays.equals(before, Files.readAllBytes(log)), "the same row again leaves the log untouched");
            assertTrue(repository.relaxedPending(), "only a checkpoint clears the mark");
            // Nor is a synced save that does change the row.
            cow.setCare(90);
            repository.saveLoaded(List.of(cow), Map.of(), 100L, false);
            assertFalse(Arrays.equals(before, Files.readAllBytes(log)), "a changed row is written to the log");
            assertTrue(repository.relaxedPending());
            assertTrue(repository.syncRelaxed());
            assertFalse(repository.relaxedPending());
            verify(repository, times(2)).checkpointedAll();

            // A checkpoint that fails leaves the saves marked, so the next minute tries again.
            repository.saveLoaded(List.of(cow), Map.of(), 100L, true);
            doThrow(new SqliteDatabaseException("disk")).doCallRealMethod().when(repository).checkpointedAll();
            assertThrows(SqliteDatabaseException.class, repository::syncRelaxed);
            assertTrue(repository.relaxedPending());
            repository.syncRelaxed();
            assertFalse(repository.relaxedPending());
            verify(repository, times(4)).checkpointedAll();
        } finally {
            repository.close();
        }
    }

    @Test
    void aCheckpointThatAnotherConnectionHoldsBackKeepsTheSavesMarked(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            repository.upsertAnimal(cow);
            try (Connection reader = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
                // A reader that keeps looking at the database as it was before the relaxed save.
                reader.setAutoCommit(false);
                try (var rows = reader.createStatement().executeQuery("SELECT COUNT(*) FROM animals")) {
                    assertTrue(rows.next());
                }
                cow.setCare(40);
                repository.saveLoaded(List.of(cow), Map.of(), 100L, true);
                // SQLite reports no error here: the checkpoint just stops at the reader's view.
                assertFalse(repository.checkpointedAll());
                repository.saveLoaded(List.of(cow), Map.of(), 101L, true);
                assertFalse(repository.syncRelaxed());
                assertTrue(repository.relaxedPending(), "nothing was synced, so the save still waits");
                reader.rollback();
            }
            assertTrue(repository.syncRelaxed());
            assertFalse(repository.relaxedPending(), "with the reader gone the next try goes through");
            assertEquals(40, repository.getAnimal(cow.uuid()).orElseThrow().care());
        } finally {
            repository.close();
        }
    }

    @Test
    void aLevelSwitchThatFailsAfterTakingEffectIsUndoneAsWell(@TempDir Path tempDir) {
        HusbandryRepository repository = spy(HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile()));
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            doAnswer(call -> {
                call.callRealMethod();
                throw new SqliteDatabaseException("the statement would not close");
            }).when(repository).synchronous("NORMAL");
            SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class,
                    () -> repository.saveLoaded(List.of(cow), Map.of(), 100L, true));
            assertEquals("the statement would not close", failure.getMessage());
            assertEquals(2, repository.synchronousLevel(), "the connection must not stay at NORMAL");
            assertFalse(repository.exists(cow.uuid()), "no save ran");
            assertFalse(repository.relaxedPending());
        } finally {
            repository.close();
        }
    }

    @Test
    void aConnectionThatCannotGoBackToSyncedCommitsIsClosed(@TempDir Path tempDir) {
        HusbandryRepository repository = spy(HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile()));
        HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
        doThrow(new SqliteDatabaseException("level stuck")).when(repository).synchronous("FULL");
        SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class,
                () -> repository.saveLoaded(List.of(cow), Map.of(), 100L, true));
        assertEquals("Could not restore synced husbandry commits", failure.getMessage());
        assertEquals("level stuck", failure.getCause().getMessage());
        // Closed rather than left committing without syncs: every later use fails loudly.
        assertThrows(SqliteDatabaseException.class, () -> repository.getAnimal(cow.uuid()));
        assertThrows(SqliteDatabaseException.class, () -> repository.upsertAnimal(cow));
    }

    @Test
    void aFailedSaveKeepsItsOwnFailureWhenTheLevelCannotBeRestoredEither(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = spy(HusbandryRepository.open(dbFile));
        HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
        repository.upsertAnimal(cow);
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            db.createStatement().execute(
                    "CREATE TRIGGER reject_snapshots BEFORE INSERT ON snapshots BEGIN SELECT RAISE(FAIL,'no snapshots'); END");
        }
        doThrow(new SqliteDatabaseException("level stuck")).when(repository).synchronous("FULL");
        SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class,
                () -> repository.saveLoaded(List.of(cow), Map.of(cow.uuid(), new byte[] {1}), 100L, true));
        assertEquals("Failed to upsert snapshots", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("level stuck", failure.getSuppressed()[0].getMessage());
        assertThrows(SqliteDatabaseException.class, () -> repository.getAnimal(cow.uuid()), "closed here too");
    }

    @Test
    void aCloseThatFailsWhileRestoringTravelsWithTheRestoreFailure(@TempDir Path tempDir) {
        HusbandryRepository repository = spy(HusbandryRepository.open(tempDir.resolve("husbandry.db").toFile()));
        try {
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 12);
            doThrow(new SqliteDatabaseException("level stuck")).doCallRealMethod().when(repository).synchronous("FULL");
            doThrow(new SqliteDatabaseException("close stuck")).doCallRealMethod().when(repository).closeDatabase();
            SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class,
                    () -> repository.saveLoaded(List.of(cow), Map.of(), 100L, true));
            assertEquals("Could not restore synced husbandry commits", failure.getMessage());
            assertEquals("close stuck", failure.getCause().getSuppressed()[0].getMessage());
        } finally {
            repository.close();
        }
    }

    private static HusbandryAnimal animal(UUID uuid, String type, String name, int care) {
        HusbandryAnimal animal = new HusbandryAnimal(uuid, type, name);
        animal.setCare(care);
        animal.setGenetics(100);
        animal.setLastProcessedAt(1_234L);
        animal.setLoadedVisitStart(2_000L);
        return animal;
    }
}
