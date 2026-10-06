package net.tfminecraft.cooking.trough;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.manager.MealManagersCoverageTest.Kitchen;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemUpdater;

class TroughCoverageTest {
    Kitchen kitchen;
    Workshop env;
    Station trough;
    TroughHandler handler;
    MockedStatic<IngredientConverter> converter;
    String previousTrough, previousFeed;
    int previousCount;

    @BeforeEach void open() {
        kitchen = new Kitchen(); env = kitchen.env; trough = kitchen.station("trough"); handler = new TroughHandler();
        previousTrough = FurnitureCache.trough; previousFeed = ItemCache.troughFeed; previousCount = ItemCache.troughItemsPerClick;
        FurnitureCache.trough = "trough"; ItemCache.troughFeed = "feed"; ItemCache.troughItemsPerClick = 3;
        for (String id : TroughSlots.ALL) trough.define(id);
        when(env.api.getCreator().getItemFromPath("feed")).thenAnswer(inv -> new ItemStack(Material.WHEAT));
        converter = env.scoped(mockStatic(IngredientConverter.class));
        converter.when(() -> IngredientConverter.convertIfNeeded(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    }
    @AfterEach void close() {
        kitchen.close(); FurnitureCache.trough = previousTrough; ItemCache.troughFeed = previousFeed; ItemCache.troughItemsPerClick = previousCount;
    }
    FoodItem carrot() { return kitchen.food("carrot", "vegetable_root"); }
    FoodItem rotten() {
        FoodItem food = carrot(); food.addOrModifyTrack(TrackLoader.getByString("freshness"));
        food.getTagTrack("freshness").forceSetValue(Math.toIntExact(food.getTagTrack("freshness").getSteps().getLast().getRequiredValue())); return food;
    }
    void fill() { for (String id : TroughSlots.ALL) trough.place(id, env.stack(carrot(), Material.CARROT, 3)); }

    @Test void ingredientsAcceptWheatAndFreshVegetablesAndResolveConvertedHands() {
        assertNull(TroughIngredients.resolveHand(env.player, null)); assertTrue(TroughIngredients.isRotten(null)); assertFalse(TroughIngredients.isAllowed(null));
        FoodItem fresh = carrot(); assertTrue(TroughIngredients.isAllowed(fresh)); assertFalse(TroughIngredients.isRotten(fresh));
        fresh.addOrModifyTrack(TrackLoader.getByString("freshness")); assertFalse(TroughIngredients.isRotten(fresh));
        assertTrue(TroughIngredients.isRotten(rotten())); assertFalse(TroughIngredients.isAllowed(rotten()));
        assertTrue(TroughIngredients.isAllowed(kitchen.food("WHEAT", "grain"))); assertFalse(TroughIngredients.isAllowed(kitchen.food("steak", "meat")));
        for (String category : new String[] {null, "", "grain"}) assertFalse(TroughIngredients.isAllowedCategory(category));
        assertFalse(TroughIngredients.isWheatFood(null)); assertTrue(TroughIngredients.isAllowedCategory("VEGETABLE_ROOT"));
        ItemStack original = env.hold(new ItemStack(Material.CARROT, 4)), converted = env.stack(fresh, Material.PAPER, 4);
        converter.when(() -> IngredientConverter.convertIfNeeded(env.player, original)).thenReturn(converted);
        assertSame(fresh, TroughIngredients.resolveHand(env.player, original)); assertEquals(converted, env.player.getInventory().getItemInMainHand());
    }

    @Test void interactionRoutesRespectFurnitureAndSneakingThenFillOneSlotPerClick() {
        var ignored = kitchen.station("chair").interact(); handler.onInteract(ignored); assertFalse(ignored.isCancelled());
        when(env.player.isSneaking()).thenReturn(true); var sneaking = trough.interact(); handler.onInteract(sneaking); assertFalse(sneaking.isCancelled());
        when(env.player.isSneaking()).thenReturn(false); var empty = trough.interact(); handler.onInteract(empty); assertTrue(empty.isCancelled()); assertTrue(trough.active.isEmpty());
        ItemStack held = env.hold(env.stack(carrot(), Material.CARROT, 8)); handler.onInteract(trough.interact());
        assertEquals(5, held.getAmount()); assertEquals(3, trough.active.get("a").getCurrentItem().getAmount()); assertEquals("b", TroughHandler.findFirstEmpty(trough.furniture));
        handler.onInteract(trough.interact()); assertEquals(2, held.getAmount()); assertEquals(3, trough.active.get("b").getCurrentItem().getAmount());
        handler.onInteract(trough.interact()); assertEquals(2, held.getAmount()); assertTrue(env.messages.getLast().contains("3 at a time")); verify(env.manager, times(2)).markDirty(trough.furniture);
    }

    @Test void invalidRottenAndFullInputsKeepTheHeldFood() {
        for (ItemStack input : List.of(new ItemStack(Material.STONE), env.stack(rotten(), Material.CARROT, 3), env.stack(kitchen.food("steak", "meat"), Material.BEEF, 3))) {
            ItemStack held = env.hold(input); handler.onInteract(trough.interact()); assertEquals(held, env.player.getInventory().getItemInMainHand()); assertTrue(trough.active.isEmpty());
        }
        assertTrue(env.messages.stream().anyMatch(message -> message.contains("Rotten")));
        fill(); ItemStack held = env.hold(env.stack(carrot(), Material.CARROT, 3)); handler.onInteract(trough.interact());
        assertEquals(3, held.getAmount()); assertTrue(env.messages.getLast().contains("full")); assertNull(TroughHandler.findFirstEmpty(trough.furniture)); assertTrue(TroughHandler.isFull(trough.furniture));
        trough.active.put("a", null); assertFalse(TroughHandler.isOccupied(trough.furniture, "a"));
        trough.place("a", new ItemStack(Material.AIR)); assertFalse(TroughHandler.isOccupied(trough.furniture, "a"));
    }

    @Test void completedFeedConsumesAllFourInputsAndDropsInventoryOverflow() {
        fill(); handler.onInteract(trough.interact()); assertTrue(trough.active.isEmpty()); assertEquals(Material.WHEAT, env.player.getInventory().getItem(0).getType());
        verify(env.manager).markDirty(trough.furniture);
        env.server.getPluginManager().assertEventFired(DishCookedEvent.class,
                event -> event.getResult().getAmount() == 1 && event.getMethod().equals("trough"));
        env.player.getInventory().clear(); fill();
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> inv.getArgument(1)); handler.onInteract(trough.interact());
        }
        assertTrue(trough.active.isEmpty()); assertEquals(1, env.dropped.size()); assertEquals(Material.WHEAT, env.dropped.getFirst().getType());
    }

