package net.tfminecraft.cooking.carve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.hook.MeatHookHandler;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.events.FurniturePunchEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class CarvingAndMeatHookCoverageTest {
    @TempDir Path directory;
    Workshop env;
    Station hook;
    MeatHookHandler hooks;
    Map<UUID, Furniture> placed;
    Map<String, CarveSequence> registry, previousSequences;
    MockedStatic<FoodParser> parser;
    MockedStatic<ItemRef> refs;
    MockedStatic<ItemUpdater> updater;
    String oldHook, oldTool;
    FoodItem lastPart;

    @BeforeEach @SuppressWarnings("unchecked") void open() throws Exception {
        // Snapshot and restore the loader's registry; scenarios themselves use its public YAML loader.
        Field field = CarveSequenceLoader.class.getDeclaredField("sequences"); field.setAccessible(true);
        registry = (Map<String, CarveSequence>) field.get(null); previousSequences = new HashMap<>(registry);
        oldHook = FurnitureCache.meatHook; oldTool = ItemCache.carveTool;
        env = new Workshop(); when(Cooking.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        FurnitureCache.meatHook = "meat_hook"; ItemCache.carveTool = "tool:knife";
        hook = env.station("meat_hook"); hook.define("content"); hooks = new MeatHookHandler();
        placed = new LinkedHashMap<>(); placed.put(hook.id, hook.furniture); when(env.manager.getPlacedFurniture()).thenReturn(placed);
        when(env.api.getChecker().checkItemWithPath(any(), eq("tool:knife"))).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.IRON_AXE;
        });
        parser = env.scoped(mockStatic(FoodParser.class));
        FoodParser.Result parsed = new FoodParser.Result(); parsed.template = env.food("cut", "Cow", 3);
        parser.when(() -> FoodParser.parse("meat(type=cut)")).thenReturn(parsed);
        refs = env.scoped(mockStatic(ItemRef.class));
        refs.when(() -> ItemRef.resolve("v.bone")).thenAnswer(inv -> new ItemStack(Material.BONE));
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(ItemStack.class), anyInt())).thenAnswer(inv -> {
            lastPart = inv.getArgument(0); return env.stack(new FoodItem(lastPart), Material.PAPER, 1);
        });
        updater = env.scoped(mockStatic(ItemUpdater.class));
        updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(inv -> updated(inv.getArgument(1)));
        updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenAnswer(inv -> updated(inv.getArgument(1)));
        load("""
                roast:
                  start-remaining: 3
                  cuts:
                    - output: meat(type=cut)
                      food: 2
                      nutrition: 3
                    - output: meat(type=cut)
                      food: 4
                      nutrition: 5
                    - item: v.bone
                      amount: 2
                      food: 0
                      nutrition: 0
                """);
    }

    @AfterEach void close() {
        env.server.getScheduler().cancelTasks(Cooking.plugin); env.close();
        registry.clear(); registry.putAll(previousSequences);
        FurnitureCache.meatHook = oldHook; ItemCache.carveTool = oldTool;
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("carve.yml"); Files.writeString(file, yaml); new CarveSequenceLoader().load(file.toFile());
    }
    FoodItem roast(String sequence) {
        MemoryConfiguration config = new MemoryConfiguration(); config.set("carve-sequence", sequence); config.set("food", 12); config.set("nutrition", 7);
        FoodItem food = new FoodItem("roast", config); food.setOrigin("Cow"); food.setCategory("meat"); food.setQualityRange(3, 3);
        food.setModel(new FoodModel(new ItemStack(Material.CARROT))); return food;
    }
    ItemStack updated(FoodItem food) { return env.stack(new FoodItem(food), Material.ROTTEN_FLESH, 1); }
    ItemStack whole(FoodItem food) {
        ItemStack stack = env.stack(food, Material.ROTTEN_FLESH, 1); CarvableRoastUtils.initCarveState(stack, food); return stack;
    }
    PlacedSlot placeRoast() { return hook.place("content", whole(roast("roast"))); }
    ItemStack knife() { return new ItemStack(Material.IRON_AXE); }
    boolean carve(PlacedSlot slot) { return CarveHandler.tryCarve(env.player, hook.furniture, slot, knife()); }
    long carried(Material material) {
        long count = 0; for (ItemStack item : env.player.getInventory().getStorageContents()) if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }
    void track(FoodItem food, String id, int value) {
        TagTrack track = new TagTrack(TrackLoader.getByString(id)); track.setValue(value); food.addOrModifyTrack(track);
    }

    @Test
    void cutsSequencesAndLoaderExposeAmountsNutritionAndCaseInsensitiveIds() throws Exception {
        CarveSequence sequence = CarveSequenceLoader.get("ROAST");
        assertEquals("roast", sequence.getId()); assertEquals(3, sequence.getStartRemaining()); assertEquals(1, sequence.getMinFoodCuts());
        assertEquals(6, sequence.sumRemainingFood(0)); assertEquals(4, sequence.sumRemainingFood(1)); assertEquals(0, sequence.sumRemainingFood(3));
        assertEquals(8, sequence.sumRemainingNutrition(0)); assertEquals(5, sequence.sumRemainingNutrition(1));
        assertNull(sequence.getCut(-1)); assertNull(sequence.getCut(3)); assertNull(CarveSequenceLoader.get(null)); assertNull(CarveSequenceLoader.get("missing"));
        CarveCut food = sequence.getCut(0), bone = sequence.getCut(2);
        assertTrue(food.isFoodCut()); assertFalse(food.isItemCut()); assertEquals("meat(type=cut)", food.getOutput());
        assertTrue(bone.isItemCut()); assertFalse(bone.isFoodCut()); assertEquals("v.bone", bone.getItemRef()); assertEquals(2, bone.getAmount());
        assertEquals(3, food.getNutrition()); assertFalse(CarveCut.food(" ", 1, 1).isFoodCut()); assertFalse(CarveCut.item(" ", 1, 1, 1).isItemCut());
        load("""
                scalar: ignored
                poultry:
                  start-remaining: 2
                  cuts:
                    - output: food
                    - item: v.bone
                    - output: food
                      item: v.bone
                    - food: 1
                other:
                  min-food-cuts: 2
                """);
        CarveSequence poultry = CarveSequenceLoader.get("poultry"); assertEquals(3, poultry.getMinFoodCuts()); assertEquals(2, poultry.getCuts().size());
        assertEquals(1, poultry.getCut(0).getFood()); assertEquals(1, poultry.getCut(0).getNutrition()); assertEquals(1, poultry.getCut(1).getAmount());
        assertEquals(2, CarveSequenceLoader.get("other").getMinFoodCuts()); assertNull(CarveSequenceLoader.get("roast"));
    }

    @Test
    void unreadableSequenceFilesClearOldRecipesAndReportTheFile() {
        PrintStream before = System.err; ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(diagnostics)) {
            System.setErr(capture); new CarveSequenceLoader().load(directory.resolve("missing.yml").toFile());
        } finally { System.setErr(before); }
        assertNull(CarveSequenceLoader.get("roast")); assertTrue(diagnostics.toString().contains("missing.yml"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"food: invalid", "nutrition: invalid", "amount: invalid", "amount: 0", "amount: -2", "amount: 1.5",
            "food: .nan", "nutrition: .inf", "amount: .inf", "amount: 2147483648", "food: -1", "nutrition: -1"})
    void malformedNumericCutFieldsDoNotPreventValidCutsFromLoading(String setting) throws Exception {
        String config = "safe:\n  cuts:\n    - item: v.bone\n      " + setting + "\n    - item: v.bone\n      amount: 2\n";
        assertDoesNotThrow(() -> load(config));
        assertEquals(1, CarveSequenceLoader.get("safe").getCuts().size()); assertEquals(2, CarveSequenceLoader.get("safe").getCut(0).getAmount());
    }

    @Test
    void recipeIdsRemainCaseInsensitiveUnderTurkishLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            load("FILET:\n  cuts:\n    - item: v.bone\n");
            assertSame(CarveSequenceLoader.get("filet"), CarveSequenceLoader.get("FILET"));
            assertNotNull(CarveSequenceLoader.get("filet"));
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void persistedCarveStateRoundTripsAndInitializesOnlyKnownSequences() {
        FoodItem original = roast("roast"); ItemStack stack = whole(original);
        FoodItem restored = roast(null); CarvableRoastUtils.readCarveState(restored, stack);
        assertEquals("roast", restored.getCarveSequencePdc()); assertEquals(0, restored.getCarveNextIndex()); assertEquals(3, restored.getCarveRemaining());
        restored.setCarveNextIndex(1); restored.setCarveRemaining(2); CarvableRoastUtils.writeCarveState(stack, restored);
        CarvableRoastUtils.readCarveState(original, stack); assertEquals(1, original.getCarveNextIndex()); assertEquals(2, original.getCarveRemaining());
        ItemStack partial = env.stack(restored, Material.PAPER, 1); var meta = partial.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.CARVE_SEQUENCE, PersistentDataType.STRING, "roast"); partial.setItemMeta(meta);
        CarvableRoastUtils.readCarveState(original, partial); assertEquals(0, original.getCarveNextIndex()); assertEquals(0, original.getCarveRemaining());
        CarvableRoastUtils.readCarveState(original, null); CarvableRoastUtils.readCarveState(original, new ItemStack(Material.AIR));
        CarvableRoastUtils.readCarveState(original, new ItemStack(Material.STONE));
        CarvableRoastUtils.writeCarveState(null, original); CarvableRoastUtils.writeCarveState(new ItemStack(Material.AIR), original);
        FoodItem plain = roast(null); CarvableRoastUtils.writeCarveState(stack, plain); CarvableRoastUtils.initCarveState(stack, plain); assertFalse(plain.hasCarveState());
        FoodItem unknown = roast("missing"); CarvableRoastUtils.initCarveState(stack, unknown); assertFalse(unknown.hasCarveState());
        assertTrue(CarvableRoastUtils.isCarvable(stack)); assertFalse(CarvableRoastUtils.isCarvable(new ItemStack(Material.STONE)));
        assertFalse(CarvableRoastUtils.isCarvable((FoodItem) null)); assertFalse(CarvableRoastUtils.isCarvable(plain));
        original.setCarveState("roast", 3, 1); assertFalse(CarvableRoastUtils.isCarvable(original));
    }

    @ParameterizedTest
    @CsvSource({"removed_recipe,0", "roast,-1"})
    void roastsWithRemovedRecipesOrNegativeIndexesAreNotCarvable(String sequence, int index) {
        FoodItem unknown = roast("roast"); unknown.setCarveState(sequence, index, 3);
        assertFalse(CarvableRoastUtils.isCarvable(unknown));
    }

    @Test
    void remainingFoodUsesEdibleCutBudgetsAndKeepsBatchNutrition() {
        CarveSequence sequence = CarveSequenceLoader.get("roast"); FoodItem food = roast("roast"); whole(food);
        assertEquals(6, CarvableRoastUtils.getRemainingFood(food)); assertEquals(7, CarvableRoastUtils.getRemainingNutrition(food));
        assertEquals(12, CarvableRoastUtils.remainingFood(food, null));
        assertEquals(2, CarvableRoastUtils.countFoodCuts(sequence)); assertEquals(2, CarvableRoastUtils.countFoodCutsFrom(sequence, -3));
        assertEquals(1, CarvableRoastUtils.countFoodCutsFrom(sequence, 1)); assertEquals(0, CarvableRoastUtils.countFoodCuts(null));
        food.setCarveState("roast", 0, 2); assertEquals(2, CarvableRoastUtils.getRemainingFood(food));
        CarvableRoastUtils.advanceAfterCarve(food); assertEquals(2, food.getCarveNextIndex()); assertEquals(1, food.getCarveRemaining());
        assertEquals(0, CarvableRoastUtils.getRemainingFood(food));
        food.setBaseFood(12); food.setCarveState("roast", 1, 2); assertEquals(6, CarvableRoastUtils.getRemainingFood(food));
        assertEquals(6, CarvableRoastUtils.portionFood(12, 2)); assertEquals(12, CarvableRoastUtils.portionFood(12, 0));
        CarveSequence allMeat = new CarveSequence("meat", 2, List.of(CarveCut.food("part", 2, 1), CarveCut.food("part", 4, 1)));
        FoodItem unmodified = roast(null); unmodified.setCarveState("meat", 0, 2); assertEquals(6, CarvableRoastUtils.remainingFood(unmodified, allMeat));
        unmodified.setCarveRemaining(0); assertEquals(0, CarvableRoastUtils.remainingFood(unmodified, allMeat));
        CarvableRoastUtils.advanceAfterCarve(unmodified, null); assertEquals(0, unmodified.getCarveRemaining());
    }

    @Test
    void portionBudgetsRespectMinimumMeatCutsAndKeepOnlyTheClosingBone() {
        CarveSequence sequence = CarveSequenceLoader.get("roast");
        assertEquals(new CarvableRoastUtils.RoastPortion(0, 2), CarvableRoastUtils.portion(sequence, -2));
        assertEquals(new CarvableRoastUtils.RoastPortion(0, 3), CarvableRoastUtils.portion(sequence, 10));
        assertEquals(new CarvableRoastUtils.RoastPortion(0, 0), CarvableRoastUtils.portion(null, 3));
        CarveSequence empty = new CarveSequence("empty", 0, List.of()); assertEquals(0, CarvableRoastUtils.portion(empty, 3).remaining());
        assertEquals(-1, CarvableRoastUtils.trailingItemIndex(empty)); assertEquals(-1, CarvableRoastUtils.trailingItemIndex(null));
        CarveSequence noBone = new CarveSequence("meat", 1, List.of(CarveCut.food("part", 1, 1)));
        assertEquals(-1, CarvableRoastUtils.trailingItemIndex(noBone)); assertEquals(1, CarvableRoastUtils.portion(noBone, 5).remaining());
        CarveSequence blankAfterBone = new CarveSequence("bone", 2, List.of(CarveCut.item("v.bone", 1, 0, 0), CarveCut.food(" ", 0, 0)));
        assertEquals(0, CarvableRoastUtils.trailingItemIndex(blankAfterBone));
        assertEquals(-1, CarvableRoastUtils.trailingItemIndex(new CarveSequence("blank", 1, List.of(CarveCut.food(" ", 0, 0)))));
    }

    @Test
    void visualStagesSpanTheRemainingMeatAndJumpToTheClosingBone() {
        CarveSequence sequence = CarveSequenceLoader.get("roast"); FoodItem food = roast("roast"); whole(food);
        assertEquals(1, CarvableRoastUtils.visualCarveStage(null, sequence)); assertEquals(1, CarvableRoastUtils.getVisualCarveStage(food));
        food.setCarveState("roast", 1, 2); assertEquals(2, CarvableRoastUtils.getVisualCarveStage(food));
        food.setCarveState("roast", 2, 1); assertEquals(3, CarvableRoastUtils.getVisualCarveStage(food));
        food.setCarveState("roast", 3, 0); assertEquals(3, CarvableRoastUtils.getVisualCarveStage(food));
        assertEquals(4, CarvableRoastUtils.visualCarveStage(food, null));
        assertEquals(4, CarvableRoastUtils.visualCarveStage(food, new CarveSequence("empty", 0, List.of())));
        CarveSequence allMeat = new CarveSequence("meat", 3, List.of(CarveCut.food("part", 1, 1), CarveCut.food("part", 1, 1), CarveCut.food("part", 1, 1)));
        food.setCarveState("meat", 1, 2); assertEquals(2, CarvableRoastUtils.visualCarveStage(food, allMeat));
        food.setCarveState("meat", 0, 0); assertEquals(1, CarvableRoastUtils.visualCarveStage(food, allMeat));
    }

    @Test
    void modelStagesRespectCookingAndRottenTagsAndInheritedTracksAreCopied() {
        FoodItem food = roast("roast"); whole(food);
        assertEquals("raw", CarvableRoastUtils.resolveCookTag(food));
        food.addOrModifyTrack(new TagTrack("cooked", false, List.of())); assertEquals("raw", CarvableRoastUtils.resolveCookTag(food));
        track(food, "cooked", 1); assertEquals("cooked", CarvableRoastUtils.resolveCookTag(food));
        food.addOrModifyTrack(new TagTrack("freshness", true, List.of())); assertEquals("cooked", CarvableRoastUtils.resolveCookTag(food));
        food.addOrModifyTrack(new TagTrack("freshness", true, List.of(new TagStep("rotten", "Rotten", 0, 1, 1))));
        assertEquals("rotten", CarvableRoastUtils.resolveCookTag(food));
        MemoryConfiguration model = new MemoryConfiguration(); model.set("rotten.stage", 1); model.set("rotten.tags", List.of("rotten"));
        FoodModel staged = new FoodModel("staged", model); food.setModel(staged);
        assertSame(staged.getStates().get("rotten"), CarvableRoastUtils.getStageModelData(food));
        food.setCarveRemaining(0); assertSame(staged.getStates().get("rotten"), CarvableRoastUtils.getStageModelData(food));
        FoodItem child = env.food("part", "Pig", 1); food.setLineage(IngredientLineage.ofMain("Cow")); track(food, "warmth", 1);
        CarvableRoastUtils.copyInheritedTracks(food, child);
        assertEquals("Cow", child.getOrigin()); assertEquals(food.getLineage(), child.getLineage()); assertEquals(3, child.getQualityMin());
        assertTrue(child.hasTagTrack("cooked")); assertTrue(child.hasTagTrack("freshness")); assertTrue(child.hasTagTrack("warmth"));
        assertNotSame(food.getTagTrack("cooked"), child.getTagTrack("cooked"));
        FoodItem plain = roast(null); plain.setOrigin(null); CarvableRoastUtils.copyInheritedTracks(plain, child); assertEquals("Cow", child.getOrigin());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingRoastModelsAreReportedAsUnavailableRatherThanThrowing(boolean carved) {
        FoodItem food = roast("roast"); if (carved) whole(food); food.setModel(null);
        assertNull(CarvableRoastUtils.getStageModelData(food));
    }

    @Test
    void emptyModelDefinitionsAreUnavailableAndCannotConsumeACarvingPortion() {
        FoodItem food = roast("roast"); ItemStack stack = whole(food);
        food.setModel(new FoodModel("empty", new MemoryConfiguration()));
        assertNull(CarvableRoastUtils.getStageModelData(food));
        PlacedSlot slot = hook.place("content", stack); assertFalse(carve(slot));
        assertSame(stack, slot.getCurrentItem()); assertEquals(0, carried(Material.PAPER));
    }

    @Test
    void missingModelsDoNotPayCarvingRewardsOrBreakHookDisplayRefresh() {
        FoodItem food = roast("roast"); ItemStack stack = whole(food); food.setModel(null);
        PlacedSlot slot = hook.place("content", stack);
        assertDoesNotThrow(() -> hooks.resumeLoadedHooks()); verify(slot, never()).applyDisplayData(any());
        assertFalse(carve(slot)); assertSame(stack, slot.getCurrentItem()); assertEquals(0, carried(Material.PAPER));
    }

    @Test
    void carvingProducesTwoInheritedFoodPortionsThenTheBoneAndClearsTheHook() {
        FoodItem food = roast("roast"); food.setBaseFood(12); food.setBaseNutrition(7); track(food, "cooked", 1); track(food, "freshness", 2);
        food.setLineage(IngredientLineage.ofMain("Cow")); PlacedSlot slot = hook.place("content", whole(food));
        assertTrue(carve(slot)); assertEquals(1, carried(Material.PAPER));
        assertEquals(6, lastPart.getBaseFood()); assertEquals(7, lastPart.getBaseNutrition()); assertEquals("Cow", lastPart.getOrigin());
        assertEquals(1, lastPart.getTagTrack("cooked").getValue()); assertEquals(2, lastPart.getTagTrack("freshness").getValue());
        FoodItem remaining = env.resolve(slot.getCurrentItem()); assertEquals(1, remaining.getCarveNextIndex()); assertEquals(2, remaining.getCarveRemaining());
        assertEquals(1, slot.getCurrentItem().getItemMeta().getPersistentDataContainer().get(Keys.CARVE_NEXT_INDEX, PersistentDataType.INTEGER));
        assertTrue(carve(slot)); assertEquals(2, carried(Material.PAPER)); assertTrue(carve(slot)); assertEquals(2, carried(Material.BONE));
        assertFalse(hook.active.containsKey("content")); verify(slot).clearModel(); verify(hook.furniture).removeActiveSlot("content");
        verify(env.manager, times(3)).markDirty(hook.furniture);
        env.composer.verify(() -> net.tfminecraft.cooking.quality.CompositionQualityResolver.compose(eq(env.player), any(), eq(CompositionContext.CARVE)), times(2));
    }

    @Test
    void itemRewardsDropWhenInventoryDeliveryFailsAndTheirVelocityIsBounded() {
        FoodItem food = roast("roast"); ItemStack stack = whole(food); food.setCarveState("roast", 2, 1); CarvableRoastUtils.writeCarveState(stack, food);
        PlacedSlot slot = hook.place("content", stack); Item entity = mock(Item.class);
        when(env.world.dropItemNaturally(any(), any())).thenReturn(entity);
        try (MockedStatic<InventoryAdder> inventory = mockStatic(InventoryAdder.class)) {
            inventory.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> inv.getArgument(1)); assertTrue(carve(slot));
        }
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class); verify(entity).setVelocity(velocity.capture());
        assertEquals(0.1, velocity.getValue().getY()); assertTrue(Math.abs(velocity.getValue().getX()) <= 0.1); assertTrue(Math.abs(velocity.getValue().getZ()) <= 0.1);
        assertTrue(hook.active.isEmpty());
    }

    @Test
    void carvingRejectsWrongToolsMissingInputsAndNoncarvableItems() {
        PlacedSlot slot = placeRoast();
        assertFalse(CarveHandler.tryCarve(null, hook.furniture, slot, knife())); assertFalse(CarveHandler.tryCarve(env.player, null, slot, knife()));
        assertFalse(CarveHandler.tryCarve(env.player, hook.furniture, null, knife())); assertFalse(CarveHandler.tryCarve(env.player, hook.furniture, slot, null));
        assertFalse(CarveHandler.tryCarve(env.player, hook.furniture, slot, new ItemStack(Material.STICK)));
        ItemCache.carveTool = null; assertFalse(carve(slot)); ItemCache.carveTool = "tool:knife";
        assertFalse(carve(hook.place("content", null))); assertFalse(carve(hook.place("content", new ItemStack(Material.STONE))));
        assertFalse(carve(hook.place("content", env.stack(roast(null), Material.PAPER, 1)))); assertEquals(0, carried(Material.PAPER));
        slot = placeRoast(); CarveSequenceLoader.get("roast").getCuts().set(0, null); assertFalse(carve(slot));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-item", "blank-cut", "missing-parser", "missing-template", "missing-food", "air-food", "air-item"})
    void invalidRewardsNeverConsumeTheRoast(String failure) {
        CarveSequence sequence = CarveSequenceLoader.get("roast");
        if (failure.endsWith("item")) sequence.getCuts().set(0, CarveCut.item("v.bone", 1, 0, 0));
        if (failure.equals("missing-item")) refs.when(() -> ItemRef.resolve("v.bone")).thenReturn(null);
        if (failure.equals("air-item")) refs.when(() -> ItemRef.resolve("v.bone")).thenReturn(new ItemStack(Material.AIR));
        if (failure.equals("blank-cut")) sequence.getCuts().set(0, CarveCut.food(" ", 0, 0));
        if (failure.equals("missing-parser")) parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        if (failure.equals("missing-template")) parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        if (failure.equals("missing-food")) env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(ItemStack.class), anyInt())).thenReturn(null);
        if (failure.equals("air-food")) env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(ItemStack.class), anyInt())).thenReturn(new ItemStack(Material.AIR));
        PlacedSlot slot = placeRoast(); ItemStack original = slot.getCurrentItem();
        assertFalse(carve(slot)); assertSame(original, slot.getCurrentItem());
        assertEquals(0, carried(Material.PAPER)); assertEquals(0, carried(Material.BONE)); verify(slot, never()).clearModel();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void failedRoastRenderingCannotPayRepeatedRewards(String failure) {
        PlacedSlot slot = placeRoast(); ItemStack original = slot.getCurrentItem();
        ItemStack invalid = failure.equals("air") ? new ItemStack(Material.AIR) : null;
        updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(invalid);
        boolean first = carve(slot), second = carve(slot);
        assertEquals(0, carried(Material.PAPER), "No rewards may be paid before the remaining roast is renderable");
        assertFalse(first); assertFalse(second); assertSame(original, slot.getCurrentItem());
        assertEquals(0, original.getItemMeta().getPersistentDataContainer().get(Keys.CARVE_NEXT_INDEX, PersistentDataType.INTEGER));
        verify(slot, never()).setCurrentItem(any()); verify(slot, never()).clearModel();
    }

    @Test
    void firstCarvableSlotSkipsEmptyEntriesAndNonFoodWithoutChangingThem() {
        hook.furniture.getActiveSlots().put("empty", null); PlacedSlot decoration = hook.place("decoration", new ItemStack(Material.STONE));
        assertFalse(CarveHandler.tryCarveFirstCarvableSlot(env.player, hook.furniture, knife()));
        PlacedSlot food = placeRoast(); assertTrue(CarveHandler.tryCarveFirstCarvableSlot(env.player, hook.furniture, knife()));
        assertEquals(1, carried(Material.PAPER)); verify(decoration, never()).setCurrentItem(any()); verify(food).setCurrentItem(any());
    }

    @Test
    void meatHookTimersAgeOnlyReadableRoastsAndRefreshTheirStageDisplay() {
        Station chair = env.station("chair"); placed.put(chair.id, chair.furniture);
        hooks.start(); env.server.getScheduler().performTicks(20);
        hook.furniture.getActiveSlots().put("content", null); env.server.getScheduler().performTicks(20);
        hook.place("content", null); env.server.getScheduler().performTicks(20);
        hook.place("content", new ItemStack(Material.STONE)); env.server.getScheduler().performTicks(20);
        PlacedSlot slot = placeRoast(); ItemStack original = slot.getCurrentItem(); env.server.getScheduler().performTicks(20);
        assertNotSame(original, slot.getCurrentItem()); verify(slot).applyDisplayData(any());
        verify(env.manager).markDirty(hook.furniture);
        updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(null); env.server.getScheduler().performTicks(20);
        verify(slot).setCurrentItem(any()); verify(slot).applyDisplayData(any()); verify(env.manager).markDirty(hook.furniture);
    }

    @Test
    void airAgingResultsCannotReplaceAHangingRoast() {
        PlacedSlot slot = placeRoast(); ItemStack original = slot.getCurrentItem();
        updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(new ItemStack(Material.AIR));
        hooks.start(); env.server.getScheduler().performTicks(20);
        assertSame(original, slot.getCurrentItem()); verify(slot, never()).setCurrentItem(any());
    }

    @Test
    void meatHooksResumeLoadedChunksAndDelaySlotAdditionDisplayUntilTheNextTick() {
        Station chair = env.station("chair"); placed.put(chair.id, chair.furniture);
        hooks.resumeLoadedHooks();
        hook.place("content", null); hooks.resumeLoadedHooks();
        hook.place("content", new ItemStack(Material.STONE)); hooks.resumeLoadedHooks();
        PlacedSlot slot = placeRoast(); hooks.resumeLoadedHooks(); verify(slot).applyDisplayData(any());
        clearInvocations(slot); hooks.onRoastAdd(chair.add("content", new ItemStack(Material.STONE)));
        hooks.onRoastAdd(hook.add("decoration", new ItemStack(Material.STONE))); verifyNoInteractions(slot);
        hooks.onRoastAdd(hook.add("content", slot.getCurrentItem())); verify(slot, never()).applyDisplayData(any());
        env.server.getScheduler().performOneTick(); verify(slot).applyDisplayData(any());
        Chunk chunk = mock(Chunk.class), far = mock(Chunk.class); when(env.world.getChunkAt(any(Location.class))).thenReturn(chunk);
        Location remote = mock(Location.class); when(remote.getChunk()).thenReturn(far); when(chair.furniture.getLoc()).thenReturn(remote);
        hooks.onChunkLoad(new ChunkLoadEvent(chunk, false)); env.server.getScheduler().performOneTick(); verify(slot, times(2)).applyDisplayData(any());
    }

    @Test
    void meatHookPunchesCancelOnlyAfterSuccessfulCarving() {
        Station chair = env.station("chair"); var unrelated = new FurniturePunchEvent(env.player, chair.furniture); hooks.onPunch(unrelated); assertFalse(unrelated.isCancelled());
        var empty = new FurniturePunchEvent(env.player, hook.furniture); hooks.onPunch(empty); assertFalse(empty.isCancelled());
        placeRoast(); env.hold(new ItemStack(Material.STICK));
        var wrongTool = new FurniturePunchEvent(env.player, hook.furniture); hooks.onPunch(wrongTool); assertFalse(wrongTool.isCancelled());
        env.hold(knife()); var carved = new FurniturePunchEvent(env.player, hook.furniture); hooks.onPunch(carved); assertTrue(carved.isCancelled());
        assertEquals(1, carried(Material.PAPER));
    }
}
