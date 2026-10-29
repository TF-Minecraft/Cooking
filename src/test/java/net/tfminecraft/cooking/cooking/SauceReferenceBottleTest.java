package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.condiment.CondimentConfig;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Vinegar or sap poured into the saucepan, boiled with a recipe's ingredients, fills an empty glass bottle. */
class SauceReferenceBottleTest {
    // The test environment parses no food paths, so recipes here match by category only.
    static final String CONFIG = """
            saucepan:
              liquids: [c.vinegar, c.sap]
              bottles:
                catsup:
                  liquid: c.vinegar
                  ingredients: [c.paste, c.salt]
                  boil-seconds: 3
                  output: condiment(type=catsup)
                syrup:
                  liquid: c.sap
                  boil-seconds: 2
                  output: sweetener(type=maple_syrup)
            """;

    CookingReferencesTest.Environment env;
    SauceReference sauce;
    @TempDir Path directory;

    @BeforeEach void setUp() throws Exception {
        env = new CookingReferencesTest.Environment();
        Path file = directory.resolve("condiments.yml");
        Files.writeString(file, CONFIG);
        new CondimentConfig().load(file.toFile());
        sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
    }

    @AfterEach void tearDown() throws Exception {
        new CondimentConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    void hold(ItemStack item) {
        env.player.getInventory().setItemInMainHand(item);
        sauce.interact(env.interaction());
    }

    ItemStack food(String category, String origin) {
        return env.stack(env.food(category, category, origin), Material.EXPERIENCE_BOTTLE, 1);
    }

    void boil(int seconds) {
        env.heated = true;
        for (int i = 0; i < seconds; i++) sauce.tick();
    }

    ItemStack hand() {
        return env.player.getInventory().getItemInMainHand();
    }

    @Test
    void vinegarBoiledWithItsIngredientsFillsAnEmptyBottleAndEmptiesThePan() {
        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player).sendMessage("§7The saucepan is empty.");

        hold(food("vinegar", "Wine"));
        assertEquals(Map.of("liquid", -1), sauce.getSecondaries());
        assertEquals(Material.GLASS_BOTTLE, hand().getType());
        assertEquals(0, env.variables.get("sauce.boiled"));

        // A second vinegar is neither poured nor added.
        hold(food("vinegar", "Wine"));
        assertTrue(sauce.getSlots().isEmpty());

        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player).sendMessage("§7Nothing in this saucepan goes in a bottle. Use a ladle for sauce.");

        // Tomato paste goes in only because a vinegar recipe needs it; a second one doesn't.
        hold(food("paste", "Tomato"));
        hold(food("paste", "Tomato"));
        assertEquals(1, sauce.getSlots().size());
        hold(food("salt", "Salt"));
        assertEquals(2, sauce.getSlots().size());

        boil(2);
        assertEquals(2, env.variables.get("sauce.boiled"));
        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player).sendMessage("§7It needs to boil a while longer (1s).");

        boil(8);
        hold(new ItemStack(Material.GLASS_BOTTLE, 2));
        assertEquals(Material.PAPER, hand().getType());
        assertEquals(1, env.player.getInventory().all(Material.GLASS_BOTTLE).values().stream().mapToInt(ItemStack::getAmount).sum());
        env.composer.verify(() -> CompositionQualityResolver.compose(any(), argThat(inputs -> inputs.size() == 3), any()));
        assertTrue(sauce.isEmpty());
        assertFalse(env.variables.containsKey("sauce.boiled"));
    }

    @Test
    void sapAloneBoilsDownAndTheBoilingTimeSurvivesAReload() {
        hold(food("sap", "Tree"));
        // Paste isn't part of any sap recipe.
        hold(food("paste", "Tomato"));
        assertTrue(sauce.getSlots().isEmpty());
        boil(1);

        sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        sauce.rebuildFromFurniture();
        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player).sendMessage("§7It needs to boil a while longer (1s).");
        boil(1);
        hold(new ItemStack(Material.GLASS_BOTTLE));
        assertEquals(Material.PAPER, hand().getType());

        // Unheated, a pan with liquid doesn't boil; one without liquid never counts.
        env.heated = false;
        sauce.tick();
        env.heated = true;
        sauce.tick();
        assertFalse(env.variables.containsKey("sauce.boiled"));
    }

    @Test
    void waterSaucesStayLadleOnlyAndFailedBottlesKeepThePan() {
        hold(new ItemStack(Material.HONEY_BOTTLE));
        hold(food("paste", "Tomato"));
        assertTrue(sauce.getSlots().isEmpty());
        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player).sendMessage("§7Nothing in this saucepan goes in a bottle. Use a ladle for sauce.");
        sauce.clear();

        hold(food("sap", "Tree"));
        boil(2);
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
        hold(new ItemStack(Material.GLASS_BOTTLE));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        hold(new ItemStack(Material.GLASS_BOTTLE));
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player, times(3)).sendMessage("§cThis can't be bottled right now.");
        assertFalse(sauce.isEmpty());

        // A bottled liquid needs the pan's liquid slot, and a food bottle isn't an empty one.
        sauce.clear();
        env.definitions.remove("liquid");
        ItemStack vinegar = food("vinegar", "Wine");
        hold(vinegar);
        assertTrue(sauce.isEmpty());
        assertEquals(1, hand().getAmount());
        hold(env.stack(env.food("glass", "glass", "Glass"), Material.GLASS_BOTTLE, 1));
        verify(env.player, never()).sendMessage("§7The saucepan is empty.");
    }
}
