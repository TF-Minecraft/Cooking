package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.item.tag.TagTrack;

class PotMashTest {

    @Test
    void mashAcceptsMashableFoodAtAnyCookState() {
        assertTrue(PotReference.canMash(food(true, 0)));
        assertTrue(PotReference.canMash(food(true, 1)));
        assertTrue(PotReference.canMash(food(true, 2)));
        assertTrue(PotReference.canMash(food(true, 3)));
        assertFalse(PotReference.canMash(food(false, 3)));
        assertFalse(PotReference.canMash(null));
    }

    @Test
    void markBoiledForcesCookedTrackToBoiled() {
        FoodItem raw = food(true, 0);
        PotReference.markBoiled(raw);
        assertEquals(3, raw.getTagTrack("cooked").getValue());
    }

    private static FoodItem food(boolean mashable, int cooked) {
        FoodItem item = new FoodItem("meat_red_meat", "Steak", true);
        item.setMashable(mashable);
        TagTrack track = new TagTrack("cooked", false, List.of());
        track.setValue(cooked);
        item.addOrModifyTrack(track);
        return item;
    }
}
