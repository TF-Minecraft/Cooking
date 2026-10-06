package net.tfminecraft.cooking.oven;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.baking.BakingTrayCoverageTest.Bakery;
import net.tfminecraft.cooking.baking.BakingTrayState;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotFurnitureAddEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotType;

class OvenCoverageTest {
    Bakery bakery;
    Workshop env;
    Station oven;
    Station cavity;
    OvenBurnManager burns;
    OvenHandler handler;
    String oldBottom, oldTop, oldWood, oldBurntWood, oldFire;
    int oldInterval;
    float oldFreshChance, oldBurntChance;

    @BeforeEach void open() {
        oldBottom = FurnitureCache.ovenBottom; oldTop = FurnitureCache.ovenTop;
        oldWood = ItemCache.ovenWoodModel; oldBurntWood = ItemCache.ovenWoodBurntModel; oldFire = ItemCache.ovenFireModel;
        oldInterval = ItemCache.ovenBurnIntervalTicks; oldFreshChance = ItemCache.ovenBurnChanceFresh; oldBurntChance = ItemCache.ovenBurnChanceBurnt;
        bakery = new Bakery(); env = bakery.env;
        FurnitureCache.ovenBottom = "oven_bottom"; FurnitureCache.ovenTop = "oven_top";
        ItemCache.ovenWoodModel = "model.wood"; ItemCache.ovenWoodBurntModel = "model.burnt"; ItemCache.ovenFireModel = "model.fire";
        ItemCache.ovenBurnIntervalTicks = 1; ItemCache.ovenBurnChanceFresh = 0; ItemCache.ovenBurnChanceBurnt = 0;
        env.cache.when(() -> ItemCache.matchesOvenFuel(any())).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.OAK_LOG;
        });
        oven = bakery.station("oven_bottom");
        for (String slot : OvenSlots.FILL_ORDER) oven.define(slot);
        oven.define(OvenSlots.FIRE);
        cavity = bakery.consumer(); bakery.nest(cavity, "tray", bakery.tray.furniture);
        when(bakery.tray.furniture.isAttached()).thenReturn(true);
        burns = new OvenBurnManager(); handler = new OvenHandler(burns);
    }
    @AfterEach void close() {
        burns.stopAll(); bakery.close();
        FurnitureCache.ovenBottom = oldBottom; FurnitureCache.ovenTop = oldTop;
        ItemCache.ovenWoodModel = oldWood; ItemCache.ovenWoodBurntModel = oldBurntWood; ItemCache.ovenFireModel = oldFire;
        ItemCache.ovenBurnIntervalTicks = oldInterval; ItemCache.ovenBurnChanceFresh = oldFreshChance; ItemCache.ovenBurnChanceBurnt = oldBurntChance;
    }

    void ignite(String slot) {
        OvenState.setStage(oven.furniture, slot, OvenSlots.WoodStage.FRESH); OvenState.setLit(oven.furniture, true);
    }

    @Test
    void persistentFuelStagesValidateSnapshotsAndBurnFromTheHighestLayer() {
        assertFalse(OvenState.isLit(oven.furniture)); assertFalse(OvenState.hasHeat(oven.furniture));
        oven.variables.put(OvenState.VAR_LIT, "true"); assertTrue(OvenState.isLit(oven.furniture));
        oven.variables.put(OvenState.VAR_LIT, 1); assertFalse(OvenState.isLit(oven.furniture));
        oven.variables.put(OvenState.VAR_STAGES, "bad"); assertEquals(0, OvenState.getWoodCount(oven.furniture));
        oven.variables.put(OvenState.VAR_STAGES, "XEFEBF");
        assertEquals(OvenSlots.WoodStage.EMPTY, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_3));
        assertEquals(OvenSlots.WoodStage.BURNT, OvenState.getStage(oven.furniture, OvenSlots.WOOD_5));
        assertArrayEquals(new String[] {OvenSlots.WOOD_6}, OvenState.getActiveBurnLayer(oven.furniture));
        OvenState.setStage(oven.furniture, OvenSlots.WOOD_6, OvenSlots.WoodStage.EMPTY);
        assertArrayEquals(new String[] {OvenSlots.WOOD_4, OvenSlots.WOOD_5}, OvenState.getActiveBurnLayer(oven.furniture));
        OvenState.setStage(oven.furniture, OvenSlots.WOOD_5, OvenSlots.WoodStage.EMPTY);
        assertArrayEquals(new String[] {OvenSlots.WOOD_1, OvenSlots.WOOD_2, OvenSlots.WOOD_3}, OvenState.getActiveBurnLayer(oven.furniture));
        assertEquals(OvenSlots.WOOD_1, OvenState.findNextFillSlot(oven.furniture));
        OvenState.setStage(oven.furniture, "missing", OvenSlots.WoodStage.FRESH);
        assertEquals(OvenSlots.WoodStage.EMPTY, OvenState.getStage(oven.furniture, "missing"));
        for (String slot : OvenSlots.FILL_ORDER) OvenState.setStage(oven.furniture, slot, OvenSlots.WoodStage.FRESH);
        assertTrue(OvenState.isFull(oven.furniture)); assertNull(OvenState.findNextFillSlot(oven.furniture));
        OvenState.setLit(oven.furniture, true); assertTrue(OvenState.shouldShowFire(oven.furniture));
        oven.variables.put("unrelated", 3); OvenState.clear(oven.furniture);
        assertEquals(Map.of("unrelated", 3), oven.variables); assertNull(OvenState.getActiveBurnLayer(oven.furniture));
        assertEquals('E', OvenSlots.WoodStage.EMPTY.code()); assertEquals('F', OvenSlots.WoodStage.FRESH.code()); assertEquals('B', OvenSlots.WoodStage.BURNT.code());
        assertEquals(OvenSlots.WoodStage.EMPTY, OvenSlots.WoodStage.fromCode('?'));
    }

    @Test
    void addingFuelFillsEachSlotOnceThenLightsAndDamagesTheIgniter() {
        var empty = oven.interact(); handler.onInteract(empty); assertFalse(empty.isCancelled());
        ItemStack wood = env.hold(new ItemStack(Material.OAK_LOG, 7));
        for (int n = 1; n <= 6; n++) {
            var add = oven.interact(); handler.onInteract(add); assertTrue(add.isCancelled());
            assertEquals(n, OvenState.getWoodCount(oven.furniture)); assertEquals(7 - n, wood.getAmount());
        }
        handler.onInteract(oven.interact()); assertEquals(1, wood.getAmount()); assertTrue(env.messages.contains("§cThe oven is full of wood."));
        env.hold(null); var occupied = oven.interact(); handler.onInteract(occupied); assertTrue(occupied.isCancelled());
        ItemStack flint = env.hold(new ItemStack(Material.FLINT_AND_STEEL));
        handler.onInteract(oven.interact()); assertTrue(OvenState.isLit(oven.furniture)); assertTrue(oven.active.containsKey("fire"));
        assertEquals(1, ((Damageable) flint.getItemMeta()).getDamage());
        handler.onInteract(oven.interact()); assertEquals(1, ((Damageable) flint.getItemMeta()).getDamage());
        env.hold(null); var lit = oven.interact(); handler.onInteract(lit); assertTrue(lit.isCancelled());
        verify(env.manager, times(7)).markDirty(oven.furniture);
    }

    @Test
    void emptyOvensCannotBeLitAndAnIgniterBreaksAtItsMaximumDamage() {
        ItemStack flint = env.hold(new ItemStack(Material.FLINT_AND_STEEL));
        handler.onInteract(oven.interact()); assertFalse(OvenState.isLit(oven.furniture));
        assertTrue(env.messages.contains("§cAdd wood before lighting the oven.")); assertEquals(0, ((Damageable) flint.getItemMeta()).getDamage());
        OvenState.setStage(oven.furniture, OvenSlots.WOOD_1, OvenSlots.WoodStage.FRESH);
        Damageable meta = (Damageable) flint.getItemMeta(); meta.setDamage(Material.FLINT_AND_STEEL.getMaxDurability() - 1); flint.setItemMeta(meta);
        handler.onInteract(oven.interact()); assertTrue(OvenState.isLit(oven.furniture)); assertTrue(env.player.getInventory().getItemInMainHand().getType().isAir());
        env.hold(new ItemStack(Material.STICK)); var unrelated = oven.interact(); handler.onInteract(unrelated); assertFalse(unrelated.isCancelled());
        var otherFurniture = bakery.station("chair").interact(); handler.onInteract(otherFurniture); assertFalse(otherFurniture.isCancelled());
    }

    @Test
    void displaysTrackFreshBurntEmptyAndFireAndTolerateUnavailableModels() {
        ignite(OvenSlots.WOOD_1); OvenState.setStage(oven.furniture, OvenSlots.WOOD_2, OvenSlots.WoodStage.BURNT);
        OvenDisplay.syncAll(oven.furniture);
        assertTrue(oven.active.containsKey(OvenSlots.WOOD_1)); assertTrue(oven.active.containsKey(OvenSlots.WOOD_2)); assertTrue(oven.active.containsKey(OvenSlots.FIRE));
        verify(env.api.getCreator()).getItemFromPath("model.wood"); verify(env.api.getCreator()).getItemFromPath("model.burnt"); verify(env.api.getCreator()).getItemFromPath("model.fire");
        OvenState.setLit(oven.furniture, false); OvenDisplay.syncAll(oven.furniture); assertFalse(oven.active.containsKey(OvenSlots.FIRE));
        OvenDisplay.clearAll(oven.furniture); assertTrue(oven.active.isEmpty());
        oven.definitions.remove(OvenSlots.WOOD_1); ItemCache.ovenWoodBurntModel = null;
        OvenDisplay.syncAll(oven.furniture); assertTrue(oven.active.isEmpty());
        oven.define(OvenSlots.WOOD_1); when(env.api.getCreator().getItemFromPath("model.wood")).thenReturn(null);
        OvenDisplay.syncAll(oven.furniture); assertTrue(oven.active.isEmpty());
        when(oven.furniture.getType()).thenReturn(null); OvenDisplay.syncAll(oven.furniture); OvenDisplay.clearAll(oven.furniture); assertTrue(oven.active.isEmpty());
    }

    @Test
    void deterministicBurningConsumesOnlyTheHighestLayerAndStopsAfterTheLastFuel() {
        ItemCache.ovenBurnChanceFresh = 1; ItemCache.ovenBurnChanceBurnt = 1;
        for (String slot : OvenSlots.FILL_ORDER) OvenState.setStage(oven.furniture, slot, OvenSlots.WoodStage.FRESH);
        OvenState.setLit(oven.furniture, true); burns.startBurning(oven.furniture); burns.startBurning(oven.furniture);
        env.server.getScheduler().performOneTick();
        assertEquals(OvenSlots.WoodStage.BURNT, OvenState.getStage(oven.furniture, OvenSlots.WOOD_6));
        assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_5));
        env.server.getScheduler().performOneTick(); assertEquals(OvenSlots.WoodStage.EMPTY, OvenState.getStage(oven.furniture, OvenSlots.WOOD_6));
        env.server.getScheduler().performTicks(4);
        assertEquals(0, OvenState.getWoodCount(oven.furniture)); assertFalse(OvenState.isLit(oven.furniture)); assertFalse(oven.active.containsKey(OvenSlots.FIRE));
        clearInvocations(env.manager); env.server.getScheduler().performTicks(3); verifyNoInteractions(env.manager);
    }

    @Test
    void zeroBurnChancesPreserveFuelAndStoppedOrRemovedOvensDoNotKeepTicking() {
        burns.startBurning(null); burns.startBurning(bakery.station("chair").furniture); burns.startBurning(oven.furniture);
        burns.stopBurning(null); burns.stopBurning(UUID.randomUUID());
        ignite(OvenSlots.WOOD_1); ItemCache.ovenBurnIntervalTicks = 0;
        burns.startBurning(oven.furniture); env.server.getScheduler().performTicks(3);
        assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        burns.stopBurning(oven.id); ItemCache.ovenBurnChanceFresh = 1;
        env.server.getScheduler().performTicks(2); assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        burns.startBurning(oven.furniture); bakery.placed.remove(oven.id); env.server.getScheduler().performTicks(2);
        assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        bakery.placed.put(oven.id, oven.furniture); burns.startBurning(oven.furniture); FurnitureCache.ovenBottom = "replacement_bottom";
        env.server.getScheduler().performTicks(2); assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
    }

    @Test
    void extinguishedOvensStopBeforeBurningAnyMoreFuel() {
        ignite(OvenSlots.WOOD_1); ItemCache.ovenBurnChanceFresh = 1;
        burns.startBurning(oven.furniture); OvenState.setLit(oven.furniture, false);
        env.server.getScheduler().performTicks(2);
        assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
    }

    @Test
    void resumingRepairsLitButEmptyOvensAndStopAllCancelsEveryActiveTask() {
        burns.resume(null); burns.resume(bakery.station("chair").furniture);
        OvenState.setLit(oven.furniture, true); burns.resume(oven.furniture); assertFalse(OvenState.isLit(oven.furniture));
        verify(env.manager).markDirty(oven.furniture);
        ignite(OvenSlots.WOOD_1); burns.resumeAll(); burns.stopAll(); ItemCache.ovenBurnChanceFresh = 1;
        env.server.getScheduler().performTicks(3); assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
    }

    @Test
    void chunkLifecycleStopsBurningAndResumesOnlyLoadedOvensOneTickLater() {
        Chunk chunk = mock(Chunk.class), other = mock(Chunk.class);
        when(env.world.getChunkAt(any(Location.class))).thenReturn(chunk);
        Station distant = bakery.station("oven_bottom"); Location distantLocation = mock(Location.class);
        when(distantLocation.getChunk()).thenReturn(other); when(distant.furniture.getLoc()).thenReturn(distantLocation);
        OvenLifecycleHandler lifecycle = new OvenLifecycleHandler(burns);
        ignite(OvenSlots.WOOD_1); lifecycle.resumeLoadedOvens();
        lifecycle.onChunkUnload(new ChunkUnloadEvent(chunk)); ItemCache.ovenBurnChanceFresh = 1;
        env.server.getScheduler().performTicks(3); assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        lifecycle.onChunkLoad(new ChunkLoadEvent(chunk, false));
        assertEquals(OvenSlots.WoodStage.FRESH, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        env.server.getScheduler().performTicks(2); assertEquals(OvenSlots.WoodStage.BURNT, OvenState.getStage(oven.furniture, OvenSlots.WOOD_1));
        lifecycle.onBreak(new FurnitureBreakEvent(bakery.tray.furniture, env.player));
        lifecycle.onBreak(new FurnitureBreakEvent(oven.furniture, env.player));
        assertTrue(oven.variables.isEmpty()); assertTrue(oven.active.isEmpty());
        env.server.getScheduler().performTicks(2); assertTrue(oven.variables.isEmpty());
    }

    @Test
    void ambienceRespectsHeatWorldAndRandomChoicesWithoutMovingTheFurniture() {
        Location initial = oven.furniture.getLoc().clone();
        assertEquals(initial.clone().add(0, 0.2, 0), OvenEffects.fireLocation(oven.furniture));
        assertEquals(initial, oven.furniture.getLoc());
        Random random = mock(Random.class);
        OvenEffects.playAmbience(oven.furniture, random); verifyNoInteractions(random);
        ignite(OvenSlots.WOOD_1); OvenEffects.playIgnite(oven.furniture);
        when(random.nextFloat()).thenReturn(0f, 0.5f, 0f, 0f); when(random.nextBoolean()).thenReturn(true);
        OvenEffects.playAmbience(oven.furniture, random);
        verify(env.world).playSound(any(Location.class), eq(Sound.BLOCK_FIRE_AMBIENT), eq(1f), eq(1f));
        verify(env.world).spawnParticle(eq(Particle.SMOKE), any(Location.class), eq(1), eq(0.05), eq(0.04), eq(0.05), eq(0.005));
        when(random.nextFloat()).thenReturn(0f, 0.5f, 0f, 1f); when(random.nextBoolean()).thenReturn(false);
        OvenEffects.playAmbience(oven.furniture, random);
        verify(env.world).playSound(any(Location.class), eq("block.furnace.fire_crackle"), eq(1f), eq(1f));
        clearInvocations(env.world); when(random.nextFloat()).thenReturn(1f); OvenEffects.playAmbience(oven.furniture, random); verifyNoInteractions(env.world);
        when(oven.furniture.getLoc()).thenReturn(new Location(null, 1, 64, 1));
        OvenEffects.playIgnite(oven.furniture); OvenEffects.playAmbience(oven.furniture, random); verifyNoInteractions(env.world);
    }

    @Test
    void cavitiesAcceptOnlyBakingTraysInFurnitureSlots() {
        OvenCavityHandler handler = new OvenCavityHandler();
        var slot = cavity.define("tray"); when(slot.getSlotType()).thenReturn(SlotType.FURNITURE);
        var allowed = new FurnitureSlotFurnitureAddEvent(env.player, cavity.furniture, slot, bakery.tray.furniture);
        handler.onFurnitureAdd(allowed); assertFalse(allowed.isCancelled());
        var empty = new FurnitureSlotFurnitureAddEvent(env.player, cavity.furniture, slot, null); handler.onFurnitureAdd(empty); assertFalse(empty.isCancelled());
        var wrong = new FurnitureSlotFurnitureAddEvent(env.player, cavity.furniture, slot, oven.furniture); handler.onFurnitureAdd(wrong); assertTrue(wrong.isCancelled());
        assertTrue(env.messages.contains("§cOnly baking trays can go in the oven."));
        var other = new FurnitureSlotFurnitureAddEvent(env.player, oven.furniture, slot, bakery.tray.furniture); handler.onFurnitureAdd(other); assertFalse(other.isCancelled());
        when(slot.getSlotType()).thenReturn(SlotType.ITEM);
        var itemSlot = new FurnitureSlotFurnitureAddEvent(env.player, cavity.furniture, slot, oven.furniture); handler.onFurnitureAdd(itemSlot); assertFalse(itemSlot.isCancelled());
    }

    @Test
    void cavityClocksPauseWithoutHeatThenCookAndBurnEachLoafAtItsOwnThreshold() {
        bakery.tray.place("left_a", bakery.loaf(0, 1)); bakery.tray.place("left_b", bakery.loaf(1, 1));
        bakery.tray.place("right_a", bakery.loaf(2, 1)); bakery.tray.place("right_b", new ItemStack(Material.STONE));
        bakery.nest(cavity, "empty", null); bakery.nest(cavity, "wrong", oven.furniture);
        bakery.heated = false; new OvenCavityManager().start(); env.server.getScheduler().performTicks(40);
        assertEquals(0, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "left_a"));
        bakery.heated = true; env.server.getScheduler().performTicks(20);
        assertEquals(1, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "left_a"));
        assertEquals(0, env.resolve(bakery.tray.active.get("left_a").getCurrentItem()).getTagTrack("cooked").getValue());
        env.server.getScheduler().performTicks(20);
        assertEquals(1, env.resolve(bakery.tray.active.get("left_a").getCurrentItem()).getTagTrack("cooked").getValue());
        env.server.getScheduler().performTicks(40);
        assertEquals(2, env.resolve(bakery.tray.active.get("left_a").getCurrentItem()).getTagTrack("cooked").getValue());
        assertEquals(2, env.resolve(bakery.tray.active.get("left_b").getCurrentItem()).getTagTrack("cooked").getValue());
        assertEquals(0, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "right_a"));
        assertEquals(0, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "right_b"));
        verify(env.manager, times(2)).markDirty(bakery.tray.furniture);
    }

    @Test
    void untaggedBreadCanBeginCookingInAHeatedCavity() {
        bakery.tray.place("left_a", env.stack(bakery.food("bread", null), Material.BREAD, 1));
        new OvenCavityManager().start(); env.server.getScheduler().performTicks(40);
        FoodItem loaf = env.resolve(bakery.tray.active.get("left_a").getCurrentItem());
        assertNotNull(loaf.getTagTrack("cooked")); assertEquals(1, loaf.getTagTrack("cooked").getValue());
    }

    @Test
    void emptyUnrecognizedAndBurntContentsDoNotAdvanceBakingClocks() {
        bakery.tray.place("left_a", null); bakery.tray.place("left_b", new ItemStack(Material.AIR));
        bakery.tray.place("right_a", new ItemStack(Material.STONE)); bakery.tray.place("right_b", bakery.loaf(2, 1));
        new OvenCavityManager().start(); env.server.getScheduler().performTicks(40);
        assertTrue(bakery.tray.variables.isEmpty()); verify(env.manager, never()).markDirty(bakery.tray.furniture);
        bakery.tray.active.clear(); env.server.getScheduler().performTicks(20); assertTrue(bakery.tray.variables.isEmpty());
    }

    @Test
    void aWorkingCavitySkipsEmptySlotsWhileAdvancingItsRawBread() {
        bakery.tray.place("left_a", bakery.loaf(0, 1));
        bakery.tray.place("left_b", null); bakery.tray.place("right_a", new ItemStack(Material.AIR));
        new OvenCavityManager().start(); env.server.getScheduler().performTicks(20);
        assertEquals(1, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "left_a"));
        assertEquals(0, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "left_b"));
        assertEquals(0, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "right_a"));
    }

    @Test
    void emptyEntriesInThePublicSlotMapDoNotPreventOtherLoavesFromBaking() {
        bakery.tray.furniture.getActiveSlots().put("left_a", null);
        bakery.tray.place("left_b", bakery.loaf(0, 1));
        new OvenCavityManager().start(); env.server.getScheduler().performTicks(40);
        assertEquals(1, env.resolve(bakery.tray.active.get("left_b").getCurrentItem()).getTagTrack("cooked").getValue());
        assertEquals(0, BakingTrayState.getSlotElapsed(bakery.tray.furniture, "left_a"));
        assertNull(bakery.tray.furniture.getActiveSlots().get("left_a"));
    }

    @Test
    void failedBakingRenderDoesNotReplaceTheDisplayedLoafOrClaimASuccessfulChange() {
        PlacedSlot slot = bakery.tray.place("left_a", bakery.loaf(0, 1));
        ItemStack original = slot.getCurrentItem();
        bakery.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        new OvenCavityManager().start(); env.server.getScheduler().performTicks(40);
        assertSame(original, slot.getCurrentItem()); verify(slot, never()).forceModel(any());
        verify(env.manager, never()).markDirty(bakery.tray.furniture);
    }
}
