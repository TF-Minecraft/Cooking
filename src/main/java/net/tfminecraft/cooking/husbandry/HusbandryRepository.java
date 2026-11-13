package net.tfminecraft.cooking.husbandry;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import net.tfminecraft.tlibs.database.SqliteDatabase;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;
import net.tfminecraft.tlibs.database.SqliteProvider;

public final class HusbandryRepository {

    private static final String CREATE_ANIMALS = """
            CREATE TABLE IF NOT EXISTS animals (
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
            """;

    private static final String CREATE_OWNERS = """
            CREATE TABLE IF NOT EXISTS owners (
                animal_uuid TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'owner',
                PRIMARY KEY (animal_uuid, player_uuid),
                FOREIGN KEY (animal_uuid) REFERENCES animals(uuid) ON DELETE CASCADE
            )
            """;

    private static final String CREATE_SNAPSHOTS = """
            CREATE TABLE IF NOT EXISTS snapshots (
                animal_uuid TEXT PRIMARY KEY,
                data BLOB NOT NULL,
                saved_at INTEGER NOT NULL,
                FOREIGN KEY (animal_uuid) REFERENCES animals(uuid) ON DELETE CASCADE
            )
            """;

    private static final String INDEX_OWNERS_PLAYER = """
            CREATE INDEX IF NOT EXISTS owners_by_player
            ON owners(player_uuid)
            """;

    private static final String UPSERT_ANIMAL = """
            INSERT INTO animals (
                uuid, type, name, state, genetics, care,
                hungry_since, dirty_since, last_processed_at, unloaded_at,
                affliction_elapsed, affliction_at, last_milk_at, wool_ready_at,
                neutered, loaded_visit_start, mature_at, shed_ready_at, egg_ready_at,
                care_up_remainder, care_down_remainder, stats_revision,
                world, x, y, z
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                type = excluded.type,
                name = excluded.name,
                state = excluded.state,
                genetics = excluded.genetics,
                care = excluded.care,
                hungry_since = excluded.hungry_since,
                dirty_since = excluded.dirty_since,
                last_processed_at = excluded.last_processed_at,
                unloaded_at = excluded.unloaded_at,
                affliction_elapsed = excluded.affliction_elapsed,
                affliction_at = excluded.affliction_at,
                last_milk_at = excluded.last_milk_at,
                wool_ready_at = excluded.wool_ready_at,
                neutered = excluded.neutered,
                loaded_visit_start = excluded.loaded_visit_start,
                mature_at = excluded.mature_at,
                shed_ready_at = excluded.shed_ready_at,
                egg_ready_at = excluded.egg_ready_at,
                care_up_remainder = excluded.care_up_remainder,
                care_down_remainder = excluded.care_down_remainder,
                stats_revision = excluded.stats_revision,
                world = excluded.world,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z
            """;

    private static final String SELECT_ANIMAL = "SELECT * FROM animals WHERE uuid = ?";
    private static final String EXISTS_ANIMAL = "SELECT 1 FROM animals WHERE uuid = ? LIMIT 1";
    private static final String DELETE_ANIMAL = "DELETE FROM animals WHERE uuid = ?";
    // Skips animals whose row is already gone, so a late snapshot cannot trip the foreign key.
    private static final String UPSERT_SNAPSHOT = """
            INSERT INTO snapshots (animal_uuid, data, saved_at)
            SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM animals WHERE uuid = ?)
            ON CONFLICT(animal_uuid) DO UPDATE SET data = excluded.data, saved_at = excluded.saved_at
            """;
    private static final String SELECT_SNAPSHOT = "SELECT data, saved_at FROM snapshots WHERE animal_uuid = ?";
    private static final String DELETE_SNAPSHOT = "DELETE FROM snapshots WHERE animal_uuid = ?";

    private final SqliteDatabase database;
    private HusbandryOwnershipStore ownership;
    /**
     * A relaxed save has been committed and no complete checkpoint from {@link #syncRelaxed()} has
     * followed it yet. Only that checkpoint clears this. A later synced commit usually puts the
     * relaxed save on disk too, but not always: a commit that changes no page (the same rows
     * written again) writes nothing and so syncs nothing. Read and written under the database's lock.
     */
    private boolean unsynced;

