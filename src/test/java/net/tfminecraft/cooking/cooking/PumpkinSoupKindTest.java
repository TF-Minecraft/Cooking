package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.pot.PotConfig;
import net.tfminecraft.cooking.salad.SaladKind;

/** The shipped pot.yml ladles a soup of at least half pumpkin as Pumpkin Soup; less pumpkin keeps its pieces' name. */
class PumpkinSoupKindTest {
    CookingReferencesTest.Environment env;
    @TempDir Path directory;

    @BeforeEach void open() {
        env = new CookingReferencesTest.Environment();
        new PotConfig().load(new File("src/main/resources/pot.yml"));
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    FoodItem piece(String origin) {
        return env.food("vegetable_cut", "vegetable", origin);
    }

    @Test
    void mostlyPumpkinIsPumpkinSoup() {
        FoodItem pumpkin = piece("Pumpkin");
        SaladKind soup = PotConfig.kindFor(List.of(pumpkin, pumpkin, pumpkin, piece("Carrot")));
        assertEquals("Pumpkin", soup.getOrigin());
        assertEquals("Pumpkin Soup", soup.getName());
        assertEquals("Pumpkin", PotConfig.kindFor(List.of(pumpkin, piece("Onion"))).getOrigin(), "Half is enough");
        assertEquals("Pumpkin", PotConfig.kindFor(List.of(pumpkin, env.food("salt", "salt", "Salt"))).getOrigin(),
                "Salt doesn't count");
    }

    @Test
    void lessThanHalfPumpkinHasNoKind() {
        FoodItem carrot = piece("Carrot");
        assertNull(PotConfig.kindFor(List.of(piece("Pumpkin"), carrot, carrot)));
        assertEquals("Borscht", PotConfig.kindFor(List.of(piece("Beetroot"), piece("Beetroot"), piece("Pumpkin")))
                .getOrigin(), "Borscht still comes first");
    }
}
