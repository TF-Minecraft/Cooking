package net.tfminecraft.cooking.mixing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.churn.ButterChurnCoverageTest.KitchenFixture;
import net.tfminecraft.cooking.churn.ButterChurnCoverageTest.Station;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.entity.ItemDisplayMock;

class MixingBowlCoverageTest {
    private KitchenFixture e;
    private Station s;
    private MixingBowlHandler handler;

    @BeforeEach void setUp() {
        e = new KitchenFixture(); s = e.station("bowl"); handler = new MixingBowlHandler();
        for (String slot : List.of("flour", "water", "yeast", "dough")) s.define(slot);
        var naming = e.scoped(NamingConfig.class);
        naming.when(() -> NamingConfig.isCategory(anyString(), any())).thenAnswer(call -> {
            String bucket = call.getArgument(0), category = call.getArgument(1);
            return "sweetener".equals(bucket) ? "sweetener".equals(category) : "dough-filler".equals(bucket) && "fruit".equals(category);
        });
        naming.when(NamingConfig::getDoughFillerMax).thenReturn(2);
    }

    @AfterEach void tearDown() { e.close(); }

    @Test void emptyPersistedStateHasSafeDefaultsAndStagesFollowVisibleLayers() {
        assertEquals(MixingBowlStage.EMPTY, MixingBowlState.getStage(s.f));
        assertEquals(0, MixingBowlState.getMixCount(s.f));
        assertNull(MixingBowlState.getFlourQuality(s.f)); assertNull(MixingBowlState.getYeastQuality(s.f));
        assertNull(MixingBowlState.getSugarQuality(s.f)); assertFalse(MixingBowlState.hasSugar(s.f));
        assertNull(MixingBowlState.getFlourFreshness(s.f)); assertNull(MixingBowlState.getYeastFreshness(s.f)); assertNull(MixingBowlState.getSugarFreshness(s.f));
        assertTrue(MixingBowlState.getFruitOrigins(s.f).isEmpty()); assertTrue(MixingBowlState.getFruitQualities(s.f).isEmpty());
        assertTrue(MixingBowlState.getFruitFreshness(s.f).isEmpty()); assertTrue(MixingBowlState.getFruitLineages(s.f).isEmpty());
        assertEquals("Wheat", MixingBowlState.getFlourOrigin(s.f)); assertEquals("Yeast", MixingBowlState.getYeastOrigin(s.f)); assertEquals("Sugar", MixingBowlState.getSugarOrigin(s.f));
        assertTrue(MixingBowlState.getFlourLineage(s.f).isEmpty()); assertTrue(MixingBowlState.getYeastLineage(s.f).isEmpty()); assertTrue(MixingBowlState.getSugarLineage(s.f).isEmpty());
        s.variables.put(MixingBowlState.VAR_STAGE, "removed-stage-from-old-config");
        for (var entry : Map.of("flour", MixingBowlStage.HAS_FLOUR, "water", MixingBowlStage.HAS_WATER, "yeast", MixingBowlStage.HAS_YEAST, "dough", MixingBowlStage.DOUGH_READY).entrySet()) {
            s.active.clear(); s.place(entry.getKey(), new ItemStack(Material.STONE));
            assertEquals(entry.getValue(), MixingBowlState.getStage(s.f));
        }
        assertEquals("flour", MixingBowlStage.EMPTY.nextIngredientSlot());
        assertEquals("water", MixingBowlStage.HAS_FLOUR.nextIngredientSlot());
        assertEquals("yeast", MixingBowlStage.HAS_WATER.nextIngredientSlot());
        assertNull(MixingBowlStage.HAS_YEAST.nextIngredientSlot()); assertNull(MixingBowlStage.DOUGH_READY.nextIngredientSlot());
        assertEquals(MixingBowlStage.HAS_FLOUR, MixingBowlStage.EMPTY.advance());
        assertEquals(MixingBowlStage.HAS_WATER, MixingBowlStage.HAS_FLOUR.advance());
        assertEquals(MixingBowlStage.HAS_YEAST, MixingBowlStage.HAS_WATER.advance());
        assertEquals(MixingBowlStage.HAS_YEAST, MixingBowlStage.HAS_YEAST.advance());
        assertEquals(MixingBowlStage.DOUGH_READY, MixingBowlStage.DOUGH_READY.advance());
    }

