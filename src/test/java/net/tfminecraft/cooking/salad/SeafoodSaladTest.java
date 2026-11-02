package net.tfminecraft.cooking.salad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.NameComposer;

/** The shipped salads.yml takes grilled octopus and jellyfish strips; any jellyfish is just "Jellyfish" in the name. */
class SeafoodSaladTest {
    Environment env;
    FoodItem built;
    @TempDir Path directory;

    @BeforeEach void open() {
        env = new Environment();
        new NamingLoader().load(new File("src/main/resources/naming.yml"));
        new SaladConfig().load(new File("src/main/resources/salads.yml"));
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            return env.stack(built, Material.BOWL, 1);
        });
    }

    @AfterEach void close() throws Exception {
        new SaladConfig().load(directory.resolve("missing.yml").toFile());
        Path empty = directory.resolve("naming.yml");
        Files.writeString(empty, "");
        new NamingLoader().load(empty.toFile());
        env.close();
    }

    FoodItem food(String type, String origin) {
        FoodItem food = env.food(type, origin);
        food.setCategory("seafood");
        return food;
    }

    @Test
    void grilledOctopusAndJellyfishStripsAreSeafoodIngredients() {
        SandwichLayer octopus = SaladConfig.ingredientFor(food("grilled_octopus", "Octopus"));
        assertEquals("seafood", octopus.getId());
        assertEquals("Octopus", octopus.fillerName(food("grilled_octopus", "Octopus")));
        assertEquals("Jellyfish", octopus.fillerName(food("jellyfish_strips", "Blue Jellyfish")));
        assertEquals("Jellyfish", octopus.fillerName(food("jellyfish_strips", "Pink Jellyfish")));
        // Raw octopus and jellyfish cubes aren't salad ingredients: grill the one and cut the other into strips.
        assertNull(SaladConfig.ingredientFor(food("seafood_octopus", "Octopus")));
        assertNull(SaladConfig.ingredientFor(food("seafood_jellyfish", "Blue Jellyfish")));
    }

    @Test
    void aSaladOfOctopusJellyfishAndTomatoIsNamedByThem() {
        List<ItemStack> in = new ArrayList<>();
        in.add(env.stack(food("grilled_octopus", "Octopus"), Material.PAPER, 1));
        in.add(env.stack(food("jellyfish_strips", "Pink Jellyfish"), Material.PAPER, 1));
        FoodItem tomato = env.food("vegetable_chopped_2", "Tomato");
        tomato.setCategory("garnish");
        in.add(env.stack(tomato, Material.PAPER, 1));
        assertNotNull(SaladItems.build(env.player, in, List.of()));
        assertEquals("Mixed", built.getOrigin());
        assertEquals("Octopus, Jellyfish and Tomato Salad",
                NameComposer.compose(built, "{prefixes}{fillers}Salad", Map.of()));
    }
}
