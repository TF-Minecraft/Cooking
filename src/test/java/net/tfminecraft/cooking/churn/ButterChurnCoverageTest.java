package net.tfminecraft.cooking.churn;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.*;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.*;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.mixing.MixingBowlAnimation;
import net.tfminecraft.cooking.quality.*;
import net.tfminecraft.cooking.utils.*;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.*;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ItemDisplayMock;
import org.mockito.MockedStatic;

/** Public only so the second station suite can reuse the same external integration fixture. */
public class ButterChurnCoverageTest {
    private KitchenFixture e;
    private Station s;
    private ButterChurnHandler handler;
    @BeforeEach void setUp() { e = new KitchenFixture(); s = e.station("churn"); handler = new ButterChurnHandler(); }
    @AfterEach void tearDown() { e.close(); }

    @Test void emptyAndMalformedSnapshotsHaveSafeDefaults() {
        assertEquals(0, ButterChurnState.getChurnCount(s.f));
        assertEquals(1, ButterChurnState.getMilkQuality(s.f));
        assertEquals(DairyOrigin.COW, ButterChurnState.getMilkOrigin(s.f));
        assertEquals(0, ButterChurnState.getDairyFreshness(s.f));
        long before = System.currentTimeMillis();
        assertTrue(ButterChurnState.getLastUpdate(s.f) >= before);
        assertFalse(ButterChurnState.hasSalt(s.f));
        assertEquals(1, ButterChurnState.getSaltQuality(s.f));
        assertFalse(ButterChurnState.hasSpice(s.f));
        assertNull(ButterChurnState.getSpiceOrigin(s.f));
        assertEquals(1, ButterChurnState.getSpiceQuality(s.f));
        assertEquals(0, ButterChurnState.getSpiceFreshness(s.f));
        assertFalse(ButterChurnState.hasExtras(s.f));
        assertTrue(ButterChurnState.getMilkLineage(s.f).isEmpty());
        assertTrue(ButterChurnState.getSaltLineage(s.f).isEmpty());
        assertTrue(ButterChurnState.getSpiceLineage(s.f).isEmpty());
        s.variables.put(ButterChurnState.VAR_HAS_SALT, false);
        s.variables.put(ButterChurnState.VAR_SPICE_ORIGIN, " ");
        assertFalse(ButterChurnState.hasExtras(s.f));
    }

    @Test void snapshotsPreserveOriginsLineageAndExtrasAndClearOnlyTheirOwnKeys() {
        s.variables.put("unrelated", "keep");
        IngredientLineage milk = IngredientLineage.ofMain("Goat"), salt = IngredientLineage.ofExtra("Salt"), spice = IngredientLineage.ofExtra("Rosemary");
        ButterChurnState.setMilkSnapshot(s.f, 4, -2, "Goat");
        ButterChurnState.setMilkLineage(s.f, milk);
        ButterChurnState.setSalt(s.f, 2); ButterChurnState.setSaltLineage(s.f, salt);
        ButterChurnState.setSpice(s.f, "Rosemary", 3, 8); ButterChurnState.setSpiceLineage(s.f, spice);
        assertEquals(4, ButterChurnState.getMilkQuality(s.f));
        assertEquals("Goat", ButterChurnState.getMilkOrigin(s.f));
        assertEquals(0, ButterChurnState.getDairyFreshness(s.f));
        assertEquals(milk, ButterChurnState.getMilkLineage(s.f));
        assertTrue(ButterChurnState.hasExtras(s.f));
        assertEquals(2, ButterChurnState.getSaltQuality(s.f));
        assertEquals(salt, ButterChurnState.getSaltLineage(s.f));
        assertEquals("Rosemary", ButterChurnState.getSpiceOrigin(s.f));
        assertEquals(3, ButterChurnState.getSpiceQuality(s.f));
        assertEquals(8, ButterChurnState.getSpiceFreshness(s.f));
        assertEquals(spice, ButterChurnState.getSpiceLineage(s.f));
        assertEquals(1, ButterChurnState.incrementChurnCount(s.f));
        assertFalse(ButterChurnState.isReady(s.f, 2));
        assertEquals(2, ButterChurnState.incrementChurnCount(s.f));
        assertTrue(ButterChurnState.isReady(s.f, 2));
        ButterChurnState.clearExtras(s.f);
        assertFalse(ButterChurnState.hasExtras(s.f));
        assertEquals(milk, ButterChurnState.getMilkLineage(s.f));
        ButterChurnState.setSpice(s.f, "Pepper", 1, -1);
        assertTrue(ButterChurnState.hasExtras(s.f));
        assertEquals(0, ButterChurnState.getSpiceFreshness(s.f));
        ButterChurnState.clear(s.f);
        assertEquals(Map.of("unrelated", "keep"), s.variables);
    }

