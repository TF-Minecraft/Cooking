package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.crafting.CraftingStation;
import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.CraftingStationLoader;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class CraftingManagerCoverageTest {
    Environment env;
    CraftingManager manager;
    @BeforeEach void open() { env = new Environment(); manager = new CraftingManager(); }
    @AfterEach void close() { env.close(); }

    void registerBoard() {
        CraftingStationLoader.get().add(new CraftingStation("board", env.config(1, false, "tool:knife")));
    }

    void firePit() {
        env.firePit = true;
        env.define("content"); env.define("turner");
        when(env.type.getId()).thenReturn("fire_pit");
    }

    FoodItem roast() { return env.food("roast", "Beef", Method.FIRE_PIT); }

    @Test
    void stationCacheReusesLiveFurnitureAndRebuildsAReplacementWithTheSameUuid() {
        assertNull(manager.getOrCreateStation(env.furniture));
        registerBoard();
        env.place("input_1", env.raw(1));
        CraftingStation original = manager.getOrCreateStation(env.furniture);
        assertNotNull(original);
        assertSame(original, manager.getOrCreateStation(env.furniture));
        assertEquals(1, original.getSlots().size());
        Furniture replacement = mock(Furniture.class);
        UUID stationId = env.furniture.getEntityId();
        when(replacement.getEntityId()).thenReturn(stationId);
        when(replacement.getType()).thenReturn(env.type);
        when(replacement.getActiveSlot(anyString())).thenReturn(Optional.empty());
        CraftingStation rebuilt = manager.getOrCreateStation(replacement);
        assertNotSame(original, rebuilt);
        assertSame(replacement, rebuilt.getFurniture());
        assertTrue(rebuilt.getSlots().isEmpty());
        assertEquals(1, manager.stations.size());
        Furniture noType = mock(Furniture.class);
        assertNull(manager.getOrCreateStation(noType));
    }

    @Test
    void rebuildingDiscardsStaleStationsAndIgnoresNonStations() {
        registerBoard();
        Furniture noType = mock(Furniture.class);
        Furniture unknown = mock(Furniture.class);
        FurnitureType type = mock(FurnitureType.class);
        when(unknown.getType()).thenReturn(type);
        when(type.getId()).thenReturn("chair");
        assertNull(manager.getOrCreateStation(unknown));
        env.placed.put(UUID.randomUUID(), noType);
        env.placed.put(UUID.randomUUID(), unknown);
        manager.stations.put(UUID.randomUUID(), env.station(1, false));
        manager.rebuildStations();
        assertEquals(1, manager.stations.size());
        assertTrue(manager.stations.containsKey(env.furniture.getEntityId()));
        CraftingStation station = manager.stations.get(env.furniture.getEntityId());
        manager.resumeLoadedStations();
        assertSame(station, manager.stations.get(env.furniture.getEntityId()));
    }

    @Test
    void chunkUnloadRemovesOnlyThatChunkAndReloadWaitsOneTickForFurniture() {
        registerBoard();
        manager.resumeLoadedStations();
        Chunk elsewhere = mock(Chunk.class);
        when(elsewhere.getWorld()).thenReturn(env.world);
        when(elsewhere.getX()).thenReturn(5);
        when(elsewhere.getZ()).thenReturn(5);
        manager.onChunkUnload(new ChunkUnloadEvent(elsewhere));
        assertEquals(1, manager.stations.size());
        manager.onChunkUnload(new ChunkUnloadEvent(env.chunk));
        assertTrue(manager.stations.isEmpty());
        Furniture foreign = mock(Furniture.class);
        Location foreignLocation = mock(Location.class);
        when(foreign.getLoc()).thenReturn(foreignLocation);
        when(foreignLocation.getChunk()).thenReturn(elsewhere);
        env.placed.put(UUID.randomUUID(), foreign);
        manager.onChunkLoad(new ChunkLoadEvent(env.chunk, false));
        assertTrue(manager.stations.isEmpty());
        env.place("input_2", env.raw(1));
        env.server.getScheduler().performOneTick();
        assertEquals(1, manager.stations.size());
        assertTrue(manager.stations.get(env.furniture.getEntityId()).getSlots().containsKey("input_2"));
    }

    @Test
    void stationEventsForwardAddTakeAndCraftAndRetainCancelledBreaks() {
        registerBoard();
        var add = env.addEvent("input_1", env.raw(1));
        manager.furnitureInteract(add);
        CraftingStation station = manager.stations.get(env.furniture.getEntityId());
        assertSame(add.getItem(), station.getSlots().get("input_1"));
        manager.furnitureInteract(new FurnitureInteractEvent(env.player, env.furniture));
        manager.furnitureTakeInteract(env.takeEvent("input_1", add.getItem()));
        assertTrue(station.getSlots().isEmpty());
        env.add(station, "input_1", env.raw(1));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        var craft = new FurnitureBreakEvent(env.furniture, env.player);
        manager.furnitureBreak(craft);
        assertTrue(craft.isCancelled());
        assertSame(station, manager.stations.get(env.furniture.getEntityId()));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        var remove = new FurnitureBreakEvent(env.furniture, env.player);
        manager.furnitureBreak(remove);
        assertFalse(remove.isCancelled());
        assertTrue(manager.stations.isEmpty());
        when(env.furniture.getType()).thenReturn(null);
        manager.furnitureBreak(new FurnitureBreakEvent(env.furniture, null));
    }

    @Test
    void unregisteredFurnitureEventsLeaveTheItemAndCacheUntouched() {
        var add = env.addEvent("input_1", env.raw(1));
        manager.furnitureInteract(add);
        manager.furnitureAddInteract(add);
        manager.furnitureTakeInteract(env.takeEvent("input_1", add.getItem()));
        manager.furnitureInteract(new FurnitureInteractEvent(env.player, env.furniture));
        manager.furniturePlace(new FurniturePlaceEvent(env.furniture, env.player));
        manager.furnitureBreak(new FurnitureBreakEvent(env.furniture, env.player));
        assertFalse(add.isCancelled());
        assertTrue(manager.stations.isEmpty());
    }

    @Test
    void firePitAdmissionRequiresHeatAndFoodWithAFirePitCookingMethod() {
        firePit();
        env.heated = false;
        var cold = env.addEvent("content", env.stack(roast(), Material.BEEF, 1));
        manager.furnitureInteract(cold);
        assertTrue(cold.isCancelled());
        verify(env.player).sendMessage("§cLight the campfire under the fire pit first.");
        env.heated = true;
        var plain = env.addEvent("content", new ItemStack(Material.STONE));
        manager.furnitureInteract(plain);
        assertTrue(plain.isCancelled());
        var unsupported = env.addEvent("content", env.raw(1));
        manager.furnitureInteract(unsupported);
        assertTrue(unsupported.isCancelled());
        verify(env.player, times(2)).sendMessage("§cOnly whole roasts can be cooked on a fire pit.");
        var accepted = env.addEvent("content", env.stack(roast(), Material.BEEF, 1));
        manager.furnitureInteract(accepted);
        assertFalse(accepted.isCancelled());
        var otherSlot = env.addEvent("turner", new ItemStack(Material.STICK));
        manager.furnitureInteract(otherSlot);
        assertFalse(otherSlot.isCancelled());
        assertTrue(manager.stations.isEmpty());
    }

    @Test
    void placingAFirePitCreatesTheTurnerAndOffsetsTheModelOnlyOnce() {
        firePit();
        PlacedSlot turner = env.place("turner", null);
        ItemDisplay display = env.display("turner", 2);
        manager.furniturePlace(new FurniturePlaceEvent(env.furniture, env.player));
        assertEquals(Material.STICK, turner.getCurrentItem().getType());
        assertEquals(0.25f, display.getTransformation().getTranslation().y);
        manager.furniturePlace(new FurniturePlaceEvent(env.furniture, env.player));
        assertEquals(0.25f, display.getTransformation().getTranslation().y);
        verify(display, times(1)).setTransformation(any());
        env.definitions.remove("turner");
        manager.furniturePlace(new FurniturePlaceEvent(env.furniture, env.player));
        when(env.furniture.getType()).thenReturn(null);
        manager.furniturePlace(new FurniturePlaceEvent(env.furniture, env.player));
        verify(turner, times(2)).forceModel(any());
    }

    @Test
    void contentDisplayIsUpdatedAfterPlacementAndCarriesTheCarveStage() {
        firePit();
        FoodItem roast = roast();
        roast.setCarveState("beef", 2, 3);
        ItemStack stack = env.stack(roast, Material.BEEF, 1);
        var add = env.addEvent("content", stack);
        manager.furnitureAddInteract(add);
        PlacedSlot content = env.place("content", stack);
        ItemDisplay display = env.display("content", 1);
        verify(content, never()).applyDisplayData(any());
        env.server.getScheduler().performOneTick();
        env.carve.verify(() -> CarvableRoastUtils.readCarveState(roast, stack));
        verify(content).applyDisplayData(any());
        assertEquals(0.25f, display.getTransformation().getTranslation().y);
        manager.furnitureAddInteract(env.addEvent("turner", new ItemStack(Material.STICK)));
        env.server.getScheduler().performOneTick();
        verify(content, times(1)).applyDisplayData(any());
    }

    @Test
    void pendingContentDisplayUpdateToleratesRemovedEmptyAndNonFoodSlots() {
        firePit();
        var add = env.addEvent("content", env.raw(1));
        manager.furnitureAddInteract(add);
        env.server.getScheduler().performOneTick();
        PlacedSlot empty = env.place("content", null);
        manager.furnitureAddInteract(add);
        env.server.getScheduler().performOneTick();
        verify(empty, never()).applyDisplayData(any());
        PlacedSlot plain = env.place("content", new ItemStack(Material.STONE));
        manager.furnitureAddInteract(add);
        env.server.getScheduler().performOneTick();
        verify(plain, never()).applyDisplayData(any());
        PlacedSlot food = env.place("content", env.stack(roast(), Material.BEEF, 1));
        manager.furnitureAddInteract(add);
        env.server.getScheduler().performOneTick();
        verify(food).applyDisplayData(any());
        UUID wrongEntity = UUID.randomUUID();
        when(food.getDisplayStandId()).thenReturn(wrongEntity);
        env.entities.put(wrongEntity, mock(Item.class));
        manager.furnitureAddInteract(add);
        env.server.getScheduler().performOneTick();
        verify(food, times(2)).applyDisplayData(any());
    }

    @Test
    void firePitControlsIgnoreMissesToolsEmptySpitsAndUnlitFires() {
        firePit();
        var miss = env.interact("missing");
        manager.furnitureInteract(miss);
        assertFalse(miss.isCancelled());
        var content = env.interact("content");
        manager.furnitureInteract(content);
        assertFalse(content.isCancelled());
        var empty = env.interact("turner");
        manager.furnitureInteract(empty);
        assertTrue(empty.isCancelled());
        FoodItem roast = roast();
        env.place("content", env.stack(roast, Material.BEEF, 1));
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        var holding = env.interact("turner");
        manager.furnitureInteract(holding);
        assertTrue(holding.isCancelled());
        assertEquals(0, roast.getCookData().getCurrentTime());
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        env.heated = false;
        manager.furnitureInteract(env.interact("turner"));
        verify(env.player).sendMessage("§cLight the campfire under the fire pit first.");
        assertEquals(0, roast.getCookData().getCurrentTime());
        env.updater.verifyNoInteractions();
    }

    @Test
    void turningAdvancesCookingOnceAndBlocksTakingContentUntilThePitBreaks() {
        firePit();
        FoodItem roast = roast();
        ItemStack original = env.stack(roast, Material.BEEF, 1);
        PlacedSlot content = env.place("content", original);
        manager.furnitureInteract(env.interact("turner"));
        assertTrue(roast.getCookData().isBeingCooked());
        assertEquals(1, roast.getCookData().getCurrentTime());
        assertNotSame(original, content.getCurrentItem());
        env.carve.verify(() -> CarvableRoastUtils.writeCarveState(content.getCurrentItem(), roast));
        manager.furnitureInteract(env.interact("turner"));
        assertEquals(1, roast.getCookData().getCurrentTime());
        var hitContent = env.interact("content");
        manager.furnitureInteract(hitContent);
        assertTrue(hitContent.isCancelled());
        var take = env.takeEvent("content", content.getCurrentItem());
        manager.furnitureTakeInteract(take);
        assertTrue(take.isCancelled());
        var other = env.takeEvent("turner", new ItemStack(Material.STICK));
        manager.furnitureTakeInteract(other);
        assertFalse(other.isCancelled());
        manager.furnitureBreak(new FurnitureBreakEvent(env.furniture, env.player));
        var afterBreak = env.takeEvent("content", content.getCurrentItem());
        manager.furnitureTakeInteract(afterBreak);
        assertFalse(afterBreak.isCancelled());
        manager.furnitureInteract(env.interact("turner"));
        assertEquals(2, roast.getCookData().getCurrentTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "plain", "unsupported", "failed-update"})
    void turningUnavailableFoodDoesNotReplaceTheStoredStack(String kind) {
        firePit();
        FoodItem food = kind.equals("unsupported") ? env.food("raw", "Beef") : roast();
        ItemStack stack = switch (kind) {
            case "null" -> null;
            case "plain" -> new ItemStack(Material.STONE);
            default -> env.stack(food, Material.BEEF, 1);
        };
        PlacedSlot content = env.place("content", stack);
        if (kind.equals("failed-update")) env.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        manager.furnitureInteract(env.interact("turner"));
        assertSame(stack, content.getCurrentItem());
        verify(content, never()).setCurrentItem(any());
        verify(content, never()).applyDisplayData(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "y", "z"})
    void rigidSpitAnimationRotatesAroundConfiguredAxisAndRestoresExactTransforms(String axis) {
        firePit();
        ItemCache.firePitSpinAxis = axis;
        env.place("content", env.stack(roast(), Material.BEEF, 1));
        env.place("turner", new ItemStack(Material.STICK));
        ItemDisplay content = env.display("content", 1);
        ItemDisplay turner = env.display("turner", 2);
        Transformation parentTransform = new Transformation(new Vector3f(), new Quaternionf().rotateY(0.4f), new Vector3f(1), new Quaternionf());
        env.display(env.furniture.getEntityId(), env.furniture.getLoc(), parentTransform);
        Transformation sourceTransform = new Transformation(new Vector3f(0.1f, 0.3f, -0.2f), new Quaternionf().rotateX(0.2f),
                new Vector3f(0.7f, 0.8f, 0.9f), new Quaternionf().rotateZ(0.1f));
        content.setTransformation(sourceTransform);
        Location contentStart = content.getLocation();
        Location turnerStart = turner.getLocation();
        manager.furnitureInteract(env.interact("turner"));
        env.server.getScheduler().performTicks(11);
        assertNotEquals(sourceTransform.getLeftRotation(), content.getTransformation().getLeftRotation());
        assertEquals(sourceTransform.getTranslation(), content.getTransformation().getTranslation());
        assertEquals(sourceTransform.getScale(), content.getTransformation().getScale());
        assertEquals(sourceTransform.getRightRotation(), content.getTransformation().getRightRotation());
        Location quarterTurn = switch (axis) {
            case "x" -> new Location(env.world, 1 - Math.sin(0.4) / 2, 64.5, 1 - Math.cos(0.4) / 2);
            case "z" -> new Location(env.world, 1 + Math.cos(0.4) / 2, 64.5, 1 - Math.sin(0.4) / 2);
            default -> contentStart;
        };
        assertEquals(0, quarterTurn.distance(content.getLocation()), 0.00001, "A quarter turn follows the parent-rotated axis");
        assertEquals(1.0, content.getLocation().distance(turner.getLocation()), 0.00001, "The spit and roast move as one rigid assembly");
        env.server.getScheduler().performTicks(35);
        assertEquals(contentStart, content.getLocation());
        assertEquals(turnerStart, turner.getLocation());
        assertEquals(sourceTransform, content.getTransformation());
        assertEquals(0.25f, turner.getTransformation().getTranslation().y);
        clearInvocations(content, turner);
        env.server.getScheduler().performTicks(3);
        verifyNoInteractions(content, turner);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disappearingDisplayStopsTheAnimationImmediately(boolean removeContent) {
        firePit();
        env.place("content", env.stack(roast(), Material.BEEF, 1));
        env.place("turner", new ItemStack(Material.STICK));
        ItemDisplay content = env.display("content", 1);
        ItemDisplay turner = env.display("turner", 2);
        env.display(env.furniture.getEntityId(), env.furniture.getLoc(), env.identity());
        manager.furnitureInteract(env.interact("turner"));
        when((removeContent ? content : turner).isDead()).thenReturn(true);
        env.server.getScheduler().performTicks(2);
        verify(content, never()).teleport(any(Location.class));
        verify(turner, never()).teleport(any(Location.class));
        clearInvocations(content, turner);
        env.server.getScheduler().performTicks(3);
        verifyNoInteractions(content, turner);
    }
}
