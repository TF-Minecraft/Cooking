package net.tfminecraft.cooking.salad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.NameComposer;

/**
 * Every salad kind with every bonus, and with none, made from the shipped salads.yml: the kind it is, the name its
 * ingredients give it and the tags it earns (Hearty, Loaded, Dressed, Seasoned, Well Seasoned, Crisp).
 */
class SaladCombinationTest {
    static final String SALAD = "{prefixes}{fillers}Salad";
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

    ItemStack food(String type, String category, String origin, boolean stale) {
        FoodItem food = env.food(type, origin);
        food.setCategory(category);
        TagTrack age = new TagTrack(TrackLoader.getByString("freshness"));
        age.setValue(stale ? 100000 : 0);
        food.addOrModifyTrack(age);
        return env.stack(food, Material.PAPER, 1);
    }

    /** A kind's ingredients, as many as asked, the first one stale when it mustn't be Crisp. */
    List<ItemStack> ingredients(String kind, int count, boolean stale) {
        List<String[]> all = switch (kind) {
            case "Fruit" -> List.of(new String[] {"fruit_1", "fruit", "Apple"}, new String[] {"fruit_2", "fruit", "Plum"},
                    new String[] {"fruit_2", "fruit", "Strawberry"}, new String[] {"fruit_2", "fruit", "Banana"},
                    new String[] {"fruit_1", "fruit", "Melon Slice"});
            case "Greek" -> List.of(new String[] {"cheese", "dairy", "Goat"}, new String[] {"garnish_chopped", "garnish", "Olive"},
                    new String[] {"vegetable_chopped_2", "garnish", "Tomato"},
                    new String[] {"vegetable_chopped_2", "garnish", "Cucumber"},
                    new String[] {"vegetable_cut_2", "vegetable", "Onion"});
            case "Carrot" -> List.of(new String[] {"vegetable_chopped", "garnish", "Carrot"},
                    new String[] {"vegetable_chopped_2", "garnish", "Lettuce"},
                    new String[] {"vegetable_cut", "vegetable", "Carrot"},
                    new String[] {"vegetable_chopped", "garnish", "Carrot"}, new String[] {"vegetable_chopped_2", "garnish", "Corn"});
            default -> List.of(new String[] {"vegetable_chopped_2", "garnish", "Tomato"},
                    new String[] {"meat_poultry", "meat", "Chicken"}, new String[] {"cheese", "dairy", "Cow"},
                    new String[] {"grilled_mushroom", "vegetable", "Brown Mushroom"}, new String[] {"sausage", "meat", "Mixed"});
        };
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(food(all.get(i)[0], all.get(i)[1], all.get(i)[2], stale && i == 0));
        return out;
    }

    // naming.yml fillers max: 3, and an origin names it once.
    static final Map<String, Map<Integer, String>> NAMES = Map.of(
            "Fruit", Map.of(2, "Apple and Plum Salad", 3, "Apple, Plum and Strawberry Salad",
                    5, "Apple, Plum and Strawberry Salad"),
            "Greek", Map.of(3, "Goat Cheese, Olive and Tomato Salad", 5, "Goat Cheese, Olive and Tomato Salad"),
            "Carrot", Map.of(2, "Carrot and Lettuce Salad", 3, "Carrot and Lettuce Salad", 5, "Carrot, Lettuce and Corn Salad"),
            "Mixed", Map.of(2, "Tomato and Chicken Salad", 3, "Tomato, Chicken and Cheese Salad",
                    5, "Tomato, Chicken and Cheese Salad"));

    @Test
    void everyKindWithEveryBonusAndWithNone() {
        List<String> bonuses = List.of("none", "hearty", "loaded", "dressed", "seasoned", "well_seasoned", "crisp", "all");
        int made = 0;
        for (String kind : List.of("Fruit", "Greek", "Carrot", "Mixed")) {
            for (String bonus : bonuses) {
                boolean all = bonus.equals("all");
                // A Greek salad needs three things in it (cheese, olive, tomato), so it's always at least Hearty.
                int count = bonus.equals("loaded") || all ? 5 : bonus.equals("hearty") || kind.equals("Greek") ? 3 : 2;
                boolean crisp = bonus.equals("crisp") || all;
                List<ItemStack> extras = new ArrayList<>();
                if (bonus.equals("dressed") || all) extras.add(food("vinegar", "vinegar", "Wine", false));
                if (bonus.equals("seasoned") || bonus.equals("well_seasoned") || all) extras.add(food("seasoning_1", "salt", "Salt", false));
                if (bonus.equals("well_seasoned") || all) extras.add(food("spice_1", "pepper", "Black Pepper", false));

                assertNotNull(SaladItems.build(env.player, ingredients(kind, count, !crisp), extras));
                String which = kind + " " + bonus;
                assertEquals(kind, built.getOrigin(), which);
                assertEquals(NAMES.get(kind).get(count), NameComposer.compose(built, SALAD, Map.of()), which);

                Map<String, String> want = new TreeMap<>();
                if (count >= 5) want.put("dish_size", "loaded");
                else if (count >= 3) want.put("dish_size", "hearty");
                if (bonus.equals("dressed") || all) want.put("dressed", "dressed");
                if (bonus.equals("seasoned")) want.put("seasoning", "seasoned");
                if (bonus.equals("well_seasoned") || all) want.put("seasoning", "well_seasoned");
                if (crisp) want.put("crisp", "crisp");
                Map<String, String> got = new TreeMap<>();
                for (String track : List.of("dish_size", "dressed", "seasoning", "crisp", "poison")) {
                    TagTrack tag = built.getTagTrack(track);
                    if (tag != null) got.put(track, tag.getCurrentStep().getId());
                }
                assertEquals(want, got, which);
                made++;
            }
        }
        assertEquals(32, made);
    }
}
