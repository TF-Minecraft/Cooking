package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SlotSnapshotTest {

    @Test
    void snapshotKeepsEachIngredientModel() {
        String encoded = Encoder.formatSlot("input_1", "APPLE", 0, "tfmc_cooking:red_meat_steak_boiled")
                + ":"
                + Encoder.formatSlot("input_2", "APPLE", 0, "tfmc_cooking:pork_steak_boiled");

        String[] parts = encoded.split(":", -1);
        assertEquals(2, parts.length);
        Encoder.ParsedSlot first = Encoder.parseSlot(parts[0]);
        Encoder.ParsedSlot second = Encoder.parseSlot(parts[1]);

        assertEquals("input_1", first.slotId());
        assertEquals("tfmc_cooking:red_meat_steak_boiled", first.itemModel());
        assertEquals("input_2", second.slotId());
        assertEquals("tfmc_cooking:pork_steak_boiled", second.itemModel());
    }

    @Test
    void snapshotKeepsItemModelKeysThatContainDots() {
        Encoder.ParsedSlot parsed = Encoder.parseSlot(
                Encoder.formatSlot("input_3", "CARROT", 4, "tfmc_cooking:item/carrot.boiled"));

        assertEquals("CARROT", parsed.material());
        assertEquals(4, parsed.customModelData());
        assertEquals("tfmc_cooking:item/carrot.boiled", parsed.itemModel());
    }

    @Test
    void olderSnapshotsWithoutAnItemModelStillParse() {
        Encoder.ParsedSlot parsed = Encoder.parseSlot("input_1.POTATO.0");

        assertEquals("POTATO", parsed.material());
        assertEquals(0, parsed.customModelData());
        assertNull(parsed.itemModel());
    }
}