    @Test void snapshotsRoundTripEveryIngredientAndClearOnlyBowlVariables() {
        s.variables.put("unrelated", "keep");
        IngredientLineage flour = IngredientLineage.ofMain("Rye"), yeast = IngredientLineage.ofMain("Starter"), sugar = IngredientLineage.ofExtra("Honey"), apple = IngredientLineage.ofExtra("Apple");
        MixingBowlState.setStage(s.f, MixingBowlStage.HAS_YEAST); MixingBowlState.setMixCount(s.f, 2);
        MixingBowlState.setFlourQuality(s.f, 4); MixingBowlState.setFlourFreshness(s.f, 9); MixingBowlState.setFlourOrigin(s.f, "Rye"); MixingBowlState.setFlourLineage(s.f, flour);
        MixingBowlState.setYeastQuality(s.f, 3); MixingBowlState.setYeastFreshness(s.f, 8); MixingBowlState.setYeastOrigin(s.f, "Starter"); MixingBowlState.setYeastLineage(s.f, yeast);
        MixingBowlState.setHasSugar(s.f, true); MixingBowlState.setSugarQuality(s.f, 2); MixingBowlState.setSugarFreshness(s.f, 7); MixingBowlState.setSugarOrigin(s.f, "Honey"); MixingBowlState.setSugarLineage(s.f, sugar);
        MixingBowlState.addFruit(s.f, "Apple", 4, 6, apple); MixingBowlState.addFruit(s.f, "Pear", 3, 5, IngredientLineage.empty());
        assertEquals(MixingBowlStage.HAS_YEAST, MixingBowlState.getStage(s.f)); assertEquals(2, MixingBowlState.getMixCount(s.f));
        assertEquals(4, MixingBowlState.getFlourQuality(s.f)); assertEquals(9, MixingBowlState.getFlourFreshness(s.f)); assertEquals("Rye", MixingBowlState.getFlourOrigin(s.f)); assertEquals(flour, MixingBowlState.getFlourLineage(s.f));
        assertEquals(3, MixingBowlState.getYeastQuality(s.f)); assertEquals(8, MixingBowlState.getYeastFreshness(s.f)); assertEquals("Starter", MixingBowlState.getYeastOrigin(s.f)); assertEquals(yeast, MixingBowlState.getYeastLineage(s.f));
        assertTrue(MixingBowlState.hasSugar(s.f)); assertEquals(2, MixingBowlState.getSugarQuality(s.f)); assertEquals(7, MixingBowlState.getSugarFreshness(s.f)); assertEquals("Honey", MixingBowlState.getSugarOrigin(s.f)); assertEquals(sugar, MixingBowlState.getSugarLineage(s.f));
        assertEquals(List.of("Apple", "Pear"), MixingBowlState.getFruitOrigins(s.f)); assertEquals(List.of(4, 3), MixingBowlState.getFruitQualities(s.f)); assertEquals(List.of(6, 5), MixingBowlState.getFruitFreshness(s.f)); assertEquals(List.of(apple, IngredientLineage.empty()), MixingBowlState.getFruitLineages(s.f));
        MixingBowlState.clear(s.f); assertEquals(Map.of("unrelated", "keep"), s.variables);
    }

