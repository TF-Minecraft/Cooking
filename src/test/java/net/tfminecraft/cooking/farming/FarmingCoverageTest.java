package net.tfminecraft.cooking.farming;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.crops.*;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.loader.*;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.utils.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

public class FarmingCoverageTest {
    @TempDir Path directory;
    Plot f;
    @BeforeEach void setUp() throws Exception { f = new Plot(directory); }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test void loaderDefaultsAndInvalidFilesResetEarlierConfiguration() throws Exception {
        f.config.set("farming.enabled", false); f.loadFarming(); assertFalse(FarmingConfig.enabled()); assertFalse(FarmingConfig.antiTrampleEnabled());
        new FarmingLoader().load(directory.resolve("missing.yml").toFile()); assertDefaults();
        File invalid = directory.resolve("invalid.yml").toFile(); java.nio.file.Files.writeString(invalid.toPath(), "farming: [broken"); new FarmingLoader().load(invalid); assertDefaults();
        f.config = new YamlConfiguration(); f.loadFarming(); assertDefaults();
    }

    @Test void loaderParsesToolsCropsEffectsAndTrampleConfiguration() throws Exception {
        f.config.loadFromString("""
                farming:
                  enabled: true
                  effects: {harvest-particle-multiplier: 2, replant-particle-multiplier: 3, tool-swing-particle: false}
                  replant-delay-min: 4
                  replant-delay-max: 2
                  only-harvest-mature: false
                  tool-damage-per-harvest: 3
                  apply-unbreaking: false
                  tools:
                    - {path: ' hoe ', radius: 2, quality-bonus-percent: 15}
                    - {path: other, radius: '3', quality-bonus-percent: '25.5'}
                    - {path: bad, radius: nope, quality-bonus-percent: nope}
                    - {path: defaults}
                    - {radius: 4}
                    - {path: ' '}
                  crops:
                    - {crop: WHEAT, seed: WHEAT_SEEDS}
                    - {crop: CARROTS, seed: CARROT}
                    - {crop: INVALID, seed: WHEAT_SEEDS}
                    - {crop: POTATOES, seed: INVALID}
                    - {crop: BEETROOTS}
                  anti-trample: {enabled: false, trample-by-walking: true, dry-empty-farmland: false, trample-particle-multiplier: 4, crops: [WHEAT, INVALID]}
                """); f.loadFarming();
        assertEquals(4, FarmingConfig.tools().size()); assertEquals("hoe", FarmingConfig.tools().getFirst().path()); assertEquals(2, FarmingConfig.tools().getFirst().radius()); assertEquals(15, FarmingConfig.tools().getFirst().qualityBonusPercent());
        assertEquals(3, FarmingConfig.tools().get(1).radius()); assertEquals(25.5, FarmingConfig.tools().get(1).qualityBonusPercent()); assertEquals(0, FarmingConfig.tools().get(2).radius()); assertEquals(0, FarmingConfig.tools().get(2).qualityBonusPercent());
        assertEquals(Set.of(Material.WHEAT, Material.CARROTS), FarmingConfig.cropMaterials()); assertEquals(Material.WHEAT_SEEDS, FarmingConfig.cropFor(Material.WHEAT).seed()); assertEquals(2, FarmingConfig.harvestParticleMultiplier()); assertEquals(3, FarmingConfig.replantParticleMultiplier()); assertFalse(FarmingConfig.toolSwingParticle()); assertEquals(4, FarmingConfig.replantDelayMax()); assertFalse(FarmingConfig.onlyHarvestMature()); assertEquals(3, FarmingConfig.toolDamagePerHarvest()); assertFalse(FarmingConfig.applyUnbreaking()); assertFalse(FarmingConfig.antiTrampleEnabled()); assertTrue(FarmingConfig.trampleByWalking()); assertFalse(FarmingConfig.dryEmptyFarmland()); assertEquals(4, FarmingConfig.trampleParticleMultiplier()); assertTrue(FarmingConfig.isTrampleableCrop(Material.WHEAT)); assertFalse(FarmingConfig.isTrampleableCrop(null)); assertFalse(FarmingConfig.isTrampleableCrop(Material.CARROTS));
        f.config.set("farming.anti-trample.crops", List.of("INVALID")); f.loadFarming(); assertTrue(FarmingConfig.isTrampleableCrop(Material.PUMPKIN_STEM)); f.config.set("farming.anti-trample.crops", List.of()); f.loadFarming(); assertTrue(FarmingConfig.isTrampleableCrop(Material.NETHER_WART));
    }

