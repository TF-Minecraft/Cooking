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
            scoped(mockStatic(HeatSources.class)).when(() -> HeatSources.stationHasHeat(any())).thenAnswer(inv -> heated);
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
