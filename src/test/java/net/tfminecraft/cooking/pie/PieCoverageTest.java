package net.tfminecraft.cooking.pie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class PieCoverageTest {
    static final String CONFIG = """
            furniture: [BOARD]
            slot: pie
            max-fillings: 3
            output: meal(type=pie)
            bake:
              cook-seconds: 2
              burn-seconds: 4
            crust:
              types: [dough]
              models:
                - burnt ia.crust_burnt
                - cooked ia.crust_cooked
                - filled ia.crust_filled
                - ia.crust_raw
            fillings:
              fruit:
                types: [fruit_2]
                name: "{origin}"
              meat:
                types: [meat_red_meat]
                name: "{origin}"
              mash:
                types: [mashed_potatoes]
                name: Mashed Potato
              spice:
                types: [spice_1]
                names:
                  salt: ""
              plain: 3
            kinds:
              Shepherds:
                name: Shepherd's
                layers: [meat, mash]
                only: [meat, mash]
              Meat:
                name: Meat
                only: [meat]
              Apple:
                name: Apple
                only-origins: [Apple]
              Mixed Berry:
                name: Mixed Berry
                only-origins: [Strawberry, Cherry]
                min-origins: 2
              Mixed: {}
              odd: 3
            bonuses:
              size:
                track: dish_size
                count: fillings
                steps:
                  3: hearty
            """;

    Environment env;
    MockedStatic<InventoryAdder> adder;
    final List<String> modelRefs = new ArrayList<>();
    final List<ItemStack> given = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    final Map<String, Object> variables = new HashMap<>();
    final Map<String, ItemStack> encoded = new HashMap<>();
    ItemStack rest;
    FoodItem built;
    final PieHandler handler = new PieHandler();
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new Environment();
        env.define("pie");
        doAnswer(inv -> {
            String id = inv.getArgument(0);
            return env.active.containsKey(id) ? env.active.get(id) : slot(id, null);
        }).when(env.furniture).getOrCreatePlacedSlot(anyString());
        when(env.furniture.getVariables()).thenReturn(variables);
        when(env.api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            modelRefs.add(ref);
            // There's no burnt look, so a burnt pie keeps the look it had.
            return new ItemStack(ref.contains("burnt") ? Material.DIRT : Material.STICK);
        });
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            return env.stack(built, Material.PUMPKIN_PIE, 1);
        });
        adder = env.scoped(mockStatic(InventoryAdder.class));
        adder.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> {
            given.add(inv.getArgument(1));
            return rest;
        });
        MockedStatic<PotReference> codec = env.scoped(mockStatic(PotReference.class));
        codec.when(() -> PotReference.encodeStack(any())).thenAnswer(inv -> {
            String key = "e" + encoded.size();
            encoded.put(key, inv.getArgument(0));
            return key;
        });
        codec.when(() -> PotReference.decodeStack(anyString())).thenAnswer(inv -> encoded.get((String) inv.getArgument(0)));
        doAnswer(inv -> { messages.add(inv.getArgument(0)); return null; }).when(env.player).sendMessage(anyString());
        load(CONFIG);
    }

    @AfterEach void close() {
        new PieConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("pies.yml");
        Files.writeString(file, yaml);
        new PieConfig().load(file.toFile());
    }

    PlacedSlot slot(String id, ItemStack item) {
        PlacedSlot slot = env.place(id, item);
        when(slot.getId()).thenReturn(id);
        return slot;
    }

    FoodItem food(String type, String origin, double food, double nutrition) {
        FoodItem item = env.food(type, origin);
        item.setBaseFood(food);
        item.setBaseNutrition(nutrition);
        return item;
    }

    ItemStack dough() { return env.stack(food("dough", "Wheat", 4, 4), Material.WHEAT, 4); }
    ItemStack fruit(String origin) { return env.stack(food("fruit_2", origin, 10, 8), Material.APPLE, 4); }
    ItemStack meat(String origin) { return env.stack(food("meat_red_meat", origin, 20, 8), Material.BEEF, 4); }
    ItemStack mash() { return env.stack(food("mashed_potatoes", "Potato", 12, 6), Material.BAKED_POTATO, 4); }
    ItemStack spice(String origin) { return env.stack(food("spice_1", origin, 1, 1), Material.GREEN_DYE, 4); }

    FurnitureInteractEvent click(ItemStack held) {
        env.player.getInventory().setItemInMainHand(held);
        FurnitureInteractEvent event = new FurnitureInteractEvent(env.player, env.furniture);
        handler.onInteract(event);
        return event;
    }

    ItemStack crust() {
        PlacedSlot slot = env.active.get("pie");
        return slot == null ? null : slot.getCurrentItem();
    }

    List<String> fillingIds() {
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : PieTin.fillings(env.furniture)) ids.add(env.resolve(stack).getOrigin());
        return ids;
    }

    void filled(ItemStack... fillings) {
        slot("pie", dough());
        PieTin.setFillings(env.furniture, new ArrayList<>(List.of(fillings)));
    }

    Furniture other() {
        Furniture other = mock(Furniture.class);
        FurnitureType otherType = mock(FurnitureType.class);
        when(other.getType()).thenReturn(otherType);
        when(otherType.getId()).thenReturn("plate");
        when(other.getLoc()).thenReturn(new Location(env.world, 1, 64, 1));
        return other;
    }

    FoodItem pie(ItemStack... fillings) {
        assertNotNull(PieItems.build(env.player, dough(), List.of(fillings), PieTin.COOKED));
        return built;
    }

    @Test
    void configReadsTinsLayersKindsAndBakeAndFallsBackToDefaults() throws Exception {
        assertTrue(PieConfig.isTin(env.furniture));
        assertFalse(PieConfig.isTin(null));
        assertFalse(PieConfig.isTin(mock(Furniture.class)));
        assertEquals("pie", PieConfig.slot());
        assertEquals(1, PieConfig.minFillings());
        assertEquals(3, PieConfig.maxFillings());
        assertEquals("meal(type=pie)", PieConfig.output());
        assertEquals(2, PieConfig.cookSeconds());
        assertEquals(4, PieConfig.burnSeconds());
        assertEquals(1, PieConfig.bonuses().size());
        FoodItem dough = env.food("dough", "Wheat");
        assertTrue(PieConfig.isCrust(PieConfig.crustFor(dough)));
        assertSame(PieConfig.crustFor(dough), PieConfig.layerFor(dough));
        assertNull(PieConfig.fillingFor(dough));
        assertEquals("meat", PieConfig.layerFor(env.food("meat_red_meat", "Beef")).getId());
        assertFalse(PieConfig.isCrust(PieConfig.layerFor(env.food("meat_red_meat", "Beef"))));
        assertFalse(PieConfig.isCrust(null));
        assertNull(PieConfig.layerFor(env.food("cheese", "Cow")));
        assertNull(PieConfig.layerFor(null));

        new PieConfig().load(directory.resolve("missing.yml").toFile());
        assertFalse(PieConfig.isTin(env.furniture));
        assertEquals("pie", PieConfig.slot());
        assertEquals(1, PieConfig.minFillings());
        assertEquals(3, PieConfig.maxFillings());
        assertEquals("meal(type=pie;origin=Mixed)", PieConfig.output());
        assertEquals(5, PieConfig.cookSeconds());
        assertEquals(10, PieConfig.burnSeconds());
        assertNull(PieConfig.crustFor(dough));
        assertNull(PieConfig.layerFor(dough));
        assertNull(PieConfig.kindFor(List.of(), List.of()));
        assertTrue(PieConfig.bonuses().isEmpty());

        // A pie can't burn before it's cooked, nor need more fillings than it takes.
        load("slot: tin\nmin-fillings: 5\nmax-fillings: 0\nbake:\n  cook-seconds: 30\n  burn-seconds: 10\n");
        assertEquals("tin", PieConfig.slot());
        assertEquals(1, PieConfig.maxFillings());
        assertEquals(1, PieConfig.minFillings());
        assertEquals(31, PieConfig.burnSeconds());
    }

    @Test
    void kindsAreShepherdsMeatAppleOrMixedBerryAndTheRestAreMixed() {
        SandwichLayer fruit = PieConfig.fillingFor(env.food("fruit_2", "Apple"));
        SandwichLayer meat = PieConfig.fillingFor(env.food("meat_red_meat", "Beef"));
        SandwichLayer mash = PieConfig.fillingFor(env.food("mashed_potatoes", "Potato"));
        FoodItem apple = env.food("fruit_2", "Apple");
        FoodItem cherry = env.food("fruit_2", "Cherry");
        FoodItem strawberry = env.food("fruit_2", "Strawberry");
        FoodItem beef = env.food("meat_red_meat", "Beef");
        FoodItem potato = env.food("mashed_potatoes", "Potato");
        assertEquals("Shepherds", PieConfig.kindFor(List.of(beef, potato), List.of(meat, mash)).getOrigin());
        assertEquals("Meat", PieConfig.kindFor(List.of(beef, beef), List.of(meat, meat)).getOrigin());
        assertEquals("Mixed", PieConfig.kindFor(List.of(potato), List.of(mash)).getOrigin());
        SaladKind applePie = PieConfig.kindFor(List.of(apple, apple), List.of(fruit, fruit));
        assertEquals("Apple", applePie.getOrigin());
        assertEquals("Apple", applePie.getName());
        assertEquals("Mixed", PieConfig.kindFor(List.of(apple, cherry), List.of(fruit, fruit)).getOrigin(),
                "An apple and a cherry are neither an apple pie nor a berry pie");
        assertEquals("Mixed Berry", PieConfig.kindFor(List.of(cherry, strawberry), List.of(fruit, fruit)).getOrigin());
        SaladKind single = PieConfig.kindFor(List.of(cherry, cherry), List.of(fruit, fruit));
        assertEquals("Mixed", single.getOrigin(), "Mixed berry takes two kinds of berry");
        assertNull(single.getName());
    }

    @Test
    void piesSumTheirDoughAndFillingsWithThePiesOwnBakeAndAreNamedByTheirKind() throws Exception {
        FoodItem raw = food("meat_red_meat", "Beef", 20, 8);
        TagTrack uncooked = new TagTrack(TrackLoader.getByString("cooked"));
        uncooked.setValue(0);
        raw.addOrModifyTrack(uncooked);
        FoodItem cut = food("mashed_potatoes", "Potato", 12, 6);
        cut.addOrModifyTrack(new TagTrack("plain", false, List.of()));
        TagTrack mashed = new TagTrack(TrackLoader.getByString("mashed"));
        cut.addOrModifyTrack(mashed);
        ItemStack stack = PieItems.build(env.player, dough(), List.of(env.stack(raw, Material.BEEF, 1),
                env.stack(cut, Material.BAKED_POTATO, 1), spice("Salt")), PieTin.COOKED);
        assertNotNull(stack);
        assertEquals("meal(type=pie)", built.getId());
        // The raw beef counts whole: the pie's bake cooks it. Dough 4, beef 20, mash 12 (and what its mashing does),
        // salt 1.
        double mash = 12 * mashed.getCurrentStep().getFoodMultiplier();
        assertEquals(4 + 20 + mash + 1, built.getBaseFood(), 1e-9);
        double nutrition = 4 * 4 + 20 * 8 + mash * 6 * mashed.getCurrentStep().getNutritionMultiplier() + 1;
        assertEquals(nutrition / (4 + 20 + mash + 1), built.getBaseNutrition(), 1e-9);
        assertEquals("Mixed", built.getOrigin(), "Salt isn't meat or mash, so it's no shepherd's pie");
        assertEquals(List.of("Beef", "Mashed Potato"), built.getIngredients(), "Salt doesn't name it");
        assertEquals(PieTin.COOKED, built.getTagTrack("cooked").getValue());
        assertNotNull(built.getTagTrack("warmth"), "It comes out of the oven hot");
        assertNotNull(built.getTagTrack("dish_size"), "Three fillings make it hearty");
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player), argThat(l -> l.size() == 4),
                eq(CompositionContext.PIE)));

        assertEquals("Shepherds", pie(meat("Beef"), mash()).getOrigin());
        assertEquals(List.of("Shepherd's"), built.getIngredients());
        assertNull(built.getTagTrack("dish_size"));
        assertEquals("Apple", pie(fruit("Apple")).getOrigin());
        assertEquals(List.of("Apple"), built.getIngredients());

        // Something that isn't food, or isn't a filling, adds nothing to its name.
        PieItems.build(env.player, new ItemStack(Material.STONE), List.of(new ItemStack(Material.STONE),
                env.stack(env.food("cheese", "Cow"), Material.YELLOW_WOOL, 1), fruit("Cherry")), PieTin.BURNT);
        assertEquals(List.of("Cherry"), built.getIngredients());
        assertEquals(PieTin.BURNT, built.getTagTrack("cooked").getValue());

        load(CONFIG.replaceAll("(?s)kinds:.*?bonuses:", "bonuses:"));
        List<TagTrack> tracks = TrackLoader.oList;
        TrackLoader.oList = new ArrayList<>();
        try {
            pie(fruit("Cherry"));
        } finally {
            TrackLoader.oList = tracks;
        }
        assertEquals("Beef", built.getOrigin(), "No kind takes it, so it keeps the output's origin");
        assertEquals(List.of("Cherry"), built.getIngredients());
        assertNull(built.getTagTrack("cooked"));

        assertNull(PieItems.build(env.player, new ItemStack(Material.STONE), List.of(), PieTin.COOKED));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        assertNull(PieItems.build(env.player, dough(), List.of(), PieTin.COOKED));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        assertNull(PieItems.build(env.player, dough(), List.of(), PieTin.COOKED));
    }

    @Test
    void anEmptyTinOnlyTakesDoughAndLeavesEverythingElseToTheFurniture() {
        env.player.getInventory().setItemInMainHand(dough());
        FurnitureInteractEvent elsewhere = new FurnitureInteractEvent(env.player, other());
        handler.onInteract(elsewhere);
        assertFalse(elsewhere.isCancelled());

        // An empty hand picks the empty tin up; something that isn't food is left alone.
        assertFalse(click(null).isCancelled());
        assertFalse(click(new ItemStack(Material.STONE)).isCancelled());

        assertTrue(click(fruit("Apple")).isCancelled());
        assertEquals(List.of("§7Line the tin with a piece of dough first."), messages);
        assertNull(crust());

        when(env.furniture.isAttached()).thenReturn(true);
        assertTrue(click(dough()).isCancelled());
        assertEquals("§7Take the tin out of the oven first.", messages.get(1));
        assertNull(crust());
    }

    @Test
    void sneakingWithAnEmptyHandCarriesTheTin() {
        filled(fruit("Apple"));
        when(env.player.isSneaking()).thenReturn(true);
        assertFalse(click(null).isCancelled());
        assertEquals(List.of("Apple"), fillingIds());
    }

    @Test
    void doughLinesTheTinFillingsGoUnderItsCrustAndAnEmptyHandTakesTheLastBack() {
        variables.put(PieTin.BAKED, "not a number");
        assertTrue(click(dough()).isCancelled());
        assertEquals(3, env.player.getInventory().getItemInMainHand().getAmount());
        assertEquals("dough", env.resolve(crust()).getId());
        assertEquals(List.of("ia.crust_raw"), modelRefs);
        assertEquals(0, variables.get(PieTin.BAKED));
        assertFalse(PieTin.ready(env.furniture));

        click(dough());
        click(env.stack(env.food("cheese", "Cow"), Material.YELLOW_WOOL, 1));
        assertEquals(List.of("§7There's already dough in this tin.", "§7That doesn't go in a pie."), messages);

        variables.put(PieTin.ELAPSED, 1);
        click(fruit("Apple"));
        assertEquals(List.of("Apple"), fillingIds());
        assertEquals("ia.crust_filled", modelRefs.get(modelRefs.size() - 1));
        assertEquals(0, PieTin.elapsed(env.furniture), "A new filling bakes through from the start");
        assertTrue(PieTin.ready(env.furniture));
        click(fruit("Cherry"));
        click(meat("Beef"));
        click(mash());
        assertEquals("§7The pie is full; bake it in the oven.", messages.get(2));
        assertEquals(List.of("Apple", "Cherry", "Beef"), fillingIds());

        // An empty hand takes back the last filling, then the dough as it looks in hand.
        assertTrue(click(null).isCancelled());
        assertEquals("Beef", env.resolve(given.get(0)).getOrigin());
        assertEquals(List.of("Apple", "Cherry"), fillingIds());
        click(null);
        click(null);
        assertFalse(variables.containsKey(PieTin.FILLINGS));
        assertEquals("ia.crust_raw", modelRefs.get(modelRefs.size() - 1));
        click(null);
        assertEquals("dough", env.resolve(given.get(3)).getId());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), any(), isNull()));
        assertNull(crust());
        assertTrue(variables.isEmpty());
    }

    @Test
    void aBakedPieComesOutWholeEvenFromTheOvenAndTakesNothingMore() {
        filled(fruit("Apple"), fruit("Apple"));
        PieTin.setBake(env.furniture, 3, PieTin.COOKED);

        click(fruit("Apple"));
        assertEquals(List.of("§7This pie is baked; take it out with an empty hand."), messages);
        when(env.furniture.isAttached()).thenReturn(true);
        click(fruit("Apple"));
        assertEquals("§7Take the tin out of the oven first.", messages.get(1));

        assertTrue(click(null).isCancelled());
        assertEquals(1, given.size());
        assertEquals(Material.PUMPKIN_PIE, given.get(0).getType());
        assertEquals("Apple", built.getOrigin());
        assertNull(crust());
        assertTrue(variables.isEmpty());
        env.server.getPluginManager().assertEventFired(DishCookedEvent.class);
    }

    @Test
    void aRawPieInTheOvenIsLeftToTheFurniture() {
        filled(fruit("Apple"));
        when(env.furniture.isAttached()).thenReturn(true);
        assertFalse(click(null).isCancelled());
        assertEquals(List.of("Apple"), fillingIds());
    }

    @Test
    void aPieThatCannotBeMadeStaysInTheTin() {
        filled(fruit("Apple"));
        PieTin.setBake(env.furniture, 5, PieTin.BURNT);
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        click(null);
        assertEquals(List.of("§cThis pie can't be made right now."), messages);
        assertNotNull(crust());
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
    void aBrokenTinDropsABakedPieWholeAndARawOneAsItsDoughAndFillings() {
        handler.onBreak(new FurnitureBreakEvent(other(), env.player));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertTrue(env.dropped.isEmpty());

        filled(fruit("Apple"), meat("Beef"));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertEquals(List.of("dough", "fruit_2", "meat_red_meat"), env.dropped.stream().map(i -> env.resolve(i).getId()).toList());
        assertNull(crust());
        assertTrue(variables.isEmpty());

        env.dropped.clear();
        filled(fruit("Apple"));
        PieTin.setBake(env.furniture, 3, PieTin.COOKED);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertEquals(List.of(Material.PUMPKIN_PIE), env.dropped.stream().map(ItemStack::getType).toList());

        // A baked pie that can't be made drops what it was made from; dough the plugin can't read drops as it is.
        env.dropped.clear();
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        slot("pie", new ItemStack(Material.STONE));
        PieTin.setBake(env.furniture, 3, PieTin.COOKED);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertEquals(List.of(Material.STONE), env.dropped.stream().map(ItemStack::getType).toList());

        env.dropped.clear();
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        ItemStack dough = dough();
        slot("pie", dough);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, null));
        assertEquals("dough", env.resolve(env.dropped.get(0)).getId());
    }

    @Test
    void onlyAFilledPieGoesInTheOvenButAnEmptyTinDoes() {
        assertNull(PieTin.ovenRefusal(env.furniture));
        slot("pie", dough());
        assertEquals("§cFill the pie before baking it.", PieTin.ovenRefusal(env.furniture));
        PieTin.setFillings(env.furniture, new ArrayList<>(List.of(fruit("Apple"))));
        assertNull(PieTin.ovenRefusal(env.furniture));

        // An empty slot, or one holding air, is no dough.
        env.active.clear();
        slot("pie", null);
        assertNull(PieTin.crust(env.furniture));
        slot("pie", new ItemStack(Material.AIR));
        assertNull(PieTin.crust(env.furniture));
        variables.put(PieTin.FILLINGS, 7);
        assertTrue(PieTin.fillings(env.furniture).isEmpty());
        variables.put(PieTin.FILLINGS, "gone,e0");
        assertEquals(1, PieTin.fillings(env.furniture).size(), "A filling that can't be read is lost");
    }

    @Test
    void ovensBakeOnlyAFilledPieWhileHotThenShowItCookedAndBurnt() {
        Furniture oven = other();
        PieBaking.tick(oven, env.furniture);
        assertTrue(variables.isEmpty());

        slot("pie", dough());
        PieBaking.tick(oven, env.furniture);
        assertTrue(variables.isEmpty(), "Dough alone doesn't bake");

        PieTin.setFillings(env.furniture, new ArrayList<>(List.of(fruit("Apple"))));
        env.heated = false;
        PieBaking.tick(oven, env.furniture);
        assertFalse(variables.containsKey(PieTin.ELAPSED));

        env.heated = true;
        PieBaking.tick(oven, env.furniture);
        assertEquals(1, PieTin.elapsed(env.furniture));
        assertEquals(PieTin.RAW, PieTin.baked(env.furniture));
        assertTrue(modelRefs.isEmpty());
        PieBaking.tick(oven, env.furniture);
        assertEquals(PieTin.COOKED, PieTin.baked(env.furniture));
        assertEquals(List.of("ia.crust_cooked"), modelRefs);
        PieBaking.tick(oven, env.furniture);
        PieBaking.tick(oven, env.furniture);
        assertEquals(PieTin.BURNT, PieTin.baked(env.furniture));
        // No burnt look exists, so it falls back to the next one that fits.
        assertEquals(List.of("ia.crust_cooked", "ia.crust_burnt", "ia.crust_filled"), modelRefs);
        PieBaking.tick(oven, env.furniture);
        assertEquals(4, PieTin.elapsed(env.furniture));
        verify(InteractibleFurniture.getInstance().getFurnitureManager(), times(4)).markDirty(env.furniture);

        variables.put(PieTin.BAKED, 9);
        assertEquals(PieTin.BURNT, PieTin.baked(env.furniture));
        variables.put(PieTin.BAKED, -3);
        assertEquals(PieTin.RAW, PieTin.baked(env.furniture));
    }

    @Test
    void aTinWithoutAnyLookForItsDoughShowsTheDoughAsItIs() throws Exception {
        // Something in the slot that isn't the crust keeps its look.
        ItemStack stone = new ItemStack(Material.STONE);
        slot("pie", stone);
        PieTin.remodel(env.furniture);
        assertSame(stone, crust());
        PieTin.remodel(other());

        load(CONFIG.replaceAll("(?s)  models:.*?fillings:", "fillings:"));
        ItemStack dough = dough();
        slot("pie", dough);
        PieTin.remodel(env.furniture);
        assertSame(dough, crust());
        assertTrue(modelRefs.isEmpty());
    }
}
