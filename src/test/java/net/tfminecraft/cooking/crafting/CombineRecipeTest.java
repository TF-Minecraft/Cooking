package net.tfminecraft.cooking.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/** A combining recipe: one of each input line per unit, whatever their origins (boiled rice and a fish filet: sushi). */
class CombineRecipeTest {
    CraftingStationCoverageTest.Environment env;

    @BeforeEach void open() { env = new CraftingStationCoverageTest.Environment(); }
    @AfterEach void close() { env.close(); }

    /** A single-origin board (as the cutting board is) with the sushi recipe; the test checker matches food:<id>. */
    CraftingStation board() {
        MemoryConfiguration config = env.config(2, true, "tool:knife");
        config.set("recipes.sushi.combine", true);
        config.set("recipes.sushi.origin-from", 2);
        config.set("recipes.sushi.yield", 2);
        config.set("recipes.sushi.inputs", List.of("food:boiled_rice", "food:filet"));
        config.set("recipes.sushi.output", "meal(type=sushi;origin={origin})");
        return new CraftingStation(env.furniture, new CraftingStation("board", config));
    }

    ItemStack rice(int amount) { return env.stack(env.food("boiled_rice", "Rice"), Material.WHITE_WOOL, amount); }
    ItemStack filet(String fish, int amount) { return env.stack(env.food("filet", fish), Material.COD, amount); }

    @Test
    void recipeReadsCombineAndOriginFromAndFindsTheLineAnItemFills() {
        CraftingRecipe plain = new CraftingRecipe("plain", new MemoryConfiguration());
        assertFalse(plain.combines());
        assertEquals(0, plain.getOriginFrom());
        assertEquals(-1, plain.line(rice(1)));
        CraftingRecipe sushi = recipe();
        assertTrue(sushi.combines());
        assertEquals(2, sushi.getOriginFrom());
        assertEquals(0, sushi.line(rice(1)));
        assertEquals(1, sushi.line(filet("Salmon", 1)));
        assertEquals(-1, sushi.line(env.raw(1)));
    }

