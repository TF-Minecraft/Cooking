package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.BucketItems;
import net.tfminecraft.cooking.cup.CupItems;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.Encoder;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

class CookingReferencesTest {
    Environment env;

    @BeforeEach void setUp() { env = new Environment(); }
    @AfterEach void tearDown() { env.close(); }

    @Test
    void referenceStoresItsStationAndExpiresOnlyTimedSecondaries() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        assertSame(env.furniture, ref.getFurniture());
        assertSame(ref.slots, ref.getSlots());
        assertEquals(Method.POT, ref.getMethod());
        assertTrue(ref.isEmpty());
        PlacedSlot butter = env.place("butter", new ItemStack(Material.GOLD_NUGGET));
        ref.secondaries.put("butter", 2);
        ref.secondaries.put("liquid", -1);
        ref.secondaries.put("missing", 1);
        assertFalse(ref.isEmpty());
        ref.tick();
        assertEquals(1, ref.getSecondary("butter"));
        assertEquals(-1, ref.getSecondary("liquid"));
        assertFalse(ref.secondaries.containsKey("missing"));
        ref.tick();
        verify(butter).clearModel();
        assertFalse(ref.secondaries.containsKey("butter"));
        ref.remove();
        assertTrue(ref.getSecondaries().isEmpty());
        ref.interact(env.interaction());
    }

    @Test
    void addingAValidIngredientConsumesOneAndUpdatesTheFurnitureModel() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        ItemStack stack = env.stack(carrot, Material.CARROT, 4);
        assertFalse(ref.add("decoration", stack));
        assertFalse(ref.add("input_1", new ItemStack(Material.STONE)));
        assertTrue(ref.add("input_1", stack));
        assertEquals(3, stack.getAmount());
        assertSame(carrot, ref.getSlot("input_1"));
        assertTrue(ref.hasSlot("vegetable"));
        assertFalse(ref.hasSlot("meat"));
        assertEquals(1, env.active.get("input_1").getCurrentItem().getAmount());
        assertEquals(List.of("aa5500"), ref.getColours());
        assertFalse(ref.add("input_1", stack));
    }

    @Test
    void rejectedFurnitureSlotDoesNotCreateAnInvisibleIngredient() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        ItemStack stack = env.stack(env.food("carrot", "vegetable", "Carrot", Method.POT), Material.CARROT, 2);
        assertFalse(ref.add("input_missing", stack));
        assertTrue(ref.isEmpty(), "A rejected add must not leave a ghost ingredient");
        assertEquals(2, stack.getAmount());
        when(env.furniture.getType()).thenReturn(null);
        assertFalse(ref.add("input_1", stack));
        assertTrue(ref.isEmpty());
    }

    @Test
    void failedIngredientRenderingDoesNotConsumeFoodOrCreateAnEmptyDisplay() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        ItemStack stack = env.stack(env.food("carrot", "vegetable", "Carrot", Method.POT), Material.CARROT, 2);
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        assertFalse(ref.add("input_1", stack));
        assertTrue(ref.isEmpty());
        assertFalse(env.active.containsKey("input_1"));
        assertEquals(2, stack.getAmount());
    }

    @Test
    void coloursAndIngredientNamesRemainUniqueAndPutMinorIngredientsLast() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        ref.slots = new LinkedHashMap<>();
        ref.slots.put("null", null);
        ref.slots.put("no-origin", env.food("unknown", "vegetable", null));
        ref.slots.put("none", env.food("none", "vegetable", "none"));
        ref.slots.put("mixed", env.food("mixed", "vegetable", "Mixed"));
        ref.slots.put("flour", env.food("flour", "flour", "Wheat"));
        ref.slots.put("more-flour", env.food("flour2", "flour", "Wheat"));
        ref.slots.put("salt", env.food("salt", "salt", "Salt"));
        ref.slots.put("carrot", env.food("carrot", "vegetable", " Carrot "));
        ref.slots.put("carrot2", env.food("carrot2", "vegetable", "Carrot"));
        assertEquals(List.of("Carrot", "Wheat"), ref.getIngredients());
        assertEquals("Carrot and Wheat Soup", ref.getName("Soup"));
        ref.addColour("aa5500");
        ref.addColour("aa5500");
        ref.addColour("ffffff");
        ref.removeColour("ffffff");
        assertEquals(List.of("aa5500"), ref.getColours());
        assertNotNull(ref.getLiquidItemPath());
        FoodItem template = env.foodNamed("soup", "soup", "Mixed", "{fillers}{ingredients}{prefixes}{colour}");
        assertTrue(ref.applyNameTemplate(template, "aa5500", "Soup").contains("Carrot and Wheat"));
        ref.slots.clear();
        assertTrue(ref.applyNameTemplate(template, "ffffff", "Soup").contains("Mixed Soup"));
    }

    @Test
    void rebuildingKeepsOnlySupportedRawFoodAndRestoresFryingButter() {
        CookingReference ref = new CookingReference(env.furniture, Method.FRYING_PAN);
        FoodItem raw = env.food("raw", "meat", "Beef", Method.FRYING_PAN);
        FoodItem running = env.food("running", "meat", "Chicken", Method.FRYING_PAN);
        running.getCookData().restore(Method.FRYING_PAN, 4);
        FoodItem cooked = env.food("cooked", "meat", "Pork", Method.FRYING_PAN);
        cooked.getTagTrack("cooked").forceSetValue(1);
        env.place("input_1", env.stack(raw, Material.BEEF, 1));
        env.place("input_2", env.stack(running, Material.CHICKEN, 1));
        env.place("input_3", env.stack(cooked, Material.PORKCHOP, 1));
        env.place("input_4", env.stack(env.food("wrong", "meat", "Fish", Method.POT), Material.COD, 1));
        env.place("input_5", env.stack(env.food("inert", "flour", "Wheat"), Material.WHEAT, 1));
        env.place("butter", new ItemStack(Material.GOLD_NUGGET));
        env.place("liquid", null);
        env.define("air"); env.place("air", new ItemStack(Material.AIR));
        env.define("stone"); env.place("stone", new ItemStack(Material.STONE));
        ref.danger = 8; ref.colours.add("old");
        ref.rebuildFromFurniture();
        assertEquals(2, ref.slots.size());
        assertSame(raw, ref.getSlot("input_1"));
        assertEquals(Method.FRYING_PAN, raw.getCookData().getCurrentMethod());
        assertEquals(4, running.getCookData().getCurrentTime());
        assertEquals(30, ref.getSecondary("butter"));
        assertEquals(0, ref.danger);
        assertTrue(ref.colours.isEmpty());
        new CookingReference(null, Method.POT).rebuildFromFurniture();
        when(env.furniture.getType()).thenReturn(null);
        ref.rebuildFromFurniture();
        assertTrue(ref.isEmpty());
    }

    @Test
    void slotAdditionAllowsWhitelistedToolsButRejectsUncookableOrWrongStateFood() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        var tool = env.addEvent(new ItemStack(Material.STICK));
        ref.slotAdd(tool);
        assertFalse(tool.isCancelled());
        FoodItem inert = env.food("salt", "salt", "Salt");
        var uncookable = env.addEvent(env.stack(inert, Material.SUGAR, 1));
        ref.slotAdd(uncookable); assertTrue(uncookable.isCancelled());
        var wrong = env.addEvent(env.stack(env.food("steak", "meat", "Beef", Method.FRYING_PAN), Material.BEEF, 1));
        ref.slotAdd(wrong); assertTrue(wrong.isCancelled());
        FoodItem cooked = env.food("cooked", "meat", "Beef", Method.POT);
        cooked.getTagTrack("cooked").forceSetValue(1);
        var done = env.addEvent(env.stack(cooked, Material.BEEF, 1));
        ref.slotAdd(done); assertTrue(done.isCancelled());
        FoodItem raw = env.food("raw", "meat", "Beef", Method.POT);
        var accepted = env.addEvent(env.stack(raw, Material.BEEF, 1));
        ref.slotAdd(accepted);
        assertFalse(accepted.isCancelled());
        assertSame(raw, ref.getSlot("input_1"));
        assertEquals(Method.POT, raw.getCookData().getCurrentMethod());
        assertNotNull(accepted.getDisplayData());
    }

    @Test
    void modelUpdatesHandleMissingSlotsMissingOutputAndUnrecognisedModels() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        FoodItem food = env.food("carrot", "vegetable", "Carrot", Method.POT);
        ref.applySlotUpdate("input_1", food);
        PlacedSlot placed = env.place("input_1", env.stack(food, Material.CARROT, 1));
        ref.applySlotUpdate("input_1", food);
        verify(placed).applyDisplayData(any());
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        ref.applySlotUpdate("input_1", food);
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(new ItemStack(Material.STONE));
        ref.applySlotUpdate("input_1", food);
        assertEquals(Material.STONE, placed.getCurrentItem().getType());
        verify(placed, times(1)).applyDisplayData(any());
    }

    @Test
    void removingAHeatedSlotReturnsHotFoodAndClearingResetsState() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        ref.slotRemove(env.takeEvent(new ItemStack(Material.STONE)));
        FoodItem food = env.food("carrot", "vegetable", "Carrot", Method.POT);
        food.getCookData().restore(Method.POT, 6);
        ItemStack stack = env.stack(food, Material.CARROT, 1);
        ref.slots.put("input_1", food);
        var take = env.takeEvent(stack);
        ref.slotRemove(take);
        assertFalse(take.isCancelled());
        assertTrue(food.hasTagTrack("warmth"));
        assertNotNull(take.getItem());
        assertFalse(ref.slots.containsKey("input_1"));
        PlacedSlot placed = env.place("input_1", stack);
        PlacedSlot liquid = env.place("liquid", new ItemStack(Material.GLASS));
        ref.slots.put("input_1", food); ref.slots.put("missing", food);
        ref.secondaries.put("liquid", -1); ref.colours.add("fff"); ref.danger = 3;
        ref.clear();
        assertTrue(ref.isEmpty()); assertTrue(ref.colours.isEmpty()); assertEquals(0, ref.danger);
        verify(placed).clearModel(); verify(liquid).clearModel();
    }

    @Test
    void slotRemovalValidationHandlesUnsupportedFoodAndEarlyOrUnavailableUpdates() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        FoodItem inert = env.food("salt", "salt", "Salt");
        ref.slots.put("input_1", inert);
        var rejected = env.takeEvent(env.stack(inert, Material.SUGAR, 1));
        ref.slotRemove(rejected); assertTrue(rejected.isCancelled());
        assertSame(inert, ref.getSlot("input_1"));
        FoodItem pan = env.food("pan", "meat", "Beef", Method.FRYING_PAN);
        ref.slots.put("input_1", pan);
        var wrong = env.takeEvent(env.stack(pan, Material.BEEF, 1));
        ref.slotRemove(wrong); assertTrue(wrong.isCancelled());
        assertSame(pan, ref.getSlot("input_1"));
        FoodItem food = env.food("carrot", "vegetable", "Carrot", Method.POT);
        ItemStack stack = env.stack(food, Material.CARROT, 1);
        ref.slots.put("input_1", food);
        ref.slotRemove(env.takeEvent(stack));
        assertFalse(food.hasTagTrack("warmth"));
        food.getCookData().restore(Method.POT, 6);
        TrackLoader.oList.removeIf(track -> track.getId().equals("warmth"));
        ref.slots.put("input_1", food);
        var missingWarmth = env.takeEvent(stack);
        ref.slotRemove(missingWarmth);
        assertTrue(missingWarmth.isCancelled());
        assertSame(food, ref.getSlot("input_1"));
        assertFalse(food.hasTagTrack("warmth"));
    }

    @Test
    void failedCookedItemRenderingCancelsTheTakeAndPreservesTheStationIngredient() {
        CookingReference ref = new CookingReference(env.furniture, Method.POT);
        FoodItem food = env.food("carrot", "vegetable", "Carrot", Method.POT);
        food.getCookData().restore(Method.POT, 6);
        ref.slots.put("input_1", food);
        var event = env.takeEvent(env.stack(food, Material.CARROT, 1));
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        ref.slotRemove(event);
        assertTrue(event.isCancelled());
        assertSame(food, ref.getSlot("input_1"));
    }

    @Test
    void sauceAcceptsOnlyDistinctApprovedAdditionsAfterLiquid() {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        ItemStack salt = env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 1);
        assertFalse(ref.canAdd(env.player, salt));
        ref.secondaries.put("liquid", -1);
        assertFalse(ref.canAdd(env.player, null));
        assertFalse(ref.canAdd(env.player, new ItemStack(Material.AIR)));
        assertFalse(ref.canAdd(env.player, new ItemStack(Material.STONE)));
        assertTrue(ref.canAdd(env.player, salt));
        ref.slots.put("salt", env.resolve(salt));
        assertFalse(ref.canAdd(env.player, salt));
        for (String category : List.of("sweetener", "garnish", "alcohol")) {
            assertTrue(ref.canAdd(env.player, env.stack(env.food(category, category, category), Material.CARROT, 1)));
        }
        assertFalse(ref.canAdd(env.player, env.stack(env.food("beef", "meat", "Beef"), Material.BEEF, 1)));
    }

    @Test
    void sauceTicksOnlyWithHeatAndUpdatesAtCookingTransitions() {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        FoodItem food = env.food("sauce", "sauce", "Tomato", Method.SAUCEPAN);
        food.getCookData().start(Method.SAUCEPAN);
        ref.slots.put("input_1", food);
        ref.slots.put("inert", env.food("salt", "salt", "Salt"));
        PlacedSlot slot = env.place("input_1", env.stack(food, Material.CARROT, 1));
        ref.tick(); assertEquals(0, food.getCookData().getCurrentTime());
        env.heated = true;
        ref.tick(); ref.tick();
        assertEquals(2, food.getCookData().getCurrentTime());
        assertEquals(1, food.getTagTrack("cooked").getValue());
        verify(slot).forceModel(any());
        ref.clear(); ref.tick();
        SauceReference other = new SauceReference(env.furniture, Method.NONE);
        other.secondaries.put("liquid", -1); other.tick();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HONEY_BOTTLE", "WHITE_DYE", "MILK_BUCKET"})
    void saucePoursSupportedContainersAndReturnsTheirEmptyVessel(String material) {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        FoodItem milk = env.food("milk", "milk", "Milk");
        env.player.getInventory().setItemInMainHand(env.stack(milk, Material.valueOf(material), 1));
        ref.interact(env.interaction());
        assertEquals(-1, ref.getSecondary("liquid"));
        assertEquals(material.equals("MILK_BUCKET") ? Material.BUCKET : Material.GLASS_BOTTLE,
                env.player.getInventory().getItemInMainHand().getType());
        assertTrue(env.active.containsKey("liquid"));
        assertFalse(ref.getColours().isEmpty());
        ref.clear(); assertTrue(ref.isEmpty());
    }

    @Test
    void sauceRejectsWrongWaterAndUnconfiguredFurnitureWithoutConsumingAnything() {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        ref.interact(env.interaction());
        for (Material material : List.of(Material.WATER_BUCKET, Material.POTION, Material.LAVA_BUCKET)) {
            env.player.getInventory().setItemInMainHand(new ItemStack(material));
            ref.interact(env.interaction());
            assertTrue(ref.isEmpty());
            assertEquals(material, env.player.getInventory().getItemInMainHand().getType());
        }
        env.definitions.remove("liquid");
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.HONEY_BOTTLE));
        ref.interact(env.interaction());
        assertTrue(ref.isEmpty());
        ref.updateModel();
        when(env.furniture.getType()).thenReturn(null);
        ref.interact(env.interaction()); ref.updateModel();
        assertTrue(ref.isEmpty());
    }

    @Test
    void sauceUsesItsConfiguredLiquidModelAndMilkFallback() {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        env.cache.when(() -> ItemCache.getLiquidModel(any(ItemStack.class))).thenReturn("v.white_stained_glass");
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.WHITE_DYE));
        ref.interact(env.interaction());
        assertEquals(-1, ref.getSecondary("liquid"));
        ref.clear();
        env.cache.when(() -> ItemCache.getLiquidModel(any(ItemStack.class))).thenReturn(" ");
        env.cache.when(() -> ItemCache.getLiquidModel("v.milk_bucket")).thenReturn("v.milk_glass");
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.MILK_BUCKET));
        ref.interact(env.interaction());
        assertEquals(-1, ref.getSecondary("liquid"));
        ref.clear();
        ref.slots.put("input_1", env.food("salt", "salt", "Salt"));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        ref.interact(env.interaction());
        assertEquals(1, ref.slots.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HONEY_BOTTLE", "MILK_BUCKET"})
    void stackedSauceContainersReturnExtrasOrDropThemWhenTheInventoryIsFull(String material) {
        try (MockedStatic<InventoryAdder> adder = mockStatic(InventoryAdder.class)) {
            ItemStack leftover = new ItemStack(Material.BUCKET);
            adder.when(() -> InventoryAdder.addItem(eq(env.player), any())).thenReturn(leftover);
            SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
            env.player.getInventory().setItemInMainHand(new ItemStack(Material.valueOf(material), 2));
            ref.interact(env.interaction());
            assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
            verify(env.world).dropItemNaturally(any(), same(leftover));
        }
    }

    @Test
    void sauceScoopCombinesQualityAndFlourMilkTagsThenClearsTheStation() {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        ref.scoop(env.player, new ItemStack(Material.PAPER));
        env.player.getInventory().setItemInMainHand(env.stack(env.food("milk", "milk", "Milk"), Material.WHITE_DYE, 1));
        ref.interact(env.interaction());
        ref.slots.put("input_1", env.food("flour", "flour", "Wheat"));
        ref.slots.put("input_2", env.food("sugar", "sweetener", "Sugar"));
        ref.colours.add("ffffff");
        ref.scoop(env.player, new ItemStack(Material.PAPER));
        FoodItem output = env.lastBuilt;
        assertEquals(1, output.getTagTrack("sauce_thickness").getValue());
        assertTrue(output.hasTagTrack("sauce_creamy"));
        assertEquals(1, output.getTagTrack("sauce_cooked").getValue());
        assertTrue(output.hasTagTrack("sweet"));
        assertEquals(72, env.lastQuality);
        assertTrue(ref.isEmpty());
        assertNotNull(env.player.getInventory().getItemInMainHand().getItemMeta()
                .getPersistentDataContainer().get(Keys.SAUCE_COLOUR, PersistentDataType.STRING));
        ref.secondaries.put("liquid", -1);
        ref.colours.add("aa5500");
        ref.scoop(env.player, new ItemStack(Material.PAPER));
        assertEquals(0, env.lastBuilt.getTagTrack("sauce_thickness").getValue());
        assertFalse(env.lastBuilt.hasTagTrack("sauce_creamy"));
    }

    @Test
    void sauceInteractionAddsAnIngredientAndALadleScoopsTheResult() {
        SauceReference ref = new SauceReference(env.furniture, Method.SAUCEPAN);
        ref.secondaries.put("liquid", -1);
        env.player.getInventory().setItemInMainHand(env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 2));
        ref.interact(env.interaction());
        assertEquals(1, ref.slots.size());
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        ref.interact(env.interaction());
        assertTrue(ref.isEmpty());
        assertTrue(env.lastBuilt.hasTagTrack("seasoning"));
    }

    @Test
    void fryingRejectsColdOrUnusableButterAndConsumesOneWhenWarm() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        ref.interact(env.interaction());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        ref.interact(env.interaction());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_NUGGET));
        ref.interact(env.interaction()); assertTrue(ref.isEmpty());
        env.heated = true;
        ref.interact(env.interaction()); assertTrue(ref.isEmpty());
        FoodItem butter = env.food("butter", "butter", "Butter");
        env.player.getInventory().setItemInMainHand(env.stack(butter, Material.GOLD_NUGGET, 3));
        var event = env.interaction();
        ref.interact(event);
        assertTrue(event.isCancelled()); assertEquals(30, ref.getSecondary("butter"));
        assertEquals(2, env.player.getInventory().getItemInMainHand().getAmount());
        ref.interact(env.interaction());
        assertEquals(2, env.player.getInventory().getItemInMainHand().getAmount());
        env.heated = false; ref.tick(); assertTrue(ref.isEmpty());
    }

    @Test
    void fryingConvertsVanillaButterBeforeConsumingTheActualHandStack() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        env.heated = true;
        ItemStack converted = env.stack(env.food("butter", "butter", "Butter"), Material.GOLD_NUGGET, 4);
        env.converter.when(() -> IngredientConverter.convertIfNeeded(eq(env.player), any())).thenReturn(converted);
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_NUGGET, 4));
        ref.interact(env.interaction());
        assertEquals(3, env.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(30, ref.getSecondary("butter"));
    }

    @Test
    void fryingRejectsButterWhenTheFurnitureHasNoButterSlot() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        env.heated = true;
        env.player.getInventory().setItemInMainHand(env.stack(env.food("butter", "butter", "Butter"), Material.GOLD_NUGGET, 1));
        env.definitions.remove("butter");
        ref.interact(env.interaction()); assertTrue(ref.isEmpty());
        when(env.furniture.getType()).thenReturn(null);
        ref.interact(env.interaction()); assertTrue(ref.isEmpty());
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void aButterDisplayDisappearingDuringRebuildDoesNotCreateAQualityExtra() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        ItemStack butter = env.stack(env.food("butter", "butter", "Butter"), Material.GOLD_NUGGET, 1);
        PlacedSlot slot = env.place("butter", butter);
        when(slot.getCurrentItem()).thenReturn(butter, null);
        assertDoesNotThrow(ref::rebuildFromFurniture);
        assertTrue(ref.slots.isEmpty());
    }

    @Test
    void takingRawFoodOutOfThePanDoesNotLeaveAnInvisibleCookingIngredient() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        FoodItem steak = env.food("steak", "meat", "Beef", Method.FRYING_PAN);
        steak.getCookData().restore(Method.FRYING_PAN, 3);
        ref.slots.put("input_1", steak);
        var event = env.takeEvent(env.stack(steak, Material.BEEF, 1));
        ref.slotRemove(event);
        assertFalse(event.isCancelled());
        assertFalse(ref.slots.containsKey("input_1"), "An accepted take removes the furniture slot too");
        assertNotNull(event.getItem());
    }

    @Test
    void aRawPanIngredientStaysInItsSlotWhenRenderingFails() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        FoodItem steak = env.food("steak", "meat", "Beef", Method.FRYING_PAN);
        steak.getCookData().restore(Method.FRYING_PAN, 3);
        ref.slots.put("input_1", steak);
        var event = env.takeEvent(env.stack(steak, Material.BEEF, 1));
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        ref.slotRemove(event);
        assertTrue(event.isCancelled()); assertSame(steak, ref.getSlot("input_1"));
    }

    @Test
    void panRemovalFailureCancelsTheFurnitureTakeAndPreservesTheIngredient() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        FoodItem steak = env.food("steak", "meat", "Beef", Method.FRYING_PAN);
        steak.getCookData().restore(Method.FRYING_PAN, 6);
        ref.slots.put("input_1", steak);
        TrackLoader.oList.removeIf(track -> track.getId().equals("warmth"));
        var event = env.takeEvent(env.stack(steak, Material.BEEF, 1));
        ref.slotRemove(event);
        assertTrue(event.isCancelled(), "Furniture must keep its display when the reference keeps its ingredient");
        assertSame(steak, ref.getSlot("input_1"));
    }

    @Test
    void rejectedPanRemovalKeepsTheExistingIngredientForBothValidationFailures() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        ref.slotRemove(env.takeEvent(new ItemStack(Material.STONE)));
        for (FoodItem food : List.of(env.food("salt", "salt", "Salt"),
                env.food("potato", "vegetable", "Potato", Method.POT))) {
            ref.slots.put("input_1", food);
            var event = env.takeEvent(env.stack(food, Material.CARROT, 1));
            ref.slotRemove(event);
            assertTrue(event.isCancelled());
            assertSame(food, ref.getSlot("input_1"));
        }
    }

    @Test
    void fryingWithoutButterBurnsFoodButAnUnheatedPanDoesNotAdvanceCooking() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        FoodItem steak = env.food("steak", "meat", "Beef", Method.FRYING_PAN);
        steak.getCookData().start(Method.FRYING_PAN);
        ref.slots.put("input_1", steak);
        ref.slots.put("inert", env.food("salt", "salt", "Salt"));
        env.place("input_1", env.stack(steak, Material.BEEF, 1));
        ref.tick(); assertEquals(0, steak.getCookData().getCurrentTime());
        env.heated = true;
        for (int i = 0; i < 10; i++) ref.tick();
        assertEquals(20, steak.getCookData().getCurrentTime());
        assertEquals(2, steak.getTagTrack("cooked").getValue());
        ref.clear(); assertTrue(ref.isEmpty()); ref.tick();
        FryingReference nonPan = new FryingReference(env.furniture, Method.NONE);
        nonPan.secondaries.put("liquid", -1); nonPan.tick();
    }

    @Test
    void fryingRebuildsButterAndComposesItIntoAHeatedDishOnRemoval() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        FoodItem butter = env.food("butter", "butter", "Butter");
        FoodItem steak = env.food("steak", "meat", "Beef", Method.FRYING_PAN);
        env.place("butter", env.stack(butter, Material.GOLD_NUGGET, 1));
        env.place("input_1", env.stack(steak, Material.BEEF, 1));
        ref.rebuildFromFurniture();
        assertEquals(30, ref.getSecondary("butter"));
        assertTrue(ref.slots.containsKey("input_1"));
        steak.getCookData().restore(Method.FRYING_PAN, 6);
        var take = env.takeEvent(env.stack(steak, Material.BEEF, 1));
        ref.slotRemove(take);
        assertFalse(take.isCancelled()); assertTrue(ref.isEmpty());
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player),
                argThat(items -> items.size() == 2), eq(CompositionContext.FRYING_PAN)));
        assertTrue(steak.hasTagTrack("warmth"));
        ref.removeSecondary(Map.entry("other", 1));
        ref.rebuildFromFurniture();
    }

    @Test
    void fryingSlotAdditionAndFailedUpdatesKeepTheDisplayedItemsConsistent() {
        FryingReference ref = new FryingReference(env.furniture, Method.FRYING_PAN);
        ref.slotAdd(env.addEvent(new ItemStack(Material.STONE)));
        var wrong = env.addEvent(env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 1));
        ref.slotAdd(wrong); assertTrue(wrong.isCancelled());
        FoodItem steak = env.food("steak", "meat", "Beef", Method.FRYING_PAN);
        var add = env.addEvent(env.stack(steak, Material.BEEF, 2));
        ref.slotAdd(add);
        assertEquals(1, add.getItem().getAmount()); assertNotNull(add.getDisplayData());
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        var failed = env.addEvent(env.stack(steak, Material.BEEF, 2));
        ref.slotAdd(failed); assertEquals(2, failed.getItem().getAmount());
        steak.getCookData().restore(Method.FRYING_PAN, 6);
        var take = env.takeEvent(failed.getItem());
        ref.slotRemove(take);
        assertTrue(take.isCancelled());
        assertSame(steak, ref.getSlot("input_1"));
    }

    /** Furniture and item metadata codecs are boundaries; food, tracks and cooking clocks remain real. */
    static final class Environment implements AutoCloseable {
        final ServerMock server;
        final Cooking previousPlugin;
        final List<TagTrack> previousTracks;
        final Furniture furniture = mock(Furniture.class);
        final FurnitureType type = mock(FurnitureType.class);
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final Map<String, SlotDefinition> definitions = new LinkedHashMap<>();
        final Map<String, PlacedSlot> active = new LinkedHashMap<>();
        final Map<String, Object> variables = new HashMap<>();
        final Map<String, FoodItem> foods = new HashMap<>();
        final List<MockedStatic<?>> mocks = new ArrayList<>();
        final MockedStatic<FoodItem> foodCodec;
        final MockedStatic<ItemUpdater> updater;
        final MockedStatic<ItemCache> cache;
        final MockedStatic<IngredientConverter> converter;
        final MockedStatic<CompositionQualityResolver> composer;
        final NamespacedKey foodKey = NamespacedKey.fromString("test:reference_food");
        final String oldFallback = ItemCache.liquidFallback;
        final String oldPotDisplay = ItemCache.potLiquidDisplay;
        final int oldScoops = ItemCache.potSoupScoops;
        final int oldHeight = ItemCache.potSoupHeightDivisor;
        boolean heated;
        int nextFood;
        FoodItem lastBuilt;
        int lastQuality;

        Environment() {
            server = MockBukkit.mock();
            previousPlugin = Cooking.plugin;
            Cooking.plugin = mock(Cooking.class);
            when(Cooking.plugin.getName()).thenReturn("Cooking");
            when(Cooking.plugin.namespace()).thenReturn("cooking");
            when(Cooking.plugin.isEnabled()).thenReturn(true);
            previousTracks = TrackLoader.oList;
            TrackLoader.oList = new ArrayList<>();
            new TrackLoader().load(new File("src/main/resources/tags.yml"));
            ItemCache.liquidFallback = "v.glass";
            ItemCache.potLiquidDisplay = "v.glass";
            ItemCache.potSoupScoops = 3;
            ItemCache.potSoupHeightDivisor = 4;
            when(player.getInventory()).thenReturn(server.addPlayer().getInventory());
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 1, 65, 1));
            when(world.getName()).thenReturn("kitchen");
            when(furniture.getId()).thenReturn("test_station");
            when(furniture.getLoc()).thenReturn(new Location(world, 1, 64, 1));
            when(furniture.getType()).thenReturn(type);
            when(furniture.getVariables()).thenReturn(variables);
            when(type.getSlots()).thenReturn(definitions);
            when(type.getSlot(anyString())).thenAnswer(inv -> definitions.get(inv.getArgument(0)));
            when(furniture.getActiveSlots()).thenReturn(active);
            when(furniture.getActiveSlot(anyString())).thenAnswer(inv -> Optional.ofNullable(active.get(inv.getArgument(0))));
            when(furniture.hasActiveSlot(anyString())).thenAnswer(inv -> active.containsKey(inv.getArgument(0)));
            when(furniture.getOrCreatePlacedSlot(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                return active.containsKey(key) ? active.get(key) : place(key, null);
            });
            for (String id : List.of("liquid", "butter", "input_1", "input_2", "input_3", "input_4", "input_5")) define(id);
            scoped(mockStatic(HeatSources.class)).when(() -> HeatSources.stationHasHeat(furniture)).thenAnswer(inv -> heated);
            foodCodec = scoped(mockStatic(FoodItem.class));
            foodCodec.when(() -> FoodItem.fromItem(any())).thenAnswer(inv -> resolve(inv.getArgument(0)));
            updater = scoped(mockStatic(ItemUpdater.class));
            updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(inv -> {
                ItemStack source = inv.getArgument(0);
                return source == null ? null : stack(inv.getArgument(1), source.getType(), source.getAmount());
            });
            cache = scoped(mockStatic(ItemCache.class));
            cache.when(() -> ItemCache.getColour(any())).thenReturn("aa5500");
            cache.when(() -> ItemCache.isLadle(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.PAPER));
            cache.when(() -> ItemCache.isMasher(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.STICK));
            cache.when(() -> ItemCache.isButter(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.GOLD_NUGGET));
            cache.when(() -> ItemCache.isPotWaterInput(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.WATER_BUCKET));
            cache.when(() -> ItemCache.isCupOfWater(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.HONEY_BOTTLE));
            cache.when(() -> ItemCache.isCupOfMilk(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.WHITE_DYE));
            cache.when(() -> ItemCache.isMilkBucket(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.MILK_BUCKET));
            cache.when(() -> ItemCache.isWater(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.POTION));
            cache.when(() -> ItemCache.isLiquid(any())).thenAnswer(inv -> material(inv.getArgument(0), Material.LAVA_BUCKET));
            ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
            scoped(mockStatic(TLibs.class)).when(TLibs::getItemAPI).thenReturn(api);
            when(api.getCreator().getItemFromPath(any())).thenAnswer(inv -> new ItemStack(Material.GLASS));
            InteractibleFurniture plugin = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
            scoped(mockStatic(InteractibleFurniture.class)).when(InteractibleFurniture::getInstance).thenReturn(plugin);
            converter = scoped(mockStatic(IngredientConverter.class));
            converter.when(() -> IngredientConverter.convertIfNeeded(any(), any())).thenAnswer(inv -> inv.getArgument(1));
            scoped(mockStatic(CupItems.class)).when(CupItems::emptyCup).thenAnswer(inv -> new ItemStack(Material.GLASS_BOTTLE));
            scoped(mockStatic(BucketItems.class)).when(BucketItems::empty).thenAnswer(inv -> new ItemStack(Material.BUCKET));
            scoped(mockStatic(Encoder.class)).when(() -> Encoder.getEncodedSlots(furniture)).thenReturn("saved-inputs");
            composer = scoped(mockStatic(CompositionQualityResolver.class));
            composer.when(() -> CompositionQualityResolver.compose(any(), any(), any())).thenReturn(
                    new CompositionResult(60, 72, Map.of(), List.of(), List.of(), List.of(), null));
            scoped(mockStatic(FoodParser.class)).when(() -> FoodParser.parse(anyString())).thenAnswer(inv -> {
                String text = inv.getArgument(0);
                String kind = text.startsWith("soup") ? "soup" : "sauce";
                FoodParser.Result result = new FoodParser.Result();
                result.template = foodNamed(kind, kind, "Mixed", "{colour}{prefixes}{ingredients}");
                result.template.setBaseFood(8);
                return result;
            });
            scoped(mockStatic(ItemBuilder.class)).when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenAnswer(inv -> {
                lastBuilt = inv.getArgument(0); lastQuality = inv.getArgument(2);
                return stack(lastBuilt, Material.PAPER, 1);
            });
        }

        <T> MockedStatic<T> scoped(MockedStatic<T> value) { mocks.add(value); return value; }
        static boolean material(ItemStack item, Material material) { return item != null && item.getType() == material; }
        FoodItem foodNamed(String id, String category, String origin, String name) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("name", name); config.set("update", false); config.set("mashable", true);
            FoodItem result = new FoodItem(id, config);
            result.setCategory(category); result.setOrigin(origin);
            result.setModel(new FoodModel(new ItemStack(Material.CARROT)));
            return result;
        }
        FoodItem food(String id, String category, String origin, Method... methods) {
            FoodItem result = foodNamed(id, category, origin, id);
            if (methods.length > 0) {
                result.addOrModifyTrack(TrackLoader.getByString("cooked"));
                for (Method method : methods) result.getCookData().getParameters().put(method, new CookParameter(1, 2, 20));
            }
            return new FoodItem(result);
        }
        ItemStack stack(FoodItem food, Material material, int amount) {
            ItemStack stack = new ItemStack(material, amount);
            String id = "food-" + nextFood++;
            foods.put(id, food);
            var meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(foodKey, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
            return stack;
        }
        FoodItem resolve(ItemStack item) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
            return foods.get(item.getItemMeta().getPersistentDataContainer().get(foodKey, PersistentDataType.STRING));
        }
        SlotDefinition define(String id) {
            SlotDefinition definition = mock(SlotDefinition.class);
            when(definition.getId()).thenReturn(id);
            when(definition.getDisplayScale()).thenReturn(new Vector(1, 2, 1));
            definitions.put(id, definition);
            return definition;
        }
        PlacedSlot place(String id, ItemStack initial) {
            PlacedSlot slot = mock(PlacedSlot.class);
            ItemStack[] current = {initial};
            when(slot.getCurrentItem()).thenAnswer(inv -> current[0]);
            doAnswer(inv -> { current[0] = inv.getArgument(0); return null; }).when(slot).forceModel(any());
            doAnswer(inv -> { current[0] = null; active.remove(id); return null; }).when(slot).clearModel();
            active.put(id, slot);
            return slot;
        }
        FurnitureInteractEvent interaction() { return new FurnitureInteractEvent(player, furniture); }
        FurnitureSlotItemAddEvent addEvent(ItemStack stack) { return new FurnitureSlotItemAddEvent(player, furniture, definitions.get("input_1"), stack); }
        FurnitureSlotItemTakeEvent takeEvent(ItemStack stack) { return new FurnitureSlotItemTakeEvent(player, furniture, definitions.get("input_1"), stack); }
        @Override public void close() {
            for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
            TrackLoader.oList = previousTracks;
            Cooking.plugin = previousPlugin;
            ItemCache.liquidFallback = oldFallback; ItemCache.potLiquidDisplay = oldPotDisplay;
            ItemCache.potSoupScoops = oldScoops; ItemCache.potSoupHeightDivisor = oldHeight;
            MockBukkit.unmock();
        }
    }
}