    @Test void matcherUsesFirstMatchingConfiguredToolAndHandlesMissingMaterials() {
        assertNull(FarmingToolMatcher.matchTool(null)); assertNull(FarmingToolMatcher.matchTool(new ItemStack(Material.AIR))); assertNull(FarmingToolMatcher.matchTool(new ItemStack(Material.STICK))); assertEquals("hoe", FarmingToolMatcher.matchTool(new ItemStack(Material.IRON_HOE)).path()); assertNull(FarmingToolMatcher.cropFor(null)); assertNull(FarmingToolMatcher.cropFor(Material.STONE)); assertEquals(Material.WHEAT, FarmingToolMatcher.cropFor(Material.WHEAT).crop());
    }

    @Test void scannerFindsTheConnectedSquareAndAllFourSlopingDirections() {
        BlockMock centre = f.crop(0, 65, 0, Material.WHEAT, true);
        assertTrue(FarmAreaScanner.findCropBlocks(null, 2, Set.of(Material.WHEAT)).isEmpty()); assertTrue(FarmAreaScanner.findCropBlocks(centre, 2, null).isEmpty()); assertTrue(FarmAreaScanner.findCropBlocks(centre, 2, Set.of()).isEmpty()); assertTrue(FarmAreaScanner.findCropBlocks(centre, 2, Set.of(Material.CARROTS)).isEmpty()); assertEquals(List.of(centre), FarmAreaScanner.findCropBlocks(centre, 0, Set.of(Material.WHEAT)));
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) f.crop(x,65,z,Material.WHEAT,true);
        List<Block> square = FarmAreaScanner.findCropBlocks(centre, 2, Set.of(Material.WHEAT)); assertEquals(25, square.size()); assertEquals(25, new HashSet<>(square).size());
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) if (x!=0 || z!=0) f.block(x,65,z,Material.AIR);
        f.crop(1,66,0,Material.WHEAT,true); f.crop(-1,64,0,Material.WHEAT,true); f.crop(0,66,1,Material.WHEAT,true); f.crop(0,64,-1,Material.WHEAT,true);
        assertEquals(5, FarmAreaScanner.findCropBlocks(centre,2,Set.of(Material.WHEAT)).size());
    }

    @Test void scannerDoesNotJumpGapsOrHarvestBelowSolidObstructions() {
        BlockMock centre = f.crop(0,65,0,Material.WHEAT,true);
        for (int sign : List.of(-1,1)) { f.crop(2*sign,65,0,Material.WHEAT,true); f.crop(0,65,2*sign,Material.WHEAT,true); f.crop(sign,65,sign,Material.WHEAT,true); f.crop(sign,65,-sign,Material.WHEAT,true); }
        assertEquals(List.of(centre), FarmAreaScanner.findCropBlocks(centre,2,Set.of(Material.WHEAT)));
        f.block(1,65,0,Material.STONE); f.crop(1,64,0,Material.WHEAT,true); assertEquals(List.of(centre),FarmAreaScanner.findCropBlocks(centre,2,Set.of(Material.WHEAT)));
        f.block(0,65,1,Material.WHEAT); f.block(1,65,1,Material.WHEAT); List<Block> connected=FarmAreaScanner.findCropBlocks(centre,2,Set.of(Material.WHEAT)); assertTrue(connected.contains(f.world.getBlockAt(1,65,1))); assertFalse(connected.contains(f.world.getBlockAt(1,64,0)));
    }

    @Test void harvestReservesOneSeedTransfersProduceAndReplantsAtAgeZero() {
        BlockMock crop = harvestable(); crop.setDrops(List.of(new ItemStack(Material.WHEAT_SEEDS,2), new ItemStack(Material.WHEAT,3))); f.holdHoe();
        assertTrue(FarmHarvestService.harvestBlock(f.player, f.tool(), hoe(), crop, wheat())); assertEquals(Material.AIR,crop.getType()); assertEquals(1,f.inventoryCount(Material.WHEAT_SEEDS)); assertEquals(3,f.inventoryCount(Material.WHEAT));
        f.server.getScheduler().performTicks(2); assertEquals(Material.WHEAT,crop.getType()); assertEquals(0,((Ageable)crop.getBlockData()).getAge()); assertEquals(1,f.inventoryCount(Material.WHEAT_SEEDS));
    }

    @Test void harvestCanConvertProduceWithOneQualityWhilePreservingSeeds() {
        f.food("grain",Material.BREAD); ConversionLoader.conversions.put("v.wheat","grain(type=grain;origin=Wheat)");
        CropsConfig.apply(Map.of(),Map.of(),Map.of("wheat",new CropDefinition("wheat","vanilla","v.wheat_seeds",0.5,Material.WHEAT)));
        BlockMock crop=harvestable(); crop.setDrops(List.of(new ItemStack(Material.WHEAT,3),new ItemStack(Material.WHEAT_SEEDS,2))); f.holdHoe();
        try(MockedStatic<CropHarvestQuality> quality=mockStatic(CropHarvestQuality.class)) {
            quality.when(() -> CropHarvestQuality.roll("wheat",crop.getLocation(),f.player)).thenReturn(2);
            assertTrue(FarmHarvestService.harvestBlock(f.player,f.tool(),new FarmingToolDefinition("hoe",0,100),crop,wheat()));
        }
        ItemStack bread=Arrays.stream(f.player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i -> i.getType()==Material.BREAD).findFirst().orElseThrow(); assertEquals(3,bread.getAmount()); assertEquals(3,FoodItem.fromItem(bread).getQualityMin()); assertEquals(1,f.inventoryCount(Material.WHEAT_SEEDS));
    }

    @Test void harvestRejectsInvalidImmatureAndProtectedInputsWithoutDrops() {
        BlockMock crop=f.crop(0,65,0,Material.WHEAT,false); f.holdHoe();
        assertFalse(FarmHarvestService.harvestBlock(null,f.tool(),hoe(),crop,wheat())); assertFalse(FarmHarvestService.harvestBlock(f.player,null,hoe(),crop,wheat())); assertFalse(FarmHarvestService.harvestBlock(f.player,f.tool(),null,crop,wheat())); assertFalse(FarmHarvestService.harvestBlock(f.player,f.tool(),hoe(),null,wheat())); assertFalse(FarmHarvestService.harvestBlock(f.player,f.tool(),hoe(),crop,null)); assertFalse(FarmHarvestService.harvestBlock(f.player,f.tool(),hoe(),crop,wheat()));
        f.mature(crop); f.on(BlockBreakEvent.class,event -> event.setCancelled(true)); assertFalse(FarmHarvestService.harvestBlock(f.player,f.tool(),hoe(),crop,wheat())); assertEquals(Material.WHEAT,crop.getType()); assertEquals(0,f.inventoryCount(Material.WHEAT));
    }

    @Test void replantRefundsSeedWhenPlayerLeavesBlockChangesOrPlacementIsDenied() {
        for (int mode=0;mode<4;mode++) {
            BlockMock crop=f.crop(mode*4,65,0,Material.WHEAT,true); crop.setDrops(List.of(new ItemStack(Material.WHEAT_SEEDS))); f.holdHoe();
            assertTrue(FarmHarvestService.harvestBlock(f.player,f.tool(),hoe(),crop,wheat()));
            if(mode==0) f.player.disconnect();
            if(mode==1) crop.setType(Material.STONE);
            if(mode==2) f.on(BlockPlaceEvent.class,event -> event.setCancelled(true));
            if(mode==3) f.on(BlockPlaceEvent.class,event -> event.setBuild(false));
            f.server.getScheduler().performTicks(2); assertEquals(mode==1?Material.STONE:Material.AIR,crop.getType()); assertEquals(mode+1,f.droppedCount(Material.WHEAT_SEEDS));
            if(mode==0) f.player.reconnect(); HandlerList.unregisterAll(f.plugin);
        }
    }

    @Test void fullInventoryDropsExcessAndMissingSeedsDoNotScheduleReplant() {
        f.holdHoe(); for(int i=1;i<36;i++) f.player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        f.player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET)); f.player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE)); f.player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS)); f.player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS)); f.player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        BlockMock crop=harvestable(); crop.setDrops(Arrays.asList(null,new ItemStack(Material.AIR),new ItemStack(Material.WHEAT,4)));
        assertEquals(3,crop.getDrops(f.tool()).size()); Player full=f.fullStoragePlayer();
        assertTrue(FarmHarvestService.harvestBlock(full,f.tool(),hoe(),crop,wheat())); assertEquals(4,f.droppedCount(Material.WHEAT)); f.server.getScheduler().performTicks(2); assertEquals(Material.AIR,crop.getType());
    }

    @Test void areaHarvestDamagesToolsAndStopsWhenTheyBreakOrDisappear() throws Exception {
        f.config.set("farming.tools",List.of(Map.of("path","hoe","radius",1))); f.loadFarming(); f.holdHoe();
        BlockMock first=harvestable(); f.crop(1,65,0,Material.WHEAT,true); Damageable meta=(Damageable)f.tool().getItemMeta(); meta.setDamage(Material.IRON_HOE.getMaxDurability()-1); f.tool().setItemMeta(meta);
        FarmHarvestService.harvestArea(f.player,f.tool(),new FarmingToolDefinition("hoe",1,0),first); assertEquals(0,f.tool().getAmount()); assertEquals(1,List.of(first,f.world.getBlockAt(1,65,0)).stream().filter(b -> b.getType()==Material.AIR).count());
        Block remaining=List.of(first,f.world.getBlockAt(1,65,0)).stream().filter(b -> b.getType()==Material.WHEAT).findFirst().orElseThrow();
        FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),remaining); assertEquals(Material.WHEAT,remaining.getType(),"The exhausted tool cannot harvest another crop");
        f.holdHoe(); BlockMock second=harvestable(); f.on(BlockBreakEvent.class,event -> f.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR))); FarmHarvestService.harvestArea(f.player,f.tool(),new FarmingToolDefinition("hoe",1,0),second); assertEquals(Material.AIR,second.getType());
        FarmHarvestService.harvestArea(null,f.tool(),hoe(),second); FarmHarvestService.harvestArea(f.player,null,hoe(),second); FarmHarvestService.harvestArea(f.player,f.tool(),null,second); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),null);
    }

    @Test void toolDamageRespectsGameModesDurabilityAndDeterministicUnbreaking() throws Exception {
        for(GameMode mode: List.of(GameMode.CREATIVE,GameMode.SPECTATOR,GameMode.ADVENTURE,GameMode.SURVIVAL)) {
            f.player.setGameMode(mode); f.holdHoe(); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),harvestable()); assertEquals(mode==GameMode.ADVENTURE||mode==GameMode.SURVIVAL?1:0,((Damageable)f.tool().getItemMeta()).getDamage());
        }
        f.player.setGameMode(GameMode.SURVIVAL); f.holdHoe(); var meta=f.tool().getItemMeta(); meta.setUnbreakable(true); f.tool().setItemMeta(meta); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),harvestable()); assertEquals(0,((Damageable)f.tool().getItemMeta()).getDamage());
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK)); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),harvestable()); assertEquals(Material.STICK,f.tool().getType());
        f.config.set("farming.tool-damage-per-harvest",0); f.loadFarming(); f.holdHoe(); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),harvestable()); assertEquals(0,((Damageable)f.tool().getItemMeta()).getDamage());
        f.config.set("farming.tool-damage-per-harvest",1); f.config.set("farming.apply-unbreaking",true); f.loadFarming(); f.tool().addUnsafeEnchantment(Enchantment.UNBREAKING,3);
        ThreadLocalRandom random=mock(ThreadLocalRandom.class); when(random.nextDouble()).thenReturn(0.9,0.1);
        try(MockedStatic<ThreadLocalRandom> rng=mockStatic(ThreadLocalRandom.class)) { rng.when(ThreadLocalRandom::current).thenReturn(random); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),harvestable()); assertEquals(0,((Damageable)f.tool().getItemMeta()).getDamage()); FarmHarvestService.harvestArea(f.player,f.tool(),hoe(),harvestable()); assertEquals(1,((Damageable)f.tool().getItemMeta()).getDamage()); }
    }

    @Test void listenerHandlesOnlyConfiguredCropsAndNeverRecursesDuringItsSyntheticBreak() throws Exception {
        FarmHarvestListener listener=new FarmHarvestListener(); f.server.getPluginManager().registerEvents(listener,f.plugin); f.holdHoe(); BlockMock crop=harvestable(); crop.setDrops(List.of(new ItemStack(Material.WHEAT,2))); var event=new BlockBreakEvent(crop,f.player); f.server.getPluginManager().callEvent(event); assertTrue(event.isCancelled()); assertEquals(Material.AIR,crop.getType()); assertEquals(2,f.inventoryCount(Material.WHEAT));
        var stone=new BlockBreakEvent(f.block(5,65,0,Material.STONE),f.player); listener.onCropBreak(stone); assertFalse(stone.isCancelled()); f.player.getInventory().setItemInMainHand(new ItemStack(Material.STICK)); var noHoe=new BlockBreakEvent(harvestable(),f.player); listener.onCropBreak(noHoe); assertFalse(noHoe.isCancelled());
        f.config.set("farming.enabled",false); f.loadFarming(); f.holdHoe(); var disabled=new BlockBreakEvent(harvestable(),f.player); listener.onCropBreak(disabled); assertFalse(disabled.isCancelled());
    }

    @Test void trampleProtectsFarmlandAndResetsCropAgeThroughCancellableEvents() {
        FarmTrampleListener listener=new FarmTrampleListener(); f.server.getPluginManager().registerEvents(listener,f.plugin); BlockMock crop=harvestable(); Block farmland=crop.getRelative(BlockFace.DOWN); farmland.setType(Material.FARMLAND);
        var event=new PlayerInteractEvent(f.player,Action.PHYSICAL,null,farmland,BlockFace.SELF); f.server.getPluginManager().callEvent(event); assertEquals(Event.Result.DENY,event.useInteractedBlock()); assertEquals(Material.FARMLAND,farmland.getType()); assertEquals(0,((Ageable)crop.getBlockData()).getAge());
        f.mature(crop); f.on(BlockFadeEvent.class,fade -> fade.setCancelled(true)); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.PHYSICAL,null,farmland,BlockFace.SELF)); assertEquals(((Ageable)crop.getBlockData()).getMaximumAge(),((Ageable)crop.getBlockData()).getAge());
        HandlerList.unregisterAll(f.plugin); f.on(PlayerInteractEvent.class,physical -> physical.setUseInteractedBlock(Event.Result.DENY)); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.PHYSICAL,null,farmland,BlockFace.SELF)); assertEquals(((Ageable)crop.getBlockData()).getMaximumAge(),((Ageable)crop.getBlockData()).getAge());
    }

    @Test void trampleAndWalkingIgnoreUnrelatedActionsAndRespectSettings() throws Exception {
        FarmTrampleListener listener=new FarmTrampleListener(); BlockMock crop=harvestable(); Block farmland=crop.getRelative(BlockFace.DOWN); farmland.setType(Material.FARMLAND);
        listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.LEFT_CLICK_AIR,null,null,BlockFace.SELF)); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.LEFT_CLICK_BLOCK,null,farmland,BlockFace.SELF)); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.PHYSICAL,null,crop,BlockFace.SELF));
        crop.setType(Material.AIR); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.PHYSICAL,null,farmland,BlockFace.SELF)); f.config.set("farming.anti-trample.dry-empty-farmland",false); f.loadFarming(); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.PHYSICAL,null,farmland,BlockFace.SELF));
        Location from=new Location(f.world,2,64,0),to=farmland.getLocation(); var move=new PlayerMoveEvent(f.player,from,to); listener.onWalk(move); f.config.set("farming.anti-trample.trample-by-walking",true); f.loadFarming(); listener.onWalk(move); f.crop(0,65,0,Material.WHEAT,true); listener.onWalk(move); assertEquals(0,((Ageable)crop.getBlockData()).getAge());
        f.mature(crop); f.player.setSneaking(true); listener.onWalk(move); f.player.setSneaking(false); var cancelled=new PlayerMoveEvent(f.player,from,to); cancelled.setCancelled(true); listener.onWalk(cancelled); listener.onWalk(new PlayerMoveEvent(f.player,from,null)); listener.onWalk(new PlayerMoveEvent(f.player,to,to.clone())); listener.onWalk(new PlayerMoveEvent(f.player,to,from)); assertEquals(((Ageable)crop.getBlockData()).getMaximumAge(),((Ageable)crop.getBlockData()).getAge());
        f.config.set("farming.enabled",false); f.loadFarming(); listener.onWalk(move); listener.onFarmlandTrample(new PlayerInteractEvent(f.player,Action.PHYSICAL,null,farmland,BlockFace.SELF));
    }

    @Test void effectsUseConfiguredParticleCountsAndQualityBonusesClamp() throws Exception {
        BlockMock crop=harvestable(); FarmingEffects.breakCrop(crop,crop.getState(),0,2); verify(f.world).spawnParticle(eq(Particle.BLOCK),any(Location.class),eq(20),eq(0.5),eq(0.5),eq(0.5),any()); FarmingEffects.breakCrop(crop,crop.getState(),2,1); FarmingEffects.trampleCrop(crop,crop.getState(),1); FarmingEffects.replant(crop,2); FarmingEffects.toolSwing(f.player);
        FarmingEffects.breakCrop(null,crop.getState(),0,1); FarmingEffects.breakCrop(crop,null,0,1); FarmingEffects.replant(null,1); FarmingEffects.toolSwing(null);
        Block nowhere=mock(Block.class); when(nowhere.getLocation()).thenReturn(new Location(null,0,0,0)); FarmingEffects.breakCrop(nowhere,crop.getState(),0,1); FarmingEffects.replant(nowhere,1); Player absent=mock(Player.class); when(absent.getLocation()).thenReturn(new Location(null,0,0,0)); when(absent.getEyeLocation()).thenReturn(new Location(null,0,0,0)); FarmingEffects.toolSwing(absent);
        f.config.set("farming.effects.tool-swing-particle",false); f.loadFarming(); clearInvocations(f.world); FarmingEffects.toolSwing(f.player); verify(f.world,never()).spawnParticle(eq(Particle.SWEEP_ATTACK),any(Location.class),anyInt());
        assertEquals(3,HoeQualityBonus.apply(3,0)); assertEquals(5,HoeQualityBonus.apply(5,100)); ThreadLocalRandom random=mock(ThreadLocalRandom.class); when(random.nextDouble()).thenReturn(0.8,0.2); try(MockedStatic<ThreadLocalRandom> rng=mockStatic(ThreadLocalRandom.class)) { rng.when(ThreadLocalRandom::current).thenReturn(random); assertEquals(3,HoeQualityBonus.apply(3,50)); assertEquals(4,HoeQualityBonus.apply(3,50)); }
    }

    @Test void harvestAreaSkipsChangedNeighboursAndStopsForAnEmptyHand() {
        BlockMock first=harvestable(),next=f.crop(1,65,0,Material.WHEAT,true);f.player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        FarmHarvestService.harvestArea(f.player,new ItemStack(Material.IRON_HOE),new FarmingToolDefinition("hoe",1,0),first);assertEquals(Material.WHEAT,first.getType());
        f.holdHoe();f.on(BlockBreakEvent.class,event->next.setType(Material.STONE));FarmHarvestService.harvestArea(f.player,f.tool(),new FarmingToolDefinition("hoe",1,0),first);assertEquals(Material.AIR,first.getType());assertEquals(Material.STONE,next.getType());assertEquals(1,((Damageable)f.tool().getItemMeta()).getDamage());
    }

    @Test void nonAgeableCropsReserveSeedsAndReplantWithinConfiguredDelayRange()throws Exception {
        ItemStack exhaustedSeed=new ItemStack(Material.BROWN_MUSHROOM); exhaustedSeed.setAmount(0);
        f.config.set("farming.replant-delay-min",1);f.config.set("farming.replant-delay-max",3);f.loadFarming();f.holdHoe();BlockMock mushroom=f.block(0,65,0,Material.BROWN_MUSHROOM);f.block(0,64,0,Material.MYCELIUM);mushroom.setDrops(List.of(new ItemStack(Material.STICK),exhaustedSeed,new ItemStack(Material.BROWN_MUSHROOM,2)));
        assertFalse(mushroom.getBlockData() instanceof Ageable);
        assertTrue(FarmHarvestService.harvestBlock(f.player,f.tool(),hoe(),mushroom,new FarmingCropDefinition(Material.BROWN_MUSHROOM,Material.BROWN_MUSHROOM)));assertEquals(1,f.inventoryCount(Material.STICK));assertEquals(1,f.inventoryCount(Material.BROWN_MUSHROOM));f.server.getScheduler().performTicks(4);assertEquals(Material.BROWN_MUSHROOM,mushroom.getType());
    }

    private BlockMock harvestable(){return f.crop(0,65,0,Material.WHEAT,true);}
    private FarmingToolDefinition hoe(){return FarmingConfig.tools().getFirst();}
    private FarmingCropDefinition wheat(){return FarmingConfig.cropFor(Material.WHEAT);}
    private static void assertDefaults(){assertTrue(FarmingConfig.enabled());assertTrue(FarmingConfig.antiTrampleEnabled());assertEquals(10,FarmingConfig.replantDelayMin());assertEquals(20,FarmingConfig.replantDelayMax());assertTrue(FarmingConfig.tools().isEmpty());assertTrue(FarmingConfig.cropsByType().isEmpty());assertTrue(FarmingConfig.isTrampleableCrop(Material.WHEAT));}

    public static final class Plot implements AutoCloseable {
        public final Path directory; public final ServerMock server; public final FieldWorld world; public final PlayerMock player; public final Cooking plugin; private final Cooking previous;
        public YamlConfiguration config=new YamlConfiguration(); public final ItemAPI items; private final MockedStatic<TLibs> tlibs; private final StaticConfigSnapshot settings;
        private final List<FoodItem> previousFoods; private final List<FoodModel> previousModels; private final Map<String,String> previousConversions;
        public Plot(Path directory)throws Exception{
            this.directory=directory; server=MockBukkit.mock(); world=spy(new FieldWorld()); world.setName("field"); server.addWorld(world); player=server.addPlayer("Farmer"); player.teleport(new Location(world,0,65,0)); previous=Cooking.plugin; plugin=mock(Cooking.class); when(plugin.getName()).thenReturn("Cooking"); when(plugin.namespace()).thenReturn("cooking"); when(plugin.isEnabled()).thenReturn(true); when(plugin.getServer()).thenReturn(server); when(plugin.getLogger()).thenReturn(Logger.getLogger("Cooking-test")); when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin("CookingFixture").getPluginLoader()); Cooking.plugin=plugin;
            settings=new StaticConfigSnapshot(FarmingConfig.class,CropsConfig.class,CropPlantingRule.class,CropCustomCropsBridge.class,QualityConfig.class); QualityConfig.apply(1,5,null,null); CropPlantingRule.configure(null); CropsConfig.apply(Map.of(),Map.of(),Map.of());
            previousFoods=FoodLoader.oList; FoodLoader.oList=new ArrayList<>(); previousModels=ModelLoader.models; ModelLoader.models=new ArrayList<>(); previousConversions=ConversionLoader.conversions; ConversionLoader.conversions=new LinkedHashMap<>();
            config.loadFromString("""
                    farming:
                      replant-delay-min: 1
                      replant-delay-max: 1
                      apply-unbreaking: false
                      tools: [{path: hoe, radius: 0}]
                      crops: [{crop: WHEAT, seed: WHEAT_SEEDS}, {crop: POTATOES, seed: POTATO}]
                    """); loadFarming(); items=mock(ItemAPI.class,RETURNS_DEEP_STUBS); tlibs=mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(items);
            when(items.getChecker().checkItemWithPath(any(),anyString())).thenAnswer(call->{ItemStack item=call.getArgument(0);String path=call.getArgument(1);if(item==null)return false;if(path.equals("hoe"))return item.getType()==Material.IRON_HOE;if(path.equals("seed"))return item.getType()==Material.WHEAT_SEEDS;if(path.startsWith("v."))return item.getType().name().equalsIgnoreCase(path.substring(2));return false;});
        }
        public void loadFarming()throws Exception{File file=directory.resolve("farming.yml").toFile();config.save(file);new FarmingLoader().load(file);}
        public void loadCrops(YamlConfiguration yaml)throws Exception{File file=directory.resolve("crops.yml").toFile();yaml.save(file);new CropsLoader().load(file);}
        public BlockMock block(int x,int y,int z,Material type){BlockMock block=world.getBlockAt(x,y,z);block.setType(type);return block;}
        public BlockMock crop(int x,int y,int z,Material type,boolean mature){BlockMock block=block(x,y,z,type);if(mature)mature(block);block.getRelative(BlockFace.DOWN).setType(Material.FARMLAND);return block;}
        public void mature(Block block){if(block.getBlockData() instanceof Ageable age){age.setAge(age.getMaximumAge());block.setBlockData(age);}}
        public void holdHoe(){player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_HOE));}
        public ItemStack tool(){return player.getInventory().getItemInMainHand();}
        public int inventoryCount(Material material){return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->i.getType()==material).mapToInt(ItemStack::getAmount).sum();}
        public int droppedCount(Material material){return world.getEntitiesByClass(Item.class).stream().map(Item::getItemStack).filter(i->i.getType()==material).mapToInt(ItemStack::getAmount).sum();}
        /** MockBukkit's addItem includes two phantom slots; model Paper's full storage boundary. */
        public Player fullStoragePlayer(){
            assertTrue(Arrays.stream(player.getInventory().getStorageContents()).allMatch(Objects::nonNull));
            Player wrapped=spy(player);PlayerInventory inventory=spy(player.getInventory());doReturn(inventory).when(wrapped).getInventory();
            doAnswer(call->{ItemStack[] input=(ItemStack[])call.getRawArguments()[0];HashMap<Integer,ItemStack> remainder=new HashMap<>();for(int i=0;i<input.length;i++)remainder.put(i,input[i].clone());return remainder;}).when(inventory).addItem(any(ItemStack[].class));return wrapped;
        }
        public FoodItem food(String id,Material model){MemoryConfiguration yaml=new MemoryConfiguration();yaml.set("name",id);FoodItem food=new FoodItem(id,yaml);food.setCategory("vegetable");food.setModel(new FoodModel(new ItemStack(model)));food.setQualityRange(1,1);FoodLoader.oList.add(food);return food;}
        public <T extends Event> void on(Class<T> type,Consumer<T> consumer){server.getPluginManager().registerEvent(type,new Listener(){},EventPriority.NORMAL,(listener,event)->consumer.accept(type.cast(event)),plugin);}
        @Override public void close()throws Exception{HandlerList.unregisterAll(plugin);MockBukkit.unmock();tlibs.close();FoodLoader.oList=previousFoods;ModelLoader.models=previousModels;ConversionLoader.conversions=previousConversions;Cooking.plugin=previous;settings.close();}
    }
    /** BlockMock supplies states and drops; this adapter implements its missing passability query. */
    public static class FieldWorld extends WorldMock {
        private final Map<String,BlockMock> fieldBlocks=new HashMap<>();
        @Override public BlockMock getBlockAt(int x,int y,int z){return fieldBlocks.computeIfAbsent(x+":"+y+":"+z,key->new BlockMock(Material.AIR,new Location(this,x,y,z)){@Override public boolean isPassable(){return !getType().isSolid();}});}
    }
    /** Restore actual static configuration; never inject private runtime states. */
    public static final class StaticConfigSnapshot implements AutoCloseable{
        private final Map<Field,Object> saved=new LinkedHashMap<>();
        public StaticConfigSnapshot(Class<?>... types)throws Exception{for(Class<?> type:types)for(Field field:type.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&!Modifier.isFinal(field.getModifiers())){field.setAccessible(true);saved.put(field,field.get(null));}}
        @Override public void close()throws Exception{for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());}
    }
}