    @Test void malformedLegacyListsKeepReadableEntriesAndFallbackOrigins() {
        s.variables.put(MixingBowlState.VAR_FRUIT_ORIGINS, " :Apple::Pear:");
        s.variables.put(MixingBowlState.VAR_FRUIT_QUALITIES, ":bad:3: :4:");
        s.variables.put(MixingBowlState.VAR_FRUIT_FRESHNESS, ":bad:9: :8:");
        assertEquals(List.of("Apple", "Pear"), MixingBowlState.getFruitOrigins(s.f));
        assertEquals(List.of(3, 4), MixingBowlState.getFruitQualities(s.f)); assertEquals(List.of(9, 8), MixingBowlState.getFruitFreshness(s.f));
        s.variables.put(MixingBowlState.VAR_FRUIT_ORIGINS, " "); s.variables.put(MixingBowlState.VAR_FRUIT_QUALITIES, " "); s.variables.put(MixingBowlState.VAR_FRUIT_FRESHNESS, " "); s.variables.put(MixingBowlState.VAR_FRUIT_LINEAGE, " ");
        assertTrue(MixingBowlState.getFruitOrigins(s.f).isEmpty()); assertTrue(MixingBowlState.getFruitQualities(s.f).isEmpty()); assertTrue(MixingBowlState.getFruitFreshness(s.f).isEmpty()); assertTrue(MixingBowlState.getFruitLineages(s.f).isEmpty());
        MixingBowlState.setFlourOrigin(s.f, " "); assertEquals("Wheat", MixingBowlState.getFlourOrigin(s.f));
        MixingBowlState.setHasSugar(s.f, false); assertFalse(MixingBowlState.hasSugar(s.f));
    }

    @Test void wrongOrderAndUnpreparedIngredientsAreRejectedWithoutConsumption() {
        handler.onInteract(e.station("other").interact());
        handler.onInteract(s.interact()); assertTrue(s.active.isEmpty());
        e.hold(new ItemStack(Material.STONE, 2)); var wrong = s.interact(); handler.onInteract(wrong);
        assertTrue(wrong.isCancelled()); assertTrue(e.messages.contains("§cAdd flour first."));
        e.hold(new ItemStack(Material.WHEAT, 2)); var raw = s.interact(); handler.onInteract(raw);
        assertTrue(raw.isCancelled()); assertEquals(2, e.player.getInventory().getItemInMainHand().getAmount()); assertTrue(e.messages.getLast().contains("prepare"));
        addFlour(); e.hold(new ItemStack(Material.STONE)); handler.onInteract(s.interact()); assertTrue(e.messages.contains("§cAdd water next."));
        addWater(Material.WATER_BUCKET, 1); e.hold(new ItemStack(Material.STONE)); handler.onInteract(s.interact()); assertTrue(e.messages.contains("§cAdd yeast next."));
        e.hold(new ItemStack(Material.BROWN_MUSHROOM)); handler.onInteract(s.interact()); assertEquals(MixingBowlStage.HAS_WATER, MixingBowlState.getStage(s.f));
    }

    @Test void flourWaterAndYeastAdvanceInOrderWithQualityAndLineageSnapshots() {
        FoodItem flour = e.food("flour", "grain", "Rye", 4, 9); flour.setLineage(IngredientLineage.ofMain("Rye"));
        add(flour, Material.WHEAT); assertEquals(MixingBowlStage.HAS_FLOUR, MixingBowlState.getStage(s.f));
        assertEquals(4, MixingBowlState.getFlourQuality(s.f)); assertEquals(9, MixingBowlState.getFlourFreshness(s.f)); assertEquals("Rye", MixingBowlState.getFlourOrigin(s.f)); assertEquals(flour.getLineage(), MixingBowlState.getFlourLineage(s.f));
        addWater(Material.WATER_BUCKET, 1); assertEquals(MixingBowlStage.HAS_WATER, MixingBowlState.getStage(s.f));
        FoodItem yeast = e.food("yeast", "ingredient", "Starter", 3, 7); yeast.setLineage(IngredientLineage.ofMain("Starter"));
        add(yeast, Material.BROWN_MUSHROOM); assertEquals(MixingBowlStage.HAS_YEAST, MixingBowlState.getStage(s.f));
        assertEquals(3, MixingBowlState.getYeastQuality(s.f)); assertEquals(7, MixingBowlState.getYeastFreshness(s.f)); assertEquals("Starter", MixingBowlState.getYeastOrigin(s.f)); assertEquals(yeast.getLineage(), MixingBowlState.getYeastLineage(s.f));
        assertEquals(Set.of("flour", "water", "yeast"), s.active.keySet()); verify(e.manager, times(3)).markDirty(s.f);
    }

