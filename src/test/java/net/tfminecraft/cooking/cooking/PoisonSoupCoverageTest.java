package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.enums.Tag;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.pot.PotConfig;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;

/**
 * Mushrooms boil in the soup pot (raw-in-cookware: they aren't Raw in hand), and red ones poison the soup ladled from
 * it (pot.yml bonuses): hidden in a soup of 3 stars or more while every red mushroom is boiled, shown otherwise.
 */
class PoisonSoupCoverageTest {
    static final String CONFIG = """
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
                hide-tags: [boiled]
            """;

    CookingReferencesTest.Environment env;
    PotReference pot;
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new CookingReferencesTest.Environment();
        Path file = directory.resolve("pot.yml");
        Files.writeString(file, CONFIG);
        new PotConfig().load(file.toFile());
        pot = new PotReference(env.furniture, Method.POT);
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    /** A mushroom as types.yml has it: grilled in the pan (an output), boiled in the pot, Raw in neither until in. */
    static FoodItem mushroom(String origin, boolean rawInCookware) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "{inherit}");
        config.set("update", false);
        config.set("mashable", true);
        config.set("cooking-options.frying_pan.output", "vegetable(type=grilled_mushroom)");
        config.set("cooking-options.pot.tag", 3);
        config.set("cooking-options.pot.time", 2);
        config.set("cooking-options.pot.burn", 4);
        config.set("cooking-options.pot.raw-in-cookware", rawInCookware);
        FoodItem type = new FoodItem("mushroom", config);
        type.setModel(new FoodModel(new ItemStack(Material.RED_MUSHROOM)));
        FoodItem food = new FoodItem(type);
        food.setCategory("vegetable");
        food.setOrigin(origin);
        return food;
    }

    @Test
    void aMushroomIsOnlyRawOnceItIsInThePotAndThenBoils() {
        FoodItem fresh = mushroom("Red Mushroom", true);
        assertFalse(fresh.hasTagTrack("cooked"), "Not Raw in hand");
        assertFalse(fresh.getCookData().cooksInPlace());
        CookingOutput.startRaw(fresh, Method.SAUCEPAN);
        assertFalse(fresh.hasTagTrack("cooked"), "Nothing happens in cookware it has no option for");

        ItemStack held = env.stack(fresh, Material.RED_MUSHROOM, 1);
        var event = env.addEvent(held);
        pot.slotAdd(event);
        assertFalse(event.isCancelled());
        FoodItem boiling = pot.slots.get("input_1");
        assertTrue(boiling.hasTag(Tag.RAW));
        boiling.getCookData().tick();
        boiling.getCookData().tick();
        assertEquals("boiled", boiling.getTagTrack("cooked").getCurrentStep().getId());

        // Without raw-in-cookware the pot option would make every mushroom Raw from the start, like cut veg.
        assertTrue(mushroom("Red Mushroom", false).hasTag(Tag.RAW));
    }

    /** Red (and brown) mushrooms in the pot, boiled and mashed unless a step is given (raw, burnt). */
    FoodItem soup(String... mushrooms) {
        int slot = 1;
        for (String entry : mushrooms) {
            String[] parts = entry.split(":");
            FoodItem piece = env.food("mushroom", "vegetable", parts[0], Method.POT);
            TagTrack cooked = piece.getTagTrack("cooked");
            cooked.forceSetValue(parts.length > 1 && parts[1].equals("burnt") ? 2 : 3);
            if (parts.length == 1) piece.addOrModifyTrack(TrackLoader.getByString("mashed"));
            pot.slots.put("input_" + slot, piece);
            env.place("input_" + slot, env.stack(piece, Material.RED_MUSHROOM, 1));
            slot++;
        }
        return ladle();
    }

    FoodItem ladle() {
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        pot.interact(env.interaction());
        return env.lastBuilt;
    }

    static String poison(FoodItem soup) {
        TagTrack track = soup.getTagTrack("poison");
        return track == null ? null : track.getCurrentStep().getId() + (track.isHidden() ? " (hidden)" : "");
    }

    @Test
    void boiledRedMushroomsHideTheirPoisonInAGoodSoup() {
        assertEquals("poisoned (hidden)", poison(soup("Red Mushroom", "Red Mushroom", "Brown Mushroom")));
        // Every serving of that pot is poisoned alike.
        assertEquals("poisoned (hidden)", poison(ladle()));
    }

    @Test
    void aBurntRedMushroomShowsIt() {
        assertEquals("deadly", poison(soup("Red Mushroom", "Red Mushroom", "Red Mushroom:burnt")));
    }

    @Test
    void brownMushroomsAloneMakeAPlainMushroomSoup() {
        assertNull(poison(soup("Brown Mushroom", "Brown Mushroom")));
    }

    @Test
    void aSoupUnderThreeStarsShowsIt() {
        env.composer.when(() -> CompositionQualityResolver.compose(any(), any(), any())).thenReturn(
                new CompositionResult(2, 2, Map.of(), List.of(), List.of(), List.of(), null));
        assertEquals("tainted", poison(soup("Red Mushroom", "Brown Mushroom")));
    }
}