    @Test void agingIgnoresMissingMilkFutureClocksAndSubsecondIntervalsThenRetainsFractions() {
        ButterChurnState.tickAge(s.f); assertTrue(s.variables.isEmpty());
        s.place("input_1", new ItemStack(Material.MILK_BUCKET));
        s.variables.put(ButterChurnState.VAR_LAST_UPDATE, System.currentTimeMillis() + 60_000);
        ButterChurnState.tickAge(s.f); assertEquals(0, ButterChurnState.getDairyFreshness(s.f));
        s.variables.put(ButterChurnState.VAR_LAST_UPDATE, System.currentTimeMillis() - 50);
        ButterChurnState.tickAge(s.f); assertEquals(0, ButterChurnState.getDairyFreshness(s.f));
        MemoryConfiguration milkConfig = new MemoryConfiguration();
        milkConfig.set("age.freshness", 4);
        e.templates.put("milk_bucket", new FoodItem("milk_bucket", milkConfig));
        s.variables.put(ButterChurnState.VAR_LAST_UPDATE, System.currentTimeMillis() - 2_100);
        ButterChurnState.tickAge(s.f);
        assertEquals(0, ButterChurnState.getDairyFreshness(s.f));
        assertEquals(0.5, s.variables.get(ButterChurnState.VAR_FRESHNESS_REMAINDER));
        s.variables.put(ButterChurnState.VAR_LAST_UPDATE, System.currentTimeMillis() - 2_100);
        ButterChurnState.tickAge(s.f);
        assertEquals(1, ButterChurnState.getDairyFreshness(s.f));
        assertEquals(0.0, s.variables.get(ButterChurnState.VAR_FRESHNESS_REMAINDER));
    }

    @Test void placementCreatesTheMissingStickButPreservesExistingModels() {
        handler.onPlace(new FurniturePlaceEvent(e.station("other").f, e.player));
        s.define("stick"); handler.onPlace(new FurniturePlaceEvent(s.f, e.player));
        assertEquals(Material.STICK, s.active.get("stick").getCurrentItem().getType());
        PlacedSlot stick = s.active.get("stick"); clearInvocations(stick);
        handler.onPlace(new FurniturePlaceEvent(s.f, e.player)); verify(stick, never()).forceModel(any());
        s.active.clear(); s.definitions.clear();
        handler.onPlace(new FurniturePlaceEvent(s.f, e.player)); assertTrue(s.active.isEmpty());
        when(s.f.getType()).thenReturn(null);
        handler.onPlace(new FurniturePlaceEvent(s.f, e.player)); assertTrue(s.active.isEmpty());
    }

    @Test void aLoadedStickSlotWithoutItsItemIsRebuiltAndUnusableCarriedFurnitureCannotCollect() {
        milk(); s.define("stick"); s.place("stick", null);
        handler.onPlace(new FurniturePlaceEvent(s.f, e.player));
        assertEquals(Material.STICK, s.active.get("stick").getCurrentItem().getType());
        Station carried = e.station("other"); when(e.manager.getByCarrier(e.player)).thenReturn(carried.f);
        handler.onInteract(s.interact()); assertTrue(s.active.containsKey("input_1")); assertTrue(carried.active.isEmpty());
        Station missingType = e.station("plate"); when(missingType.f.getType()).thenReturn(null); when(e.manager.getByCarrier(e.player)).thenReturn(missingType.f);
        handler.onInteract(s.interact()); assertTrue(s.active.containsKey("input_1")); assertTrue(missingType.active.isEmpty());
    }

    @Test void addingMilkSnapshotsItsMetadataAndReturnsTheEmptyBucket() {
        s.define("input_1");
        FoodItem milk = e.food("milk_bucket", "dairy", "Goat", 4, 9);
        milk.setLineage(IngredientLineage.ofMain("Goat"));
        var event = s.add(e.stack(milk, Material.MILK_BUCKET, 1), "input_1");
        handler.onMilkAdd(event);
        assertFalse(event.isCancelled());
        assertEquals(4, ButterChurnState.getMilkQuality(s.f));
        assertEquals(9, ButterChurnState.getDairyFreshness(s.f));
        assertEquals("Goat", ButterChurnState.getMilkOrigin(s.f));
        assertEquals(milk.getLineage(), ButterChurnState.getMilkLineage(s.f));
        assertEquals(Material.BUCKET, e.player.getInventory().getItemInMainHand().getType());
        verify(e.manager).markDirty(s.f);
        s.place("input_1", event.getItem());
        var duplicate = s.add(event.getItem(), "input_1"); handler.onMilkAdd(duplicate);
        assertTrue(duplicate.isCancelled()); assertTrue(e.messages.contains("The churn already has milk."));
        s.active.clear(); handler.onMilkAdd(s.add(new ItemStack(Material.MILK_BUCKET), "input_1"));
        assertEquals(DairyOrigin.COW, ButterChurnState.getMilkOrigin(s.f));
        handler.onMilkAdd(s.add(new ItemStack(Material.STONE), "decor"));
        handler.onMilkAdd(e.station("other").add(new ItemStack(Material.STONE), "input_1"));
    }

