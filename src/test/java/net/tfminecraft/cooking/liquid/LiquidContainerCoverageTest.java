package net.tfminecraft.cooking.liquid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.CupItems;
import net.tfminecraft.cooking.cup.MilkBucketConverter;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class LiquidContainerCoverageTest {
    Workshop env;
    Station barrel;
    LiquidContainerHandler handler;
    MockedStatic<CupItems> cups;
    MockedStatic<MilkBucketConverter> converter;
    Map<UUID, Furniture> placed;
    ItemStack waterCup, milkCup;
    String oldContainer, oldWater, oldMilk;
    int oldPerBucket, oldMaximum;

    @BeforeEach void open() {
        oldContainer = FurnitureCache.liquidContainer; oldWater = ItemCache.liquidBlockWater; oldMilk = ItemCache.liquidBlockMilk;
        oldPerBucket = ItemCache.blocksPerBucket; oldMaximum = ItemCache.maxBlocks;
        env = new Workshop(); handler = new LiquidContainerHandler();
        FurnitureCache.liquidContainer = "barrel"; ItemCache.liquidBlockWater = "model.water"; ItemCache.liquidBlockMilk = "model.milk";
        ItemCache.blocksPerBucket = 3; ItemCache.maxBlocks = 6;
        barrel = env.station("barrel"); for (String id : LiquidContainerSlots.all()) barrel.define(id);
        placed = new LinkedHashMap<>(); placed.put(barrel.id, barrel.furniture); when(env.manager.getPlacedFurniture()).thenReturn(placed);
        env.cache.when(() -> ItemCache.isMilkBucket(any())).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.MILK_BUCKET;
        });
        env.cache.when(() -> ItemCache.isEmptyCup(any())).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.PAPER;
        });
        converter = env.scoped(mockStatic(MilkBucketConverter.class));
        converter.when(() -> MilkBucketConverter.convertIfNeeded(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        cups = env.scoped(mockStatic(CupItems.class));
        waterCup = env.stack(env.food("cup_of_water", null, 1), Material.POTION, 1);
        milkCup = env.stack(env.food("cup_of_milk", "Goat", 4), Material.POTION, 1);
        cups.when(CupItems::cupOfWater).thenAnswer(inv -> waterCup.clone());
        cups.when(() -> CupItems.cupOfMilk(any(), anyInt(), anyInt(), anyString())).thenAnswer(inv -> milkCup.clone());
    }

    @AfterEach void close() {
        LiquidContainerAging.stopAll(); env.server.getScheduler().cancelTasks(Cooking.plugin); env.close();
        FurnitureCache.liquidContainer = oldContainer; ItemCache.liquidBlockWater = oldWater; ItemCache.liquidBlockMilk = oldMilk;
        ItemCache.blocksPerBucket = oldPerBucket; ItemCache.maxBlocks = oldMaximum;
    }

    void fill(String type, int count) { LiquidContainerState.setType(barrel.furniture, type); LiquidContainerState.setBlocks(barrel.furniture, count); }
    void click() { handler.onInteract(barrel.interact()); }
    ItemStack milk(int quality, int freshness, String origin) {
        FoodItem food = env.food("milk_bucket", origin, quality);
        TagTrack track = new TagTrack(TrackLoader.getByString("freshness")); track.setValue(freshness); food.addOrModifyTrack(track);
        return env.stack(food, Material.MILK_BUCKET, 1);
    }

    @Test
    void stateDefaultsClampNegativeAmountsAndClearOnlyLiquidVariables() {
        assertNull(LiquidContainerState.getType(barrel.furniture)); assertTrue(LiquidContainerState.isEmpty(barrel.furniture));
        assertEquals(1, LiquidContainerState.getMilkQuality(barrel.furniture)); assertEquals("Cow", LiquidContainerState.getMilkOrigin(barrel.furniture));
        assertEquals(0, LiquidContainerState.getDairyFreshness(barrel.furniture));
        assertTrue(Math.abs(System.currentTimeMillis() - LiquidContainerState.getLastUpdate(barrel.furniture)) < 1000);
        barrel.variables.put(LiquidContainerState.VAR_TYPE, 3); barrel.variables.put(LiquidContainerState.VAR_BLOCKS, "bad");
        assertNull(LiquidContainerState.getType(barrel.furniture)); assertEquals(0, LiquidContainerState.getBlocks(barrel.furniture));
        fill("MILK", 1); assertTrue(LiquidContainerState.canAccept(barrel.furniture, "milk")); assertFalse(LiquidContainerState.canAccept(barrel.furniture, "water"));
        LiquidContainerState.setBlocks(barrel.furniture, -5); assertTrue(LiquidContainerState.canAccept(barrel.furniture, "water"));
        assertEquals(6, LiquidContainerState.addBlocks(barrel.furniture, 12, 6)); assertEquals(5, LiquidContainerState.removeBlock(barrel.furniture));
        LiquidContainerState.setMilkSnapshot(barrel.furniture, 4, -7, "Goat");
        assertEquals(4, LiquidContainerState.getMilkQuality(barrel.furniture)); assertEquals("Goat", LiquidContainerState.getMilkOrigin(barrel.furniture));
        assertEquals(0, LiquidContainerState.getDairyFreshness(barrel.furniture));
        LiquidContainerState.setType(barrel.furniture, null); assertNull(LiquidContainerState.getType(barrel.furniture));
        barrel.variables.put("unrelated", 8); LiquidContainerState.clear(barrel.furniture); assertEquals(Map.of("unrelated", 8), barrel.variables);
        assertEquals(0, LiquidContainerState.removeBlock(barrel.furniture));
    }

    @Test
    void milkAgingUsesElapsedWholeSecondsAndCarriesScaledFractions() {
        MemoryConfiguration settings = new MemoryConfiguration(); settings.set("age.freshness", 4.0);
        env.templates.put("milk_bucket", new FoodItem("milk_bucket", settings));
        fill("water", 2); LiquidContainerState.tickAge(barrel.furniture); assertFalse(barrel.variables.containsKey(LiquidContainerState.VAR_LAST_UPDATE));
        fill("milk", 0); LiquidContainerState.tickAge(barrel.furniture); assertFalse(barrel.variables.containsKey(LiquidContainerState.VAR_LAST_UPDATE));
        fill("milk", 2); LiquidContainerState.setMilkSnapshot(barrel.furniture, 3, 7, "Cow");
        barrel.variables.put(LiquidContainerState.VAR_LAST_UPDATE, System.currentTimeMillis() + 100_000L);
        LiquidContainerState.tickAge(barrel.furniture); assertEquals(7, LiquidContainerState.getDairyFreshness(barrel.furniture));
        barrel.variables.put(LiquidContainerState.VAR_LAST_UPDATE, System.currentTimeMillis());
        LiquidContainerState.tickAge(barrel.furniture); assertEquals(7, LiquidContainerState.getDairyFreshness(barrel.furniture));
        barrel.variables.put(LiquidContainerState.VAR_LAST_UPDATE, System.currentTimeMillis() - 2_000L);
        LiquidContainerState.tickAge(barrel.furniture); assertEquals(7, LiquidContainerState.getDairyFreshness(barrel.furniture));
        assertEquals(0.5, (Double) barrel.variables.get(LiquidContainerState.VAR_FRESHNESS_REMAINDER), 0.001);
        barrel.variables.put(LiquidContainerState.VAR_LAST_UPDATE, System.currentTimeMillis() - 2_000L);
        LiquidContainerState.tickAge(barrel.furniture); assertEquals(8, LiquidContainerState.getDairyFreshness(barrel.furniture));
        assertEquals(0.0, (Double) barrel.variables.get(LiquidContainerState.VAR_FRESHNESS_REMAINDER), 0.001);
    }

    @Test
    void liquidDisplaysShowIndependentLayersAndClearUnusedLevels() {
        fill("water", 3); LiquidContainerDisplay.sync(barrel.furniture);
        assertEquals(3, barrel.active.size());
        assertNotSame(barrel.active.get("liquid_1").getCurrentItem(), barrel.active.get("liquid_2").getCurrentItem());
        verify(env.api.getCreator()).getItemFromPath("model.water");
        fill("milk", 6); ItemCache.maxBlocks = 8; LiquidContainerDisplay.sync(barrel.furniture);
        assertEquals(6, barrel.active.size()); verify(env.api.getCreator()).getItemFromPath("model.milk");
        for (int level = 1; level <= 6; level++) assertEquals("liquid_" + level, LiquidContainerSlots.slotForLevel(level));
        assertNull(LiquidContainerSlots.slotForLevel(0)); assertNull(LiquidContainerSlots.slotForLevel(7));
        LiquidContainerDisplay.clearAll(barrel.furniture); assertTrue(barrel.active.isEmpty());
        barrel.definitions.remove("liquid_1"); fill("water", 1); LiquidContainerDisplay.sync(barrel.furniture); assertTrue(barrel.active.isEmpty());
        when(barrel.furniture.getType()).thenReturn(null); LiquidContainerDisplay.sync(barrel.furniture); LiquidContainerDisplay.clearAll(barrel.furniture);
        assertTrue(barrel.active.isEmpty());
    }

    @Test
    void missingLiquidModelsDoNotReplaceExistingDisplays() {
        PlacedSlot slot = barrel.place("liquid_1", new ItemStack(Material.STONE)); fill("water", 1);
        when(env.api.getCreator().getItemFromPath("model.water")).thenReturn(null);
        LiquidContainerDisplay.sync(barrel.furniture); verify(slot, never()).forceModel(any()); verify(slot, never()).clearModel();
    }

    @Test
    void waterBucketsFillToCapacityAndRejectMixingWithMilk() {
        env.hold(new ItemStack(Material.WATER_BUCKET)); click();
        assertEquals(3, LiquidContainerState.getBlocks(barrel.furniture)); assertEquals("water", LiquidContainerState.getType(barrel.furniture));
        assertEquals(Material.BUCKET, env.player.getInventory().getItemInMainHand().getType()); assertEquals(3, barrel.active.size());
        env.hold(new ItemStack(Material.WATER_BUCKET)); click(); assertEquals(6, LiquidContainerState.getBlocks(barrel.furniture));
        env.hold(new ItemStack(Material.WATER_BUCKET)); click(); assertEquals(Material.WATER_BUCKET, env.player.getInventory().getItemInMainHand().getType());
        assertTrue(env.messages.contains("The container is full."));
        fill("milk", 2); click(); assertTrue(env.messages.contains("This container already has milk."));
        assertEquals(2, LiquidContainerState.getBlocks(barrel.furniture)); verify(env.manager, times(2)).markDirty(barrel.furniture);
    }

    @ParameterizedTest
    @CsvSource({"WATER_BUCKET,0", "WATER_BUCKET,-2", "MILK_BUCKET,0", "MILK_BUCKET,-2"})
    void invalidBucketVolumesDoNotConsumeThePlayersBucket(Material bucket, int amount) {
        ItemCache.blocksPerBucket = amount; env.hold(new ItemStack(bucket)); click();
        assertEquals(bucket, env.player.getInventory().getItemInMainHand().getType());
        assertTrue(LiquidContainerState.isEmpty(barrel.furniture)); assertTrue(barrel.active.isEmpty());
        converter.verify(() -> MilkBucketConverter.convertIfNeeded(any(), any()), never());
        verify(env.manager, never()).markDirty(barrel.furniture);
    }

    @Test
    void milkRetainsTheFirstBatchSnapshotAndRejectsFullOrWaterContainers() {
        ItemStack converted = milk(4, 2, "Goat");
        converter.when(() -> MilkBucketConverter.convertIfNeeded(any(), any())).thenReturn(converted);
        env.hold(new ItemStack(Material.MILK_BUCKET)); click();
        assertEquals(3, LiquidContainerState.getBlocks(barrel.furniture)); assertEquals("milk", LiquidContainerState.getType(barrel.furniture));
        assertEquals(4, LiquidContainerState.getMilkQuality(barrel.furniture)); assertEquals("Goat", LiquidContainerState.getMilkOrigin(barrel.furniture));
        assertEquals(2, LiquidContainerState.getDairyFreshness(barrel.furniture));
        converter.when(() -> MilkBucketConverter.convertIfNeeded(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        env.hold(milk(2, 0, "Cow")); click(); assertEquals(6, LiquidContainerState.getBlocks(barrel.furniture));
        assertEquals("Goat", LiquidContainerState.getMilkOrigin(barrel.furniture)); assertEquals(4, LiquidContainerState.getMilkQuality(barrel.furniture));
        env.hold(milk(2, 0, "Cow")); click(); assertTrue(env.messages.contains("The container is full."));
        fill("water", 1); click(); assertTrue(env.messages.contains("This container already has water."));
        assertEquals(Material.MILK_BUCKET, env.player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void dispensingReplacesTheLastEmptyCupAndClearsTheLastLiquidBlock() {
        fill("water", 1); env.hold(new ItemStack(Material.PAPER)); click();
        assertEquals(waterCup, env.player.getInventory().getItemInMainHand()); assertTrue(LiquidContainerState.isEmpty(barrel.furniture));
        assertNull(LiquidContainerState.getType(barrel.furniture)); assertTrue(barrel.active.isEmpty());
        fill("milk", 2); LiquidContainerState.setMilkSnapshot(barrel.furniture, 4, 2, "Goat");
        env.hold(new ItemStack(Material.PAPER)); click();
        assertEquals(milkCup, env.player.getInventory().getItemInMainHand()); assertEquals(1, LiquidContainerState.getBlocks(barrel.furniture));
        cups.verify(() -> CupItems.cupOfMilk(env.player, 4, 2, "Goat"));
    }

    @Test
    void dispensingFromAStackRequiresSpaceAndDropsDeliveryLeftovers() {
        fill("water", 3); ItemStack held = env.hold(new ItemStack(Material.PAPER, 3)); click();
        assertEquals(2, held.getAmount()); assertEquals(2, LiquidContainerState.getBlocks(barrel.furniture));
        assertEquals(waterCup, env.player.getInventory().getItem(1));
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> inv.getArgument(1));
            click();
        }
        assertEquals(1, held.getAmount()); assertEquals(1, env.dropped.size()); assertEquals(waterCup, env.dropped.getFirst());
        for (int slot = 0; slot < 36; slot++) env.player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        held = env.hold(new ItemStack(Material.PAPER, 2)); click();
        assertEquals(2, held.getAmount()); assertEquals(1, LiquidContainerState.getBlocks(barrel.furniture));
        assertTrue(env.messages.contains("You have no room to carry another cup."));
        env.hold(new ItemStack(Material.PAPER)); click(); assertTrue(LiquidContainerState.isEmpty(barrel.furniture));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void failedCupCreationPreservesTheHeldCupsAndStoredLiquid(String failure) {
        fill("water", 2); ItemStack held = env.hold(new ItemStack(Material.PAPER, 3));
        ItemStack invalid = failure.equals("air") ? new ItemStack(Material.AIR) : null;
        cups.when(CupItems::cupOfWater).thenReturn(invalid); click();
        assertEquals(3, held.getAmount()); assertEquals(2, LiquidContainerState.getBlocks(barrel.furniture));
        verify(env.manager, never()).markDirty(barrel.furniture);
    }

    @Test
    void unsupportedInteractionsAndDirectLayerTakingLeaveTheContentsAlone() {
        click(); assertTrue(env.messages.contains("Hold a bucket or empty cup to use the container."));
        env.hold(new ItemStack(Material.STICK)); click(); assertTrue(env.messages.contains("Hold a water bucket, milk bucket, or empty cup."));
        env.hold(new ItemStack(Material.PAPER)); click(); assertTrue(env.messages.contains("The container is empty."));
        Station chair = env.station("chair"); var ignored = chair.interact(); handler.onInteract(ignored); assertFalse(ignored.isCancelled());
        var layer = new FurnitureSlotItemTakeEvent(env.player, barrel.furniture, barrel.define("liquid_1"), waterCup);
        handler.onTake(layer); assertTrue(layer.isCancelled());
        var decoration = new FurnitureSlotItemTakeEvent(env.player, barrel.furniture, barrel.define("decoration"), waterCup);
        handler.onTake(decoration); assertFalse(decoration.isCancelled());
        when(decoration.getSlot().getId()).thenReturn(null); handler.onTake(decoration); assertFalse(decoration.isCancelled());
        var other = new FurnitureSlotItemTakeEvent(env.player, chair.furniture, chair.define("liquid_1"), waterCup);
        handler.onTake(other); assertFalse(other.isCancelled());
    }

    @Test
    void agingTasksStartOnceStopWhenEmptyAndDoNotSurviveExplicitCleanup() {
        LiquidContainerAging.start(null); LiquidContainerAging.stop(null); LiquidContainerAging.stop(barrel.furniture);
        LiquidContainerAging.start(barrel.furniture); fill("milk", 0); LiquidContainerAging.start(barrel.furniture);
        fill("milk", 2); LiquidContainerState.setMilkSnapshot(barrel.furniture, 4, 0, "Cow");
        LiquidContainerAging.start(barrel.furniture); LiquidContainerAging.start(barrel.furniture);
        barrel.variables.put(LiquidContainerState.VAR_LAST_UPDATE, System.currentTimeMillis() - 2_000L);
        env.server.getScheduler().performTicks(20); assertTrue(LiquidContainerState.getDairyFreshness(barrel.furniture) >= 2);
        verify(env.manager).markDirty(barrel.furniture);
        fill("water", 1); env.server.getScheduler().performTicks(40); verify(env.manager).markDirty(barrel.furniture);
        fill("milk", 2); LiquidContainerAging.start(barrel.furniture); LiquidContainerState.setBlocks(barrel.furniture, 0);
        env.server.getScheduler().performTicks(20); verify(env.manager).markDirty(barrel.furniture);
        fill("milk", 2); LiquidContainerAging.start(barrel.furniture); LiquidContainerAging.stopAll();
        env.server.getScheduler().performTicks(20); verify(env.manager).markDirty(barrel.furniture);
    }

    @Test
    void placementResetsTheContainerAndChunkLoadResumesOnlyMatchingFurniture() {
        Station chair = env.station("chair"); placed.put(chair.id, chair.furniture);
        fill("milk", 3); barrel.place("liquid_1", new ItemStack(Material.STONE)); barrel.variables.put("unrelated", 4);
        handler.onPlace(new FurniturePlaceEvent(chair.furniture, env.player));
        handler.onPlace(new FurniturePlaceEvent(barrel.furniture, env.player));
        assertEquals(Map.of("unrelated", 4), barrel.variables); assertTrue(barrel.active.isEmpty());
        fill("milk", 1); handler.resumeLoadedContainers(); assertEquals(1, barrel.active.size());
        clearInvocations(env.manager); handler.onBreak(new FurnitureBreakEvent(chair.furniture, env.player));
        handler.onBreak(new FurnitureBreakEvent(barrel.furniture, env.player)); env.server.getScheduler().performTicks(20); verifyNoInteractions(env.manager);
        Chunk chunk = mock(Chunk.class), distant = mock(Chunk.class);
        when(env.world.getChunkAt(any(Location.class))).thenReturn(chunk);
        Location far = mock(Location.class); when(far.getChunk()).thenReturn(distant); when(chair.furniture.getLoc()).thenReturn(far);
        handler.onChunkLoad(new ChunkLoadEvent(chunk, false)); env.server.getScheduler().performTicks(21);
        verify(env.manager).markDirty(barrel.furniture);
        fill("water", 2); handler.resumeLoadedContainers(); assertEquals(2, barrel.active.size());
    }
}