    private HusbandryRepository(SqliteDatabase database) {
        this.database = database;
    }

    public static HusbandryRepository open(File dbFile) {
        SqliteProvider.ensureDriverLoaded();
        SqliteDatabase sqlite = new SqliteDatabase(dbFile);
        HusbandryRepository repository = new HusbandryRepository(sqlite);
        try {
            repository.initSchema();
        } catch (RuntimeException ex) {
            try {
                sqlite.close();
            } catch (Exception closeError) {
                ex.addSuppressed(closeError);
            }
            throw ex;
        }
        return repository;
    }

    private void initSchema() {
        database.execute("PRAGMA journal_mode=WAL");
        database.execute("PRAGMA foreign_keys=ON");
        database.execute("PRAGMA synchronous=FULL");
        database.execute("PRAGMA busy_timeout=5000");
        database.execute(CREATE_ANIMALS);
        database.execute(CREATE_OWNERS);
        database.execute(INDEX_OWNERS_PLAYER);
        database.execute(CREATE_SNAPSHOTS);
        migrateSchema();
        ownership = new HusbandryOwnershipStore(database);
    }

    private void migrateSchema() {
        int version = queryOne("PRAGMA user_version", result -> result.getInt(1)).orElse(0);
        if (version < 2) {
            database.execute("UPDATE animals SET genetics = MIN(genetics / 10, 1000)");
            database.execute("PRAGMA user_version = 2");
            version = 2;
        }
        if (version < 3) {
            if (!hasColumn("animals", "mature_at")) {
                database.execute("ALTER TABLE animals ADD COLUMN mature_at INTEGER");
            }
            database.execute("PRAGMA user_version = 3");
            version = 3;
        }
        if (version < 4) {
            if (!hasColumn("animals", "shed_ready_at")) {
                database.execute("ALTER TABLE animals ADD COLUMN shed_ready_at INTEGER");
            }
            database.execute("PRAGMA user_version = 4");
            version = 4;
        }
        if (version < 5) {
            if (!hasColumn("animals", "egg_ready_at")) {
                database.execute("ALTER TABLE animals ADD COLUMN egg_ready_at INTEGER");
            }
            database.execute("PRAGMA user_version = 5");
            version = 5;
        }
        if (version < 6) {
            if (!hasColumn("animals", "care_up_remainder")) {
                database.execute("ALTER TABLE animals ADD COLUMN care_up_remainder INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn("animals", "care_down_remainder")) {
                database.execute("ALTER TABLE animals ADD COLUMN care_down_remainder INTEGER NOT NULL DEFAULT 0");
            }
            database.execute("PRAGMA user_version = 6");
        }
        if (version < 7) {
            if (!hasColumn("animals", "stats_revision")) {
                database.execute("ALTER TABLE animals ADD COLUMN stats_revision TEXT");
            }
            database.execute("PRAGMA user_version = 7");
        }
        if (version < 8) {
            if (!hasColumn("animals", "world")) {
                database.execute("ALTER TABLE animals ADD COLUMN world TEXT");
            }
            if (!hasColumn("animals", "x")) {
                database.execute("ALTER TABLE animals ADD COLUMN x INTEGER");
            }
            if (!hasColumn("animals", "y")) {
                database.execute("ALTER TABLE animals ADD COLUMN y INTEGER");
            }
            if (!hasColumn("animals", "z")) {
                database.execute("ALTER TABLE animals ADD COLUMN z INTEGER");
            }
            database.execute("PRAGMA user_version = 8");
        }
    }

    private boolean hasColumn(String table, String column) {
        return queryList("PRAGMA table_info(" + table + ")", result -> result.getString("name"))
                .contains(column);
    }

    public int resetStaleStats(String revision, Random random) {
        if (revision == null || revision.isBlank()) {
            return 0;
        }
        String current = revision.trim();
        List<HusbandryAnimal> stale = queryList(
                """
                SELECT * FROM animals
                WHERE stats_revision IS NULL
                   OR TRIM(stats_revision) = ''
                   OR stats_revision != ?
                """,
                HusbandryRepository::mapAnimal,
                current);
        if (stale.isEmpty()) {
            return 0;
        }
        Random rng = random == null ? ThreadLocalRandom.current() : random;
        int max = HusbandryConfig.initialGeneticMax();
        for (HusbandryAnimal animal : stale) {
            animal.setGenetics(max <= 0 ? 0 : rng.nextInt(max + 1));
            animal.setCare(0);
            animal.setCareUpRemainderSeconds(0);
            animal.setCareDownRemainderSeconds(0);
            animal.setStatsRevision(current);
        }
        upsertAnimals(stale);
        return stale.size();
    }

    public void upsertAnimal(HusbandryAnimal animal) {
        if (animal == null || animal.uuid() == null) {
            return;
        }
        upsertAnimals(List.of(animal));
    }

    public void upsertAnimals(Collection<HusbandryAnimal> animals) {
        List<HusbandryAnimal> valid = valid(animals);
        if (valid.isEmpty()) {
            return;
        }
        database.runTransaction(connection -> writeAnimals(connection, valid));
    }

    private static List<HusbandryAnimal> valid(Collection<HusbandryAnimal> animals) {
        List<HusbandryAnimal> valid = new ArrayList<>();
        if (animals == null) {
            return valid;
        }
        for (HusbandryAnimal animal : animals) {
            if (animal == null || animal.uuid() == null) {
                continue;
            }
            valid.add(animal);
        }
        return valid;
    }

    private static void writeAnimals(Connection connection, List<HusbandryAnimal> valid) {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ANIMAL)) {
            for (HusbandryAnimal animal : valid) {
                bindAnimal(statement, animal);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SqliteDatabaseException("Failed to upsert animals", e);
        }
    }

