package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.pan.PanConfig;
import net.tfminecraft.cooking.pan.StirIn;
import net.tfminecraft.cooking.pot.MashRecipe;
import net.tfminecraft.cooking.pot.PotConfig;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.sandwich.SandwichItems;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;

/**
 * Rice: it boils into boiled rice in the pot (a pot option's output), boiled rice fries into fried rice in the pan and
 * stirs in the other fried pieces there (frying-pan.yml), and the shipped config makes rice pudding and sushi of it.
 */
class RiceCookingTest {
    static final String BOILED = "grain(type=boiled_rice)";
    static final String FRIED = "grain(type=fried_rice)";

    static FoodParser.Result parsed(String id, String name, double food, double nutrition) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", name); config.set("update", false);
        FoodItem type = new FoodItem(id, config);
        type.setModel(new FoodModel(new ItemStack(Material.CARROT)));
        FoodParser.Result result = new FoodParser.Result();
        result.template = new FoodItem(type);
        result.template.setCategory("grain");
        result.template.setBaseFood(food);
        result.template.setBaseNutrition(nutrition);
        return result;
    }

    static int cooked(FoodItem food) { return food.getTagTrack("cooked").getValue(); }

    static void boiled(FoodItem food) {
        TagTrack cooked = new TagTrack(TrackLoader.getByString("cooked"));
        cooked.forceSetValue(3);
        food.addOrModifyTrack(cooked);
    }

    /** The soup pot boils rice into boiled rice. */
    @Nested
    class Pot {
        final CookingReferencesTest.Environment env = new CookingReferencesTest.Environment();
        final PotReference pot = new PotReference(env.furniture, Method.POT);

        @AfterEach void close() { env.close(); }

        Pot() {
            env.parser.when(() -> FoodParser.parse(BOILED)).thenAnswer(inv -> parsed("boiled_rice", "Boiled Rice", 10, 5));
            env.builder.when(() -> ItemBuilder.displayName(any())).thenAnswer(inv -> ((FoodItem) inv.getArgument(0)).getName());
        }

        /** Rice as conversions.yml makes it: its only option (the pot) names an output, so it carries no Raw tag. */
        ItemStack rice() {
            FoodItem rice = env.foodNamed("rice", "grain", "Rice", "Rice");
            rice.getCookData().getParameters().put(Method.POT, new CookParameter(3, 3, 6, BOILED));
            FoodItem copy = new FoodItem(rice);
            assertFalse(copy.hasTagTrack("cooked"));
            return env.stack(copy, Material.WHEAT_SEEDS, 2);
        }

        void hold(ItemStack item) {
            env.player.getInventory().setItemInMainHand(item);
            FurnitureInteractEvent event = env.interaction();
            pot.interact(event);
        }

        void boil() {
            hold(new ItemStack(Material.WATER_BUCKET));
            env.heated = true;
            for (int i = 0; i < 20; i++) pot.tick();
            assertTrue(pot.isBoiling());
        }

        ItemStack hand() { return env.player.getInventory().getItemInMainHand(); }

        @Test
        void riceBoilsIntoBoiledRiceThenBurnsAsBoiledRiceAndComesOutHot() {
            boil();
            hold(rice());
            FoodItem rice = pot.getSlot("input_1");
            assertEquals("rice", rice.getId());
            pot.tick();
            assertEquals(0, cooked(pot.getSlot("input_1")), "Raw once it's in the water");
            pot.tick();
            pot.tick();
            FoodItem boiled = pot.getSlot("input_1");
            assertEquals("boiled_rice", boiled.getId());
            assertEquals("Rice", boiled.getOrigin());
            assertEquals(3, cooked(boiled));
            env.updater.verify(() -> ItemUpdater.applyItemUpdate(
                    argThat(stack -> stack != null && "Boiled Rice".equals(stack.getItemMeta().getDisplayName())),
                    same(boiled), any()));
            assertNull(boiled.getCookData().getParameters().get(Method.POT).getOutput());
            for (int i = 0; i < 3; i++) pot.tick();
            assertSame(boiled, pot.getSlot("input_1"), "Burnt boiled rice stays boiled rice");
            assertEquals(2, cooked(boiled));

            hold(new ItemStack(Material.AIR));
            FoodItem taken = env.resolve(hand());
            assertEquals("boiled_rice", taken.getId());
            assertTrue(pot.getSlots().isEmpty());
        }

        @Test
        void riceTakenOutBeforeItHasBoiledIsRiceAgain() {
            boil();
            hold(rice());
            pot.tick();
            hold(new ItemStack(Material.AIR));
            FoodItem taken = env.resolve(hand());
            assertEquals("rice", taken.getId());
            assertFalse(taken.hasTagTrack("cooked"), "No Raw tag on rice in hand");
            assertFalse(taken.hasTagTrack("warmth"));
        }

        @Test
        void aRebuiltPotKeepsItsBoiledRiceThoughBoiledRiceOnlyFries() {
            FoodItem boiled = env.foodNamed("boiled_rice", "grain", "Rice", "Boiled Rice");
            boiled.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, 15, 30, FRIED));
            boiled(boiled);
            assertTrue(PotReference.keepsOnRebuild(boiled));
            assertFalse(PotReference.keepsOnRebuild(env.foodNamed("butter", "dairy", "Cow", "Butter")));
        }
    }

    /** Boiled rice fries into fried rice, which stirs in the other fried pieces in the pan. */
    @Nested
    class Pan {
        final CraftingStationCoverageTest.Environment env = new CraftingStationCoverageTest.Environment();
        final FryingReference pan = new FryingReference(env.furniture, Method.FRYING_PAN);
        @TempDir Path directory;

        @AfterEach void close() {
            new PanConfig().load(new File("missing-frying-pan.yml"));
            env.close();
        }

        Pan() {
            new PanConfig().load(new File("src/main/resources/frying-pan.yml"));
            env.parser.when(() -> FoodParser.parse(FRIED)).thenAnswer(inv -> parsed("fried_rice", "{fillers}Fried Rice", 12, 6));
            env.builder.when(() -> ItemBuilder.displayName(any())).thenAnswer(inv -> {
                FoodItem food = inv.getArgument(0);
                return String.join(" and ", food.getIngredients()) + (food.getIngredients().isEmpty() ? "" : " ")
                        + food.getName().replace("{fillers}", "");
            });
        }

        FoodItem boiledRice(int time, int burn) {
            FoodItem rice = env.food("boiled_rice", "Rice");
            rice.setCategory("grain");
            rice.setBaseFood(10);
            rice.setBaseNutrition(5);
            rice.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, time, burn, FRIED));
            boiled(rice);
            return rice;
        }

        /** A piece that fries as itself (an egg, a steak): Raw, cooked at tick 2, burnt at tick 20. */
        FoodItem piece(String id, String category, String origin, double food, double nutrition) {
            FoodItem piece = env.food(id, origin, Method.FRYING_PAN);
            piece.setCategory(category);
            piece.setBaseFood(food);
            piece.setBaseNutrition(nutrition);
            return piece;
        }

        void add(FoodItem food, String slot) {
            FurnitureSlotItemAddEvent event = env.addEvent(slot, env.stack(food, Material.WHEAT, 1));
            pan.slotAdd(event);
            assertFalse(event.isCancelled(), food.getId());
            env.place(slot, event.getItem());
        }

        FurnitureSlotItemTakeEvent take(String slot) {
            FurnitureSlotItemTakeEvent event = env.takeEvent(slot, env.active.get(slot).getCurrentItem());
            pan.slotRemove(event);
            return event;
        }

        void tick(int times) { for (int i = 0; i < times; i++) pan.tick(); }

        @Test
        void boiledRiceFriesIntoFriedRiceThatStirsInAFriedEggAndIsNamedForIt() {
            pan.getSecondaries().put("butter", -1);
            FoodItem egg = piece("egg", "egg", "Egg", 10, 6);
            add(egg, "input_2");
            add(boiledRice(5, 30), "input_1");
            tick(5);
            FoodItem fried = pan.getSlot("input_1");
            assertEquals("fried_rice", fried.getId());
            assertEquals("Rice", fried.getOrigin());
            assertEquals(1, cooked(fried));
            assertEquals(1, cooked(egg));

            FurnitureSlotItemTakeEvent taken = take("input_1");
            assertFalse(taken.isCancelled());
            FoodItem dish = env.resolve(taken.getItem());
            assertSame(fried, dish);
            assertEquals(List.of("Egg"), dish.getIngredients());
            env.updater.verify(() -> ItemUpdater.applyItemUpdate(
                    argThat(stack -> stack != null && "Egg Fried Rice".equals(stack.getItemMeta().getDisplayName())),
                    same(dish), isNull()));
            // Its food with its own Cooked tag is the rice's and the egg's, each with theirs (12 x 1.55 + 10 x 1.55).
            assertEquals(22, dish.getBaseFood(), 1e-9);
            assertEquals(6, dish.getBaseNutrition(), 1e-9);
            assertNull(pan.getSlot("input_2"), "The egg went into the rice");
            assertFalse(env.active.containsKey("input_2"));
            env.composer.verify(() -> CompositionQualityResolver.compose(any(),
                    argThat(inputs -> inputs.size() == 2), eq(CompositionContext.FRYING_PAN)));
            assertNull(dish.getTagTrack("poison"));
            assertTrue(dish.hasTagTrack("warmth"), "Served hot");
        }

        @Test
        void piecesStillRawStayInThePanAndPlainFriedRiceNeedsNoButter() {
            FoodItem steak = env.food("steak", "Beef");
            steak.setCategory("meat");
            steak.addOrModifyTrack(TrackLoader.getByString("cooked"));
            steak.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, 50, 60));
            add(new FoodItem(steak), "input_2");
            add(boiledRice(5, 30), "input_1");
            tick(5);
            FurnitureSlotItemTakeEvent taken = take("input_1");
            FoodItem dish = env.resolve(taken.getItem());
            assertTrue(dish.getIngredients().isEmpty());
            assertEquals(12, dish.getBaseFood(), 1e-9);
            assertNotNull(pan.getSlot("input_2"), "The raw steak stays");
            env.composer.verifyNoInteractions();
        }

        @Test
        void burntFriedRiceStirsInNothingAndBurntBoiledRiceStaysOutOfThePan() {
            FoodItem egg = piece("egg", "egg", "Egg", 10, 6);
            egg.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, 2, 200));
            add(egg, "input_2");
            add(boiledRice(5, 7), "input_1");
            tick(7);
            assertEquals(2, cooked(pan.getSlot("input_1")));
            FoodItem dish = env.resolve(take("input_1").getItem());
            assertTrue(dish.getIngredients().isEmpty());
            assertNotNull(pan.getSlot("input_2"));

            FoodItem burnt = boiledRice(2, 4);
            burnt.getTagTrack("cooked").forceSetValue(2);
            FurnitureSlotItemAddEvent refused = env.addEvent("input_3", env.stack(burnt, Material.WHEAT, 1));
            pan.slotAdd(refused);
            assertTrue(refused.isCancelled());
        }

        @Test
        void boiledRiceTakenOffBeforeItFriesComesBackBoiled() {
            pan.getSecondaries().put("butter", -1);
            FoodItem rice = boiledRice(10, 20);
            add(rice, "input_1");
            tick(6);
            FurnitureSlotItemTakeEvent taken = take("input_1");
            assertFalse(taken.isCancelled());
            assertSame(rice, env.resolve(taken.getItem()));
            assertEquals(3, cooked(rice), "Still boiled");
            assertFalse(rice.hasTagTrack("warmth"));
            assertTrue(pan.getSecondaries().containsKey("butter"), "The butter is still in the pan");
        }

        @Test
        void aRedMushroomStirredInPoisonsItAndAtMostMaxPiecesGoIn() throws Exception {
            Path file = directory.resolve("frying-pan.yml");
            Files.writeString(file, Files.readString(Path.of("src/main/resources/frying-pan.yml")).replace("max: 3", "max: 1"));
            new PanConfig().load(file.toFile());
            FoodItem mushroom = piece("grilled_mushroom", "vegetable", "Red Mushroom", 6, 4.5);
            FoodItem carrot = piece("vegetable_cut", "vegetable", "Carrot", 7, 4.5);
            add(mushroom, "input_2");
            add(carrot, "input_3");
            add(boiledRice(5, 30), "input_1");
            tick(5);
            FoodItem dish = env.resolve(take("input_1").getItem());
            assertEquals(List.of("Red Mushroom"), dish.getIngredients());
            assertEquals("tainted", dish.getTagTrack("poison").getCurrentStep().getId());
            assertNotNull(pan.getSlot("input_3"), "Max 1: the carrot stays");
        }

        @Test
        void aRebuiltPanKeepsFryingItsBoiledRice() {
            FoodItem rice = boiledRice(3, 30);
            env.place("input_1", env.stack(rice, Material.WHEAT, 1));
            FoodItem fried = new FoodItem(rice);
            fried.getTagTrack("cooked").forceSetValue(1);
            fried.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, 3, 30));
            env.place("input_2", env.stack(fried, Material.WHEAT, 1));
            pan.rebuildFromFurniture();
            assertSame(rice, pan.getSlot("input_1"));
            assertNull(pan.getSlot("input_2"), "Done pieces aren't cooking any more");
        }

        @Test
        void stirInsMatchTheirDishAndPiecesByPath() {
            StirIn stirIn = PanConfig.stirInFor(parsed("fried_rice", "Fried Rice", 12, 6).template);
            assertEquals("fried_rice", stirIn.getId());
            assertEquals(3, stirIn.getMax());
            FoodItem egg = piece("egg", "egg", "Egg", 1, 1);
            assertTrue(stirIn.takes(egg));
            assertFalse(stirIn.takes(piece("popcorn", "snack", "Corn", 1, 1)));
            assertNull(PanConfig.stirInFor(egg));
            assertEquals(1, PanConfig.bonuses().size());
            MemoryConfiguration bare = new MemoryConfiguration();
            StirIn defaults = new StirIn("bare", bare);
            assertEquals(3, defaults.getMax());
            assertFalse(defaults.isDish(egg));
        }

        @Test
        void aMissingFileOrStirInsSectionLeavesNoneAndSkipsStrayEntries() throws Exception {
            new PanConfig().load(directory.resolve("missing.yml").toFile());
            assertNull(PanConfig.stirInFor(parsed("fried_rice", "Fried Rice", 12, 6).template));
            assertTrue(PanConfig.bonuses().isEmpty());
            Path file = directory.resolve("frying-pan.yml");
            Files.writeString(file, "stir-ins:\n  stray: 3\n");
            new PanConfig().load(file.toFile());
            assertNull(PanConfig.stirInFor(parsed("fried_rice", "Fried Rice", 12, 6).template));
        }
    }

    /** The shipped types, pot and models give rice its whole journey. */
    @Nested
    class ShippedConfig {
        final CookingReferencesTest.Environment env = new CookingReferencesTest.Environment();
        List<FoodItem> previous = FoodLoader.oList;

        ShippedConfig() {
            // Real path matching: the environment's parser mock otherwise matches paths by category only.
            env.parser.when(() -> FoodParser.extractFields(anyString())).thenCallRealMethod();
        }

        @AfterEach void close() {
            FoodLoader.oList = previous;
            new PotConfig().load(new File("missing-pot.yml"));
            env.close();
        }

        FoodItem type(String id) {
            return new FoodItem(FoodLoader.getByString(id));
        }

        @Test
        void riceBoilsInThePotAndBoiledRiceFriesInThePanAndNeitherIsRawInHand() throws Exception {
            FoodLoader.oList = new ArrayList<>();
            new FoodLoader().load(new File("src/main/resources/types.yml"));
            FoodItem rice = type("rice");
            assertEquals(BOILED, rice.getCookData().getParameters().get(Method.POT).getOutput());
            assertFalse(rice.hasTagTrack("cooked"));
            FoodItem boiled = type("boiled_rice");
            assertEquals(FRIED, boiled.getCookData().getParameters().get(Method.FRYING_PAN).getOutput());
            assertFalse(boiled.hasTagTrack("cooked"));
            for (String id : List.of("fried_rice", "rice_pudding", "sushi")) {
                assertTrue(type(id).getCookData().getParameters().isEmpty(), id);
            }
            assertTrue(Files.readString(Path.of("src/main/resources/conversions.yml"))
                    .contains("ia.tfmc_cooking:rice grain(type=rice;origin=Rice;tags=freshness.0)"));
            assertEquals(1.55, SandwichItems.multiplier(cookedRice(boiled), true), 1e-9);
        }

        FoodItem cookedRice(FoodItem boiled) {
            boiled(boiled);
            boiled.getTagTrack("cooked").forceSetValue(1);
            return boiled;
        }

        @Test
        void riceObjectsHaveModelsAndItemsAdderItems() throws Exception {
            String models = Files.readString(Path.of("src/main/resources/models.yml"));
            StringBuilder items = new StringBuilder();
            for (String file : List.of("intermediates.yml", "categories.yml")) {
                items.append(Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/" + file)));
            }
            for (String model : List.of("rice", "boiled_rice", "fried_rice", "rice_pudding", "sushi")) {
                assertTrue(models.contains("\n" + model + ":\n"), model);
            }
            for (String id : List.of("rice_rotten", "rice_grains", "boiled_rice", "boiled_rice_burnt", "boiled_rice_rotten",
                    "boiled_rice_heap", "boiled_rice_heap_burnt", "boiled_rice_heap_rotten", "fried_rice", "fried_rice_burnt",
                    "fried_rice_rotten", "fried_rice_heap", "fried_rice_heap_burnt", "rice_pudding", "rice_pudding_rotten",
                    "sushi", "sushi_rotten")) {
                assertTrue(models.contains("ia.tfmc_cooking:" + id + "\n"), id);
                assertTrue(items.toString().contains("\n  " + id + ":\n"), id);
                assertTrue(items.toString().contains("- tfmc_cooking:" + id + "\n"), id);
                assertTrue(Files.exists(Path.of("ItemsAdder/tfmc_cooking/resourcepack/assets/tfmc_cooking/textures/item/intermediate/"
                        + id + ".png")), id);
            }
        }

        @Test
        void anEmptyPotStirsTwoBoiledRiceAMilkAndASweetenerIntoRicePudding() {
            new PotConfig().load(new File("src/main/resources/pot.yml"));
            MashRecipe pudding = PotConfig.mash("rice_pudding");
            assertEquals(2, pudding.getServings());
            assertEquals("mash(type=rice_pudding;origin=Rice;tags=freshness.0)", pudding.getOutput());
            FoodItem rice = env.foodNamed("boiled_rice", "grain", "Rice", "Boiled Rice");
            boiled(rice);
            FoodItem burnt = env.foodNamed("boiled_rice", "grain", "Rice", "Boiled Rice");
            boiled(burnt);
            burnt.getTagTrack("cooked").forceSetValue(2);
            FoodItem milk = env.foodNamed("cup_of_milk", "ingredient", "Cow", "Cup of Milk");
            FoodItem sugar = env.foodNamed("sweetener_1", "sweetener", "Sugar", "Sugar");
            FoodItem cinnamon = env.foodNamed("spice_1", "spice", "Cinnamon", "Cinnamon");
            assertTrue(pudding.takesIngredient(rice, List.of()));
            assertFalse(pudding.takesIngredient(burnt, List.of()), "Burnt rice makes no pudding");
            assertTrue(pudding.takesIngredient(milk, List.of(rice, rice)));
            assertFalse(pudding.complete(List.of(rice, rice, milk)));
            assertTrue(pudding.complete(List.of(rice, milk, rice, sugar)));
            FoodItem honey = env.foodNamed("honey", "sweetener", "Honey", "Honey");
            assertTrue(pudding.complete(List.of(rice, milk, rice, honey)), "honey sweetens it as well as sugar");
            assertEquals("aromatic", pudding.extraFor(cinnamon).track());
            assertTrue(PotConfig.isMashIngredient(sugar), "Sugar over a pot of water gets the mash hint");
            // Milk alone starts a pudding, not mashed potatoes, whose milk is only an extra.
            assertFalse(PotConfig.mash("mashed_potatoes").holds(List.of(milk), List.of()));
            assertTrue(pudding.holds(List.of(milk), List.of()));
        }
    }
}