    @Test void waterCupsReturnTheirContainerToHandInventoryOrGroundWithoutDuplication() {
        addFlour(); addWater(Material.HONEY_BOTTLE, 1);
        assertEquals(Material.GLASS_BOTTLE, e.player.getInventory().getItemInMainHand().getType());
        resetBowl(); addFlour(); addWater(Material.HONEY_BOTTLE, 2);
        assertEquals(1, e.player.getInventory().getItemInMainHand().getAmount()); assertEquals(1, e.player.getInventory().all(Material.GLASS_BOTTLE).size());
        resetBowl(); addFlour();
        for (int slot = 0; slot < e.player.getInventory().getSize(); slot++) e.player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        addWater(Material.HONEY_BOTTLE, 2);
        verify(e.world).dropItemNaturally(eq(s.f.getLoc()), argThat(item -> item.getType() == Material.GLASS_BOTTLE && item.getAmount() == 1));
    }

    @Test void unavailableIngredientModelLeavesInventoryAndBowlUnchanged() {
        e.cache.when(() -> ItemCache.getMixingModel("flour")).thenReturn(null);
        e.hold(e.stack(e.food("flour", "grain", "Wheat", 3, 0), Material.WHEAT, 2));
        handler.onInteract(s.interact());
        assertEquals(2, e.player.getInventory().getItemInMainHand().getAmount(), "A missing configured model must not eat an ingredient");
        assertEquals(MixingBowlStage.EMPTY, MixingBowlState.getStage(s.f)); assertTrue(s.active.isEmpty()); assertTrue(s.variables.isEmpty());
    }

    @Test void aPersistedStageBehindItsVisibleLayerCannotConsumeTheSameIngredientTwice() {
        // Preserve an already loaded layer when saved stage metadata lags the visible model.
        MixingBowlState.setStage(s.f, MixingBowlStage.EMPTY);
        PlacedSlot existing = s.place("flour", new ItemStack(Material.WHEAT));
        e.hold(e.stack(e.food("flour", "grain", "Wheat", 3, 0), Material.WHEAT, 2));
        handler.onInteract(s.interact());
        assertEquals(2, e.player.getInventory().getItemInMainHand().getAmount());
        assertSame(existing, s.active.get("flour")); verify(existing, never()).forceModel(any());
    }

    @Test void unavailableDoughModelPreservesIngredientsAndCanCompleteAfterConfigurationRepair() {
        prepare(); MixingBowlState.setMixCount(s.f, ItemCache.mixingStirCount - 1);
        e.cache.when(() -> ItemCache.getMixingModel("dough")).thenReturn(null); e.hold(null);
        handler.onInteract(s.interact());
        assertEquals(MixingBowlStage.HAS_YEAST, MixingBowlState.getStage(s.f), "An invisible result must not discard its visible ingredients");
        assertEquals(Set.of("flour", "water", "yeast"), s.active.keySet());
        e.cache.when(() -> ItemCache.getMixingModel("dough")).thenReturn("model.dough");
        handler.onInteract(s.interact()); assertEquals(MixingBowlStage.DOUGH_READY, MixingBowlState.getStage(s.f)); assertEquals(Set.of("dough"), s.active.keySet());
    }

