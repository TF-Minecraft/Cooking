package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import net.tfminecraft.cooking.brewery.BrewLiquid;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A BreweryX brew can be the saucepan's liquid in place of water or milk. */
class SauceReferenceBrewTest {
    private static final BrewLiquid WINE = new BrewLiquid("Red Wine", 5, "ff0000", true);
    private CookingReferencesTest.Environment env;

    @BeforeEach void setUp() {
        env = new CookingReferencesTest.Environment();
        BrewLiquid.setReader(item -> item.getType() == Material.POTION && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName() ? brewNamed(item.getItemMeta().getDisplayName()) : null);
    }
    @AfterEach void tearDown() {
        BrewLiquid.setReader(null);
        env.close();
    }

    private static BrewLiquid brewNamed(String name) {
        return switch (name) {
            case "coffee" -> new BrewLiquid("Coffee", 4, "000000", false);
            case "vodka" -> new BrewLiquid("Vodka", 3, "ffffff", true);
            case "plain" -> new BrewLiquid("", 2, null, true);
            default -> WINE;
        };
    }

    private static ItemStack brew(String name) {
        ItemStack potion = new ItemStack(Material.POTION);
        var meta = potion.getItemMeta();
        meta.setDisplayName(name);
        potion.setItemMeta(meta);
        return potion;
    }

    private SauceReference pour(String name) {
        SauceReference sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        env.player.getInventory().setItemInMainHand(brew(name));
        sauce.interact(env.interaction());
        return sauce;
    }

    private ItemStack scoop(SauceReference sauce) {
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        sauce.interact(env.interaction());
        return env.player.getInventory().getItemInMainHand();
    }

    @Test
    void anAlcoholicBrewBecomesTheLiquidAndLeavesBothAddInSlotsFree() {
        SauceReference sauce = pour("wine");

        assertEquals(Map.of("liquid", -1), sauce.getSecondaries());
        assertTrue(sauce.getSlots().isEmpty());
        assertEquals(List.of("ff0000"), sauce.getColours());
        assertEquals("5:Red Wine", env.variables.get("sauce.brew"));
        assertFalse(env.variables.containsKey("sauce.liquid"));
        assertEquals(Material.GLASS_BOTTLE, env.player.getInventory().getItemInMainHand().getType());
        assertEquals(List.of("Red Wine"), sauce.getIngredients());

        env.player.getInventory().setItemInMainHand(env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 1));
        sauce.interact(env.interaction());
        env.player.getInventory().setItemInMainHand(env.stack(env.food("garlic", "garnish", "Garlic"), Material.SUGAR, 1));
        sauce.interact(env.interaction());
        assertEquals(2, sauce.getSlots().size());
        assertEquals(List.of("Red Wine", "Garlic"), sauce.getIngredients());

        ItemStack served = scoop(sauce);

        assertTrue(served.getItemMeta().getDisplayName().contains("Red Wine"));
        assertNotNull(env.lastBuilt.getTagTrack("reduced"));
        env.composer.verify(() -> CompositionQualityResolver.compose(any(), argThat(inputs -> {
            FoodItem liquid = inputs.iterator().next();
            return inputs.size() == 3 && "alcohol".equals(liquid.getCategory())
                    && "Red Wine".equals(liquid.getOrigin()) && liquid.getQualityMin() == 5
                    && "fresh".equals(liquid.getTagTrack("freshness").getCurrentStep().getId());
        }), any()));
        assertTrue(sauce.isEmpty());
        assertFalse(env.variables.containsKey("sauce.brew"));
    }

    @Test
    void aWhiteBrewIsNotMilkAndAnUnnamedBrewKeepsTheUsualName() {
        scoop(pour("vodka"));
        assertNull(env.lastBuilt.getTagTrack("sauce_creamy"));
        assertNotNull(env.lastBuilt.getTagTrack("reduced"));

        SauceReference plain = pour("plain");
        assertTrue(plain.getColours().isEmpty());
        assertTrue(plain.getIngredients().isEmpty());
    }

    @Test
    void nonAlcoholicBrewsAndBrewsIntoAFullPanAreRefused() {
        SauceReference coffee = pour("coffee");
        assertTrue(coffee.isEmpty());
        assertEquals(Material.POTION, env.player.getInventory().getItemInMainHand().getType());
        verify(env.player).sendMessage("§cOnly alcoholic brews can go in the saucepan.");

        SauceReference wine = pour("wine");
        env.player.getInventory().setItemInMainHand(brew("vodka"));
        wine.interact(env.interaction());
        assertEquals(List.of("ff0000"), wine.getColours());
        assertEquals(Material.POTION, env.player.getInventory().getItemInMainHand().getType());

        env.definitions.remove("liquid");
        assertTrue(pour("wine").isEmpty());
        when(env.furniture.getType()).thenReturn(null);
        assertTrue(pour("wine").isEmpty());
    }

    @Test
    void aRebuiltSaucepanKeepsItsBrew() {
        pour("wine");

        SauceReference restored = new SauceReference(env.furniture, Method.SAUCEPAN);
        restored.rebuildFromFurniture();

        assertEquals(List.of("Red Wine"), restored.getIngredients());
        assertEquals(List.of("ff0000"), restored.getColours());
        scoop(restored);
        assertNotNull(env.lastBuilt.getTagTrack("reduced"));
        env.composer.verify(() -> CompositionQualityResolver.compose(any(), argThat(inputs ->
                inputs.size() == 1 && "alcohol".equals(inputs.iterator().next().getCategory())), any()));
    }

    @Test
    void unreadableSavedBrewsAreIgnored() {
        for (String saved : List.of("Red Wine", ":Red Wine", "five:Red Wine")) {
            pour("wine");
            env.variables.put("sauce.brew", saved);
            SauceReference restored = new SauceReference(env.furniture, Method.SAUCEPAN);
            restored.rebuildFromFurniture();
            assertTrue(restored.getIngredients().isEmpty(), saved);
            restored.clear();
        }
    }
}
