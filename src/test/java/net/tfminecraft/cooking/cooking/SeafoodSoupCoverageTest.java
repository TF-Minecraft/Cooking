package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.pot.PotConfig;

/**
 * The shipped pot.yml kinds: boiled seafood mashed with a potato is a Seafood Chowder, with other vegetables or another
 * seafood a Seafood Stew. One seafood alone, or seafood with meat, is still named by its pieces. Salt doesn't count.
 */
class SeafoodSoupCoverageTest {
    CookingReferencesTest.Environment env;
    PotReference pot;
    @TempDir Path directory;

    @BeforeEach void open() {
        env = new CookingReferencesTest.Environment();
        new PotConfig().load(new File("src/main/resources/pot.yml"));
        pot = new PotReference(env.furniture, Method.POT);
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    /** Mashed pieces in the pot ("Origin/category"), and a salt. Returns the ladled soup's name. */
    @SuppressWarnings("deprecation")
    String ladle(String... pieces) {
        int slot = 1;
        for (String entry : pieces) {
            String[] parts = entry.split("/");
            FoodItem piece = env.food("piece", parts[1], parts[0], Method.POT);
            piece.getTagTrack("cooked").forceSetValue(3);
            piece.addOrModifyTrack(TrackLoader.getByString("mashed"));
            pot.slots.put("input_" + slot, piece);
            env.place("input_" + slot, env.stack(piece, Material.COD, 1));
            slot++;
        }
        FoodItem salt = env.food("salt", "salt", "Salt");
        pot.slots.put("extra_salt", salt);
        env.place("extra_salt", env.stack(salt, Material.SUGAR, 1));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        pot.interact(env.interaction());
        return org.bukkit.ChatColor.stripColor(env.player.getInventory().getItemInMainHand().getItemMeta().getDisplayName());
    }

    @Test
    void octopusAndPotatoMakeASeafoodChowder() {
        assertEquals("Seafood Chowder", ladle("Octopus/seafood", "Potato/vegetable"));
        assertEquals("Seafood Chowder", env.lastBuilt.getOrigin());
    }

    @Test
    void fishWithPotatoOnionAndAMushroomIsStillAChowder() {
        assertEquals("Seafood Chowder", ladle("Tuna/seafood", "Potato/vegetable", "Onion/vegetable", "Brown Mushroom/vegetable"));
    }

    @Test
    void seafoodWithOtherVegetablesOrAnotherSeafoodIsAStew() {
        assertEquals("Seafood Stew", ladle("Octopus/seafood", "Carrot/vegetable"));
    }

    @Test
    void twoSeafoodsAreAStew() {
        assertEquals("Seafood Stew", ladle("Blue Jellyfish/seafood", "Pink Jellyfish/seafood"));
    }

    @Test
    void oneSeafoodAloneIsStillNamedByIt() {
        String name = ladle("Octopus/seafood", "Octopus/seafood");
        assertEquals("Mixed", env.lastBuilt.getOrigin());
        assertTrue(name.endsWith("Soup") && name.contains("Octopus"), name);
    }

    @Test
    void seafoodWithMeatIsNoSeafoodDish() {
        String name = ladle("Octopus/seafood", "Pork/meat", "Potato/vegetable");
        assertEquals("Mixed", env.lastBuilt.getOrigin());
        assertTrue(name.endsWith("Soup"), name);
    }
}
