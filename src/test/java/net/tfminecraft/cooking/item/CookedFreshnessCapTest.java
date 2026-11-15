package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ConfigLoader;
import net.tfminecraft.cooking.loader.TrackLoader;

/** config.yml cooked-freshness: cooked food ages at most as slowly as the cap, raw food keeps its type's pace. */
class CookedFreshnessCapTest {
    List<TagTrack> tracks;
    double previousCap;

    @BeforeEach void setup() {
        MockBukkit.mock();
        tracks = new ArrayList<>(TrackLoader.oList);
        previousCap = ItemCache.cookedFreshness;
        TrackLoader.oList.clear();
        TrackLoader.oList.add(FoodItemCoverageTest.track("cooked", false, 0, "raw", "cooked", "burnt"));
        MemoryConfiguration freshness = new MemoryConfiguration(); freshness.set("ageable", true);
        freshness.set("fresh.value", 0); freshness.set("stale.value", 86400); freshness.set("rotten.value", 129600);
        TrackLoader.oList.add(new TagTrack("freshness", 2, freshness));
        TrackLoader.oList.add(FoodItemCoverageTest.track("warmth", true, 1, "hot", "warm", "cold"));
        ItemCache.cookedFreshness = 3.0;
    }

    @AfterEach void cleanup() {
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        ItemCache.cookedFreshness = previousCap;
        MockBukkit.unmock();
    }

    static FoodItem meat(MemoryConfiguration extra) {
        MemoryConfiguration config = FoodItemCoverageTest.config();
        config.set("age.freshness", 14); config.set("age.warmth", 450);
        config.set("overrides.chicken.age.freshness", 10);
        if (extra != null) for (String key : extra.getKeys(true)) if (!extra.isConfigurationSection(key)) config.set(key, extra.get(key));
        FoodItem food = new FoodItem("meat", config);
        food.addTagTrack("freshness"); food.addTagTrack("cooked");
        return food;
    }

    @Test void rawFoodKeepsItsOwnShelfLife() {
        FoodItem food = meat(null);
        assertFalse(food.isCooked());
        assertEquals(14, food.resolveAgeMultiplier("freshness"));
        food.setOrigin("chicken"); assertEquals(10, food.resolveAgeMultiplier("freshness"));
    }

    @Test void cookedBoiledOrBurntFoodIsCappedOnFreshnessOnly() {
        FoodItem food = meat(null);
        for (int step : new int[] {10, 20, 30}) {
            food.getTagTrack("cooked").setValue(step);
            assertTrue(food.isCooked());
            assertEquals(3, food.resolveAgeMultiplier("FRESHNESS"));
            assertEquals(450, food.resolveAgeMultiplier("warmth"));
        }
        food.setOrigin("chicken"); assertEquals(3, food.resolveAgeMultiplier("freshness"));
    }

    @Test void shorterLivedFoodIsNeverStretchedByTheCap() {
        MemoryConfiguration extra = new MemoryConfiguration(); extra.set("age.freshness", 2);
        FoodItem food = meat(extra);
        food.getTagTrack("cooked").setValue(10);
        assertEquals(2, food.resolveAgeMultiplier("freshness"));
    }

    @Test void foodWithoutACookedTrackIsNotCapped() {
        MemoryConfiguration config = FoodItemCoverageTest.config(); config.set("age.freshness", 21);
        FoodItem veg = new FoodItem("veg", config); veg.addTagTrack("freshness");
        assertFalse(veg.isCooked());
        assertEquals(21, veg.resolveAgeMultiplier("freshness"));
    }

    @Test void typeCookedFreshnessReplacesTheConfigCapAndCopiesKeepIt() {
        MemoryConfiguration extra = new MemoryConfiguration(); extra.set("cooked-freshness", 7);
        FoodItem bread = meat(extra);
        bread.getTagTrack("cooked").setValue(10);
        assertEquals(7, bread.resolveAgeMultiplier("freshness"));
        assertEquals(7, new FoodItem(bread).resolveAgeMultiplier("freshness"));
        extra.set("cooked-freshness", 0);
        FoodItem uncapped = meat(extra); uncapped.getTagTrack("cooked").setValue(10);
        assertEquals(14, uncapped.resolveAgeMultiplier("freshness"));
    }

    @Test void zeroOrInvalidConfigCapTurnsItOff() {
        FoodItem food = meat(null); food.getTagTrack("cooked").setValue(10);
        for (double cap : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            ItemCache.cookedFreshness = cap;
            assertEquals(14, food.resolveAgeMultiplier("freshness"));
        }
    }

    @Test void cookingKeepsTheShareOfFreshnessUsedSoFoodNeverGetsFresher() {
        FoodItem food = meat(null);
        // half way through its raw fresh days: 7 of 14 days, stored as 43200 age units at multiplier 1
        food.getTagTrack("freshness").forceSetValue(43200);
        food.getTagTrack("cooked").setValue(10);
        food.setLastUpdate(System.currentTimeMillis() - 3L * 3600_000L); // 3 hours later
        food.updateAge();
        // 3 h at multiplier 3 = 10800 / 3 = 3600 units: the stored value only grows, now at the cooked pace
        assertEquals(43200 + 3600, food.getTagTrack("freshness").getValue());
    }

    @Test void configLoaderReadsTheCapAndDefaultsToThreeDays(@TempDir Path dir) throws Exception {
        File file = dir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), "cooked-freshness: 4.5\n");
        new ConfigLoader().loadConfig(file);
        assertEquals(4.5, ItemCache.cookedFreshness);
        Files.writeString(file.toPath(), "logging: true\n");
        new ConfigLoader().loadConfig(file);
        assertEquals(3.0, ItemCache.cookedFreshness);
    }
}
