package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.baking.BakingTrayFill;
import net.tfminecraft.cooking.baking.BakingTrayTransform;
import net.tfminecraft.cooking.item.tag.TagQuality;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.quality.CompositionConfig;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.DisplayUtils;

class RisingDoughTest {
    private static final String LOAF_ID = "rising_dough_loaf";

    @AfterEach
    void removeLoaf() {
        FoodLoader.get().removeIf(item -> LOAF_ID.equals(item.getId()));
    }

    @Test
    void risingStepsRaiseQualityTowardInheritedStars() {
        TagTrack rising = risingTrack();
        assertEquals("unrisen", rising.getCurrentStep().getId());
        assertEquals(-3, rising.getCurrentStep().getStars());

        rising.forceSetValue(299);
        assertEquals("unrisen", rising.getCurrentStep().getId());

        rising.forceSetValue(300);
        assertEquals("rising", rising.getCurrentStep().getId());
        assertEquals(-1, rising.getCurrentStep().getStars());

        rising.forceSetValue(900);
        assertEquals("risen", rising.getCurrentStep().getId());
        assertEquals(0, rising.getCurrentStep().getStars());
        assertEquals(0, TagQuality.stars(List.of(rising)));
        assertTrue(DisplayUtils.getDisplayString("Unrisen", 1.0, 1.0, 0.0, -3).contains("-3★"));
        assertTrue(DisplayUtils.getDisplayString("Rising", 1.0, 1.0, 0.0, -1).contains("-1★"));
    }

    @Test
    void loafKeepsTheStarsFromTheCurrentRiseAndDropsTheTrack() {
        FoodLoader.get().add(new FoodItem(LOAF_ID, "Loaf", true));

        FoodItem dough = new FoodItem("dough", "Dough", true);
        dough.setQualityRange(5, 5);
        dough.addOrModifyTrack(risingTrack());
        assertEquals(2, dough.getEffectiveQuality());

        FoodItem loaf = BakingTrayTransform.doughToLoaf(
                dough, new BakingTrayFill("dough", "dough", 1, LOAF_ID, "", "grain"));
        assertEquals(2, loaf.getQualityMin());
        assertEquals(2, loaf.getEffectiveQuality());
        assertNull(loaf.getTagTrack("rising"));

        dough.getTagTrack("rising").forceSetValue(300);
        assertEquals(4, dough.getEffectiveQuality());

        dough.getTagTrack("rising").forceSetValue(900);
        assertEquals(5, dough.getEffectiveQuality());
        FoodItem risenLoaf = BakingTrayTransform.doughToLoaf(
                dough, new BakingTrayFill("dough", "dough", 1, LOAF_ID, "", "grain"));
        assertEquals(5, risenLoaf.getQualityMin());
        assertNull(risenLoaf.getTagTrack("rising"));
    }

    @Test
    void composedFoodInheritsTheRisePenaltyWithoutTheRisingTrack() {
        CompositionConfig.apply(false, Set.of(), null, Set.of(), Map.of(), false, 0, 0, false);
        try {
            FoodItem dough = new FoodItem("dough", "Dough", true);
            dough.setCategory("grain");
            dough.setQualityRange(5, 5);
            dough.addOrModifyTrack(risingTrack());

            CompositionResult result = CompositionQualityResolver.compose(null, List.of(dough), null);
            assertEquals(2, result.getFinalQuality());
            assertFalse(result.getFreshnessTracks().containsKey("rising"));
        } finally {
            CompositionConfig.apply(true, Set.of(), null, Set.of(), Map.of(), true, 0.12, 0.15, true);
        }
    }

    private static TagTrack risingTrack() {
        return new TagTrack("rising", true, List.of(
                new TagStep("unrisen", "Unrisen", 0, 1.0, 1.0, 0.0, 0.0, -3),
                new TagStep("rising", "Rising", 300, 1.0, 1.0, 0.0, 0.0, -1),
                new TagStep("risen", "Risen", 900, 1.0, 1.0, 0.0, 0.0, 0)));
    }
}
