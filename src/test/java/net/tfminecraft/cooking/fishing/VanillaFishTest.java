package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.quality.PermissionEffectsConfig;
import net.tfminecraft.cooking.quality.QualityConfig;

class VanillaFishTest {

    @TempDir
    File tempDir;

    @BeforeEach
    void loadLiveMappings() {
        QualityConfig.apply(1, 5, null, null);
        PermissionEffectsConfig.apply(java.util.List.of(), java.util.List.of());
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @AfterEach
    void resetQuality() {
        PermissionEffectsConfig.apply(java.util.List.of(), java.util.List.of());
        QualityConfig.apply(1, 5, null, null);
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @Test
    void mapsFourVanillaDefaults() {
        assertEquals(Set.of("COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH"),
                CustomFishingCatalog.vanillaFish().stream().map(VanillaFish::material).collect(Collectors.toSet()));
        assertFish("COD", "Cod", 45);
        assertFish("SALMON", "Salmon", 60);
        assertFish("TROPICAL_FISH", "Tropical Fish", 12);
        assertFish("PUFFERFISH", "Pufferfish", 15);
        assertNull(CustomFishingCatalog.findVanilla("COOKED_COD"));
    }

    @Test
    void customModelDataAndUnknownMaterialsStayUntouched() {
        VanillaFishAdapter.Decision plain = VanillaFishAdapter.decide("COD", false);
        VanillaFishAdapter.Decision modeled = VanillaFishAdapter.decide("COD", true);
        VanillaFishAdapter.Decision beef = VanillaFishAdapter.decide("BEEF", false);

        assertTrue(plain.replaces());
        assertEquals("Cod", plain.fish().origin());
        assertEquals(45, plain.fish().sizeCm());
        assertFalse(modeled.replaces());
        assertNull(modeled.fish());
        assertFalse(beef.replaces());
    }

    @Test
    void malformedVanillaEntryIsSkipped() throws Exception {
        File file = new File(tempDir, "custom-fishing.yml");
        Files.writeString(file.toPath(), """
                vanilla:
                  COD:
                    origin: Cod
                    cut: fish
                    size-cm: 45
                  BROKEN:
                    origin: Broken
                    cut: soup
                    size-cm: 0
                """);
        CustomFishingCatalog.load(file);

        assertEquals("Cod", CustomFishingCatalog.findVanilla("COD").origin());
        assertNull(CustomFishingCatalog.findVanilla("BROKEN"));
    }

    @Test
    void vanillaQualityUsesPickupRangeNotRodBands() {
        assertEquals(1, PickupQuality.finish(1, null));
        assertEquals(5, PickupQuality.finish(5, null));
        assertEquals(1, PickupQuality.finish(0, null));
        assertEquals(4, FishingQuality.finish("mythril_rod", 1, null));
    }

    private static void assertFish(String material, String origin, int sizeCm) {
        VanillaFish fish = CustomFishingCatalog.findVanilla(material);
        assertEquals(origin, fish.origin());
        assertEquals("fish", fish.cut());
        assertEquals(sizeCm, fish.sizeCm());
    }
}