    /**
     * The routine state of loaded animals and the entity copies taken with it, in one transaction:
     * the animals first, since a copy is only stored for an animal that has a row.
     *
     * <p>A {@code relaxed} save is committed without its own disk sync, which is what a save on the
     * server thread costs. A crash or kill of the server process still loses nothing, because the
     * commit already reached the operating system. {@link #syncRelaxed()} puts it on disk, and the
     * once-a-minute husbandry tick calls that, so an operating-system crash or power loss can only
     * roll back relaxed saves made since then. Every other write keeps {@code synchronous=FULL}.
     */
    public void saveLoaded(Collection<HusbandryAnimal> animals, Map<UUID, byte[]> snapshots, long savedAt,
            boolean relaxed) {
        List<HusbandryAnimal> valid = valid(animals);
        Map<UUID, byte[]> copies = snapshots == null ? Map.of() : snapshots;
        if (valid.isEmpty() && copies.isEmpty()) {
            return;
        }
        Consumer<Connection> work = connection -> {
            writeAnimals(connection, valid);
            writeSnapshots(connection, copies, savedAt);
        };
        // One lock for the level change, the transaction and the change back: no other thread's
        // write can run between them and be committed without its sync.
        synchronized (database) {
            if (!relaxed) {
                // Relaxed saves before this stay marked: this commit syncs the log only if it
                // changes a page, and rows written again unchanged change none.
                database.runTransaction(work);
                return;
            }
            synchronous("NORMAL");
            Throwable failure = null;
            try {
                database.runTransaction(work);
                unsynced = true;
            } catch (Throwable e) {
                // Errors too: TLibs rolls back and rethrows them, and the level must still go back.
                failure = e;
                throw e;
            } finally {
                restoreSynced(failure);
            }
        }
    }

    /**
     * Back to synced commits after a relaxed save, whatever became of it. A connection that cannot
     * be switched back is closed: ownership and estate changes rely on their sync, so husbandry
     * then fails loudly until the plugin restarts instead of committing without one.
     */
    private void restoreSynced(Throwable failure) {
        try {
            synchronous("FULL");
        } catch (Throwable restore) {
            try {
                closeDatabase();
            } catch (Throwable closing) {
                restore.addSuppressed(closing);
            }
            if (failure == null) {
                throw new SqliteDatabaseException("Could not restore synced husbandry commits", restore);
            }
            // The save's own failure is the one reported; this one travels with it.
            failure.addSuppressed(restore);
        }
    }

