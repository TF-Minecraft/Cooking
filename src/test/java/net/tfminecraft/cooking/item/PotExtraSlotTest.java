package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.cooking.PotReference;

class PotExtraSlotTest {

    @Test
    void extraKeysCoverGarnishSpiceSaltAndPepperOnly() {
        assertEquals("extra_garnish", PotReference.extraSlotKey("Garnish"));
        assertEquals("extra_spice", PotReference.extraSlotKey("spice"));
        assertEquals("extra_salt", PotReference.extraSlotKey("salt"));
        assertEquals("extra_pepper", PotReference.extraSlotKey("Pepper"));
        assertNull(PotReference.extraSlotKey("vegetable"));
        assertNull(PotReference.extraSlotKey(null));
    }

    @Test
    void extraSlotIdsAreNotMainSlots() {
        assertTrue(PotReference.isExtraSlot("extra_salt"));
        assertFalse(PotReference.isExtraSlot("input_1"));
        assertFalse(PotReference.isExtraSlot(null));
    }
}
