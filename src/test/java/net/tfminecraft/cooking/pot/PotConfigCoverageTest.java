package net.tfminecraft.cooking.pot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** pot.yml: stock poured instead of water, and mashes made from exactly their ingredients in an empty pot. */
class PotConfigCoverageTest {
    @TempDir Path directory;
    Cooking previousPlugin;
    List<TagTrack> previousTracks;

    @BeforeEach void open() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        previousTracks = TrackLoader.oList;
        TrackLoader.oList = new ArrayList<>();
        new TrackLoader().load(new File("src/main/resources/tags.yml"));
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        TrackLoader.oList = previousTracks;
        Cooking.plugin = previousPlugin;
        MockBukkit.unmock();
    }

    static FoodItem food(String id, String category, String origin) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("update", false);
        FoodItem food = new FoodItem(id, config);
        food.setCategory(category);
        food.setOrigin(origin);
        return food;
    }

    static FoodItem boiled(String origin, int cooked) {
        FoodItem piece = food("vegetable_cut", "vegetable", origin);
        TagTrack track = new TagTrack(TrackLoader.getByString("cooked"));
        track.forceSetValue(cooked);
        piece.addOrModifyTrack(track);
        return piece;
    }

    @Test
    void theShippedPotYmlReadsStockAndMashedPotatoes() {
        new PotConfig().load(new File("src/main/resources/pot.yml"));
        FoodItem stock = food("stock", "stock", "Bone");
        PotLiquid poured = PotConfig.liquidFor(stock);
        assertEquals(new PotLiquid("stock", "c.stock", "rich", "c9a66b", "v.glass_bottle"), poured);
        assertSame(poured, PotConfig.liquid("stock"));
        assertNull(PotConfig.liquid("broth"));
        assertNull(PotConfig.liquidFor(food("butter", "ingredient", "Cow")));

        MashRecipe mash = PotConfig.mash("mashed_potatoes");
        assertSame(mash, PotConfig.mashes().get(0));
        assertNull(PotConfig.mash("gravy"));
        assertEquals(2, mash.getServings());
        assertEquals("mash(type=mashed_potatoes;origin=Potato;tags=freshness.0)", mash.getOutput());
        assertEquals("ia.tfmc_cooking:gray", mash.getDisplay());
        assertEquals("4 boiled cut potatoes and a butter", mash.getHint());
        assertEquals(List.of(new MashRecipe.Extra("milk", "c.ingredient(type=cup_of_milk)", "sauce_creamy", "ia.tfmc_cooking:empty_cup"),
                new MashRecipe.Extra("salt", "c.salt", "seasoning", "")), mash.getExtras());

        // Only cut potatoes boiled in a pot (cooked.3) go in: not fried ones, nor boiled carrots.
        FoodItem potato = boiled("Potato", 3);
        FoodItem butter = food("butter", "ingredient", "Cow");
        FoodItem milk = food("cup_of_milk", "ingredient", "Cow");
        FoodItem salt = food("seasoning_1", "salt", "Salt");
        assertTrue(mash.takesIngredient(potato, List.of()));
        assertFalse(mash.takesIngredient(boiled("Potato", 1), List.of()));
        assertFalse(mash.takesIngredient(boiled("Carrot", 3), List.of()));
        assertTrue(mash.takesIngredient(butter, List.of(potato, potato)));
        assertFalse(mash.takesIngredient(potato, List.of(potato, potato, potato, potato)));
        assertFalse(mash.takesIngredient(butter, List.of(milk)));
        assertNull(mash.extraFor(butter));
        assertEquals("milk", mash.extraFor(milk).id());
        assertEquals("salt", mash.extraFor(salt).id());
        assertTrue(PotConfig.isMashIngredient(butter));
        assertTrue(PotConfig.isMashIngredient(salt));
        assertFalse(PotConfig.isMashIngredient(boiled("Carrot", 3)));

        List<FoodItem> all = List.of(potato, butter, potato, potato, potato);
        assertTrue(mash.complete(all));
        assertFalse(mash.complete(List.of(potato, butter)));
        assertFalse(mash.complete(List.of(potato, butter, potato, potato, potato, potato)));
        assertTrue(mash.holds(all, Set.of("milk", "salt")));
        assertFalse(mash.holds(all, Set.of("pepper")));
        assertFalse(mash.holds(List.of(milk), Set.of()));
    }

    @Test
    void missingAndBrokenEntriesFallBackToSafeDefaults() throws Exception {
        Path file = directory.resolve("pot.yml");
        Files.writeString(file, """
                liquids:
                  bare: {}
                  broken: 3
                mashes:
                  bare:
                    ingredients:
                      odd: 2
                      none:
                        amount: -3
                    extras:
                      odd: 4
                      plain: {}
                    servings: 0
                  broken: 5
                """);
        new PotConfig().load(file.toFile());
        assertEquals(List.of(new PotLiquid("bare", "", "", "3d85c6", "")), List.of(PotConfig.liquid("bare")));
        MashRecipe bare = PotConfig.mash("bare");
        assertEquals(1, PotConfig.mashes().size());
        assertEquals(1, bare.getServings());
        assertEquals("", bare.getOutput());
        assertEquals("", bare.getDisplay());
        assertEquals("the ingredients pot.yml lists", bare.getHint());
        assertEquals(List.of(new MashRecipe.Extra("plain", "", "", "")), bare.getExtras());
        // An amount below one still means one; an empty path matches nothing.
        assertFalse(bare.complete(List.of()));
        assertFalse(bare.uses(food("x", "x", "X")));

        new PotConfig().load(directory.resolve("missing.yml").toFile());
        assertTrue(PotConfig.mashes().isEmpty());
        assertNull(PotConfig.liquidFor(food("stock", "stock", "Bone")));
        assertEquals(2, new MashRecipe("blank", new MemoryConfiguration()).getServings());
    }

    @Test
    void aServingSumsItsIngredientsSplitsThemAndCarriesItsExtrasTracks() {
        new PotConfig().load(new File("src/main/resources/pot.yml"));
        MashRecipe mash = PotConfig.mash("mashed_potatoes");
        FoodItem potato = boiled("Potato", 3);
        potato.setBaseFood(7);
        potato.setBaseNutrition(4);
        FoodItem butter = food("butter", "ingredient", "Cow");
        butter.setBaseFood(10);
        butter.setBaseNutrition(6);
        FoodItem template = food("mashed_potatoes", "mash", "Potato");
        ItemStack ladle = new ItemStack(Material.PAPER);
        try (MockedStatic<FoodParser> parser = mockStatic(FoodParser.class);
             MockedStatic<CompositionQualityResolver> composer = mockStatic(CompositionQualityResolver.class);
             MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class)) {
            FoodParser.Result parsed = new FoodParser.Result();
            parsed.template = template;
            parser.when(() -> FoodParser.parse(mash.getOutput())).thenReturn(parsed);
            composer.when(() -> CompositionQualityResolver.compose(any(), any(), any()))
                    .thenReturn(new CompositionResult(60, 4, java.util.Map.of(), List.of(), List.of(), List.of(), null));
            builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenAnswer(inv -> new ItemStack(Material.PAPER));

            ItemStack serving = MashItems.serve(null, mash, List.of(potato, potato, potato, potato, butter), Set.of("milk"), ladle);
            assertTrue(MashItems.isLadled(serving));
            // Four boiled pieces (7 x 1.55) and a butter, over two servings.
            assertEquals((4 * 7 * 1.55 + 10) / 2, template.getBaseFood(), 1e-9);
            assertNotNull(template.getTagTrack("sauce_creamy"));
            assertNull(template.getTagTrack("seasoning"));
            assertEquals(4, template.getQualityMin());
            builder.verify(() -> ItemBuilder.buildSingleWithQuality(same(template), same(ladle), eq(4)));

            ItemStack plated = MashItems.unladled(serving);
            assertFalse(MashItems.isLadled(plated));
            assertTrue(MashItems.isLadled(serving));
            assertFalse(MashItems.isLadled(null));
            assertFalse(MashItems.isLadled(new ItemStack(Material.STONE)));

            builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
            assertNull(MashItems.serve(null, mash, List.of(butter), Set.of(), ladle));
            parsed.template = null;
            assertNull(MashItems.serve(null, mash, List.of(butter), Set.of(), ladle));
            parser.when(() -> FoodParser.parse(mash.getOutput())).thenReturn(null);
            assertNull(MashItems.serve(null, mash, List.of(butter), Set.of(), ladle));
        }

        FoodItem soup = food("soup", "soup", "Mixed");
        MashItems.addTrack(soup, "rich");
        assertEquals("rich", soup.getTagTrack("rich").getCurrentStep().getId());
        MashItems.addTrack(soup, "no_such_track");
        assertNull(soup.getTagTrack("no_such_track"));
    }
}
