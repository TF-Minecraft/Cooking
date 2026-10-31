package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.condiment.CondimentConfig;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.IngredientConverter;

/** Two bones boiled in a cup of water fill an empty glass bottle with stock (condiments.yml liquid: water). */
class SauceReferenceStockTest {
    // The test environment parses no food paths, so recipes here match by category only.
    static final String CONFIG = """
            saucepan:
              bottles:
                stock:
                  liquid: water
                  ingredients: [c.bone, c.bone]
                  boil-seconds: 2
                  output: stock(type=stock)
            """;

    CookingReferencesTest.Environment env;
    SauceReference sauce;
    FoodItem bone;
    @TempDir Path directory;

    @BeforeEach void setUp() throws Exception {
        env = new CookingReferencesTest.Environment();
        Path file = directory.resolve("condiments.yml");
        Files.writeString(file, CONFIG);
        new CondimentConfig().load(file.toFile());
        sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        bone = env.food("bone", "bone", "Bone");
        // Carving gives vanilla bones; conversions.yml turns them into Cooking bones.
        env.converter.when(() -> IngredientConverter.convertIfNeeded(any(), argThat(item -> item != null && item.getType() == Material.BONE)))
                .thenAnswer(inv -> env.stack(bone, Material.BONE, ((ItemStack) inv.getArgument(1)).getAmount()));
        FoodItem stick = env.food("stick", "stick", "Wood");
        env.converter.when(() -> IngredientConverter.convertIfNeeded(any(), argThat(item -> item != null && item.getType() == Material.STICK)))
                .thenAnswer(inv -> env.stack(stick, Material.STICK, 1));
    }

    @AfterEach void tearDown() throws Exception {
        new CondimentConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    void hold(ItemStack item) {
        env.player.getInventory().setItemInMainHand(item);
        sauce.interact(env.interaction());
    }

    ItemStack hand() {
        return env.player.getInventory().getItemInMainHand();
    }

    @Test
    void bonesBoiledInACupOfWaterBottleAsStockEvenAfterAReload() {
        // No liquid yet: a vanilla bone isn't converted or taken.
        hold(new ItemStack(Material.BONE));
        assertNull(env.resolve(hand()));
        assertTrue(sauce.isEmpty());

        hold(new ItemStack(Material.HONEY_BOTTLE));
        assertEquals(true, env.variables.get("sauce.water"));

        // Carved bones go in as Cooking bones, two of them; a third, or what no recipe needs, stays in hand.
        hold(new ItemStack(Material.BONE, 3));
        assertEquals(1, sauce.getSlots().size());
        assertEquals(bone.getId(), env.resolve(hand()).getId());
        assertEquals(2, hand().getAmount());
        hold(hand());
        hold(hand());
        assertEquals(2, sauce.getSlots().size());
        assertEquals(1, hand().getAmount());
        hold(new ItemStack(Material.STICK));
        assertNull(env.resolve(hand()), "A converted food no recipe needs leaves the held item as it was");
        hold(new ItemStack(Material.FEATHER));
        assertEquals(2, sauce.getSlots().size());

        sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        sauce.rebuildFromFurniture();
        env.heated = true;
        for (int i = 0; i < 2; i++) sauce.tick();
        hold(new ItemStack(Material.GLASS_BOTTLE));
        assertEquals(Material.PAPER, hand().getType());
        // The cup of water adds nothing to the stock's quality; its two bones do.
        env.composer.verify(() -> CompositionQualityResolver.compose(any(), argThat(inputs -> inputs.size() == 2), any()));
        assertTrue(sauce.isEmpty());
        assertFalse(env.variables.containsKey("sauce.water"));
    }

    @Test
    void milkIsNoWaterForStock() {
        hold(new ItemStack(Material.WHITE_DYE));
        assertFalse(env.variables.containsKey("sauce.water"));
        hold(new ItemStack(Material.BONE));
        assertTrue(sauce.getSlots().isEmpty());
    }
}