    @Test void plateAndChurnEventsRejectWrongFoodsAndKeepRenderedButter() {
        Station plate = e.station("plate");
        var churnTake = s.take(null, "input_1"); handler.onTake(churnTake); assertTrue(churnTake.isCancelled());
        var empty = plate.take(null, "input_1"); handler.onTake(empty); assertTrue(empty.isCancelled());
        var stone = plate.add(new ItemStack(Material.STONE), "input_1"); handler.onPlateAdd(stone); assertTrue(stone.isCancelled());
        FoodItem carrot = e.food("carrot", "vegetable", "Carrot", 3, 0);
        var wrong = plate.take(e.stack(carrot, Material.CARROT, 1), "input_1"); handler.onTake(wrong); assertTrue(wrong.isCancelled());
        var wrongAdd = plate.add(wrong.getItem(), "input_1"); handler.onPlateAdd(wrongAdd); assertTrue(wrongAdd.isCancelled());
        FoodItem butter = e.food("butter", "dairy", "Cow", 4, 0);
        ItemStack stack = e.stack(butter, Material.GOLD_NUGGET, 1);
        var add = plate.add(stack, "input_1"); handler.onPlateAdd(add); assertFalse(add.isCancelled()); assertNotNull(add.getItem());
        var take = plate.take(stack, "input_1"); handler.onTake(take); assertFalse(take.isCancelled()); assertNotNull(take.getItem());
        e.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        handler.onPlateAdd(plate.add(stack, "input_1")); handler.onTake(plate.take(stack, "input_1"));
        Station other = e.station("other");
        handler.onPlateAdd(other.add(stack, "input_1")); handler.onTake(other.take(stack, "input_1"));
    }

    @Test void anEmptyChurnHidesItsStickAndCannotConsumeHeldExtras() {
        s.define("stick");
        handler.onInteract(s.interact()); assertTrue(s.active.isEmpty());
        e.hold(new ItemStack(Material.STONE));
        handler.onInteract(s.interact()); assertEquals(1, e.player.getInventory().getItemInMainHand().getAmount());
        handler.onInteract(e.station("other").interact());
    }

