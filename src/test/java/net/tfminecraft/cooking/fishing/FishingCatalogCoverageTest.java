package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FishingCatalogCoverageTest {
    @TempDir Path directory;
    @AfterEach void restore() { CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml")); }

    @Test void replacingCatalogsClearsPreviousDefinitionsAndUsesUnknownBandsForEmptyIdentifiers() {
        var catches=java.util.Map.of("tuna",new CatchMapping("tuna","Tuna","fish"));
        CustomFishingCatalog.replace(catches,java.util.Map.of(),new RodBand(2,3));
        assertNotNull(CustomFishingCatalog.find("TUNA")); assertNull(CustomFishingCatalog.find(null)); assertNull(CustomFishingCatalog.find(" "));
        assertEquals(new RodBand(2,3),CustomFishingCatalog.band(null)); assertEquals(new RodBand(2,3),CustomFishingCatalog.band(" "));
        var fish=java.util.Map.of("COD",new VanillaFish("COD","Cod","fish",30));
        CustomFishingCatalog.replace(java.util.Map.of(),java.util.Map.of(),null,fish);
        assertNull(CustomFishingCatalog.find("tuna")); assertEquals(30,CustomFishingCatalog.findVanilla("cod").sizeCm());
        assertNull(CustomFishingCatalog.findVanilla(null)); assertNull(CustomFishingCatalog.findVanilla(" "));
        assertNull(CustomFishingCatalog.cutRule(null)); assertNull(CustomFishingCatalog.cutRule(" "));
    }

    @Test void missingAndFailedConfigurationReadsResetTheCatalog() {
        CustomFishingCatalog.load(null); assertTrue(CustomFishingCatalog.catches().isEmpty());
        CustomFishingCatalog.load(directory.resolve("absent").toFile()); assertTrue(CustomFishingCatalog.vanillaFish().isEmpty());
        File raced=mock(File.class); when(raced.isFile()).thenReturn(true); when(raced.toPath()).thenReturn(directory.resolve("deleted"));
        CustomFishingCatalog.load(raced); assertEquals(new RodBand(1,5),CustomFishingCatalog.unknownRod());
        CustomFishingCatalog.replace(null,null,null,null,null,null,0); assertEquals(45,CustomFishingCatalog.legacySizeCm());
    }

    @Test void malformedEntriesAreSkippedAndDuplicateModelsHaveDeterministicOwnership() throws Exception {
        Path path=directory.resolve("fishing.yml"); Files.writeString(path,"""
                # malformed definitions should not poison valid ones
                ignored line
                : invalid key
                legacy-size-cm: NaN
                catches:
                  bad:
                    origin: Fish
                    cut: unknown
                  first:
                    origin: Tuna
                    cut: fish
                    model-data: 10, nope, 11
                  second:
                    origin: Pike
                    cut: fish
                    model-data: 10
                  third:
                    origin: Cod
                    cut: fish
                rods:
                  bad:
                    min: 7
                    max: 1
                unknown-rod:
                  min: invalid
                  max: 5
                vanilla:
                  COD:
                    origin: Cod
                    cut: fish
                    size-cm: wrong
                cutting:
                  fish:
                    output: fillet
                    food-per-cm: NaN
                    min-total: 1
                    max-total: 20
                    cm-per-portion: 10
                    max-portions: 8
                """);
        CustomFishingCatalog.load(path.toFile());
        assertEquals(3,CustomFishingCatalog.catches().size()); assertEquals("second",CustomFishingCatalog.findModel(10).id());
        assertEquals("first",CustomFishingCatalog.findModel(11).id()); assertNull(CustomFishingCatalog.findModel(12));
        assertEquals(45,CustomFishingCatalog.legacySizeCm()); assertEquals(new RodBand(1,5),CustomFishingCatalog.band("bad"));
        assertNull(CustomFishingCatalog.findVanilla("COD")); assertNull(CustomFishingCatalog.cutRule("fish"));
    }
}
