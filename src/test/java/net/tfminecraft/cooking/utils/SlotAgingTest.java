package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class SlotAgingTest {
    static final String HOOK = "meat_hook";

    Cooking plugin;
    List<FoodItem> foods;
    List<TagTrack> tracks;
    MockedStatic<StackNormalizer> clock;
    PlacedSlot slot;
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
        slot = mock(PlacedSlot.class);
        SlotAging.forgetAll();
    }

    @AfterEach
    void cleanup() {
        SlotAging.forgetAll();
        clock.close();
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock();
        Cooking.plugin = plugin;
    }

    /**
     * The hook, tray and trough timers used to rewrite their food every second (ItemUpdater.updateItem). Skipping
     * resting slots must leave food that looks exactly the same after every second, through steps and warmth loss,
     * with one write per visible change (plus setting a clock that started off the second once).
     */
    @Test
    void restingSlotsLookExactlyLikeRewritingEverySecond() {
        for (long start : new long[] {10_000L, 10_300L}) {
            SlotAging.forgetAll();
            ItemStack old = warmRoast(start);
            ItemStack aged = old.clone();
            int writes = 0;
            int visibleChanges = 0;
            now = 10_000L;
            for (int second = 0; second < 100; second++) {
                now += 1000L;
                List<String> loreBefore = lore(old);
                ItemStack rewritten = ItemUpdater.updateItem(old, FoodItem.fromItem(old), HOOK);
                if (rewritten != null) old = rewritten;
                if (!lore(old).equals(loreBefore)) visibleChanges++;
                if (!SlotAging.isResting(slot, aged)) {
                    ItemStack written = SlotAging.age(slot, aged, FoodItem.fromItem(aged), HOOK);
                    if (written != null) {
                        aged = written;
                        writes++;
                    }
                }
                assertEquals(lore(old), lore(aged), "at " + now);
                assertEquals(steps(old), steps(aged), "at " + now);
            }
            assertTrue(visibleChanges >= 3, "the roast should go aging, warm, stale and lose its warmth");
            assertEquals(visibleChanges + (start % 1000 == 0 ? 0 : 1), writes);
            assertTrue(SlotAging.isResting(slot, aged), "stale, cold food never changes again");
        }
    }

    @Test
    void aRestEndsWhenTheItemChangesOrItsTimeComes() {
        now = 10_000L;
        ItemStack roast = warmRoast(now);
        assertFalse(SlotAging.isResting(slot, roast));
        now = 11_000L;
        assertNull(SlotAging.age(slot, roast, FoodItem.fromItem(roast), HOOK));
        assertTrue(SlotAging.isResting(slot, roast));
        assertTrue(SlotAging.isResting(slot, roast.clone()), "an equal item rests too");
        assertFalse(SlotAging.isResting(mock(PlacedSlot.class), roast), "rests are per slot");

        ItemStack two = roast.clone();
        two.setAmount(2);
        assertFalse(SlotAging.isResting(slot, two), "another item ends the rest");
        assertFalse(SlotAging.isResting(slot, roast), "and it stays ended");

        assertNull(SlotAging.age(slot, roast, FoodItem.fromItem(roast), HOOK));
        // Started at 10 s, it goes aging at 18 s (warm at 22 s): due from 17 s, a second early.
        now = 16_000L;
        assertTrue(SlotAging.isResting(slot, roast));
        now = 17_000L;
        assertFalse(SlotAging.isResting(slot, roast));
    }

    @Test
    void writtenFoodIsLookedAtAgainOnTheNextPass() {
        now = 11_000L;
        ItemStack offTheSecond = warmRoast(10_300L);
        ItemStack written = SlotAging.age(slot, offTheSecond, FoodItem.fromItem(offTheSecond), HOOK);
        assertNotNull(written);
        assertFalse(SlotAging.isResting(slot, written));
        assertNull(SlotAging.age(slot, written, FoodItem.fromItem(written), HOOK));
        assertTrue(SlotAging.isResting(slot, written));
    }

    @Test
    void foodIsRebuiltWithTheFurnituresModel() {
        now = 11_000L;
        ItemStack offTheSecond = warmRoast(10_300L);
        ItemStack resting = warmRoast(11_000L);
        try (MockedStatic<ItemUpdater> updater = mockStatic(ItemUpdater.class, CALLS_REAL_METHODS)) {
            SlotAging.age(slot, offTheSecond, FoodItem.fromItem(offTheSecond), HOOK);
            SlotAging.age(slot, resting, FoodItem.fromItem(resting), HOOK);
            updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), eq(HOOK)), times(2));
            updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()), never());
        }
    }

    @Test
    void sauceFoodIsRewrittenEveryPassAndNeverRests() {
        FoodItem dish = new FoodItem(food("plate", new MemoryConfiguration()));
        FoodItem gravy = new FoodItem(food("gravy", new MemoryConfiguration()));
        gravy.addOrModifyTrack(TrackLoader.getByString("freshness"));
        dish.setSauce(gravy);
        dish.setLastUpdate(10_000L);
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.BOWL), dish, "Plate");
        now = 12_000L;
        ItemStack written;
        try (MockedStatic<ItemUpdater> updater = mockStatic(ItemUpdater.class, CALLS_REAL_METHODS)) {
            written = SlotAging.age(slot, stack, FoodItem.fromItem(stack), HOOK);
            updater.verify(() -> ItemUpdater.updateItem(any(), any(), eq(HOOK), eq(false)));
        }
        assertNotNull(written);
        assertFalse(SlotAging.isResting(slot, written));
        assertNull(SlotAging.age(slot, written, FoodItem.fromItem(written), HOOK), "nothing new this second");
        assertFalse(SlotAging.isResting(slot, written), "but it is looked at again next second");
    }

    @Test
    void foodThatNeverAgesRestsUntilItChangesAndReloadsEndEveryRest() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("update", false);
        FoodItem salt = food("salt", config);
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.SUGAR), new FoodItem(salt), "Salt");
        now = 5_000L;
        assertNull(SlotAging.age(slot, stack, FoodItem.fromItem(stack), HOOK));
        now = Long.MAX_VALUE - 1;
        assertTrue(SlotAging.isResting(slot, stack));
        ItemRef.clearCache();
        assertFalse(SlotAging.isResting(slot, stack), "a reload or /iareload ends every rest");
    }

    /** Fresh, hot roast whose clock reads start. Freshness ages at 2 s a step, warmth at 4 s. */
    private ItemStack warmRoast(long start) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("age.freshness", 2.0);
        config.set("age.warmth", 4.0);
        FoodItem roast = new FoodItem(food("roast", config));
        roast.addOrModifyTrack(TrackLoader.getByString("freshness"));
        roast.addOrModifyTrack(TrackLoader.getByString("warmth"));
        roast.setLastUpdate(start);
        // Built food always has a model; without one, the first rewrite would add it.
        roast.setModel(new FoodModel(new ItemStack(Material.COOKED_BEEF)));
        return ItemBuilder.stamp(new ItemStack(Material.COOKED_BEEF), roast, "Roast");
    }

    @SuppressWarnings("deprecation")
    private static List<String> lore(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta.getLore();
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
