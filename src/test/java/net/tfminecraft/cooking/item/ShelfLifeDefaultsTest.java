package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Bundled shelf lives (age.freshness = days until Stale; Rotten comes at 1.5 times that, see tags.yml).
 * Owner rules 2026-11-15: grains a month, produce three weeks, meat, fish, eggs and milk two weeks, bread a week;
 * cooked food three days (config.yml cooked-freshness), so there is always a reason to cook.
 */
class ShelfLifeDefaultsTest {

    private static final YamlConfiguration TYPES =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/types.yml"));
    private static final YamlConfiguration TAGS =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/tags.yml"));
    private static final YamlConfiguration CONFIG =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));

    @Test
    void grainsLastAMonth() {
        for (String id : new String[] {"wheat", "flour", "dry_ration"}) {
            assertEquals(28.0, freshness(id), id);
        }
    }

    @Test
    void produceDoughAndYeastLastThreeWeeks() {
        for (String id : new String[] {"vegetable_1", "vegetable_2", "vegetable_chop_only", "vegetable_cut",
                "vegetable_cut_2", "vegetable_chopped", "vegetable_chopped_2", "garnish_chopped", "fruit_1", "fruit_2",
                "butter", "dough", "yeast"}) {
            assertEquals(21.0, freshness(id), id);
        }
    }

    @Test
    void meatFishEggsAndMilkLastTwoWeeks() {
        for (String id : new String[] {"roast", "meat_poultry", "meat_poultry_leg", "meat_red_meat", "meat_pork",
                "sausage", "sausage_chain", "egg", "cup_of_milk", "milk_bucket", "seafood_whole", "seafood_fish_filet",
                "seafood_jellyfish", "seafood_octopus"}) {
            assertEquals(14.0, freshness(id), id);
        }
    }

    @Test
    void breadLastsAWeekEvenOnceBaked() {
        assertEquals(7.0, freshness("bread"));
        assertEquals(7.0, TYPES.getDouble("bread.cooked-freshness", -1));
    }

    @Test
    void cookedFoodSoupAndSauceLastThreeDays() {
        assertEquals(3.0, CONFIG.getDouble("cooked-freshness", -1));
        assertEquals(3.0, freshness("soup"));
        assertEquals(3.0, freshness("sauce"));
    }

    @Test
    void staleFoodKeepsItsNutritionAndRotsAtOneAndAHalfTimesItsFreshDays() {
        assertEquals(86400, TAGS.getInt("freshness.stale.value"));
        assertEquals(1.0, TAGS.getDouble("freshness.stale.nutrition-mult"));
        assertEquals(129600, TAGS.getInt("freshness.rotten.value"));
    }

    private static double freshness(String id) {
        return TYPES.getDouble(id + ".age.freshness", -1);
    }
}