    @ParameterizedTest @ValueSource(strings = {"null-path", "blank-path", "null", "air", "throw"})
    void failedFeedCreationPreservesAllIngredients(String failure) {
        fill(); if (failure.equals("null-path")) ItemCache.troughFeed = null;
        else if (failure.equals("blank-path")) ItemCache.troughFeed = " ";
        else if (failure.equals("throw")) when(env.api.getCreator().getItemFromPath("feed")).thenThrow(new IllegalArgumentException("Missing feed template"));
        else when(env.api.getCreator().getItemFromPath("feed")).thenReturn(failure.equals("air") ? new ItemStack(Material.AIR) : null);
        handler.onInteract(trough.interact()); assertEquals(4, trough.active.size()); assertTrue(env.player.getInventory().isEmpty()); verify(env.manager, never()).markDirty(trough.furniture);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1})
    void invalidBatchAmountsNeverConsumeOrStoreFood(int count) {
        ItemCache.troughItemsPerClick = count; ItemStack held = env.hold(env.stack(carrot(), Material.CARROT, 4));
        handler.onInteract(trough.interact()); assertEquals(4, held.getAmount()); assertTrue(trough.active.isEmpty()); verify(env.manager, never()).markDirty(trough.furniture);
    }

    @Test void agingUpdatesFreshFoodAndEjectsUnrecognizedOrRottenContents() {
        var fresh = trough.place("a", env.stack(carrot(), Material.CARROT, 3)); trough.place("b", new ItemStack(Material.STONE)); trough.place("c", env.stack(rotten(), Material.CARROT, 3)); trough.active.put("d", null);
        Station ignored = kitchen.station("chair"); var unaged = ignored.place("a", fresh.getCurrentItem());
        new TroughAging().start(); env.server.getScheduler().performTicks(20);
        assertEquals(2, env.dropped.size()); assertFalse(trough.active.containsKey("b")); assertFalse(trough.active.containsKey("c")); verify(fresh).forceModel(any()); verify(unaged, never()).forceModel(any());
        verify(env.manager).markDirty(trough.furniture); assertEquals(3, fresh.getCurrentItem().getAmount());
        kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(null);
        trough.place("b", null); trough.place("c", new ItemStack(Material.AIR)); env.server.getScheduler().performTicks(20); verify(env.manager).markDirty(trough.furniture);
    }

    @Test void agingEjectsFoodWhoseUpdatedMetadataIsGoneAndHandlesUnavailableDropLocations() {
        trough.place("a", env.stack(carrot(), Material.CARROT, 3)); kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(new ItemStack(Material.STONE));
        new TroughAging().start(); env.server.getScheduler().performTicks(20); assertFalse(trough.active.containsKey("a")); assertEquals(Material.STONE, env.dropped.getFirst().getType());
        trough.place("a", new ItemStack(Material.STONE)); when(trough.furniture.getLoc()).thenReturn(null); env.server.getScheduler().performTicks(20); assertFalse(trough.active.containsKey("a")); assertEquals(1, env.dropped.size());
        trough.place("a", new ItemStack(Material.STONE)); when(trough.furniture.getLoc()).thenReturn(new Location(null, 0, 0, 0)); env.server.getScheduler().performTicks(20); assertFalse(trough.active.containsKey("a")); assertEquals(1, env.dropped.size());
    }

    @Test void failedAgingRenderingNeverDeletesStoredFood() {
        ItemStack original = env.stack(carrot(), Material.CARROT, 3); var slot = trough.place("a", original);
        kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(new ItemStack(Material.AIR));
        new TroughAging().start(); env.server.getScheduler().performTicks(20);
        assertSame(original, slot.getCurrentItem()); assertTrue(trough.active.containsKey("a")); assertTrue(env.dropped.isEmpty()); verify(env.manager, never()).markDirty(trough.furniture);
    }
}
