package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.baking.BakingTrayFill;
import net.tfminecraft.cooking.baking.BakingTrayTransform;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionConfig;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.FoodParser;

class IngredientLineageTest {
    private static final String LOAF_ID = "lineage_test_loaf";

    @BeforeEach
    void useStationRoles() {
        Map<CompositionContext, CompositionConfig.RoleSets> overrides = Map.of(
                CompositionContext.SOUP_SCOOP, roles(Set.of("salt", "pepper")),
                CompositionContext.SAUCE_SCOOP, roles(Set.of("salt", "pepper", "sweetener", "flour")),
                CompositionContext.MIXING_BOWL, roles(Set.of("sweetener")),
                CompositionContext.CHURN, roles(Set.of("salt", "spice", "seasoning")),
                CompositionContext.FRYING_PAN, roles(Set.of("dairy")),
                CompositionContext.SAUSAGE_MAKER, roles(Set.of())
        );
        CompositionConfig.apply(
                false,
                Set.of(),
                Set.of("seasoning", "sweetener", "flour", "salt", "pepper"),
                Set.of("water"),
                overrides,
                false,
                0,
                0,
                false);
    }

    @AfterEach
    void restoreDefaults() {
        CompositionConfig.apply(true, Set.of(), null, Set.of(), Map.of(), true, 0.12, 0.15, true);
        FoodLoader.get().removeIf(item -> LOAF_ID.equals(item.getId()));
    }

    @Test
    void dedupesOriginsAndIgnoresPlaceholders() {
        IngredientLineage lineage = IngredientLineage.empty()
                .withMain("Tomato")
                .withMain(" tomato ")
                .withMain("Mixed")
                .withMain("  ")
                .withExtra("Salt")
                .withExtra("salt")
                .withExtra("Tomato");

        assertEquals(List.of("Tomato"), lineage.mains());
        assertEquals(List.of("Salt"), lineage.extras());
    }

    @Test
    void codecRoundTripsSeparators() {
        IngredientLineage lineage = IngredientLineage.ofMain("Beef;Pork")
                .withMain("A=B,C")
                .withExtra("Salt (fine)");

        assertEquals(lineage, IngredientLineageCodec.decode(IngredientLineageCodec.encode(lineage)));
        assertTrue(IngredientLineageCodec.decode("9|Tomato|").isEmpty());
        assertTrue(IngredientLineageCodec.decode(null).isEmpty());
    }

    @Test
    void parserRoundTripsLineage() {
        FoodItem template = new FoodItem("soup", "Soup", true);
        FoodLoader.get().add(template);
        try {
            String encoded = IngredientLineageCodec.encode(
                    IngredientLineage.ofMain("Tomato").withExtra("Salt"));
            FoodParser.Result parsed = FoodParser.parse(
                    "meal(type=soup;origin=Mixed;lineage=" + encoded + ")");
            assertEquals(List.of("Tomato"), parsed.template.getLineage().mains());
            assertEquals(List.of("Salt"), parsed.template.getLineage().extras());

            String written = FoodParser.toString(parsed.template, 1);
            FoodParser.Result again = FoodParser.parse(written);
            assertEquals(parsed.template.getLineage(), again.template.getLineage());
        } finally {
            FoodLoader.get().remove(template);
        }
    }

    @Test
    void rawOriginIsClassifiedAndNestedLineageIsPreserved() {
        FoodItem beef = food("roast", "meat", "Beef");
        FoodItem pork = food("roast", "meat", "Pork");
        CompositionResult sausage = CompositionQualityResolver.compose(
                null, List.of(beef, pork), CompositionContext.SAUSAGE_MAKER);
        assertEquals(List.of("Beef", "Pork"), sausage.getLineage().mains());

        FoodItem chain = food("sausage_chain", "meat", "Mixed");
        chain.setLineage(sausage.getLineage());
        FoodItem tomato = food("vegetable_2", "vegetable", "Tomato");
        FoodItem salt = food("seasoning_1", "salt", "Salt");
        FoodItem water = food("water", "water", "Spring");

        CompositionResult soup = CompositionQualityResolver.compose(
                null, List.of(chain, tomato, salt, water), CompositionContext.SOUP_SCOOP);
        assertEquals(List.of("Beef", "Pork", "Tomato"), soup.getLineage().mains());
        assertEquals(List.of("Salt"), soup.getLineage().extras());

        FoodItem tuna = food("seafood_fish_filet", "seafood", "Tuna");
        FoodItem blue = food("seafood_jellyfish", "seafood", "Blue Jellyfish");
        FoodItem pink = food("seafood_jellyfish", "seafood", "Pink Jellyfish");
        FoodItem octopus = food("seafood_octopus", "seafood", "Octopus");
        FoodItem steak = food("meat_red_meat", "meat", "Beef");
        FoodItem chicken = food("meat_poultry", "meat", "Chicken");
        FoodItem soupSalt = food("seasoning_1", "salt", "Salt");
        CompositionResult seafoodSoup = CompositionQualityResolver.compose(
                null, List.of(tuna, blue, pink, octopus, steak, chicken, soupSalt), CompositionContext.SOUP_SCOOP);
        assertEquals(List.of("Tuna", "Blue Jellyfish", "Pink Jellyfish", "Octopus", "Beef", "Chicken"),
                seafoodSoup.getLineage().mains());
        assertEquals(List.of("Salt"), seafoodSoup.getLineage().extras());

        FoodItem output = food("soup", "meal", "Mixed");
        CompositionApplier.apply(output, soup);
        assertEquals(soup.getLineage(), output.getLineage());
    }

