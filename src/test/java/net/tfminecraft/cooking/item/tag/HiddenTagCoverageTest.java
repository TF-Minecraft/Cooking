package net.tfminecraft.cooking.item.tag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.cooking.utils.NameComposer;

/**
 * A hidden tag (a poison slipped into a dish) is eaten but not shown: the lore leaves out its line, and the stars, Food
 * and Nutrition it shows leave it out too. It stays hidden through stamping, re-reading, copying, food strings and
 * names, and a hidden tag doesn't stack with a shown one.
 */
class HiddenTagCoverageTest {
    List<TagTrack> tracks;
    List<FoodItem> foods;
    Cooking previousPlugin;
    @TempDir Path directory;

    @BeforeEach void open() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        tracks = new ArrayList<>(TrackLoader.oList);
        foods = new ArrayList<>(FoodLoader.oList);
        new TrackLoader().load(new File("src/main/resources/tags.yml"));
        FoodLoader.oList.clear();
        QualityConfig.apply(1, 5, null, null);
    }

    @AfterEach void close() throws Exception {
        Path empty = directory.resolve("empty.yml");
        Files.writeString(empty, "");
        new NamingLoader().load(empty.toFile());
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        Cooking.plugin = previousPlugin;
        MockBukkit.unmock();
    }

    /** A 5-star sandwich type with 10 food and 10 nutrition, known to FoodLoader so stacks and strings resolve it. */
    FoodItem sandwich() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "Sandwich");
        config.set("food", 10);
        config.set("nutrition", 10);
        config.set("update", false);
        FoodItem type = new FoodItem("sandwich", config);
        type.setModel(new FoodModel(new ItemStack(Material.BREAD)));
        FoodLoader.oList.add(type);
        FoodItem food = new FoodItem(type);
        food.setCategory("meal");
        food.setOrigin("Mixed");
        food.setQualityRange(5, 5);
        return food;
    }

    static TagTrack at(String trackId, String stepId) {
        TagTrack track = new TagTrack(TrackLoader.getByString(trackId));
        for (TagStep step : track.getSteps()) {
            if (step.getId().equals(stepId)) track.forceSetValue((int) step.getRequiredValue());
        }
        return track;
    }

    static FoodItem poisoned(FoodItem food, boolean hidden) {
        TagTrack poison = at("poison", "poisoned");
        poison.setHidden(hidden);
        food.addOrModifyTrack(poison);
        return food;
    }

    static boolean anyLine(List<String> lore, String text) {
        return lore.stream().anyMatch(line -> line.contains(text));
    }

    @Test
    void aHiddenTagIsEatenButTheLoreShowsNeitherItsLineNorItsEffect() {
        FoodItem food = poisoned(sandwich(), true);
        food.addOrModifyTrack(at("dish_size", "hearty"));

        // Eating counts it: 10 x 1.70 (5 stars) x 0.15 (Poisoned) = 2.6; Food is untouched (x1.8 stars, Hearty x1.1).
        assertEquals(2.6, food.getFinalNutrition(), 1e-9);
        assertEquals(19.8, food.getFinalFood(), 1e-9);
        assertEquals(17.0, food.getShownNutrition(), 1e-9);
        assertEquals(19.8, food.getShownFood(), 1e-9);

        List<String> lore = ItemBuilder.buildLore(food);
        assertTrue(anyLine(lore, "Nutrition §f17.0"));
        assertTrue(anyLine(lore, "Food §f19.8"));
        assertTrue(anyLine(lore, "Hearty"));
        assertFalse(anyLine(lore, "Poisoned"));
        assertFalse(anyLine(lore, "-85% Nutrition"));

        food.getTagTrack("poison").setHidden(false);
        lore = ItemBuilder.buildLore(food);
        assertTrue(anyLine(lore, "Nutrition §f2.6"));
        assertTrue(anyLine(lore, "Poisoned"));
        assertTrue(anyLine(lore, "-85% Nutrition"));
    }

    @Test
    void hiddenStarsAndAHiddenTagOnASauceStayOutOfTheLoreToo() {
        FoodItem food = sandwich();
        TagTrack spoiled = new TagTrack("spoiled", false,
                List.of(new TagStep("spoiled", "Spoiled", 0, 1.0, 1.0, 0, 0, -2)));
        spoiled.setHidden(true);
        food.addOrModifyTrack(spoiled);
        assertEquals(3, food.getEffectiveQuality());
        assertEquals(5, food.getShownQuality());
        assertTrue(ItemBuilder.buildLore(food).contains(ItemBuilder.qualityStars(5)));
        // Eaten at 3 stars (x0.75), shown at 5 (x1.70).
        assertEquals(7.5, food.getFinalNutrition(), 1e-9);
        assertEquals(17.0, food.getShownNutrition(), 1e-9);

        FoodItem plain = sandwich();
        FoodItem sauce = poisoned(sandwich(), true);
        plain.setSauce(sauce);
        plain.setSauceName("Gravy");
        assertEquals(17.0 + 2.6, plain.getFinalNutrition(), 1e-9);
        assertEquals(34.0, plain.getShownNutrition(), 1e-9);
        assertTrue(anyLine(ItemBuilder.buildLore(plain), "+17.0 Nutrition"));
    }

    @Test
    void stampingRereadingCopyingAndFoodStringsKeepItHidden() {
        FoodItem food = poisoned(sandwich(), true);
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.PAPER), food, "Mushroom Sandwich");
        assertEquals("poison",
                stack.getItemMeta().getPersistentDataContainer().get(Keys.HIDDEN_TAGS, PersistentDataType.STRING));
        FoodItem read = FoodItem.fromItem(stack);
        assertTrue(read.getTagTrack("poison").isHidden());
        assertEquals(List.of("poison"), read.getHiddenTrackIds());
        assertTrue(new FoodItem(read).getTagTrack("poison").isHidden());
        // A rebuild (ItemUpdater stamps again from what it read) still hides it.
        ItemStack rebuilt = ItemBuilder.stamp(stack, read, null);
        assertFalse(anyLine(rebuilt.getItemMeta().getLore(), "Poisoned"));

        String line = FoodParser.toString(read, 1);
        assertTrue(line.contains("hidden=poison;"));
        assertTrue(FoodParser.parse(line).template.getTagTrack("poison").isHidden());

        // Shown again, the stack loses the marker and the food string doesn't write one.
        read.getTagTrack("poison").setHidden(false);
        ItemBuilder.stamp(stack, read, null);
        assertFalse(stack.getItemMeta().getPersistentDataContainer().has(Keys.HIDDEN_TAGS, PersistentDataType.STRING));
        assertFalse(FoodItem.fromItem(stack).getTagTrack("poison").isHidden());
        assertFalse(FoodParser.toString(read, 1).contains("hidden="));

        // Hiding a track the food doesn't have does nothing.
        read.hideTracks(List.of("nowhere"));
        assertTrue(read.getHiddenTrackIds().isEmpty());
    }

    @Test
    void aHiddenTagNeverNamesTheFoodNorStacksWithAShownOne() throws Exception {
        Path naming = directory.resolve("naming.yml");
        Files.writeString(naming, "prefix-tracks:\n  - poison\n");
        new NamingLoader().load(naming.toFile());
        FoodItem hidden = poisoned(sandwich(), true);
        FoodItem shown = poisoned(sandwich(), false);
        assertEquals("", NameComposer.formatPrefixes(hidden));
        assertTrue(NameComposer.formatPrefixes(shown).endsWith("Poisoned "));

        assertFalse(hidden.sameTags(shown));
        assertFalse(InventoryAdder.equalsFood(hidden, shown));
        assertTrue(hidden.sameTags(poisoned(sandwich(), true)));
        assertTrue(InventoryAdder.equalsFood(hidden, poisoned(sandwich(), true)));
    }
}
