package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;

class ItemLoreRebuildTest {

    @Test
    void emptyLoreNeedsRebuild() {
        assertTrue(ItemUpdater.needsLoreRebuild(null, true));
        assertTrue(ItemUpdater.needsLoreRebuild(List.of(), true));
    }

    @Test
    void missingIndexNeedsRebuild() {
        assertTrue(ItemUpdater.needsLoreRebuild(List.of("Dairy", "Nutrition 6", "Food 10"), false));
    }

    @Test
    void nameOnlyLoreNeedsRebuild() {
        assertTrue(ItemUpdater.needsLoreRebuild(List.of("Cup of Milk"), true));
    }

    @Test
    void completeLoreDoesNotNeedRebuild() {
        assertFalse(ItemUpdater.needsLoreRebuild(
                List.of("Dairy", "Origin: Milk", "stars", "Nutrition 6.0", "Food 10.0", " ", "Fresh"),
                true));
    }

    @Test
    void raisingFreshnessThresholdsKeepsFullLore() {
        TagTrack freshness = new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Fresh", 0, 1.0, 1.5),
                new TagStep("stale", "Stale", 86400, 1.0, 0.8),
                new TagStep("rotten", "Rotten", 259200, 0.6, 0.05)));
        freshness.forceSetValue(3200);
        assertEquals("fresh", freshness.getCurrentStep().getId());

        Map<String, Integer> indexMap = new HashMap<>();
        List<String> lore = ItemBuilder.assembleLore(
                "Dairy",
                "Origin: Milk",
                3,
                "Nutrition 6.0",
                "Food 10.0",
                List.of(),
                null,
                null,
                List.of(freshness),
                indexMap);

        assertTrue(lore.size() > 3);
        assertTrue(lore.stream().anyMatch(line -> line.contains("Nutrition")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Food") && !line.contains("% Food")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Fresh")));
        assertFalse(lore.stream().anyMatch(line -> line.contains("Rotten")));
        assertEquals(Integer.valueOf(3), indexMap.get("nutrition"));
        assertEquals(Integer.valueOf(4), indexMap.get("food"));
        assertFalse(ItemUpdater.needsLoreRebuild(lore, true));
    }

    // origin/main needsLoreRebuild, which compiled the colour-code regex for every lore line.
    private static boolean oldNeedsLoreRebuild(List<String> lore, boolean hasLoreIndex) {
        if (lore == null || lore.isEmpty() || !hasLoreIndex) {
            return true;
        }
        boolean nutrition = false;
        boolean food = false;
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String stripped = line.replaceAll("§.", "");
            if (stripped.contains("Nutrition")) {
                nutrition = true;
            }
            if (stripped.contains("Food") && !stripped.contains("% Food") && !stripped.contains("+")) {
                food = true;
            }
        }
        return !nutrition || !food;
    }

    @Test
    void colourCodesAreStrippedExactlyAsBefore() {
        String s = "§";
        List<String> lines = Arrays.asList(
                s + "aNutrition " + s + "f6.0", s + "7Food " + s + "f10.0", "Nu" + s + "ltrition 6",
                "F" + s + "x" + s + "a" + s + "b" + s + "c" + s + "d" + s + "e" + s + "food 3", s + s + "Food",
                "F" + s + s + "ood", "Food" + s, s, s + "\nFood", s + "\rNutrition", "Nutri" + s + " tion",
                "Fo" + s + "\u0085od", s + "😀Food", s + "\uD83DFood", "12% Food",
                s + "712" + s + "7% Food", "+2 Food", "Food " + s + "+", "%" + s + "a Food", "Fo" + s + "aod +",
                "", null, "plain");
        List<List<String>> lores = new ArrayList<>();
        for (String nutrition : lines) {
            for (String food : lines) {
                lores.add(Arrays.asList(nutrition, food));
            }
        }
        lores.add(Arrays.asList((String) null));
        lores.add(lines);
        int needed = 0;
        for (List<String> lore : lores) {
            for (boolean index : new boolean[] {true, false}) {
                boolean expected = oldNeedsLoreRebuild(lore, index);
                assertEquals(expected, ItemUpdater.needsLoreRebuild(lore, index), String.valueOf(lore));
                if (expected) needed++;
            }
        }
        // Both answers occur, so the comparison covers more than one outcome.
        assertTrue(needed > 0);
        assertTrue(needed < lores.size() * 2);
        assertFalse(ItemUpdater.needsLoreRebuild(List.of(s + "aNutrition", s + "7Food"), true));
        // A colour sign before a line break is not a colour code, so the break stays.
        assertTrue(ItemUpdater.needsLoreRebuild(List.of("Nutrition", "Fo" + s + "\nod"), true));
    }

    @Test
    void heldSilentDoesNotWrite() {
        assertFalse(ItemUpdater.shouldWriteToSlot(true, false, true));
    }

    @Test
    void heldVisualWrites() {
        assertTrue(ItemUpdater.shouldWriteToSlot(true, true, false));
        assertTrue(ItemUpdater.shouldWriteToSlot(true, true, true));
    }

    @Test
    void notHeldSilentWrites() {
        assertTrue(ItemUpdater.shouldWriteToSlot(false, false, true));
    }

    @Test
    void nothingToWriteSkips() {
        assertFalse(ItemUpdater.shouldWriteToSlot(false, false, false));
        assertFalse(ItemUpdater.shouldWriteToSlot(true, false, false));
    }
}
