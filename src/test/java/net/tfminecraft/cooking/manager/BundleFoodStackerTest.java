package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Equal foods put into a bundle stack there as they do in an inventory, keeping the older data. */
class BundleFoodStackerTest {
    private Cooking previous;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private ServerMock server;
    private PlayerMock player;
    private long now;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        tracks = new ArrayList<>(TrackLoader.oList);
        TrackLoader.oList.clear();
        TrackLoader.oList.add(new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Fresh", 0, 1, 1), new TagStep("stale", "Stale", 1_000_000, 1, 1))));
        TrackLoader.oList.add(new TagTrack("warmth", true, List.of(new TagStep("hot", "Hot", 0, 1, 1))));
        food("beef");
        food("pork");
        FoodLoader.getByString("pork").addOrModifyTrack(TrackLoader.getByString("warmth"));
        now = System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock();
        Cooking.plugin = previous;
    }

    private static void food(String id) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", id);
        FoodItem food = new FoodItem(id, config);
        food.addOrModifyTrack(TrackLoader.getByString("freshness"));
        FoodLoader.oList.add(food);
    }

    /** A food whose stored freshness is value as of secondsAgo seconds ago. */
    private ItemStack meat(String id, Material type, int amount, int value, long secondsAgo) {
        FoodItem food = new FoodItem(FoodLoader.getByString(id));
        food.getTagTrack("freshness").setValue(value);
        food.setLastUpdate(now - secondsAgo * 1000L);
        return ItemBuilder.stamp(new ItemStack(type, amount), food, id);
    }

    /** Hot pork with the given freshness and warmth, stored now. */
    private ItemStack hotPork(int freshness, int warmth) {
        FoodItem food = new FoodItem(FoodLoader.getByString("pork"));
        food.getTagTrack("freshness").setValue(freshness);
        food.getTagTrack("warmth").forceSetValue(warmth);
        food.setLastUpdate(now);
        return ItemBuilder.stamp(new ItemStack(Material.PORKCHOP), food, "pork");
    }

    private ItemStack beef(int amount, int value, long secondsAgo) {
        return meat("beef", Material.BEEF, amount, value, secondsAgo);
    }

    private static int freshness(ItemStack item) {
        return FoodItem.fromItem(item).getTagTrack("freshness").getValue();
    }

    private static ItemStack bundle(ItemStack... items) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(items));
        bundle.setItemMeta(meta);
        return bundle;
    }

    private static List<ItemStack> contents(ItemStack bundle) {
        return ((BundleMeta) bundle.getItemMeta()).getItems();
    }

    @Test
    void meatThatOnlyAgedDifferentlyStacksAndKeepsTheOlderData() {
        // Stored freshness 50 ten seconds ago is younger than 5 stored a hundred seconds ago.
        ItemStack younger = beef(2, 50, 10);
        ItemStack older = beef(3, 5, 100);

        List<ItemStack> merged = BundleFoodStacker.merge(List.of(younger, older));

        assertEquals(1, merged.size());
        assertEquals(5, merged.get(0).getAmount());
        assertEquals(5, freshness(merged.get(0)));
    }

    @Test
    void otherItemsAndOtherFoodsStayApartInTheirOrder() {
        ItemStack stone = new ItemStack(Material.STONE, 4);
        ItemStack pork = meat("pork", Material.PORKCHOP, 1, 0, 0);
        ItemStack stale = beef(1, 1_000_000, 0);

        assertNull(BundleFoodStacker.merge(List.of(stone, beef(1, 0, 0), pork, stale)));

        List<ItemStack> merged = BundleFoodStacker.merge(List.of(stone, beef(1, 0, 0), pork, beef(2, 0, 30)));
        assertEquals(List.of(Material.STONE, Material.BEEF, Material.PORKCHOP),
                merged.stream().map(ItemStack::getType).toList());
        assertEquals(3, merged.get(1).getAmount());
    }

    @Test
    void mergedStacksStopAtTheirMaximumAndTheRestStaysSeparate() {
        List<ItemStack> merged = BundleFoodStacker.merge(List.of(beef(60, 0, 0), beef(10, 0, 50)));

        assertEquals(2, merged.size());
        assertEquals(64, merged.get(0).getAmount());
        assertEquals(6, merged.get(1).getAmount());
        assertEquals(0, freshness(merged.get(1)));

        assertNull(BundleFoodStacker.merge(List.of(beef(64, 0, 0), beef(1, 0, 50))));
    }

    @Test
    void equallyAgedFoodKeepsTheFirstCopy() {
        ItemStack a = beef(1, 7, 0);
        ItemStack b = beef(1, 7, 0);

        assertSame(a, BundleFoodStacker.moreAged(a, b));
        assertSame(b, BundleFoodStacker.moreAged(beef(1, 1, 0), b));
    }

    @Test
    void freshnessDecidesWhichIsOlderAndWarmthOnlyBreaksATie() {
        ItemStack staleButHot = hotPork(15, 10);
        ItemStack freshButCool = hotPork(10, 19);
        assertSame(staleButHot, BundleFoodStacker.moreAged(freshButCool, staleButHot));
        assertSame(staleButHot, BundleFoodStacker.moreAged(staleButHot, freshButCool));

        ItemStack hot = hotPork(10, 5);
        ItemStack cooler = hotPork(10, 9);
        assertSame(cooler, BundleFoodStacker.moreAged(hot, cooler));
    }

    @Test
    void onlyBundlesWithSomethingToMergeAreRewritten() {
        assertNull(BundleFoodStacker.stack(null));
        assertNull(BundleFoodStacker.stack(beef(1, 0, 0)));
        assertNull(BundleFoodStacker.stack(bundle(beef(1, 0, 0))));

        ItemStack stacked = BundleFoodStacker.stack(bundle(beef(1, 0, 0), beef(1, 0, 20)));
        assertEquals(1, contents(stacked).size());
        assertEquals(2, contents(stacked).get(0).getAmount());
    }

    @Test
    void theCursorAndTheClickedSlotAreReadAgainAndRewritten() {
        Inventory chest = server.createInventory(null, 27);
        player.openInventory(chest);
        player.setItemOnCursor(bundle(beef(1, 0, 0), beef(1, 0, 20)));
        chest.setItem(4, bundle(beef(2, 0, 0), beef(2, 0, 20)));

        BundleFoodStacker.stackBundles(player, chest, 4);

        assertEquals(2, contents(player.getItemOnCursor()).get(0).getAmount());
        assertEquals(4, contents(chest.getItem(4)).get(0).getAmount());
    }

    @Test
    void anInventoryNoLongerOpenOrNoSlotAtAllIsLeftAlone() {
        Inventory chest = server.createInventory(null, 27);
        chest.setItem(4, bundle(beef(1, 0, 0), beef(1, 0, 20)));

        BundleFoodStacker.stackBundles(player, chest, 4);
        BundleFoodStacker.stackBundles(player, null, -999);

        assertEquals(2, contents(chest.getItem(4)).size());

        player.getInventory().setItem(2, new ItemStack(Material.STONE));
        BundleFoodStacker.stackBundles(player, player.getInventory(), 2);
        assertEquals(Material.STONE, player.getInventory().getItem(2).getType());
    }

    @Test
    void onlyClicksInvolvingABundleScheduleAMerge() {
        List<Runnable> tasks = new ArrayList<>();
        BundleFoodStacker stacker = new BundleFoodStacker(tasks::add);
        Inventory chest = server.createInventory(null, 27);
        var view = player.openInventory(chest);

        chest.setItem(0, beef(1, 0, 0));
        stacker.onClick(new InventoryClickEvent(view, SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(0, tasks.size());

        chest.setItem(0, bundle(beef(1, 0, 0), beef(1, 0, 20)));
        stacker.onClick(new InventoryClickEvent(view, SlotType.CONTAINER, 0, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        chest.setItem(0, null);
        player.setItemOnCursor(bundle(beef(1, 0, 0), beef(1, 0, 20)));
        stacker.onClick(new InventoryClickEvent(view, SlotType.CONTAINER, 0, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        assertEquals(2, tasks.size());

        chest.setItem(0, bundle(beef(1, 0, 0), beef(1, 0, 20)));
        tasks.forEach(Runnable::run);
        assertEquals(1, contents(chest.getItem(0)).size());
        assertEquals(1, contents(player.getItemOnCursor()).size());

        new BundleFoodStacker();
    }
}
