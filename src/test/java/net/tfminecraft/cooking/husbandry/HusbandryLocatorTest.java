package net.tfminecraft.cooking.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HusbandryLocatorTest {

    @Test
    void onlyAnAnimalLastSeenInAScannedWorldCanBeAGhost() {
        assertTrue(HusbandryLocator.inScannedWorld("TFMC_Map", List.of("TFMC_Map", "TFMC_Map_nether")));
        assertTrue(HusbandryLocator.inScannedWorld(null, List.of("TFMC_Map")));
        assertTrue(HusbandryLocator.inScannedWorld("  ", List.of("TFMC_Map")));
        assertFalse(HusbandryLocator.inScannedWorld("other", List.of("TFMC_Map")));
        assertFalse(HusbandryLocator.inScannedWorld("TFMC_Map", null));
    }

    @Test
    void theUnreadableLogNamesTheFirstProblemsAndCountsTheRest() {
        assertEquals("[Cooking] Animal scan could not read 1 file or chunk: playerdata/a.dat: truncated",
                HusbandryLocator.unreadableLog(List.of("playerdata/a.dat: truncated")));
        List<String> many = java.util.stream.IntStream.range(0, HusbandryLocator.LOGGED_PROBLEMS + 3)
                .mapToObj(i -> "p" + i).toList();
        String line = HusbandryLocator.unreadableLog(many);
        assertTrue(line.startsWith("[Cooking] Animal scan could not read 13 files or chunks: p0; p1;"), line);
        assertTrue(line.endsWith("; p9; and 3 more"), line);
    }

    @Test
    void droppingAGhostRemovesTheRowAndOwners(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            UUID animalId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID coOwnerId = UUID.randomUUID();
            HusbandryAnimal animal = new HusbandryAnimal(animalId, "COW", "Bess");
            animal.setLastLocation("TFMC_Map", 4369, 167, 1950);
            animal.setUnloadedAt(50L);
            repository.upsertAnimal(animal);
            repository.upsertOwner(new HusbandryOwner(animalId, ownerId, ownerId, "owner"));
            repository.upsertOwner(new HusbandryOwner(animalId, coOwnerId, coOwnerId, "coowner"));
            HusbandryEntities.putLoaded(animal);

            String line = HusbandryLocator.deleteGhost(repository, animal);

            assertFalse(repository.exists(animalId));
            assertTrue(repository.listOwners(animalId).isEmpty());
            assertEquals(0, repository.countForPlayer(ownerId));
            assertEquals(0, repository.countForPlayer(coOwnerId));
            assertTrue(HusbandryEntities.getLoaded(animalId).isEmpty());
            assertFalse(HusbandryLocator.isMissing(animalId));
            String owners = List.of(
                            new HusbandryOwner(animalId, ownerId, ownerId, "owner"),
                            new HusbandryOwner(animalId, coOwnerId, coOwnerId, "coowner"))
                    .stream()
                    .sorted((left, right) -> left.playerUuid().compareTo(right.playerUuid()))
                    .map(owner -> owner.playerUuid() + " (" + owner.role() + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElseThrow();
            assertEquals(
                    "[Cooking] Dropped ghost animal Bess (COW) " + animalId
                            + " owners=" + owners
                            + " last seen TFMC_Map 4369, 167, 1950",
                    line);
        } finally {
            repository.close();
            HusbandryEntities.evict(null);
        }
    }
}
