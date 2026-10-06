package net.tfminecraft.cooking.heat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.tfminecraft.cooking.oven.OvenSlots;
import net.tfminecraft.cooking.oven.OvenState;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeatRuntimeCoverageTest {
    Workshop env;
    World world;
    Station fire,pan,oven;
    Map<UUID,Furniture> placed=new LinkedHashMap<>();
    @BeforeEach void setup() {
        env=new Workshop(); world=env.server.addSimpleWorld("heat");
        fire=at("fire",0,64,0); pan=at("pan",0,65,0); oven=at("oven",5,64,0);
        when(env.manager.getPlacedFurniture()).thenReturn(placed);
        HeatSources.load(Map.of("fire",new HeatSourceDefinition("fire",HeatSourceType.CAMPFIRE),
                "oven",new HeatSourceDefinition("oven",HeatSourceType.OVEN)),
                Map.of("pan",new HeatConsumerDefinition("pan","fire",HeatLookup.BLOCK_BELOW)));
    }
    @AfterEach void cleanup() { HeatLoader.load(new YamlConfiguration()); env.close(); }
    Station at(String id,int x,int y,int z) {
        Station s=env.station(id); when(s.furniture.getOriginBlockLocation()).thenReturn(Optional.of(new Location(world,x,y,z)));
        placed.put(s.id,s.furniture); return s;
    }
    @Test void heatRequiresALitCampfireOrALitFueledOven() {
        assertFalse(HeatSources.isSource(null)); assertFalse(HeatSources.isConsumer(null)); assertFalse(HeatSources.hasHeat(null));
        assertFalse(HeatSources.stationHasHeat(null)); assertFalse(HeatSources.stationHasHeat(at("unknown",9,9,9).furniture));
        assertFalse(HeatSources.hasHeat(pan.furniture)); assertFalse(HeatSources.hasHeat(fire.furniture));
        for(Material type:new Material[]{Material.CAMPFIRE,Material.SOUL_CAMPFIRE}) {
            var block=world.getBlockAt(0,64,0); block.setType(type); Campfire data=(Campfire)block.getBlockData();
            data.setLit(false); block.setBlockData(data); assertFalse(HeatSources.hasHeat(fire.furniture));
            data.setLit(true); block.setBlockData(data); assertTrue(HeatSources.stationHasHeat(fire.furniture));
            assertTrue(HeatSources.stationHasHeat(pan.furniture));
        }
        assertFalse(HeatSources.stationHasHeat(oven.furniture)); OvenState.setLit(oven.furniture,true);
        OvenState.setStage(oven.furniture,OvenSlots.FILL_ORDER[0],OvenSlots.WoodStage.FRESH);
        assertTrue(HeatSources.stationHasHeat(oven.furniture));
        when(fire.furniture.getOriginBlockLocation()).thenReturn(Optional.empty()); assertFalse(HeatSources.hasHeat(fire.furniture));
    }
    @Test void lookupUsesUnattachedFurnitureAtExactlyTheBlockBelow() {
        assertTrue(HeatSources.findSource(null).isEmpty()); assertTrue(HeatSources.findSource(fire.furniture).isEmpty());
        assertSame(fire.furniture,HeatSources.findSource(pan.furniture).orElseThrow());
        placed.remove(fire.id); assertFalse(HeatSources.consumerHasHeat(pan.furniture));
        Station attached=at("fire",0,64,0); when(attached.furniture.isAttached()).thenReturn(true);
        Station noOrigin=at("fire",0,64,0); when(noOrigin.furniture.getOriginBlockLocation()).thenReturn(Optional.empty());
        at("fire",1,64,0); at("fire",0,63,0); at("fire",0,64,1);
        Station nowhere=at("fire",0,64,0); when(nowhere.furniture.getOriginBlockLocation()).thenReturn(Optional.of(new Location(null,0,64,0)));
        assertTrue(HeatSources.findSource(pan.furniture).isEmpty());
        placed.put(fire.id,fire.furniture); assertSame(fire.furniture,HeatSources.findSource(pan.furniture).orElseThrow());
        when(pan.furniture.getOriginBlockLocation()).thenReturn(Optional.empty()); assertTrue(HeatSources.findSource(pan.furniture).isEmpty());
        when(pan.furniture.getOriginBlockLocation()).thenReturn(Optional.of(new Location(world,0,65,0)));
        HeatSources.load(Map.of(),Map.of("pan",new HeatConsumerDefinition("pan","fire",HeatLookup.BLOCK_BELOW)));
        assertTrue(HeatSources.findSource(pan.furniture).isEmpty());
        HeatSources.load(Map.of(),Map.of("pan",new HeatConsumerDefinition("pan",null,HeatLookup.BLOCK_BELOW)));
        assertTrue(HeatSources.findSource(pan.furniture).isEmpty());
    }
    @Test void aConsumerAbovePreventsPickingUpItsHeatSource() {
        HeatPickupGuard listener=new HeatPickupGuard();
        assertFalse(HeatSources.hasBlockingConsumerAbove(pan.furniture));
        assertTrue(HeatSources.hasBlockingConsumerAbove(fire.furniture));
        assertFalse(HeatSources.hasBlockingConsumerAbove(oven.furniture));
        when(fire.type.canPickup()).thenReturn(true); env.hold(new ItemStack(Material.AIR));
        FurnitureInteractEvent event=fire.interact(); listener.onInteract(event); assertTrue(event.isCancelled());
        assertTrue(env.messages.getLast().contains("Remove the piece above"));
        listener.onInteract(new FurnitureInteractEvent(null,fire.furniture)); listener.onInteract(new FurnitureInteractEvent(env.player,null));
        when(env.player.isSneaking()).thenReturn(true); assertAllowed(listener); when(env.player.isSneaking()).thenReturn(false);
        env.hold(new ItemStack(Material.STICK)); assertAllowed(listener); env.hold(new ItemStack(Material.AIR));
        when(fire.furniture.getType()).thenReturn(null); assertAllowed(listener); when(fire.furniture.getType()).thenReturn(fire.type);
        when(fire.type.canPickup()).thenReturn(false); assertAllowed(listener); when(fire.type.canPickup()).thenReturn(true);
        fire.place("food",new ItemStack(Material.COD)); assertAllowed(listener); fire.active.clear();
        when(fire.furniture.getActiveFurnitureSlots()).thenReturn(Map.of("top",mock(net.tfminecraft.interactiblefurniture.furniture.PlacedFurnitureSlot.class))); assertAllowed(listener);
        when(fire.furniture.getActiveFurnitureSlots()).thenReturn(Map.of()); placed.remove(pan.id); assertAllowed(listener);
        when(fire.furniture.getOriginBlockLocation()).thenReturn(Optional.empty()); assertFalse(HeatSources.hasBlockingConsumerAbove(fire.furniture));
        HeatSourceDefinition source=new HeatSourceDefinition("fire",HeatSourceType.CAMPFIRE); assertEquals("fire",source.getFurnitureId());
        HeatSources.load(Map.of("fire",source),Map.of("pan",new HeatConsumerDefinition("pan","fire",null)));
        when(fire.furniture.getOriginBlockLocation()).thenReturn(Optional.of(new Location(world,0,64,0)));
        assertFalse(HeatSources.hasBlockingConsumerAbove(fire.furniture));
    }
    @Test void furnitureIdentifiersAreIndependentOfServerLocale() {
        Locale old=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            YamlConfiguration config=new YamlConfiguration(); config.set("heat.sources.FIRE.type","campfire");
            config.set("heat.consumers.SKILLET.source","FIRE"); config.set("heat.consumers.SKILLET.lookup","block-below");
            HeatLoader.load(config); assertTrue(HeatSources.isSource(fire.furniture));
            Station skillet=at("skillet",0,65,0); assertTrue(HeatSources.isConsumer(skillet.furniture));
            assertSame(fire.furniture,HeatSources.findSource(skillet.furniture).orElseThrow());
        } finally { Locale.setDefault(old); }
    }
    void assertAllowed(HeatPickupGuard listener) { FurnitureInteractEvent event=fire.interact(); listener.onInteract(event); assertFalse(event.isCancelled()); }
}