    @Test
    void sauceCuttingCarveAndButterKeepTheirBuckets() {
        FoodItem pepper = food("seasoning_1", "pepper", "Pepper");
        FoodItem cream = food("dairy", "dairy", "Cow");
        CompositionResult sauce = CompositionQualityResolver.compose(
                null, List.of(cream, pepper), CompositionContext.SAUCE_SCOOP);
        assertEquals(List.of("Cow"), sauce.getLineage().mains());
        assertEquals(List.of("Pepper"), sauce.getLineage().extras());

        FoodItem carrot = food("vegetable_cut", "vegetable", "Carrot");
        FoodItem carrotAgain = food("vegetable_cut", "vegetable", "carrot");
        CompositionResult cut = CompositionQualityResolver.compose(
                null, List.of(carrot, carrotAgain), CompositionContext.CUTTING_BOARD);
        assertEquals(List.of("Carrot"), cut.getLineage().mains());

        FoodItem roast = food("roast", "meat", "Chicken");
        roast.setLineage(IngredientLineage.ofMain("Chicken").withExtra("Butter"));
        FoodItem slice = food("meat_slice", "meat", null);
        CarvableRoastUtils.copyInheritedTracks(roast, slice);
        assertEquals(roast.getLineage(), slice.getLineage());

        CompositionResult carved = CompositionQualityResolver.compose(
                null, List.of(roast), CompositionContext.CARVE);
        assertEquals(List.of("Chicken"), carved.getLineage().mains());
        assertEquals(List.of("Butter"), carved.getLineage().extras());

        FoodItem milk = food("milk_bucket", "dairy", "Goat");
        FoodItem spice = food("spice_1", "spice", "Garlic");
        FoodItem churnSalt = food("seasoning_1", "salt", "Salt");
        CompositionResult butter = CompositionQualityResolver.compose(
                null, List.of(milk, spice, churnSalt), CompositionContext.CHURN);
        assertEquals(List.of("Goat"), butter.getLineage().mains());
        assertEquals(List.of("Garlic", "Salt"), butter.getLineage().extras());

        FoodItem steak = food("roast", "meat", "Beef");
        FoodItem panButter = food("butter", "dairy", "Cow");
        panButter.setLineage(butter.getLineage());
        CompositionResult fried = CompositionQualityResolver.compose(
                null, List.of(steak, panButter), CompositionContext.FRYING_PAN);
        assertEquals(List.of("Beef", "Goat"), fried.getLineage().mains());
        assertEquals(List.of("Garlic", "Salt"), fried.getLineage().extras());
    }

    @Test
    void doughLineageCopiesOntoTheLoaf() {
        FoodItem loafTemplate = new FoodItem(LOAF_ID, "Loaf", true);
        FoodLoader.get().add(loafTemplate);

        FoodItem dough = food("dough", "grain", "Wheat");
        dough.setLineage(IngredientLineage.ofMain("Wheat").withMain("Strawberry").withExtra("Sugar"));
        dough.addIngredient("Strawberry");

        BakingTrayFill fill = new BakingTrayFill("dough", "dough", 1, LOAF_ID, "", "grain");
        FoodItem loaf = BakingTrayTransform.doughToLoaf(dough, fill);

        assertEquals(dough.getLineage(), loaf.getLineage());
        assertEquals(List.of("Strawberry"), loaf.getIngredients());
    }

    @Test
    void eatenLineageIncludesNestedSauceAndFallsBackToOrigin() {
        FoodItem steak = food("roast", "meat", "Beef");
        FoodItem sauce = food("sauce", "sauce", "Mixed");
        sauce.setLineage(IngredientLineage.ofMain("Tomato").withExtra("Salt"));
        steak.setSauce(sauce);

        IngredientLineage eaten = IngredientLineage.forEat(steak);
        assertEquals(List.of("Beef", "Tomato"), eaten.mains());
        assertEquals(List.of("Salt"), eaten.extras());

        FoodItem legacy = food("vegetable_1", "vegetable", "Carrot");
        assertEquals(List.of("Carrot"), IngredientLineage.forEat(legacy).mains());
    }

    private static CompositionConfig.RoleSets roles(Set<String> extras) {
        return new CompositionConfig.RoleSets(Set.of(), extras, Set.of());
    }

    private static FoodItem food(String id, String category, String origin) {
        FoodItem item = new FoodItem(id, id, true);
        item.setCategory(category);
        item.setOrigin(origin);
        item.setQualityRange(3, 3);
        return item;
    }
}
