package net.tfminecraft.cooking.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.utils.DisplayUtils;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

class SauceColourTest {
    private static final String WATER = "ia.test:water";
    private static final String WATER_PLATED = "ia.test:water_plated";
    private static final String MILK_PLATED = "ia.test:milk_plated";

    private String previousFallback;
    private String previousPlatedFallback;
    private HashMap<String, String> previousDict;

    @BeforeEach
    void setUp() {
        previousFallback = ItemCache.liquidFallback;
        previousPlatedFallback = ItemCache.platedLiquidFallback;
        previousDict = CategoryDictionary.sauceDict;
        ItemCache.liquidFallback = WATER;
        ItemCache.platedLiquidFallback = WATER_PLATED;
        CategoryDictionary.sauceDict = new HashMap<>();
        CategoryDictionary.sauceDict.put("ffffff", "ia.test:milk|" + MILK_PLATED);
    }

    @AfterEach
    void tearDown() {
        ItemCache.liquidFallback = previousFallback;
        ItemCache.platedLiquidFallback = previousPlatedFallback;
        CategoryDictionary.sauceDict = previousDict;
    }

    @Test
    void mergedColourAlwaysHasAHash() {
        assertEquals("#000000", DisplayUtils.getMergedColour(List.of()));
        assertEquals("#000000", DisplayUtils.getMergedColour(List.of("000000")));
        assertEquals("#ffffff", DisplayUtils.getMergedColour(List.of("ffffff")));
        assertEquals("#abcdef", DisplayUtils.getMergedColour(List.of("#abcdef")));
        assertEquals("#185d15", DisplayUtils.getMergedColour(List.of("000000", "30ba2b")));
    }

    @Test
    void uncolouredMixesAreNamedWhite() {
        assertEquals("&f", DisplayUtils.getNameColour("#000000"));
        assertEquals("&f", DisplayUtils.getNameColour(null));
        assertEquals("#185d15", DisplayUtils.getNameColour("#185d15"));
    }

    @Test
    void waterOnlySauceNameShowsThePlatedWaterVisual() {
        String name = StringFormatter.formatHex(
                DisplayUtils.getNameColour(DisplayUtils.getMergedColour(List.of("000000"))) + "Mixed Sauce");

        assertEquals("§fMixed Sauce", name);
        assertNull(StringFormatter.extractHexColor(name));
        assertEquals(WATER_PLATED, CategoryDictionary.getSauceItemPath(StringFormatter.extractHexColor(name), CategoryDictionary.PLATED));
    }

    @Test
    void olderUncolouredSauceNameShowsThePlatedWaterVisual() {
        assertEquals(WATER_PLATED, CategoryDictionary.getSauceItemPath(StringFormatter.extractHexColor("000000Mixed Sauce"), CategoryDictionary.PLATED));
    }

    @Test
    void milkOnlySauceColourShowsTheMilkVisual() {
        // Scooping stores this merged colour on the ladle; the name alone can't carry it (Paper keeps #ffffff as named white).
        assertEquals(MILK_PLATED, CategoryDictionary.getSauceItemPath(DisplayUtils.getMergedColour(List.of("ffffff")), CategoryDictionary.PLATED));
    }
}
