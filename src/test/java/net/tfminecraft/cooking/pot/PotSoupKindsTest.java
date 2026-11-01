package net.tfminecraft.cooking.pot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.salad.SaladKind;

/** pot.yml kinds: the pieces boiled in the pot make a Seafood Chowder or a Seafood Stew, or no kind at all. */
class PotSoupKindsTest {
    @TempDir Path directory;
    Cooking previousPlugin;

    @BeforeEach void open() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        new PotConfig().load(new File("src/main/resources/pot.yml"));
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        Cooking.plugin = previousPlugin;
        MockBukkit.unmock();
    }

    static FoodItem piece(String type, String origin) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("update", false);
        FoodItem food = new FoodItem(type, config);
        food.setOrigin(origin);
        return food;
    }

    static String kind(FoodItem... pieces) {
        SaladKind kind = PotConfig.kindFor(new ArrayList<>(List.of(pieces)));
        return kind == null ? null : kind.getOrigin();
    }

    @Test
    void seafoodWithAPotatoIsAChowder() {
        assertEquals("Seafood Chowder", kind(piece("seafood_octopus", "Octopus"), piece("vegetable_cut", "Potato")));
        assertEquals("Seafood Chowder", kind(piece("seafood_fish_filet", "Tuna"), piece("vegetable_cut", "Potato"),
                piece("vegetable_cut_2", "Onion"), piece("mushroom", "Brown Mushroom")));
    }

    @Test
    void seafoodWithOtherVegetablesOrAnotherSeafoodIsAStew() {
        assertEquals("Seafood Stew", kind(piece("seafood_octopus", "Octopus"), piece("vegetable_cut", "Carrot")));
        assertEquals("Seafood Stew", kind(piece("seafood_fish_filet", "Tuna"), piece("seafood_octopus", "Octopus")));
        assertEquals("Seafood Stew", kind(piece("seafood_jellyfish", "Blue Jellyfish"),
                piece("seafood_jellyfish", "Pink Jellyfish")));
    }

    @Test
    void oneSeafoodAloneOrNoSeafoodIsNoKind() {
        assertNull(kind(piece("seafood_octopus", "Octopus"), piece("seafood_octopus", "Octopus")));
        assertNull(kind(piece("vegetable_cut", "Potato"), piece("vegetable_cut", "Carrot")));
        assertNull(kind());
    }

    @Test
    void meatOrAPieceNoIngredientNamesMakesNoKind() {
        assertNull(kind(piece("seafood_octopus", "Octopus"), piece("meat_pork", "Pork"), piece("vegetable_cut", "Potato")));
        assertNull(kind(piece("seafood_octopus", "Octopus"), piece("vegetable_cut", "Potato"), piece("stew_bone", "Bone")));
        assertEquals("meat", PotConfig.ingredientFor(piece("meat_pork", "Pork")).getId());
        assertNull(PotConfig.ingredientFor(piece("stew_bone", "Bone")));
    }

    @Test
    void withoutKindsInPotYmlEverySoupIsPlain() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        assertNull(kind(piece("seafood_octopus", "Octopus"), piece("vegetable_cut", "Potato")));
        assertNull(PotConfig.ingredientFor(piece("seafood_octopus", "Octopus")));
    }
}
