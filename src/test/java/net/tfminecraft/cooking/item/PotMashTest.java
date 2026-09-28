package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.item.tag.TagStep;
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

    @Test
    void rebuildKeepsSoupAndBoilingPiecesOnly() {
        FoodItem mashed = piece();
        mashed.addOrModifyTrack(new TagTrack("mashed", false, List.of(step("mashed"))));
        assertTrue(PotReference.keepsOnRebuild(mashed));

        FoodItem boiled = piece();
        boiled.addOrModifyTrack(new TagTrack("cooked", false, List.of(step("cooked"))));
        boiled.getCookData().getParameters().put(Method.POT, new CookParameter(1, 15, 30));
        assertTrue(PotReference.keepsOnRebuild(boiled));

        FoodItem panOnly = piece();
        panOnly.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, 15, 30));
        assertFalse(PotReference.keepsOnRebuild(panOnly));
        assertFalse(PotReference.keepsOnRebuild(piece()));
        assertFalse(PotReference.keepsOnRebuild(null));
    }

    private static FoodItem piece() {
        return new FoodItem("vegetable_cut", "Carrot", true);
    }

    private static TagStep step(String key) {
        return new TagStep(key, key, 0, 1.0, 1.0);
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
