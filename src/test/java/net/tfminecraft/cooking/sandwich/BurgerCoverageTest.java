package net.tfminecraft.cooking.sandwich;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.OverrideData;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/**
 * Burgers: fried meat goes in a sandwich, and the shipped sandwiches.yml kinds make a sandwich with fried beef a
 * Hamburger (a Cheeseburger with cheese) and one with fried pork, lettuce and tomato a BLT, named by their types.yml
 * override.
 */
class BurgerCoverageTest {
    static final File SHIPPED = new File("src/main/resources/sandwiches.yml");

    Environment env;
    FoodItem built;
    final List<String> modelRefs = new ArrayList<>();
    @TempDir Path directory;

    @AfterEach void close() {
        if (env != null) env.close();
    }

    /** A board with the shipped sandwiches.yml, building sandwiches without the item layer. */
    void board() {
        env = new Environment();
        for (String id : List.of("sandwich_1", "sandwich_2", "sandwich_3", "sandwich_4", "sandwich_5")) env.define(id);
        when(env.type.getId()).thenReturn("cutting_board");
        when(env.api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            modelRefs.add(ref);
            boolean exists = List.of("ia.tfmc_cooking:sandwich_meat", "ia.tfmc_cooking:sandwich_meat_raw",
                    "ia.tfmc_cooking:sandwich_meat_burnt", "ia.tfmc_cooking:sandwich_meat_pork",
                    "ia.tfmc_cooking:sandwich_meat_pork_rotten", "ia.tfmc_cooking:sandwich_bread_cooked").contains(ref);
            return new ItemStack(exists ? Material.STICK : Material.DIRT);
        });
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            return env.stack(built, Material.BREAD, 1);
        });
        env.scoped(mockStatic(InventoryAdder.class)).when(() -> InventoryAdder.addItem(any(), any())).thenReturn(null);
        new SandwichConfig().load(SHIPPED);
    }

    FoodItem cooked(String type, String origin, int step) {
        FoodItem food = env.food(type, origin);
        TagTrack cooked = new TagTrack(TrackLoader.getByString("cooked"));
        cooked.setValue(step);
        food.addOrModifyTrack(cooked);
        return food;
    }

    ItemStack item(FoodItem food) {
        return env.stack(food, Material.PAPER, 4);
    }

    ItemStack bread() {
        return item(cooked("bread_slice", null, 1));
    }

    ItemStack beef(int step) {
        return item(cooked("meat_red_meat", "Beef", step));
    }

    ItemStack veg(String origin) {
        return item(env.food("vegetable_chopped", origin));
    }

    ItemStack cheese() {
        return item(env.food("cheese", "Cow"));
    }

    /** The sandwich these fillings make between two slices. */
    FoodItem sandwich(ItemStack... fillings) {
        List<ItemStack> layers = new ArrayList<>();
        layers.add(bread());
        layers.addAll(List.of(fillings));
        layers.add(bread());
        assertNotNull(SandwichItems.build(env.player, layers));
        return built;
    }

    String step(FoodItem food, String track) {
        TagTrack tagged = food.getTagTrack(track);
        return tagged == null ? null : tagged.getCurrentStep().getId();
    }

    @Test
    void friedBeefMakesAHamburgerAndWithCheeseACheeseburgerThatKeepTheSandwichBonuses() {
        board();
        FoodItem hamburger = sandwich(beef(1), veg("Tomato"));
        assertEquals("Hamburger", hamburger.getOrigin());
        assertEquals(List.of("Beef", "Tomato"), hamburger.getIngredients());

        FoodItem cheeseburger = sandwich(beef(1), cheese(), veg("Lettuce"));
        assertEquals("Cheeseburger", cheeseburger.getOrigin());
        assertEquals(List.of("Beef", "Cheese", "Lettuce"), cheeseburger.getIngredients());
        assertEquals("hearty", step(cheeseburger, "dish_size"), "Three fillings: Hearty");

        FoodItem buttered = sandwich(item(env.food("butter", "Cow")), beef(1), cheese(), veg("Tomato"),
                item(env.food("catsup", "Tomato")));
        assertEquals("Cheeseburger", buttered.getOrigin());
        assertEquals("loaded", step(buttered, "dish_size"));
        assertEquals("buttered", step(buttered, "buttered"));
        assertEquals("well_rounded", step(buttered, "well_rounded"));

        assertEquals("Hamburger", sandwich(beef(1), item(env.food("jam", "Plum"))).getOrigin(),
                "A kind beats the jam's sandwich-origin");
        assertEquals("Jam", sandwich(item(env.food("jam", "Plum")), veg("Tomato")).getOrigin());
    }

    @Test
    void rawBoiledOrBurntBeefMakesAPlainBeefSandwich() {
        board();
        // The test output line has origin Beef: the sandwich keeps it when no kind takes it.
        for (int step : new int[] {0, 2, 3}) {
            FoodItem plain = sandwich(beef(step), cheese());
            assertEquals("Beef", plain.getOrigin(), "cooked step " + step);
            assertEquals(List.of("Beef", "Cheese"), plain.getIngredients());
        }
        assertEquals("Beef", sandwich(beef(1), beef(0)).getOrigin(), "Every meat must be fried");
        assertEquals("Beef", sandwich(item(cooked("meat_red_meat", "Venison", 1))).getOrigin());
    }

    @Test
    void friedPorkLettuceAndTomatoMakeABlt() {
        board();
        ItemStack pork = item(cooked("meat_pork", "Pork", 1));
        FoodItem blt = sandwich(pork, veg("Lettuce"), veg("Tomato"));
        assertEquals("BLT", blt.getOrigin());
        assertEquals(List.of("Pork", "Lettuce", "Tomato"), blt.getIngredients());
        assertEquals("Beef", sandwich(item(cooked("meat_pork", "Pork", 1)), veg("Lettuce")).getOrigin());
        assertEquals("Beef", sandwich(item(cooked("meat_pork", "Pork", 0)), veg("Lettuce"), veg("Tomato")).getOrigin());
        assertEquals("Hamburger", sandwich(item(cooked("meat_pork", "Pork", 1)), beef(1), veg("Lettuce"),
                veg("Tomato")).getOrigin(), "Beef comes first: a bacon burger is a burger");

        FoodItem sausage = sandwich(item(cooked("sausage", "Mixed", 1)), item(cooked("meat_poultry", "Chicken", 1)));
        assertEquals(List.of("Sausage", "Chicken"), sausage.getIngredients());
    }

    @Test
    void meatLiesOnTheBoardAsAPattyOrPorkRashersByItsCookedState() {
        board();
        SandwichLayer meat = SandwichConfig.layerFor(env.food("meat_red_meat", "Beef"));
        assertEquals("meat", meat.getId());
        assertEquals(0.5, meat.getThickness());
        FoodItem fried = cooked("meat_red_meat", "Beef", 1);
        meat.model(fried, item(fried));
        assertEquals(List.of("ia.tfmc_cooking:sandwich_meat_beef", "ia.tfmc_cooking:sandwich_meat"), modelRefs);
        modelRefs.clear();
        FoodItem raw = cooked("meat_red_meat", "Beef", 0);
        meat.model(raw, item(raw));
        assertEquals(List.of("ia.tfmc_cooking:sandwich_meat_beef_raw", "ia.tfmc_cooking:sandwich_meat_raw"), modelRefs);
        modelRefs.clear();
        FoodItem pork = cooked("meat_pork", "Pork", 1);
        meat.model(pork, item(pork));
        assertEquals(List.of("ia.tfmc_cooking:sandwich_meat_pork"), modelRefs);

        // On the board: bread, then a fried patty lifted by the slice's thickness.
        env.player.getInventory().setItemInMainHand(bread());
        FurnitureInteractEvent first = env.interact("input_1");
        new SandwichHandler().onInteract(first);
        env.player.getInventory().setItemInMainHand(beef(1));
        new SandwichHandler().onInteract(env.interact("input_1"));
        PlacedSlot patty = env.active.get("sandwich_2");
        assertEquals(Material.STICK, patty.getCurrentItem().getType());
        assertEquals("meat_red_meat", env.resolve(patty.getCurrentItem()).getId());
    }

    @Test
    void kindTagRulesNeedEveryFoodOfTheirLayerAtOneOfTheirSteps() throws Exception {
        board();
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                tags:
                  meat: [COOKED, burnt]
                  vegetable: []
                """);
        SaladKind kind = new SaladKind("Grilled", config);
        SandwichLayer meat = SandwichConfig.layerFor(env.food("meat_pork", "Pork"));
        SandwichLayer veg = SandwichConfig.layerFor(env.food("vegetable_chopped", "Tomato"));
        assertTrue(kind.matches(List.of(cooked("meat_pork", "Pork", 2)), List.of(meat)));
        assertTrue(kind.matches(List.of(cooked("meat_pork", "Pork", 1)), List.of(meat)));
        assertFalse(kind.matches(List.of(cooked("meat_pork", "Pork", 0)), List.of(meat)));
        assertFalse(kind.matches(List.of(env.food("meat_pork", "Pork")), List.of(meat)), "No cooked track at all");
        assertFalse(kind.matches(List.of(env.food("vegetable_chopped", "Tomato")), List.of(veg)));
        assertTrue(kind.matches(List.of(), List.of()), "A layer that isn't there needs nothing");
        assertTrue(new SaladKind("Any", new MemoryConfiguration()).matches(List.of(cooked("meat_pork", "Pork", 0)),
                List.of(meat)));
    }

    @Test
    void kindsAreReadFromSandwichesYmlAndNoneMeansNoKind() throws Exception {
        board();
        FoodItem beef = cooked("meat_red_meat", "Beef", 1);
        SandwichLayer meat = SandwichConfig.layerFor(beef);
        assertEquals("Hamburger", SandwichConfig.kindFor(List.of(beef), List.of(meat)).getOrigin());
        assertNull(SandwichConfig.kindFor(List.of(), List.of()));

        Path file = directory.resolve("sandwiches.yml");
        Files.writeString(file, """
                layers:
                  meat:
                    types: [meat_red_meat]
                kinds:
                  Plain: 3
                  Steak: {}
                """);
        new SandwichConfig().load(file.toFile());
        assertEquals("Steak", SandwichConfig.kindFor(List.of(beef), List.of(SandwichConfig.layerFor(beef))).getOrigin());
        new SandwichConfig().load(directory.resolve("missing.yml").toFile());
        assertNull(SandwichConfig.kindFor(List.of(beef), List.of(meat)));
    }

    @Test
    void aSandwichsOriginOverrideNamesItWhetherItIsComposedOrBuiltFromALine() {
        MockBukkit.mock();
        Cooking previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        List<FoodItem> foods = new ArrayList<>(FoodLoader.oList);
        try {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("name", "{prefixes}{fillers}Sandwich");
            config.set("food", 20);
            config.set("nutrition", 6);
            FoodItem sandwich = new FoodItem("sandwich", config);
            sandwich.setModel(new FoodModel(new ItemStack(Material.BREAD)));
            FoodLoader.oList.add(sandwich);
            sandwich.getOverrides().put("HAMBURGER", new OverrideData("{prefixes}Hamburger", null, null));
            sandwich.getOverrides().put("BLT", new OverrideData("BLT", null, null));
            sandwich.getOverrides().put("JAM", new OverrideData(null, null, null));
            sandwich.addIngredient("Beef");
            sandwich.addIngredient("Tomato");

            sandwich.setOrigin("Hamburger");
            assertEquals("Hamburger", name(ItemBuilder.buildComposedWithQuality(sandwich, 3)));
            assertEquals("Hamburger", name(ItemBuilder.buildSingleWithQuality(sandwich, 3)));
            sandwich.setOrigin("BLT");
            assertEquals("BLT", name(ItemBuilder.buildComposedWithQuality(sandwich, 3)));
            sandwich.setOrigin("Jam");
            assertEquals("Beef and Tomato Sandwich", name(ItemBuilder.buildComposedWithQuality(sandwich, 3)));
            assertEquals("Beef and Tomato Sandwich", name(ItemBuilder.buildSingleWithQuality(sandwich, 3)));
        } finally {
            FoodLoader.oList.clear();
            FoodLoader.oList.addAll(foods);
            Cooking.plugin = previous;
            MockBukkit.unmock();
        }
    }

    @SuppressWarnings("deprecation")
    private static String name(ItemStack stack) {
        return stack.getItemMeta().getDisplayName();
    }
}
