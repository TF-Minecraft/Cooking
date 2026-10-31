package net.tfminecraft.cooking.salad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class SaladCoverageTest {
    static final String CONFIG = """
            furniture: [BOARD]
            min-ingredients: 2
            max-ingredients: 3
            max-extras: 2
            output: meal(type=salad;origin=Mixed)
            ingredients:
              vegetable:
                types: [vegetable_chopped]
                name: "{origin}"
              fruit:
                types: [fruit_2]
              cheese:
                types: [cheese]
                name: Cheese
                names:
                  goat: Goat Cheese
              plain: 3
            extras:
              dressing:
                types: [vinegar]
                once: true
                returns-bottle: true
              seasoning:
                types: [seasoning_1, spice_1]
              plain: 3
            kinds:
              Fruit:
                only: [fruit]
              Greek:
                layers: [cheese]
                origins: [Olive]
                any-origin: [Tomato, Cucumber]
              Carrot:
                mostly: Carrot
              plain: 3
            bonuses:
              dressed:
                track: dressed
                layers: [dressing]
            """;

    Environment env;
    MockedStatic<InventoryAdder> adder;
    final List<ItemStack> given = new ArrayList<>();
    final Map<String, Object> variables = new HashMap<>();
    final Map<String, ItemStack> encoded = new HashMap<>();
    final List<String> messages = new ArrayList<>();
    FoodItem built;
    final SaladHandler handler = new SaladHandler();
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new Environment();
        env.define("food_item");
        when(env.furniture.getVariables()).thenReturn(variables);
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            return env.stack(built, Material.BOWL, 1);
        });
        adder = env.scoped(mockStatic(InventoryAdder.class));
        adder.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> {
            given.add(inv.getArgument(1));
            return null;
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
        new SaladConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("salads.yml");
        Files.writeString(file, yaml);
        new SaladConfig().load(file.toFile());
    }

    FoodItem food(String type, String category, String origin) {
        FoodItem food = env.food(type, origin);
        food.setCategory(category);
        return food;
    }

    ItemStack veg(String origin) { return env.stack(food("vegetable_chopped", "garnish", origin), Material.CARROT, 4); }
    ItemStack fruit(String origin) { return env.stack(food("fruit_2", "fruit", origin), Material.APPLE, 4); }
    ItemStack cheese(String origin) { return env.stack(food("cheese", "dairy", origin), Material.YELLOW_WOOL, 2); }
    ItemStack vinegar(String origin) { return env.stack(food("vinegar", "vinegar", origin), Material.POTION, 2); }
    ItemStack salt() { return env.stack(food("seasoning_1", "salt", "Salt"), Material.SUGAR, 1); }

    FurnitureInteractEvent click(ItemStack held) {
        env.player.getInventory().setItemInMainHand(held);
        FurnitureInteractEvent event = env.interact("input_1");
        handler.onInteract(event);
        return event;
    }

    ItemStack in(String slot) {
        PlacedSlot placed = env.active.get(slot);
        return placed == null ? null : placed.getCurrentItem();
    }

    @Test
    void configReadsBowlsIngredientsExtrasAndKindsAndFallsBackToDefaults() throws Exception {
        assertTrue(SaladConfig.isSaladFurniture(env.furniture));
        assertFalse(SaladConfig.isSaladFurniture(null));
        when(env.furniture.getType()).thenReturn(null);
        assertFalse(SaladConfig.isSaladFurniture(env.furniture));
        when(env.furniture.getType()).thenReturn(env.type);
        assertEquals(3, SaladConfig.maxIngredients());
        assertEquals(2, SaladConfig.minIngredients());
        assertEquals(2, SaladConfig.maxExtras());
        assertEquals(1, SaladConfig.bonuses().size());

        FoodItem tomato = food("vegetable_chopped", "garnish", "Tomato");
        FoodItem vinegar = food("vinegar", "vinegar", "Wine");
        SandwichLayer veg = SaladConfig.ingredientFor(tomato);
        SandwichLayer dressing = SaladConfig.extraFor(vinegar);
        assertEquals("vegetable", veg.getId());
        assertNull(SaladConfig.extraFor(tomato));
        assertNull(SaladConfig.ingredientFor(vinegar));
        assertSame(veg, SaladConfig.layerFor(tomato));
        assertSame(dressing, SaladConfig.layerFor(vinegar));
        assertNull(SaladConfig.layerFor(food("stone", "rock", "Stone")));
        assertTrue(SaladConfig.isExtra(dressing));
        assertFalse(SaladConfig.isExtra(veg));
        assertTrue(SaladConfig.isOnce(dressing));
        assertTrue(SaladConfig.returnsBottle(dressing));
        SandwichLayer seasoning = SaladConfig.extraFor(env.resolve(salt()));
        assertFalse(SaladConfig.isOnce(seasoning));
        assertFalse(SaladConfig.returnsBottle(seasoning));

        FoodItem salad = food("salad", "meal", "Mixed");
        assertTrue(SaladConfig.isSalad(salad));
        assertFalse(SaladConfig.isSalad(tomato));
        assertFalse(SaladConfig.isSalad(null));

        new SaladConfig().load(directory.resolve("missing.yml").toFile());
        assertEquals("meal(type=salad;origin=Mixed)", SaladConfig.output());
        assertEquals(5, SaladConfig.maxIngredients());
        assertNull(SaladConfig.ingredientFor(tomato));
        assertNull(SaladConfig.kindFor(List.of(), List.of()));
        load("output: plain\nmax-ingredients: 0\nmin-ingredients: 9\n");
        assertEquals(1, SaladConfig.maxIngredients());
        assertEquals(1, SaladConfig.minIngredients());
        assertTrue(SaladConfig.isSalad(salad));
    }

    @Test
    void kindsAreFruitGreekCarrotOrNoneByWhatIsInTheBowl() {
        SandwichLayer veg = SaladConfig.ingredientFor(food("vegetable_chopped", "garnish", "Tomato"));
        SandwichLayer fruit = SaladConfig.ingredientFor(food("fruit_2", "fruit", "Plum"));
        SandwichLayer cheese = SaladConfig.ingredientFor(food("cheese", "dairy", "Cow"));
        FoodItem plum = food("fruit_2", "fruit", "Plum");
        FoodItem tomato = food("vegetable_chopped", "garnish", "Tomato");
        FoodItem olive = food("vegetable_chopped", "garnish", "Olive");
        FoodItem carrot = food("vegetable_chopped", "garnish", "Carrot");
        FoodItem none = food("vegetable_chopped", "garnish", null);
        FoodItem feta = food("cheese", "dairy", "Goat");

        assertEquals("Fruit", SaladConfig.kindFor(List.of(plum, plum), List.of(fruit, fruit)).getOrigin());
        assertEquals("Greek", SaladConfig.kindFor(List.of(feta, olive, tomato), List.of(cheese, veg, veg)).getOrigin());
        assertNull(SaladConfig.kindFor(List.of(feta, olive), List.of(cheese, veg)), "Greek needs tomato or cucumber");
        assertNull(SaladConfig.kindFor(List.of(olive, tomato), List.of(veg, veg)), "Greek needs cheese");
        assertNull(SaladConfig.kindFor(List.of(feta, tomato), List.of(cheese, veg)), "Greek needs olive");
        assertEquals("Carrot", SaladConfig.kindFor(List.of(carrot, none), List.of(veg, veg)).getOrigin());
        assertNull(SaladConfig.kindFor(List.of(carrot, none, tomato), List.of(veg, veg, veg)));
    }

    @Test
    void ingredientsGoInTheBowlUntilItIsFull() throws Exception {
        assertFalse(click(null).isCancelled(), "An empty bowl and an empty hand do nothing");
        assertFalse(click(new ItemStack(Material.STONE)).isCancelled());

        ItemStack hand = veg("Tomato");
        FurnitureInteractEvent event = click(hand);
        assertTrue(event.isCancelled());
        assertEquals("Tomato", env.resolve(in("input_1")).getOrigin());
        assertEquals(1, in("input_1").getAmount());
        assertEquals(3, env.player.getInventory().getItemInMainHand().getAmount());
        verify(env.active.get("input_1")).applyDisplayData(any());
        assertTrue(SaladHandler.isMaking(env.furniture));

        click(veg("Onion"));
        click(fruit("Plum"));
        click(veg("Corn"));
        assertNull(in("input_4"));
        assertEquals("§7The bowl is full; toss the salad with an empty hand.", messages.get(0));

        load(CONFIG.replace("max-ingredients: 3", "max-ingredients: 9"));
        click(veg("Corn"));
        click(veg("Lettuce"));
        click(veg("Garlic"));
        assertEquals(2, messages.size(), "No free slot left in the bowl");
    }

    @Test
    void dressingsAndSeasoningsGoInUnseenOnceEach() throws Exception {
        FurnitureInteractEvent event = click(vinegar("Wine"));
        assertTrue(event.isCancelled());
        assertEquals(Material.POTION, env.player.getInventory().getItemInMainHand().getType());
        assertEquals(List.of(Material.GLASS_BOTTLE), given.stream().map(ItemStack::getType).toList());
        assertEquals(1, SaladHandler.extras(env.furniture).size());
        assertTrue(SaladHandler.isMaking(env.furniture));

        click(vinegar("Cider"));
        assertEquals("§7It already has a dressing.", messages.get(0));
        click(salt());
        ItemStack left = env.player.getInventory().getItemInMainHand();
        assertTrue(left.getType().isAir() || left.getAmount() == 0, "The last salt is used up");
        assertEquals(2, SaladHandler.extras(env.furniture).size());
        click(salt());
        assertEquals("§7There's enough dressing and seasoning in it already.", messages.get(1));

        // An extra already in, an unreadable entry and something that isn't food don't stop a different one.
        load(CONFIG.replace("max-extras: 2", "max-extras: 5"));
        variables.put(SaladHandler.EXTRAS, PotReference.encodeStack(salt()) + ",missing");
        encoded.put("plain", new ItemStack(Material.STONE));
        FoodItem pepper = food("spice_1", "pepper", "Black Pepper");
        List<ItemStack> extras = new ArrayList<>(SaladHandler.extras(env.furniture));
        extras.add(new ItemStack(Material.STONE));
        assertNull(SaladHandler.refuseExtra(extras, pepper, SaladConfig.extraFor(pepper)));
        assertEquals("That's already in it.", SaladHandler.refuseExtra(extras, env.resolve(salt()), SaladConfig.extraFor(pepper)));
        FoodItem nameless = food("spice_1", "spice", null);
        assertNull(SaladHandler.refuseExtra(List.of(env.stack(nameless, Material.SUGAR, 1)), pepper, SaladConfig.extraFor(pepper)));
        assertEquals("That's already in it.", SaladHandler.refuseExtra(List.of(env.stack(nameless, Material.SUGAR, 1)), nameless,
                SaladConfig.extraFor(nameless)));
        variables.put(SaladHandler.EXTRAS, 3);
        assertTrue(SaladHandler.extras(env.furniture).isEmpty());
    }

    @Test
    void sneakingWithAnEmptyHandTakesBackWhatWentInLast() {
        click(veg("Tomato"));
        click(salt());
        when(env.player.isSneaking()).thenReturn(true);
        assertTrue(click(null).isCancelled());
        assertNull(in("input_1"));
        assertEquals("Tomato", env.resolve(given.get(0)).getOrigin());
        click(null);
        assertEquals("Salt", env.resolve(given.get(1)).getOrigin());
        assertFalse(variables.containsKey(SaladHandler.EXTRAS));
        assertFalse(SaladHandler.isMaking(env.furniture));
    }

    @Test
    void tossingMakesTheSaladThatStaysInTheBowl() {
        click(veg("Tomato"));
        click(null);
        assertEquals("§7Add at least 2 ingredients before you toss it.", messages.get(0));

        click(cheese("Goat"));
        click(veg("Olive"));
        click(vinegar("Wine"));
        assertTrue(click(null).isCancelled());
        assertEquals("Greek", built.getOrigin());
        assertEquals(List.of("Tomato", "Goat Cheese", "Olive"), built.getIngredients());
        assertNotNull(built.getTagTrack("dressed"));
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player), argThat(l -> l.size() == 4),
                eq(CompositionContext.SALAD)));
        assertNotNull(in("food_item"));
        verify(env.active.get("food_item")).applyDisplayData(argThat(d -> d.getxScale() < 0.01f));
        assertFalse(variables.containsKey(SaladHandler.EXTRAS));
        assertFalse(SaladHandler.isMaking(env.furniture));

        // A tossed salad is only eaten from the bowl.
        assertFalse(click(veg("Corn")).isCancelled());
        assertFalse(click(null).isCancelled());
    }

    @Test
    void aSaladThatCannotBeMadeStaysUntossed() {
        click(veg("Tomato"));
        click(veg("Onion"));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        click(null);
        assertEquals("§cThis salad can't be made right now.", messages.get(0));
        assertNull(in("food_item"));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        assertNull(SaladItems.build(env.player, List.of(veg("Tomato")), List.of()));
        assertNull(SaladItems.build(env.player, List.of(), List.of(salt())));
    }

    @Test
    void saladsSumWhatIsInThemAndOnlyIngredientsNameThem() {
        SaladItems.build(env.player, List.of(veg("Tomato"), salt(), new ItemStack(Material.STONE), veg("Onion")),
                List.of(vinegar("Wine")));
        assertEquals(List.of("Tomato", "Onion"), built.getIngredients());
        assertEquals("Beef", built.getOrigin(), "No kind takes it, so it keeps the output's origin");
    }

    @Test
    void aBrokenBowlDropsWhatWasNotTossedYet() throws Exception {
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertTrue(env.dropped.isEmpty());
        click(veg("Tomato"));
        click(salt());
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertEquals(2, env.dropped.size());
        assertNull(in("input_1"));
        assertFalse(variables.containsKey(SaladHandler.EXTRAS));

        env.dropped.clear();
        click(veg("Tomato"));
        click(veg("Onion"));
        click(null);
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertTrue(env.dropped.isEmpty(), "A tossed salad goes with the bowl");

        load(CONFIG.replace("[BOARD]", "[BOWL]"));
        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertFalse(click(veg("Corn")).isCancelled());
        assertFalse(SaladHandler.isMaking(env.furniture));
    }

    @Test
    void anIngredientWithoutFoodDataStillComesBack() {
        PlacedSlot slot = env.place("input_1", new ItemStack(Material.STONE));
        assertEquals(1, SaladHandler.ingredients(env.furniture).size());
        when(env.player.isSneaking()).thenReturn(true);
        click(null);
        assertEquals(Material.STONE, given.get(0).getType());
        verify(slot).clearModel();
        env.place("input_1", new ItemStack(Material.AIR));
        assertTrue(SaladHandler.ingredients(env.furniture).isEmpty());
    }
}
