package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A chunk reload or restart rebuilds the saucepan from its furniture; the sauce must still scoop. */
class SauceReferenceReloadTest {
    private CookingReferencesTest.Environment env;

    @BeforeEach void setUp() {
        env = new CookingReferencesTest.Environment();
        env.cache.when(() -> ItemCache.getColour(any())).thenAnswer(inv ->
                CookingReferencesTest.Environment.material(inv.getArgument(0), Material.WHITE_DYE) ? "ffffff" : "aa5500");
    }
    @AfterEach void tearDown() { env.close(); }

    private SauceReference saucepanWithMilkAndSalt() {
        SauceReference sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        env.player.getInventory().setItemInMainHand(env.stack(env.food("milk", "milk", "Milk"), Material.WHITE_DYE, 1));
        sauce.interact(env.interaction());
        env.player.getInventory().setItemInMainHand(env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 2));
        sauce.interact(env.interaction());
        return sauce;
    }

    private SauceReference rebuilt() {
        SauceReference restored = new SauceReference(env.furniture, Method.SAUCEPAN);
        restored.rebuildFromFurniture();
        return restored;
    }

    private ItemStack scoop(SauceReference sauce) {
        ItemStack ladle = new ItemStack(Material.PAPER);
        env.player.getInventory().setItemInMainHand(ladle);
        sauce.interact(env.interaction());
        return env.player.getInventory().getItemInMainHand();
    }

    @Test
    void aRebuiltSaucepanKeepsItsLiquidIngredientsAndColoursAndStillScoops() {
        SauceReference sauce = saucepanWithMilkAndSalt();
        assertEquals("ffffff,aa5500", env.variables.get("sauce.colours"));
        assertNotNull(env.variables.get("sauce.liquid"));

        SauceReference restored = rebuilt();

        assertEquals(Map.of("liquid", -1), restored.getSecondaries());
        assertEquals(sauce.getSlots().keySet(), restored.getSlots().keySet());
        assertEquals("salt", restored.getSlots().values().iterator().next().getCategory());
        assertEquals(List.of("ffffff", "aa5500"), restored.getColours());

        ItemStack served = scoop(restored);
        assertTrue(served.getItemMeta().getPersistentDataContainer().has(Keys.SAUCE_COLOUR, PersistentDataType.STRING));
        assertNotNull(env.lastBuilt.getTagTrack("sauce_creamy"), "The saved milk keeps the sauce creamy");
        env.composer.verify(() -> CompositionQualityResolver.compose(any(), argThat(inputs -> inputs.size() == 2), any()));
        assertTrue(restored.isEmpty());
        assertTrue(env.active.isEmpty());
        assertFalse(env.variables.containsKey("sauce.colours"));
        assertFalse(env.variables.containsKey("sauce.liquid"));
    }

    @Test
    void saucepansFilledBeforeTheFixUseTheirIngredientsColours() {
        saucepanWithMilkAndSalt();
        env.variables.clear();

        SauceReference restored = rebuilt();

        assertEquals(Map.of("liquid", -1), restored.getSecondaries());
        assertEquals(List.of("aa5500"), restored.getColours());
        ItemStack served = scoop(restored);
        assertEquals(Material.PAPER, served.getType());
        env.composer.verify(() -> CompositionQualityResolver.compose(any(), argThat(inputs -> inputs.size() == 1), any()));
        assertTrue(restored.isEmpty());
    }

    @Test
    void ingredientsWithoutLiquidAreKeptButNothingCanBeScooped() {
        env.place("input_1", env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 1));
        env.place("input_2", new ItemStack(Material.STONE));

        SauceReference restored = rebuilt();

        assertEquals(Set.of("input_1"), restored.getSlots().keySet());
        assertTrue(restored.getSecondaries().isEmpty());
        assertTrue(restored.getColours().isEmpty());
        assertEquals(Material.PAPER, scoop(restored).getType());
        assertNull(env.lastBuilt, "No sauce is served without liquid");
    }

    @Test
    void pouringLiquidThatIsNotFoodForgetsAnEarlierSavedLiquid() {
        env.variables.put("sauce.liquid", "stale");
        SauceReference sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.HONEY_BOTTLE));
        sauce.interact(env.interaction());

        assertEquals(Map.of("liquid", -1), sauce.getSecondaries());
        assertFalse(env.variables.containsKey("sauce.liquid"));
        assertEquals("aa5500", env.variables.get("sauce.colours"));
        assertEquals(Map.of("liquid", -1), rebuilt().getSecondaries());
    }

    @Test
    void missingFurnitureOrTypeLeavesAnEmptySaucepan() {
        SauceReference detached = new SauceReference(null, Method.SAUCEPAN);
        assertDoesNotThrow(detached::rebuildFromFurniture);
        assertDoesNotThrow(detached::clear);
        assertTrue(detached.isEmpty());
        org.mockito.Mockito.when(env.furniture.getType()).thenReturn(null);
        assertTrue(rebuilt().isEmpty());
    }
}
