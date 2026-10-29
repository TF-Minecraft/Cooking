package net.tfminecraft.cooking.sandwich;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

class SandwichCoverageTest {
    static final String CONFIG = """
            furniture: [BOARD]
            max-fillings: 2
            output: meal(type=sandwich)
            layers:
              bread:
                types: [bread_slice]
                bread: true
                thickness: 0.8
                models:
                  - rotten ia.bread_rotten
                  - ia.bread
              jam:
                types: [Jam]
                name: "{origin} Jam"
                names:
                  melon slice: Melon Jam
                sandwich-origin: Jam
                thickness: 0.3
                models:
                  - ia.missing_{origin}
                  - ia.null
                  - ia.air
                  - ia.jam
              cheese:
                types: [cheese]
                name: Cheese
              plain: 3
            """;

    Environment env;
    MockedStatic<InventoryAdder> adder;
    final List<String> modelRefs = new ArrayList<>();
    final List<ItemStack> given = new ArrayList<>();
    ItemStack rest;
    FoodItem built;
    int builtQuality;
    final SandwichHandler handler = new SandwichHandler();
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new Environment();
        for (String id : List.of("sandwich_2", "sandwich_1", "sandwich_3", "sandwich_4", "sandwich_x")) env.define(id);
        doAnswer(inv -> {
            String id = inv.getArgument(0);
            return env.active.containsKey(id) ? env.active.get(id) : slot(id, null);
        }).when(env.furniture).getOrCreatePlacedSlot(anyString());
        when(env.api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            modelRefs.add(ref);
            if (ref.startsWith("ia.missing")) return new ItemStack(Material.DIRT);
            if (ref.equals("ia.null")) return null;
            if (ref.equals("ia.air")) return new ItemStack(Material.AIR);
            return new ItemStack(Material.STICK);
        });
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            builtQuality = inv.getArgument(1);
            return env.stack(built, Material.BREAD, 1);
        });
        adder = env.scoped(mockStatic(InventoryAdder.class));
        adder.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> {
            given.add(inv.getArgument(1));
            return rest;
        });
        load(CONFIG);
    }

    @AfterEach void close() {
        env.close();
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("sandwiches.yml");
        Files.writeString(file, yaml);
        new SandwichConfig().load(file.toFile());
    }

    PlacedSlot slot(String id, ItemStack item) {
        PlacedSlot slot = env.place(id, item);
        when(slot.getId()).thenReturn(id);
        return slot;
    }

    FoodItem food(String type, String origin) {
        return env.food(type, origin);
    }

    ItemStack bread() {
        FoodItem slice = food("bread_slice", null);
        TagTrack cooked = new TagTrack(TrackLoader.getByString("cooked"));
        cooked.setValue(1);
        slice.addOrModifyTrack(cooked);
        slice.setBaseFood(6.5);
        slice.setBaseNutrition(5);
        return env.stack(slice, Material.BREAD, 8);
    }

    ItemStack jam(String origin) {
        FoodItem jam = food("jam", origin);
        jam.setBaseFood(12);
        jam.setBaseNutrition(9);
        return env.stack(jam, Material.HONEY_BOTTLE, 4);
    }

    ItemStack cheese() {
        return env.stack(food("cheese", "Cow"), Material.YELLOW_WOOL, 2);
    }

    void hold(ItemStack item) {
        env.player.getInventory().setItemInMainHand(item);
    }

    FurnitureInteractEvent click(ItemStack held) {
        hold(held);
        FurnitureInteractEvent event = env.interact("input_1");
        handler.onInteract(event);
        return event;
    }

    ItemStack layer(int level) {
        PlacedSlot slot = env.active.get("sandwich_" + level);
        return slot == null ? null : slot.getCurrentItem();
    }

    float lift(int level) {
        ArgumentCaptor<DisplayData> data = ArgumentCaptor.forClass(DisplayData.class);
        verify(env.active.get("sandwich_" + level), atLeastOnce()).applyDisplayData(data.capture());
        return data.getValue().getyPos();
    }

    @Test
    void configReadsFurnitureLayersAndFallsBackToDefaults() throws Exception {
        assertTrue(SandwichConfig.isSandwichFurniture(env.furniture));
        assertFalse(SandwichConfig.isSandwichFurniture(null));
        Furniture untyped = mock(Furniture.class);
        assertFalse(SandwichConfig.isSandwichFurniture(untyped));
        assertEquals(2, SandwichConfig.maxFillings());
        assertEquals("meal(type=sandwich)", SandwichConfig.output());
        assertEquals("jam", SandwichConfig.layerFor(food("JAM", "Plum")).getId());
        assertNull(SandwichConfig.layerFor(food("beef", "Cow")));
        assertNull(SandwichConfig.layerFor(null));

        new SandwichConfig().load(directory.resolve("missing.yml").toFile());
        assertFalse(SandwichConfig.isSandwichFurniture(env.furniture));
        assertEquals(6, SandwichConfig.maxFillings());
        assertEquals("meal(type=sandwich;origin=Mixed)", SandwichConfig.output());
        assertNull(SandwichConfig.layerFor(food("jam", "Plum")));

        load("max-fillings: 0\n");
        assertEquals(1, SandwichConfig.maxFillings());
    }

    @Test
    void layersNameFillingsAndPickTheFirstModelThatExists() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(CONFIG);
        SandwichLayer jam = new SandwichLayer("jam", config.getConfigurationSection("layers.jam"));
        SandwichLayer bread = new SandwichLayer("bread", config.getConfigurationSection("layers.bread"));
        SandwichLayer bare = new SandwichLayer("bare", new MemoryConfiguration());
        assertEquals("jam", jam.getId());
        assertFalse(jam.isBread());
        assertTrue(bread.isBread());
        assertEquals(0.3, jam.getThickness());
        assertEquals(0.5, bare.getThickness());
        assertEquals("Jam", jam.getSandwichOrigin());
        assertNull(bare.getSandwichOrigin());
        assertFalse(jam.matches(null));
        assertFalse(jam.matches(food(null, "Plum")));
        assertTrue(jam.matches(food("JAM", "Plum")));

        assertEquals("Strawberry Jam", jam.fillerName(food("jam", "Strawberry")));
        assertEquals("Melon Jam", jam.fillerName(food("jam", "Melon Slice")));
        assertEquals("Jam", jam.fillerName(food("jam", null)));
        assertEquals("Cow", bare.fillerName(food("cheese", "Cow")));

        ItemStack melon = jam("Melon Slice");
        ItemStack shown = jam.model(env.resolve(melon), melon);
        assertEquals(Material.STICK, shown.getType());
        assertSame(env.resolve(melon), env.resolve(shown));
        assertEquals(List.of("ia.missing_melon_slice", "ia.null", "ia.air", "ia.jam"), modelRefs);

        modelRefs.clear();
        FoodItem fresh = env.resolve(bread());
        assertEquals(Material.STICK, bread.model(fresh, bread()).getType());
        assertEquals(List.of("ia.bread"), modelRefs);
        FoodItem mouldy = food("bread_slice", null);
        TagTrack age = new TagTrack(TrackLoader.getByString("freshness"));
        age.setValue(300000);
        mouldy.addOrModifyTrack(age);
        modelRefs.clear();
        bread.model(mouldy, bread());
        assertEquals(List.of("ia.bread_rotten"), modelRefs);

        modelRefs.clear();
        MemoryConfiguration missing = new MemoryConfiguration();
        missing.set("models", List.of("ia.missing_{origin}"));
        assertNull(new SandwichLayer("missing", missing).model(food("jam", null), jam("Plum")));
        assertEquals(List.of("ia.missing_none"), modelRefs);
        assertNull(bare.model(food("jam", "Plum"), jam("Plum")));
    }

    @Test
    void sandwichesSumTheirLayersAndAreNamedByTheirFillings() {
        ItemStack sandwich = SandwichItems.build(env.player, List.of(bread(), jam("Strawberry"),
                new ItemStack(Material.STONE), cheese(), jam("Strawberry"), env.raw(1), bread()));
        assertNotNull(sandwich);
        assertEquals(72, builtQuality);
        assertEquals("meal(type=sandwich)", built.getId());
        assertEquals(List.of("Strawberry Jam", "Cheese"), built.getIngredients());
        assertEquals("Jam", built.getOrigin());
        // Two cooked slices (6.5 x 1.55), two jams (12), a cheese and a beef (1 each).
        double food = 2 * 6.5 * 1.55 + 2 * 12 + 1 + 1;
        assertEquals(food, built.getBaseFood(), 1e-9);
        double nutrition = 2 * 6.5 * 1.55 * 5 * 1.25 + 2 * 12 * 9 + 1 + 1;
        assertEquals(nutrition / food, built.getBaseNutrition(), 1e-9);
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player), argThat(l -> l.size() == 6),
                eq(CompositionContext.SANDWICH)));

        FoodItem empty = food("bread_slice", null);
        empty.setBaseFood(0);
        empty.setBaseNutrition(3);
        empty.addOrModifyTrack(new TagTrack("plain", false, List.of()));
        empty.addOrModifyTrack(new TagTrack(TrackLoader.getByString("freshness")));
        SandwichItems.build(null, List.of(env.stack(empty, Material.BREAD, 1)));
        assertEquals(0, built.getBaseFood());
        assertEquals(1, built.getBaseNutrition());
        assertEquals("Beef", built.getOrigin());

        assertNull(SandwichItems.build(env.player, List.of(new ItemStack(Material.STONE))));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        assertNull(SandwichItems.build(env.player, List.of(bread())));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        assertNull(SandwichItems.build(env.player, List.of(bread())));
    }

    @Test
    void aBoardWithoutASandwichOnlyTakesASliceOfBreadToStartOne() {
        Furniture other = mock(Furniture.class);
        FurnitureType otherType = mock(FurnitureType.class);
        when(other.getType()).thenReturn(otherType);
        when(otherType.getId()).thenReturn("plate");
        hold(bread());
        FurnitureInteractEvent elsewhere = new FurnitureInteractEvent(env.player, other);
        handler.onInteract(elsewhere);
        assertFalse(elsewhere.isCancelled());

        assertFalse(click(jam("Plum")).isCancelled());
        assertFalse(click(null).isCancelled());
        assertFalse(click(new ItemStack(Material.STONE)).isCancelled());

        slot("input_1", env.raw(1));
        ItemStack slices = bread();
        assertTrue(click(slices).isCancelled());
        verify(env.player).sendMessage("§7Clear the board before you start a sandwich on it.");
        assertEquals(8, env.player.getInventory().getItemInMainHand().getAmount());
        assertNull(layer(1));

        env.definitions.keySet().removeIf(id -> id.startsWith("sandwich_"));
        env.active.clear();
        assertFalse(click(bread()).isCancelled());
    }

    @Test
    void layersStackUpUntilASliceClosesTheSandwichAndARightClickPicksItUp() {
        assertTrue(click(bread()).isCancelled());
        assertEquals(7, env.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(Material.STICK, layer(1).getType());
        assertEquals("bread_slice", env.resolve(layer(1)).getId());
        assertEquals(1, layer(1).getAmount());
        assertEquals(0f, lift(1));

        click(bread());
        verify(env.player).sendMessage("§7Put a filling on the bread first.");
        assertNull(layer(2));

        click(jam("Strawberry"));
        assertEquals(0.8f / 16, lift(2), 1e-6);

        // An empty hand takes the top layer back, as it looks in hand.
        click(null);
        assertNull(layer(2));
        assertEquals(1, given.size());
        assertEquals("jam", env.resolve(given.get(0)).getId());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()));

        click(new ItemStack(Material.STONE));
        verify(env.player).sendMessage("§7That doesn't go in a sandwich.");

        click(jam("Strawberry"));
        ItemStack cheese = cheese();
        click(cheese);
        // Cheese has no board model, so it lies as it looks in hand.
        assertEquals(Material.YELLOW_WOOL, layer(3).getType());
        assertEquals(1, layer(3).getAmount());
        assertEquals(1.1f / 16, lift(3), 1e-6);

        click(jam("Plum"));
        verify(env.player).sendMessage("§7There's no room for more; close the sandwich with a slice of bread.");
        assertNull(layer(4));

        click(bread());
        assertEquals(1.6f / 16, lift(4), 1e-6);

        // Closed: any right-click picks it up, even with something in hand.
        given.clear();
        assertTrue(click(bread()).isCancelled());
        assertEquals(1, given.size());
        assertEquals(Material.BREAD, given.get(0).getType());
        assertEquals(List.of("Strawberry Jam", "Cheese"), built.getIngredients());
        assertTrue(env.active.isEmpty());
        assertEquals(8, env.player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void whatDoesNotFitInThePlayersInventoryDropsAtTheirFeet() {
        rest = new ItemStack(Material.BREAD);
        click(bread());
        click(null);
        assertEquals(1, env.dropped.size());
    }

    @Test
    void aSandwichThatCannotBeMadeStaysOnTheBoard() {
        slot("sandwich_1", bread());
        slot("sandwich_2", jam("Plum"));
        slot("sandwich_3", bread());
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        click(null);
        verify(env.player).sendMessage("§cThis sandwich can't be made right now.");
        assertNotNull(layer(3));
        assertTrue(given.isEmpty());
    }

    @Test
    void aBrokenBoardDropsAClosedSandwichWholeAndAnOpenOneAsItsLayers() {
        Furniture other = mock(Furniture.class);
        handler.onBreak(new FurnitureBreakEvent(other, env.player));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));

        ItemStack slice = bread();
        slot("sandwich_1", slice);
        slot("sandwich_2", new ItemStack(Material.STONE));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertNotSame(slice, layer(1));
        assertSame(env.resolve(slice), env.resolve(layer(1)));
        assertEquals(Material.STONE, layer(2).getType());

        ItemStack held = layer(1);
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertSame(held, layer(1));

        env.active.clear();
        slot("sandwich_1", bread());
        slot("sandwich_2", jam("Plum"));
        slot("sandwich_3", bread());
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertEquals("meal(type=sandwich)", env.resolve(layer(1)).getId());
        assertNull(layer(2));
        assertNull(layer(3));
    }

    @Test
    void loadedSandwichesAreStackedAgainOnStartAndWhenTheirChunkLoads() {
        Furniture other = mock(Furniture.class);
        FurnitureType otherType = mock(FurnitureType.class);
        when(other.getType()).thenReturn(otherType);
        when(otherType.getId()).thenReturn("plate");
        when(other.getLoc()).thenReturn(new Location(env.world, 1, 64, 1));
        env.placed.put(UUID.randomUUID(), other);
        slot("sandwich_1", bread());
        slot("sandwich_2", new ItemStack(Material.STONE));
        slot("sandwich_3", null);
        slot("sandwich_4", jam("Plum"));

        handler.resumeLoaded();
        assertEquals(0.8f / 16, lift(2), 1e-6);
        // Something that isn't a layer adds no height.
        assertEquals(0.8f / 16, lift(4), 1e-6);
        verify(env.active.get("sandwich_3"), never()).applyDisplayData(any());

        clearInvocations(env.active.get("sandwich_1"));
        handler.onChunkLoad(new ChunkLoadEvent(env.chunk, false));
        verify(env.active.get("sandwich_1"), never()).applyDisplayData(any());
        env.server.getScheduler().performTicks(2);
        verify(env.active.get("sandwich_1")).applyDisplayData(any());
    }
}
