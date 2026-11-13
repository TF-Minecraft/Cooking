package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Bundled shelf lives (age.freshness = days until Stale; Rotten comes at 1.25 times that, see tags.yml).
 * Owner rule 2026-11-13: ingredients last at least a week, cooked soup and sauce three days.
 */
class ShelfLifeDefaultsTest {

    private static final YamlConfiguration TYPES =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/types.yml"));

    @Test
    void ingredientsLastAWeek() {
        for (String id : new String[] {"milk_bucket", "cup_of_milk", "dough", "yeast", "egg"}) {
            assertEquals(7.0, freshness(id), id);
        }
    }

    @Test
    void soupAndSauceLastThreeDays() {
        assertEquals(3.0, freshness("soup"));
        assertEquals(3.0, freshness("sauce"));
    }

    private static double freshness(String id) {
        return TYPES.getDouble(id + ".age.freshness", -1);
    }
}
