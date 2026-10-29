package net.tfminecraft.cooking.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.fishing.CustomFishingCatalog;
import net.tfminecraft.cooking.fishing.CutRule;
import net.tfminecraft.cooking.fishing.SeafoodCutting;
import net.tfminecraft.cooking.fishing.SeafoodPortions;
import net.tfminecraft.cooking.fishing.SeafoodYield;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.item.data.OverrideData;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.CraftingStationLoader;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

public class CraftingStationCoverageTest {
    Environment env;
    @BeforeEach void open() { env = new Environment(); }
    @AfterEach void close() { env.close(); }

    @Test
    void recipeAndStationConfigurationUseDeclaredValuesAndDefaults() {
        CraftingRecipe defaults = new CraftingRecipe("empty", new MemoryConfiguration());
        assertEquals("empty", defaults.getId());
        assertTrue(defaults.getInputs().isEmpty());
        assertEquals("none", defaults.getOutput());
        assertEquals(1, defaults.getRatio());
        assertFalse(defaults.isProcessed());
        assertEquals(1, defaults.getYield());
        assertFalse(defaults.inherits());
        assertFalse(defaults.canAdd(new ItemStack(Material.STONE)));
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        CraftingStation template = new CraftingStation("board", config);
        CraftingStation station = new CraftingStation(env.furniture, template);
        assertEquals("board", station.getId());
        assertEquals("board", station.getBlockId());
        assertTrue(station.isSingleOrigin());
        assertEquals(5, station.getMaxSlots());
        assertSame(env.furniture, station.getFurniture());
        assertTrue(station.getSlots().isEmpty());
        assertFalse(station.hasRecipe());
        CraftingStation empty = new CraftingStation("empty", new MemoryConfiguration());
        assertEquals("none", empty.getBlockId());
        assertEquals(0, empty.getMaxSlots());
    }

