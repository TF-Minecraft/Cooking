package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.quality.PermissionEffectsConfig;
import net.tfminecraft.cooking.quality.PermissionEffectsConfig.PickupEffect;
import net.tfminecraft.cooking.quality.QualityConfig;

class CustomFishingCatchTest {
    private static final List<String> LIVE_IDS = List.of(
            "tfmc_tuna_fish",
            "tfmc_pike_fish",
            "tfmc_sardine_fish",
            "tfmc_sunfish",
            "tfmc_cat_fish",
            "tfmc_salmon_void_fish",
            "tfmc_woodskip_fish",
            "tfmc_sturgeon_fish",
            "tfmc_red_snapper_fish",
            "tfmc_octopus",
            "tfmc_blue_jellyfish",
            "tfmc_pink_jellyfish",
            "tfmc_gold_fish",
            "tfmc_perch_fish",
            "tfmc_mullet_fish",
            "tfmc_carp_fish");

    @BeforeEach
    void loadLiveMappings() {
        QualityConfig.apply(1, 5, null, null);
        PermissionEffectsConfig.apply(List.of(), List.of());
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @AfterEach
    void resetPermissions() {
        PermissionEffectsConfig.apply(List.of(), List.of());
        QualityConfig.apply(1, 5, null, null);
    }

    @Test
    void mapsAllSixteenLiveCatches() {
        assertEquals(Set.copyOf(LIVE_IDS), CustomFishingCatalog.catches().stream()
                .map(CatchMapping::id)
                .collect(Collectors.toSet()));
        assertEquals("Tuna", CustomFishingCatalog.find("tfmc_tuna_fish").origin());
        assertEquals("Void Salmon", CustomFishingCatalog.find("tfmc_salmon_void_fish").origin());
        assertEquals("Catfish", CustomFishingCatalog.find("tfmc_cat_fish").origin());
        assertEquals("Goldfish", CustomFishingCatalog.find("tfmc_gold_fish").origin());
        assertEquals("Blue Jellyfish", CustomFishingCatalog.find("tfmc_blue_jellyfish").origin());
        assertEquals("Pink Jellyfish", CustomFishingCatalog.find("tfmc_pink_jellyfish").origin());
        assertNull(CustomFishingCatalog.find("not_a_fish"));
    }

    @Test
    void classifiesFishOctopusAndJellyfish() {
        assertEquals(13, countCut("fish"));
        assertEquals(1, countCut("octopus"));
        assertEquals(2, countCut("jellyfish"));
        assertEquals("octopus", CustomFishingCatalog.find("tfmc_octopus").cut());
        assertEquals("jellyfish", CustomFishingCatalog.find("tfmc_pink_jellyfish").cut());
        assertEquals("fish", CustomFishingCatalog.find("tfmc_carp_fish").cut());
    }

    @Test
    void rodBandsAndUnknownRodFallback() {
        assertBand("fishing_rod", 1, 2);
        assertBand("steel_rod", 2, 3);
        assertBand("abyssalite_rod", 3, 4);
        assertBand("mythril_rod", 4, 5);
        assertBand("no_such_rod", 1, 5);
        assertEquals(1, FishingQuality.finish("fishing_rod", 1, null));
        assertEquals(2, FishingQuality.finish("fishing_rod", 2, null));
        assertEquals(2, FishingQuality.finish("steel_rod", 2, null));
        assertEquals(3, FishingQuality.finish("steel_rod", 3, null));
        assertEquals(3, FishingQuality.finish("abyssalite_rod", 3, null));
        assertEquals(4, FishingQuality.finish("abyssalite_rod", 4, null));
        assertEquals(4, FishingQuality.finish("mythril_rod", 4, null));
        assertEquals(5, FishingQuality.finish("mythril_rod", 5, null));
        assertEquals(1, FishingQuality.finish("no_such_rod", 1, null));
        assertEquals(5, FishingQuality.finish("no_such_rod", 5, null));
    }

    @Test
    void pickupPermissionRaisesInsideTheCookingClamp() {
        PermissionEffectsConfig.apply(List.of(new PickupEffect("fisher", "cooking.quality.fisher", 4, 1)), List.of());

        assertEquals(2, FishingQuality.finish("fishing_rod", 2, null));
        assertEquals(2, OriginQualityResolver.applyPickupPermissions(null, 2));
        assertEquals(4, OriginQualityResolver.adjust(2, 4, 1));
        assertEquals(5, OriginQualityResolver.adjust(5, 4, 1));
        assertEquals(1, OriginQualityResolver.adjust(0, 1, 0));
    }

    @Test
    void unconfiguredLootAndNonItemsStayUntouched() {
        CustomFishingCatchAdapter.Decision unknown = CustomFishingCatchAdapter.decide("treasure", 40.0, true);
        CustomFishingCatchAdapter.Decision entity = CustomFishingCatchAdapter.decide("tfmc_tuna_fish", 40.0, false);

        assertEquals(CustomFishingCatchAdapter.Action.UNTOUCHED, unknown.action());
        assertFalse(unknown.replacesItem());
        assertEquals(CustomFishingCatchAdapter.Action.UNTOUCHED, entity.action());
        assertFalse(entity.replacesItem());
    }

    @Test
    void configuredItemCatchIsReplacedWithoutStarData() {
        CustomFishingCatchAdapter.Decision decision = CustomFishingCatchAdapter.decide("tfmc_tuna_fish", 30.4, true);

        assertTrue(decision.replacesItem());
        assertEquals("tfmc_tuna_fish", decision.mapping().id());
        assertEquals("Tuna", decision.mapping().origin());
        assertEquals("fish", decision.mapping().cut());
        assertEquals(30, decision.sizeCm());
    }

    @Test
    void malformedConfiguredSizeIsNotReplaced() {
        assertMalformed("tfmc_octopus", null);
        assertMalformed("tfmc_octopus", Double.NaN);
        assertMalformed("tfmc_octopus", Double.POSITIVE_INFINITY);
        assertMalformed("tfmc_octopus", Double.NEGATIVE_INFINITY);
        assertMalformed("tfmc_octopus", 0.0);
        assertMalformed("tfmc_octopus", -8.0);
        assertMalformed("tfmc_octopus", 0.4);
        assertEquals(1, CustomFishingCatchAdapter.decide("tfmc_octopus", 0.5, true).sizeCm());
        assertEquals(11, CustomFishingCatchAdapter.decide("tfmc_octopus", 10.5, true).sizeCm());
    }

    private static void assertMalformed(String id, Double size) {
        CustomFishingCatchAdapter.Decision decision = CustomFishingCatchAdapter.decide(id, size, true);
        assertEquals(CustomFishingCatchAdapter.Action.MALFORMED, decision.action());
        assertFalse(decision.replacesItem());
        assertNull(decision.mapping());
    }

    private static void assertBand(String rodId, int min, int max) {
        RodBand band = CustomFishingCatalog.band(rodId);
        assertEquals(min, band.min());
        assertEquals(max, band.max());
    }

    private static int countCut(String cut) {
        return (int) CustomFishingCatalog.catches().stream().filter(mapping -> cut.equals(mapping.cut())).count();
    }
}