    @Test void mixinsConsumeOneConvertedItemAndRejectDuplicateSugarFruitAndOtherFoods() {
        prepare();
        FoodItem sugar = e.food("sugar", "sweetener", null, 4, -1); sugar.setLineage(IngredientLineage.ofExtra("Sugar"));
        ItemStack converted = e.stack(sugar, Material.SUGAR, 2);
        e.converter.when(() -> IngredientConverter.convertIfNeeded(eq(e.player), any())).thenReturn(converted);
        e.hold(new ItemStack(Material.SUGAR, 2)); handler.onInteract(s.interact());
        assertTrue(MixingBowlState.hasSugar(s.f)); assertEquals(4, MixingBowlState.getSugarQuality(s.f)); assertEquals(0, MixingBowlState.getSugarFreshness(s.f)); assertEquals("Sugar", MixingBowlState.getSugarOrigin(s.f)); assertEquals(sugar.getLineage(), MixingBowlState.getSugarLineage(s.f)); assertEquals(1, e.player.getInventory().getItemInMainHand().getAmount());
        e.converter.when(() -> IngredientConverter.convertIfNeeded(any(), any())).thenAnswer(call -> call.getArgument(1));
        handler.onInteract(s.interact()); assertEquals(1, e.player.getInventory().getItemInMainHand().getAmount()); assertTrue(e.messages.getLast().contains("already"));
        FoodItem apple = e.food("apple", "fruit", "Apple", 3, 12); apple.setLineage(IngredientLineage.ofExtra("Apple"));
        add(apple, Material.APPLE); assertEquals(List.of("Apple"), MixingBowlState.getFruitOrigins(s.f)); assertEquals(List.of(12), MixingBowlState.getFruitFreshness(s.f)); assertEquals(List.of(apple.getLineage()), MixingBowlState.getFruitLineages(s.f));
        add(apple, Material.APPLE); assertEquals(List.of("Apple"), MixingBowlState.getFruitOrigins(s.f)); assertTrue(e.messages.getLast().contains("already"));
        add(e.food("pear", "fruit", "Pear", 2, 8), Material.APPLE); add(e.food("plum", "fruit", "Plum", 2, 8), Material.APPLE);
        assertEquals(List.of("Apple", "Pear"), MixingBowlState.getFruitOrigins(s.f)); assertTrue(e.messages.getLast().contains("more fruit"));
        add(e.food("carrot", "vegetable", "Carrot", 2, 0), Material.CARROT); assertTrue(e.messages.getLast().contains("doesn't go"));
        e.hold(new ItemStack(Material.STONE)); handler.onInteract(s.interact()); assertTrue(e.messages.getLast().contains("doesn't go"));
    }

    @Test void fruitWithoutOriginIsRejectedAndBlankIngredientOriginsUseDocumentedFallbacks() {
        add(e.food("flour", "grain", " ", 3, -1), Material.WHEAT); addWater(Material.WATER_BUCKET, 1); add(e.food("yeast", "ingredient", null, 3, -1), Material.BROWN_MUSHROOM);
        assertEquals("Wheat", MixingBowlState.getFlourOrigin(s.f)); assertEquals("Yeast", MixingBowlState.getYeastOrigin(s.f));
        for (String origin : Arrays.asList(null, " ")) {
            add(e.food("fruit", "fruit", origin, 3, 0), Material.APPLE);
            assertTrue(MixingBowlState.getFruitOrigins(s.f).isEmpty()); assertTrue(e.messages.getLast().contains("doesn't go"));
        }
    }

    @Test void stirringUsesAnimationAsCooldownThenReplacesLayersWithCollectableDough() {
        prepare(); e.hold(null); ItemDisplayMock display = e.display(s.id);
        Transformation initial = display.getTransformation();
        handler.onInteract(s.interact()); assertEquals(1, MixingBowlState.getMixCount(s.f)); assertTrue(MixingBowlAnimation.isAnimating(s.f));
        var repeat = s.interact(); handler.onInteract(repeat); assertTrue(repeat.isCancelled()); assertEquals(1, MixingBowlState.getMixCount(s.f));
        e.server.getScheduler().performTicks(6); assertFalse(MixingBowlAnimation.isAnimating(s.f)); assertEquals(initial, display.getTransformation());
        handler.onInteract(s.interact()); e.server.getScheduler().performTicks(6); handler.onInteract(s.interact());
        assertEquals(MixingBowlStage.DOUGH_READY, MixingBowlState.getStage(s.f)); assertEquals(Set.of("dough"), s.active.keySet());
        e.hold(new ItemStack(Material.STONE)); var finished = s.interact(); handler.onInteract(finished); assertTrue(finished.isCancelled());
        var take = s.take(new ItemStack(Material.STONE), "dough"); handler.onTake(take);
        assertFalse(take.isCancelled()); assertEquals(Material.PAPER, take.getItem().getType()); assertTrue(s.variables.isEmpty()); assertFalse(MixingBowlAnimation.isAnimating(s.f)); assertEquals("Wheat", e.lastBuilt.getOrigin());
    }

