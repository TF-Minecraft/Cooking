package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;

class EggIngredientTest {

    @Test
    void rawEggIsInedibleUntilItIsFried() {
        FoodItem raw = egg();
        raw.addOrModifyTrack(cookedTrack(0));
        assertFalse(raw.isEdible());

        FoodItem cooked = egg();
        cooked.addOrModifyTrack(cookedTrack(1));
        assertTrue(cooked.isEdible());

        FoodItem burnt = egg();
        burnt.addOrModifyTrack(cookedTrack(2));
        assertTrue(burnt.isEdible());
    }

    @Test
    void eggStatesAndHusbandryQualityAreConfigured() throws Exception {
        String types = Files.readString(Path.of("src/main/resources/types.yml"));
        String models = Files.readString(Path.of("src/main/resources/models.yml"));
        String conversions = Files.readString(Path.of("src/main/resources/conversions.yml"));
        String husbandry = Files.readString(Path.of("src/main/resources/husbandry.yml"));
        String items = Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/items.yml"));

        assertTrue(types.contains("edible-when-cooked: true"));
        assertTrue(conversions.contains("v.egg egg(type=egg;origin=Egg;tags=freshness.0:cooked.0)"));
        assertTrue(husbandry.contains("food(type=egg;origin=Egg;tags=freshness.0:cooked.0)"));
        assertTrue(husbandry.contains("egg-timer: 1h"));
        assertFalse(husbandry.contains("egg-timer: 10m"));
        for (String id : List.of(
                "egg_rotten",
                "fried_egg_raw",
                "fried_egg_cooked",
                "fried_egg_burnt",
                "fried_egg_rotten")) {
            assertTrue(items.contains(id + ":"), id);
            assertTrue(models.contains("ia.tfmc_cooking:" + id), id);
        }
        assertTrue(models.contains("item: v.egg"));
    }

    private static FoodItem egg() {
        FoodItem item = new FoodItem("egg", "Egg", true);
        item.setEdibleWhenCooked(true);
        return item;
    }

    private static TagTrack cookedTrack(int value) {
        TagTrack track = new TagTrack("cooked", false, List.of(
                new TagStep("raw", "Raw", 0, 0.4, 0.5),
                new TagStep("cooked", "Cooked", 1, 1.55, 1.25),
                new TagStep("burnt", "Burnt", 2, 0.25, 0.1)));
        track.forceSetValue(value);
        return track;
    }
}
