package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.enums.Tag;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

class PotReferenceCoverageTest {
    CookingReferencesTest.Environment env;

    @BeforeEach void setUp() { env = new CookingReferencesTest.Environment(); }
    @AfterEach void tearDown() { env.close(); }

    @Test
    void waterRequiresTwentyHeatedTicksAndCoolsWhenTheFireGoesOut() {
        PotReference pot = pot();
        env.heated = true;
        for (int i = 0; i < 25; i++) pot.tick();
        assertFalse(pot.isBoiling(), "An empty pot cannot heat water it does not contain");
        pot.secondaries.put("liquid", -1);
        for (int i = 0; i < 19; i++) pot.tick();
        assertFalse(pot.isBoiling());
        pot.tick(); assertTrue(pot.isBoiling());
        pot.tick(); assertTrue(pot.isBoiling());
        env.server.getScheduler().performTicks(25);
        env.heated = false; pot.tick(); assertFalse(pot.isBoiling());
        for (int i = 0; i < 25; i++) pot.tick();
        assertFalse(pot.isBoiling());
    }

    @Test
    void boilingSchedulesFiniteBubbleEffectsAtTheLiquidDisplay() {
        PotReference pot = pot();
        PlacedSlot liquid = env.place("liquid", new ItemStack(Material.GLASS));
        Entity display = mock(Entity.class);
        UUID id = UUID.randomUUID();
        when(liquid.getDisplayStandId()).thenReturn(id);
        when(display.getLocation()).thenAnswer(invocation -> env.furniture.getLoc());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(display);
            boil(pot);
            for (int i = 0; i < 100; i++) pot.tick();
            env.server.getScheduler().performTicks(25);
            verify(env.world, atLeast(1)).spawnParticle(eq(Particle.BUBBLE_POP), any(), eq(1), eq(0d), eq(0.05d), eq(0d), eq(0.05d));
            clearInvocations(env.world);
            env.server.getScheduler().performTicks(20);
            verifyNoInteractions(env.world);
        }
    }

    @Test
    void firstFoodSelectionSkipsEmptyAndNonFoodDisplaysAndUpdatesOnlyTheMain() {
        PotReference pot = pot();
        assertNull(pot.getMain());
        pot.setMain(null);
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        pot.setMain(carrot);
        env.place("empty", null);
        env.place("liquid", new ItemStack(Material.GLASS));
        PlacedSlot first = env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        FoodItem potato = env.food("potato", "vegetable", "Potato", Method.POT);
        PlacedSlot next = env.place("input_2", env.stack(potato, Material.POTATO, 1));
        assertSame(carrot, pot.getMain());
        pot.setMain(carrot);
        verify(first).forceModel(any()); verify(next, never()).forceModel(any());
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        pot.setMain(carrot);
        verify(first, times(1)).forceModel(any());
    }

    @Test
    void potStartsRawCookingAndUpdatesTheDisplayWhenItsCookedStateChanges() {
        PotReference pot = pot();
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        PlacedSlot placed = env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        pot.slots.put("input_1", carrot);
        pot.slots.put("extra_salt", env.food("salt", "salt", "Salt"));
        pot.slots.put("uncookable", env.food("inert", "flour", "Wheat"));
        env.heated = true;
        pot.tick(); assertEquals(1, carrot.getCookData().getCurrentTime());
        pot.tick(); assertEquals(2, carrot.getCookData().getCurrentTime());
        assertEquals(1, carrot.getTagTrack("cooked").getValue());
        verify(placed).forceModel(any());
    }

    @Test
    void mashedSoupThickensWithoutRestartingTheIngredientCookingClock() {
        PotReference pot = pot();
        FoodItem carrot = mashed("carrot");
        carrot.getCookData().restore(Method.POT, 4);
        env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        pot.slots.put("input_1", carrot);
        env.heated = true;
        pot.tick();
        assertTrue(carrot.hasTagTrack("soup_thickness"));
        assertFalse(carrot.getCookData().isBeingCooked());
        pot.tick();
        assertEquals(1, carrot.getTagTrack("soup_thickness").getValue());
        assertFalse(carrot.getCookData().isBeingCooked(), "Mashed soup must stay off the raw-ingredient cooking clock");
    }

    @Test
    void aRawIngredientAddedToExistingSoupStillCooksWhileMashedIngredientsStayStopped() {
        PotReference pot = preparedSoup();
        FoodItem mashed = pot.getMain();
        FoodItem raw = env.food("potato", "vegetable", "Potato", Method.POT);
        env.place("input_2", env.stack(raw, Material.POTATO, 1));
        pot.slots.put("input_2", raw);
        env.heated = true;
        pot.tick();
        assertTrue(raw.getCookData().isBeingCooked());
        assertEquals(1, raw.getCookData().getCurrentTime());
        assertFalse(mashed.getCookData().isBeingCooked());
        pot.tick();
        assertEquals(2, raw.getCookData().getCurrentTime());
        assertFalse(mashed.getCookData().isBeingCooked());
    }

    @Test
    void cookingTheFirstRawIngredientPreservesSoupThicknessOnTheDisplayedFood() {
        useIndependentFoodSnapshots();
        FoodItem raw = env.food("potato", "vegetable", "Potato", Method.POT);
        PlacedSlot first = env.place("input_1", env.stack(raw, Material.POTATO, 1));
        env.place("input_2", env.stack(mashed("carrot"), Material.CARROT, 1));
        env.place("liquid", new ItemStack(Material.GLASS));
        PotReference pot = pot();
        pot.rebuildFromFurniture();
        assertTrue(pot.isSoup());
        assertNotSame(pot.getSlot("input_1"), pot.getMain());

        env.heated = true;
        pot.tick();
        assertEquals(1, pot.getSlot("input_1").getCookData().getCurrentTime());
        assertTrue(env.resolve(first.getCurrentItem()).hasTagTrack("soup_thickness"));
        pot.tick();

        FoodItem displayed = env.resolve(first.getCurrentItem());
        assertEquals(2, pot.getSlot("input_1").getCookData().getCurrentTime());
        assertEquals(1, displayed.getTagTrack("cooked").getValue());
        assertTrue(displayed.hasTagTrack("soup_thickness"),
                "Rendering the cooking transition must preserve the thickness accumulated by the soup");
        assertEquals(1, displayed.getTagTrack("soup_thickness").getValue());
        assertFalse(pot.getSlot("input_2").getCookData().isBeingCooked());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void remashingSoupPreservesItsAccumulatedThickness(boolean rawFirst) {
        useIndependentFoodSnapshots();
        FoodItem raw = env.food("potato", "vegetable", "Potato", Method.POT);
        FoodItem soup = mashed("carrot");
        PlacedSlot first = env.place("input_1", env.stack(rawFirst ? raw : soup, Material.CARROT, 1));
        env.place("input_2", env.stack(rawFirst ? soup : raw, Material.POTATO, 1));
        env.place("liquid", new ItemStack(Material.GLASS));
        PotReference pot = pot();
        pot.rebuildFromFurniture();
        assertTrue(pot.isSoup());
        env.heated = true;
        for (int tick = 0; tick < 3; tick++) pot.tick();
        assertEquals(2, env.resolve(first.getCurrentItem()).getTagTrack("soup_thickness").getValue());

        pot.mash(env.player);

        FoodItem displayed = env.resolve(first.getCurrentItem());
        assertTrue(displayed.hasTagTrack("soup_thickness"),
                "Re-mashing must preserve the soup's accumulated thickness");
        assertEquals(2, displayed.getTagTrack("soup_thickness").getValue());
        for (FoodItem ingredient : pot.getSlots().values()) {
            assertTrue(ingredient.hasTag(Tag.MASHED));
            assertEquals(3, ingredient.getTagTrack("cooked").getValue());
            assertFalse(ingredient.getCookData().isBeingCooked());
        }
        pot.tick();
        pot.mash(env.player);
        assertEquals(3, env.resolve(first.getCurrentItem()).getTagTrack("soup_thickness").getValue());
    }

    @Test
    void additionsRequireBoilingWaterAndAppropriateFoodAndKeepTheFiveMainLimit() {
        PotReference pot = pot();
        ItemStack carrot = env.stack(env.food("carrot", "vegetable", "Carrot", Method.POT), Material.CARROT, 1);
        assertFalse(pot.canAdd(env.player, carrot));
        boil(pot);
        pot.secondaries.clear(); assertFalse(pot.canAdd(env.player, carrot));
        pot.secondaries.put("liquid", -1);
        assertFalse(pot.canAdd(env.player, null));
        assertFalse(pot.canAdd(env.player, new ItemStack(Material.AIR)));
        assertFalse(pot.canAdd(env.player, new ItemStack(Material.STONE)));
        assertFalse(pot.canAdd(env.player, env.stack(env.food("raw", "vegetable", "Carrot"), Material.CARROT, 1)));
        assertFalse(pot.canAdd(env.player, env.stack(env.food("pan", "meat", "Beef", Method.FRYING_PAN), Material.BEEF, 1)));
        assertTrue(pot.canAdd(env.player, carrot));
        ItemStack salt = env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 1);
        assertFalse(pot.canAdd(env.player, salt));
        pot.slots.put("input_1", env.resolve(carrot));
        assertFalse(pot.canAdd(env.player, carrot));
        pot.slots.put("input_1", mashed("carrot"));
        assertTrue(pot.canAdd(env.player, salt));
        pot.slots.put("extra_salt", env.resolve(salt));
        assertFalse(pot.canAdd(env.player, salt));
        for (int i = 2; i <= 5; i++) pot.slots.put("input_" + i, mashed("carrot" + i));
        assertFalse(pot.canAdd(env.player, carrot));
        assertFalse(pot.canAdd(null, carrot));
    }

    @Test
    void takingBoiledFoodSkipsUnavailableSlotsAndAppliesWarmthToTheReturnedItem() {
        PotReference pot = pot();
        FoodItem food = env.food("carrot", "vegetable", "Carrot", Method.POT);
        food.getTagTrack("cooked").forceSetValue(3);
        pot.slots = new LinkedHashMap<>();
        pot.slots.put("missing", food); pot.slots.put("empty", food); pot.slots.put("input_1", food);
        env.place("empty", null);
        PlacedSlot placed = env.place("input_1", env.stack(food, Material.CARROT, 1));
        pot.take(env.player);
        assertTrue(food.hasTagTrack("warmth"));
        assertTrue(pot.slots.isEmpty()); verify(placed).clearModel();
        assertSame(food, env.resolve(env.player.getInventory().getItemInMainHand()));
        pot.slots.put("input_1", mashed("carrot"));
        pot.take(env.player); assertEquals(1, pot.slots.size());
    }

    @Test
    void mashingMarksOnlyMashableMainsBoiledAndHidesTheirDisplays() {
        PotReference pot = pot();
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        carrot.getCookData().start(Method.POT);
        FoodItem hard = env.food("hard", "meat", "Bone", Method.POT); hard.setMashable(false);
        pot.slots.put("input_1", carrot); pot.slots.put("input_2", hard);
        pot.slots.put("extra_salt", env.food("salt", "salt", "Salt"));
        PlacedSlot first = env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        env.place("input_2", env.stack(hard, Material.BONE, 1));
        pot.mash(env.player);
        assertTrue(pot.isSoup()); assertTrue(carrot.hasTag(Tag.MASHED));
        assertEquals(3, carrot.getTagTrack("cooked").getValue());
        assertFalse(carrot.getCookData().isBeingCooked());
        assertFalse(hard.hasTag(Tag.MASHED));
        assertEquals(3, env.variables.get("pot.soupServings"));
        ArgumentCaptor<DisplayData> poses = ArgumentCaptor.forClass(DisplayData.class);
        verify(first, atLeastOnce()).applyDisplayData(poses.capture());
        assertTrue(poses.getAllValues().stream().anyMatch(pose -> Float.valueOf(0).equals(pose.getxScale())));
        pot.mash(env.player); assertEquals(3, env.variables.get("pot.soupServings"));
    }

    @Test
    void mashingWithoutADisplayedEligibleMainDoesNotCreateServings() {
        PotReference pot = pot();
        pot.mash(env.player);
        FoodItem hard = env.food("bone", "meat", "Bone"); hard.setMashable(false);
        pot.slots.put("input_1", hard); pot.mash(env.player);
        assertFalse(env.variables.containsKey("pot.soupServings"));
        FoodItem missing = env.food("carrot", "vegetable", "Carrot", Method.POT);
        pot.slots.put("input_2", missing); pot.mash(env.player);
        assertFalse(env.variables.containsKey("pot.soupServings"));
    }

    @Test
    void boilingTrackIsCreatedWhenMissingAndMissingTrackDefinitionsAreTolerated() {
        FoodItem food = env.food("carrot", "vegetable", "Carrot");
        PotReference.markBoiled(food);
        assertEquals(3, food.getTagTrack("cooked").getValue());
        FoodItem other = env.food("potato", "vegetable", "Potato");
        TrackLoader.oList.removeIf(track -> track.getId().equals("cooked"));
        PotReference.markBoiled(other);
        assertFalse(other.hasTagTrack("cooked"));
    }

    @Test
    void soupScoopKeepsTemplateFoodAndPersistsEachRemainingServingAndHeight() {
        PotReference pot = preparedSoup();
        FoodItem main = pot.getMain();
        main.addOrModifyTrack(TrackLoader.getByString("soup_thickness"));
        main.getTagTrack("soup_thickness").setValue(400);
        PlacedSlot liquid = env.place("liquid", new ItemStack(Material.GLASS));
        ItemStack ladle = new ItemStack(Material.PAPER);
        pot.scoop(env.player, ladle);
        assertEquals(2, env.variables.get("pot.soupServings"));
        assertEquals(8, env.lastBuilt.getBaseFood());
        assertEquals(400, env.lastBuilt.getTagTrack("soup_thickness").getValue());
        assertEquals(72, env.lastQuality);
        assertEquals("saved-inputs", env.player.getInventory().getItemInMainHand().getItemMeta()
                .getPersistentDataContainer().get(Keys.SLOT_DATA, PersistentDataType.STRING));
        ArgumentCaptor<DisplayData> pose = ArgumentCaptor.forClass(DisplayData.class);
        verify(liquid).applyDisplayData(pose.capture());
        assertEquals(-0.5f, pose.getValue().getyPos());
        pot.scoop(env.player, ladle); assertEquals(1, env.variables.get("pot.soupServings"));
        pot.scoop(env.player, ladle); assertTrue(pot.isEmpty());
        assertFalse(env.variables.containsKey("pot.soupServings"));
        assertFalse(env.variables.containsKey("pot.extras"));
        pot.scoop(env.player, ladle);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "invalid", "-4"})
    void restoredServingValuesAreParsedOrFallBackToConfiguredPortions(String count) {
        PotReference pot = preparedSoup();
        env.variables.put("pot.soupServings", count);
        pot.scoop(env.player, new ItemStack(Material.PAPER));
        if (count.equals("-4")) assertFalse(env.variables.containsKey("pot.soupServings"));
        else assertEquals(count.equals("2") ? 1 : 2, env.variables.get("pot.soupServings"));
    }

    @Test
    void soupLevelUsesSafeConfigurationMinimumsAndMissingDefinitionsAreHarmless() {
        PotReference pot = preparedSoup();
        ItemCache.potSoupScoops = 0; ItemCache.potSoupHeightDivisor = 0;
        env.variables.put("pot.soupServings", 0);
        env.place("liquid", new ItemStack(Material.GLASS));
        pot.updateModel();
        env.definitions.remove("liquid"); pot.updateModel();
        pot.rebuildFromFurniture();
        when(env.furniture.getType()).thenReturn(null); pot.updateModel();
        new PotReference(null, Method.POT).rebuildFromFurniture();
        assertEquals(26, PotReference.scoopFood(26));
    }

    @Test
    void waterInteractionConsumesOnlyTheCorrectBucketAndRejectsWrongContainers() {
        PotReference pot = pot();
        pot.interact(env.interaction());
        for (Material wrong : List.of(Material.HONEY_BOTTLE, Material.POTION, Material.LAVA_BUCKET)) {
            env.player.getInventory().setItemInMainHand(new ItemStack(wrong));
            pot.interact(env.interaction());
            assertTrue(pot.isEmpty());
        }
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
        var event = env.interaction(); pot.interact(event);
        assertTrue(event.isCancelled());
        assertEquals(Material.BUCKET, env.player.getInventory().getItemInMainHand().getType());
        assertEquals(-1, pot.getSecondary("liquid"));
        assertEquals(List.of("3d85c6"), pot.getColours());
        assertFalse(pot.isBoiling());
    }

    @Test
    void unconfiguredPotRejectsWaterWithoutConsumingIt() {
        PotReference pot = pot();
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
        env.definitions.remove("liquid"); pot.interact(env.interaction());
        assertTrue(pot.isEmpty());
        when(env.furniture.getType()).thenReturn(null); pot.interact(env.interaction());
        assertTrue(pot.isEmpty());
        assertEquals(Material.WATER_BUCKET, env.player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void toolsAndHeldSoupCannotAccidentallyAddOrConsumeAnotherServing() {
        PotReference pot = pot();
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        var emptyLadle = env.interaction(); pot.interact(emptyLadle); assertFalse(emptyLadle.isCancelled());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        var mash = env.interaction(); pot.interact(mash); assertTrue(mash.isCancelled());
        env.player.getInventory().setItemInMainHand(env.stack(env.food("soup", "soup", "Mixed"), Material.PAPER, 1));
        var heldSoup = env.interaction(); pot.interact(heldSoup); assertTrue(heldSoup.isCancelled());
        assertTrue(pot.isEmpty());
        PotReference soup = preparedSoup();
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        var scoop = env.interaction(); soup.interact(scoop); assertTrue(scoop.isCancelled());
        assertEquals(2, env.variables.get("pot.soupServings"));
    }

    @Test
    void ingredientInteractionFillsTheFirstFreeInputAndEmptyHandTakesItBack() {
        PotReference pot = pot(); boil(pot);
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        env.player.getInventory().setItemInMainHand(env.stack(carrot, Material.CARROT, 3));
        var add = env.interaction(); pot.interact(add);
        assertTrue(add.isCancelled()); assertEquals(1, pot.slots.size());
        assertEquals(2, env.player.getInventory().getItemInMainHand().getAmount());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        pot.interact(env.interaction());
        assertSame(carrot, env.resolve(env.player.getInventory().getItemInMainHand()));
        assertTrue(pot.slots.isEmpty());
    }

    @Test
    void soupExtrasConsumeOnePersistAcrossRebuildAndThickenTheMain() {
        PotReference pot = preparedSoup(); boil(pot);
        pot.getMain().addOrModifyTrack(TrackLoader.getByString("soup_thickness"));
        env.player.getInventory().setItemInMainHand(env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 2));
        var add = env.interaction(); pot.interact(add);
        assertTrue(add.isCancelled());
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
        assertNotNull(env.variables.get("pot.extras"));
        assertEquals(600, pot.getMain().getTagTrack("soup_thickness").getValue());
        env.player.getInventory().setItemInMainHand(env.stack(env.food("pepper", "pepper", "Pepper"), Material.BLACK_DYE, 2));
        pot.interact(env.interaction());
        assertTrue(env.variables.get("pot.extras").toString().contains("|"));
        assertEquals(1200, pot.getMain().getTagTrack("soup_thickness").getValue());
        PotReference restored = pot(); restored.rebuildFromFurniture();
        assertTrue(restored.slots.containsKey("extra_salt"));
        assertTrue(restored.slots.containsKey("extra_pepper"));
        assertEquals("salt", restored.getSlot("extra_salt").getCategory());
        assertTrue(restored.isSoup());
        assertEquals("3d85c6", restored.getColours().get(0));
        restored.clear(); assertFalse(env.variables.containsKey("pot.extras"));
    }

    @Test
    void anExtraThatCannotBeSerializedIsRejectedWithoutConsumingTheIngredient() {
        PotReference pot = preparedSoup(); boil(pot);
        env.player.getInventory().setItemInMainHand(env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 2));
        // The codec already returns null on an I/O failure; the transaction must handle that result.
        try (MockedStatic<PotReference> codec = mockStatic(PotReference.class, CALLS_REAL_METHODS)) {
            codec.when(() -> PotReference.encodeStack(any())).thenReturn(null);
            pot.interact(env.interaction());
        }
        assertEquals(2, env.player.getInventory().getItemInMainHand().getAmount());
        assertFalse(pot.slots.containsKey("extra_salt"));
        assertFalse(env.variables.containsKey("pot.extras"));
    }

    @Test
    void aFailedSecondExtraDoesNotReplacePreviouslySavedIngredients() {
        PotReference pot = preparedSoup(); boil(pot);
        FoodItem salt = env.food("salt", "salt", "Salt");
        env.player.getInventory().setItemInMainHand(env.stack(salt, Material.SUGAR, 2));
        pot.interact(env.interaction());
        Object saved = env.variables.get("pot.extras");
        env.player.getInventory().setItemInMainHand(env.stack(env.food("pepper", "pepper", "Pepper"), Material.BLACK_DYE, 2));
        try (MockedStatic<PotReference> codec = mockStatic(PotReference.class, CALLS_REAL_METHODS)) {
            codec.when(() -> PotReference.encodeStack(argThat(stack -> stack != null
                    && env.resolve(stack) != null && env.resolve(stack).getCategory().equals("pepper"))))
                    .thenReturn(null);
            pot.interact(env.interaction());
        }
        assertEquals(2, env.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(saved, env.variables.get("pot.extras"));
        assertSame(salt, pot.getSlot("extra_salt"));
        assertFalse(pot.slots.containsKey("extra_pepper"));
    }

    @Test
    void extraSlotNamesRecognizeOnlyTheSupportedCategories() {
        for (String category : List.of("salt", "pepper", "garnish", "spice")) {
            assertEquals("extra_" + category, PotReference.extraSlotKey(category.toUpperCase()));
        }
        assertNull(PotReference.extraSlotKey(null)); assertNull(PotReference.extraSlotKey("vegetable"));
        assertTrue(PotReference.isExtraSlot("extra_salt"));
        assertFalse(PotReference.isExtraSlot(null)); assertFalse(PotReference.isExtraSlot("input_1"));
        assertFalse(PotReference.canMash(null));
    }

    @Test
    void rebuildRecoversBoiledAndMashedMainsButDiscardsUnsupportedOrMissingFood() {
        PotReference pot = pot();
        FoodItem boiled = env.food("boiled", "vegetable", "Carrot", Method.POT);
        boiled.getTagTrack("cooked").forceSetValue(3);
        env.place("input_1", env.stack(boiled, Material.CARROT, 1));
        env.place("input_2", env.stack(mashed("potato"), Material.POTATO, 1));
        env.place("input_3", new ItemStack(Material.STONE));
        env.place("input_4", env.stack(env.food("pan", "meat", "Beef", Method.FRYING_PAN), Material.BEEF, 1));
        env.place("liquid", new ItemStack(Material.GLASS));
        pot.rebuildFromFurniture();
        assertEquals(2, pot.slots.size()); assertTrue(pot.isSoup());
        assertSame(boiled, pot.getSlot("input_1"));
        assertEquals(-1, pot.getSecondary("liquid"));
        assertEquals(List.of("3d85c6", "aa5500"), pot.getColours());
    }

    @Test
    void malformedSavedExtrasAreIgnoredInsteadOfCrashingFurnitureReload() {
        for (Object saved : List.of(5, " ", "missing-dot", ".bad", "extra_salt.", "input_1.bad", "extra_salt.%%%")) {
            env.variables.put("pot.extras", saved);
            PotReference pot = pot();
            assertDoesNotThrow(pot::rebuildFromFurniture);
            assertTrue(pot.slots.isEmpty());
        }
        String stone = PotReference.encodeStack(new ItemStack(Material.STONE));
        env.variables.put("pot.extras", "extra_salt." + stone);
        PotReference pot = pot(); pot.rebuildFromFurniture(); assertTrue(pot.slots.isEmpty());
    }

    @Test
    void savedStackCodecRoundTripsItemsAndRejectsCorruptOrNonItemData() throws Exception {
        ItemStack original = new ItemStack(Material.CARROT, 5);
        assertEquals(original, PotReference.decodeStack(PotReference.encodeStack(original)));
        assertNull(PotReference.encodeStack(null));
        assertNull(PotReference.decodeStack(null)); assertNull(PotReference.decodeStack(" "));
        assertNull(PotReference.decodeStack("%%%"));
        assertNull(PotReference.decodeStack(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) { stream.writeObject("not an item"); }
        assertNull(PotReference.decodeStack(Base64.getEncoder().encodeToString(bytes.toByteArray())));
        ItemStack broken = mock(ItemStack.class);
        when(broken.serialize()).thenReturn(Map.of("nonserializable", new Object()));
        assertNull(PotReference.encodeStack(broken));
    }

    @Test
    void clearingPlainWaterPreservesTheFoodUntilThePlayerTakesIt() {
        PotReference pot = pot();
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        PlacedSlot input = env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        PlacedSlot liquid = env.place("liquid", new ItemStack(Material.GLASS));
        pot.slots.put("input_1", carrot); pot.secondaries.put("liquid", -1);
        env.variables.put("pot.soupServings", 2); env.variables.put("pot.extras", "old");
        pot.clear();
        assertSame(carrot, pot.getSlot("input_1"));
        assertTrue(pot.secondaries.isEmpty());
        assertFalse(env.variables.containsKey("pot.soupServings"));
        verify(input, never()).clearModel(); verify(liquid).clearModel();
    }

    private void useIndependentFoodSnapshots() {
        // Serialized food reads and writes create independent values in production.
        env.foodCodec.when(() -> FoodItem.fromItem(any())).thenAnswer(invocation -> {
            FoodItem stored = env.resolve(invocation.getArgument(0));
            return stored == null ? null : new FoodItem(stored);
        });
        env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(invocation -> {
            ItemStack source = invocation.getArgument(0);
            FoodItem food = invocation.getArgument(1);
            return env.stack(new FoodItem(food), source.getType(), source.getAmount());
        });
    }

    private PotReference pot() { return new PotReference(env.furniture, Method.POT); }
    private void boil(PotReference pot) {
        env.heated = true; pot.secondaries.put("liquid", -1);
        for (int i = 0; i < 20; i++) pot.tick();
        assertTrue(pot.isBoiling());
    }
    private FoodItem mashed(String id) {
        FoodItem food = env.food(id, "vegetable", id, Method.POT);
        food.addOrModifyTrack(TrackLoader.getByString("mashed"));
        food.getTagTrack("cooked").forceSetValue(3);
        return food;
    }
    private PotReference preparedSoup() {
        PotReference pot = pot();
        FoodItem carrot = mashed("carrot");
        pot.slots.put("input_1", carrot);
        env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        env.place("liquid", new ItemStack(Material.GLASS));
        pot.secondaries.put("liquid", -1);
        return pot;
    }
}
