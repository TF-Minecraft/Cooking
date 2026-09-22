package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyFishTest {

    @BeforeEach
    void loadLiveMappings() {
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @AfterEach
    void reloadLiveMappings() {
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @Test
    void leftoverCodUsesModelDataAndLegacySize() {
        LegacyFishAdapter.Decision tagged = LegacyFishAdapter.decide(true, "COD", 50002, null, null);
        LegacyFishAdapter.Decision silverTuna = LegacyFishAdapter.decide(false, "COD", 50002, null, null);
        LegacyFishAdapter.Decision rainbow = LegacyFishAdapter.decide(false, "COD", 50100, null, null);
        LegacyFishAdapter.Decision unknown = LegacyFishAdapter.decide(false, "COD", 49999, null, null);
        LegacyFishAdapter.Decision rod = LegacyFishAdapter.decide(false, "FISHING_ROD", 50001, null, null);
        LegacyFishAdapter.Decision sized = LegacyFishAdapter.decide(false, "COD", 50001, null, 12.6);

        assertFalse(tagged.replaces());
        assertTrue(silverTuna.replaces());
        assertEquals("tfmc_tuna_fish", silverTuna.mapping().id());
        assertEquals(45, silverTuna.sizeCm());
        assertFalse(rainbow.replaces());
        assertFalse(unknown.replaces());
        assertFalse(rod.replaces());
        assertEquals(13, sized.sizeCm());
    }

    @Test
    void unknownLootIdDoesNotFallThroughToModelData() {
        LegacyFishAdapter.Decision unknownId = LegacyFishAdapter.decide(false, "COD", 50002, "not_a_fish", null);
        LegacyFishAdapter.Decision knownId = LegacyFishAdapter.decide(false, "COD", null, "tfmc_octopus", 80.2);

        assertFalse(unknownId.replaces());
        assertTrue(knownId.replaces());
        assertEquals("Octopus", knownId.mapping().origin());
        assertEquals(80, knownId.sizeCm());
    }
}