    /** Sets how commits on this connection reach the disk. Only possible outside a transaction. */
    void synchronous(String level) {
        database.execute("PRAGMA synchronous=" + level);
    }

    /** The connection's {@code PRAGMA synchronous} level (2 = FULL, 1 = NORMAL). For tests. */
    int synchronousLevel() {
        return queryOne("PRAGMA synchronous", result -> result.getInt(1)).orElseThrow();
    }

    void closeDatabase() {
        database.close();
    }

    /**
     * Puts relaxed saves on disk, if any are waiting. They stay marked until a checkpoint has
     * copied the whole log, so one that could not is tried again.
     */
    public void syncRelaxed() {
        synchronized (database) {
            if (unsynced && checkpointedAll()) {
                unsynced = false;
            }
        }
    }

    /**
     * Runs a passive checkpoint and tells whether it copied the whole write-ahead log, which
     * SQLite only does after syncing that log. A passive checkpoint does not throw when another
     * connection is in its way: it reports busy, or fewer frames copied than the log holds.
     */
    boolean checkpointedAll() {
        // Columns: busy, frames in the log, frames checkpointed.
        return queryOne("PRAGMA wal_checkpoint(PASSIVE)",
                result -> result.getInt(1) == 0 && result.getInt(2) == result.getInt(3)).orElse(false);
    }

    /** Whether a relaxed save is still waiting for its sync. For tests. */
    boolean relaxedPending() {
        synchronized (database) {
            return unsynced;
        }
    }

    public Optional<HusbandryAnimal> getAnimal(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return queryOne(SELECT_ANIMAL, HusbandryRepository::mapAnimal, uuid.toString());
    }

    public boolean exists(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return queryOne(EXISTS_ANIMAL, result -> true, uuid.toString()).isPresent();
    }

    public void deleteAnimal(UUID uuid) {
        if (uuid == null) {
            return;
        }
        executeUpdate(DELETE_ANIMAL, uuid.toString());
    }

    public void upsertOwner(HusbandryOwner owner) { ownership.put(owner); }

