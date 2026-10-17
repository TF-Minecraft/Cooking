package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryViewMock;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

class FoodInventoryClickTest {
    private Cooking previous;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private ServerMock server;
    private PlayerMock player;
    private Inventory chest;
    private long now;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        tracks = new ArrayList<>(TrackLoader.oList);
        FoodLoader.oList.clear();
        TrackLoader.oList.clear();
        TrackLoader.oList.add(new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Fresh", 0, 1, 1),
                new TagStep("stale", "Stale", 1_000_000, 1, 1))));
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "beef");
        FoodItem food = new FoodItem("beef", config);
        food.addOrModifyTrack(TrackLoader.getByString("freshness"));
        FoodLoader.oList.add(food);
        chest = server.createInventory(null, 27);
        player.openInventory(chest);
        server.getPluginManager().registerEvents(new ConversionManager(), MockBukkit.createMockPlugin());
        now = System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        Cooking.plugin = previous;
        MockBukkit.unmock();
    }

    private ItemStack beef(int amount, long secondsAgo) {
        FoodItem food = new FoodItem(FoodLoader.getByString("beef"));
        food.setLastUpdate(now - secondsAgo * 1000L);
        return ItemBuilder.stamp(new ItemStack(Material.BEEF, amount), food, "beef");
    }

    private InventoryClickEvent click(int slot, ClickType click, InventoryAction action) {
        // MockBukkit 4.95 converts event slots correctly but getItem/setItem skip that conversion.
        InventoryView open = player.getOpenInventory();
        Inventory top = open.getTopInventory();
        if (top == null) {
            // Closing a container also leaves its personal crafting view unimplemented.
            top = mock(Inventory.class);
            when(top.getSize()).thenReturn(5);
            when(top.getType()).thenReturn(InventoryType.CRAFTING);
            when(top.getHolder()).thenReturn(player);
        }
        InventoryView view = spy(new InventoryViewMock(player, top,
                player.getInventory(), top.getType()) {});
        doAnswer(call -> {
            int raw = call.getArgument(0);
            return view.getInventory(raw).getItem(view.convertSlot(raw));
        }).when(view).getItem(anyInt());
        doAnswer(call -> {
            int raw = call.getArgument(0);
            view.getInventory(raw).setItem(view.convertSlot(raw), call.getArgument(1));
            return null;
        }).when(view).setItem(anyInt(), any());
        InventoryClickEvent event = new InventoryClickEvent(view,
                InventoryType.SlotType.CONTAINER, slot, click, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void leftClickStacksInEitherDirectionAndKeepsOlderAge(boolean olderOnCursor) {
        ItemStack older = beef(3, 100);
        ItemStack newer = beef(2, 10);
        assertFalse(older.isSimilar(newer));
        chest.setItem(0, olderOnCursor ? newer : older);
        player.setItemOnCursor(olderOnCursor ? older : newer);
        assertTrue(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
        assertEquals(5, chest.getItem(0).getAmount());
        assertTrue(chest.getItem(0).isSimilar(older));
        assertTrue(player.getItemOnCursor().isEmpty());
    }

    @Test
    void rightClickAddsOnlyOneAndLeavesCursorAgeAlone() {
        ItemStack older = beef(3, 100);
        chest.setItem(0, beef(2, 10));
        player.setItemOnCursor(older);
        assertTrue(click(0, ClickType.RIGHT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
        assertEquals(3, chest.getItem(0).getAmount());
        assertEquals(2, player.getItemOnCursor().getAmount());
        assertTrue(chest.getItem(0).isSimilar(older));
        assertTrue(player.getItemOnCursor().isSimilar(older));
    }

    @Test
    void fullStackLeavesBothItemsUntouched() {
        ItemStack full = beef(64, 100);
        ItemStack cursor = beef(3, 10);
        chest.setItem(0, full);
        player.setItemOnCursor(cursor);
        assertTrue(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
        assertEquals(full, chest.getItem(0));
        assertEquals(cursor, player.getItemOnCursor());
    }

    @Test
    void partialMergeRespectsStackLimit() {
        chest.setItem(0, beef(63, 100));
        player.setItemOnCursor(beef(3, 10));
        click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        assertEquals(64, chest.getItem(0).getAmount());
        assertEquals(2, player.getItemOnCursor().getAmount());
    }

    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {"SHIFT_LEFT", "SHIFT_RIGHT"})
    void shiftClickFillsExistingFoodBeforeEmptySlots(ClickType click) {
        ItemStack older = beef(63, 100);
        chest.setItem(0, older);
        player.getInventory().setItem(9, beef(4, 10));
        assertTrue(click(27, click, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertEquals(64, chest.getItem(0).getAmount());
        assertTrue(chest.getItem(0).isSimilar(older));
        assertEquals(3, chest.getItem(1).getAmount());
        assertNull(player.getInventory().getItem(9));
    }

    @Test
    void shiftFromChestKeepsOlderIncomingAge() {
        ItemStack older = beef(4, 100);
        chest.setItem(0, older);
        player.getInventory().setItem(8, beef(2, 10));
        click(0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertNull(chest.getItem(0));
        assertEquals(6, player.getInventory().getItem(8).getAmount());
        assertTrue(player.getInventory().getItem(8).isSimilar(older));
    }

    @Test
    void shiftIntoFullInventoryLeavesExcessAtSource() {
        for (int i = 0; i < chest.getSize(); i++) chest.setItem(i, new ItemStack(Material.STONE, 64));
        chest.setItem(0, beef(63, 100));
        player.getInventory().setItem(9, beef(4, 10));
        click(27, ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertEquals(64, chest.getItem(0).getAmount());
        assertEquals(3, player.getInventory().getItem(9).getAmount());
    }

    @Test
    void differentQualityAndCustomDataRemainSeparate() {
        ItemStack item = beef(3, 100);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.QUALITY, PersistentDataType.INTEGER, 5);
        item.setItemMeta(meta);
        chest.setItem(0, item);
        player.setItemOnCursor(beef(2, 10));
        assertFalse(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
        item = beef(3, 100);
        meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.CARVE_REMAINING, PersistentDataType.INTEGER, 2);
        item.setItemMeta(meta);
        chest.setItem(0, item);
        assertFalse(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
    }

    @Test
    void cancelledClicksDoNotChangeItems() {
        ItemStack older = beef(3, 100);
        ItemStack newer = beef(2, 10);
        chest.setItem(0, older);
        player.setItemOnCursor(newer);
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(older, chest.getItem(0));
        assertEquals(newer, player.getItemOnCursor());
    }

    @Test
    void playerInventoryStacksWithoutOpeningAChest() {
        player.closeInventory();
        player.getInventory().setItem(9, beef(2, 10));
        player.setItemOnCursor(beef(3, 100));
        assertTrue(click(9, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
        assertEquals(5, player.getInventory().getItem(9).getAmount());
        assertTrue(player.getItemOnCursor().isEmpty());
    }

    @Test
    void shiftWithinPlayerInventoryKeepsArmorAndOffhandOutOfTheMove() {
        player.closeInventory();
        player.getInventory().setItem(9, beef(2, 10));
        player.getInventory().setItem(0, beef(3, 100));
        ItemStack offhand = beef(1, 200);
        player.getInventory().setItemInOffHand(offhand);
        assertTrue(click(9, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertEquals(5, player.getInventory().getItem(0).getAmount());
        assertNull(player.getInventory().getItem(9));
        assertEquals(offhand, player.getInventory().getItemInOffHand());
    }

    @Test
    void itemsThatHaveReachedDifferentFreshnessStagesStaySeparate() {
        chest.setItem(0, beef(2, 1_000_100));
        player.setItemOnCursor(beef(3, 10));
        assertFalse(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
    }

    @Test
    void inventoryStackLimitIsRespected() {
        chest.setMaxStackSize(16);
        chest.setItem(0, beef(15, 100));
        player.setItemOnCursor(beef(3, 10));
        click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        assertEquals(16, chest.getItem(0).getAmount());
        assertEquals(2, player.getItemOnCursor().getAmount());
    }

    @Test
    void pluginMenusAndMachineSlotsAreNotHandled() {
        Inventory menu = server.createInventory(mock(org.bukkit.inventory.InventoryHolder.class), 27);
        player.openInventory(menu);
        menu.setItem(0, beef(2, 100));
        player.setItemOnCursor(beef(3, 10));
        assertFalse(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
        Inventory furnace = server.createInventory(null, InventoryType.FURNACE);
        player.openInventory(furnace);
        furnace.setItem(0, beef(2, 100));
        player.setItemOnCursor(beef(3, 10));
        assertFalse(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
    }

    @Test
    void pendingCharacterDoesNotModifyInventory() {
        try (var characters = new net.tfminecraft.cooking.testsupport.TestCharacters(MockBukkit.createMockPlugin())) {
            chest.setItem(0, beef(2, 100));
            player.setItemOnCursor(beef(3, 10));
            assertFalse(click(0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled());
            assertEquals(2, chest.getItem(0).getAmount());
            assertEquals(3, player.getItemOnCursor().getAmount());
        }
    }

    @ParameterizedTest
    @EnumSource(value = InventoryType.SlotType.class, names = {"RESULT", "ARMOR", "CRAFTING", "FUEL", "OUTSIDE"})
    void restrictedSlotsAreLeftToTheirInventory(InventoryType.SlotType type) {
        ItemStack older = beef(2, 100);
        ItemStack newer = beef(3, 10);
        chest.setItem(0, older);
        player.setItemOnCursor(newer);
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(),
                type, 0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertEquals(older, chest.getItem(0));
        assertEquals(newer, player.getItemOnCursor());
    }
    @Test
    void doubleClickGathersFoodThatAgedApartAndKeepsTheOlderAge() {
        ItemStack older = beef(2, 100);
        chest.setItem(0, older);
        chest.setItem(1, new ItemStack(Material.STONE));
        chest.setItem(2, beef(2, 1_000_100));
        player.getInventory().setItem(9, beef(3, 10));
        player.getInventory().setItem(0, beef(1, 50));
        player.setItemOnCursor(beef(1, 5));

        assertTrue(click(3, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());

        assertEquals(7, player.getItemOnCursor().getAmount());
        assertTrue(player.getItemOnCursor().isSimilar(older));
        assertNull(chest.getItem(0));
        assertNull(player.getInventory().getItem(9));
        assertNull(player.getInventory().getItem(0));
        assertEquals(Material.STONE, chest.getItem(1).getType());
        assertEquals(2, chest.getItem(2).getAmount());
    }

    @Test
    void doubleClickTakesPartStacksBeforeFullOnesAndStopsAtAFullCursor() {
        chest.setItem(0, beef(64, 100));
        player.getInventory().setItem(9, beef(10, 10));
        player.setItemOnCursor(beef(1, 5));

        click(3, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);

        assertEquals(64, player.getItemOnCursor().getAmount());
        assertNull(player.getInventory().getItem(9));
        assertEquals(11, chest.getItem(0).getAmount());
    }

    @Test
    void doubleClickInThePlayerInventoryAlsoGathersArmourAndTheOffHand() {
        player.closeInventory();
        player.getInventory().setItem(9, beef(2, 10));
        ItemStack oldest = beef(1, 200);
        player.getInventory().setItemInOffHand(oldest);
        player.getInventory().setHelmet(beef(1, 150));
        player.setItemOnCursor(beef(3, 100));

        assertTrue(click(10, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());

        assertEquals(7, player.getItemOnCursor().getAmount());
        assertNull(player.getInventory().getHelmet());
        assertTrue(player.getItemOnCursor().isSimilar(oldest));
        assertNull(player.getInventory().getItem(9));
        assertTrue(player.getInventory().getItemInOffHand().isEmpty());
    }

    @Test
    void doubleClickInThePlayerInventoryGathersTheCraftingGridButNotTheResult() {
        player.closeInventory();
        Inventory grid = mock(Inventory.class);
        when(grid.getSize()).thenReturn(5);
        when(grid.getType()).thenReturn(InventoryType.CRAFTING);
        when(grid.getHolder()).thenReturn(player);
        ItemStack oldest = beef(2, 300);
        when(grid.getItem(2)).thenReturn(oldest);
        player.getInventory().setItem(9, beef(2, 10));
        player.setItemOnCursor(beef(1, 100));
        InventoryView view = new InventoryViewMock(player, grid, player.getInventory(), InventoryType.CRAFTING) {};
        // Double-clicked in the grid itself (raw slot 1).
        InventoryClickEvent event = new InventoryClickEvent(view,
                InventoryType.SlotType.CRAFTING, 1, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertEquals(5, player.getItemOnCursor().getAmount());
        assertTrue(player.getItemOnCursor().isSimilar(oldest));
        verify(grid).setItem(2, null);
        verify(grid, never()).getItem(0);
    }

    @Test
    void doubleClickOverExactCopiesIsLeftToVanilla() {
        ItemStack cursor = beef(1, 10);
        chest.setItem(0, cursor.clone());
        player.setItemOnCursor(cursor);

        assertFalse(click(3, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        assertEquals(1, chest.getItem(0).getAmount());
    }

    @Test
    void doubleClickWithoutFoodIsLeftToVanilla() {
        player.setItemOnCursor(new ItemStack(Material.STONE));
        assertFalse(click(3, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
    }

    @Test
    void addingOlderFoodToAFresherStackKeepsTheOlderAge() {
        player.getInventory().setItem(0, beef(2, 10));
        ItemStack older = beef(3, 100);

        assertNull(InventoryAdder.addItem(player, older));

        assertEquals(5, player.getInventory().getItem(0).getAmount());
        assertTrue(player.getInventory().getItem(0).isSimilar(older));
    }

    @Test
    void addingFresherFoodKeepsTheStacksOlderAge() {
        ItemStack older = beef(2, 100);
        player.getInventory().setItem(0, older);

        assertNull(InventoryAdder.addItem(player, beef(3, 10)));

        assertEquals(5, player.getInventory().getItem(0).getAmount());
        assertTrue(player.getInventory().getItem(0).isSimilar(older));
    }
}