    CraftingRecipe recipe() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("combine", true);
        config.set("origin-from", 2);
        config.set("inputs", List.of("food:boiled_rice", "food:filet"));
        return new CraftingRecipe("sushi", config);
    }

    @Test
    void riceAndAFiletOfAnotherOriginRollIntoTwoPiecesNamedForTheFish() {
        CraftingStation board = board();
        env.add(board, "input_1", rice(1));
        ItemStack salmon = filet("Salmon", 1);
        assertTrue(board.canAddItem(salmon), "A single-origin board still takes the fish beside the rice");
        env.add(board, "input_2", salmon);

        board.craft(env.player);
        assertEquals("meal(type=sushi;origin=Salmon)", env.lastParsed);
        ItemStack sushi = board.getSlots().get("input_1");
        assertEquals(2, sushi.getAmount(), "Two pieces a pair");
        assertEquals("meal(type=sushi;origin=Salmon)", env.resolve(sushi).getId());
        assertNull(board.getSlots().get("input_2"));
        assertFalse(env.active.containsKey("input_2"));
        assertFalse(board.hasRecipe(), "Nothing left to roll");
        assertTrue(env.dropped.isEmpty());
        env.composer.verify(() -> CompositionQualityResolver.compose(same(env.player),
                argThat(inputs -> inputs.size() == 2), eq(CompositionContext.CUTTING_BOARD)));
        assertEquals(1, env.server.getPluginManager().getFiredEvents().filter(e -> e instanceof DishCookedEvent).count());

        // With the sushi still on the board, more rice starts the recipe again: the sushi's origin doesn't count.
        assertTrue(board.canAddItem(rice(1)));
    }

    @Test
    void asManyPiecesAsEveryLineHasAndTheRestWaitsForMore() {
        CraftingStation board = board();
        PlacedSlot riceSlot = env.add(board, "input_1", rice(3));
        env.add(board, "input_2", filet("Salmon", 1));
        board.craft(env.player);
        ItemStack left = board.getSlots().get("input_1");
        assertEquals(2, left.getAmount(), "One rice was used");
        verify(riceSlot).setCurrentItem(left);
        assertEquals(2, board.getSlots().get("input_2").getAmount(), "The sushi took the filet's place");
        assertTrue(board.hasRecipe(), "The rice left over waits for another filet");

        assertTrue(board.canAddItem(filet("Cod", 1)), "The salmon is used up, so another fish may come");
        env.add(board, "input_3", filet("Cod", 2));
        assertFalse(board.canAddItem(filet("Salmon", 1)), "One fish at a time");
        assertTrue(board.canAddItem(rice(1)));
        board.craft(env.player);
        assertEquals("meal(type=sushi;origin=Cod)", env.lastParsed);
        assertNull(board.getSlots().get("input_3"), "Both cod filets used");
        assertEquals(4, board.getSlots().get("input_1").getAmount(), "Two pairs, four pieces, where the rice was");
        assertFalse(board.hasRecipe());
    }

    @Test
    void oneLineAloneMakesNothingAndAnOutputThatDoesNotParseTakesNothing() {
        CraftingStation board = board();
        ItemStack lonely = rice(2);
        env.add(board, "input_1", lonely);
        board.craft(env.player);
        assertSame(lonely, board.getSlots().get("input_1"));
        assertEquals(2, lonely.getAmount());

        env.add(board, "input_2", filet("Salmon", 1));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        board.craft(env.player);
        assertEquals(2, lonely.getAmount(), "Nothing is used when the sushi can't be made");
        assertNotNull(board.getSlots().get("input_2"));
    }

    @Test
    void aFiletWithoutFoodDataFillsItsLineButNamesNothing() {
        MemoryConfiguration config = env.config(1, false, "tool:knife");
        config.set("recipes", null);
        config.set("recipes.mix.combine", true);
        config.set("recipes.mix.inputs", List.of("food:boiled_rice", "v.stone"));
        config.set("recipes.mix.output", "meal(type=mix;origin={origin})");
        CraftingStation station = new CraftingStation(env.furniture, new CraftingStation("board", config));
        env.add(station, "input_1", rice(1));
        env.add(station, "input_2", new ItemStack(Material.STONE));
        station.craft(env.player);
        assertEquals("meal(type=mix;origin={origin})", env.lastParsed, "No origin-from, no origin");
        assertEquals(1, station.getSlots().size());
    }

    @Test
    void theShippedCuttingBoardRollsSushiFromBoiledRiceAndARawFilet() {
        YamlConfiguration stations = YamlConfiguration.loadConfiguration(new File("src/main/resources/crafting-stations.yml"));
        ConfigurationSection sushi = stations.getConfigurationSection("cutting_board.recipes.sushi");
        CraftingRecipe recipe = new CraftingRecipe("sushi", sushi);
        assertTrue(recipe.combines());
        assertEquals(2, recipe.getOriginFrom());
        assertEquals(2, recipe.getYield());
        assertEquals(List.of("c.grain(type=boiled_rice;tags=cooked.3)", "c.seafood(type=seafood_fish_filet;tags=cooked.0)"),
                recipe.getInputs());
        assertEquals("meal(type=sushi;origin={origin};tags=freshness.0)", recipe.getOutput());
        assertTrue(stations.getBoolean("cutting_board.single-origin"));
    }

    @Test
    void anItemOutsideTheRecipeOnASingleOriginBoardIsStillComparedByOrigin() {
        CraftingStation board = board();
        env.add(board, "input_1", rice(1));
        FoodItem other = env.food("boiled_rice", "Wild Rice");
        assertFalse(board.canAddItem(env.stack(other, Material.WHITE_WOOL, 1)), "Rice of another origin on the rice line");
    }
}
