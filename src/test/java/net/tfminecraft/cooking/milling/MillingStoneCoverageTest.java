package net.tfminecraft.cooking.milling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.entity.ItemDisplayMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class MillingStoneCoverageTest {
    Workshop env;
    Station station;
    MillingStoneHandler handler;
    Map<String, MillingRecipe> previous;
    @BeforeEach void open() {
        previous = new LinkedHashMap<>();
        for (MillingRecipe recipe : MillingRecipeRegistry.getAll()) previous.put(recipe.getId(), recipe);
        env = new Workshop(); station = env.station("milling_stone"); handler = new MillingStoneHandler();
        for (String slot : List.of("top", "flour", "wheat_1", "wheat_2", "wheat_3", "wheat_4")) station.define(slot);
        recipe("wheat", "food:grain", "v.wheat");
    }
    @AfterEach void close() { MillingRecipeRegistry.load(previous); env.close(); }
    MillingRecipe recipe(String inputFood, String matcher, String fallback) {
        MillingRecipe recipe = new MillingRecipe("flour", "milling_stone", 8, inputFood, matcher, fallback, "flour", 1, 8);
        MillingRecipeRegistry.load(Map.of("flour", recipe));
        return recipe;
    }
    void loaded() {
        env.hold(new ItemStack(Material.WHEAT, 8)); handler.onInteract(station.interact()); env.hold(null);
        assertEquals(MillingStoneStage.LOADED, MillingStoneState.getStage(station.furniture));
    }
    void ready() { loaded(); handler.onInteract(station.interact()); assertEquals(MillingStoneStage.READY, MillingStoneState.getStage(station.furniture)); }

    @Test
    void recipeLoaderReadsOptionalSettingsDefaultsAndCaseInsensitiveFurniture(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("milling.yml");
        Files.writeString(config, """
                ignored: scalar
                missing-furniture:
                  output-food: flour
                missing-output:
                  furniture: mill
                blank-furniture:
                  furniture: ' '
                  output-food: flour
                blank-output:
                  furniture: other
                  output-food: ' '
                defaults:
                  furniture: MILLING_STONE
                  output-food: flour
                custom:
                  furniture: second_mill
                  input-count: 4
                  input-food: barley
                  input: food:grain
                  vanilla-fallback: v.wheat
                  output-food: barley_flour
                  revolutions: 2
                  duration-ticks: 10
                """);
        new MillingRecipeLoader().load(config.toFile());
        assertEquals(2, MillingRecipeRegistry.getAll().size());
        MillingRecipe defaults = MillingRecipeRegistry.getByFurnitureId("milling_STONE");
        assertEquals("defaults", defaults.getId()); assertEquals("MILLING_STONE", defaults.getFurnitureId());
        assertEquals(8, defaults.getInputCount()); assertEquals(4, defaults.getRevolutions()); assertEquals(60, defaults.getDurationTicks());
        assertNull(defaults.getInputFood()); assertNull(defaults.getInputMatcher()); assertNull(defaults.getVanillaFallback());
        MillingRecipe custom = MillingRecipeRegistry.getByFurnitureId("SECOND_MILL");
        assertEquals(4, custom.getInputCount()); assertEquals("barley", custom.getInputFood());
        assertEquals("food:grain", custom.getInputMatcher()); assertEquals("v.wheat", custom.getVanillaFallback());
        assertEquals("barley_flour", custom.getOutputFood()); assertEquals(2, custom.getRevolutions()); assertEquals(10, custom.getDurationTicks());
        assertNull(MillingRecipeRegistry.getByFurnitureId(null)); assertNull(MillingRecipeRegistry.getByFurnitureId("missing"));
        assertThrows(UnsupportedOperationException.class, () -> MillingRecipeRegistry.getAll().clear());
    }

    @Test
    void unreadableRecipeFilesClearOldRecipesAndReportTheFilename(@TempDir Path directory) {
        PrintStream original = System.err;
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(diagnostics)) {
            System.setErr(capture);
            new MillingRecipeLoader().load(directory.resolve("missing.yml").toFile());
        } finally { System.setErr(original); }
        assertTrue(MillingRecipeRegistry.getAll().isEmpty());
        assertTrue(diagnostics.toString().contains("missing.yml"));
    }

    @ParameterizedTest
    @CsvSource({"input-count,0", "input-count,-1", "duration-ticks,0", "duration-ticks,-1"})
    void invalidCountsAndDurationsCannotCreateFreeFlourOrInvalidTransforms(String field, int value, @TempDir Path directory) throws Exception {
        Path config = directory.resolve("invalid.yml");
        Files.writeString(config, "invalid:\n  furniture: milling_stone\n  output-food: flour\n  vanilla-fallback: v.wheat\n  " + field + ": " + value + "\n");
        new MillingRecipeLoader().load(config.toFile());
        ItemStack held = env.hold(new ItemStack(Material.WHEAT, 8));
        handler.onInteract(station.interact());
        assertEquals(8, held.getAmount()); assertTrue(station.variables.isEmpty());
        assertTrue(MillingRecipeRegistry.getAll().isEmpty(), "Invalid recipe settings must be rejected before a player can consume or duplicate items");
    }

    @Test
    void stateSnapshotsRoundTripAndLegacyVisualsProvideStageFallbacks() {
        assertEquals(MillingStoneStage.EMPTY, MillingStoneState.getStage(station.furniture));
        assertEquals(QualityConfig.getPickupMin(), MillingStoneState.getOutputQuality(station.furniture));
        assertTrue(MillingStoneState.getLineage(station.furniture).isEmpty());
        station.variables.put(MillingStoneState.VAR_STAGE, "removed_stage");
        station.place("wheat_3", new ItemStack(Material.WHEAT));
        assertEquals(MillingStoneStage.LOADED, MillingStoneState.getStage(station.furniture));
        station.place("flour", new ItemStack(Material.SUGAR));
        assertEquals(MillingStoneStage.READY, MillingStoneStage.fromFurniture(station.furniture));
        station.variables.put(MillingStoneState.VAR_STAGE, 12);
        assertEquals(MillingStoneStage.READY, MillingStoneState.getStage(station.furniture));
        IngredientLineage lineage = IngredientLineage.ofMain("Emmer");
        MillingStoneState.setLineage(station.furniture, lineage); MillingStoneState.setOutputQuality(station.furniture, 4);
        MillingStoneState.setStage(station.furniture, MillingStoneStage.LOADED);
        assertEquals(lineage, MillingStoneState.getLineage(station.furniture));
        assertEquals(4, MillingStoneState.getOutputQuality(station.furniture));
        assertEquals(MillingStoneStage.LOADED, MillingStoneState.getStage(station.furniture));
        station.variables.put("unrelated", "keep"); MillingStoneState.clear(station.furniture);
        assertEquals(Map.of("unrelated", "keep"), station.variables);
    }

    @Test
    void visualsShowOnlyTheCurrentStageAndUseTheConfiguredOrFallbackFlourModel() {
        MillingStoneDisplay.syncVisuals(station.furniture, MillingStoneStage.LOADED);
        assertTrue(station.active.containsKey("top")); assertFalse(station.active.containsKey("flour"));
        for (String slot : MillingStoneSlots.WHEAT) assertEquals(Material.WHEAT, station.active.get(slot).getCurrentItem().getType());
        MillingStoneDisplay.syncVisuals(station.furniture, MillingStoneStage.READY);
        assertTrue(station.active.containsKey("flour"));
        for (String slot : MillingStoneSlots.WHEAT) assertFalse(station.active.containsKey(slot));
        verify(env.api.getCreator()).getItemFromPath("model.flour");
        env.cache.when(() -> ItemCache.getMixingModel("flour")).thenReturn(null);
        MillingStoneDisplay.showFlour(station.furniture);
        verify(env.api.getCreator()).getItemFromPath("ia.tfmc_cooking:flour_model");
        MillingStoneDisplay.syncVisuals(station.furniture, MillingStoneStage.EMPTY);
        assertEquals(List.of("top"), List.copyOf(station.active.keySet()));
    }

    @Test
    void missingDefinitionsTypesAndModelsDoNotCreateBrokenDisplays() {
        station.definitions.clear();
        MillingStoneDisplay.showTop(station.furniture); MillingStoneDisplay.showWheat(station.furniture);
        MillingStoneDisplay.showFlour(station.furniture); MillingStoneDisplay.clearAll(station.furniture);
        assertTrue(station.active.isEmpty());
        when(station.furniture.getType()).thenReturn(null);
        MillingStoneDisplay.showTop(station.furniture); MillingStoneDisplay.showWheat(station.furniture);
        MillingStoneDisplay.showFlour(station.furniture); MillingStoneDisplay.clearAll(station.furniture);
        assertTrue(station.active.isEmpty());
        when(station.furniture.getType()).thenReturn(station.type);
        station.define("top"); station.define("flour");
        when(env.api.getCreator().getItemFromPath(anyString())).thenReturn(null);
        MillingStoneDisplay.showTop(station.furniture); MillingStoneDisplay.showFlour(station.furniture);
        assertTrue(station.active.isEmpty());
    }

    @Test
    void placementAndUnrelatedFurnitureEventsRespectTheRegistry() {
        Station chair = env.station("chair");
        handler.onPlace(new FurniturePlaceEvent(chair.furniture, env.player));
        handler.onInteract(chair.interact()); handler.onBreak(new FurnitureBreakEvent(chair.furniture, env.player));
        assertTrue(chair.active.isEmpty());
        handler.onPlace(new FurniturePlaceEvent(station.furniture, env.player));
        assertTrue(station.active.containsKey("top")); verify(env.manager).markDirty(station.furniture);
        MillingRecipeRegistry.load(Map.of());
        ItemStack hand = env.hold(new ItemStack(Material.WHEAT, 8));
        var event = station.interact(); handler.onInteract(event); assertFalse(event.isCancelled()); assertEquals(8, hand.getAmount());
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertTrue(station.variables.isEmpty());
    }

    @Test
    void loadingRejectsInsufficientOrWrongInputWithoutChangingItsCount() {
        ItemStack shortStack = env.hold(new ItemStack(Material.WHEAT, 4));
        var tooFew = station.interact(); handler.onInteract(tooFew);
        assertTrue(tooFew.isCancelled()); assertEquals(4, shortStack.getAmount());
        assertTrue(env.messages.contains("§cYou need 8 wheat to fill the mill."));
        ItemStack wrong = env.hold(new ItemStack(Material.STONE, 8));
        handler.onInteract(station.interact()); assertEquals(8, wrong.getAmount());
        assertTrue(env.messages.contains("§cThat doesn't go in the mill."));
        assertTrue(station.variables.isEmpty());
        recipe(null, null, null);
        FoodItem rejected = env.food("barley", "Barley", 3);
        env.hold(env.stack(rejected, Material.WHEAT, 8));
        handler.onInteract(station.interact()); assertTrue(station.variables.isEmpty());
    }

    @Test
    void configuredFoodPreservesQualityAndLineageAcrossLoadingGrindingAndTaking() {
        FoodItem wheat = env.food("WHEAT", "Emmer", 4); wheat.setCategory("grain");
        IngredientLineage lineage = IngredientLineage.ofMain("Emmer"); wheat.setLineage(lineage);
        ItemStack input = env.hold(env.stack(wheat, Material.WHEAT, 10));
        handler.onPlace(new FurniturePlaceEvent(station.furniture, env.player));
        ItemDisplayMock top = station.display("top"); Transformation start = top.getTransformation();
        var load = station.interact(); handler.onInteract(load);
        assertTrue(load.isCancelled()); assertEquals(2, input.getAmount());
        assertEquals(4, MillingStoneState.getOutputQuality(station.furniture));
        assertEquals(lineage, MillingStoneState.getLineage(station.furniture));
        var occupied = station.interact(); handler.onInteract(occupied);
        assertTrue(occupied.isCancelled()); assertEquals(2, input.getAmount());
        env.hold(null); handler.onInteract(station.interact());
        assertTrue(MillingStoneAnimation.isAnimating(station.furniture));
        handler.onInteract(station.interact());
        verify(env.player, times(2)).swingMainHand();
        env.server.getScheduler().performTicks(3);
        assertNotEquals(start.getLeftRotation(), top.getTransformation().getLeftRotation());
        assertEquals(start.getTranslation(), top.getTransformation().getTranslation());
        env.server.getScheduler().performTicks(8);
        assertEquals(start, top.getTransformation());
        assertFalse(MillingStoneAnimation.isAnimating(station.furniture));
        assertEquals(MillingStoneStage.READY, MillingStoneState.getStage(station.furniture));
        assertTrue(station.active.containsKey("flour"));
        env.hold(new ItemStack(Material.STICK));
        var full = station.interact(); handler.onInteract(full); assertTrue(full.isCancelled());
        assertTrue(station.active.containsKey("flour"));
        env.hold(null); handler.onInteract(station.interact());
        assertEquals(1, env.outputs().size()); assertEquals("flour", env.lastBuilt.getId());
        assertEquals("grain", env.lastBuilt.getCategory()); assertEquals("Wheat", env.lastBuilt.getOrigin());
        assertEquals(lineage, env.lastBuilt.getLineage()); assertEquals(4, env.lastQuality);
        assertTrue(station.variables.isEmpty()); assertFalse(station.active.containsKey("flour"));
        assertEquals(MillingStoneStage.EMPTY, MillingStoneState.getStage(station.furniture));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Barley", "", "NULL"})
    void inputMatcherAcceptsOtherGrainsAndDerivesLineageWhenMissing(String origin) {
        recipe(null, "food:grain", null);
        FoodItem barley = env.food("barley", origin.equals("NULL") ? null : origin, 2); barley.setCategory("grain");
        env.hold(env.stack(barley, Material.WHEAT, 8)); handler.onInteract(station.interact());
        assertEquals(2, MillingStoneState.getOutputQuality(station.furniture));
        assertEquals(IngredientLineage.ofMain(origin.equals("Barley") ? "Barley" : "Wheat"), MillingStoneState.getLineage(station.furniture));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void vanillaWheatUsesOriginQualityWithOrWithoutAFoodTemplate(boolean templateAvailable) {
        FoodItem template = templateAvailable ? env.templates.get("wheat") : null;
        if (!templateAvailable) env.templates.remove("wheat");
        loaded();
        assertEquals(3, MillingStoneState.getOutputQuality(station.furniture));
        assertEquals(IngredientLineage.ofMain("Wheat"), MillingStoneState.getLineage(station.furniture));
        env.originQuality.verify(() -> OriginQualityResolver.resolve(env.player, template));
    }

    @Test
    void legacyAndSavedStatesRebuildMissingVisualsAndKeepAlreadyPresentDisplays() {
        station.place("wheat_3", new ItemStack(Material.WHEAT));
        MillingStoneState.setStage(station.furniture, MillingStoneStage.EMPTY);
        env.hold(new ItemStack(Material.STICK)); handler.onInteract(station.interact());
        assertEquals(MillingStoneStage.LOADED, MillingStoneState.getStage(station.furniture));
        assertTrue(station.active.containsKey("wheat_1"));
        MillingStoneDisplay.clearAll(station.furniture); MillingStoneState.setStage(station.furniture, MillingStoneStage.READY);
        handler.onInteract(station.interact()); assertTrue(station.active.containsKey("flour"));
        PlacedSlot flour = station.active.get("flour");
        handler.onInteract(station.interact()); assertSame(flour, station.active.get("flour"));
        MillingStoneDisplay.clearAll(station.furniture); MillingStoneState.clear(station.furniture); env.hold(null);
        var empty = station.interact(); handler.onInteract(empty); assertTrue(empty.isCancelled()); assertTrue(env.outputs().isEmpty());
    }

    @Test
    void unavailableFlourDoesNotClearAReadyBatchAndRejectedInventoryDeliveryDropsIt() {
        ready(); env.templates.remove("flour");
        handler.onInteract(station.interact());
        assertTrue(env.messages.contains("§cFailed to create flour."));
        assertEquals(MillingStoneStage.READY, MillingStoneState.getStage(station.furniture));
        assertTrue(station.active.containsKey("flour"));
        env.templates.put("flour", env.food("flour", "Wheat", 3));
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(eq(env.player), any())).thenAnswer(inv -> inv.getArgument(1));
            handler.onInteract(station.interact());
        }
        assertEquals(1, env.dropped.size()); assertEquals("flour", env.resolve(env.dropped.get(0)).getId());
        assertTrue(station.variables.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void breakingRefundsTheLoadedWheatOrDropsTheCompletedFlour(boolean ground) {
        if (ground) ready(); else loaded();
        station.variables.put("unrelated", "keep");
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertEquals(1, env.dropped.size());
        ItemStack dropped = env.dropped.get(0); FoodItem food = env.resolve(dropped);
        assertEquals(ground ? "flour" : "wheat", food.getId());
        assertEquals(ground ? 1 : 8, dropped.getAmount());
        assertEquals("grain", food.getCategory()); assertEquals("Wheat", food.getOrigin());
        assertEquals(IngredientLineage.ofMain("Wheat"), food.getLineage());
        assertEquals(3, env.lastQuality);
        assertEquals(Map.of("unrelated", "keep"), station.variables);
        assertFalse(station.active.containsKey("flour"));
        for (String slot : MillingStoneSlots.WHEAT) assertFalse(station.active.containsKey(slot));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedBreakRefundKeepsTheBatchAndCancelsFurnitureRemoval(boolean ground) {
        if (ground) ready(); else loaded();
        Map<String, Object> saved = new LinkedHashMap<>(station.variables);
        env.templates.remove(ground ? "flour" : "wheat");
        var event = new FurnitureBreakEvent(station.furniture, env.player);
        handler.onBreak(event);
        assertTrue(event.isCancelled(), "A failed refund must not silently destroy the paid ingredients");
        verify(env.player).sendMessage("§cThe mill's contents could not be returned. Please ask staff to check its recipe.");
        assertEquals(saved, station.variables); assertTrue(env.dropped.isEmpty());
        assertEquals(ground ? MillingStoneStage.READY : MillingStoneStage.LOADED, MillingStoneState.getStage(station.furniture));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void removingARecipeDuringReloadDoesNotDiscardAnExistingBatch(boolean ground) {
        if (ground) ready(); else loaded();
        Map<String, Object> saved = new LinkedHashMap<>(station.variables);
        MillingRecipeRegistry.load(Map.of());
        var event = new FurnitureBreakEvent(station.furniture, env.player);
        handler.onBreak(event);
        assertTrue(event.isCancelled()); assertEquals(saved, station.variables); assertTrue(env.dropped.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nullOrAirRenderedFlourLeavesAReadyBatchAvailable(boolean air) {
        ready();
        Map<String, Object> saved = new LinkedHashMap<>(station.variables);
        ItemStack failed = air ? new ItemStack(Material.AIR) : null;
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(FoodItem.class), anyInt())).thenReturn(failed);
        handler.onInteract(station.interact());
        assertEquals(saved, station.variables); assertTrue(station.active.containsKey("flour"));
        assertTrue(env.outputs().isEmpty()); assertTrue(env.dropped.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void nullOrAirRefundsNeverClearIngredientsDuringABreak(boolean ground, boolean air) {
        if (ground) ready(); else loaded();
        Map<String, Object> saved = new LinkedHashMap<>(station.variables);
        ItemStack failed = air ? new ItemStack(Material.AIR) : null;
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(FoodItem.class), anyInt())).thenReturn(failed);
        var event = new FurnitureBreakEvent(station.furniture, env.player);
        handler.onBreak(event);
        assertTrue(event.isCancelled()); assertEquals(saved, station.variables); assertTrue(env.dropped.isEmpty());
    }

    @Test
    void inferredLegacyBatchesUseWheatLineageWhenNoSnapshotExists() {
        station.place("flour", new ItemStack(Material.SUGAR));
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertEquals(IngredientLineage.ofMain("Wheat"), env.lastBuilt.getLineage());
        station.place("wheat_4", new ItemStack(Material.WHEAT));
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertEquals(IngredientLineage.ofMain("Wheat"), env.lastBuilt.getLineage());
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertEquals(2, env.dropped.size());
    }

    @Test
    void animationStopsOnDisplayRemovalAndMissingTopDisplaysCompleteSynchronously() {
        assertFalse(MillingStoneAnimation.isAnimating(null));
        Furniture noId = mock(Furniture.class);
        assertFalse(MillingStoneAnimation.isAnimating(noId));
        MillingStoneAnimation.clearAnimating(null); MillingStoneAnimation.clearAnimating(noId);
        AtomicInteger callbacks = new AtomicInteger();
        MillingStoneAnimation.playMill(station.furniture, 1, 8, callbacks::incrementAndGet);
        assertEquals(1, callbacks.get());
        MillingStoneAnimation.playMill(station.furniture, 1, 8, null);
        PlacedSlot top = station.active.get("top");
        when(top.getDisplayStandId()).thenReturn(java.util.UUID.randomUUID());
        MillingStoneAnimation.playMill(station.furniture, 1, 8, callbacks::incrementAndGet);
        assertEquals(2, callbacks.get());
        ItemDisplayMock display = station.display("top");
        MillingStoneAnimation.playMill(station.furniture, 1, 8, callbacks::incrementAndGet);
        assertTrue(MillingStoneAnimation.isAnimating(station.furniture));
        env.server.getScheduler().performOneTick(); display.remove();
        env.server.getScheduler().performTicks(10);
        assertEquals(2, callbacks.get()); assertFalse(MillingStoneAnimation.isAnimating(station.furniture));
        station.active.clear(); station.definitions.clear();
        MillingStoneAnimation.playMill(station.furniture, 1, 8, callbacks::incrementAndGet);
        assertEquals(3, callbacks.get());
    }

    @Test
    void aRecipeLoadedByAFoodPathRefundsThatFood() {
        java.util.function.BiFunction<String, String, String> refund = (food, matcher) ->
                new MillingRecipe("r", "milling_stone", 2, food, matcher, null, "cocoa_powder", 1, 8).getRefundPath();
        assertEquals("ingredient(type=roasted_cocoa_beans;tags=cooked.1)",
                refund.apply(null, " C.ingredient(type=roasted_cocoa_beans;tags=cooked.1)"));
        assertEquals("ingredient(type=roasted_cocoa_beans)", refund.apply(" ", "c.ingredient(type=roasted_cocoa_beans)"));
        assertNull(refund.apply("roasted_cocoa_beans", "c.ingredient(type=roasted_cocoa_beans)"));
        assertNull(refund.apply(null, null));
        assertNull(refund.apply(null, "c.ingredient"));
        assertNull(refund.apply(null, "ia.tfmc_cooking:roasted_cocoa_beans"));

        // Two roasted beans load the cocoa recipe; breaking the loaded stone gives them back as roasted beans.
        MillingRecipe cocoa = new MillingRecipe("cocoa_powder", "milling_stone", 2, null, "c.ingredient(type=roasted_cocoa_beans)",
                null, "cocoa_powder", 1, 8, "Cocoa", "ingredient", "roasted cocoa beans", null, null, null, 1);
        MillingRecipeRegistry.load(Map.of("cocoa_powder", cocoa));
        env.templates.put("roasted_cocoa_beans", env.food("roasted_cocoa_beans", "Cocoa", 3));
        when(env.api.getChecker().checkItemWithPath(any(), eq("c.ingredient(type=roasted_cocoa_beans)"))).thenAnswer(inv -> {
            ItemStack stack = inv.getArgument(0);
            return stack != null && stack.getType() == Material.COCOA_BEANS;
        });
        ItemStack beans = env.hold(env.stack(env.food("roasted_cocoa_beans", "Cocoa", 4), Material.COCOA_BEANS, 3));
        handler.onInteract(station.interact());
        assertEquals(1, beans.getAmount());
        env.hold(null);
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        FoodItem back = env.resolve(env.dropped.get(env.dropped.size() - 1));
        assertEquals("roasted_cocoa_beans", back.getId());
        assertEquals("ingredient", back.getCategory());
        assertEquals("Cocoa", back.getOrigin());
        assertEquals(2, env.dropped.get(env.dropped.size() - 1).getAmount());

        // A refund food that no longer exists keeps the stone and its beans.
        env.hold(env.stack(env.food("roasted_cocoa_beans", "Cocoa", 4), Material.COCOA_BEANS, 2));
        handler.onInteract(station.interact()); env.hold(null);
        assertEquals(MillingStoneStage.LOADED, MillingStoneState.getStage(station.furniture));
        env.templates.remove("roasted_cocoa_beans");
        FurnitureBreakEvent event = new FurnitureBreakEvent(station.furniture, env.player);
        handler.onBreak(event);
        assertTrue(event.isCancelled());
    }

    // ---------- a second recipe on the same stone: sugar cane into sugar ----------
    MillingRecipe sugar(String refund) {
        MillingRecipe flour = new MillingRecipe("flour", "milling_stone", 8, "wheat", "food:grain", "v.wheat", "flour", 1, 8);
        MillingRecipe sugar = new MillingRecipe("sugar", "milling_stone", 8, null, null, "v.sugar_cane", "sweetener_1", 1, 8,
                "Sugar", "sweetener", "sugar cane", "v.sugar_cane", "ia.tfmc_cooking:sugar_pile", refund, 8);
        Map<String, MillingRecipe> both = new LinkedHashMap<>();
        both.put("flour", flour); both.put("sugar", sugar);
        MillingRecipeRegistry.load(both);
        env.templates.put("sweetener_1", env.food("sweetener_1", "Sugar", 3));
        when(env.api.getChecker().checkItemWithPath(any(), eq("v.sugar_cane"))).thenAnswer(inv -> {
            ItemStack stack = inv.getArgument(0);
            return stack != null && stack.getType() == Material.SUGAR_CANE;
        });
        when(env.api.getCreator().getItemFromPath("v.sugar_cane")).thenAnswer(inv -> new ItemStack(Material.SUGAR_CANE));
        when(env.api.getCreator().getItemFromPath("ia.tfmc_cooking:sugar_pile")).thenAnswer(inv -> new ItemStack(Material.SUGAR));
        return sugar;
    }

    @Test
    void sugarCaneLoadsTheSecondRecipeAndGrindsIntoSugarOneForEachCane() {
        sugar("v.sugar_cane");
        ItemStack few = env.hold(new ItemStack(Material.SUGAR_CANE, 3));
        handler.onInteract(station.interact());
        assertEquals(3, few.getAmount());
        assertTrue(env.messages.contains("§cYou need 8 sugar cane to fill the mill."));
        ItemStack cane = env.hold(new ItemStack(Material.SUGAR_CANE, 9));
        handler.onInteract(station.interact());
        assertEquals(1, cane.getAmount());
        assertEquals("sugar", MillingStoneState.getRecipe(station.furniture));
        assertEquals(IngredientLineage.ofMain("Sugar"), MillingStoneState.getLineage(station.furniture));
        for (String slot : MillingStoneSlots.WHEAT) assertEquals(Material.SUGAR_CANE, station.active.get(slot).getCurrentItem().getType());
        env.hold(null); handler.onInteract(station.interact());
        env.server.getScheduler().performTicks(10);
        assertEquals(MillingStoneStage.READY, MillingStoneState.getStage(station.furniture));
        assertEquals(Material.SUGAR, station.active.get("flour").getCurrentItem().getType());
        // A reload or chunk load that loses the displays shows the sugar pile again, not flour.
        MillingStoneDisplay.clearAll(station.furniture);
        handler.onInteract(station.interact());
        assertEquals("sweetener_1", env.lastBuilt.getId());
        assertEquals("sweetener", env.lastBuilt.getCategory()); assertEquals("Sugar", env.lastBuilt.getOrigin());
        assertEquals(3, env.lastQuality);
        ItemStack given = null;
        for (ItemStack stack : env.player.getInventory().getStorageContents()) {
            FoodItem food = env.resolve(stack);
            if (food != null && food.getId().equals("sweetener_1")) given = stack;
        }
        assertNotNull(given); assertEquals(8, given.getAmount());
        assertTrue(station.variables.isEmpty());
    }

    @Test
    void wheatStillLoadsTheFirstRecipeOnAStoneThatAlsoGrindsSugar() {
        sugar(null);
        loaded();
        assertEquals("flour", MillingStoneState.getRecipe(station.furniture));
        for (String slot : MillingStoneSlots.WHEAT) assertEquals(Material.WHEAT, station.active.get(slot).getCurrentItem().getType());
    }

    @Test
    void breakingALoadedStoneGivesTheCaneBackAndAMissingRefundItemKeepsIt() {
        sugar("v.sugar_cane");
        env.hold(new ItemStack(Material.SUGAR_CANE, 8)); handler.onInteract(station.interact()); env.hold(null);
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertEquals(1, env.dropped.size());
        assertEquals(Material.SUGAR_CANE, env.dropped.get(0).getType()); assertEquals(8, env.dropped.get(0).getAmount());
        env.dropped.clear();
        env.hold(new ItemStack(Material.SUGAR_CANE, 8)); handler.onInteract(station.interact()); env.hold(null);
        when(env.api.getCreator().getItemFromPath("v.sugar_cane")).thenReturn(null);
        var event = new FurnitureBreakEvent(station.furniture, env.player);
        handler.onBreak(event);
        assertTrue(event.isCancelled()); assertTrue(env.dropped.isEmpty());
    }

    @Test
    void aSugarCaneRecipeWithoutARefundGivesBackWheatFoodAsBefore() {
        sugar(null);
        env.hold(new ItemStack(Material.SUGAR_CANE, 8)); handler.onInteract(station.interact()); env.hold(null);
        handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
        assertEquals("wheat", env.resolve(env.dropped.get(0)).getId());
        assertEquals("Sugar", env.lastBuilt.getOrigin());
    }

    @Test
    void aMissingInputDisplayItemLeavesTheStoneEmptyLooking() {
        MillingRecipe recipe = sugar(null);
        when(env.api.getCreator().getItemFromPath("v.sugar_cane")).thenReturn(null);
        MillingStoneDisplay.showWheat(station.furniture, recipe);
        for (String slot : MillingStoneSlots.WHEAT) assertFalse(station.active.containsKey(slot));
    }

    @Test
    void recipeKeysDefaultAndASavedRecipeThatIsGoneFallsBackToTheFirst(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("milling.yml");
        Files.writeString(config, """
                flour:
                  furniture: milling_stone
                  output-food: flour
                  origin: ' '
                  input-display: ''
                sugar:
                  furniture: milling_stone
                  vanilla-fallback: v.sugar_cane
                  output-food: sweetener_1
                  category: sweetener
                  origin: Sugar
                  input-name: sugar cane
                  input-display: v.sugar_cane
                  output-display: ia.tfmc_cooking:sugar_pile
                  refund: v.sugar_cane
                  output-amount: 0
                """);
        new MillingRecipeLoader().load(config.toFile());
        MillingRecipe flour = MillingRecipeRegistry.getByFurnitureId("milling_stone");
        assertEquals("Wheat", flour.getOrigin()); assertEquals("grain", flour.getCategory()); assertEquals("wheat", flour.getInputName());
        assertNull(flour.getInputDisplay()); assertNull(flour.getOutputDisplay()); assertNull(flour.getRefund());
        assertEquals(1, flour.getOutputAmount()); assertEquals("wheat", flour.getRefundFood());
        MillingRecipe sugar = MillingRecipeRegistry.getForFurniture("MILLING_STONE", "sugar");
        assertEquals("Sugar", sugar.getOrigin()); assertEquals("sweetener", sugar.getCategory());
        assertEquals("sugar cane", sugar.getInputName()); assertEquals("v.sugar_cane", sugar.getInputDisplay());
        assertEquals("ia.tfmc_cooking:sugar_pile", sugar.getOutputDisplay()); assertEquals("v.sugar_cane", sugar.getRefund());
        assertEquals(1, sugar.getOutputAmount());
        assertEquals(List.of(flour, sugar), MillingRecipeRegistry.getAllForFurniture("milling_stone"));
        assertSame(flour, MillingRecipeRegistry.getForFurniture("milling_stone", "removed"));
        assertSame(flour, MillingRecipeRegistry.getForFurniture("milling_stone", null));
        assertNull(MillingRecipeRegistry.getForFurniture("chair", "sugar"));
        assertTrue(MillingRecipeRegistry.getAllForFurniture(null).isEmpty());
        assertEquals("barley", new MillingRecipe("b", "m", 8, "barley", null, null, "flour", 1, 8).getRefundFood());
        assertEquals("wheat", new MillingRecipe("b", "m", 8, " ", null, null, "flour", 1, 8).getRefundFood());
        station.variables.put(MillingStoneState.VAR_RECIPE, 7);
        assertNull(MillingStoneState.getRecipe(station.furniture));
    }

    @Test
    void theShippedRecipesGrindWheatIntoFlourAndSugarCaneIntoSugar() {
        new MillingRecipeLoader().load(new java.io.File("src/main/resources/milling-recipes.yml"));
        List<MillingRecipe> recipes = MillingRecipeRegistry.getAllForFurniture("milling_stone");
        assertEquals(List.of("flour", "sugar", "cocoa_powder"), recipes.stream().map(MillingRecipe::getId).toList());
        MillingRecipe sugar = recipes.get(1);
        assertEquals("v.sugar_cane", sugar.getVanillaFallback()); assertEquals("sweetener_1", sugar.getOutputFood());
        assertEquals("Sugar", sugar.getOrigin()); assertEquals("sweetener", sugar.getCategory());
        assertEquals(8, sugar.getInputCount()); assertEquals(8, sugar.getOutputAmount());
        // Two roasted (not burnt) cocoa beans, one pan's worth, grind into one cocoa powder; a broken stone gives them back.
        MillingRecipe cocoa = recipes.get(2);
        assertEquals("c.ingredient(type=roasted_cocoa_beans;tags=cooked.1)", cocoa.getInputMatcher());
        assertEquals("ingredient(type=roasted_cocoa_beans;tags=cooked.1)", cocoa.getRefundPath());
        assertEquals("cocoa_powder", cocoa.getOutputFood()); assertEquals("ingredient", cocoa.getCategory());
        assertEquals("Cocoa", cocoa.getOrigin()); assertEquals(2, cocoa.getInputCount()); assertEquals(1, cocoa.getOutputAmount());
    }
}
