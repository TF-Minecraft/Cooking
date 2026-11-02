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

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.condiment.CondimentConfig;
import net.tfminecraft.cooking.enums.Method;

/**
 * Hot chocolate: the saucepan takes a flavouring extra besides the recipe's ingredients, tags the output with its
 * track, and only the recipe's container (an empty cup, here a bowl) takes it out.
 */
class SauceReferenceContainerTest {
    // The test environment parses no food paths, so recipes here match by category only.
    static final String CONFIG = """
            saucepan:
              liquids: [c.vinegar]
              bottles:
                cocoa:
                  liquid: c.vinegar
                  ingredients: [c.paste]
                  extras:
                    flavour: {path: c.cream, track: sweet}
                  container: test.cup
                  container-name: an empty cup
                  boil-seconds: 1
                  output: drink(type=hot_chocolate)
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
        when(TLibs.getItemAPI().getChecker().checkItemWithPath(argThat(item -> item != null && item.getType() == Material.BOWL),
                eq("test.cup"))).thenReturn(true);
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

    ItemStack hand() {
        return env.player.getInventory().getItemInMainHand();
    }

    @Test
    void aFlavouredDrinkGoesOnlyIntoItsCup() {
        hold(new ItemStack(Material.BOWL));
        verify(env.player).sendMessage("§7The saucepan is empty.");

        hold(food("vinegar", "Wine"));
        hold(food("paste", "Cocoa"));
        // The flavouring goes in once, only because the recipe has it as an extra.
        hold(food("cream", "Vanilla"));
        hold(food("cream", "Vanilla"));
        assertEquals(2, sauce.getSlots().size());

        env.heated = true;
        sauce.tick();
        hold(new ItemStack(Material.GLASS_BOTTLE));
        verify(env.player).sendMessage("§7Take this out with an empty cup.");
        assertFalse(sauce.isEmpty());

        // A food that happens to be a bowl isn't the cup.
        hold(env.stack(env.food("bowl_food", "bowl_food", "Bowl"), Material.BOWL, 1));
        assertFalse(sauce.isEmpty());

        hold(new ItemStack(Material.BOWL));
        assertEquals(Material.PAPER, hand().getType());
        assertNotNull(env.lastBuilt.getTagTracks().stream().filter(track -> track.getId().equals("sweet")).findFirst().orElse(null));
        assertTrue(sauce.isEmpty());
    }
}