    @Test
    void loaderReplacesTemplatesAndLooksUpNamesIgnoringCase(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("stations.yml");
        Files.writeString(config, "board:\n  furniture: oak_board\n  max-slots: 2\nsecond:\n  furniture: stone_board\n");
        new CraftingStationLoader().load(config.toFile());
        assertEquals(2, CraftingStationLoader.get().size());
        assertEquals("oak_board", CraftingStationLoader.getById("BOARD").getBlockId());
        assertEquals("stone_board", CraftingStationLoader.getById("second").getBlockId());
        assertNull(CraftingStationLoader.getById("missing"));
        PrintStream previous = System.err;
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(diagnostics)) {
            System.setErr(capture);
            new CraftingStationLoader().load(directory.resolve("missing.yml").toFile());
        } finally { System.setErr(previous); }
        assertTrue(CraftingStationLoader.get().isEmpty());
        assertTrue(diagnostics.toString().contains("missing.yml"));
    }

    @Test
    void rebuildingReadsOnlyNonemptyInputDisplaysAndCopiesTheirStacks() {
        CraftingStation station = env.station(1, false);
        ItemStack raw = env.raw(1);
        env.place("input_2", raw);
        env.place("input_1", new ItemStack(Material.AIR));
        env.place("input_3", null);
        env.place("decoration", env.raw(1));
        station.add(env.raw(1), "stale");
        station.rebuildFromFurniture();
        assertEquals(List.of("input_2"), new ArrayList<>(station.getSlots().keySet()));
        assertEquals(raw, station.getSlots().get("input_2"));
        assertNotSame(raw, station.getSlots().get("input_2"));
        assertTrue(station.hasRecipe());
        when(env.furniture.getType()).thenReturn(null);
        station.rebuildFromFurniture();
        assertTrue(station.getSlots().isEmpty());
        assertFalse(station.hasRecipe());
        CraftingStation template = new CraftingStation("board", env.config(1, false, "none"));
        template.rebuildFromFurniture();
        assertTrue(template.getSlots().isEmpty());
    }

    @Test
    void eligibilityEnforcesCapacityRecipeOriginAndCarvableRestrictions() {
        CraftingStation station = env.station(1, false);
        ItemStack raw = env.raw(1);
        assertTrue(station.canAddItem(raw));
        env.carve.when(() -> CarvableRoastUtils.isCarvable(raw)).thenReturn(true);
        assertFalse(station.canAddItem(raw));
        env.carve.when(() -> CarvableRoastUtils.isCarvable(raw)).thenReturn(false);
        station.add(raw, "input_1");
        assertFalse(station.canAddItem(new ItemStack(Material.STONE)));
        assertTrue(station.canAddItem(env.raw(1)));
        for (int n = 2; n <= 5; n++) station.add(env.raw(1), "input_" + n);
        assertFalse(station.hasFreeSlots());
        assertFalse(station.canAddItem(raw));

        MemoryConfiguration limited = env.config(1, false, "none");
        limited.set("max-slots", 1);
        CraftingStation one = new CraftingStation(env.furniture, new CraftingStation("one", limited));
        one.add(raw, "input_1");
        assertTrue(one.hasFreeSlots());
        assertFalse(one.canAddItem(raw));

        CraftingStation origin = env.station(1, true);
        assertTrue(origin.canAddItem(new ItemStack(Material.STONE)));
        origin.add(new ItemStack(Material.STONE), "input_1");
        assertTrue(origin.canAddItem(raw));
        origin.add(raw, "input_2");
        assertTrue(origin.canAddItem(env.stack(env.food("raw", "beef"), Material.BEEF, 1)));
        assertFalse(origin.canAddItem(env.stack(env.food("raw", "Pork"), Material.PORKCHOP, 1)));
    }

    @Test
    void additionAndRemovalEventsKeepRecipeAndDisplayState() {
        CraftingStation station = env.station(1, false);
        var add = env.addEvent("input_1", env.raw(1));
        station.addItem(add);
        assertFalse(add.isCancelled());
        assertNotNull(add.getDisplayData());
        assertTrue(station.hasRecipe());
        var denied = env.addEvent("input_2", new ItemStack(Material.STONE));
        station.addItem(denied);
        assertTrue(denied.isCancelled());
        assertEquals(1, station.getSlots().size());
        FoodItem carved = env.food("raw", "Beef");
        carved.getOverrides().put("BEEF", new OverrideData(null, null, "roast"));
        carved.setCarveState("roast", 1, 3);
        var carve = env.addEvent("input_2", env.stack(carved, Material.BEEF, 1));
        station.addItem(carve);
        assertNotNull(carve.getDisplayData());
        env.carve.verify(() -> CarvableRoastUtils.readCarveState(carved, carve.getItem()));
        station.removeItem(env.takeEvent("input_1", add.getItem()));
        assertTrue(station.hasRecipe());
        station.removeItem(env.takeEvent("input_2", carve.getItem()));
        assertFalse(station.hasRecipe());
        var plain = env.addEvent("input_1", new ItemStack(Material.STONE));
        station.addItem(plain);
        assertNull(plain.getDisplayData());
        station.interact(new FurnitureInteractEvent(env.player, env.furniture));
        assertEquals(1, station.getSlots().size());
    }

    @Test
    void craftingWaitsForARecipeAndEnoughInputs() {
        CraftingStation station = env.station(2, false);
        station.craft();
        station.add(env.raw(1), "input_1");
        station.craft();
        assertEquals(1, station.getSlots().size());
        station.getSlots().clear();
        station.craft();
        env.builder.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidRecipeRatioLeavesIngredientsAndDisplaysUntouched(int ratio) {
        CraftingStation station = env.station(ratio, false);
        ItemStack input = env.raw(2);
        PlacedSlot display = env.add(station, "input_1", input);
        assertDoesNotThrow(() -> { station.craft(); });
        assertSame(input, station.getSlots().get("input_1"));
        assertSame(input, display.getCurrentItem());
        verify(display, never()).clearModel();
        assertTrue(env.dropped.isEmpty());
        env.builder.verifyNoInteractions();
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL,true", "NULL,Beef,false", "Beef,NULL,false", "Beef,bEeF,true"}, nullValues = "NULL")
    void sameOriginMatchingHandlesFoodsWithoutAnOrigin(String existing, String incoming, boolean accepted) {
        CraftingStation station = env.station(1, true);
        env.add(station, "input_1", env.stack(env.food("raw", existing), Material.BEEF, 1));
        ItemStack candidate = env.stack(env.food("raw", incoming), Material.BEEF, 1);
        assertEquals(accepted, assertDoesNotThrow(() -> station.canAddItem(candidate)));
        assertEquals(1, station.getSlots().size());
        if (accepted) {
            env.add(station, "input_2", candidate);
            assertDoesNotThrow(() -> { station.craft(); });
            assertEquals(2, station.getSlots().values().iterator().next().getAmount());
        }
    }

    @Test
    void craftingPreservesOriginProcessesFoodAndReturnsUnusedInput() {
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        config.set("recipes.cut.output", "cut:{origin}");
        config.set("recipes.cut.processed", true);
        CraftingStation station = new CraftingStation(env.furniture, new CraftingStation("board", config));
        for (int n = 1; n <= 3; n++) env.add(station, "input_" + n, env.raw(1));
        station.craft(env.player);
        assertEquals("cut:Beef", env.lastParsed);
        assertEquals(72, env.lastQuality);
        assertNotNull(env.lastBuilt.getTagTrack("processed"));
        assertEquals(1, env.dropped.size());
        assertEquals("raw", env.resolve(env.dropped.get(0)).getId());
        assertEquals(1, station.getSlots().size());
        assertEquals("cut:Beef", env.resolve(station.getSlots().values().iterator().next()).getId());
        assertFalse(station.hasRecipe());
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player), argThat(inputs -> inputs.size() == 2), eq(CompositionContext.CUTTING_BOARD)));
        assertTrue(env.server.getPluginManager().getFiredEvents().anyMatch(event -> event instanceof DishCookedEvent cooked
                && cooked.getPlayer() == env.player && cooked.getMethod().equals("craft") && cooked.getResult().getAmount() == 1));
    }

    @Test
    void craftingCanProcessNonFoodAndDropsOutputWhenDisplaysAreOccupied() {
        MemoryConfiguration config = env.config(1, true, "none");
        config.set("recipes.cut.inputs", List.of("v.stone"));
        CraftingStation station = new CraftingStation(env.furniture, new CraftingStation("board", config));
        env.definitions.keySet().removeIf(id -> !id.equals("input_1") && !id.equals("decoration"));
        env.place("input_1", new ItemStack(Material.STICK));
        env.add(station, "incoming", new ItemStack(Material.STONE));
        station.craft();
        assertEquals(1, env.dropped.size());
        assertEquals("cut", env.resolve(env.dropped.get(0)).getId());
        assertTrue(station.getSlots().isEmpty());
        assertFalse(station.hasRecipe());
        env.composer.verify(() -> CompositionQualityResolver.compose(isNull(), argThat(inputs -> inputs.isEmpty()), eq(CompositionContext.CUTTING_BOARD)));
    }

    @Test
    void missingRemainderDisplayDoesNotLoseIngredientsOrAbortCrafting() {
        CraftingStation station = env.station(2, false);
        for (int n = 1; n <= 3; n++) station.add(env.raw(1), "input_" + n);
        assertDoesNotThrow(() -> { station.craft(); });
        assertEquals(1, env.dropped.size(), "The unconsumed ingredient must still be returned");
        assertEquals("raw", env.resolve(env.dropped.get(0)).getId());
        assertEquals(1, station.getSlots().values().iterator().next().getAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-result", "null-template", "null-stack", "air-stack"})
    void invalidOutputDoesNotConsumeInputsOrDisplays(String failure) {
        CraftingStation station = env.station(1, false);
        ItemStack raw = env.raw(1);
        ItemStack airOutput = new ItemStack(Material.AIR);
        PlacedSlot display = env.add(station, "input_1", raw);
        switch (failure) {
            case "null-result" -> env.parser.when(() -> FoodParser.parse("cut")).thenReturn(null);
            case "null-template" -> env.parser.when(() -> FoodParser.parse("cut")).thenReturn(new FoodParser.Result());
            case "null-stack" -> env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
            case "air-stack" -> env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(airOutput);
            default -> fail("Unknown output failure");
        }
        assertDoesNotThrow(() -> { station.craft(); });
        assertSame(raw, station.getSlots().get("input_1"));
        assertSame(raw, display.getCurrentItem());
        assertTrue(station.hasRecipe());
        verify(display, never()).clearModel();
        assertTrue(env.dropped.isEmpty());
    }

    @Test
    void craftingConsumesRecipeUnitsFromAStackAndReturnsItsRemainder() {
        CraftingStation station = env.station(2, false);
        env.add(station, "input_1", env.raw(3));
        station.craft();
        assertEquals(1, station.getSlots().size());
        ItemStack result = station.getSlots().values().iterator().next();
        assertEquals("cut", env.resolve(result).getId());
        assertEquals(1, result.getAmount());
        assertEquals(1, env.dropped.size());
        assertEquals("raw", env.resolve(env.dropped.get(0)).getId());
        assertEquals(1, env.dropped.get(0).getAmount());
        env.composer.verify(() -> CompositionQualityResolver.compose(isNull(), argThat(inputs -> inputs.size() == 2), eq(CompositionContext.CUTTING_BOARD)));
    }

    @Test
    void displayedRemainderWithoutAnItemStillReturnsTheStoredIngredient() {
        CraftingStation station = env.station(2, false);
        for (int n = 1; n <= 3; n++) {
            station.add(env.raw(1), "input_" + n);
            env.place("input_" + n, null);
        }
        station.craft();
        assertEquals(1, env.dropped.size());
        assertEquals("raw", env.resolve(env.dropped.get(0)).getId());
        assertEquals(1, env.dropped.get(0).getAmount());
    }

    @Test
    void consecutiveRecipesPreserveEveryUnitInProducedStacks() {
        MemoryConfiguration config = env.config(2, false, "none");
        env.recipe(config, "chop", List.of("food:cut"), "chopped", 1);
        CraftingStation station = new CraftingStation(env.furniture, new CraftingStation("board", config));
        for (int n = 1; n <= 4; n++) env.add(station, "input_" + n, env.raw(1));
        station.craft();
        assertEquals(2, station.getSlots().values().iterator().next().getAmount());
        assertTrue(station.hasRecipe());
        station.craft();
        ItemStack finalOutput = station.getSlots().values().iterator().next();
        assertEquals("chopped", env.resolve(finalOutput).getId());
        assertEquals(2, finalOutput.getAmount(), "Each of the two intermediate portions must survive the second recipe");
    }

    @Test
    void breakingWithTheConfiguredToolCraftsInsteadOfRemovingTheBoard() {
        CraftingStation station = env.station(1, false);
        var unattended = new FurnitureBreakEvent(env.furniture, null);
        station.remove(unattended);
        assertFalse(unattended.isCancelled());
        var empty = new FurnitureBreakEvent(env.furniture, env.player);
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        station.remove(empty);
        assertFalse(empty.isCancelled());
        env.add(station, "input_1", env.raw(1));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        station.remove(empty);
        assertFalse(empty.isCancelled());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        station.remove(empty);
        assertTrue(empty.isCancelled());
        assertEquals("cut", env.resolve(station.getSlots().values().iterator().next()).getId());
        CraftingStation noTool = new CraftingStation(env.furniture, new CraftingStation("board", env.config(1, false, "none")));
        var allowed = new FurnitureBreakEvent(env.furniture, env.player);
        noTool.remove(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void seafoodSkipsUnusableSourcesAndPreservesCatchMetadata() {
        CraftingStation station = env.seafoodStation();
        FoodItem invalid = env.whole(); invalid.setCatchSizeCm(null);
        station.add(new ItemStack(Material.STONE), "input_1");
        station.add(env.raw(1), "input_2");
        station.add(env.stack(invalid, Material.COD, 1), "input_3");
        station.craft();
        assertEquals(3, station.getSlots().size());
        assertNull(env.lastBuilt);
        FoodItem whole = env.whole();
        whole.setLineage(IngredientLineage.ofMain("Sea Bass"));
        env.add(station, "input_4", env.stack(whole, Material.COD, 1));
        station.craft(env.player);
        assertEquals("fillet", env.lastBuilt.getId());
        assertEquals("Sea Bass", env.lastBuilt.getOrigin());
        assertEquals(whole.getLineage(), env.lastBuilt.getLineage());
        assertEquals(40, env.lastBuilt.getCatchSizeCm());
        assertEquals("fish", env.lastBuilt.getSeafoodCutType());
        assertEquals("sea_bass", env.lastBuilt.getCustomFishingId());
        assertEquals(67, env.lastBuilt.getQualityMin());
        assertEquals(6.5, env.lastBuilt.getBaseNutrition());
        assertTrue(env.lastBuilt.isEdible());
        assertEquals(0, env.lastBuilt.getTagTrack("freshness").getValue());
        assertTrue(env.server.getPluginManager().getFiredEvents().anyMatch(event -> event instanceof DishCookedEvent));
    }

    @Test
    void seafoodUsesOneWholeFishAtATimeAndReturnsOutputWhenBoardIsFull() {
        CraftingStation station = env.seafoodStation();
        env.definitions.keySet().removeIf(id -> !id.equals("input_1") && !id.equals("decoration"));
        ItemStack whole = env.stack(env.whole(), Material.COD, 2);
        PlacedSlot source = env.add(station, "input_1", whole);
        station.craft();
        assertEquals(1, source.getCurrentItem().getAmount());
        assertEquals(1, station.getSlots().get("input_1").getAmount());
        assertEquals(1, env.dropped.size());
        assertEquals("fillet", env.resolve(env.dropped.get(0)).getId());
        assertTrue(station.hasRecipe());
        station.craft();
        assertFalse(station.hasRecipe());
        assertEquals("fillet", env.resolve(station.getSlots().get("input_1")).getId());
        assertEquals(1, env.lastBuilt.getLineage().mains().size());
    }

    @Test
    void unavailableSeafoodTemplatesLeaveWholeFishUntouched() {
        CraftingStation station = env.seafoodStation();
        ItemStack fish = env.stack(env.whole(), Material.COD, 1);
        PlacedSlot display = env.add(station, "input_1", fish);
        FoodLoader.oList.clear();
        station.craft();
        assertSame(fish, station.getSlots().get("input_1"));
        verify(display, never()).clearModel();
        assertNull(SeafoodPortions.build(null, new SeafoodYield("fillet", 1, 0)));
        assertNull(SeafoodPortions.build(env.whole(), null));
        assertNull(SeafoodCutting.plan("unknown", 10));
        assertNull(SeafoodCutting.plan("fish", 0));
        assertNull(SeafoodCutting.plan(null, 10));
        assertNull(SeafoodCutting.plan(" ", 10));
    }

    /** Real food and inventory values, with external furniture, item codecs and plugin services scoped per test. */
    CraftingStation fillStation(List<String> models) {
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        config.set("fill-models", models);
        for (String id : List.of("input_1", "input_2", "input_3")) {
            when(env.definitions.get(id).getOffset()).thenReturn(new Vector(0.1, 0, 0.2));
            when(env.definitions.get(id).getDisplayPosition()).thenReturn(new Vector(0, -0.2, 0));
        }
        return new CraftingStation(env.furniture, new CraftingStation("board", config));
    }

    @Test
    void fillStationShowsItsInputsAsOneRisingLevelCentredOnTheFurniture() {
        assertFalse(env.station(1, false).showsFill());
        CraftingStation station = fillStation(List.of("fill_1", "fill_2"));
        assertTrue(station.showsFill());
        FurnitureSlotItemAddEvent first = env.addEvent("input_1", env.raw(1));
        station.addItem(first);
        assertEquals(Material.STICK, first.getItem().getType());
        assertEquals("raw", env.resolve(first.getItem()).getId(), "The fill keeps the cup's food data");
        assertEquals(-0.1f, first.getDisplayData().getxPos(), 1e-6);
        assertEquals(0.2f, first.getDisplayData().getyPos(), 1e-6);
        assertEquals(-0.2f, first.getDisplayData().getzPos(), 1e-6);
        PlacedSlot firstSlot = env.place("input_1", first.getItem());
        verify(env.api.getCreator()).getItemFromPath("fill_1");
        FurnitureSlotItemAddEvent second = env.addEvent("input_2", env.raw(1));
        station.addItem(second);
        verify(firstSlot).setCurrentItem(argThat(item -> item.getType() == Material.STICK));
        verify(firstSlot).applyDisplayData(any());
        verify(env.api.getCreator(), atLeastOnce()).getItemFromPath("fill_2");
        env.place("input_2", second.getItem());
        station.addItem(env.addEvent("input_3", env.raw(1)));
        verify(env.api.getCreator(), atLeast(3)).getItemFromPath("fill_2");

        FurnitureSlotItemTakeEvent take = env.takeEvent("input_2", second.getItem());
        station.removeItem(take);
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()));
        assertEquals("raw", env.resolve(take.getItem()).getId());
        FurnitureSlotItemTakeEvent stone = env.takeEvent("input_3", new ItemStack(Material.STONE));
        station.removeItem(stone);
        assertEquals(Material.STONE, stone.getItem().getType());
    }

    @Test
    void fillStationKeepsTheInputWhenItsFillModelIsMissingAndCentresUnknownSlots() {
        when(env.api.getCreator().getItemFromPath("missing")).thenReturn(null);
        CraftingStation missing = fillStation(List.of("missing"));
        ItemStack cup = env.raw(1);
        FurnitureSlotItemAddEvent add = env.addEvent("input_1", cup);
        missing.addItem(add);
        assertSame(cup, add.getItem());

        CraftingStation station = fillStation(List.of("fill_1"));
        PlacedSlot ghost = env.place("ghost", env.raw(1));
        station.add(ghost.getCurrentItem(), "ghost");
        station.addItem(env.addEvent("input_1", env.raw(1)));
        verify(ghost).applyDisplayData(argThat(data -> data.getxPos() == 0 && data.getyPos() == 0));
        // Something that is not a recipe input (a pressed result) keeps its own look.
        ItemStack stone = new ItemStack(Material.STONE);
        station.add(stone, "input_2");
        station.addItem(env.addEvent("input_3", env.raw(1)));
        assertSame(stone, station.getSlots().get("input_2"));
    }

    @Test
    void fillModelsCanNameTheInputsOriginSoEachFruitGetsItsOwnPile() {
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        config.set("fill-models", List.of("pile_{origin}_1", "pile_{origin}_2"));
        config.set("recipes.cut.inputs", List.of("food:raw", "v.stone"));
        for (String id : List.of("input_1", "input_2", "input_3")) {
            when(env.definitions.get(id).getOffset()).thenReturn(new Vector());
            when(env.definitions.get(id).getDisplayPosition()).thenReturn(new Vector());
        }
        CraftingStation station = new CraftingStation(env.furniture, new CraftingStation("juicer", config));
        station.addItem(env.addEvent("input_1", env.stack(env.food("raw", "Melon Slice"), Material.MELON_SLICE, 1)));
        verify(env.api.getCreator()).getItemFromPath("pile_melon_slice_1");

        CraftingStation unnamed = new CraftingStation(env.furniture, new CraftingStation("juicer", config));
        unnamed.addItem(env.addEvent("input_1", env.stack(env.food("raw", null), Material.MELON_SLICE, 1)));
        verify(env.api.getCreator()).getItemFromPath("pile_none_1");
        CraftingStation stone = new CraftingStation(env.furniture, new CraftingStation("juicer", config));
        stone.addItem(env.addEvent("input_2", new ItemStack(Material.STONE)));
        verify(env.api.getCreator(), times(2)).getItemFromPath("pile_none_1");
    }

    @Test
    void fillStationRebuildsItsLevelAndShowsThePressedResultInItsFurnitureModel() {
        CraftingStation empty = fillStation(List.of("fill_1"));
        empty.rebuildFromFurniture();
        verify(env.api.getCreator(), never()).getItemFromPath("fill_1");

        CraftingStation station = fillStation(List.of("fill_1", "fill_2"));
        for (int n = 1; n <= 3; n++) env.place("input_" + n, env.raw(1));
        station.rebuildFromFurniture();
        verify(env.active.get("input_1")).applyDisplayData(any());
        station.craft(env.player);
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), eq("board")));
        assertEquals(1, env.dropped.size(), "The unused cup comes back");
        assertEquals("raw", env.resolve(env.dropped.get(0)).getId());
        PlacedSlot output = env.active.values().iterator().next();
        verify(output).forceModel(any());
        verify(output, atLeastOnce()).applyDisplayData(argThat(data -> data.getyPos() > 0.19f));
        assertEquals("cut", env.resolve(station.getSlots().values().iterator().next()).getId());
    }

    @Test
    void breakingAFillStationDropsWhatWasPutInNotTheFill() {
        CraftingStation station = fillStation(List.of("fill_1"));
        FurnitureSlotItemAddEvent add = env.addEvent("input_1", env.raw(1));
        station.addItem(add);
        PlacedSlot slot = env.place("input_1", add.getItem());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        var broken = new FurnitureBreakEvent(env.furniture, env.player);
        station.remove(broken);
        assertFalse(broken.isCancelled());
        verify(slot, atLeast(1)).setCurrentItem(any());
        assertEquals("raw", env.resolve(slot.getCurrentItem()).getId());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()));

        env.add(station, "input_2", env.raw(1));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        var pressed = new FurnitureBreakEvent(env.furniture, env.player);
        station.remove(pressed);
        assertTrue(pressed.isCancelled());
    }

    CraftingStation pouringStation(String container) {
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        config.set("fill-models", List.of("fill_1", "fill_2"));
        config.set("pour-return", container);
        for (String id : List.of("input_1", "input_2", "input_3")) {
            when(env.definitions.get(id).getOffset()).thenReturn(new Vector());
            when(env.definitions.get(id).getDisplayPosition()).thenReturn(new Vector());
        }
        return new CraftingStation(env.furniture, new CraftingStation("board", config));
    }

    @Test
    void pouringHandsTheContainerBackAndKeepsThePouredInputInTheFurniture() {
        CraftingStation station = pouringStation("empty_cup");
        var inventory = env.player.getInventory();
        FurnitureSlotItemAddEvent pour = env.addEvent("input_1", env.raw(1));
        station.addItem(pour);
        verify(env.api.getCreator()).getItemFromPath("empty_cup");
        assertTrue(inventory.contains(Material.STICK), "The empty cup comes back");
        env.place("input_1", pour.getItem());

        FurnitureSlotItemTakeEvent take = env.takeEvent("input_1", pour.getItem());
        station.removeItem(take);
        assertTrue(take.isCancelled());
        verify(env.player).sendMessage(contains("can't go back"));
        assertTrue(station.getSlots().containsKey("input_1"));

        for (int n = 0; n < inventory.getSize(); n++) inventory.setItem(n, new ItemStack(Material.DIRT, 64));
        station.addItem(env.addEvent("input_2", env.raw(1)));
        assertTrue(env.dropped.stream().anyMatch(item -> item.getType() == Material.STICK), "A full inventory drops the cup");

        when(env.api.getCreator().getItemFromPath("missing")).thenReturn(null);
        CraftingStation missing = pouringStation("missing");
        int drops = env.dropped.size();
        missing.addItem(env.addEvent("input_3", env.raw(1)));
        assertEquals(drops, env.dropped.size());
    }

    @Test
    void anInputWithItsOwnModelForTheFurnitureShowsIt() {
        CraftingStation station = env.station(1, false);
        MemoryConfiguration models = new MemoryConfiguration();
        models.set("plain.gui.item", "v.beef");
        models.set("plain.display.item", "v.beef");
        models.set("plain.display.furniture", List.of("board v.bone"));
        FoodItem crushed = env.food("raw", "Olive");
        crushed.setModel(new net.tfminecraft.cooking.item.model.FoodModel("olive", models));
        ItemStack olive = env.stack(crushed, Material.BEEF, 1);
        FurnitureSlotItemAddEvent add = env.addEvent("input_1", olive);
        station.addItem(add);
        assertNotSame(olive, add.getItem());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), same(crushed), eq("board")));

        ItemStack plain = env.raw(1);
        FurnitureSlotItemAddEvent other = env.addEvent("input_2", plain);
        station.addItem(other);
        assertSame(plain, other.getItem());
    }

    @Test
    void aRecipeThatDoesNotFillKeepsItsInputsAsThemselvesOnAPouringStation() {
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        config.set("fill-models", List.of("fill_1", "fill_2"));
        config.set("pour-return", "empty_cup");
        config.set("recipes.cut.fill", false);
        CraftingStation station = new CraftingStation(env.furniture, new CraftingStation("board", config));
        assertFalse(new CraftingRecipe("cut", config.getConfigurationSection("recipes.cut")).fills());

        ItemStack olive = env.raw(1);
        FurnitureSlotItemAddEvent add = env.addEvent("input_1", olive);
        station.addItem(add);
        assertSame(olive, add.getItem());
        verify(env.api.getCreator(), never()).getItemFromPath("empty_cup");
        verify(env.api.getCreator(), never()).getItemFromPath("fill_1");
        FurnitureSlotItemTakeEvent take = env.takeEvent("input_1", olive);
        station.removeItem(take);
        assertFalse(take.isCancelled());

        env.active.clear();
        for (int n = 1; n <= 3; n++) env.place("input_" + n, env.raw(1));
        station.rebuildFromFurniture();
        env.updater.clearInvocations();
        station.craft(env.player);
        assertEquals(1, env.dropped.size(), "The odd olive drops as itself");
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()), never());
    }

    @Test
    void pouredLeftoversAreNotHandedOutAgainAndBreakingSpillsThePouredInputs() {
        CraftingStation station = pouringStation("empty_cup");
        for (int n = 1; n <= 3; n++) env.place("input_" + n, env.raw(1));
        station.rebuildFromFurniture();
        station.craft(env.player);
        assertTrue(env.dropped.isEmpty(), "The odd poured input is not dropped as a full cup");

        CraftingStation spilled = pouringStation("empty_cup");
        env.active.clear();
        PlacedSlot milk = env.place("input_1", env.raw(1));
        PlacedSlot cheese = env.place("input_2", new ItemStack(Material.STONE));
        spilled.rebuildFromFurniture();
        spilled.remove(new FurnitureBreakEvent(env.furniture, null));
        verify(milk).setCurrentItem(isNull());
        verify(cheese).setCurrentItem(argThat(item -> item != null && item.getType() == Material.STONE));
    }

    @Test
    void rightClickingTheTopWithAnEmptyHandPressesInsteadOfATool() {
        MemoryConfiguration config = env.config(1, false, "none");
        config.set("press-height", 0.85);
        CraftingStation press = new CraftingStation(env.furniture, new CraftingStation("board", config));
        env.add(press, "input_1", env.raw(1));
        var low = new FurnitureInteractEvent(env.player, env.furniture, null, new Vector(1, 64.5, 1));
        press.interact(low);
        assertFalse(low.isCancelled());
        var nowhere = new FurnitureInteractEvent(env.player, env.furniture, null, null);
        press.interact(nowhere);
        assertFalse(nowhere.isCancelled());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        var holding = new FurnitureInteractEvent(env.player, env.furniture, null, new Vector(1, 64.95, 1));
        press.interact(holding);
        assertFalse(holding.isCancelled());
        env.builder.verifyNoInteractions();

        env.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        var handle = new FurnitureInteractEvent(env.player, env.furniture, null, new Vector(1, 64.95, 1));
        press.interact(handle);
        assertTrue(handle.isCancelled(), "The handle neither takes items out nor picks the press up");
        assertEquals("cut", env.resolve(press.getSlots().values().iterator().next()).getId());

        CraftingStation board = env.station(1, false);
        var plain = new FurnitureInteractEvent(env.player, env.furniture, null, new Vector(1, 64.95, 1));
        board.interact(plain);
        assertFalse(plain.isCancelled());
    }

    @Test
    void aFinishedItemLeftInTheStationIsNotUsedAsAnIngredient() {
        CraftingStation station = env.station(2, false);
        ItemStack jam = env.stack(env.food("jam", "Beef"), Material.PAPER, 1);
        PlacedSlot jar = env.add(station, "input_1", jam);
        env.add(station, "input_2", env.raw(1));
        station.craft();
        assertSame(jam, station.getSlots().get("input_1"), "One ingredient and a finished jar don't make a batch");
        assertEquals(2, station.getSlots().size());

        env.add(station, "input_3", env.raw(1));
        station.craft();
        assertSame(jam, station.getSlots().get("input_1"), "The jar stays where it is");
        verify(jar, never()).clearModel();
        assertEquals(2, station.getSlots().size());
        assertTrue(station.getSlots().values().stream().anyMatch(i -> "cut".equals(env.resolve(i).getId())));
        env.composer.verify(() -> CompositionQualityResolver.compose(isNull(), argThat(inputs -> inputs.size() == 2), eq(CompositionContext.CUTTING_BOARD)));
    }

    @Test
    void anInheritingRecipeCutsEachLoafWhereItLiesAndItsSlicesStayWhatThatLoafWas() {
        env.parser.when(() -> FoodParser.parse(anyString())).thenAnswer(inv -> {
            FoodParser.Result result = new FoodParser.Result(); result.template = env.food(inv.getArgument(0), "");
            return result;
        });
        MemoryConfiguration config = env.config(1, false, "tool:knife");
        config.set("recipes.cut.yield", 4);
        config.set("recipes.cut.inherit", true);
        config.set("recipes.cut.output", "slice");
        CraftingRecipe recipe = new CraftingRecipe("cut", config.getConfigurationSection("recipes.cut"));
        assertEquals(4, recipe.getYield());
        assertTrue(recipe.inherits());
        CraftingStation board = new CraftingStation(env.furniture, new CraftingStation("board", config));

        FoodItem banana = env.food("raw", "Wheat");
        banana.addIngredient("Banana");
        banana.addOrModifyTrack(TrackLoader.getByString("processed"));
        FoodItem plain = env.food("raw", "Wheat");
        env.add(board, "input_1", env.stack(banana, Material.BREAD, 1));
        assertTrue(board.canAddItem(env.stack(plain, Material.BREAD, 1)), "Two loaves fit end to end");
        PlacedSlot second = env.add(board, "input_2", env.stack(plain, Material.BREAD, 2));
        ItemStack stone = new ItemStack(Material.STONE);
        board.add(stone, "input_3");

        board.craft(env.player);
        FoodItem first = env.resolve(board.getSlots().get("input_1"));
        assertEquals("slice", first.getId());
        assertEquals("Wheat", first.getOrigin());
        assertEquals(List.of("Banana"), first.getIngredients(), "The banana loaf's slices stay banana");
        assertNotNull(first.getTagTrack("processed"), "Sweet, Cooked and the like carry over");
        assertEquals(4, board.getSlots().get("input_1").getAmount());
        assertTrue(env.resolve(board.getSlots().get("input_2")).getIngredients().isEmpty(), "The plain loaf stays plain");
        assertEquals(8, board.getSlots().get("input_2").getAmount(), "Each loaf gives four");
        verify(second).setCurrentItem(argThat(item -> "slice".equals(env.resolve(item).getId())));
        assertSame(stone, board.getSlots().get("input_3"), "Anything else on the board stays");
        assertTrue(env.dropped.isEmpty());
        assertFalse(board.hasRecipe());
        assertEquals(2, env.server.getPluginManager().getFiredEvents().filter(e -> e instanceof DishCookedEvent).count());

        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
        CraftingStation failed = new CraftingStation(env.furniture, new CraftingStation("board", config));
        ItemStack loaf = env.stack(plain, Material.BREAD, 1);
        env.add(failed, "input_1", loaf);
        failed.craft();
        assertSame(loaf, failed.getSlots().get("input_1"), "A loaf that can't be cut stays whole");
    }

    @Test
    void foodTakenFromAStationGoesOntoTheSameFoodInTheInventoryLikeAPickup() {
        CraftingStation station = env.station(1, false);
        ItemStack raw = env.raw(2);
        env.add(station, "input_1", raw);
        when(env.player.getLocation()).thenReturn(new Location(env.world, 0, 64, 0));
        try (MockedStatic<InventoryAdder> adder = mockStatic(InventoryAdder.class)) {
            adder.when(() -> InventoryAdder.addItem(any(), any())).thenReturn(new ItemStack(Material.BEEF));
            FurnitureSlotItemTakeEvent take = env.takeEvent("input_1", raw);
            station.removeItem(take);
            adder.verify(() -> InventoryAdder.addItem(eq(env.player), argThat(i -> "raw".equals(env.resolve(i).getId()))));
            assertTrue(take.getItem().getAmount() == 0 || take.getItem().getType().isAir(),
                    "The furniture has nothing left to hand over, so it can't make a second stack");
            assertEquals(List.of(Material.BEEF), env.dropped.stream().map(ItemStack::getType).toList(), "A full inventory drops the rest");
        }
    }

    @Test
    void keepOutputAgeStartsTheOutputFreshInsteadOfAveragingTheIngredientsAge() {
        env.composer.when(() -> CompositionQualityResolver.compose(any(), any(), any())).thenReturn(
                new CompositionResult(60, 72, Map.of("freshness", 90000, "warmth", 40), List.of(), List.of(), List.of(), null));
        env.parser.when(() -> FoodParser.parse(anyString())).thenAnswer(inv -> {
            FoodItem cheese = env.food(inv.getArgument(0), "Cow");
            TagTrack fresh = new TagTrack(TrackLoader.getByString("freshness"));
            fresh.setValue(0);
            cheese.addOrModifyTrack(fresh);
            FoodParser.Result result = new FoodParser.Result(); result.template = cheese;
            return result;
        });
        MemoryConfiguration config = env.config(1, false, "none");
        config.set("recipes.cut.keep-output-age", true);
        CraftingStation press = new CraftingStation(env.furniture, new CraftingStation("board", config));
        assertTrue(new CraftingRecipe("cut", config.getConfigurationSection("recipes.cut")).keepsOutputAge());
        env.add(press, "input_1", env.raw(1));
        press.craft(env.player);
        assertEquals(0, env.lastBuilt.getTagTrack("freshness").getValue(), "The output keeps its own age");
        assertNull(env.lastBuilt.getTagTrack("warmth"), "An age only the ingredients had is dropped");

        env.active.clear();
        CraftingStation board = env.station(1, false);
        env.add(board, "input_1", env.raw(1));
        board.craft(env.player);
        assertEquals(90000, env.lastBuilt.getTagTrack("freshness").getValue(), "Without the option the age is averaged in");
    }

    public static final class Environment implements AutoCloseable {
        public final ServerMock server;
        final Cooking previousPlugin;
        final List<TagTrack> previousTracks;
        final List<FoodItem> previousFoods;
        final List<CraftingStation> previousStations;
        final String oldTurner = ItemCache.firePitTurner;
        final String oldAxis = ItemCache.firePitSpinAxis;
        final float oldVisual = ItemCache.firePitVisualY;
        final float oldPivot = ItemCache.firePitPivotY;
        public final Furniture furniture = mock(Furniture.class);
        public final FurnitureType type = mock(FurnitureType.class);
        public final World world = mock(World.class);
        public final Chunk chunk = mock(Chunk.class);
        public final Player player = mock(Player.class);
        public final Map<String, SlotDefinition> definitions = new LinkedHashMap<>();
        public final Map<String, PlacedSlot> active = new LinkedHashMap<>();
        public final Map<UUID, Furniture> placed = new LinkedHashMap<>();
        public final Map<UUID, Entity> entities = new HashMap<>();
        public final List<ItemStack> dropped = new ArrayList<>();
        final Map<String, FoodItem> foods = new HashMap<>();
        final List<MockedStatic<?>> mocks = new ArrayList<>();
        public final MockedStatic<FoodItem> foodCodec;
        public final MockedStatic<ItemUpdater> updater;
        public final MockedStatic<ItemBuilder> builder;
        public final MockedStatic<FoodParser> parser;
        public final MockedStatic<CarvableRoastUtils> carve;
        public final MockedStatic<CompositionQualityResolver> composer;
        public final MockedStatic<CustomFishingCatalog> fishing;
        public final ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        final NamespacedKey foodKey = NamespacedKey.fromString("test:craft_food");
        public boolean heated = true;
        public boolean firePit;
        int nextFood;
        public FoodItem lastBuilt;
        public int lastQuality;
        public String lastParsed;

        public Environment() {
            server = MockBukkit.mock();
            previousPlugin = Cooking.plugin;
            Cooking.plugin = mock(Cooking.class);
            when(Cooking.plugin.getName()).thenReturn("Cooking");
            when(Cooking.plugin.namespace()).thenReturn("cooking");
            when(Cooking.plugin.isEnabled()).thenReturn(true);
            previousTracks = TrackLoader.oList;
            TrackLoader.oList = new ArrayList<>();
            new TrackLoader().load(new File("src/main/resources/tags.yml"));
            previousFoods = FoodLoader.oList;
            FoodLoader.oList = new ArrayList<>();
            previousStations = new ArrayList<>(CraftingStationLoader.get());
            CraftingStationLoader.get().clear();
            ItemCache.firePitTurner = "v.stick";
            ItemCache.firePitSpinAxis = "x";
            ItemCache.firePitVisualY = 0.25f;
            ItemCache.firePitPivotY = 0.5f;
            when(player.getInventory()).thenReturn(server.addPlayer().getInventory());
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(world.getName()).thenReturn("craft-test");
            when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
            when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
            when(chunk.getWorld()).thenReturn(world);
            when(chunk.getX()).thenReturn(0);
            when(chunk.getZ()).thenReturn(0);
            when(world.dropItem(any(Location.class), any(ItemStack.class))).thenAnswer(inv -> {
                dropped.add(((ItemStack) inv.getArgument(1)).clone());
                return mock(Item.class);
            });
            when(furniture.getId()).thenReturn("board");
            when(furniture.getEntityId()).thenReturn(UUID.randomUUID());
            when(furniture.getLoc()).thenReturn(new Location(world, 1, 64, 1));
            when(furniture.getType()).thenReturn(type);
            when(type.getId()).thenReturn("board");
            when(type.getSlots()).thenReturn(definitions);
            when(type.getSlot(anyString())).thenAnswer(inv -> definitions.get(inv.getArgument(0)));
            when(furniture.getActiveSlots()).thenReturn(active);
            when(furniture.getActiveSlot(anyString())).thenAnswer(inv -> Optional.ofNullable(active.get(inv.getArgument(0))));
            when(furniture.hasActiveSlot(anyString())).thenAnswer(inv -> active.containsKey(inv.getArgument(0)));
            when(furniture.getOrCreatePlacedSlot(anyString())).thenAnswer(inv -> {
                String id = inv.getArgument(0);
                return active.containsKey(id) ? active.get(id) : place(id, null);
            });
            doAnswer(inv -> { active.remove(inv.getArgument(0)); return null; }).when(furniture).removeActiveSlot(anyString());
            for (String id : List.of("decoration", "input_1", "input_2", "input_3", "input_4", "input_5")) define(id);
            placed.put(furniture.getEntityId(), furniture);
            MockedStatic<HeatSources> heat = scoped(mockStatic(HeatSources.class));
            heat.when(() -> HeatSources.stationHasHeat(any())).thenAnswer(inv -> heated);
            heat.when(() -> HeatSources.consumerHasHeat(any())).thenAnswer(inv -> heated);
            scoped(mockStatic(FurnitureCache.class)).when(() -> FurnitureCache.isFirePit(any())).thenAnswer(inv -> firePit);
            foodCodec = scoped(mockStatic(FoodItem.class, CALLS_REAL_METHODS));
            foodCodec.when(() -> FoodItem.fromItem(any())).thenAnswer(inv -> resolve(inv.getArgument(0)));
            updater = scoped(mockStatic(ItemUpdater.class));
            updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(inv -> {
                ItemStack source = inv.getArgument(0);
                return source == null ? null : stack(inv.getArgument(1), source.getType(), source.getAmount());
            });
            scoped(mockStatic(TLibs.class)).when(TLibs::getItemAPI).thenReturn(api);
            when(api.getChecker().checkItemWithPath(any(), anyString())).thenAnswer(inv -> {
                ItemStack stack = inv.getArgument(0); String path = inv.getArgument(1);
                if (stack == null) return false;
                FoodItem food = resolve(stack);
                return path.equals("any") || (path.startsWith("food:") && food != null && food.getId().equals(path.substring(5)))
                        || (path.equals("tool:knife") && stack.getType() == Material.IRON_SWORD)
                        || (path.equals("v.stone") && stack.getType() == Material.STONE);
            });
            when(api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> new ItemStack(Material.STICK));
            InteractibleFurniture plugin = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
            scoped(mockStatic(InteractibleFurniture.class)).when(InteractibleFurniture::getInstance).thenReturn(plugin);
            when(plugin.getFurnitureManager().getPlacedFurniture()).thenReturn(placed);
            scoped(mockStatic(Bukkit.class, CALLS_REAL_METHODS)).when(() -> Bukkit.getEntity(any(UUID.class)))
                    .thenAnswer(inv -> entities.get(inv.getArgument(0)));
            carve = scoped(mockStatic(CarvableRoastUtils.class));
            carve.when(() -> CarvableRoastUtils.getStageModelData(any())).thenAnswer(inv -> {
                FoodItem food = inv.getArgument(0);
                return food.getModel().getModel(food);
            });
            composer = scoped(mockStatic(CompositionQualityResolver.class));
            composer.when(() -> CompositionQualityResolver.compose(any(), any(), any())).thenReturn(
                    new CompositionResult(60, 72, Map.of(), List.of(), List.of(), List.of(), null));
            parser = scoped(mockStatic(FoodParser.class));
            parser.when(() -> FoodParser.parse(anyString())).thenAnswer(inv -> {
                lastParsed = inv.getArgument(0);
                FoodParser.Result result = new FoodParser.Result(); result.template = food(lastParsed, "Beef");
                return result;
            });
            builder = scoped(mockStatic(ItemBuilder.class));
            builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenAnswer(inv -> {
                lastBuilt = inv.getArgument(0); lastQuality = inv.getArgument(2);
                return stack(lastBuilt, Material.PAPER, 1);
            });
            fishing = scoped(mockStatic(CustomFishingCatalog.class));
            fishing.when(() -> CustomFishingCatalog.cutRule("fish")).thenReturn(new CutRule("fish", "fillet", 1, 1, 100, 10, 10));
        }

        public <T> MockedStatic<T> scoped(MockedStatic<T> value) { mocks.add(value); return value; }
        public MemoryConfiguration config(int ratio, boolean singleOrigin, String tool) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("furniture", "board"); config.set("max-slots", 5);
            config.set("single-origin", singleOrigin); config.set("tool", tool);
            recipe(config, "cut", List.of("food:raw"), "cut", ratio);
            return config;
        }
        public void recipe(MemoryConfiguration config, String id, List<String> inputs, String output, int ratio) {
            config.set("recipes." + id + ".inputs", inputs);
            config.set("recipes." + id + ".output", output);
            config.set("recipes." + id + ".ratio", ratio);
        }
        public CraftingStation station(int ratio, boolean singleOrigin) {
            return new CraftingStation(furniture, new CraftingStation("board", config(ratio, singleOrigin, "tool:knife")));
        }
        public CraftingStation seafoodStation() {
            MemoryConfiguration config = config(1, false, "tool:knife");
            config.set("recipes", null);
            recipe(config, "cut_seafood", List.of("food:seafood_whole", "v.stone"), "fillet", 1);
            FoodItem fillet = food("fillet", "Fish"); fillet.setBaseNutrition(6.5);
            FoodLoader.oList.add(fillet);
            return new CraftingStation(furniture, new CraftingStation("board", config));
        }
        public FoodItem food(String id, String origin, Method... methods) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("name", id); config.set("update", false);
            FoodItem food = new FoodItem(id, config);
            food.setCategory("meat"); food.setOrigin(origin);
            food.setModel(new FoodModel(new ItemStack(Material.CARROT)));
            if (methods.length > 0) {
                food.addOrModifyTrack(TrackLoader.getByString("cooked"));
                for (Method method : methods) food.getCookData().getParameters().put(method, new CookParameter(1, 2, 20));
            }
            return new FoodItem(food);
        }
        public FoodItem whole() {
            FoodItem food = food("seafood_whole", "Sea Bass");
            food.setCatchSizeCm(40); food.setSeafoodCutType("fish"); food.setCustomFishingId("sea_bass");
            food.setQualityRange(67, 67);
            return food;
        }
        public ItemStack raw(int amount) { return stack(food("raw", "Beef"), Material.BEEF, amount); }
        public ItemStack stack(FoodItem food, Material material, int amount) {
            ItemStack item = new ItemStack(material, amount);
            String key = "food-" + nextFood++;
            foods.put(key, food);
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(foodKey, PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            return item;
        }
        public FoodItem resolve(ItemStack item) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
            return foods.get(item.getItemMeta().getPersistentDataContainer().get(foodKey, PersistentDataType.STRING));
        }
        public SlotDefinition define(String id) {
            SlotDefinition definition = mock(SlotDefinition.class);
            when(definition.getId()).thenReturn(id);
            when(definition.getDisplayScale()).thenReturn(new Vector(1, 1, 1));
            definitions.put(id, definition);
            return definition;
        }
        public PlacedSlot place(String id, ItemStack initial) {
            PlacedSlot slot = mock(PlacedSlot.class);
            ItemStack[] current = {initial};
            when(slot.getCurrentItem()).thenAnswer(inv -> current[0]);
            doAnswer(inv -> { current[0] = inv.getArgument(0); return null; }).when(slot).forceModel(any());
            doAnswer(inv -> { current[0] = inv.getArgument(0); return null; }).when(slot).setCurrentItem(any());
            doAnswer(inv -> { current[0] = null; active.remove(id); return null; }).when(slot).clearModel();
            active.put(id, slot);
            return slot;
        }
        public PlacedSlot add(CraftingStation station, String id, ItemStack item) {
            station.add(item, id);
            return place(id, item);
        }
        public FurnitureSlotItemAddEvent addEvent(String id, ItemStack item) {
            return new FurnitureSlotItemAddEvent(player, furniture, definitions.get(id), item);
        }
        public FurnitureSlotItemTakeEvent takeEvent(String id, ItemStack item) {
            return new FurnitureSlotItemTakeEvent(player, furniture, definitions.get(id), item);
        }
        public FurnitureInteractEvent interact(String id) {
            return new FurnitureInteractEvent(player, furniture, definitions.get(id), new Vector());
        }
        public ItemDisplay display(UUID entityId, Location initial, Transformation transform) {
            ItemDisplay display = mock(ItemDisplay.class);
            Location[] location = {initial.clone()};
            Transformation[] state = {transform};
            when(display.getUniqueId()).thenReturn(entityId);
            when(display.getLocation()).thenAnswer(inv -> location[0].clone());
            when(display.getTransformation()).thenAnswer(inv -> state[0]);
            doAnswer(inv -> { state[0] = inv.getArgument(0); return null; }).when(display).setTransformation(any());
            when(display.teleport(any(Location.class))).thenAnswer(inv -> { location[0] = ((Location) inv.getArgument(0)).clone(); return true; });
            entities.put(entityId, display);
            return display;
        }
        public ItemDisplay display(String slotId, float x) {
            UUID uuid = UUID.randomUUID();
            when(active.get(slotId).getDisplayStandId()).thenReturn(uuid);
            return display(uuid, new Location(world, x, 64, 1), identity());
        }
        public Transformation identity() { return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf()); }
        @Override public void close() {
            for (int n = mocks.size() - 1; n >= 0; n--) mocks.get(n).close();
            TrackLoader.oList = previousTracks;
            FoodLoader.oList = previousFoods;
            CraftingStationLoader.get().clear(); CraftingStationLoader.get().addAll(previousStations);
            ItemCache.firePitTurner = oldTurner; ItemCache.firePitSpinAxis = oldAxis;
            ItemCache.firePitVisualY = oldVisual; ItemCache.firePitPivotY = oldPivot;
            Cooking.plugin = previousPlugin;
            MockBukkit.unmock();
        }
    }
}
