package net.tfminecraft.cooking.sandwich;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.loader.TrackLoader;

class DishBonusTest {
    Environment env;
    @TempDir Path directory;
    final Map<String, SandwichLayer> layers = Map.of(
            "bread_slice", layer("bread", "bread_slice"),
            "butter", layer("butter", "butter"),
            "jam", layer("jam", "jam"),
            "cheese", layer("cheese", "cheese"),
            "vegetable_cut", layer("vegetable", "vegetable_cut"),
            "garnish", layer("vegetable", "garnish"),
            "fruit", layer("fruit", "fruit"),
            "sausage", layer("sausage", "sausage"),
            "spice", layer("spice", "spice"),
            "paste", layer("sauce", "paste"));
    final Function<FoodItem, SandwichLayer> layerFor = food -> layers.get(food.getId());

    @BeforeEach void open() {
        env = new Environment();
        new NamingLoader().load(new File("src/main/resources/naming.yml"));
    }

    @AfterEach void close() throws Exception {
        new NamingLoader().load(save("naming.yml", ""));
        env.close();
    }

    static SandwichLayer layer(String id, String type) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("types", List.of(type));
        return new SandwichLayer(id, config);
    }

    File save(String name, String yaml) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, yaml);
        return file.toFile();
    }

    List<DishBonus> bonuses(String yaml) throws Exception {
        return DishBonus.load(YamlConfiguration.loadConfiguration(save("bonuses.yml", yaml)).getConfigurationSection("bonuses"));
    }

    FoodItem food(String id, String category, String origin, boolean valuable) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", id);
        config.set("update", false);
        config.set("valuable", valuable);
        FoodItem food = new FoodItem(id, config);
        food.setCategory(category);
        food.setOrigin(origin);
        return food;
    }

    FoodItem food(String id, String category, String origin) {
        return food(id, category, origin, false);
    }

    FoodItem dish(List<FoodItem> foods, List<DishBonus> bonuses) {
        FoodItem dish = food("sandwich", "meal", "Mixed");
        DishBonus.apply(dish, foods, layerFor, layer -> !layer.getId().equals("bread") && !layer.getId().equals("sauce"),
                bonuses);
        return dish;
    }

    static String step(FoodItem dish, String track) {
        TagTrack tag = dish.getTagTrack(track);
        return tag == null ? null : tag.getCurrentStep().getId();
    }

    static final String SANDWICH = """
            bonuses:
              size:
                track: dish_size
                count: fillings
                steps:
                  3: hearty
                  5: loaded
                  lots: loaded
              buttered:
                track: buttered
                layers: [butter]
              well_rounded:
                track: well_rounded
                layers: [Cheese, vegetable]
                any-of: [Jam, paste, butter]
              flavourful:
                track: flavourful
                valuable: true
              plain: 3
            """;

    @Test
    void loadingSkipsWhatIsNotABonusAndAMissingSectionGivesNone() throws Exception {
        assertTrue(DishBonus.load(null).isEmpty());
        assertEquals(4, bonuses(SANDWICH).size());
    }

    @Test
    void sandwichesGrowHeartyThenLoadedWithTheirFillings() throws Exception {
        List<DishBonus> bonuses = bonuses(SANDWICH);
        List<FoodItem> foods = new ArrayList<>(List.of(food("bread_slice", "grain", null), food("jam", "jam", "Plum"),
                food("jam", "jam", "Plum"), food("bread_slice", "grain", null)));
        assertNull(step(dish(foods, bonuses), "dish_size"));
        foods.add(1, food("jam", "jam", "Plum"));
        assertEquals("hearty", step(dish(foods, bonuses), "dish_size"));
        foods.add(1, food("jam", "jam", "Plum"));
        foods.add(1, food("stone", "rock", "Stone"));
        assertEquals("hearty", step(dish(foods, bonuses), "dish_size"));
        foods.add(1, food("jam", "jam", "Plum"));
        assertEquals("loaded", step(dish(foods, bonuses), "dish_size"));
    }

    @Test
    void butterCheeseVegAndValuableVegEarnTheirTags() throws Exception {
        List<DishBonus> bonuses = bonuses(SANDWICH);
        FoodItem plain = dish(List.of(food("cheese", "dairy", "Cow"), food("vegetable_cut", "vegetable", "Carrot")), bonuses);
        assertNull(step(plain, "buttered"));
        assertNull(step(plain, "well_rounded"));
        assertNull(step(plain, "flavourful"));

        FoodItem full = dish(List.of(food("butter", "dairy", "Cow"), food("cheese", "dairy", "Cow"),
                food("vegetable_cut", "vegetable", "Tomato", true)), bonuses);
        assertEquals("buttered", step(full, "buttered"));
        assertEquals("well_rounded", step(full, "well_rounded"));
        assertEquals("flavourful", step(full, "flavourful"));
        // A filling still names its track's multipliers: Buttered is +5% Food, +10% Nutrition.
        assertEquals(1.05, full.getTagTrack("buttered").getCurrentStep().getFoodMultiplier(), 1e-9);
    }

    @Test
    void pizzasCountCheeseSeasoningsAndGarnishesAgainstSpices() throws Exception {
        List<DishBonus> bonuses = bonuses("""
                bonuses:
                  extra_cheesy:
                    track: extra_cheesy
                    layers: [cheese]
                    min: 2
                  seasoning:
                    track: seasoning
                    count: seasonings
                    steps:
                      1: seasoned
                      2: well_seasoned
                  rounded:
                    track: rounded
                    addons: rounded
                  aromatic:
                    track: aromatic
                    addons: aromatic
                  sweet:
                    track: sweet_and_savoury
                    layers: [fruit, sausage]
                  saucy:
                    track: saucy
                    layers: [sauce]
                """);
        FoodItem one = dish(List.of(food("cheese", "dairy", "Cow"), food("spice", "salt", "Salt"),
                food("spice", "salt", "Salt"), food("fruit", "fruit", "Pineapple")), bonuses);
        assertNull(step(one, "extra_cheesy"));
        assertEquals("seasoned", step(one, "seasoning"));
        assertNull(step(one, "rounded"));
        assertNull(step(one, "aromatic"));
        assertNull(step(one, "sweet_and_savoury"));
        assertNull(step(one, "saucy"));

        FoodItem full = dish(List.of(food("paste", "paste", "Tomato"), food("cheese", "dairy", "Cow"),
                food("cheese", "dairy", "Goat"), food("spice", "salt", "Salt"), food("spice", "pepper", "Black Pepper"),
                food("garnish", "garnish", "Olive"), food("spice", "spice", "Basil"), food("fruit", "fruit", "Pineapple"),
                food("sausage", "meat", "Mixed")), bonuses);
        assertEquals("extra_cheesy", step(full, "extra_cheesy"));
        assertEquals("well_seasoned", step(full, "seasoning"));
        assertEquals("rounded", step(full, "rounded"));
        assertNull(step(full, "aromatic"));
        assertEquals("sweet_and_savoury", step(full, "sweet_and_savoury"));
        assertEquals("saucy", step(full, "saucy"));

        // Two basils are one spice; basil and cinnamon are two of a kind.
        FoodItem twice = dish(List.of(food("spice", "spice", "Basil"), food("spice", "spice", "Basil")), bonuses);
        assertNull(step(twice, "aromatic"));
        FoodItem two = dish(List.of(food("spice", "spice", "Basil"), food("spice", "spice", null),
                food("garnish", "garnish", "Olive")), bonuses);
        assertEquals("rounded", step(two, "rounded"));
        FoodItem kinds = dish(List.of(food("spice", "spice", "Basil"), food("spice", "spice", "Cinnamon")), bonuses);
        assertEquals("aromatic", step(kinds, "aromatic"));
    }

    @Test
    void crispNeedsEverythingWithAFreshnessStillFresh() throws Exception {
        List<DishBonus> bonuses = bonuses("""
                bonuses:
                  crisp:
                    track: crisp
                    fresh: true
                """);
        FoodItem fresh = food("jam", "jam", "Plum");
        fresh.addOrModifyTrack(new TagTrack(TrackLoader.getByString("freshness")));
        assertEquals("crisp", step(dish(List.of(fresh, food("spice", "salt", "Salt")), bonuses), "crisp"));
        FoodItem stale = food("jam", "jam", "Plum");
        TagTrack age = new TagTrack(TrackLoader.getByString("freshness"));
        age.forceSetValue((int) age.getSteps().get(1).getRequiredValue());
        stale.addOrModifyTrack(age);
        assertNull(step(dish(List.of(fresh, stale), bonuses), "crisp"));
    }

    @Test
    void aBonusTakesItsNamedStepAndSkipsTracksThatDoNotExistOrHaveNoSteps() throws Exception {
        TrackLoader.oList.add(new TagTrack("empty", false, List.of()));
        List<DishBonus> bonuses = bonuses("""
                bonuses:
                  loaded:
                    track: dish_size
                    step: loaded
                  odd:
                    track: seasoning
                    step: missing
                  missing:
                    track: nowhere
                  empty:
                    layers: []
                """);
        FoodItem dish = dish(List.of(food("jam", "jam", "Plum")), bonuses);
        assertEquals("loaded", step(dish, "dish_size"));
        assertEquals("seasoned", step(dish, "seasoning"));
        assertNull(dish.getTagTrack("nowhere"));
        assertNull(dish.getTagTrack("empty"));
    }
}