    @Test void takingAndBreakingOnlyReleaseCompletedDoughAndHandleMissingTemplates() {
        handler.onTake(e.station("other").take(null, "dough")); handler.onTake(s.take(null, "flour"));
        var unready = s.take(null, "dough"); handler.onTake(unready); assertTrue(unready.isCancelled()); assertTrue(e.messages.getLast().contains("Knead"));
        MixingBowlState.setStage(s.f, MixingBowlStage.DOUGH_READY); e.templates.remove("dough");
        var missing = s.take(null, "dough"); handler.onTake(missing); assertTrue(missing.isCancelled()); assertEquals(MixingBowlStage.DOUGH_READY, MixingBowlState.getStage(s.f));
        handler.onBreak(new FurnitureBreakEvent(s.f, e.player)); assertTrue(s.variables.isEmpty()); verify(e.world, never()).dropItemNaturally(any(), any());
        e.templates.put("dough", e.food("dough", "grain", "Wheat", 3, 0)); MixingBowlState.setStage(s.f, MixingBowlStage.DOUGH_READY); s.place("dough", new ItemStack(Material.STONE));
        handler.onBreak(new FurnitureBreakEvent(s.f, e.player)); verify(e.world).dropItemNaturally(eq(s.f.getLoc()), argThat(item -> item.getType() == Material.PAPER)); assertFalse(s.active.containsKey("dough")); assertTrue(s.variables.isEmpty());
        handler.onBreak(new FurnitureBreakEvent(s.f, e.player)); handler.onBreak(new FurnitureBreakEvent(e.station("other").f, e.player));
    }

    @Test void collectedDoughCarriesAllIngredientSnapshotsAndSweetFruitTags() {
        prepare(); add(e.food("sugar", "sweetener", "Honey", 2, 6), Material.SUGAR);
        FoodItem apple = e.food("apple", "fruit", "Apple", 4, 5); apple.setLineage(IngredientLineage.ofExtra("Apple")); add(apple, Material.APPLE);
        add(e.food("pear", "fruit", "Pear", 3, 4), Material.APPLE);
        MixingBowlState.setFlourLineage(s.f, IngredientLineage.ofMain("Wheat")); MixingBowlState.setYeastLineage(s.f, IngredientLineage.ofMain("Starter")); MixingBowlState.setSugarLineage(s.f, IngredientLineage.ofExtra("Honey"));
        MixingBowlState.setStage(s.f, MixingBowlStage.DOUGH_READY);
        var take = s.take(null, "dough"); handler.onTake(take); assertFalse(take.isCancelled());
        assertEquals(List.of("grain", "ingredient", "sweetener", "fruit", "fruit"), e.composedInputs.stream().map(FoodItem::getCategory).toList());
        assertEquals(List.of("Wheat", "Yeast", "Honey", "Apple", "Pear"), e.composedInputs.stream().map(FoodItem::getOrigin).toList());
        assertEquals(IngredientLineage.ofMain("Wheat"), e.composedInputs.getFirst().getLineage()); assertTrue(e.lastBuilt.hasTagTrack("sweet")); assertTrue(e.lastBuilt.hasTagTrack("richly_fruity"));
        assertEquals(apple.getLineage(), e.composedInputs.get(3).getLineage());
        assertEquals(List.of("Apple", "Pear"), e.lastBuilt.getIngredients());
    }

    @Test void olderSavedDoughWithoutOptionalSnapshotFieldsRemainsCollectable() {
        // Furniture persisted before origin/freshness/lineage fields were added can have only qualities.
        MixingBowlState.setStage(s.f, MixingBowlStage.DOUGH_READY);
        MixingBowlState.setFlourQuality(s.f, 3); MixingBowlState.setYeastQuality(s.f, 2); MixingBowlState.setHasSugar(s.f, true);
        s.variables.put(MixingBowlState.VAR_FRUIT_QUALITIES, "4:3"); s.variables.put(MixingBowlState.VAR_FRUIT_ORIGINS, "Apple"); s.variables.put(MixingBowlState.VAR_FRUIT_FRESHNESS, "-1");
        var take = s.take(null, "dough"); handler.onTake(take); assertFalse(take.isCancelled()); assertEquals(4, e.composedInputs.size()); assertTrue(e.lastBuilt.hasTagTrack("sweet")); assertTrue(e.lastBuilt.hasTagTrack("fruity"));
    }

