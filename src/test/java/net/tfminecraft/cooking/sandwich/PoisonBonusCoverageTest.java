package net.tfminecraft.cooking.sandwich;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.nutrition.DietMath;
import net.tfminecraft.cooking.quality.CompositionConfig;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/**
 * Red mushrooms poison a dish (count: origin bonuses): Tainted, Poisoned, then Deadly with each one, raw or grilled. The
 * poison is hidden when the dish has enough stars and is cooked well (hide-tags on the mushrooms, hide-dish-tags on
 * the dish's own bake), and shows otherwise. The repo's default configs make a 5-star grilled-mushroom sandwich hide it.
 */
class PoisonBonusCoverageTest {
    static final String POISON = """
            bonuses:
              poison:
                track: poison
                count: origin
                origin: Red Mushroom
                steps:
                  1: tainted
                  2: poisoned
                  3: deadly
                hide-at-stars: 3
                hide-tags: [Cooked]
            """;

    List<TagTrack> tracks;
    List<FoodItem> foods;
    Cooking previousPlugin;
    @TempDir Path directory;
    final SandwichLayer bread = layer("bread", "bread_slice");
    final SandwichLayer mushroom = layer("mushroom", "grilled_mushroom");

    @BeforeEach void open() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        tracks = new ArrayList<>(TrackLoader.oList);
        foods = new ArrayList<>(FoodLoader.oList);
        new TrackLoader().load(new File("src/main/resources/tags.yml"));
        new NamingLoader().load(new File("src/main/resources/naming.yml"));
        QualityConfig.apply(1, 5, null, null);
        CompositionConfig.apply(false, Set.of(), null, Set.of(), Map.of(), false, 0, 0, false);
    }

    @AfterEach void close() throws Exception {
        new SandwichConfig().load(directory.resolve("missing.yml").toFile());
        new NamingLoader().load(save("naming.yml", ""));
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        Cooking.plugin = previousPlugin;
        MockBukkit.unmock();
    }

    File save(String name, String yaml) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, yaml);
        return file.toFile();
    }

    List<DishBonus> bonuses(String yaml) throws Exception {
        return DishBonus.load(YamlConfiguration.loadConfiguration(save("bonuses.yml", yaml)).getConfigurationSection("bonuses"));
    }

    static SandwichLayer layer(String id, String type) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("types", List.of(type));
        return new SandwichLayer(id, config);
    }

    static FoodItem food(String id, String category, String origin, String... steps) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", id);
        config.set("update", false);
        FoodItem food = new FoodItem(id, config);
        food.setCategory(category);
        food.setOrigin(origin);
        for (String step : steps) {
            String[] at = step.split("\\.");
            food.addOrModifyTrack(at(at[0], at[1]));
        }
        return food;
    }

    static TagTrack at(String trackId, String stepId) {
        TagTrack track = new TagTrack(TrackLoader.getByString(trackId));
        for (TagStep step : track.getSteps()) {
            if (step.getId().equals(stepId)) track.forceSetValue((int) step.getRequiredValue());
        }
        return track;
    }

    static FoodItem grilled(String origin) {
        return food("grilled_mushroom", "vegetable", origin, "cooked.cooked");
    }

    FoodItem dish(int stars, List<FoodItem> layers, List<DishBonus> bonuses) {
        FoodItem dish = food("sandwich", "meal", "Mixed");
        dish.setQualityRange(stars, stars);
        DishBonus.apply(dish, layers, food -> food.getId().equals("bread_slice") ? bread
                : food.getId().equals("grilled_mushroom") ? mushroom : null, layer -> layer != bread, bonuses);
        return dish;
    }

    static String poison(FoodItem dish) {
        TagTrack track = dish.getTagTrack("poison");
        return track == null ? null : track.getCurrentStep().getId() + (track.isHidden() ? " (hidden)" : "");
    }

    @Test
    void eachRedMushroomRaisesThePoisonWhateverItIsAndWhereverItLies() throws Exception {
        List<DishBonus> bonuses = bonuses(POISON);
        List<FoodItem> layers = new ArrayList<>(List.of(food("bread_slice", "grain", "Wheat"), grilled("Brown Mushroom")));
        assertNull(poison(dish(5, layers, bonuses)));
        layers.add(grilled("Red Mushroom"));
        assertEquals("tainted (hidden)", poison(dish(5, layers, bonuses)));
        layers.add(grilled("red mushroom"));
        assertEquals("poisoned (hidden)", poison(dish(5, layers, bonuses)));
        // A raw one counts too, even where no layer takes it, and shows the poison: it isn't grilled.
        layers.add(food("mushroom", "vegetable", "Red Mushroom"));
        assertEquals("deadly", poison(dish(5, layers, bonuses)));
        layers.add(grilled("Red Mushroom"));
        assertEquals("deadly", poison(dish(5, layers, bonuses)));
        // Deadly takes all of the dish's nutrition, but none of its food.
        TagStep deadly = dish(5, layers, bonuses).getTagTrack("poison").getCurrentStep();
        assertEquals(0.0, deadly.getNutritionMultiplier());
        assertEquals(1.0, deadly.getFoodMultiplier());
    }

    @Test
    void thePoisonShowsInADishUnderThreeStarsOrWithABurntMushroom() throws Exception {
        List<DishBonus> bonuses = bonuses(POISON);
        List<FoodItem> grilled = List.of(grilled("Red Mushroom"), grilled("Red Mushroom"));
        assertEquals("poisoned (hidden)", poison(dish(3, grilled, bonuses)));
        assertEquals("poisoned", poison(dish(2, grilled, bonuses)));
        List<FoodItem> burnt = List.of(grilled("Red Mushroom"),
                food("grilled_mushroom", "vegetable", "Red Mushroom", "cooked.burnt"));
        assertEquals("poisoned", poison(dish(5, burnt, bonuses)));
        // A burnt brown mushroom gives nothing away: only the red ones have to be grilled.
        List<FoodItem> brown = List.of(grilled("Red Mushroom"),
                food("grilled_mushroom", "vegetable", "Brown Mushroom", "cooked.burnt"));
        assertEquals("tainted (hidden)", poison(dish(5, brown, bonuses)));
    }

    @Test
    void aBakedDishHidesItWhileTheDishItselfIsCookedAndStarsCountItsBonuses() throws Exception {
        List<DishBonus> bonuses = bonuses("""
                bonuses:
                  poison:
                    track: poison
                    count: origin
                    origin: Red Mushroom
                    steps: {1: tainted}
                    hide-at-stars: 4
                    hide-dish-tags: [cooked]
                  shown:
                    track: dish_size
                    count: origin
                    origin: Red Mushroom
                    steps: {1: hearty}
                """);
        // The pizza's own bake decides, not the raw mushroom it baked.
        FoodItem cooked = food("pizza_slice", "meal", "Mixed", "cooked.cooked");
        cooked.setQualityRange(4, 4);
        DishBonus.apply(cooked, List.of(food("mushroom", "vegetable", "Red Mushroom")), f -> null, l -> true, bonuses);
        assertEquals("tainted (hidden)", poison(cooked));
        assertFalse(cooked.getTagTrack("dish_size").isHidden(), "A bonus without hide-at-stars always shows");

        FoodItem burnt = food("pizza_slice", "meal", "Mixed", "cooked.burnt");
        burnt.setQualityRange(5, 5);
        DishBonus.apply(burnt, List.of(food("mushroom", "vegetable", "Red Mushroom")), f -> null, l -> true, bonuses);
        assertEquals("tainted", poison(burnt));

        // Every bonus is in before the stars are judged: a 5-star dish that a bonus takes down to 3 shows it.
        TrackLoader.oList.add(new TagTrack("soggy", false,
                List.of(new TagStep("soggy", "Soggy", 0, 1.0, 1.0, 0, 0, -2))));
        List<DishBonus> soggy = new ArrayList<>(bonuses);
        soggy.addAll(bonuses("""
                bonuses:
                  soggy:
                    track: soggy
                """));
        FoodItem pie = food("pie", "meal", "Mixed", "cooked.cooked");
        pie.setQualityRange(5, 5);
        DishBonus.apply(pie, List.of(food("mushroom", "vegetable", "Red Mushroom")), f -> null, l -> true, soggy);
        assertEquals("tainted", poison(pie));
    }

    @Test
    void hideTagsJudgeTheFoodsEachKindOfCountCounted() throws Exception {
        List<DishBonus> bonuses = bonuses("""
                bonuses:
                  size:
                    track: dish_size
                    count: fillings
                    steps: {1: hearty}
                    hide-at-stars: 1
                    hide-tags: [cooked]
                  seasoning:
                    track: seasoning
                    count: seasonings
                    steps: {1: seasoned}
                    hide-at-stars: 1
                    hide-tags: [cooked]
                  buttered:
                    track: buttered
                    hide-at-stars: 1
                    hide-tags: [cooked]
                """);
        SandwichLayer spice = layer("spice", "seasoning_1");
        FoodItem dish = food("sandwich", "meal", "Mixed");
        dish.setQualityRange(5, 5);
        DishBonus.apply(dish, List.of(food("bread_slice", "grain", "Wheat"), grilled("Brown Mushroom"),
                food("seasoning_1", "salt", "Salt")), f -> switch (f.getId()) {
                    case "bread_slice" -> bread;
                    case "grilled_mushroom" -> mushroom;
                    default -> spice;
                }, l -> l == mushroom, bonuses);
        // The filling is grilled; the salt isn't cooked; and an uncounted bonus judges every layer (the bread too).
        assertTrue(dish.getTagTrack("dish_size").isHidden());
        assertFalse(dish.getTagTrack("seasoning").isHidden());
        assertFalse(dish.getTagTrack("buttered").isHidden());
    }

    /** A food built from its food string, at 5 stars, with the given model so it makes an item. */
    static ItemStack item(String line) {
        FoodItem template = FoodParser.parse(line).template;
        return ItemBuilder.buildSingleWithQuality(template, 5);
    }

    // The repo's types.yml has no sandwich or bread slice (Dev's config adds them): these are Dev's.
    static final String DEV_TYPES = """
            bread_slice:
              name: "#d9c4a5{prefixes}{fillers}Bread Slice"
              food: 6.5
              nutrition: 5
            sandwich:
              name: "#e0b878{prefixes}{fillers}Sandwich"
              food: 20
              nutrition: 6
              age:
                freshness: 1
            """;

    @Test
    void theRepoConfigsHideTwoGrilledRedMushroomsInAFiveStarSandwich() throws Exception {
        new FoodLoader().load(new File("src/main/resources/types.yml"));
        YamlConfiguration dev = YamlConfiguration.loadConfiguration(save("dev-types.yml", DEV_TYPES));
        for (String type : dev.getKeys(false)) FoodLoader.oList.add(new FoodItem(type, dev.getConfigurationSection(type)));
        for (String type : List.of("bread_slice", "grilled_mushroom", "sandwich")) {
            FoodLoader.getByString(type).setModel(new FoodModel(new ItemStack(Material.PAPER)));
        }
        new SandwichConfig().load(new File("src/main/resources/sandwiches.yml"));
        String bread = "grain(type=bread_slice;origin=Wheat;quality=5;tags=cooked.1:freshness.0)";
        String red = "vegetable(type=grilled_mushroom;origin=Red Mushroom;quality=5;tags=cooked.1:freshness.0)";
        ItemStack sandwich = SandwichItems.build(null, List.of(item(bread), item(red), item(red), item(bread)));

        // Layers: 2 x bread 6.5 x 1.55 (Cooked) + 2 x mushroom 6 x 1.55 = 38.75 food, at a weighted nutrition of
        // (2 x 10.075 x 6.25 + 2 x 9.3 x 5.625) / 38.75 = 5.95. At 5 stars (Food x1.8, Nutrition x1.7) and Fresh
        // (Nutrition x1.5) it shows 69.8 Food and 15.2 Nutrition; Poisoned (x0.15) makes it 2.3 when eaten.
        assertEquals("§x§e§0§b§8§7§8Mushroom Sandwich", sandwich.getItemMeta().getDisplayName());
        List<String> lore = sandwich.getItemMeta().getLore();
        assertTrue(lore.stream().anyMatch(line -> line.contains("Food §f69.8")), lore.toString());
        assertTrue(lore.stream().anyMatch(line -> line.contains("Nutrition §f15.2")), lore.toString());
        assertTrue(lore.stream().anyMatch(line -> line.equals("§7Mushroom")), lore.toString());
        assertTrue(lore.stream().noneMatch(line -> line.contains("Poison") || line.contains("Red")), lore.toString());

        FoodItem eaten = FoodItem.fromItem(sandwich);
        assertEquals("poisoned", eaten.getTagTrack("poison").getCurrentStep().getId());
        assertEquals(69.8, eaten.getFinalFood(), 1e-9);
        assertEquals(2.3, eaten.getFinalNutrition(), 1e-9);
        // An eater at Nutrition 30 with room for all 70 food (max food 200): expects 30 + (15.2 - 30) x 70/200 = 25,
        // gets 30 + (2.3 - 30) x 70/200 = 20.
        int gained = (int) Math.ceil(eaten.getFinalFood());
        assertEquals(25, DietMath.lerpRaw(30, eaten.getShownNutrition(), gained, 200, 1.0));
        assertEquals(20, DietMath.lerpRaw(30, eaten.getFinalNutrition(), gained, 200, 1.0));

        // A burnt red mushroom shows it, and the values with it: a burnt layer feeds 6 x 0.25 at 4.5 x 0.1.
        String burnt = "vegetable(type=grilled_mushroom;origin=Red Mushroom;quality=5;tags=cooked.2:freshness.0)";
        List<String> shown = SandwichItems.build(null, List.of(item(bread), item(burnt), item(red), item(bread)))
                .getItemMeta().getLore();
        assertTrue(shown.stream().anyMatch(line -> line.contains("Poisoned")), shown.toString());
        assertTrue(shown.stream().anyMatch(line -> line.contains("Food §f55.7")), shown.toString());
        assertTrue(shown.stream().anyMatch(line -> line.contains("Nutrition §f2.2")), shown.toString());
    }
}
