package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.CategoryDictionary;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.fishing.CustomFishingCatalog;
import net.tfminecraft.cooking.fishing.LegacyFishConversion;
import net.tfminecraft.cooking.fishing.SeafoodWholeItems;
import net.tfminecraft.cooking.fishing.VanillaFish;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.util.LegacyModelData;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public class MealManagersCoverageTest {
    Kitchen kitchen;
    Workshop env;
    Station plate, bowl;
    PlateManager plates;
    ConversionManager conversions;
    MockedStatic<FoodParser> parser;
    MockedStatic<LegacyFishConversion> legacy;
    MockedStatic<SeafoodWholeItems> seafood;
    Map<String, String> previousConversions;
    ItemStack pickupOutput;
    int builtQuality;

    @BeforeEach void open() {
        kitchen = new Kitchen(); env = kitchen.env; plate = kitchen.station("plate"); bowl = kitchen.station("bowl");
        plates = new PlateManager(); conversions = new ConversionManager();
        for (String id : List.of("food_item", "food_2", "sauce", "display_1")) plate.define(id);
        for (String id : List.of("food_item", "input_1", "input_2", "input_3", "input_4", "input_5", "liquid", "display_1")) bowl.define(id);
        previousConversions = ConversionLoader.conversions; ConversionLoader.conversions = new LinkedHashMap<>();
        ConversionLoader.conversions.put("v.wheat", "grain(type=wheat)"); ConversionLoader.conversions.put("v.beef", "meat(type=steak)");
        when(env.api.getChecker().checkItemWithPath(any(), eq("v.beef"))).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.BEEF;
        });
        parser = env.scoped(mockStatic(FoodParser.class));
        parser.when(() -> FoodParser.parse(anyString())).thenAnswer(inv -> {
            FoodParser.Result parsed = new FoodParser.Result(); parsed.template = kitchen.food("converted", "grain"); return parsed;
        });
        legacy = env.scoped(mockStatic(LegacyFishConversion.class));
        seafood = env.scoped(mockStatic(SeafoodWholeItems.class));
        env.scoped(mockStatic(CustomFishingCatalog.class)).when(() -> CustomFishingCatalog.findVanilla("COD"))
                .thenReturn(new VanillaFish("COD", "Cod", "filet", 30));
        pickupOutput = env.stack(kitchen.food("converted", "grain"), Material.PAPER, 1);
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(ItemStack.class), anyInt())).thenAnswer(inv -> {
            builtQuality = inv.getArgument(2); return pickupOutput.clone();
        });
    }

    @AfterEach void close() { ConversionLoader.conversions = previousConversions; kitchen.close(); }

    FoodItem sauce() { return kitchen.food("sauce", "sauce"); }
    ItemStack ladle(FoodItem food) {
        ItemStack stack = env.stack(food, Material.PAPER, 1); var meta = stack.getItemMeta();
        meta.setDisplayName("Basil Sauce"); meta.getPersistentDataContainer().set(Keys.SAUCE_COLOUR, PersistentDataType.STRING, "#185d15");
        stack.setItemMeta(meta); return stack;
    }
    ItemStack soup() {
        ItemStack stack = ladle(kitchen.food("soup", "soup")); var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.SLOT_DATA, PersistentDataType.STRING,
                "input_1.CARROT.0:input_2.POTATO.2:input_3.BEEF.0:input_4.WHEAT.0:input_5.BEETROOT.0:liquid.GLASS.0:undeclared.STONE.0");
        stack.setItemMeta(meta); return stack;
    }
    EntityPickupItemEvent pickup(ItemStack stack) {
        Item item = mock(Item.class); when(item.getItemStack()).thenReturn(stack);
        return new EntityPickupItemEvent(env.player, item, 0);
    }
    InventoryOpenEvent opening(Inventory top) {
        InventoryView view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(top); when(view.getPlayer()).thenReturn(env.player);
        return new InventoryOpenEvent(view);
    }

    @Test
    void plateAgingRemovesOrphanSauceAndUpdatesOnlyFoodSlots() {
        PlacedSlot visual = plate.place("display_1", new ItemStack(Material.STONE));
        plate.place("sauce", new ItemStack(Material.STONE)); plates.update(plate.furniture);
        assertFalse(plate.active.containsKey("sauce")); verify(env.manager).persistFurniture(plate.furniture); verify(visual, never()).forceModel(any());
        PlacedSlot food = plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
        plate.place("food_2", null); plate.place("decoration", new ItemStack(Material.STONE));
        Station other = kitchen.station("chair"); PlacedSlot untouched = other.place("food_item", food.getCurrentItem());
        plates.start(); env.server.getScheduler().performTicks(20); verify(food).forceModel(any()); verify(untouched, never()).forceModel(any());
        verify(env.manager).markDirty(plate.furniture);
        kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(null);
        env.server.getScheduler().performTicks(20); verify(food).forceModel(any()); verify(env.manager).markDirty(plate.furniture);
    }

    @Test
    void airAgingResultsNeverRemovePlatedFood() {
        ItemStack original = env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1); PlacedSlot slot = plate.place("food_item", original);
        kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenReturn(new ItemStack(Material.AIR));
        plates.update(plate.furniture); assertSame(original, slot.getCurrentItem()); verify(slot, never()).forceModel(any());
    }

    @Test
    void breakingClearsPlateDecorationsAndAllBowlContentsButKeepsLoosePlateFood() {
        PlacedSlot meat = plate.place("food_item", new ItemStack(Material.BEEF)); PlacedSlot display = plate.place("display_1", new ItemStack(Material.STONE));
        PlacedSlot soup = bowl.place("food_item", new ItemStack(Material.PAPER)); PlacedSlot ingredient = bowl.place("input_1", new ItemStack(Material.CARROT));
        Station chair = kitchen.station("chair"); PlacedSlot other = chair.place("display_1", new ItemStack(Material.STONE));
        plates.remove(new FurnitureBreakEvent(chair.furniture, env.player)); verify(other, never()).clearModel();
        plates.remove(new FurnitureBreakEvent(plate.furniture, env.player)); verify(display).clearModel(); verify(meat, never()).clearModel();
        plates.remove(new FurnitureBreakEvent(bowl.furniture, env.player)); verify(soup).clearModel(); verify(ingredient).clearModel(); assertTrue(bowl.active.isEmpty());
    }

    @Test
    void saucesRecognizeFoodMetadataAndCleanUpAfterTheLastServingLeaves() {
        plate.place("display_1", env.stack(sauce(), Material.PAPER, 1)); plate.place("empty", null); plate.place("unknown", new ItemStack(Material.STONE));
        assertFalse(plates.hasSauce(plate.furniture));
        plate.place("food_2", env.stack(sauce(), Material.PAPER, 1)); assertTrue(plates.hasSauce(plate.furniture)); plate.active.remove("food_2");
        plate.place("sauce", new ItemStack(Material.STONE)); plate.place("food_item", null);
        var take = new FurnitureSlotItemTakeEvent(env.player, plate.furniture, plate.define("food_item"), new ItemStack(Material.BEEF));
        plates.takeItem(take); assertTrue(plate.active.containsKey("sauce"), "Other active food slots still preserve the sauce visual");
        plate.active.remove("empty"); plate.active.remove("unknown"); plates.takeItem(take); assertFalse(plate.active.containsKey("sauce"));
        bowl.place("sauce", new ItemStack(Material.STONE)); plates.takeItem(new FurnitureSlotItemTakeEvent(env.player, bowl.furniture, bowl.define("food_item"), null));
        assertTrue(bowl.active.containsKey("sauce"));
        assertEquals("#185d15", PlateManager.sauceColour("#185d15", "ignored")); assertNull(PlateManager.sauceColour(null, "plain"));
    }

    @Test
    void sauceAppliesToEveryFoodAndStoresItsVisualWithoutReplacingDecorations() {
        PlacedSlot first = plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
        PlacedSlot second = plate.place("food_2", env.stack(kitchen.food("carrot", "vegetable"), Material.CARROT, 1));
        PlacedSlot decoration = plate.place("display_1", new ItemStack(Material.STONE)); plate.place("empty", null); plate.place("unknown", new ItemStack(Material.STONE));
        ItemStack full = env.hold(ladle(sauce())); var event = plate.interact(); plates.interact(event);
        assertTrue(event.isCancelled()); assertEquals(Material.STICK, env.player.getInventory().getItemInMainHand().getType());
        assertTrue(env.resolve(first.getCurrentItem()).hasSauce()); assertTrue(env.resolve(second.getCurrentItem()).hasSauce());
        assertEquals(full.getItemMeta().getDisplayName(), env.resolve(first.getCurrentItem()).getSauceName());
        verify(decoration, never()).forceModel(any()); assertTrue(plate.active.containsKey("sauce")); verify(env.manager).markDirty(plate.furniture);
        ItemStack duplicate = env.hold(ladle(sauce())); plates.interact(plate.interact()); assertEquals(duplicate, env.player.getInventory().getItemInMainHand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void aFailedSauceUpdatePreservesBothFoodsAndTheFullLadle(String failure) {
        ItemStack first = env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1), second = env.stack(kitchen.food("carrot", "vegetable"), Material.CARROT, 1);
        PlacedSlot a = plate.place("food_item", first), b = plate.place("food_2", second); ItemStack full = env.hold(ladle(sauce()));
        ItemStack invalid = failure.equals("air") ? new ItemStack(Material.AIR) : null;
        kitchen.updater.when(() -> ItemUpdater.applyItemUpdate(eq(second), any(), any())).thenReturn(invalid);
        plates.interact(plate.interact());
        assertEquals(full, env.player.getInventory().getItemInMainHand()); assertSame(first, a.getCurrentItem()); assertSame(second, b.getCurrentItem());
        assertFalse(plate.active.containsKey("sauce")); verify(env.manager, never()).markDirty(plate.furniture);
    }

    @Test
    void anEmptySaucePlaceholderReceivesTheNewVisual() {
        plate.place("sauce", null); plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
        env.hold(ladle(sauce())); plates.interact(plate.interact());
        assertNotNull(plate.active.get("sauce").getCurrentItem()); assertTrue(plates.hasSauce(plate.furniture));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plate", "bowl", "plate-air", "bowl-air"})
    void anUnavailableEmptyLadleNeverConsumesTheHeldServing(String target) {
        boolean onPlate = target.startsWith("plate"); Station holder = onPlate ? plate : bowl;
        plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
        ItemStack full = env.hold(onPlate ? ladle(sauce()) : soup());
        when(env.api.getCreator().getItemFromPath("tool:ladle")).thenReturn(target.endsWith("-air") ? new ItemStack(Material.AIR) : null);
        plates.interact(holder.interact()); assertEquals(full, env.player.getInventory().getItemInMainHand());
    }

    @Test
    void sauceCanUpdateFoodEvenWhenTheOptionalVisualSlotIsUnavailable() {
        for (boolean missingType : List.of(false, true)) {
            plate.active.clear(); PlacedSlot food = plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
            plate.definitions.remove("sauce"); if (missingType) when(plate.furniture.getType()).thenReturn(null);
            env.hold(ladle(sauce())); plates.interact(plate.interact());
            assertTrue(env.resolve(food.getCurrentItem()).hasSauce()); assertFalse(plate.active.containsKey("sauce"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void unavailableOptionalSauceVisualDoesNotPreventApplyingSauceToFood(String failure) {
        PlacedSlot food = plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
        when(env.api.getCreator().getItemFromPath("model.sauce")).thenReturn(failure.equals("null") ? null : new ItemStack(Material.AIR));
        env.hold(ladle(sauce())); plates.interact(plate.interact());
        assertTrue(env.resolve(food.getCurrentItem()).hasSauce()); assertFalse(plate.active.containsKey("sauce"));
        assertEquals(Material.STICK, env.player.getInventory().getItemInMainHand().getType()); verify(env.manager).markDirty(plate.furniture);
        ItemStack duplicate = env.hold(ladle(sauce())); plates.interact(plate.interact());
        assertEquals(duplicate, env.player.getInventory().getItemInMainHand(), "Sauced food still rejects a second ladle when its optional visual is unavailable");
    }

    @Test
    void soupCopiesDeclaredIngredientsSpreadsThemAndKeepsExistingDecorations() {
        PlacedSlot existing = bowl.place("input_2", new ItemStack(Material.POTATO)); ItemStack full = env.hold(soup());
        var event = bowl.interact(); plates.interact(event); assertTrue(event.isCancelled());
        assertEquals(Material.STICK, env.player.getInventory().getItemInMainHand().getType());
        assertEquals(full, bowl.active.get("food_item").getCurrentItem()); assertFalse(bowl.active.containsKey("undeclared"));
        verify(existing, never()).forceModel(any()); assertEquals(Material.CARROT, bowl.active.get("input_1").getCurrentItem().getType());
        verify(bowl.active.get("input_1")).applyDisplayData(argThat(data -> Math.abs(data.getzPos() + 0.12f) < 0.001));
        verify(bowl.active.get("liquid"), never()).applyDisplayData(any()); verify(env.manager).markDirty(bowl.furniture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"occupied", "missing-slot", "missing-type"})
    void soupCannotOverwriteAnExistingServingOrDisappearIntoAnUnusableBowl(String failure) {
        ItemStack original = soup();
        if (failure.equals("occupied")) bowl.place("food_item", original);
        if (failure.equals("missing-slot")) bowl.definitions.remove("food_item");
        if (failure.equals("missing-type")) when(bowl.furniture.getType()).thenReturn(null);
        ItemStack full = env.hold(soup()); plates.interact(bowl.interact());
        assertEquals(full, env.player.getInventory().getItemInMainHand());
        if (failure.equals("occupied")) assertSame(original, bowl.active.get("food_item").getCurrentItem());
        else assertTrue(bowl.active.isEmpty());
    }

    @Test
    void bowlIngredientPositionsAreDistinctAndUnsupportedSlotNamesHaveNoOffset() {
        for (int index = 1; index <= 5; index++) {
            var data = BowlIngredientLayout.offsetFor("input_" + index);
            assertEquals(0.12, Math.hypot(data.getxPos(), data.getzPos()), 0.001); assertEquals(index - 1, BowlIngredientLayout.inputIndex("input_" + index));
        }
        for (String id : new String[] {null, "food_item", "input_0", "input_6", "input_bad"}) assertNull(BowlIngredientLayout.offsetFor(id));
    }

    @Test
    void furnitureInteractionIgnoresNonMealsAndAddsFoodDisplayPosesOnlyToCookingFurniture() {
        plates.interact(plate.interact()); env.hold(new ItemStack(Material.STONE)); plates.interact(plate.interact()); plates.interact(bowl.interact());
        env.hold(env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1));
        var plateEvent = plate.interact(); plates.interact(plateEvent); assertFalse(plateEvent.isCancelled());
        var bowlEvent = bowl.interact(); plates.interact(bowlEvent); assertFalse(bowlEvent.isCancelled());
        Station chair = kitchen.station("chair"); when(chair.type.getItemPath()).thenReturn("other:chair");
        var ignored = chair.add("food", env.player.getInventory().getItemInMainHand()); plates.addItem(ignored); assertNull(ignored.getDisplayData());
        var plain = plate.add("food_item", new ItemStack(Material.STONE)); plates.addItem(plain); assertNull(plain.getDisplayData());
        var food = plate.add("food_item", env.player.getInventory().getItemInMainHand()); plates.addItem(food); assertNotNull(food.getDisplayData());
    }

    @Test
    void publicNullSlotEntriesDoNotCrashMealMaintenanceOrServing() {
        plate.furniture.getActiveSlots().put("missing", null);
        assertDoesNotThrow(() -> plates.update(plate.furniture)); assertDoesNotThrow(() -> plates.clear(plate.furniture));
        assertFalse(plates.hasSauce(plate.furniture));
        env.hold(ladle(sauce())); assertDoesNotThrow(() -> plates.interact(plate.interact()));
        assertEquals(Material.PAPER, env.player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void aNullOptionalSauceSlotDoesNotCrashAfterApplyingSauce() {
        plate.furniture.getActiveSlots().put("sauce", null);
        PlacedSlot food = plate.place("food_item", env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1)); env.hold(ladle(sauce()));
        assertDoesNotThrow(() -> plates.interact(plate.interact())); assertTrue(env.resolve(food.getCurrentItem()).hasSauce());
        assertEquals(Material.STICK, env.player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void aNullBowlFoodSlotIsRecreatedWhenSoupIsServed() {
        bowl.furniture.getActiveSlots().put("food_item", null); ItemStack full = env.hold(soup());
        assertDoesNotThrow(() -> plates.interact(bowl.interact()));
        assertEquals(Material.STICK, env.player.getInventory().getItemInMainHand().getType());
        assertEquals(full, bowl.active.get("food_item").getCurrentItem()); assertEquals(Material.CARROT, bowl.active.get("input_1").getCurrentItem().getType());
        verify(env.manager).markDirty(bowl.furniture);
    }

    @Test
    void missingFoodModelLeavesTheFurnitureDefaultDisplayUnchanged() {
        FoodItem unmodeled = kitchen.food("unmodeled", "meat"); unmodeled.setModel(null);
        var event = plate.add("food_item", env.stack(unmodeled, Material.BEEF, 1)); plates.addItem(event); assertNull(event.getDisplayData());
    }

    @Test
    void cancelledPickupsPreserveTheProtectedWorldItem() {
        env.server.getPluginManager().registerEvents(conversions, org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("MealManagerEvents"));
        var event = pickup(new ItemStack(Material.WHEAT, 3)); event.setCancelled(true); env.server.getPluginManager().callEvent(event);
        verify(event.getItem(), never()).remove(); assertTrue(env.player.getInventory().isEmpty());
    }

    @Test
    void ordinaryPickupConversionPreservesAmountAndUsesFarmOrOriginQuality() {
        var farm = pickup(new ItemStack(Material.WHEAT, 4)); conversions.pickup(farm);
        assertTrue(farm.isCancelled()); verify(farm.getItem()).remove(); assertEquals(1, builtQuality); assertEquals(4, env.player.getInventory().getItem(0).getAmount());
        env.player.getInventory().clear(); var other = pickup(new ItemStack(Material.BEEF, 2)); conversions.pickup(other);
        assertTrue(other.isCancelled()); assertEquals(3, builtQuality); assertEquals(2, env.player.getInventory().getItem(0).getAmount());
    }

    @Test
    void legacyAndVanillaFishConversionsTakePriorityAndDropInventoryLeftovers() {
        ItemStack raw = new ItemStack(Material.COD, 3), legacyOutput = env.stack(kitchen.food("legacy_fish", "fish"), Material.SALMON, 1);
        legacy.when(() -> LegacyFishConversion.convert(env.player, raw)).thenReturn(legacyOutput);
        var old = pickup(raw); conversions.pickup(old); assertTrue(old.isCancelled()); assertEquals(Material.SALMON, env.player.getInventory().getItem(0).getType());
        env.player.getInventory().clear(); legacy.when(() -> LegacyFishConversion.convert(any(), any())).thenReturn(null);
        seafood.when(() -> SeafoodWholeItems.build(any(), any(), anyInt())).thenReturn(pickupOutput.clone());
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(any(), any())).thenAnswer(inv -> inv.getArgument(1));
            var vanilla = pickup(raw); conversions.pickup(vanilla); assertTrue(vanilla.isCancelled());
        }
        assertEquals(1, env.dropped.size()); assertEquals(3, env.dropped.getFirst().getAmount());
    }

    @Test
    void unsupportedPickupsMissingParsersAndCustomFishModelsStayInTheWorld() {
        var known = pickup(pickupOutput); conversions.pickup(known); verify(known.getItem(), never()).remove();
        var nonplayer = new EntityPickupItemEvent(mock(LivingEntity.class), pickup(new ItemStack(Material.WHEAT)).getItem(), 0);
        conversions.pickup(nonplayer); assertFalse(nonplayer.isCancelled());
        var unknown = pickup(new ItemStack(Material.STONE)); conversions.pickup(unknown); assertFalse(unknown.isCancelled());
        var unavailableFish = pickup(new ItemStack(Material.COD)); conversions.pickup(unavailableFish); assertFalse(unavailableFish.isCancelled());
        // Bukkit permits a vanilla stack without metadata; MockBukkit otherwise eagerly creates it.
        ItemStack plainFish = spy(new ItemStack(Material.COD)); doReturn(false).when(plainFish).hasItemMeta();
        var noMetadata = pickup(plainFish); conversions.pickup(noMetadata); assertFalse(noMetadata.isCancelled());
        ItemStack custom = new ItemStack(Material.COD); var meta = custom.getItemMeta(); LegacyModelData.set(meta, 7); custom.setItemMeta(meta);
        var customFish = pickup(custom); conversions.pickup(customFish); assertFalse(customFish.isCancelled());
        parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        var missing = pickup(new ItemStack(Material.WHEAT)); conversions.pickup(missing); assertFalse(missing.isCancelled());
        parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
        var empty = pickup(new ItemStack(Material.WHEAT)); conversions.pickup(empty); assertFalse(empty.isCancelled());
    }

    @Test
    void pickedUpFoodStacksOntoTheSameFoodThatOnlyAgedDifferently() {
        ItemStack held = age(pickupOutput, 100L, "freshness.1"); held.setAmount(3); env.player.getInventory().setItem(4, held);
        ItemStack ground = age(pickupOutput, 900L, "freshness.4"); ground.setAmount(2);
        var event = pickup(ground); conversions.pickup(event);
        assertTrue(event.isCancelled()); verify(event.getItem()).remove();
        assertEquals(5, env.player.getInventory().getItem(4).getAmount()); assertNull(env.player.getInventory().getItem(0));
    }

    @Test
    void pickedUpFoodThatDoesNotAllFitStaysOnTheGroundWithTheRest() {
        ItemStack ground = age(pickupOutput, 900L, "freshness.4"); ground.setAmount(5); ItemStack rest = ground.clone(); rest.setAmount(2);
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.hasStackFor(any(), any())).thenReturn(true);
            delivery.when(() -> InventoryAdder.addItem(any(), any())).thenReturn(rest);
            var event = pickup(ground); conversions.pickup(event);
            assertTrue(event.isCancelled()); verify(event.getItem()).setItemStack(rest); verify(event.getItem(), never()).remove();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air", "legacy-air", "fish-air"})
    void unusablePickupReplacementsKeepTheOriginalItemEntity(String failure) {
        ItemStack source = new ItemStack(failure.equals("fish-air") ? Material.COD : Material.WHEAT, 3);
        ItemStack invalid = failure.equals("null") ? null : new ItemStack(Material.AIR);
        if (failure.equals("legacy-air")) legacy.when(() -> LegacyFishConversion.convert(any(), any())).thenReturn(invalid);
        else if (failure.equals("fish-air")) seafood.when(() -> SeafoodWholeItems.build(any(), any(), anyInt())).thenReturn(invalid);
        else env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(ItemStack.class), anyInt())).thenReturn(invalid);
        var event = pickup(source); assertDoesNotThrow(() -> conversions.pickup(event));
        assertFalse(event.isCancelled()); verify(event.getItem(), never()).remove(); assertTrue(env.player.getInventory().isEmpty());
    }

    @Test
    void inventoryOpeningNormalizesOnlyAgingAndPreservesStackAmountsAndCarveState() {
        Inventory top = env.server.createInventory(null, 9); ItemStack base = pickupOutput.clone();
        ItemStack older = age(base, 100L, "freshness.1"), newer = age(base, 900L, "freshness.4");
        older.setAmount(3); newer.setAmount(2); top.setItem(0, newer); env.player.getInventory().setItem(0, older);
        ItemStack carved = base.clone(); var meta = carved.getItemMeta(); meta.getPersistentDataContainer().set(Keys.CARVE_REMAINING, PersistentDataType.INTEGER, 2); carved.setItemMeta(meta);
        top.setItem(2, carved); top.setItem(3, new ItemStack(Material.STONE));
        conversions.onOpen(opening(top));
        assertEquals(3, env.player.getInventory().getItem(0).getAmount()); assertEquals(2, top.getItem(0).getAmount());
        assertEquals(900L, env.player.getInventory().getItem(0).getItemMeta().getPersistentDataContainer().get(Keys.LAST_UPDATE, PersistentDataType.LONG));
        assertEquals(carved, top.getItem(2));
        assertTrue(ConversionManager.sameApartFromAging(older, newer)); assertFalse(ConversionManager.sameApartFromAging(base, carved));
        assertTrue(ConversionManager.sameApartFromAging(new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
    }

    @Test
    void virtualPluginInventoriesAreSkippedButBlockAndEntityHoldersAreSupported() {
        Inventory custom = env.server.createInventory(mock(InventoryHolder.class), 9); custom.setItem(0, pickupOutput);
        env.player.getInventory().setItem(0, age(pickupOutput, 500L, "freshness.1"));
        ItemStack before = env.player.getInventory().getItem(0).clone(); conversions.onOpen(opening(custom)); assertEquals(before, env.player.getInventory().getItem(0));
        for (InventoryHolder holder : List.<InventoryHolder>of((InventoryHolder) mock(BlockState.class, withSettings().extraInterfaces(InventoryHolder.class)), env.player)) {
            Inventory inventory = env.server.createInventory(holder, 9); inventory.setItem(0, pickupOutput); conversions.onOpen(opening(inventory));
            assertEquals(pickupOutput.getType(), inventory.getItem(0).getType());
        }
    }

    ItemStack age(ItemStack source, long timestamp, String tags) {
        ItemStack result = source.clone(); var meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.LAST_UPDATE, PersistentDataType.LONG, timestamp);
        meta.getPersistentDataContainer().set(Keys.AGE_REMAINDER, PersistentDataType.STRING, "freshness=0.5");
        meta.getPersistentDataContainer().set(Keys.TAGS, PersistentDataType.STRING, tags);
        meta.getPersistentDataContainer().set(Keys.LORE_INDEX_MAP, PersistentDataType.STRING, "aging=0");
        meta.setLore(List.of("Different aging lore " + timestamp)); result.setItemMeta(meta); return result;
    }

    public static final class Kitchen implements AutoCloseable {
        public final Workshop env = new Workshop();
        public final Map<UUID, Furniture> placed = new LinkedHashMap<>();
        public final MockedStatic<ItemUpdater> updater;
        final String oldPlate = FurnitureCache.plate, oldBowl = FurnitureCache.bowl, oldLadle = ItemCache.ladle;
        public Kitchen() {
            FurnitureCache.plate = "plate"; FurnitureCache.bowl = "bowl"; ItemCache.ladle = "tool:ladle";
            when(env.manager.getPlacedFurniture()).thenReturn(placed);
            when(env.api.getCreator().getItemFromPath("tool:ladle")).thenAnswer(inv -> new ItemStack(Material.STICK));
            updater = env.scoped(mockStatic(ItemUpdater.class));
            updater.when(() -> ItemUpdater.updateItem(any(), any(), any())).thenAnswer(inv -> updated(inv.getArgument(0), inv.getArgument(1)));
            updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(inv -> updated(inv.getArgument(0), inv.getArgument(1)));
            var modelData = env.scoped(mockStatic(LegacyModelData.class));
            modelData.when(() -> LegacyModelData.has(any())).thenAnswer(inv -> ((org.bukkit.inventory.meta.ItemMeta) inv.getArgument(0)).hasCustomModelData());
            modelData.when(() -> LegacyModelData.get(any())).thenAnswer(inv -> ((org.bukkit.inventory.meta.ItemMeta) inv.getArgument(0)).getCustomModelData());
            modelData.when(() -> LegacyModelData.set(any(), anyInt())).thenAnswer(inv -> {
                ((org.bukkit.inventory.meta.ItemMeta) inv.getArgument(0)).setCustomModelData(inv.getArgument(1)); return null;
            });
            env.scoped(mockStatic(CategoryDictionary.class)).when(() -> CategoryDictionary.getSauceItemPath(any(), anyInt())).thenReturn("model.sauce");
        }
        ItemStack updated(ItemStack source, FoodItem food) { return env.stack(new FoodItem(food), source.getType(), source.getAmount()); }
        public FoodItem food(String id, String category) { FoodItem food = env.food(id, "Cow", 3); food.setCategory(category); return food; }
        public Station station(String kind) {
            Station station = env.station(kind); placed.put(station.id, station.furniture); when(station.type.getItemPath()).thenReturn("ia.tfmc_cooking:" + kind);
            doAnswer(inv -> {
                String id = inv.getArgument(0); PlacedSlot existing = station.active.get(id);
                return existing != null ? existing : station.place(id, null);
            }).when(station.furniture).getOrCreatePlacedSlot(anyString());
            return station;
        }
        @Override public void close() {
            env.server.getScheduler().cancelTasks(Cooking.plugin); env.close();
            FurnitureCache.plate = oldPlate; FurnitureCache.bowl = oldBowl; ItemCache.ladle = oldLadle;
        }
    }
}
