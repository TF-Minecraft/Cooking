package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.File;
import java.nio.file.Path;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.pot.PotConfig;
import net.tfminecraft.cooking.utils.FoodParser;

/**
 * A soup ladled from a pot of seafood and a potato is a Seafood Chowder: its kind (pot.yml kinds) is its origin, and
 * the soup type's override for that origin (types.yml) names it. Salt and other extras don't change its kind.
 */
class SeafoodSoupCoverageTest {
    CookingReferencesTest.Environment env;
    PotReference pot;
    @TempDir Path directory;

    @BeforeEach void open() {
        env = new CookingReferencesTest.Environment();
        new PotConfig().load(new File("src/main/resources/pot.yml"));
        // The soup as types.yml has it, with its kinds' names.
        env.parser.when(() -> FoodParser.parse(startsWith("soup"))).thenAnswer(inv -> {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("name", "{colour}{prefixes}{fillers}Soup");
            config.set("update", false);
            config.set("overrides.SEAFOOD CHOWDER.name", "{colour}{prefixes}Seafood Chowder");
            config.set("overrides.SEAFOOD STEW.name", "{colour}{prefixes}Seafood Stew");
            FoodItem soup = new FoodItem("soup", config);
            soup.setCategory("soup");
            soup.setOrigin("Mixed");
            soup.setModel(new FoodModel(new ItemStack(Material.CARROT)));
            soup.setBaseFood(8);
            FoodParser.Result result = new FoodParser.Result();
            result.template = soup;
            return result;
        });
        pot = new PotReference(env.furniture, Method.POT);
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    /** Mashed pieces in the pot (type:origin), and a salt as an extra. */
    String ladle(String... pieces) {
        int slot = 1;
        for (String entry : pieces) {
            String[] parts = entry.split(":");
            FoodItem piece = env.food(parts[0], "seafood", parts[1], Method.POT);
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
        return env.player.getInventory().getItemInMainHand().getItemMeta().getDisplayName();
    }

    @Test
    void octopusAndPotatoMakeASeafoodChowder() {
        String name = ladle("seafood_octopus:Octopus", "vegetable_cut:Potato");
        assertEquals("Seafood Chowder", env.lastBuilt.getOrigin());
        assertTrue(name.endsWith("Seafood Chowder"), name);
        assertFalse(name.contains("Soup"), name);
    }

    @Test
    void twoSeafoodsMakeASeafoodStew() {
        assertTrue(ladle("seafood_fish_filet:Tuna", "seafood_jellyfish:Blue Jellyfish").endsWith("Seafood Stew"));
        assertEquals("Seafood Stew", env.lastBuilt.getOrigin());
    }

    @Test
    void octopusAloneIsStillAnOctopusSoup() {
        String name = ladle("seafood_octopus:Octopus", "seafood_octopus:Octopus");
        assertEquals("Mixed", env.lastBuilt.getOrigin());
        assertTrue(name.endsWith("Soup"), name);
        assertFalse(name.contains("Seafood"), name);
    }
}
