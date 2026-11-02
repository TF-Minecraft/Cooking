package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

class FoodScanTest {
    Cooking plugin;
    List<FoodItem> foods;
    List<TagTrack> tracks;
    MockedStatic<StackNormalizer> clock;
    long now;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        plugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        tracks = new ArrayList<>(TrackLoader.oList);
        TrackLoader.oList.clear();
        TrackLoader.oList.add(new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Fresh", 0, 1, 1),
                new TagStep("aging", "Aging", 4, .9, .9),
                new TagStep("stale", "Stale", 9, .7, .7))));
        TrackLoader.oList.add(new TagTrack("warmth", true, List.of(
                new TagStep("hot", "Hot", 0, 1.2, 1.2),
                new TagStep("warm", "Warm", 3, 1.1, 1.1),
                new TagStep("cold", "Cold", 20, 1, 1))));
        clock = mockStatic(StackNormalizer.class, CALLS_REAL_METHODS);
        clock.when(StackNormalizer::quantizedNow).thenAnswer(inv -> now);
    }

    @AfterEach
    void cleanup() {
        clock.close();
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock();
        Cooking.plugin = plugin;
    }

    /**
     * The old scan rewrote unheld food on every pass. The new one leaves it until it would look different, and its
     * food must look exactly the same as the old one's after every scan, through steps, warmth expiry and odd gaps.
     * The multipliers are powers of two so both add up seconds exactly: with others, the old per-scan sums can round
     * a step that falls exactly on a scan to the next scan, while one sum over the whole time does not.
     */
    @Test
    void skippedRewritesLookExactlyLikeRewritingOnEveryScan() {
        for (long start : new long[] {10_000L, 10_300L}) {
            ItemStack old = warmBread(start);
            ItemStack scanned = old.clone();
            int[] gaps = {1, 3, 4, 2, 7, 1, 5};
            int writes = 0;
            int visibleChanges = 0;
            long recheckAt = Long.MIN_VALUE;
            now = 10_000L;
            for (int scan = 0; scan < 40; scan++) {
                now += gaps[scan % gaps.length] * 1000L;
                List<String> loreBefore = lore(old);
                ItemStack rewritten = ItemUpdater.updateItem(old, FoodItem.fromItem(old), null, false);
                if (rewritten != null) old = rewritten;
                boolean looksDifferent = !lore(old).equals(loreBefore);
                if (looksDifferent) visibleChanges++;
                // The new scan may skip the slot until recheckAt; it must never skip a visible change.
                if (looksDifferent) assertTrue(recheckAt <= now, "skipped a change at " + now);
                if (now < recheckAt) {
                    assertEquals(lore(old), lore(scanned), "at " + now);
                    continue;
                }
                ItemMeta meta = scanned.getItemMeta();
                FoodScan.Result result = FoodScan.update(scanned, meta, FoodItem.fromItem(scanned, meta), false);
                recheckAt = result.recheckAt();
                if (result.item() != null) {
                    scanned = result.item();
                    writes++;
                }
                assertEquals(lore(old), lore(scanned), "at " + now);
                assertEquals(steps(old), steps(scanned), "at " + now);
            }
            assertTrue(visibleChanges >= 3, "the food should go aging, warm, stale and lose its warmth");
            // One write per visible change, plus setting the clock to the second once when it started off it.
            assertEquals(visibleChanges + (start % 1000 == 0 ? 0 : 1), writes);
            assertEquals(ItemScanHandler.WHEN_CHANGED, recheckAt);
        }
    }

    @Test
    void heldFoodKeepsTheOldHeldRuleAndIsCheckedEveryScan() {
        ItemStack bread = warmBread(10_000L);
        now = 11_000L;
        FoodScan.Result quiet = scan(bread, true);
        assertNull(quiet.item());
        assertEquals(ItemScanHandler.EVERY_SCAN, quiet.recheckAt());
        now = 20_000L;
        FoodScan.Result changed = scan(bread, true);
        assertNotNull(changed.item());
        assertEquals(ItemScanHandler.EVERY_SCAN, changed.recheckAt());

        // A held food whose clock is missing waits like before; an unheld one gets its clock.
        ItemStack noClock = withoutClock(bread);
        assertNull(scan(noClock, true).item());
        ItemStack clocked = scan(noClock, false).item();
        assertEquals(20_000L, clocked.getItemMeta().getPersistentDataContainer().get(Keys.LAST_UPDATE, PersistentDataType.LONG));
    }

    @Test
    void theClockIsWrittenWhenMissingAheadOrOffTheSecond() {
        assertTrue(FoodScan.clockNeedsWriting(null, 5_000L));
        assertTrue(FoodScan.clockNeedsWriting(6_000L, 5_000L));
        assertTrue(FoodScan.clockNeedsWriting(4_300L, 5_000L));
        assertFalse(FoodScan.clockNeedsWriting(5_000L, 5_000L));
        assertFalse(FoodScan.clockNeedsWriting(2_000L, 5_000L));
        ItemStack ahead = warmBread(90_000L);
        now = 30_000L;
        assertNotNull(scan(ahead, false).item());
    }

    @Test
    void foodThatLooksDifferentAfterAReloadIsRebuiltWithoutANewStep() {
        ItemStack bread = warmBread(10_000L);
        now = 11_000L;
        assertNull(scan(bread, false).item());
        // A reload renames the fresh step: same step, new lore.
        TrackLoader.oList.set(0, new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Just baked", 0, 1, 1),
                new TagStep("aging", "Aging", 4, .9, .9),
                new TagStep("stale", "Stale", 9, .7, .7))));
        ItemStack rebuilt = scan(bread, false).item();
        assertNotNull(rebuilt);
        assertTrue(String.join("|", lore(rebuilt)).contains("Just baked"));
        assertNull(scan(rebuilt, false).item());
    }

    @Test
    void foodsThatNeverAgeAreOnlyRebuiltForBrokenLore() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("update", false);
        FoodItem salt = food("salt", config);
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.SUGAR), new FoodItem(salt), "Salt");
        FoodScan.Result fine = scan(stack, false);
        assertNull(fine.item());
        assertEquals(ItemScanHandler.WHEN_CHANGED, fine.recheckAt());
        ItemMeta meta = stack.getItemMeta();
        meta.setLore(List.of("Salt"));
        stack.setItemMeta(meta);
        assertNotNull(scan(stack, false).item());
    }

    @Test
    void sauceFoodsAreRewrittenOnEveryScanAsBefore() {
        FoodItem plate = food("plate", new MemoryConfiguration());
        FoodItem sauce = food("gravy", new MemoryConfiguration());
        FoodItem dish = new FoodItem(plate);
        FoodItem gravy = new FoodItem(sauce);
        gravy.addOrModifyTrack(TrackLoader.getByString("freshness"));
        dish.setSauce(gravy);
        dish.setLastUpdate(10_000L);
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.BOWL), dish, "Plate");
        now = 12_000L;
        FoodScan.Result result = scan(stack, false);
        assertNotNull(result.item());
        assertEquals(ItemScanHandler.EVERY_SCAN, result.recheckAt());
    }

    @Test
    void nextChangeFindsTheEarliestStepOrWarmthLoss() {
        FoodItem bread = new FoodItem(food("bread", new MemoryConfiguration()));
        assertEquals(ItemScanHandler.WHEN_CHANGED, FoodScan.nextChange(bread, 1_000L));
        bread.addOrModifyTrack(new TagTrack("cooked", false, List.of(new TagStep("raw", "Raw", 0, 1, 1))));
        bread.addOrModifyTrack(new TagTrack("plain", true, List.of()));
        assertEquals(ItemScanHandler.WHEN_CHANGED, FoodScan.nextChange(bread, 1_000L));
        bread.addOrModifyTrack(TrackLoader.getByString("freshness"));
        bread.getTagTrack("freshness").forceSetValue(9);
        assertEquals(ItemScanHandler.WHEN_CHANGED, FoodScan.nextChange(bread, 1_000L), "stale food stops aging");
        bread.getTagTrack("freshness").forceSetValue(1);
        bread.setAgeRemainder("freshness", .5);
        // 2.5 units to aging at 1 s each: due from 2 s on, a second early.
        assertEquals(3_000L, FoodScan.nextChange(bread, 1_000L));
        bread.setAgeRemainder("freshness", 7);
        assertEquals(1_000L, FoodScan.nextChange(bread, 1_000L), "already due");

        TagTrack warmth = new TagTrack("warmth", true, List.of(new TagStep("hot", "Hot", 0, 1, 1)));
        warmth.forceSetValue(15);
        assertEquals(20L, FoodScan.nextStepValue(warmth), "past every step, warmth still runs out at room temperature");
        assertEquals(20L, FoodScan.nextStepValue(new TagTrack("warmth", true, List.of())));
        assertEquals(3L, FoodScan.nextStepValue(TrackLoader.getByString("warmth")));
        assertEquals(Long.MAX_VALUE, FoodScan.nextStepValue(new TagTrack("plain", true, List.of())));

        MemoryConfiguration slow = new MemoryConfiguration();
        slow.set("age.freshness", 1e15);
        FoodItem honey = new FoodItem(food("honey", slow));
        honey.addOrModifyTrack(TrackLoader.getByString("freshness"));
        assertEquals(ItemScanHandler.WHEN_CHANGED, FoodScan.nextChange(honey, 1_000L));
    }

    @Test
    void itemsWithoutMetaNeverLookTheSame() {
        ItemStack bread = warmBread(10_000L);
        assertFalse(FoodScan.looksTheSame(bread, bread.getItemMeta(), new ItemStack(Material.AIR)));
    }

    private FoodScan.Result scan(ItemStack stack, boolean held) {
        ItemMeta meta = stack.getItemMeta();
        return FoodScan.update(stack, meta, FoodItem.fromItem(stack, meta), held);
    }

    /** Fresh, hot bread whose clock reads start. Freshness ages at 2 s a step, warmth at 4 s. */
    private ItemStack warmBread(long start) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("age.freshness", 2.0);
        config.set("age.warmth", 4.0);
        FoodItem bread = new FoodItem(food("bread", config));
        bread.addOrModifyTrack(TrackLoader.getByString("freshness"));
        bread.addOrModifyTrack(TrackLoader.getByString("warmth"));
        bread.setLastUpdate(start);
        // Built food always has a model; without one, the first rewrite would add it.
        bread.setModel(new FoodModel(new ItemStack(Material.BREAD)));
        return ItemBuilder.stamp(new ItemStack(Material.BREAD, 3), bread, "Bread");
    }

    private static ItemStack withoutClock(ItemStack stack) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().remove(Keys.LAST_UPDATE);
        copy.setItemMeta(meta);
        return copy;
    }

    @SuppressWarnings("deprecation")
    private static List<String> lore(ItemStack stack) {
        return stack.getItemMeta().getLore();
    }

    private static List<String> steps(ItemStack stack) {
        List<String> steps = new ArrayList<>();
        for (TagTrack track : FoodItem.fromItem(stack).getTagTracks()) {
            steps.add(track.getId() + "=" + track.getCurrentStep().getId());
        }
        return steps;
    }

    private FoodItem food(String id, MemoryConfiguration config) {
        config.set("name", id);
        FoodItem food = new FoodItem(id, config);
        FoodLoader.oList.removeIf(existing -> existing.getId().equals(id));
        FoodLoader.oList.add(food);
        return food;
    }
}