    @Test void displayCreationValidatesConfigurationAndClearingPreservesOtherLayers() {
        e.cache.when(() -> ItemCache.getMixingModel("flour")).thenReturn(null); assertFalse(MixingBowlDisplay.showLayer(s.f, "flour"));
        e.cache.when(() -> ItemCache.getMixingModel("flour")).thenReturn("model.flour");
        when(s.f.getType()).thenReturn(null); assertFalse(MixingBowlDisplay.showLayer(s.f, "flour")); MixingBowlDisplay.clearLayer(s.f, "flour");
        when(s.f.getType()).thenReturn(s.type); s.definitions.remove("flour"); assertFalse(MixingBowlDisplay.showLayer(s.f, "flour")); MixingBowlDisplay.clearLayer(s.f, "flour");
        s.define("flour"); when(e.itemApi.getCreator().getItemFromPath("model.flour")).thenReturn(null); assertFalse(MixingBowlDisplay.showLayer(s.f, "flour"));
        when(e.itemApi.getCreator().getItemFromPath("model.flour")).thenReturn(new ItemStack(Material.WHEAT)); assertTrue(MixingBowlDisplay.showLayer(s.f, "flour"));
        s.place("water", new ItemStack(Material.WATER_BUCKET)); MixingBowlDisplay.clearLayer(s.f, "flour"); assertEquals(Set.of("water"), s.active.keySet());
    }

    @Test void animationHandlesMissingDeadAndOffsetDisplaysAndFollowsActiveIngredients() {
        assertFalse(MixingBowlAnimation.isAnimating(null)); MixingBowlAnimation.clearAnimating(null);
        Furniture missingId = mock(Furniture.class); assertFalse(MixingBowlAnimation.isAnimating(missingId)); MixingBowlAnimation.clearAnimating(missingId);
        MixingBowlAnimation.playStir(s.f); assertFalse(MixingBowlAnimation.isAnimating(s.f));
        ItemDisplayMock display = e.display(s.id); ItemCache.mixingStirPivotY = 0.3f;
        Transformation original = new Transformation(new Vector3f(1, 2, 3), new Quaternionf(), new Vector3f(1), new Quaternionf()); display.setTransformation(original);
        PlacedSlot flour = s.place("flour", new ItemStack(Material.WHEAT)); MixingBowlAnimation.playStir(s.f); e.server.getScheduler().performOneTick();
        assertTrue(MixingBowlAnimation.isAnimating(s.f)); verify(flour).followParentTransform(display);
        e.server.getScheduler().performTicks(5); assertFalse(MixingBowlAnimation.isAnimating(s.f));
        Transformation restored = display.getTransformation();
        assertTrue(original.getTranslation().equals(restored.getTranslation(), 0.00001f));
        assertTrue(original.getScale().equals(restored.getScale(), 0.00001f));
        assertEquals(1f, Math.abs(original.getLeftRotation().dot(restored.getLeftRotation())), 0.00001f);
        assertEquals(1f, Math.abs(original.getRightRotation().dot(restored.getRightRotation())), 0.00001f);
        MixingBowlAnimation.playStir(s.f); display.remove(); e.server.getScheduler().performOneTick(); assertFalse(MixingBowlAnimation.isAnimating(s.f));
    }

    private void addFlour() { add(e.food("flour", "grain", "Wheat", 3, 9), Material.WHEAT); }
    private void addWater(Material material, int amount) { e.hold(new ItemStack(material, amount)); handler.onInteract(s.interact()); }
    private void add(FoodItem food, Material material) { e.hold(e.stack(food, material, 2)); handler.onInteract(s.interact()); }
    private void prepare() { addFlour(); addWater(Material.WATER_BUCKET, 1); add(e.food("yeast", "ingredient", "Yeast", 3, 8), Material.BROWN_MUSHROOM); }
    private void resetBowl() { s.active.clear(); MixingBowlState.clear(s.f); e.player.getInventory().clear(); }
}