    @Test void saltAndSpiceAreConsumedOnceAndPreserveTheirQualityFreshnessAndLineage() {
        milk();
        FoodItem salt = e.food("salt", "salt", "Salt", 4, 0); salt.setLineage(IngredientLineage.ofExtra("Salt"));
        e.hold(e.stack(salt, Material.SUGAR, 2)); handler.onInteract(s.interact());
        assertTrue(ButterChurnState.hasSalt(s.f)); assertEquals(4, ButterChurnState.getSaltQuality(s.f));
        assertEquals(1, e.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(salt.getLineage(), ButterChurnState.getSaltLineage(s.f));
        handler.onInteract(s.interact()); assertTrue(e.messages.contains("Already added salt."));
        FoodItem spice = e.food("spice", "spice", "Rosemary", 3, 7); spice.setLineage(IngredientLineage.ofExtra("Rosemary"));
        e.hold(e.stack(spice, Material.GREEN_DYE, 1)); handler.onInteract(s.interact());
        assertEquals("Rosemary", ButterChurnState.getSpiceOrigin(s.f)); assertEquals(7, ButterChurnState.getSpiceFreshness(s.f));
        assertEquals(spice.getLineage(), ButterChurnState.getSpiceLineage(s.f));
        assertTrue(e.player.getInventory().getItemInMainHand().getType().isAir());
        e.hold(e.stack(spice, Material.GREEN_DYE, 2)); handler.onInteract(s.interact()); assertTrue(e.messages.contains("Already added spice."));
        ButterChurnState.clearExtras(s.f);
        for (String origin : Arrays.asList(null, "")) {
            e.hold(e.stack(e.food("bad-spice", "spice", origin, 2, 0), Material.GREEN_DYE, 2));
            handler.onInteract(s.interact()); assertFalse(ButterChurnState.hasSpice(s.f));
        }
        e.hold(new ItemStack(Material.STONE)); handler.onInteract(s.interact()); assertFalse(ButterChurnState.hasExtras(s.f));
    }

    @Test void churningRequiresItsDisplayUsesCooldownAndAnimatesBackToTheOriginalPosition() {
        milk(); s.define("stick"); PlacedSlot stick = s.place("stick", new ItemStack(Material.STICK));
        UUID displayId = UUID.randomUUID(); when(stick.getDisplayStandId()).thenReturn(displayId);
        handler.onInteract(s.interact()); assertEquals(0, ButterChurnState.getChurnCount(s.f));
        ItemDisplayMock display = e.display(displayId);
        Transformation initial = display.getTransformation();
        handler.onInteract(s.interact()); assertEquals(1, ButterChurnState.getChurnCount(s.f)); assertTrue(e.messages.contains("Churn 1/3"));
        handler.onInteract(s.interact()); assertEquals(1, ButterChurnState.getChurnCount(s.f));
        e.server.getScheduler().performTicks(14);
        assertEquals(initial.getTranslation(), display.getTransformation().getTranslation());
        handler.onBreak(new FurnitureBreakEvent(s.f, e.player));
        ButterChurnState.setChurnCount(s.f, 2); handler.onInteract(s.interact());
        assertEquals(3, ButterChurnState.getChurnCount(s.f)); assertTrue(e.messages.contains("Butter ready - use a butter plate to collect."));
        display.remove(); e.server.getScheduler().performOneTick();
        handler.onBreak(new FurnitureBreakEvent(s.f, e.player));
        ItemDisplayMock replacement = e.display(displayId); handler.onInteract(s.interact());
        assertTrue(e.messages.contains("Use a butter plate to collect."));
        handler.onBreak(new FurnitureBreakEvent(e.station("other").f, e.player));
    }

    @Test void aChurnDefinitionWithoutAStickCannotAdvanceTheBatch() {
        milk(); s.define("decoration"); s.place("decoration", new ItemStack(Material.OAK_PLANKS));
        handler.onInteract(s.interact());
        assertEquals(0, ButterChurnState.getChurnCount(s.f)); assertTrue(s.active.containsKey("input_1"));
    }

    @Test void collectingButterWaitsForReadinessThenFillsTheCarriedPlateAndClearsMilk() {
        milk(); s.define("stick");
        Station plate = e.station("plate"); plate.define("butter");
        when(e.manager.getByCarrier(e.player)).thenReturn(plate.f);
        handler.onInteract(s.interact()); assertTrue(e.messages.getLast().contains("times first"));
        ButterChurnState.setChurnCount(s.f, 3);
        e.templates.remove("butter"); handler.onInteract(s.interact()); assertTrue(e.messages.contains("Could not create butter."));
        e.templates.put("butter", e.food("butter", "dairy", "Cow", 1, 0));
        e.display(s.id);
        handler.onInteract(s.interact());
        assertNotNull(plate.active.get("butter").getCurrentItem());
        assertFalse(s.active.containsKey("input_1")); assertTrue(s.variables.isEmpty());
        verify(plate.active.get("butter")).followParentTransform(any());
        verify(e.manager).markDirty(plate.f);
    }

    @Test void collectingCannotOverwriteButterAlreadyOnThePlate() {
        milk(); s.define("stick"); ButterChurnState.setChurnCount(s.f, 3);
        Station plate = e.station("plate"); plate.define("butter");
        ItemStack previous = e.stack(e.food("butter", "dairy", "Goat", 4, 6), Material.GOLD_NUGGET, 1);
        plate.place("butter", previous); when(e.manager.getByCarrier(e.player)).thenReturn(plate.f);
        handler.onInteract(s.interact());
        assertEquals(previous, plate.active.get("butter").getCurrentItem(), "An occupied plate must retain its existing butter");
        assertTrue(s.active.containsKey("input_1")); assertEquals(3, ButterChurnState.getChurnCount(s.f));
    }

    @Test void collectingRejectsABuiltItemWithoutCookingMetadata() {
        milk(); s.define("stick"); ButterChurnState.setChurnCount(s.f, 3);
        Station plate=e.station("plate"); plate.define("butter"); when(e.manager.getByCarrier(e.player)).thenReturn(plate.f);
        e.builder.when(()->ItemBuilder.buildComposedWithQuality(any(),anyInt())).thenReturn(new ItemStack(Material.PAPER));
        handler.onInteract(s.interact());
        assertTrue(s.active.containsKey("input_1")); assertTrue(plate.active.isEmpty());
        assertEquals("Could not create butter.",e.messages.getLast());
    }

    @Test void collectingOntoAPlateWithoutOutputSlotsCannotConsumeTheMilk() {
        milk(); s.define("stick"); ButterChurnState.setChurnCount(s.f, 3);
        Station plate = e.station("plate"); when(e.manager.getByCarrier(e.player)).thenReturn(plate.f);
        handler.onInteract(s.interact());
        assertTrue(s.active.containsKey("input_1"), "A plate with no configured output cannot receive or consume a batch");
        assertEquals(3, ButterChurnState.getChurnCount(s.f)); assertTrue(plate.active.isEmpty());
    }

    @Test void collectingKeepsTheBuiltButterWhenItsOptionalFurnitureRenderIsUnavailable() {
        milk(); s.define("stick"); ButterChurnState.setChurnCount(s.f, 3);
        Station plate = e.station("plate"); plate.define("butter"); when(e.manager.getByCarrier(e.player)).thenReturn(plate.f);
        e.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        handler.onInteract(s.interact());
        assertNotNull(plate.active.get("butter").getCurrentItem(), "Rendering failure must not replace a completed batch with null");
        assertEquals("butter", e.resolve(plate.active.get("butter").getCurrentItem()).getId()); assertFalse(s.active.containsKey("input_1"));
    }

    @Test void missingOptionalIngredientTemplatesLeaveTheBatchAvailableForRepair() {
        e.templates.remove("seasoning_1");
        assertNull(ButterItems.fromMilkSnapshot(e.player, 3, 0, "Cow", true, 2, null, 1, 0));
        e.templates.put("seasoning_1", e.food("seasoning_1", "salt", "Salt", 2, 0)); e.templates.remove("spice_1");
        assertNull(ButterItems.fromMilkSnapshot(e.player, 3, 0, "Cow", false, 2, "Rosemary", 1, 0));
    }

    @Test void anAirModelBuiltByTheRealItemBuilderCannotConsumeACompletedBatch() {
        milk(); s.define("stick"); ButterChurnState.setChurnCount(s.f, 3);
        Station plate = e.station("plate"); plate.define("butter"); when(e.manager.getByCarrier(e.player)).thenReturn(plate.f);
        e.templates.get("butter").setModel(new FoodModel(new ItemStack(Material.AIR)));
        e.builder.close(); // Exercise the actual model renderer and metadata writer for this invalid config.
        handler.onInteract(s.interact());
        assertTrue(s.active.containsKey("input_1")); assertEquals(3, ButterChurnState.getChurnCount(s.f)); assertTrue(plate.active.isEmpty()); assertTrue(e.messages.contains("Could not create butter."));
    }

    @Test void missingSaltedTrackLeavesTheBatchAvailableForConfigurationRepair() {
        TrackLoader.oList.removeIf(track -> track.getId().equals("butter_salted"));
        assertNull(ButterItems.fromMilkSnapshot(e.player, 3, 0, "Cow", true, 2, null, 1, 0));
    }

    @Test void missingSpicedTrackLeavesTheBatchAvailableForConfigurationRepair() {
        TrackLoader.oList.removeIf(track -> track.getId().equals("butter_spiced"));
        assertNull(ButterItems.fromMilkSnapshot(e.player, 3, 0, "Cow", false, 2, "Rosemary", 1, 0));
    }

    @Test void emptyAndAirPlateSlotsReceiveIndependentCopiesOfTheFinishedButter() {
        milk(); s.define("stick"); ButterChurnState.setChurnCount(s.f, 3);
        Station plate = e.station("plate"); plate.define("first"); plate.define("second");
        plate.place("first", new ItemStack(Material.AIR)); plate.place("second", null);
        when(e.manager.getByCarrier(e.player)).thenReturn(plate.f); handler.onInteract(s.interact());
        ItemStack first = plate.active.get("first").getCurrentItem(), second = plate.active.get("second").getCurrentItem();
        assertNotNull(first); assertEquals(first, second); assertNotSame(first, second);
        first.setAmount(2); assertEquals(1, second.getAmount()); assertFalse(s.active.containsKey("input_1"));
    }

    @Test void loadedChurnsResumeAgingAndEmptyStaleChurnsResetOnTheirOwnChunk() {
        s.define("stick"); milk();
        Station stale = e.station("churn-stale"); ButterChurnState.setChurnCount(stale.f, 1);
        Station other = e.station("other");
        Station distant = e.station("churn-distant");
        when(distant.f.getLoc()).thenReturn(new Location(e.world, 80, 64, 80));
        Chunk far = mock(Chunk.class); when(e.world.getChunkAt(5, 5)).thenReturn(far);
        handler.resumeLoadedChurns();
        assertEquals(0, ButterChurnState.getChurnCount(stale.f)); verify(e.manager).markDirty(stale.f);
        clearInvocations(e.manager);
        handler.onChunkLoad(new ChunkLoadEvent(e.chunk, false)); e.server.getScheduler().performOneTick();
        verify(e.manager).markDirty(s.f); verify(e.manager, never()).markDirty(distant.f);
        e.server.getScheduler().performTicks(20); verify(e.manager, atLeast(2)).markDirty(s.f);
        s.active.remove("input_1"); e.server.getScheduler().performTicks(20);
        ButterChurnAging.start(null); ButterChurnAging.stop(null); ButterChurnAging.start(s.f);
        s.place("input_1", new ItemStack(Material.MILK_BUCKET)); ButterChurnAging.start(s.f); ButterChurnAging.start(s.f);
        ButterChurnAging.stopAll();
    }

    @Test void butterCompositionSupportsPlainSaltedSpicedMilkAndMissingTemplates() {
        e.templates.remove("milk_bucket"); assertNull(ButterItems.fromMilkSnapshot(e.player, 3, 10));
        e.templates.put("milk_bucket", e.food("milk_bucket", "dairy", "Cow", 3, 0));
        assertNotNull(ButterItems.fromMilkSnapshot(e.player, 3, 10));
        assertEquals("Cow", e.lastBuilt.getOrigin()); assertEquals(10, e.lastBuilt.getTagTrack("freshness").getValue());
        assertNotNull(ButterItems.fromMilkSnapshot(e.player, 4, 12, "Goat", true, 2, "Rosemary", 3, 7));
        assertEquals("Goat", e.lastBuilt.getOrigin()); assertTrue(e.lastBuilt.hasTagTrack("butter_salted")); assertTrue(e.lastBuilt.hasTagTrack("butter_spiced"));
        assertEquals(List.of("dairy", "salt", "spice"), e.composedInputs.stream().map(FoodItem::getCategory).toList());
        IngredientLineage milk = IngredientLineage.ofMain("Goat"), salt = IngredientLineage.ofExtra("Salt"), spice = IngredientLineage.ofExtra("Rosemary");
        ButterItems.fromMilkSnapshot(e.player, 4, -1, "Goat", true, 2, "Rosemary", 3, -1, milk, salt, spice);
        assertEquals(List.of(milk, salt, spice), e.composedInputs.stream().map(FoodItem::getLineage).toList());
        ButterItems.fromMilkSnapshot(e.player, 4, 0, null, false, 2, " ", 3, 0);
        assertEquals(1, e.composedInputs.size());
        assertEquals(0, ChurnExtras.readFreshness(null));
        assertEquals(0, ChurnExtras.readFreshness(e.food("plain", "salt", null, 1, -1)));
        assertFalse(ChurnExtras.isChurnSalt(null)); assertFalse(ChurnExtras.isChurnSpice(null));
        assertFalse(ChurnExtras.isChurnSpice(e.stack(e.food("salt", "salt", "Salt", 3, 0), Material.SUGAR, 1)));
        assertNotNull(ButterItems.fromMilkSnapshot(e.player, 3, 1, "Cow", true, 2, "Pepper", 3, 4, null, null, null));
    }

    private void milk() { s.place("input_1", new ItemStack(Material.MILK_BUCKET)); ButterChurnState.setMilkSnapshot(s.f, 3, 0, "Cow"); }

    /** Mock only external furniture/metadata rendering; items, tracks, inventories and clocks are real. */
    public static final class KitchenFixture implements AutoCloseable {
        public final ServerMock server = MockBukkit.mock();
        public final Player player = mock(Player.class);
        public final World world = mock(World.class);
        public final Chunk chunk = mock(Chunk.class);
        public final FurnitureManager manager = mock(FurnitureManager.class);
        public final List<String> messages = new ArrayList<>();
        public final Map<UUID, Furniture> placed = new LinkedHashMap<>();
        public final Map<String, FoodItem> templates = new HashMap<>();
        public final MockedStatic<ItemCache> cache;
        public final MockedStatic<FoodItem> foods;
        public final MockedStatic<ItemUpdater> updater;
        public final MockedStatic<IngredientConverter> converter;
        public final MockedStatic<ItemBuilder> builder;
        public final ItemAPI itemApi = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        public FoodItem lastBuilt;
        public List<FoodItem> composedInputs = List.of();
        private final List<MockedStatic<?>> mocks = new ArrayList<>();
        private final Map<String, FoodItem> coded = new HashMap<>();
        private final List<TagTrack> oldTracks = TrackLoader.oList;
        private final Cooking oldPlugin = Cooking.plugin;
        private final int oldChurnCount = ItemCache.butterChurnCount, oldMixCount = ItemCache.mixingStirCount, oldDuration = ItemCache.mixingStirDurationTicks;
        private final float oldPivot = ItemCache.mixingStirPivotY;
        private final NamespacedKey key = NamespacedKey.fromString("test:kitchen_food");
        public KitchenFixture() {
            Cooking.plugin = mock(Cooking.class);
            when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking"); when(Cooking.plugin.isEnabled()).thenReturn(true);
            when(player.getInventory()).thenReturn(server.addPlayer().getInventory());
            when(player.getWorld()).thenReturn(world); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            doAnswer(call -> { messages.add(call.getArgument(0)); return null; }).when(player).sendMessage(anyString());
            when(world.getName()).thenReturn("kitchen"); when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.getChunkAt(0, 0)).thenReturn(chunk);
            when(world.getChunkAt(any(Location.class))).thenAnswer(call -> { Location loc = call.getArgument(0); return world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4); });
            when(manager.getPlacedFurniture()).thenReturn(placed);
            InteractibleFurniture plugin = mock(InteractibleFurniture.class); when(plugin.getFurnitureManager()).thenReturn(manager);
            scoped(InteractibleFurniture.class).when(InteractibleFurniture::getInstance).thenReturn(plugin);
            MockedStatic<FurnitureCache> furnitureCache = scoped(FurnitureCache.class);
            furnitureCache.when(() -> FurnitureCache.isButterChurn(any())).thenAnswer(call -> kind(call.getArgument(0), "churn"));
            furnitureCache.when(() -> FurnitureCache.isButterPlate(any())).thenAnswer(call -> kind(call.getArgument(0), "plate"));
            furnitureCache.when(() -> FurnitureCache.isMixingBowl(any())).thenAnswer(call -> kind(call.getArgument(0), "bowl"));
            TrackLoader.oList = new ArrayList<>(); new TrackLoader().load(new File("src/main/resources/tags.yml"));
            for (String id : List.of("milk_bucket", "butter", "seasoning_1", "spice_1", "dough")) templates.put(id, food(id, "ingredient", null, 3, 0));
            scoped(FoodLoader.class).when(() -> FoodLoader.getByString(anyString())).thenAnswer(call -> templates.get(call.getArgument(0)));
            foods = mockStatic(FoodItem.class, CALLS_REAL_METHODS); mocks.add(foods);
            foods.when(() -> FoodItem.fromItem(any())).thenAnswer(call -> resolve(call.getArgument(0)));
            updater = scoped(ItemUpdater.class); updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(call -> call.getArgument(0));
            converter = scoped(IngredientConverter.class); converter.when(() -> IngredientConverter.convertIfNeeded(any(), any())).thenAnswer(call -> call.getArgument(1));
            scoped(MilkBucketConverter.class).when(() -> MilkBucketConverter.convertIfNeeded(any(), any())).thenAnswer(call -> call.getArgument(1));
            MockedStatic<MilkBucketSnapshot> snapshot = scoped(MilkBucketSnapshot.class);
            snapshot.when(() -> MilkBucketSnapshot.readQuality(any(), any())).thenAnswer(call -> { FoodItem f = resolve(call.getArgument(1)); return f == null ? 1 : f.getQualityMin(); });
            snapshot.when(() -> MilkBucketSnapshot.readOrigin(any(), any())).thenAnswer(call -> { FoodItem f = resolve(call.getArgument(1)); return f == null ? "Cow" : f.getOrigin(); });
            snapshot.when(() -> MilkBucketSnapshot.readDairyFreshness(any(), any())).thenAnswer(call -> ChurnExtras.readFreshness(resolve(call.getArgument(1))));
            scoped(BucketItems.class).when(BucketItems::empty).thenAnswer(call -> new ItemStack(Material.BUCKET));
            scoped(CupItems.class).when(CupItems::emptyCup).thenAnswer(call -> new ItemStack(Material.GLASS_BOTTLE));
            scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(itemApi);
            when(itemApi.getCreator().getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.STONE));
            cache = scoped(ItemCache.class);
            cache.when(() -> ItemCache.getMixingModel(anyString())).thenAnswer(call -> "model." + call.getArgument(0));
            cache.when(() -> ItemCache.matchesMixingInput(anyString(), any())).thenAnswer(call -> {
                String slot = call.getArgument(0); ItemStack item = call.getArgument(1);
                return item != null && switch (slot) { case "flour" -> item.getType() == Material.WHEAT; case "water" -> item.getType() == Material.WATER_BUCKET || item.getType() == Material.HONEY_BOTTLE; case "yeast" -> item.getType() == Material.BROWN_MUSHROOM; default -> false; };
            });
            cache.when(() -> ItemCache.isCupOfWater(any())).thenAnswer(call -> { ItemStack item = call.getArgument(0); return item != null && item.getType() == Material.HONEY_BOTTLE; });
            scoped(CompositionQualityResolver.class).when(() -> CompositionQualityResolver.compose(any(), anyList(), any())).thenAnswer(call -> {
                composedInputs = List.copyOf(call.getArgument(1));
                return new CompositionResult(3, 4, Map.of(), composedInputs, List.of(), List.of(), IngredientLineage.empty());
            });
            builder = scoped(ItemBuilder.class); builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(call -> { lastBuilt = call.getArgument(0); return stack(lastBuilt, Material.PAPER, 1); });
            ItemCache.butterChurnCount = 3; ItemCache.mixingStirCount = 3; ItemCache.mixingStirDurationTicks = 3; ItemCache.mixingStirPivotY = 0;
        }
        private static boolean kind(Furniture f, String prefix) { return f != null && f.getId() != null && f.getId().startsWith(prefix); }
        public <T> MockedStatic<T> scoped(Class<T> type) { MockedStatic<T> mock = mockStatic(type); mocks.add(mock); return mock; }
        public Station station(String id) { return new Station(this, id); }
        public void hold(ItemStack item) { player.getInventory().setItemInMainHand(item); }
        public FoodItem food(String id, String category, String origin, int quality, int freshness) {
            MemoryConfiguration config = new MemoryConfiguration(); config.set("name", id); config.set("update", false);
            FoodItem food = new FoodItem(id, config); food.setCategory(category); food.setOrigin(origin); food.setQualityRange(quality, quality);
            food.setModel(new FoodModel(new ItemStack(Material.CARROT)));
            if (freshness >= 0) { TagTrack track = new TagTrack(TrackLoader.getByString("freshness")); track.forceSetValue(freshness); food.addOrModifyTrack(track); }
            return food;
        }
        public ItemStack stack(FoodItem food, Material material, int amount) {
            ItemStack stack = new ItemStack(material, amount); String id = UUID.randomUUID().toString(); coded.put(id, food);
            var meta = stack.getItemMeta(); meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id); stack.setItemMeta(meta); return stack;
        }
        public FoodItem resolve(ItemStack stack) {
            if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
            return coded.get(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
        }
        public ItemDisplayMock display(UUID id) {
            ItemDisplayMock display = new ItemDisplayMock(server, id); display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
            server.registerEntity(display); return display;
        }
        @Override public void close() {
            ButterChurnAging.stopAll(); for (Furniture f : placed.values()) MixingBowlAnimation.clearAnimating(f);
            MockBukkit.unmock(); for (int i = mocks.size() - 1; i >= 0; i--) if (!mocks.get(i).isClosed()) mocks.get(i).close();
            TrackLoader.oList = oldTracks; Cooking.plugin = oldPlugin;
            ItemCache.butterChurnCount = oldChurnCount; ItemCache.mixingStirCount = oldMixCount; ItemCache.mixingStirDurationTicks = oldDuration; ItemCache.mixingStirPivotY = oldPivot;
        }
    }

    public static final class Station {
        public final Furniture f = mock(Furniture.class);
        public final FurnitureType type = mock(FurnitureType.class);
        public final UUID id = UUID.randomUUID();
        public final Map<String, Object> variables = new LinkedHashMap<>();
        public final Map<String, SlotDefinition> definitions = new LinkedHashMap<>();
        public final Map<String, PlacedSlot> active = new LinkedHashMap<>();
        private final KitchenFixture e;
        Station(KitchenFixture e, String name) {
            this.e = e; when(f.getId()).thenReturn(name); when(f.getEntityId()).thenReturn(id); when(f.getType()).thenReturn(type);
            when(f.getLoc()).thenReturn(new Location(e.world, 1, 64, 1)); when(f.getVariables()).thenReturn(variables);
            when(type.getSlots()).thenReturn(definitions); when(type.getSlot(anyString())).thenAnswer(call -> definitions.get(call.getArgument(0)));
            when(f.getActiveSlots()).thenReturn(active); when(f.hasActiveSlot(anyString())).thenAnswer(call -> active.containsKey(call.getArgument(0)));
            when(f.getActiveSlot(anyString())).thenAnswer(call -> Optional.ofNullable(active.get(call.getArgument(0))));
            when(f.getOrCreatePlacedSlot(anyString())).thenAnswer(call -> { String id = call.getArgument(0); return active.containsKey(id) ? active.get(id) : place(id, null); });
            doAnswer(call -> { active.remove(call.getArgument(0)); return null; }).when(f).removeActiveSlot(anyString());
            e.placed.put(id, f);
        }
        public SlotDefinition define(String id) {
            return definitions.computeIfAbsent(id, key -> { SlotDefinition def = mock(SlotDefinition.class); when(def.getId()).thenReturn(key); return def; });
        }
        public PlacedSlot place(String id, ItemStack item) {
            PlacedSlot slot = mock(PlacedSlot.class); ItemStack[] value = {item};
            when(slot.getDisplayStandId()).thenReturn(UUID.randomUUID());
            when(slot.getCurrentItem()).thenAnswer(call -> value[0]);
            doAnswer(call -> { value[0] = call.getArgument(0); active.put(id, slot); return null; }).when(slot).forceModel(any());
            doAnswer(call -> { value[0] = null; active.remove(id); return null; }).when(slot).clearModel();
            active.put(id, slot); return slot;
        }
        public FurnitureInteractEvent interact() { return new FurnitureInteractEvent(e.player, f); }
        public FurnitureSlotItemAddEvent add(ItemStack stack, String slot) { return new FurnitureSlotItemAddEvent(e.player, f, define(slot), stack); }
        public FurnitureSlotItemTakeEvent take(ItemStack stack, String slot) { return new FurnitureSlotItemTakeEvent(e.player, f, define(slot), stack); }
    }
}
