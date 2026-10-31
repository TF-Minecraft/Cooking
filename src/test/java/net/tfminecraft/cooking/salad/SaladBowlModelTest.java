package net.tfminecraft.cooking.salad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.manager.BowlIngredientLayout;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

/** Salad ingredients lie in the bowl as their layer's bowl model (salads.yml models, spread-radius). */
class SaladBowlModelTest {
    static final String CONFIG = """
            furniture: [BOARD]
            spread-radius: 0.13
            ingredients:
              vegetable:
                types: [vegetable_chopped, vegetable_cut]
                models:
                  - rotten ia.salad_bowl_{type}_{origin}_rotten
                  - cooked ia.salad_bowl_{type}_{origin}_cooked
                  - ia.salad_bowl_{type}_{origin}
              meat:
                types: [meat_pork]
                models:
                  - ia.salad_bowl_{type}_raw
            """;

    Environment env;
    final List<String> refs = new ArrayList<>();
    final List<ItemStack> given = new ArrayList<>();
    FoodItem built;
    final SaladHandler handler = new SaladHandler();
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new Environment();
        env.define("food_item");
        when(env.furniture.getVariables()).thenReturn(new java.util.HashMap<>());
        // Only these bowl models exist; TLibs hands out dirt for any other ItemsAdder item.
        when(env.api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            refs.add(ref);
            return List.of("ia.salad_bowl_vegetable_chopped_tomato", "ia.salad_bowl_vegetable_chopped_tomato_rotten",
                    "ia.salad_bowl_vegetable_cut_tomato_cooked", "ia.salad_bowl_meat_pork_raw").contains(ref)
                    ? new ItemStack(Material.STICK) : new ItemStack(Material.DIRT);
        });
        env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> {
            built = inv.getArgument(0);
            return env.stack(built, Material.BOWL, 1);
        });
        MockedStatic<InventoryAdder> adder = env.scoped(mockStatic(InventoryAdder.class));
        adder.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> {
            given.add(inv.getArgument(1));
            return null;
        });
        Path file = directory.resolve("salads.yml");
        Files.writeString(file, CONFIG);
        new SaladConfig().load(file.toFile());
    }

    @AfterEach void close() {
        new SaladConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    ItemStack food(String type, String origin, String... steps) {
        FoodItem food = env.food(type, origin);
        food.setCategory("garnish");
        for (String step : steps) {
            TagTrack track = new TagTrack(TrackLoader.getByString(step.equals("rotten") ? "freshness" : "cooked"));
            track.forceSetValue((int) track.getSteps().stream().filter(s -> s.getId().equals(step)).findFirst()
                    .orElseThrow().getRequiredValue());
            food.addOrModifyTrack(track);
        }
        return env.stack(food, Material.CARROT, 3);
    }

    void click(ItemStack held) {
        env.player.getInventory().setItemInMainHand(held);
        handler.onInteract(env.interact("input_1"));
    }

    ItemStack in(String slot) {
        PlacedSlot placed = env.active.get(slot);
        return placed == null ? null : placed.getCurrentItem();
    }

    static double radius(DisplayData data) {
        return Math.hypot(data.getxPos(), data.getzPos());
    }

    @Test
    void anIngredientLiesAsItsBowlModelKeepingItsFoodSpreadAroundTheBowl() {
        click(food("vegetable_chopped", "Tomato"));
        assertEquals(Material.STICK, in("input_1").getType(), "The bowl model, not the held icon");
        assertEquals("Tomato", env.resolve(in("input_1")).getOrigin(), "It keeps its food data");
        assertEquals(1, in("input_1").getAmount());
        assertEquals(2, env.player.getInventory().getItemInMainHand().getAmount());
        verify(env.active.get("input_1")).applyDisplayData(argThat(d -> Math.abs(radius(d) - 0.13) < 0.001));

        // Its state picks the look; {type} keeps a cut tomato apart from a chopped one.
        click(food("vegetable_chopped", "Tomato", "rotten"));
        click(food("vegetable_cut", "Tomato", "cooked"));
        click(food("meat_pork", "Pork", "raw"));
        assertTrue(refs.containsAll(List.of("ia.salad_bowl_vegetable_chopped_tomato_rotten",
                "ia.salad_bowl_vegetable_cut_tomato_cooked", "ia.salad_bowl_meat_pork_raw")));
        for (String slot : List.of("input_2", "input_3", "input_4")) assertEquals(Material.STICK, in(slot).getType());
    }

    @Test
    void anIngredientWithoutABowlModelShowsAsInHandCloserIn() {
        click(food("vegetable_chopped", "Onion"));
        assertEquals(Material.CARROT, in("input_1").getType());
        verify(env.active.get("input_1")).applyDisplayData(
                argThat(d -> Math.abs(radius(d) - BowlIngredientLayout.RADIUS) < 0.001));
    }

    @Test
    void theTossedSaladKeepsItsIngredientModelsShowingAndIsMadeFromThem() {
        click(food("vegetable_chopped", "Tomato"));
        click(food("vegetable_cut", "Tomato", "cooked"));
        click(null);
        assertEquals(List.of("Tomato"), built.getIngredients(), "An origin names it once");
        assertEquals(2, SaladHandler.ingredients(env.furniture).size());
        assertNotNull(in("food_item"));
        assertEquals(Material.STICK, in("input_1").getType(), "The ingredients still show in the bowl");
        assertEquals(Material.STICK, in("input_2").getType());
    }

    @Test
    void anIngredientTakenBackOrDroppedLooksAsInHandAgain() {
        click(food("vegetable_chopped", "Tomato"));
        click(food("vegetable_cut", "Tomato", "cooked"));
        when(env.player.isSneaking()).thenReturn(true);
        click(null);
        assertEquals("vegetable_cut", env.resolve(given.get(0)).getId());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), argThat(f -> "vegetable_cut".equals(f.getId())), isNull()));

        handler.onBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertEquals(1, env.dropped.size());
        assertEquals("vegetable_chopped", env.resolve(env.dropped.get(0)).getId());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(any(), argThat(f -> "vegetable_chopped".equals(f.getId())), isNull()));
    }

    @Test
    void spreadRadiusDefaultsToTheSoupsSpreadAndTypelessFoodsNameNone() {
        assertEquals(0.13f, SaladConfig.spreadRadius(), 0.0001);
        new SaladConfig().load(directory.resolve("missing.yml").toFile());
        assertEquals(BowlIngredientLayout.RADIUS, SaladConfig.spreadRadius(), 0.0001);

        MemoryConfiguration config = new MemoryConfiguration();
        config.set("models", List.of("ia.salad_bowl_{type}_{origin}"));
        refs.clear();
        assertNull(new SandwichLayer("any", config).model(env.food(null, "Melon Slice"), new ItemStack(Material.MELON_SLICE)));
        assertEquals(List.of("ia.salad_bowl_none_melon_slice"), refs);
    }
}