    public void saveAnimalAndOwner(HusbandryAnimal animal, HusbandryOwner owner) {
        if (!animal.uuid().equals(owner.animalUuid()) || !HusbandryOwnershipStore.recognized(owner.role())) {
            throw new IllegalArgumentException("Invalid animal ownership publication");
        }
        if (ownership.estateHeld(animal.uuid())) throw new IllegalStateException("Animal estate intent requires staff review");
        database.runTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_ANIMAL)) {
                String[] before = HusbandryOwnershipStore.slot(connection, animal.uuid().toString(), owner.characterUuid().toString());
                if (before != null && (!owner.playerUuid().toString().equals(before[0])
                        || !HusbandryOwnershipStore.recognized(before[1]))) throw new IllegalArgumentException("Conflicting owner slot");
                bindAnimal(statement, animal);
                statement.executeUpdate();
                HusbandryOwnershipStore.put(connection, animal.uuid().toString(), owner.ref(), owner.role());
            } catch (SQLException ex) {
                throw new SqliteDatabaseException("Could not publish animal ownership", ex);
            }
        });
    }

    /** The second UUID names a character, never an account. */
    public void deleteCharacterOwner(UUID animalUuid, UUID characterUuid) { ownership.remove(animalUuid, characterUuid); }

    /** An account is ambiguous once several of its characters can share the same animal. */
    @Deprecated
    public void deleteOwner(UUID animalUuid, UUID accountUuid) {
        throw new IllegalArgumentException("Use an explicit character owner");
    }

    public int countForPlayer(UUID playerUuid) { return ownership.countAccount(playerUuid); }

    public List<HusbandryOwner> listOwners(UUID animalUuid) { return ownership.owners(animalUuid); }

    public boolean hasAnyOwner(UUID animalUuid) { return ownership.hasAny(animalUuid); }
    public boolean hasHeldOwner(UUID animalUuid) { return ownership.held(animalUuid); }
    public boolean accountHasAnimal(UUID account, UUID animal) { return ownership.accountHas(account, animal); }
    public boolean isEstateHeld(UUID animal) { return ownership.estateHeld(animal); }
    public int heldOwners() { return ownership.heldCount(); }
    public boolean migrateOwners(java.util.function.BooleanSupplier ready,
            java.util.function.Function<UUID, Optional<net.tfminecraft.tlibs.character.CharacterRef>> heirs) {
        return ownership.migrate(ready, heirs);
    }
    SqliteDatabase ownershipDatabase() { return database; }

    public List<HusbandryOwned> listForCharacter(net.tfminecraft.tlibs.character.CharacterRef ref) {
        if (ref == null) return List.of();
        return queryList("""
                SELECT a.*, o.role AS owner_role FROM animals a
                INNER JOIN character_owners o ON o.animal_uuid = a.uuid
                WHERE o.account_uuid = ? AND o.character_uuid = ? AND o.role IN ('owner','coowner')
                """, result -> new HusbandryOwned(mapAnimal(result), result.getString("owner_role")),
                ref.ownerId().toString(), ref.characterId().toString());
    }

    public List<HusbandryOwned> listForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }
        return queryList(
                """
                SELECT a.*, MAX(o.role) AS owner_role
                FROM animals a
                INNER JOIN character_owners o ON o.animal_uuid = a.uuid
                WHERE o.account_uuid = ? AND o.role IN ('owner','coowner')
                GROUP BY a.uuid
                """,
                result -> new HusbandryOwned(mapAnimal(result), result.getString("owner_role")),
                playerUuid.toString());
    }

    public List<HusbandryAnimal> listOwnedAnimals() {
        return queryList(
                """
                SELECT a.*
                FROM animals a
                WHERE EXISTS (SELECT 1 FROM character_owners o WHERE o.animal_uuid = a.uuid)
                   OR EXISTS (SELECT 1 FROM owners o LEFT JOIN husbandry_owner_plan p
                     ON p.animal_uuid = o.animal_uuid AND p.account_uuid = o.player_uuid
                     WHERE o.animal_uuid = a.uuid AND (p.status IS NULL OR p.status = 'held'))
                """,
                HusbandryRepository::mapAnimal);
    }

    /** Stores the serialized entities in one transaction; animals without a row are skipped. */
    public void upsertSnapshots(Map<UUID, byte[]> snapshots, long savedAt) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        database.runTransaction(connection -> writeSnapshots(connection, snapshots, savedAt));
    }

    private static void writeSnapshots(Connection connection, Map<UUID, byte[]> snapshots, long savedAt) {
        if (snapshots.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SNAPSHOT)) {
            for (Map.Entry<UUID, byte[]> snapshot : snapshots.entrySet()) {
                if (snapshot.getKey() == null || snapshot.getValue() == null) {
                    continue;
                }
                String uuid = snapshot.getKey().toString();
                bindParams(statement, uuid, snapshot.getValue(), savedAt, uuid);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SqliteDatabaseException("Failed to upsert snapshots", e);
        }
    }

    public Optional<HusbandrySnapshot> getSnapshot(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return queryOne(
                SELECT_SNAPSHOT,
                result -> new HusbandrySnapshot(result.getBytes("data"), result.getLong("saved_at")),
                uuid.toString());
    }

    public void deleteSnapshot(UUID uuid) {
        if (uuid == null) {
            return;
        }
        executeUpdate(DELETE_SNAPSHOT, uuid.toString());
    }

    public void checkpointWal(boolean truncate) {
        String mode = truncate ? "TRUNCATE" : "PASSIVE";
        database.execute("PRAGMA wal_checkpoint(" + mode + ")");
    }

    public void close() {
        try {
            checkpointWal(true);
        } catch (RuntimeException ignored) {
            // still close
        }
        database.close();
    }

    private static void bindAnimal(PreparedStatement statement, HusbandryAnimal animal) throws SQLException {
        bindParams(
                statement,
                animal.uuid().toString(),
                animal.type() == null ? "" : animal.type(),
                animal.name() == null ? "" : animal.name(),
                animal.state().storage(),
                animal.genetics(),
                animal.care(),
                animal.hungrySince(),
                animal.dirtySince(),
                animal.lastProcessedAt(),
                animal.unloadedAt(),
                animal.afflictionElapsed(),
                animal.afflictionAt(),
                animal.lastMilkAt(),
                animal.woolReadyAt(),
                animal.neutered() ? 1 : 0,
                animal.loadedVisitStart(),
                animal.matureAt(),
                animal.shedReadyAt(),
                animal.eggReadyAt(),
                animal.careUpRemainderSeconds(),
                animal.careDownRemainderSeconds(),
                blankToNull(animal.statsRevision()),
                animal.hasLocation() ? animal.world() : null,
                animal.hasLocation() ? animal.x() : null,
                animal.hasLocation() ? animal.y() : null,
                animal.hasLocation() ? animal.z() : null);
    }

    private static HusbandryAnimal mapAnimal(ResultSet result) throws SQLException {
        HusbandryAnimal animal = new HusbandryAnimal(
                UUID.fromString(result.getString("uuid")),
                result.getString("type"),
                result.getString("name"));
        animal.setState(HusbandryAnimalState.fromStorage(result.getString("state")));
        animal.setGenetics(result.getInt("genetics"));
        animal.setCare(result.getInt("care"));
        animal.setHungrySince(nullableLong(result, "hungry_since"));
        animal.setDirtySince(nullableLong(result, "dirty_since"));
        animal.setLastProcessedAt(result.getLong("last_processed_at"));
        animal.setUnloadedAt(nullableLong(result, "unloaded_at"));
        animal.setAfflictionElapsed(result.getDouble("affliction_elapsed"));
        animal.setAfflictionAt(result.getDouble("affliction_at"));
        animal.setLastMilkAt(nullableLong(result, "last_milk_at"));
        animal.setWoolReadyAt(nullableLong(result, "wool_ready_at"));
        animal.setNeutered(result.getInt("neutered") != 0);
        animal.setLoadedVisitStart(nullableLong(result, "loaded_visit_start"));
        animal.setMatureAt(nullableLong(result, "mature_at"));
        animal.setShedReadyAt(nullableLong(result, "shed_ready_at"));
        animal.setEggReadyAt(nullableLong(result, "egg_ready_at"));
        animal.setCareUpRemainderSeconds(intOrZero(result, "care_up_remainder"));
        animal.setCareDownRemainderSeconds(intOrZero(result, "care_down_remainder"));
        animal.setStatsRevision(nullableString(result, "stats_revision"));
        animal.setStoredLocation(
                nullableString(result, "world"),
                nullableInt(result, "x"),
                nullableInt(result, "y"),
                nullableInt(result, "z"));
        return animal;
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String nullableString(ResultSet result, String column) throws SQLException {
        try {
            String value = result.getString(column);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value;
        } catch (SQLException ex) {
            return null;
        }
    }

    private static Integer nullableInt(ResultSet result, String column) throws SQLException {
        try {
            int value = result.getInt(column);
            return result.wasNull() ? null : value;
        } catch (SQLException ex) {
            return null;
        }
    }

    private static int intOrZero(ResultSet result, String column) throws SQLException {
        try {
            int value = result.getInt(column);
            return result.wasNull() ? 0 : value;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private int executeUpdate(String sql, Object... params) {
        synchronized (database) {
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                bindParams(statement, params);
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new SqliteDatabaseException("Failed to execute update: " + sql, e);
            }
        }
    }

    private <T> Optional<T> queryOne(String sql, ResultSetFunction<T> mapper, Object... params) {
        List<T> rows = queryList(sql, mapper, params);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private <T> List<T> queryList(String sql, ResultSetFunction<T> mapper, Object... params) {
        List<T> rows = new ArrayList<>();
        synchronized (database) {
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                bindParams(statement, params);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(mapper.apply(result));
                    }
                }
            } catch (SQLException e) {
                throw new SqliteDatabaseException("Failed to query: " + sql, e);
            }
        }
        return rows;
    }

    private static void bindParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value == null) {
                statement.setNull(i + 1, Types.NULL);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    @FunctionalInterface
    private interface ResultSetFunction<T> {
        T apply(ResultSet result) throws SQLException;
    }
}
