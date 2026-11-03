package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

/** Hoppers stack equal foods that only aged apart, keeping the more aged copy's data. */
class HopperFoodStackerTest {
    private Cooking previous;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private ServerMock server;
    private Inventory hopper;
    private Inventory chest;
    private long now;
    private final List<Runnable> ticks = new ArrayList<>();
    private final HopperFoodStacker stacker = new HopperFoodStacker(ticks::add);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
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
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "egg");
        FoodItem egg = new FoodItem("egg", config);
        egg.addOrModifyTrack(TrackLoader.getByString("freshness"));
        FoodLoader.oList.add(egg);
        hopper = server.createInventory(null, InventoryType.HOPPER);
        chest = server.createInventory(null, 27);
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

    /** An egg whose stored freshness is value as of secondsAgo seconds ago, at the given stars. */
    private ItemStack egg(int amount, int value, long secondsAgo, int quality) {
        FoodItem food = new FoodItem(FoodLoader.getByString("egg"));
        food.getTagTrack("freshness").setValue(value);
        food.setLastUpdate(now - secondsAgo * 1000L);
        food.setQualityRange(quality, quality);
        return ItemBuilder.stamp(new ItemStack(Material.EGG, amount), food, "egg");
    }

    private ItemStack egg(int amount, long secondsAgo) {
        return egg(amount, 0, secondsAgo, 1);
    }

    private static long clock(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(Keys.LAST_UPDATE, PersistentDataType.LONG);
    }

    /** A hopper pushing moved into chest, after Cooking's listener ran. */
    private InventoryMoveItemEvent push(ItemStack moved) {
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(hopper, moved, chest, true);
        stacker.onMove(event);
        return event;
    }

    /** What vanilla does with an item that matches slot 0 exactly. */
    private static void addTo(Inventory inventory, int slot, ItemStack item) {
        ItemStack current = inventory.getItem(slot);
        assertTrue(current.isSimilar(item));
        current.setAmount(current.getAmount() + item.getAmount());
        inventory.setItem(slot, current);
    }

    private void tick() {
        List<Runnable> due = new ArrayList<>(ticks);
        ticks.clear();
        due.forEach(Runnable::run);
    }

    @Test
    void aFresherEggTakesTheStoredStacksDataSoItStacks() {
        chest.setItem(0, egg(3, 600));

        InventoryMoveItemEvent event = push(egg(1, 0));

        assertTrue(event.getItem().isSimilar(chest.getItem(0)));
        assertEquals(1, event.getItem().getAmount());
        assertEquals(now - 600_000L, clock(chest.getItem(0)));
        assertTrue(ticks.isEmpty());
    }

    @Test
    void anOlderEggStacksAndTheStackTakesItsDataOnTheNextTick() {
        ItemStack stored = egg(3, 0);
        chest.setItem(0, stored);
        ItemStack moved = egg(1, 600);

        InventoryMoveItemEvent event = push(moved);
        // Nothing in the chest changes while the move can still be cancelled.
        assertTrue(chest.getItem(0).isSimilar(stored));
        assertTrue(event.getItem().isSimilar(stored));
        addTo(chest, 0, event.getItem());
        tick();

        assertTrue(chest.getItem(0).isSimilar(moved));
        assertEquals(4, chest.getItem(0).getAmount());
    }

    @Test
    void aCancelledMoveOfAnOlderEggLeavesTheStackAlone() {
        ItemStack stored = egg(3, 0);
        chest.setItem(0, stored);

        push(egg(1, 600));
        tick();

        assertTrue(chest.getItem(0).isSimilar(stored));
        assertEquals(3, chest.getItem(0).getAmount());
    }

    @Test
    void equallyAgedEggsKeepTheStoredStacksData() {
        // Ten points stored now and none stored ten seconds ago are the same age.
        chest.setItem(0, egg(2, 0, 10, 1));
        ItemStack stored = chest.getItem(0).clone();

        InventoryMoveItemEvent event = push(egg(1, 10, 0, 1));

        assertTrue(event.getItem().isSimilar(stored));
        assertTrue(ticks.isEmpty());
    }

    @Test
    void anEarlierEmptySlotIsLeftToVanilla() {
        chest.setItem(1, egg(3, 600));
        ItemStack moved = egg(1, 0);

        assertTrue(push(moved).getItem().isSimilar(moved));
    }

    @Test
    void anExactCopyWithRoomIsLeftToVanilla() {
        ItemStack moved = egg(1, 0);
        ItemStack copy = moved.clone();
        copy.setAmount(4);
        chest.setItem(0, copy);
        chest.setItem(1, egg(3, 600));

        assertTrue(push(moved).getItem().isSimilar(moved));
    }

    @Test
    void fullStacksOtherItemsAndOtherStarsAreSkipped() {
        chest.setItem(0, egg(16, 600));
        chest.setItem(1, new ItemStack(Material.EGG, 2));
        chest.setItem(2, new ItemStack(Material.STONE));
        chest.setItem(3, egg(2, 0, 600, 3));
        chest.setItem(4, egg(2, 300));

        InventoryMoveItemEvent event = push(egg(1, 0));

        assertTrue(event.getItem().isSimilar(chest.getItem(4)));
        assertEquals(16, chest.getItem(0).getAmount());
    }

    @Test
    void foodThatDiffersBeyondAgingIsSkipped() {
        ItemStack named = egg(2, 600);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Golden egg");
        named.setItemMeta(meta);
        chest.setItem(0, named);
        ItemStack moved = egg(1, 0);

        assertTrue(push(moved).getItem().isSimilar(moved));
    }

    @Test
    void aFullChestChangesNothing() {
        for (int slot = 0; slot < chest.getSize(); slot++) chest.setItem(slot, egg(16, 600));
        ItemStack moved = egg(1, 0);

        assertTrue(push(moved).getItem().isSimilar(moved));
    }

    @Test
    void itemsThatAreNotFoodAreLeftAlone() {
        chest.setItem(0, new ItemStack(Material.EGG, 2));
        ItemStack named = new ItemStack(Material.EGG);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Painted egg");
        named.setItemMeta(meta);

        assertTrue(push(named).getItem().isSimilar(named));
        assertTrue(push(new ItemStack(Material.EGG)).getItem().isSimilar(new ItemStack(Material.EGG)));
    }

    @Test
    void aHopperPullingIntoItselfStacksToo() {
        Inventory above = server.createInventory(null, 27);
        hopper.setItem(0, egg(2, 600));
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(above, egg(1, 0), hopper, false);

        stacker.onMove(event);

        assertTrue(event.getItem().isSimilar(hopper.getItem(0)));
    }

    @Test
    void droppersAreLeftAlone() {
        Inventory dropper = server.createInventory(null, InventoryType.DROPPER);
        chest.setItem(0, egg(3, 600));
        ItemStack moved = egg(1, 0);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(dropper, moved, chest, true);

        stacker.onMove(event);

        assertTrue(event.getItem().isSimilar(moved));
    }

    @Test
    void containersWithSlotRulesAreLeftAlone() {
        Inventory furnace = server.createInventory(null, InventoryType.FURNACE);
        furnace.setItem(0, egg(3, 600));
        ItemStack moved = egg(1, 0);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(hopper, moved, furnace, true);

        stacker.onMove(event);

        assertTrue(event.getItem().isSimilar(moved));
    }

    @Test
    void aHopperPickingUpAFresherEggGivesTheGroundItemTheStoredData() {
        WorldMock world = server.addSimpleWorld("world");
        hopper.setItem(0, egg(2, 600));
        Item ground = world.dropItem(world.getSpawnLocation(), egg(3, 0));

        stacker.onPickup(new InventoryPickupItemEvent(hopper, ground));
        addTo(hopper, 0, ground.getItemStack());
        ground.remove();
        tick();

        assertEquals(now - 600_000L, clock(hopper.getItem(0)));
        assertEquals(5, hopper.getItem(0).getAmount());
    }

    @Test
    void aHopperPickingUpAnOlderEggAgesTheStackOnceItIsIn() {
        WorldMock world = server.addSimpleWorld("world");
        ItemStack stored = egg(2, 0);
        hopper.setItem(0, stored);
        ItemStack old = egg(1, 600);
        Item ground = world.dropItem(world.getSpawnLocation(), old);

        stacker.onPickup(new InventoryPickupItemEvent(hopper, ground));
        assertTrue(hopper.getItem(0).isSimilar(stored));
        assertTrue(ground.getItemStack().isSimilar(stored));
        addTo(hopper, 0, ground.getItemStack());
        ground.remove();
        tick();

        assertTrue(hopper.getItem(0).isSimilar(old));
        assertEquals(3, hopper.getItem(0).getAmount());
    }

    @Test
    void aCancelledPickupGetsItsOwnDataBack() {
        WorldMock world = server.addSimpleWorld("world");
        ItemStack stored = egg(2, 600);
        hopper.setItem(0, stored);
        ItemStack fresh = egg(3, 0);
        Item ground = world.dropItem(world.getSpawnLocation(), fresh);

        stacker.onPickup(new InventoryPickupItemEvent(hopper, ground));
        assertTrue(ground.getItemStack().isSimilar(stored));
        tick();

        assertTrue(ground.getItemStack().isSimilar(fresh));
        assertEquals(3, ground.getItemStack().getAmount());
        assertTrue(hopper.getItem(0).isSimilar(stored));
        assertEquals(2, hopper.getItem(0).getAmount());
    }

    @Test
    void pickupsIntoOtherInventoriesAreLeftAlone() {
        WorldMock world = server.addSimpleWorld("world");
        Inventory furnace = server.createInventory(null, InventoryType.FURNACE);
        furnace.setItem(0, egg(2, 600));
        ItemStack fresh = egg(1, 0);
        Item ground = world.dropItem(world.getSpawnLocation(), fresh);

        stacker.onPickup(new InventoryPickupItemEvent(furnace, ground));

        assertTrue(ground.getItemStack().isSimilar(fresh));
        assertTrue(ticks.isEmpty());
    }

    @Test
    void foodThatWouldNotAllFitIsLeftToVanilla() {
        hopper.setItem(0, egg(14, 0));
        assertNull(HopperFoodStacker.merge(hopper, egg(3, 600)));
        assertNotNull(HopperFoodStacker.merge(hopper, egg(2, 600)));
    }

    @Test
    void theStackIsNotAgedWhenItDidNotGrow() {
        ItemStack stored = egg(3, 0);
        chest.setItem(0, stored);
        HopperFoodStacker.Merge merge = HopperFoodStacker.merge(chest, egg(1, 600));
        assertTrue(merge.incomingIsOlder());

        merge.ageStack();
        assertTrue(chest.getItem(0).isSimilar(stored));
        chest.setItem(0, null);
        merge.ageStack();
        assertNull(chest.getItem(0));
    }

    @Test
    void onlyFoodThatNeedsNewDataGetsAMerge() {
        assertNull(HopperFoodStacker.merge(chest, null));
        assertNull(HopperFoodStacker.merge(chest, new ItemStack(Material.AIR)));
        ItemStack moved = egg(1, 0);
        assertNull(HopperFoodStacker.merge(chest, moved));
        chest.setItem(0, egg(2, 600));
        HopperFoodStacker.Merge merge = HopperFoodStacker.merge(chest, moved);
        assertNotNull(merge);
        assertFalse(merge.incomingIsOlder());
        assertEquals(0, merge.slot());
    }
}
