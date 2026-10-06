package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.manager.MealManagersCoverageTest.Kitchen;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;

class CookingAndTagManagersCoverageTest {
    Kitchen kitchen;
    Workshop env;
    CookingManager cooking;
    TagManager tags;
    String oldPan, oldSaucepan, oldPot;
    boolean heated;

    @BeforeEach void open() {
        kitchen = new Kitchen(); env = kitchen.env; cooking = new CookingManager(); tags = new TagManager(); heated = true;
        oldPan = FurnitureCache.fryingPan; oldSaucepan = FurnitureCache.saucePan; oldPot = FurnitureCache.pot;
        FurnitureCache.fryingPan = "pan"; FurnitureCache.saucePan = "saucepan"; FurnitureCache.pot = "pot";
        env.scoped(mockStatic(HeatSources.class)).when(() -> HeatSources.stationHasHeat(any())).thenAnswer(inv -> heated);
        env.cache.when(() -> ItemCache.isButter(any())).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.GOLD_NUGGET;
        });
        env.cache.when(() -> ItemCache.getColour(any())).thenReturn("aa5500");
        env.scoped(mockStatic(IngredientConverter.class)).when(() -> IngredientConverter.convertIfNeeded(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any(), anyBoolean())).thenAnswer(inv -> kitchen.updated(inv.getArgument(0), inv.getArgument(1)));
    }

    @AfterEach void close() {
        kitchen.close(); FurnitureCache.fryingPan = oldPan; FurnitureCache.saucePan = oldSaucepan; FurnitureCache.pot = oldPot;
    }

    Station station(String kind) {
        Station station = kitchen.station(kind);
        for (String slot : List.of("input_1", "input_2", "butter", "liquid")) station.define(slot);
        return station;
    }
    FoodItem raw(Method method) {
        FoodItem food = kitchen.food("steak", "meat"); food.addOrModifyTrack(TrackLoader.getByString("cooked"));
        food.getCookData().getParameters().put(method, new CookParameter(1, 2, 20)); return new FoodItem(food);
    }
    Chunk chunk(int x) {
        Chunk chunk = mock(Chunk.class); when(chunk.getWorld()).thenReturn(env.world); when(chunk.getX()).thenReturn(x); when(chunk.getZ()).thenReturn(0); return chunk;
    }
    PlayerInteractEvent cauldron(Action action, Material type) {
        Block block = type == null ? null : mock(Block.class); if (block != null) when(block.getType()).thenReturn(type);
        return new PlayerInteractEvent(env.player, action, env.player.getInventory().getItemInMainHand(), block, BlockFace.UP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pan", "saucepan", "pot"})
    void restoredStationsSelectTheRightMethodAndContinueCookingOnTheTimer(String kind) {
        Method method = switch (kind) { case "pan" -> Method.FRYING_PAN; case "saucepan" -> Method.SAUCEPAN; default -> Method.POT; };
        Station station = station(kind); FoodItem food = raw(method); var slot = station.place("input_2", env.stack(food, Material.BEEF, 1));
        cooking.resumeLoadedStations(); assertEquals(method, food.getCookData().getCurrentMethod());
        cooking.start(); env.server.getScheduler().performTicks(40);
        assertEquals(2, food.getCookData().getCurrentTime()); assertEquals(1, food.getTagTrack("cooked").getValue()); verify(slot).forceModel(any());
        heated = false; env.server.getScheduler().performTicks(20); assertEquals(2, food.getCookData().getCurrentTime());
    }

    @Test
    void resumingSkipsMissingTypesUnknownFurnitureAndEmptyKnownStations() {
        kitchen.placed.put(UUID.randomUUID(), null); Station broken = station("pan"); when(broken.furniture.getType()).thenReturn(null);
        Station chair = station("chair"); chair.place("input_1", env.stack(raw(Method.FRYING_PAN), Material.BEEF, 1));
        Station empty = station("pot"); assertDoesNotThrow(() -> cooking.resumeLoadedStations());
        verify(empty.furniture, never()).getActiveSlot(anyString());
        var ignored = chair.add("input_1", new ItemStack(Material.STONE)); cooking.addItem(ignored); assertFalse(ignored.isCancelled());
        var take = new FurnitureSlotItemTakeEvent(env.player, chair.furniture, chair.define("input_1"), new ItemStack(Material.STONE));
        cooking.takeItem(take); assertFalse(take.isCancelled()); cooking.interact(chair.interact()); cooking.breakEvent(new FurnitureBreakEvent(chair.furniture, env.player));
    }

    @Test
    void addAndTakeEventsReuseTheReferenceAndPreservePendingIngredients() {
        Station pan = station("pan"); FoodItem food = raw(Method.FRYING_PAN); ItemStack item = env.stack(food, Material.BEEF, 1);
        var add = pan.add("input_1", item); cooking.addItem(add); assertFalse(add.isCancelled()); assertNotNull(add.getDisplayData());
        cooking.interact(pan.interact()); cooking.start(); env.server.getScheduler().performTicks(20);
        assertEquals(1, food.getCookData().getCurrentTime(), "Reusing the reference must keep a just-added ingredient before furniture commits the display");
        var take = new FurnitureSlotItemTakeEvent(env.player, pan.furniture, pan.define("input_1"), item);
        cooking.takeItem(take); assertFalse(take.isCancelled()); assertEquals("steak", env.resolve(take.getItem()).getId());
        env.server.getScheduler().performTicks(20); assertEquals(1, food.getCookData().getCurrentTime());
    }

    @Test
    void interactionsReachTheFryingReferenceAndConsumeExactlyOneButter() {
        Station pan = station("pan"); ItemStack butter = env.hold(env.stack(kitchen.food("butter", "butter"), Material.GOLD_NUGGET, 3));
        var event = pan.interact(); cooking.interact(event); assertTrue(event.isCancelled()); assertEquals(2, butter.getAmount());
        assertEquals(1, pan.active.get("butter").getCurrentItem().getAmount());
        cooking.interact(pan.interact()); assertEquals(2, butter.getAmount(), "An existing butter secondary rejects another serving");
    }

    @Test
    void replacingFurnitureUnderTheSameUuidRebuildsTheReference() {
        Station old = station("pan"); FoodItem first = raw(Method.FRYING_PAN); old.place("input_1", env.stack(first, Material.BEEF, 1));
        cooking.resumeLoadedStations(); cooking.start(); env.server.getScheduler().performTicks(20); assertEquals(1, first.getCookData().getCurrentTime());
        Station replacement = station("pan"); when(replacement.furniture.getEntityId()).thenReturn(old.id);
        kitchen.placed.remove(replacement.id); kitchen.placed.put(old.id, replacement.furniture);
        FoodItem second = raw(Method.FRYING_PAN); replacement.place("input_2", env.stack(second, Material.BEEF, 1));
        cooking.interact(replacement.interact()); env.server.getScheduler().performTicks(20);
        assertEquals(1, first.getCookData().getCurrentTime()); assertEquals(1, second.getCookData().getCurrentTime());
    }

    @Test
    void unloadingStopsOnlyThatChunksTimersAndLoadingResumesOnTheNextTick() {
        Chunk near = chunk(0), far = chunk(2);
        when(env.world.getChunkAt(any(Location.class))).thenAnswer(inv -> ((Location) inv.getArgument(0)).getBlockX() < 16 ? near : far);
        Station pan = station("pan"), other = station("pan"); when(other.furniture.getLoc()).thenReturn(new Location(env.world, 33, 64, 1));
        FoodItem first = raw(Method.FRYING_PAN), second = raw(Method.FRYING_PAN);
        pan.place("input_1", env.stack(first, Material.BEEF, 1)); other.place("input_1", env.stack(second, Material.BEEF, 1));
        cooking.resumeLoadedStations(); cooking.start(); cooking.onChunkUnload(new ChunkUnloadEvent(near)); env.server.getScheduler().performTicks(20);
        assertEquals(0, first.getCookData().getCurrentTime()); assertEquals(1, second.getCookData().getCurrentTime());
        cooking.onChunkLoad(new ChunkLoadEvent(near, false)); env.server.getScheduler().performTicks(1);
        env.server.getScheduler().performTicks(19); assertEquals(1, first.getCookData().getCurrentTime()); assertEquals(2, second.getCookData().getCurrentTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pan", "saucepan", "pot"})
    void breakingTheStationClearsItsManagedStateAndStopsCooking(String kind) {
        Method method = switch (kind) { case "pan" -> Method.FRYING_PAN; case "saucepan" -> Method.SAUCEPAN; default -> Method.POT; };
        Station station = station(kind); FoodItem food = raw(method); station.place("input_1", env.stack(food, Material.BEEF, 1));
        if (kind.equals("pan")) station.place("butter", env.stack(kitchen.food("butter", "butter"), Material.GOLD_NUGGET, 1));
        cooking.resumeLoadedStations(); cooking.start(); cooking.breakEvent(new FurnitureBreakEvent(station.furniture, env.player));
        env.server.getScheduler().performTicks(20); assertEquals(0, food.getCookData().getCurrentTime()); assertFalse(station.active.containsKey("butter"));
        if (kind.equals("saucepan")) assertFalse(station.active.containsKey("input_1"));
        else assertTrue(station.active.containsKey("input_1"), "Frying and raw pot ingredients are left for furniture's normal item drop");
        cooking.breakEvent(new FurnitureBreakEvent(station.furniture, env.player));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sauce", "soup"})
    void cauldronsEmptyOnlyFilledFoodLadles(String category) {
        env.hold(env.stack(kitchen.food(category, category), Material.PAPER, 1));
        var wrongAction = cauldron(Action.LEFT_CLICK_BLOCK, Material.CAULDRON); cooking.empty(wrongAction); assertFalse(wrongAction.isCancelled());
        var wrongBlock = cauldron(Action.RIGHT_CLICK_BLOCK, Material.STONE); cooking.empty(wrongBlock); assertFalse(wrongBlock.isCancelled());
        var valid = cauldron(Action.RIGHT_CLICK_BLOCK, Material.CAULDRON); cooking.empty(valid); assertTrue(valid.isCancelled());
        assertEquals(Material.STICK, env.player.getInventory().getItemInMainHand().getType());
        cooking.empty(cauldron(Action.RIGHT_CLICK_BLOCK, Material.CAULDRON));
        env.hold(env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1)); var meat = cauldron(Action.RIGHT_CLICK_BLOCK, Material.CAULDRON);
        cooking.empty(meat); assertFalse(meat.isCancelled()); assertEquals(Material.BEEF, env.player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void anInteractionWithoutAClickedBlockDoesNotDiscardFoodOrThrow() {
        ItemStack full = env.hold(env.stack(kitchen.food("soup", "soup"), Material.PAPER, 1));
        assertDoesNotThrow(() -> cooking.empty(cauldron(Action.RIGHT_CLICK_BLOCK, null)));
        assertEquals(full, env.player.getInventory().getItemInMainHand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void unavailableEmptyLadlesLeaveTheFilledLadleIntact(String failure) {
        ItemStack full = env.hold(env.stack(kitchen.food("soup", "soup"), Material.PAPER, 1));
        when(env.api.getCreator().getItemFromPath("tool:ladle")).thenReturn(failure.equals("null") ? null : new ItemStack(Material.AIR));
        cooking.empty(cauldron(Action.RIGHT_CLICK_BLOCK, Material.CAULDRON)); assertEquals(full, env.player.getInventory().getItemInMainHand());
    }

    @Test
    void scanMatchingRequiresFoodMetadataAndRecognizesTheOwningPlayersHeldSlots() {
        assertFalse(tags.matches(null)); assertFalse(tags.matches(new ItemStack(Material.AIR))); assertFalse(tags.matches(new ItemStack(Material.STONE)));
        ItemStack food = env.stack(kitchen.food("steak", "meat"), Material.BEEF, 1); assertFalse(tags.matches(food));
        var meta = food.getItemMeta(); meta.getPersistentDataContainer().set(Keys.FOOD_ID, PersistentDataType.STRING, "steak"); food.setItemMeta(meta); assertTrue(tags.matches(food));
        var inventory = env.player.getInventory(); inventory.setHeldItemSlot(3);
        assertTrue(TagManager.isHeldSlot(env.player, inventory, 3)); assertTrue(TagManager.isHeldSlot(env.player, inventory, 40));
        assertFalse(TagManager.isHeldSlot(env.player, inventory, 4)); assertFalse(TagManager.isHeldSlot(null, inventory, 3));
        assertFalse(TagManager.isHeldSlot(env.player, null, 3)); assertFalse(TagManager.isHeldSlot(env.player, env.server.createInventory(null, 9), 3));
        assertFalse(TagManager.isHeldSlot(env.player, env.server.addPlayer().getInventory(), 3));
    }

    @Test
    void unchangedFoodAndNonFoodAreNotRewrittenButMissingLoreIsRebuilt() {
        Inventory inventory = env.server.createInventory(null, 9); ItemStack item = env.stack(kitchen.food("steak", "meat"), Material.BEEF, 2);
        inventory.setItem(0, item); tags.update(env.player, inventory, 0, new ItemStack(Material.STONE)); tags.update(env.player, inventory, 0, item);
        kitchen.updater.verify(() -> ItemUpdater.updateItem(any(), any(), any(), anyBoolean()), never()); assertEquals(item, inventory.getItem(0));
        kitchen.updater.when(() -> ItemUpdater.needsLoreRebuild(any(), anyBoolean())).thenReturn(true);
        tags.update(env.player, inventory, 0, item); kitchen.updater.verify(() -> ItemUpdater.updateItem(item, env.resolve(item), null, false));
        assertEquals(2, inventory.getItem(0).getAmount()); assertNotEquals(item, inventory.getItem(0));
    }

    @Test
    void changingFoodUsesHeldContextOnlyForThePlayersSelectedOrOffhandSlot() {
        MemoryConfiguration config = new MemoryConfiguration(); config.set("name", "Steak"); config.set("update", true);
        FoodItem food = new FoodItem("steak", config); ItemStack stack = env.stack(food, Material.BEEF, 2); var inventory = env.player.getInventory();
        inventory.setItem(0, stack); tags.update(env.player, inventory, 0, stack);
        kitchen.updater.verify(() -> ItemUpdater.updateItem(stack, food, null, true)); assertEquals(2, inventory.getItem(0).getAmount());
        tags.update(env.player, inventory, 1, stack); kitchen.updater.verify(() -> ItemUpdater.updateItem(stack, food, null, false));
        tags.update(env.player, inventory, 40, stack); assertEquals(2, inventory.getItemInOffHand().getAmount());
        tags.update(env.player, null, 0, stack); tags.update(env.player, inventory, -1, stack);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void failedTagUpdatesNeverDeleteTheExistingFood(String failure) {
        ItemStack source = env.stack(kitchen.food("steak", "meat"), Material.BEEF, 2); Inventory inventory = env.server.createInventory(null, 9); inventory.setItem(0, source);
        kitchen.updater.when(() -> ItemUpdater.needsLoreRebuild(any(), anyBoolean())).thenReturn(true);
        kitchen.updater.when(() -> ItemUpdater.updateItem(any(), any(), any(), anyBoolean())).thenReturn(failure.equals("null") ? null : new ItemStack(Material.AIR));
        tags.update(env.player, inventory, 0, source); assertEquals(source, inventory.getItem(0));
    }
}
