package net.tfminecraft.cooking.brewery;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.StationFoodTemplates;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.utils.FoodParser;

class BrewingIngredientResolverTest {
    private Map<String, String> previous;
    @BeforeEach void setup() {
        previous = ConversionLoader.conversions;
        ConversionLoader.conversions = new HashMap<>();
        new ConversionLoader().load(new File("src/main/resources/conversions.yml"));
    }
    @AfterEach void cleanup() { ConversionLoader.conversions = previous; }

    @Test void reversesTheEntireConfiguredWholeIngredientCatalogAcrossFreshnessStates() {
        for (var entry : ConversionLoader.get().entrySet()) {
            String specification = entry.getValue();
            int open = specification.indexOf('(');
            var fields = FoodParser.extractFields(specification.substring(open + 1, specification.length() - 1));
            FoodItem food = food(fields.get("type"), fields.get("origin"));
            TagTrack freshness = StationFoodTemplates.freshness();
            food.addOrModifyTrack(freshness);
            String expected = List.of("v.blue_egg", "v.brown_egg").contains(entry.getKey()) ? "v.egg" : entry.getKey();
            for (int age : List.of(0, 86400, 259200)) {
                food.getTagTrack("freshness").setValue(age);
                assertEquals(expected, BrewingIngredientResolver.sourceFor(food), entry.getKey() + " at " + age);
            }
        }
    }

    @Test void rejectsPreparedFoodsAndUnknownOrAmbiguousIdentities() {
        assertNull(BrewingIngredientResolver.sourceFor(null));
        assertNull(BrewingIngredientResolver.sourceFor(food("wheat", null)));
        assertNull(BrewingIngredientResolver.sourceFor(food("soup", "Potato")));
        assertNull(BrewingIngredientResolver.sourceFor(food("vegetable_cut", "Potato")));
        assertNull(BrewingIngredientResolver.sourceFor(food("vegetable_chopped", "Potato")));
        FoodItem sauced = food("vegetable_1", "Potato");
        sauced.setSauce(food("sauce", "Tomato"));
        assertNull(BrewingIngredientResolver.sourceFor(sauced));
        FoodItem composed = food("vegetable_1", "Potato"); composed.addIngredient("Beef");
        assertNull(BrewingIngredientResolver.sourceFor(composed));
        for (String preparation : List.of("cut", "chopped", "processed", "mashed", "salted")) {
            FoodItem prepared = food("vegetable_1", "Potato");
            prepared.addOrModifyTrack(new TagTrack(preparation, false,
                    List.of(new TagStep(preparation, preparation, 0, 1, 1))));
            assertNull(BrewingIngredientResolver.sourceFor(prepared), preparation);
        }
        ConversionLoader.conversions.put("ia.other:wheat", "grain(type=wheat;origin=Wheat)");
        assertEquals("v.wheat", BrewingIngredientResolver.sourceFor(food("wheat", "Wheat")));
        ConversionLoader.conversions.remove("v.wheat");
        ConversionLoader.conversions.put("ia.another:wheat", "grain(type=wheat;origin=Wheat)");
        assertNull(BrewingIngredientResolver.sourceFor(food("wheat", "Wheat")));
        ConversionLoader.conversions.put("v.invalid", "invalid");
        ConversionLoader.conversions.put("v.unclosed", "invalid(type=wheat");
        assertNull(BrewingIngredientResolver.sourceFor(food("missing", "Missing")));
    }

    @Test void distinguishesRawAndFriedEggsAndUsesLiveConversionsAfterReload() {
        FoodItem egg = food("egg", "Egg");
        TagTrack cooked = new TagTrack("cooked", false, List.of(
                new TagStep("raw", "Raw", 0, 1, 1), new TagStep("cooked", "Cooked", 1, 1, 1)));
        egg.addOrModifyTrack(cooked);
        assertEquals("v.egg", BrewingIngredientResolver.sourceFor(egg));
        // A raw egg taken off the heat early is warm but still an egg; a warm cooked egg is not.
        egg.addOrModifyTrack(new TagTrack("warmth", true, List.of(new TagStep("hot", "Hot", 0, 1, 1))));
        assertEquals("v.egg", BrewingIngredientResolver.sourceFor(egg));
        egg.getTagTrack("cooked").setValue(1);
        assertNull(BrewingIngredientResolver.sourceFor(egg));
        FoodItem potato = food("vegetable_1", "Potato");
        potato.addOrModifyTrack(new TagTrack("empty", false, List.of()));
        assertEquals("v.potato", BrewingIngredientResolver.sourceFor(potato));
        ConversionLoader.conversions.put("v.potato", "vegetable(type=another_type;origin=Potato)");
        assertNull(BrewingIngredientResolver.sourceFor(potato));
        assertEquals("v.potato", BrewingIngredientResolver.sourceFor(food("another_type", "Potato")));
    }

    static FoodItem food(String id, String origin) {
        FoodItem food = StationFoodTemplates.template(id, "ingredient");
        food.setOrigin(origin); return food;
    }
}
