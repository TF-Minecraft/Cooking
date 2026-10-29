package net.tfminecraft.cooking.pizza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedFurnitureSlot;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

class PizzaCoverageTest {
    static final String CONFIG = """
            furniture: [BOARD]
            max-toppings: 2
            output: meal(type=pizza)
            slices: 2
            bake:
              cook-seconds: 2
              burn-seconds: 4
            base:
              types: [dough]
              thickness: 1.0
              models:
                - burnt ia.base_burnt
                - cooked ia.base_cooked
                - ia.base_raw
            sauce:
              types: [paste]
              thickness: 0.2
              models:
                - ia.sauce_{origin}
            toppings:
              cheese:
                types: [cheese]
                name: Cheese
                thickness: 0.3
              spice:
                types: [spice_1]
                names:
                  salt: ""
                thickness: 0.05
              plain: 3
            """;

    Environment env;
    MockedStatic<InventoryAdder> adder;
    final List<String> modelRefs = new ArrayList<>();
    final List<ItemStack> given = new ArrayList<>();
    final Map<String, Object> variables = new HashMap<>();
    ItemStack rest;
    FoodItem built;
    int builtQuality;
    final PizzaHandler handler = new PizzaHandler();
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new Environment();
        for (String id : List.of("pizza_2", "pizza_1", "pizza_3", "pizza_4", "pizza_5", "pizza_x")) env.define(id);
        doAnswer(inv -> {
            String id = inv.getArgument(0);
            return env.active.containsKey(id) ? env.active.get(id) : slot(id, null);
        }).when(env.furniture).getOrCreatePlacedSlot(anyString());
        when(env.furniture.getVariables()).thenReturn(variables);
        when(env.api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            modelRefs.add(ref);
            return new ItemStack(ref.startsWith("ia.sauce") ? Material.RED_DYE : Material.STICK);
        });
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            builtQuality = inv.getArgument(1);
            return env.stack(built, Material.PUMPKIN_PIE, 1);
        });
        adder = env.scoped(mockStatic(InventoryAdder.class));
        adder.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> {
            given.add(inv.getArgument(1));
            return rest;
        });
        load(CONFIG);
    }

    @AfterEach void close() throws Exception {
        new PizzaConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("pizzas.yml");
        Files.writeString(file, yaml);
        new PizzaConfig().load(file.toFile());
    }

    PlacedSlot slot(String id, ItemStack item) {
        PlacedSlot slot = env.place(id, item);
        when(slot.getId()).thenReturn(id);
        return slot;
    }

    ItemStack dough() {
        FoodItem dough = env.food("dough", "Wheat");
        dough.setBaseFood(4);
        dough.setBaseNutrition(4);
        return env.stack(dough, Material.WHEAT, 4);
    }

    ItemStack paste(String origin) {
        FoodItem paste = env.food("paste", origin);
        paste.setBaseFood(12);
        paste.setBaseNutrition(8);
        return env.stack(paste, Material.RED_DYE, 4);
    }

    ItemStack cheese() {
        FoodItem cheese = env.food("cheese", "Cow");
        cheese.setBaseFood(12);
        cheese.setBaseNutrition(8);
        return env.stack(cheese, Material.YELLOW_WOOL, 4);
    }

    ItemStack spice(String origin) {
        return env.stack(env.food("spice_1", origin), Material.GREEN_DYE, 4);
    }

    void hold(ItemStack item) {
        env.player.getInventory().setItemInMainHand(item);
    }

    FurnitureInteractEvent click(ItemStack held) {
        hold(held);
        FurnitureInteractEvent event = new FurnitureInteractEvent(env.player, env.furniture);
        handler.onInteract(event);
        return event;
    }

    ItemStack layer(int level) {
        PlacedSlot slot = env.active.get("pizza_" + level);
        return slot == null ? null : slot.getCurrentItem();
    }

    float lift(int level) {
        return lift("pizza_" + level);
    }

    float lift(String id) {
        ArgumentCaptor<DisplayData> data = ArgumentCaptor.forClass(DisplayData.class);
        verify(env.active.get(id), atLeastOnce()).applyDisplayData(data.capture());
        return data.getValue().getyPos();
    }

    Furniture other() {
        Furniture other = mock(Furniture.class);
        FurnitureType otherType = mock(FurnitureType.class);
        when(other.getType()).thenReturn(otherType);
        when(otherType.getId()).thenReturn("plate");
        when(other.getLoc()).thenReturn(new Location(env.world, 1, 64, 1));
        return other;
    }

    @Test
    void configReadsPeelsLayersAndBakeAndFallsBackToDefaults() throws Exception {
        assertTrue(PizzaConfig.isPeel(env.furniture));
        assertFalse(PizzaConfig.isPeel(null));
        assertFalse(PizzaConfig.isPeel(mock(Furniture.class)));
        assertEquals(2, PizzaConfig.maxToppings());
        assertEquals("meal(type=pizza)", PizzaConfig.output());
        assertEquals(2, PizzaConfig.slices());
        assertEquals(2, PizzaConfig.cookSeconds());
        assertEquals(4, PizzaConfig.burnSeconds());
        assertTrue(PizzaConfig.isBase(PizzaConfig.layerFor(env.food("dough", "Wheat"))));
        assertTrue(PizzaConfig.isSauce(PizzaConfig.layerFor(env.food("paste", "Tomato"))));
        assertEquals("cheese", PizzaConfig.layerFor(env.food("cheese", "Cow")).getId());
        assertFalse(PizzaConfig.isBase(PizzaConfig.layerFor(env.food("cheese", "Cow"))));
        assertFalse(PizzaConfig.isSauce(null));
        assertFalse(PizzaConfig.isBase(null));
        assertNull(PizzaConfig.layerFor(env.food("beef", "Cow")));
        assertNull(PizzaConfig.layerFor(null));

        new PizzaConfig().load(directory.resolve("missing.yml").toFile());
        assertFalse(PizzaConfig.isPeel(env.furniture));
        assertEquals(6, PizzaConfig.maxToppings());
        assertEquals("meal(type=pizza_slice;origin=Mixed)", PizzaConfig.output());
        assertEquals(4, PizzaConfig.slices());
        assertEquals(20, PizzaConfig.cookSeconds());
        assertEquals(60, PizzaConfig.burnSeconds());
        assertNull(PizzaConfig.layerFor(env.food("dough", "Wheat")));

        // A pizza can't burn before it's cooked.
        load("max-toppings: 0\nslices: 0\nbake:\n  cook-seconds: 30\n  burn-seconds: 10\n");
        assertEquals(1, PizzaConfig.maxToppings());
        assertEquals(1, PizzaConfig.slices());
        assertEquals(31, PizzaConfig.burnSeconds());
    }

    @Test
    void pizzasAddTheirToppingsToTheCrustAndAreNamedByThem() {
        FoodItem fried = env.food("cheese", "Cow");
        fried.setBaseFood(10);
        fried.setBaseNutrition(2);
        TagTrack cooked = new TagTrack(TrackLoader.getByString("cooked"));
        cooked.setValue(1);
        fried.addOrModifyTrack(cooked);
        fried.addOrModifyTrack(new TagTrack("plain", false, List.of()));
        fried.addOrModifyTrack(new TagTrack(TrackLoader.getByString("freshness")));
        ItemStack pizza = PizzaItems.build(env.player, List.of(dough(), paste("Tomato"), new ItemStack(Material.STONE),
                cheese(), spice("Basil"), spice("Salt"), env.stack(fried, Material.YELLOW_WOOL, 1), env.raw(1)),
                PizzaPeel.COOKED);
        assertNotNull(pizza);
        assertEquals(72, builtQuality);
        assertEquals("meal(type=pizza)", built.getId());
        assertEquals(List.of("Cheese", "Basil"), built.getIngredients());
        assertEquals(PizzaPeel.COOKED, built.getTagTrack("cooked").getValue());
        // The slice's crust (the template's 1) in place of the dough, then its half of paste 12, cheese 12, the spices,
        // beef and the fried cheese 1 each but the fried one's 10, its own cooking left out.
        double food = 1 + (12 + 12 + 1 + 1 + 10 + 1) / 2.0;
        assertEquals(food, built.getBaseFood(), 1e-9);
        double nutrition = 1 + (12 * 8 + 12 * 8 + 1 + 1 + 10 * 2 + 1) / 2.0;
        assertEquals(nutrition / food, built.getBaseNutrition(), 1e-9);
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player), argThat(l -> l.size() == 7),
                eq(CompositionContext.PIZZA)));

        env.parser.when(() -> FoodParser.parse(anyString())).thenAnswer(inv -> {
            FoodParser.Result result = new FoodParser.Result();
            result.template = env.food("pizza", "Mixed");
            result.template.setBaseFood(0);
            return result;
        });
        FoodItem empty = env.food("spice_1", "Salt");
        empty.setBaseFood(0);
        List<TagTrack> tracks = TrackLoader.oList;
        TrackLoader.oList = new ArrayList<>();
        try {
            PizzaItems.build(null, List.of(env.stack(empty, Material.GREEN_DYE, 1)), PizzaPeel.BURNT);
        } finally {
            TrackLoader.oList = tracks;
        }
        assertEquals(0, built.getBaseFood());
        assertNull(built.getTagTrack("cooked"));
        assertTrue(built.getIngredients().isEmpty());

        assertNull(PizzaItems.build(env.player, List.of(new ItemStack(Material.STONE)), PizzaPeel.COOKED));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        assertNull(PizzaItems.build(env.player, List.of(dough()), PizzaPeel.COOKED));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        assertNull(PizzaItems.build(env.player, List.of(dough()), PizzaPeel.COOKED));
    }

    @Test
    void anEmptyPeelOnlyTakesDoughAndLeavesEverythingElseToTheFurniture() {
        hold(dough());
        FurnitureInteractEvent elsewhere = new FurnitureInteractEvent(env.player, other());
        handler.onInteract(elsewhere);
        assertFalse(elsewhere.isCancelled());

        // An empty hand picks the empty peel up; something that isn't food is left alone.
        assertFalse(click(null).isCancelled());
        assertFalse(click(new ItemStack(Material.STONE)).isCancelled());

        assertTrue(click(cheese()).isCancelled());
        verify(env.player).sendMessage("§7Start a pizza with a piece of dough.");
        assertNull(layer(1));

        when(env.furniture.isAttached()).thenReturn(true);
        assertTrue(click(dough()).isCancelled());
        verify(env.player).sendMessage("§7Take the peel out of the oven first.");
        assertNull(layer(1));

        env.definitions.keySet().removeIf(id -> id.startsWith("pizza_"));
        assertFalse(click(dough()).isCancelled());
    }

    @Test
    void sneakingWithAnEmptyHandCarriesThePeel() {
        slot("pizza_1", dough());
        when(env.player.isSneaking()).thenReturn(true);
        assertFalse(click(null).isCancelled());
        assertTrue(click(cheese()).isCancelled());
    }

    @Test
    void doughSauceAndToppingsStackUpAndAnEmptyHandTakesTheTopOneBack() {
        variables.put(PizzaPeel.BAKED, "not a number");
        assertTrue(click(dough()).isCancelled());
        assertEquals(3, env.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(Material.STICK, layer(1).getType());
        assertEquals("dough", env.resolve(layer(1)).getId());
        assertEquals(List.of("ia.base_raw"), modelRefs);
        assertEquals(0f, lift(1));
        assertEquals(0, variables.get(PizzaPeel.BAKED));

        click(dough());
        verify(env.player).sendMessage("§7There's already dough on this peel.");
        click(new ItemStack(Material.STONE));
        verify(env.player).sendMessage("§7That doesn't go on a pizza.");

        click(paste("Tomato"));
        assertEquals(Material.RED_DYE, layer(2).getType());
        assertTrue(modelRefs.contains("ia.sauce_tomato"));
        assertEquals(1f / 16, lift(2), 1e-6);
        click(paste("Carrot"));
        verify(env.player).sendMessage("§7This pizza already has its sauce.");

        // Cheese has no peel model, so it lies as it looks in hand.
        click(cheese());
        assertEquals(Material.YELLOW_WOOL, layer(3).getType());
        assertEquals(1, layer(3).getAmount());
        assertEquals(1.2f / 16, lift(3), 1e-6);

        click(paste("Carrot"));
        verify(env.player, times(2)).sendMessage("§7This pizza already has its sauce.");
        click(spice("Basil"));
        assertEquals(1.5f / 16, lift(4), 1e-6);
        click(spice("Basil"));
        verify(env.player).sendMessage("§7There's no room for more toppings.");
        assertNull(layer(5));

        // An empty hand takes the top layer back, as it looks in hand.
        click(null);
        assertNull(layer(4));
        assertEquals("spice_1", env.resolve(given.get(0)).getId());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()));
        click(null);
        click(null);
        assertEquals(0, variables.get(PizzaPeel.BAKED));
        click(null);
        assertNull(layer(1));
        assertTrue(variables.isEmpty());
        assertEquals(4, given.size());
    }

    @Test
    void sauceGoesStraightOnTheDoughAndToppingsStopAtTheLastSlot() throws Exception {
        click(dough());
        click(cheese());
        click(paste("Tomato"));
        verify(env.player).sendMessage("§7Spread the sauce before the toppings.");

        load(CONFIG.replace("max-toppings: 2", "max-toppings: 9"));
        env.definitions.remove("pizza_4");
        env.definitions.remove("pizza_5");
        env.definitions.remove("pizza_x");
        click(cheese());
        assertNotNull(layer(3));
        click(cheese());
        verify(env.player).sendMessage("§7There's no room for more toppings.");
    }

    @Test
    void aBakedPizzaIsServedASliceAtATimeAndTakesNothingMore() {
        slot("pizza_1", dough());
        slot("pizza_2", paste("Tomato"));
        slot("pizza_3", cheese());
        PizzaPeel.setBake(env.furniture, 3, PizzaPeel.COOKED);

        click(cheese());
        verify(env.player).sendMessage("§7This pizza is baked; take a slice with an empty hand.");
        assertNotNull(layer(3));

        // Even in the oven, an empty hand takes a slice, and the rest of the pizza shows the slice gone.
        when(env.furniture.isAttached()).thenReturn(true);
        click(cheese());
        verify(env.player, times(1)).sendMessage("§7Take the peel out of the oven first.");
        assertTrue(click(null).isCancelled());
        assertEquals(1, given.size());
        assertEquals(Material.PUMPKIN_PIE, given.get(0).getType());
        assertEquals(List.of("Cheese"), built.getIngredients());
        assertEquals(1, PizzaPeel.left(env.furniture));
        assertTrue(modelRefs.contains("ia.base_cooked_left1"));
        assertTrue(modelRefs.contains("ia.sauce_tomato_left1"));
        assertNotNull(layer(3));
        env.server.getPluginManager().assertEventFired(DishCookedEvent.class);
        env.server.getPluginManager().clearEvents();

        // The last slice is the same as the first, and leaves the peel empty.
        FoodItem first = built;
        assertTrue(click(null).isCancelled());
        assertEquals(2, given.size());
        assertTrue(given.get(1).isSimilar(given.get(0)));
        assertSame(first, built);
        assertTrue(env.active.isEmpty());
        assertTrue(variables.isEmpty());
        assertThrows(AssertionError.class,
                () -> env.server.getPluginManager().assertEventFired(DishCookedEvent.class));
    }

    @Test
    void aRawPizzaInTheOvenIsLeftToTheFurniture() {
        slot("pizza_1", dough());
        when(env.furniture.isAttached()).thenReturn(true);
        assertFalse(click(null).isCancelled());
        assertNotNull(layer(1));
    }

    @Test
    void aPizzaThatCannotBeMadeStaysOnThePeel() {
        slot("pizza_1", dough());
        PizzaPeel.setBake(env.furniture, 3, PizzaPeel.BURNT);
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        click(null);
        verify(env.player).sendMessage("§cThis pizza can't be made right now.");
        assertNotNull(layer(1));
        assertTrue(given.isEmpty());
    }

    @Test
    void whatDoesNotFitInThePlayersInventoryDropsAtTheirFeet() {
        rest = new ItemStack(Material.WHEAT);
        click(dough());
        click(null);
        assertEquals(1, env.dropped.size());
    }

    @Test
    void aBrokenPeelDropsABakedPizzaWholeAndARawOneAsItsLayers() {
        handler.onBreak(new FurnitureBreakEvent(other(), env.player));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));

        ItemStack dough = dough();
        slot("pizza_1", dough);
        slot("pizza_2", new ItemStack(Material.STONE));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertNotSame(dough, layer(1));
        assertSame(env.resolve(dough), env.resolve(layer(1)));
        assertEquals(Material.STONE, layer(2).getType());

        ItemStack held = layer(1);
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertSame(held, layer(1));

        // A baked pizza drops as the slices left on it.
        env.active.clear();
        slot("pizza_1", dough());
        slot("pizza_2", cheese());
        PizzaPeel.setBake(env.furniture, 3, PizzaPeel.COOKED);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertEquals("meal(type=pizza)", env.resolve(layer(1)).getId());
        assertEquals(2, layer(1).getAmount());
        assertNull(layer(2));
        assertTrue(variables.isEmpty());
    }

    @Test
    void ovensBakeThePizzaWhileHotThenShowEveryLayerCookedAndBurnt() {
        Furniture oven = other();
        PizzaBaking.tick(oven, env.furniture);
        assertTrue(variables.isEmpty());

        slot("pizza_1", dough());
        slot("pizza_2", new ItemStack(Material.STONE));
        env.heated = false;
        PizzaBaking.tick(oven, env.furniture);
        assertTrue(variables.isEmpty());

        env.heated = true;
        PizzaBaking.tick(oven, env.furniture);
        assertEquals(1, PizzaPeel.elapsed(env.furniture));
        assertEquals(PizzaPeel.RAW, PizzaPeel.baked(env.furniture));
        assertTrue(modelRefs.isEmpty());
        variables.put(PizzaPeel.SLICE, "a slice cut before");
        PizzaBaking.tick(oven, env.furniture);
        assertEquals(PizzaPeel.COOKED, PizzaPeel.baked(env.furniture));
        assertEquals(List.of("ia.base_cooked"), modelRefs);
        // Slices cut after this take the new bake.
        assertFalse(variables.containsKey(PizzaPeel.SLICE));
        assertEquals(Material.STONE, layer(2).getType());
        PizzaBaking.tick(oven, env.furniture);
        PizzaBaking.tick(oven, env.furniture);
        assertEquals(PizzaPeel.BURNT, PizzaPeel.baked(env.furniture));
        assertEquals(List.of("ia.base_cooked", "ia.base_burnt"), modelRefs);
        PizzaBaking.tick(oven, env.furniture);
        assertEquals(4, PizzaPeel.elapsed(env.furniture));
        verify(InteractibleFurniture.getInstance().getFurnitureManager(), times(4)).markDirty(env.furniture);
    }

    @Test
    void loadedPizzasAreStackedAgainOnStartAndWhenTheirChunkLoadsAndShrinkWithTheirPeel() {
        env.placed.put(UUID.randomUUID(), other());
        slot("pizza_1", dough());
        slot("pizza_2", new ItemStack(Material.STONE));
        slot("pizza_3", null);
        slot("pizza_x", cheese());
        slot("pizza_4", paste("Tomato"));

        handler.start();
        assertEquals(1f / 16, lift(2), 1e-6);
        // Something that isn't a layer adds no height, and a slot that isn't numbered comes last.
        assertEquals(1f / 16, lift(4), 1e-6);
        assertEquals(1.2f / 16, lift("pizza_x"), 1e-6);
        verify(env.active.get("pizza_3"), never()).applyDisplayData(any());

        clearInvocations(env.active.get("pizza_1"));
        handler.onChunkLoad(new ChunkLoadEvent(env.chunk, false));
        verify(env.active.get("pizza_1"), never()).applyDisplayData(any());
        env.server.getScheduler().performTicks(2);
        verify(env.active.get("pizza_1")).applyDisplayData(any());

        // In an oven's cavity the peel is drawn smaller, and the layers' lift shrinks with it; out again, it grows back.
        Furniture oven = other();
        PlacedFurnitureSlot cavity = mock(PlacedFurnitureSlot.class);
        when(cavity.getNested()).thenReturn(env.furniture);
        Map<String, PlacedFurnitureSlot> cavities = new HashMap<>();
        cavities.put("tray", cavity);
        cavities.put("empty", mock(PlacedFurnitureSlot.class));
        when(oven.getActiveFurnitureSlots()).thenReturn(cavities);
        env.placed.remove(env.furniture.getEntityId());
        env.placed.put(UUID.randomUUID(), oven);
        ItemDisplay display = env.display(env.furniture.getEntityId(), new Location(env.world, 0, 64, 0),
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
        env.server.getScheduler().performTicks(10);
        assertEquals(0.8f / 16, lift(2), 1e-6);
        clearInvocations(env.active.get("pizza_2"));
        env.server.getScheduler().performTicks(10);
        verify(env.active.get("pizza_2"), never()).applyDisplayData(any());
        display.setTransformation(env.identity());
        env.server.getScheduler().performTicks(10);
        assertEquals(1f / 16, lift(2), 1e-6);
    }
}
