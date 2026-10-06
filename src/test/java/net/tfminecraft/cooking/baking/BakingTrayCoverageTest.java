package net.tfminecraft.cooking.baking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.WarmthUtils;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedFurnitureSlot;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public class BakingTrayCoverageTest {
    Bakery bakery;
    Workshop env;
    Station tray;
    BakingTrayHandler handler;
    @BeforeEach void open() { bakery = new Bakery(); env = bakery.env; tray = bakery.tray; handler = new BakingTrayHandler(); }
    @AfterEach void close() { bakery.close(); }

    @Test
    void recipesExposeMoldsFillAndBakingSettingsAndRegistryMatchesIgnoringCase() {
        BakingTrayRecipe recipe = bakery.recipe;
        assertEquals("bread", recipe.getId()); assertEquals("bread_tray", recipe.getFurnitureId());
        assertEquals(List.of("left", "right"), recipe.getMoldIds());
        assertEquals(List.of("left_a", "left_b", "right_a", "right_b"), recipe.getAllSlotIds());
        assertEquals("left", recipe.getMoldForSlot("LEFT_B")); assertNull(recipe.getMoldForSlot("missing"));
        assertEquals(List.of("right_a", "right_b"), recipe.getMoldSlotIds("RIGHT")); assertTrue(recipe.getMoldSlotIds("missing").isEmpty());
        assertEquals(2, recipe.getMolds().size());
        BakingTrayFill fill = recipe.getFill();
        assertEquals("food:dough", fill.getInput()); assertEquals("dough", fill.getInputFood()); assertEquals(2, fill.getOutputsPerMold());
        assertEquals("bread", fill.getFood()); assertEquals("grain", fill.getCategory()); assertEquals("cooked.0:freshness.0", fill.getTags());
        assertEquals("grain(type=bread;tags=cooked.0:freshness.0)", fill.buildSpawnString());
        assertEquals(2, recipe.getBake().getCookSeconds()); assertEquals(4, recipe.getBake().getBurnSeconds()); assertFalse(recipe.getBake().isSync());
        assertSame(recipe, BakingTrayRegistry.getById("BREAD")); assertSame(recipe, BakingTrayRegistry.getByFurniture(tray.furniture));
        assertTrue(BakingTrayRegistry.isTray(tray.furniture)); assertFalse(BakingTrayRegistry.isTray(bakery.station("chair").furniture));
        assertNull(BakingTrayRegistry.getById(null)); assertNull(BakingTrayRegistry.getByFurniture(null));
        assertEquals(1, BakingTrayRegistry.getAll().size());
    }

    @Test
    void loaderReadsDefaultsAndExplicitBakingAndSkipsIncompleteEntries(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("baking.yml");
        Files.writeString(file, """
                scalar: ignored
                missing-furniture:
                  category: grain
                blank-furniture:
                  furniture: ' '
                missing-molds:
                  furniture: tray
                empty-molds:
                  furniture: tray
                  molds:
                    empty: []
                missing-fill:
                  furniture: tray
                  molds:
                    mold: [a]
                missing-input:
                  furniture: tray
                  molds:
                    mold: [a]
                  fill:
                    food: bread
                missing-output:
                  furniture: tray
                  molds:
                    mold: [a]
                  fill:
                    input: food:dough
                defaults:
                  furniture: default_tray
                  molds:
                    mold: [a, b]
                  fill:
                    input: food:dough
                    food: bread
                explicit:
                  furniture: custom_tray
                  category: baked
                  molds:
                    mold: [a]
                  fill:
                    input: food:dough
                    input-food: dough
                    food: roll
                    outputs-per-mold: 1
                    tags: cooked.0
                  bake:
                    cook-seconds: 7
                    burn-seconds: 14
                    sync: false
                """);
        new BakingTrayLoader().load(file.toFile());
        assertEquals(2, BakingTrayRegistry.getAll().size());
        BakingTrayRecipe defaults = BakingTrayRegistry.getById("defaults");
        assertEquals(45, defaults.getBake().getCookSeconds()); assertEquals(90, defaults.getBake().getBurnSeconds()); assertTrue(defaults.getBake().isSync());
        assertEquals("grain", defaults.getFill().getCategory()); assertEquals(2, defaults.getFill().getOutputsPerMold());
        BakingTrayRecipe explicit = BakingTrayRegistry.getById("explicit");
        assertEquals(7, explicit.getBake().getCookSeconds()); assertEquals(14, explicit.getBake().getBurnSeconds()); assertFalse(explicit.getBake().isSync());
        assertEquals("dough", explicit.getFill().getInputFood()); assertEquals("roll", explicit.getFill().getFood());
        assertEquals("baked", explicit.getFill().getCategory()); assertEquals(1, explicit.getFill().getOutputsPerMold());
    }

    @Test
    void unreadableRecipeFilesRemoveOldDefinitionsAndReportTheFailure(@TempDir Path directory) {
        PrintStream before = System.err; ByteArrayOutputStream error = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(error)) {
            System.setErr(capture); new BakingTrayLoader().load(directory.resolve("missing.yml").toFile());
        } finally { System.setErr(before); }
        assertTrue(BakingTrayRegistry.getAll().isEmpty()); assertTrue(error.toString().contains("missing.yml"));
    }

    @Test
    void bakingClocksAreIndependentAndCleanupPreservesOtherFurnitureVariables() {
        assertEquals(0, BakingTrayState.getSlotElapsed(tray.furniture, "left_a"));
        tray.variables.put("bake.slot.left_a.elapsed", "invalid");
        assertEquals(0, BakingTrayState.getSlotElapsed(tray.furniture, "left_a"));
        BakingTrayState.setSlotElapsed(tray.furniture, "left_a", 4); BakingTrayState.incrementSlotElapsed(tray.furniture, "left_a");
        BakingTrayState.setSlotElapsed(tray.furniture, "right_a", 2);
        assertEquals(5, BakingTrayState.getSlotElapsed(tray.furniture, "left_a")); assertEquals(2, BakingTrayState.getSlotElapsed(tray.furniture, "right_a"));
        BakingTrayState.clearSlots(tray.furniture, List.of("left_a")); assertEquals(0, BakingTrayState.getSlotElapsed(tray.furniture, "left_a"));
        for (String old : List.of("bake.elapsed", "bake.cook_applied", "bake.burn_applied")) tray.variables.put(old, 1);
        tray.variables.put("unrelated", 3); BakingTrayState.clear(tray.furniture);
        assertEquals(Map.of("unrelated", 3), tray.variables);
    }

    @Test
    void doughTransformationPreservesIngredientsLineageAndAgingButResetsCookingAndRising() {
        FoodItem dough = bakery.food("dough", null); dough.setOrigin("Rye"); dough.setCategory("baked");
        dough.setLineage(IngredientLineage.ofMain("Rye")); dough.addIngredient("Walnut");
        bakery.track(dough, "cooked", 2); bakery.track(dough, "rising", 1); bakery.track(dough, "freshness", 4);
        BakingTrayFill fill = new BakingTrayFill("food:dough", "dough", 2, "bread", "cooked.0:freshness.6:unknown.3:bad:cooked.nope", "grain");
        FoodItem loaf = BakingTrayTransform.doughToLoaf(dough, fill);
        assertEquals("bread", loaf.getId()); assertEquals("baked", loaf.getCategory()); assertEquals("Rye", loaf.getOrigin());
        assertEquals(dough.getEffectiveQuality(), loaf.getQualityMin()); assertEquals(dough.getLineage(), loaf.getLineage());
        assertTrue(loaf.getIngredients().contains("Walnut")); assertEquals(0, loaf.getTagTrack("cooked").getValue());
        assertEquals(6, loaf.getTagTrack("freshness").getValue()); assertFalse(loaf.hasTagTrack("rising"));
        assertEquals(2, dough.getTagTrack("cooked").getValue());
        assertNull(BakingTrayTransform.doughToLoaf(null, fill)); assertNull(BakingTrayTransform.doughToLoaf(dough, null));
        env.templates.remove("bread"); assertNull(BakingTrayTransform.doughToLoaf(dough, fill));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "NULL"})
    void missingDoughCategoryAndTagSpecUseTheFillAndTemplateDefaults(String value) {
        FoodItem dough = bakery.food("dough", null); dough.setCategory(value.equals("NULL") ? null : value); dough.setOrigin(null);
        BakingTrayFill fill = new BakingTrayFill(null, "dough", 2, "bread", value.equals("NULL") ? null : value, "grain");
        FoodItem loaf = BakingTrayTransform.doughToLoaf(dough, fill);
        assertEquals("grain", loaf.getCategory()); assertEquals("Wheat", loaf.getOrigin()); assertFalse(loaf.hasTagTrack("cooked"));
    }

    @Test
    void slotSelectionUsesClickDistanceAndFindsTheNextAvailableSlotOfTheRequestedKind() {
        env.display(tray.id);
        assertNull(BakingTraySlots.findClosestSlot(null, bakery.recipe, new Vector()));
        assertNull(BakingTraySlots.findClosestSlot(tray.furniture, null, new Vector()));
        assertEquals("right_b", BakingTraySlots.findClosestSlot(tray.furniture, bakery.recipe, bakery.click(3)));
        tray.place("right_b", bakery.loaf(0, 1)); tray.place("left_a", bakery.loaf(0, 1));
        assertEquals("right_b", BakingTraySlots.findClosestOccupiedSlot(tray.furniture, bakery.recipe, bakery.click(3)));
        assertEquals("right_a", BakingTraySlots.findClosestEmptySlot(tray.furniture, bakery.recipe, bakery.click(3)));
        assertEquals("left_a", BakingTraySlots.findClosestOccupiedSlot(tray.furniture, bakery.recipe, bakery.click(1)));
        assertEquals("left_b", BakingTraySlots.findClosestEmptySlot(tray.furniture, bakery.recipe, bakery.click(1)));
        assertFalse(BakingTraySlots.isMoldEmpty(tray.furniture, bakery.recipe, "left"));
        assertNull(BakingTraySlots.findFillableMold(tray.furniture, bakery.recipe, "right"));
        tray.active.clear();
        assertEquals("right", BakingTraySlots.findFillableMold(tray.furniture, bakery.recipe, "right"));
        assertEquals("left", BakingTraySlots.findFirstFillableMold(tray.furniture, bakery.recipe));
    }

    @Test
    void selectionFallbacksHandleMissingDisplaysDefinitionsAndEmptyMolds() {
        assertEquals("left_a", BakingTraySlots.findClosestSlot(tray.furniture, bakery.recipe, null));
        assertEquals("left_a", BakingTraySlots.findClosestSlot(tray.furniture, bakery.recipe, bakery.click(3)));
        assertNull(BakingTraySlots.findClosestOccupiedSlot(tray.furniture, bakery.recipe, null));
        tray.place("left_a", new ItemStack(Material.AIR)); tray.place("left_b", null);
        assertEquals("left", BakingTraySlots.findFirstFillableMold(tray.furniture, bakery.recipe));
        for (String slot : bakery.recipe.getAllSlotIds()) tray.place(slot, bakery.loaf(0, 1));
        assertEquals("left_a", BakingTraySlots.findClosestOccupiedSlot(tray.furniture, bakery.recipe, null));
        assertNull(BakingTraySlots.findClosestEmptySlot(tray.furniture, bakery.recipe, null));
        assertNull(BakingTraySlots.findFirstFillableMold(tray.furniture, bakery.recipe));
        env.display(tray.id); tray.definitions.clear();
        assertEquals("left_a", BakingTraySlots.findClosestSlot(tray.furniture, bakery.recipe, bakery.click(0)));
        assertEquals("left_a", BakingTraySlots.findClosestSlot(tray.furniture, bakery.recipe, null));
        tray.active.clear();
        assertNull(BakingTraySlots.findClosestOccupiedSlot(tray.furniture, bakery.recipe, bakery.click(0)));
        when(tray.furniture.getType()).thenReturn(null);
        assertEquals("left_a", BakingTraySlots.findClosestEmptySlot(tray.furniture, bakery.recipe, bakery.click(0)));
        BakingTrayRecipe noSlots = bakery.recipe(Map.of(), bakery.recipe.getFill());
        assertNull(BakingTraySlots.findClosestSlot(tray.furniture, noSlots, null));
        BakingTrayRecipe emptyMold = bakery.recipe(Map.of("empty", List.of()), bakery.recipe.getFill());
        assertNull(BakingTraySlots.findClosestSlot(tray.furniture, emptyMold, null));
    }

    @Test
    void slotSelectionSkipsMissingDefinitionsAndUsesAnotherEmptyMold() {
        env.display(tray.id);
        tray.place("left_a", bakery.loaf(0, 1)); tray.place("right_b", bakery.loaf(0, 1));
        tray.definitions.remove("right_b");
        assertEquals("left_a", BakingTraySlots.findClosestOccupiedSlot(tray.furniture, bakery.recipe, bakery.click(1)));
        tray.definitions.remove("left_b");
        assertEquals("right_a", BakingTraySlots.findClosestEmptySlot(tray.furniture, bakery.recipe, bakery.click(0)));
        tray.active.remove("right_b");
        assertEquals("right", BakingTraySlots.findFillableMold(tray.furniture, bakery.recipe, "left"));
    }

    @Test
    void recipesWithoutAnInputMatcherDoNotTreatUnrelatedItemsAsDough() {
        bakery.register(bakery.recipe(bakery.recipe.getMolds(), new BakingTrayFill(null, null, 2, "bread", null, "grain")));
        ItemStack hand = env.hold(new ItemStack(Material.STICK));
        var event = bakery.interact(null); handler.onInteract(event);
        assertFalse(event.isCancelled()); assertEquals(1, hand.getAmount()); assertTrue(tray.active.isEmpty());
    }

    @Test
    void fillingAMoldConsumesOneDoughBuildsIndividualLoavesAndClearsTheirClocks() {
        ItemStack hand = env.hold(env.stack(bakery.food("dough", null), Material.WHEAT, 2));
        BakingTrayState.setSlotElapsed(tray.furniture, "left_a", 7); BakingTrayState.setSlotElapsed(tray.furniture, "right_a", 9);
        var event = bakery.interact(null); handler.onInteract(event);
        assertTrue(event.isCancelled()); assertEquals(1, hand.getAmount());
        assertEquals(2, tray.active.size());
        ItemStack first = tray.active.get("left_a").getCurrentItem(), second = tray.active.get("left_b").getCurrentItem();
        assertNotSame(first, second); assertEquals(1, first.getAmount()); assertEquals("bread", env.resolve(first).getId());
        assertEquals(0, env.resolve(first).getTagTrack("cooked").getValue());
        assertEquals(0, BakingTrayState.getSlotElapsed(tray.furniture, "left_a"));
        assertEquals(9, BakingTrayState.getSlotElapsed(tray.furniture, "right_a"));
        verify(env.manager).markDirty(tray.furniture);
        handler.onInteract(bakery.interact(null));
        assertEquals(4, tray.active.size());
        env.hold(env.stack(bakery.food("dough", null), Material.WHEAT, 1)); handler.onInteract(bakery.interact(null));
        assertTrue(env.messages.contains("§cThe tray molds are full."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unreadable-dough", "missing-template", "null-loaf", "nonfood-loaf"})
    void fillingRejectsUnreadableOrUnbuildableFoodsWithoutTakingTheDough(String failure) {
        ItemStack plain = new ItemStack(Material.WHEAT, 2);
        ItemStack hand = env.hold(failure.equals("unreadable-dough") ? plain : env.stack(bakery.food("dough", null), Material.WHEAT, 2));
        if (failure.equals("missing-template")) env.templates.remove("bread");
        if (failure.equals("null-loaf")) env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenReturn(null);
        ItemStack stone = new ItemStack(Material.STONE);
        if (failure.equals("nonfood-loaf")) env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenReturn(stone);
        var event = bakery.interact(null); handler.onInteract(event);
        assertTrue(event.isCancelled()); assertEquals(2, hand.getAmount()); assertTrue(tray.active.isEmpty());
    }

    @Test
    void attachedTraysRejectNewDoughButAllowSingleLoavesAndRespectSneaking() {
        when(tray.furniture.isAttached()).thenReturn(true);
        ItemStack dough = env.hold(env.stack(bakery.food("dough", null), Material.WHEAT, 1));
        var fill = bakery.interact(null); handler.onInteract(fill); assertTrue(fill.isCancelled()); assertEquals(1, dough.getAmount());
        assertTrue(env.messages.contains("§cRemove the tray from the oven before adding dough."));
        env.hold(bakery.loaf(0, 2));
        var place = bakery.interact(null); handler.onInteract(place); assertTrue(place.isCancelled());
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount()); assertEquals(1, tray.active.size());
        env.hold(null); when(env.player.isSneaking()).thenReturn(true);
        var sneaking = bakery.interact(null); handler.onInteract(sneaking); assertFalse(sneaking.isCancelled()); assertEquals(1, tray.active.size());
        when(env.player.isSneaking()).thenReturn(false);
        var take = bakery.interact(null); handler.onInteract(take); assertTrue(take.isCancelled()); assertTrue(tray.active.isEmpty());
        env.hold(new ItemStack(Material.STICK)); handler.onInteract(bakery.interact(null)); assertTrue(tray.active.isEmpty());
    }

    @Test
    void looseLoavesFillIndividualSlotsAndFullTraysKeepTheHeldItem() {
        ItemStack hand = env.hold(bakery.loaf(0, 5));
        for (int n = 0; n < 4; n++) handler.onInteract(bakery.interact(null));
        assertEquals(1, hand.getAmount()); assertEquals(4, tray.active.size());
        handler.onInteract(bakery.interact(null)); assertEquals(1, hand.getAmount()); assertTrue(env.messages.contains("§cThe tray is full."));
        Station other = bakery.station("chair"); var ignored = other.interact(); handler.onInteract(ignored); assertFalse(ignored.isCancelled());
        env.hold(env.stack(bakery.food("other", null), Material.PAPER, 1));
        var unrelated = bakery.interact(null); handler.onInteract(unrelated); assertFalse(unrelated.isCancelled());
    }

    @Test
    void takingLoavesReturnsOneAtATimeAndHeatedBreadGetsWarmth() {
        ItemStack raw = bakery.loaf(0, 2); tray.place("left_a", raw); BakingTrayState.setSlotElapsed(tray.furniture, "left_a", 1);
        var first = bakery.interact(null); handler.onInteract(first); assertTrue(first.isCancelled());
        assertEquals(1, raw.getAmount()); assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, BakingTrayState.getSlotElapsed(tray.furniture, "left_a"));
        env.hold(null); handler.onInteract(bakery.interact(null)); assertFalse(tray.active.containsKey("left_a"));
        assertEquals(0, BakingTrayState.getSlotElapsed(tray.furniture, "left_a"));
        env.hold(null); ItemStack cooked = bakery.loaf(1, 1); tray.place("left_b", cooked);
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(eq(env.player), any())).thenAnswer(inv -> inv.getArgument(1));
            handler.onInteract(bakery.interact(null));
        }
        assertEquals(1, env.dropped.size()); assertTrue(env.resolve(env.dropped.get(0)).hasTagTrack("warmth"));
        assertFalse(tray.active.containsKey("left_b"));
    }

    @Test
    void takingSkipsUnrecognizedFoodFailedUpdatesAndSneakingWithoutMutation() {
        var empty = bakery.interact(null); handler.onInteract(empty); assertFalse(empty.isCancelled());
        when(env.player.isSneaking()).thenReturn(true); handler.onInteract(bakery.interact(null));
        when(env.player.isSneaking()).thenReturn(false);
        PlacedSlot plain = tray.place("left_a", new ItemStack(Material.STONE)); handler.onInteract(bakery.interact(null));
        verify(plain, never()).clearModel();
        ItemStack cooked = bakery.loaf(1, 1); PlacedSlot slot = tray.place("left_a", cooked);
        bakery.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        var rejected = bakery.interact(null); handler.onInteract(rejected); assertFalse(rejected.isCancelled());
        assertSame(cooked, slot.getCurrentItem()); verify(slot, never()).clearModel();
    }

    @Test
    void cookingAndBurningChangeOnlyLoavesAtTheExpectedStage() {
        assertFalse(BakingTrayBakeApplier.applyCook(tray.furniture, bakery.recipe));
        tray.place("left_a", bakery.loaf(0, 1)); tray.place("left_b", bakery.loaf(1, 1));
        tray.place("right_a", bakery.loaf(2, 1)); tray.place("right_b", new ItemStack(Material.STONE));
        assertTrue(BakingTrayBakeApplier.applyCook(tray.furniture, bakery.recipe));
        assertEquals(1, env.resolve(tray.active.get("left_a").getCurrentItem()).getTagTrack("cooked").getValue());
        assertFalse(BakingTrayBakeApplier.applyCook(tray.furniture, bakery.recipe));
        assertTrue(BakingTrayBakeApplier.applyBurn(tray.furniture, bakery.recipe));
        assertEquals(2, env.resolve(tray.active.get("left_b").getCurrentItem()).getTagTrack("cooked").getValue());
        assertFalse(BakingTrayBakeApplier.applyBurnSlot(tray.furniture, "right_a"));
        tray.place("left_a", null); assertFalse(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a"));
        tray.place("left_a", new ItemStack(Material.AIR)); assertFalse(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a"));
    }

    @Test
    void firstCookingTransitionPersistsANewCookedTrackOnUntaggedBread() {
        FoodItem bread = bakery.food("bread", null); tray.place("left_a", env.stack(bread, Material.BREAD, 1));
        assertTrue(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a"));
        FoodItem cooked = env.resolve(tray.active.get("left_a").getCurrentItem());
        assertEquals(1, cooked.getTagTrack("cooked").getValue(), "The stored track, not a detached copy, must be advanced");
    }

    @Test
    void unavailableCookingTracksAndFailedRenderingPreserveTheDisplayedLoaf() {
        ItemStack bread = env.stack(bakery.food("bread", null), Material.BREAD, 1); PlacedSlot slot = tray.place("left_a", bread);
        TrackLoader.oList.removeIf(track -> track.getId().equals("cooked"));
        assertFalse(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a")); assertSame(bread, slot.getCurrentItem());
        bakery.track(env.resolve(bread), "freshness", 0);
        FoodItem tagged = bakery.food("bread", null);
        tagged.addOrModifyTrack(new TagTrack("cooked", false, List.of()));
        ItemStack raw = env.stack(tagged, Material.BREAD, 1); slot = tray.place("left_a", raw);
        bakery.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        assertFalse(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a")); assertSame(raw, slot.getCurrentItem());
        verify(slot, never()).forceModel(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"take", "cook", "age"})
    void airRenderingResultsNeverConsumeOrReplaceBread(String operation) {
        ItemStack original = bakery.loaf(operation.equals("cook") ? 0 : 1, 1);
        PlacedSlot slot = tray.place("left_a", original);
        ItemStack invalid = new ItemStack(Material.AIR);
        bakery.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(invalid);
        bakery.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(invalid);
        if (operation.equals("take")) {
            var event = bakery.interact(null); handler.onInteract(event);
            assertFalse(event.isCancelled());
        } else if (operation.equals("cook")) {
            assertFalse(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a"));
        } else {
            new BakingTrayAging().start(); env.server.getScheduler().performTicks(20);
        }
        assertSame(original, slot.getCurrentItem()); assertSame(slot, tray.active.get("left_a"));
        verify(slot, never()).clearModel(); verify(slot, never()).forceModel(any());
        verify(env.manager, never()).markDirty(tray.furniture);
    }

    @Test
    void agingVisitsLooseAndNestedTraysOnceAndIgnoresUnsupportedContents() {
        Station consumer = bakery.consumer();
        when(tray.furniture.isAttached()).thenReturn(true); bakery.nest(consumer, "tray", tray.furniture);
        bakery.nest(consumer, "empty", null); bakery.nest(consumer, "chair", bakery.station("chair").furniture);
        Station loose = bakery.station("bread_tray"); bakery.defineTraySlots(loose);
        ItemStack bread = bakery.loaf(0, 1); PlacedSlot nested = tray.place("left_a", bread);
        PlacedSlot separate = loose.place("right_a", bakery.loaf(0, 1));
        tray.place("left_b", null); tray.place("right_a", new ItemStack(Material.AIR)); tray.place("right_b", new ItemStack(Material.STONE));
        bakery.placed.put(UUID.randomUUID(), loose.furniture);
        new BakingTrayAging().start(); env.server.getScheduler().performTicks(20);
        verify(nested, times(1)).forceModel(any()); verify(separate, times(1)).forceModel(any());
        verify(env.manager).markDirty(tray.furniture); verify(env.manager).markDirty(loose.furniture);
        bakery.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(null);
        env.server.getScheduler().performTicks(20);
        verify(nested, times(1)).forceModel(any()); verify(separate, times(1)).forceModel(any());
    }

    @Test
    void emptyEntriesInThePublicSlotMapDoNotPreventOtherLoavesFromAging() {
        tray.furniture.getActiveSlots().put("left_a", null);
        PlacedSlot bread = tray.place("left_b", bakery.loaf(0, 1));
        assertFalse(BakingTrayBakeApplier.applyCookSlot(tray.furniture, "left_a"));
        new BakingTrayAging().start(); env.server.getScheduler().performTicks(20);
        assertNull(tray.furniture.getActiveSlots().get("left_a"));
        verify(bread).forceModel(any()); verify(env.manager).markDirty(tray.furniture);
    }

    /** Reuses processor item boundaries and adds tray definitions, attachment state and the heat service. */
    public static final class Bakery implements AutoCloseable {
        public final Workshop env = new Workshop();
        public final Map<UUID, Furniture> placed = new LinkedHashMap<>();
        public final Set<Furniture> consumers = new HashSet<>();
        public final Map<Furniture, Map<String, PlacedFurnitureSlot>> nested = new LinkedHashMap<>();
        public final MockedStatic<ItemUpdater> updater;
        public final MockedStatic<HeatSources> heat;
        public final Station tray;
        public BakingTrayRecipe recipe;
        public boolean heated = true;
        final Map<String, BakingTrayRecipe> previous = new LinkedHashMap<>();
        public Bakery() {
            for (BakingTrayRecipe old : BakingTrayRegistry.getAll()) previous.put(old.getId(), old);
            when(env.manager.getPlacedFurniture()).thenReturn(placed);
            env.templates.put("bread", food("bread", null)); env.templates.put("dough", food("dough", null));
            when(env.api.getChecker().checkItemWithPath(any(), eq("food:dough"))).thenAnswer(inv -> {
                ItemStack stack = inv.getArgument(0); return stack != null && stack.getType() == Material.WHEAT;
            });
            updater = env.scoped(mockStatic(ItemUpdater.class));
            updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(inv -> updated(inv.getArgument(0), inv.getArgument(1)));
            updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenAnswer(inv -> updated(inv.getArgument(0), inv.getArgument(1)));
            heat = env.scoped(mockStatic(HeatSources.class));
            heat.when(() -> HeatSources.isConsumer(any())).thenAnswer(inv -> consumers.contains(inv.getArgument(0)));
            heat.when(() -> HeatSources.consumerHasHeat(any())).thenAnswer(inv -> heated);
            tray = station("bread_tray"); defineTraySlots(tray);
            Map<String, List<String>> molds = new LinkedHashMap<>();
            molds.put("left", List.of("left_a", "left_b")); molds.put("right", List.of("right_a", "right_b"));
            recipe = recipe(molds, new BakingTrayFill("food:dough", "dough", 2, "bread", "cooked.0:freshness.0", "grain"));
            register(recipe);
        }
        ItemStack updated(ItemStack source, FoodItem food) { return env.stack(food, source.getType(), source.getAmount()); }
        public BakingTrayRecipe recipe(Map<String, List<String>> molds, BakingTrayFill fill) {
            return new BakingTrayRecipe("bread", "bread_tray", molds, fill, new BakingTrayBake(2, 4, false));
        }
        public void register(BakingTrayRecipe recipe) { BakingTrayRegistry.load(Map.of(recipe.getId(), recipe)); this.recipe = recipe; }
        public Station station(String id) {
            Station station = env.station(id); placed.put(station.id, station.furniture);
            Map<String, PlacedFurnitureSlot> attachments = new LinkedHashMap<>(); nested.put(station.furniture, attachments);
            when(station.furniture.getActiveFurnitureSlots()).thenReturn(attachments);
            return station;
        }
        public Station consumer() { Station station = station("oven_top"); consumers.add(station.furniture); return station; }
        public void nest(Station parent, String id, Furniture child) {
            PlacedFurnitureSlot slot = mock(PlacedFurnitureSlot.class); when(slot.getNested()).thenReturn(child);
            nested.get(parent.furniture).put(id, slot);
        }
        public void defineTraySlots(Station station) {
            int index = 0;
            for (String id : List.of("left_a", "left_b", "right_a", "right_b")) {
                var definition = station.define(id); Location location = new Location(env.world, index++ * 2, 64, 1);
                when(definition.computeDisplayLocation(any(), any(), any())).thenReturn(location);
            }
        }
        public FoodItem food(String id, Integer cooked) {
            FoodItem food = env.food(id, "Wheat", 4); food.setCategory("grain");
            if (cooked != null) track(food, "cooked", cooked);
            return food;
        }
        public void track(FoodItem food, String id, int value) {
            TagTrack track = new TagTrack(TrackLoader.getByString(id)); track.setValue(value); food.addOrModifyTrack(track);
        }
        public ItemStack loaf(int cooked, int amount) { return env.stack(food("bread", cooked), Material.BREAD, amount); }
        public Vector click(int slot) { return new Vector(slot * 2, 64, 1); }
        public FurnitureInteractEvent interact(Vector click) { return new FurnitureInteractEvent(env.player, tray.furniture, null, click); }
        @Override public void close() {
            env.server.getScheduler().cancelTasks(Cooking.plugin);
            BakingTrayRegistry.load(previous); env.close();
        }
    }
}
