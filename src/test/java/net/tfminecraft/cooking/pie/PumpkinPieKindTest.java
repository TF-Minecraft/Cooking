package net.tfminecraft.cooking.pie;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.sandwich.SandwichLayer;

/** The shipped pies.yml bakes cut or chopped pumpkin, and nothing else, into a Pumpkin Pie. */
class PumpkinPieKindTest {
    Environment env;
    @TempDir Path directory;

    @BeforeEach void open() {
        env = new Environment();
        new PieConfig().load(new File("src/main/resources/pies.yml"));
    }

    @AfterEach void close() {
        new PieConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    SaladKind kindOf(FoodItem... foods) {
        List<FoodItem> all = List.of(foods);
        List<SandwichLayer> made = all.stream().map(PieConfig::fillingFor).toList();
        return PieConfig.kindFor(all, made);
    }

    @Test
    void cutOrChoppedPumpkinMakesAPumpkinPie() {
        FoodItem cut = env.food("vegetable_cut", "Pumpkin");
        FoodItem chopped = env.food("vegetable_chopped", "Pumpkin");
        assertEquals("vegetable", PieConfig.fillingFor(cut).getId());
        assertEquals("vegetable", PieConfig.fillingFor(chopped).getId());

        SaladKind pie = kindOf(cut, chopped, cut);
        assertEquals("Pumpkin", pie.getOrigin());
        assertEquals("Pumpkin", pie.getName());
        assertEquals("Pumpkin", kindOf(chopped).getOrigin(), "One piece of pumpkin is enough");
    }

    @Test
    void pumpkinWithAnythingElseIsAMixedPie() {
        FoodItem pumpkin = env.food("vegetable_cut", "Pumpkin");
        assertEquals("Mixed", kindOf(pumpkin, env.food("vegetable_cut", "Carrot")).getOrigin());
        assertEquals("Mixed", kindOf(pumpkin, env.food("fruit_1", "Apple")).getOrigin());
        assertEquals("Meat", kindOf(env.food("meat_red_meat", "Beef"), pumpkin).getOrigin(),
                "Meat with vegetables is still a meat pie");
    }
}
