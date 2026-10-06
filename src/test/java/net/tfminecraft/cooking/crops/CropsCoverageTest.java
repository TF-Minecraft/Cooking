package net.tfminecraft.cooking.crops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.farming.FarmingCoverageTest.Plot;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.utils.*;
import net.tfminecraft.simplefactions.map.fertility.FertilityProvinceResolver;
import net.momirealms.customcrops.api.context.*;
import net.momirealms.customcrops.api.core.Registries;
import net.momirealms.customcrops.api.core.block.*;
import net.momirealms.customcrops.api.core.mechanic.crop.*;
import net.momirealms.customcrops.api.core.world.CustomCropsBlockState;
import net.momirealms.customcrops.api.event.*;
import net.momirealms.customcrops.api.requirement.Requirement;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class CropsCoverageTest {
    @TempDir Path directory;
    Plot f;
    private final CropCustomCropsListener listener=new CropCustomCropsListener();
    @BeforeEach void setUp()throws Exception{f=new Plot(directory);}
    @AfterEach void tearDown()throws Exception{f.close();}

    @Test void invalidNanAffectionIsRejectedByGrowthProbability(){assertThrows(IllegalArgumentException.class,()->CropGrowthChance.growChance(50,Double.NaN));}
    @Test void invalidNanAffectionFallsBackWhenLoadingCropYaml()throws Exception{
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("crops.wheat.block","WHEAT");yaml.set("crops.wheat.affection",Double.NaN);f.loadCrops(yaml);assertEquals(0.5,CropsConfig.crop("wheat").affection());
    }
    @Test void invalidNanAffectionCannotPoisonHarvestQualityWeights(){assertArrayEquals(CropHarvestQuality.weights(50,0.5),CropHarvestQuality.weights(50,Double.NaN),1e-9);}

    @Test void cropLoaderParsesWeightsSourcesMaterialNamesAndDefaults()throws Exception{
        YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString("""
                growth-gate: {enabled: false}
                harvest-quality:
                  rich: {'1': 10, '5': 40, '0': 99, '6': 99, bad: 100}
                  poor: {'1': -2, '5': 1}
                crops:
                  CARROT: {source: ' CustomCrops ', seed: seed, affection: 0.8, block: ' carrots '}
                  wheat: {affection: 0, block: WHEAT}
                  high: {affection: 2, block: INVALID}
                  blank: {block: ' '}
                  empty: {}
                  ignored: scalar
                """);f.loadCrops(yaml);
        assertFalse(CropsConfig.growthGateEnabled());assertEquals(10,CropsConfig.harvestRich().get(1));assertEquals(40,CropsConfig.harvestRich().get(5));assertEquals(16,CropsConfig.harvestRich().get(2));assertEquals(0,CropsConfig.harvestPoor().get(1));assertEquals(5,CropsConfig.crops().size());
        CropDefinition carrot=CropsConfig.crop("CARROT");assertEquals("carrot",carrot.id());assertEquals("customcrops",carrot.source());assertEquals("seed",carrot.seed());assertEquals(0.8,carrot.affection());assertEquals(Material.CARROTS,carrot.block());assertSame(carrot,CropsConfig.byBlock(Material.CARROTS));assertEquals(0.5,CropsConfig.crop("wheat").affection());assertEquals(0.5,CropsConfig.crop("high").affection());assertNull(CropsConfig.crop("high").block());assertNull(CropsConfig.crop(null));assertNull(CropsConfig.byBlock(null));
        f.loadCrops(new YamlConfiguration());assertTrue(CropsConfig.growthGateEnabled());assertTrue(CropsConfig.crops().isEmpty());assertEquals(8,CropsConfig.harvestRich().get(1));
        new CropsLoader().load(directory.resolve("missing.yml").toFile());assertTrue(CropsConfig.growthGateEnabled());assertTrue(CropPlantingRule.requiresOpenSky(Material.WHEAT));
    }

    @Test void cropDefinitionsAndFoodClassificationAreImmutableAndLocaleIndependent(){
        Locale old=Locale.getDefault();try{Locale.setDefault(Locale.forLanguageTag("tr-TR"));CropDefinition rice=new CropDefinition("rice","vanilla","seed",0.5,Material.WHEAT);Map<String,CropDefinition> crops=new HashMap<>(Map.of("rice",rice));CropsConfig.apply(null,null,crops);crops.clear();assertSame(rice,CropsConfig.crop("RICE"));assertThrows(UnsupportedOperationException.class,()->CropsConfig.crops().clear());}finally{Locale.setDefault(old);}
        assertEquals("vanilla",CropsConfig.normalizeSource(null));assertEquals("vanilla",CropsConfig.normalizeSource("unknown"));CropsConfig.apply(Map.of(),Map.of(),null);assertTrue(CropsConfig.crops().isEmpty());
        assertFalse(CropsConfig.isFarmFood(null));assertFalse(CropsConfig.isFarmFood(" "));assertFalse(CropsConfig.isFarmConversionInput(null));assertFalse(CropsConfig.isFarmConversionInput(" "));
        for(String seed:List.of("v.wheat","v.carrot","v.potato","v.beetroot","v.apple","ia.tfmc_cooking:rice","IA.TFMC_COOKING:TOMATO"))assertTrue(CropsConfig.isFarmConversionInput(seed),seed);
        for(String other:List.of("v.beef","ia.other:carrot","ia.tfmc_cooking:butter","ia.tfmc_cooking:SALT","ia.tfmc_cooking:dough","ia.tfmc_cooking:cup_of_milk"))assertFalse(CropsConfig.isFarmConversionInput(other),other);
        ConversionLoader.conversions.put("v.beef","grain(type=grain)");ConversionLoader.conversions.put("v.wheat",null);ConversionLoader.conversions.put("v.carrot","other(type=other)");assertFalse(CropsConfig.isFarmFood("grain(type=grain)"));ConversionLoader.conversions.put("v.wheat","c.grain(type=grain)");assertTrue(CropsConfig.isFarmFood("grain(type=grain)"));
    }

    @Test void plantingRulesRecognizeGreenhousesExplicitCoversExemptionsAndDisabledGate()throws Exception{
        Location location=new Location(f.world,3,65,4);assertTrue(CropPlantingRule.hasOpenSky(location));assertFalse(CropPlantingRule.hasOpenSky(new Location(null,0,65,0)));
        f.block(3,70,4,Material.STONE);assertFalse(CropPlantingRule.hasOpenSky(location));for(Material glass:List.of(Material.GLASS,Material.TINTED_GLASS,Material.BLUE_STAINED_GLASS_PANE)){f.block(3,70,4,glass);assertTrue(CropPlantingRule.hasOpenSky(location));}
        YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString("""
                planting:
                  allow-glass-roofs: false
                  allowed-cover: [OAK_LEAVES, INVALID, DIAMOND]
                  exempt-vanilla: [WHEAT]
                  exempt-custom: [RICE]
                """);f.loadCrops(yaml);assertFalse(CropPlantingRule.hasOpenSky(location));f.block(3,70,4,Material.OAK_LEAVES);assertTrue(CropPlantingRule.hasOpenSky(location));assertFalse(CropPlantingRule.requiresOpenSky(Material.WHEAT));assertTrue(CropPlantingRule.requiresOpenSky(Material.NETHER_WART));assertTrue(CropPlantingRule.requiresOpenSky(Material.BROWN_MUSHROOM));assertTrue(CropPlantingRule.requiresOpenSky(Material.RED_MUSHROOM));assertFalse(CropPlantingRule.requiresOpenSky(Material.STONE));assertFalse(CropPlantingRule.customRequiresOpenSky("rice"));assertTrue(CropPlantingRule.customRequiresOpenSky(null));
        yaml.set("planting.require-open-sky",false);f.loadCrops(yaml);assertFalse(CropPlantingRule.requiresOpenSky(Material.CARROTS));assertFalse(CropPlantingRule.customRequiresOpenSky("tomato"));
    }

    @Test void plantingEventsDenyCoveredFoodCropsAndAllowOpenSkyOrExemptSpecies(){
        Block crop=f.crop(0,65,0,Material.WHEAT,false);f.block(0,68,0,Material.STONE);var event=plant(crop);new CropPlantingListener().onPlant(event);assertTrue(event.isCancelled());assertEquals(CropPlantingListener.DENIAL_MESSAGE,f.player.nextMessage());
        f.block(0,68,0,Material.AIR);var open=plant(crop);new CropPlantingListener().onPlant(open);assertFalse(open.isCancelled());crop.setType(Material.NETHER_WART);f.block(0,68,0,Material.STONE);var exempt=plant(crop);new CropPlantingListener().onPlant(exempt);assertFalse(exempt.isCancelled());
        CropConfig config=config("carrot");var custom=new CropPlantEvent(f.player,new ItemStack(Material.WHEAT_SEEDS),EquipmentSlot.HAND,crop.getLocation(),config,null,0);listener.onPlant(custom);assertTrue(custom.isCancelled());assertEquals(CropPlantingListener.DENIAL_MESSAGE,f.player.nextMessage());
        var yeast=new CropPlantEvent(f.player,new ItemStack(Material.WHEAT_SEEDS),EquipmentSlot.HAND,crop.getLocation(),config("yeast"),null,0);listener.onPlant(yeast);assertFalse(yeast.isCancelled());
    }

    @Test void fertilityUsesOptionalPluginAndSafelyHandlesMissingOrIncompatibleMapApi(){
        Location location=new Location(f.world,0,65,0);assertFalse(CropFertility.mapActive());assertEquals(0,CropFertility.at(location));Plugin map=MockBukkit.createMockPlugin("SimpleFactions");
        try(MockedStatic<FertilityProvinceResolver> resolver=mockStatic(FertilityProvinceResolver.class)){
            assertFalse(CropFertility.mapActive());assertEquals(0,CropFertility.at(location));resolver.when(FertilityProvinceResolver::isActive).thenReturn(true);resolver.when(()->FertilityProvinceResolver.fertilityAt(location)).thenReturn(120,-2,52);assertTrue(CropFertility.mapActive());assertEquals(100,CropFertility.at(location));assertEquals(0,CropFertility.at(location));assertEquals(52,CropFertility.at(location));
            resolver.when(FertilityProvinceResolver::isActive).thenThrow(new IllegalStateException("not ready"));assertFalse(CropFertility.mapActive());assertEquals(0,CropFertility.at(location));f.server.getPluginManager().disablePlugin(map);assertFalse(CropFertility.mapActive());assertEquals(0,CropFertility.at(location));
        }
    }

    @Test void growthProbabilitiesAndGatesAreDeterministicAtBoundaries(){
        assertEquals(0,CropGrowthChance.growChance(-1,0.5));assertEquals(1,CropGrowthChance.growChance(101,0.5));assertEquals(0.5,CropGrowthChance.growChance(25,0.5));assertThrows(IllegalArgumentException.class,()->CropGrowthChance.growChance(50,0));assertThrows(IllegalArgumentException.class,()->CropGrowthChance.growChance(50,2));
        Random random=mock(Random.class);when(random.nextDouble()).thenReturn(0.2,0.8);assertTrue(CropGrowthChance.rollGrows(25,0.5,random));assertFalse(CropGrowthChance.rollGrows(25,0.5,random));assertTrue(CropGrowthChance.rollGrows(100,0.5,random));assertFalse(CropGrowthChance.rollGrows(0,0.5,random));
        assertTrue(CropGrowthGate.allowsGrowth(0,OptionalDouble.of(0.5),false,true,random));assertTrue(CropGrowthGate.allowsGrowth(0,OptionalDouble.of(0.5),true,false,random));assertTrue(CropGrowthGate.allowsGrowth(0,null,true,true,random));assertTrue(CropGrowthGate.allowsGrowth(0,OptionalDouble.empty(),true,true,random));assertTrue(CropGrowthGate.allowsGrowth(0,OptionalDouble.of(0.5),true,true,null));assertFalse(CropGrowthGate.allowsGrowth(0,OptionalDouble.of(0.5),true,true,random));assertTrue(CropGrowthGate.allowsGrowth(100,OptionalDouble.of(0.5),true,true,random));when(random.nextDouble()).thenReturn(0.2);assertTrue(CropGrowthGate.allowsGrowth(25,OptionalDouble.of(0.5),true,true,random));
        configureCustom();assertEquals(0.5,CropGrowthGate.resolveCustomAffection("carrot",null).orElseThrow());assertEquals(0.8,CropGrowthGate.resolveCustomAffection("carrot",OptionalDouble.of(0.8)).orElseThrow());assertTrue(CropGrowthGate.resolveCustomAffection("missing",OptionalDouble.empty()).isEmpty());
    }

    @Test void growthEventsCancelOnlyWhenAnActiveFertilityMapRejectsTheCrop(){
        configureCustom();Location location=new Location(f.world,0,65,0);Block crop=f.crop(0,65,0,Material.CARROTS,false);
        try(MockedStatic<CropFertility> fertility=mockStatic(CropFertility.class)){
            fertility.when(CropFertility::mapActive).thenReturn(true);fertility.when(()->CropFertility.at(any())).thenReturn(0);var denied=new BlockGrowEvent(crop,crop.getState());new CropGrowthListener().onBlockGrow(denied);assertTrue(denied.isCancelled());assertFalse(CropGrowthGate.allowsCustom("carrot",location,OptionalDouble.empty()));
            fertility.when(()->CropFertility.at(any())).thenReturn(100);var allowed=new BlockGrowEvent(crop,crop.getState());new CropGrowthListener().onBlockGrow(allowed);assertFalse(allowed.isCancelled());assertTrue(CropGrowthGate.allowsCustom("carrot",location,OptionalDouble.empty()));assertFalse(CropGrowthGate.shouldCancelVanilla(Material.STONE,location));
        }
    }

    @Test void qualityWeightsRemainNormalizedAndRollEveryBoundary(){
        double[] weights=CropHarvestQuality.weights(-20,0.5);double total=0;for(int i=1;i<=5;i++){assertTrue(weights[i]>0);total+=weights[i];}assertEquals(1,total,1e-9);assertArrayEquals(CropHarvestQuality.weights(100,0.5),CropHarvestQuality.weights(200,0.5));assertArrayEquals(CropHarvestQuality.weights(50,0.5),CropHarvestQuality.weights(50,0));assertArrayEquals(CropHarvestQuality.weights(50,0.5),CropHarvestQuality.weights(50,2));
        Random random=mock(Random.class);when(random.nextDouble()).thenReturn(0.0,0.99);assertEquals(1,CropHarvestQuality.roll(0,0.5,random));assertEquals(5,CropHarvestQuality.roll(0,0.5,random));int fallback=CropHarvestQuality.roll(50,0.5,null);assertTrue(fallback>=1&&fallback<=5);assertTrue(CropHarvestQuality.roll("missing",new Location(f.world,0,65,0),null)>=1);
    }

    @Test void itemConversionPreservesSeedsAndAlreadyCookedFoodAndConvertsAmounts(){
        configureCustom();CropDefinition crop=CropsConfig.crop("carrot");FoodItem template=f.food("carrot",Material.BREAD);ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot;origin=Carrot)");
        assertNull(CropHarvestItems.convertDrop(null,crop,2));assertNull(CropHarvestItems.rewriteCustomDrop(null,crop,2));ItemStack air=new ItemStack(Material.AIR);assertSame(air,CropHarvestItems.convertDrop(air,crop,2));assertSame(air,CropHarvestItems.rewriteCustomDrop(air,crop,2));
        for(Material seed:List.of(Material.WHEAT_SEEDS,Material.BEETROOT_SEEDS,Material.MELON_SEEDS,Material.PUMPKIN_SEEDS)){ItemStack stack=new ItemStack(seed);assertSame(stack,CropHarvestItems.convertDrop(stack,crop,5));}
        ItemStack original=new ItemStack(Material.CARROT,4);ItemStack converted=CropHarvestItems.convertDrop(original,crop,9);assertEquals(4,converted.getAmount());assertEquals(5,FoodItem.fromItem(converted).getQualityMin());assertEquals(4,original.getAmount());assertSame(converted,CropHarvestItems.rewriteCustomDrop(converted,crop,1));
        ItemStack seed=new ItemStack(Material.WHEAT_SEEDS);assertSame(seed,CropHarvestItems.rewriteCustomDrop(seed,crop,5));assertFalse(CropHarvestItems.isConfiguredSeed(null,crop));assertFalse(CropHarvestItems.isConfiguredSeed(seed,null));assertFalse(CropHarvestItems.isConfiguredSeed(seed,new CropDefinition("none","customcrops",null,0.5,null)));assertFalse(CropHarvestItems.isConfiguredSeed(seed,new CropDefinition("none","customcrops"," ",0.5,null)));
        when(f.items.getChecker().checkItemWithPath(seed,"broken")).thenThrow(new IllegalStateException("missing provider"));assertFalse(CropHarvestItems.isConfiguredSeed(seed,new CropDefinition("none","customcrops","broken",0.5,null)));
        ItemStack stone=new ItemStack(Material.STONE);assertSame(stone,CropHarvestItems.convertDrop(stone,crop,3));ConversionLoader.conversions.put("v.carrot","invalid");assertSame(original,CropHarvestItems.convertDrop(original,crop,3));ConversionLoader.conversions.put("v.carrot","vegetable(type=missing)");assertSame(original,CropHarvestItems.convertDrop(original,crop,3));
        ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot)");template.setModel(null);try(MockedStatic<ItemBuilder> builder=mockStatic(ItemBuilder.class)){assertSame(original,CropHarvestItems.convertDrop(original,crop,3));}
    }

    @Test void fertilityWrapperPreservesDelegatePointsAndRequiresLocationForRealCrops(){
        GrowCondition delegate=mock(GrowCondition.class);when(delegate.pointToAdd()).thenReturn(3);CookingFertilityGrowCondition wrapped=new CookingFertilityGrowCondition(delegate);assertEquals(3,wrapped.pointToAdd());Context<CustomCropsBlockState> context=mock(Context.class);assertFalse(wrapped.isMet(context));when(delegate.isMet(context)).thenReturn(true);assertTrue(wrapped.isMet(context));assertTrue(CookingFertilityGrowCondition.fertilityAllows(null));
        CustomCropsBlockState state=mock(CustomCropsBlockState.class);when(context.holder()).thenReturn(state);when(state.type()).thenReturn(mock(CustomCropsBlock.class));assertTrue(wrapped.isMet(context));CropBlock cropBlock=mock(CropBlock.class);when(state.type()).thenReturn(cropBlock);assertTrue(wrapped.isMet(context));CropConfig config=config(null);when(cropBlock.config(state)).thenReturn(config);assertTrue(wrapped.isMet(context));when(config.id()).thenReturn("carrot");assertFalse(wrapped.isMet(context));
        when(context.arg(ContextKeys.LOCATION)).thenReturn(new Location(f.world,0,65,0));try(MockedStatic<CropGrowthGate> gate=mockStatic(CropGrowthGate.class)){gate.when(()->CropGrowthGate.allowsCustom(eq("carrot"),any(),eq(OptionalDouble.empty()))).thenReturn(true);assertTrue(wrapped.isMet(context));assertTrue(new CookingFertilityGrowCondition(new CookingFertilityGrowCondition(wrapped)).isMet(context));}
    }

    @Test void bridgeWrapsRegisteredConditionsOnceAndSuppliesAnUnconditionalDelegate(){
        List<CropConfig> saved=new ArrayList<>();Registries.CROP.forEach(saved::add);Registries.CROP.clear();try{
            GrowCondition original=new GrowCondition(new Requirement[0],4);CropConfig configured=CropConfig.builder().id("carrot").seed(List.of("seed")).stages(List.of()).growConditions(new GrowCondition[]{original,null}).build();CropConfig empty=CropConfig.builder().id("empty").seed(List.of()).stages(List.of()).growConditions(new GrowCondition[0]).build();CropConfig absent=CropConfig.builder().id("absent").seed(List.of()).stages(List.of()).growConditions(null).build();
            CropConfig foreign=config("foreign");when(foreign.growConditions()).thenReturn(new GrowCondition[0]);Registries.CROP.register("foreign",foreign);
            Registries.CROP.register("carrot",configured);Registries.CROP.register("empty",empty);Registries.CROP.register("absent",absent);Registries.CROP.register("ignored",null);CropCustomCropsBridge.injectFertility();
            assertInstanceOf(CookingFertilityGrowCondition.class,configured.growConditions()[0]);assertEquals(4,configured.growConditions()[0].pointToAdd());assertNull(configured.growConditions()[1]);assertEquals(1,empty.growConditions().length);assertEquals(1,empty.growConditions()[0].pointToAdd());assertEquals(1,absent.growConditions().length);
            GrowCondition first=configured.growConditions()[0];CropCustomCropsBridge.injectFertility();assertSame(first,configured.growConditions()[0]);
        }finally{Registries.CROP.clear();for(CropConfig crop:saved)Registries.CROP.register(crop.id(),crop);}
    }

    @Test void bridgeHandlesPluginAvailabilityReloadSchedulingAndUnreadyExternalRegistry(){
        CropCustomCropsBridge bridge=new CropCustomCropsBridge();CropCustomCropsBridge.tryRegister(f.plugin);Plugin other=MockBukkit.createMockPlugin("Other");bridge.onPluginEnable(new PluginEnableEvent(other));Plugin custom=MockBukkit.createMockPlugin("CustomCrops");f.server.getPluginManager().disablePlugin(custom);CropCustomCropsBridge.tryRegister(f.plugin);f.server.getPluginManager().enablePlugin(custom);CropCustomCropsBridge.tryRegister(null);CropCustomCropsBridge.tryRegister(f.plugin);CropCustomCropsBridge.tryRegister(f.plugin);bridge.onPluginEnable(new PluginEnableEvent(custom));bridge.onCustomCropsReload(new CustomCropsReloadEvent(null));f.server.getScheduler().performOneTick();
        List<CropConfig> saved=new ArrayList<>();Registries.CROP.forEach(saved::add);Registries.CROP.clear();try{CropConfig broken=config("unready");when(broken.growConditions()).thenThrow(new IllegalStateException("reloading"));Registries.CROP.register("unready",broken);assertDoesNotThrow(CropCustomCropsBridge::injectFertility);Cooking.plugin=null;assertDoesNotThrow(()->bridge.onCustomCropsReload(new CustomCropsReloadEvent(null)));Cooking.plugin=f.plugin;}finally{Registries.CROP.clear();for(CropConfig crop:saved)Registries.CROP.register(crop.id(),crop);}
    }

    @Test void inventoryRewriteConvertsOnlyNewHarvestAndPreservesPreexistingProduce(){
        configureCustom();f.food("carrot",Material.BREAD);ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot;origin=Carrot)");f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,3));
        try(MockedStatic<CropHarvestQuality> quality=mockStatic(CropHarvestQuality.class)){quality.when(()->CropHarvestQuality.roll(anyString(),any(),any())).thenReturn(4);listener.onBreak(breakEvent(config("carrot"),new Location(f.world,0,65,0),f.player));f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,5));f.server.getScheduler().performOneTick();}
        assertEquals(3,f.inventoryCount(Material.CARROT),"Harvesting two carrots must not convert three carrots already held");assertEquals(2,f.inventoryCount(Material.BREAD));
    }

    @Test void inventoryRewriteDoesNotConvertAStackThatShrankDuringHarvest(){
        configureCustom();f.food("carrot",Material.BREAD);ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot;origin=Carrot)");f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,5));listener.onBreak(breakEvent(config("carrot"),new Location(f.world,0,65,0),f.player));f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,3));f.server.getScheduler().performOneTick();assertEquals(3,f.inventoryCount(Material.CARROT));assertEquals(0,f.inventoryCount(Material.BREAD));
    }

    @Test void customDropsUseNearbySameWorldHarvestQualityAndPreserveSeedsAndFood(){
        configureCustom();f.food("carrot",Material.BREAD);ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot;origin=Carrot)");Location location=new Location(f.world,0,65,0);Item item=f.world.dropItem(location.clone().add(0.5,0.5,0.5),new ItemStack(Material.CARROT,2));Item seed=f.world.dropItem(location.clone().add(0.5,0.5,0.5),new ItemStack(Material.WHEAT_SEEDS));Item far=f.world.dropItem(new Location(f.world,10,65,0),new ItemStack(Material.CARROT));
        try(MockedStatic<CropHarvestQuality> quality=mockStatic(CropHarvestQuality.class)){quality.when(()->CropHarvestQuality.roll(anyString(),any(),any())).thenReturn(4);listener.onBreak(breakEvent(config("carrot"),location,null));listener.onItemSpawn(new ItemSpawnEvent(item));listener.onItemSpawn(new ItemSpawnEvent(seed));listener.onItemSpawn(new ItemSpawnEvent(far));}
        assertEquals(Material.BREAD,item.getItemStack().getType());assertEquals(2,item.getItemStack().getAmount());assertEquals(4,FoodItem.fromItem(item.getItemStack()).getQualityMin());assertEquals(Material.WHEAT_SEEDS,seed.getItemStack().getType());assertEquals(Material.CARROT,far.getItemStack().getType());
        World other=f.server.addSimpleWorld("other");Item elsewhere=other.dropItem(new Location(other,0.5,65.5,0.5),new ItemStack(Material.CARROT));listener.onItemSpawn(new ItemSpawnEvent(elsewhere));assertEquals(Material.CARROT,elsewhere.getItemStack().getType());
    }

    @Test void customHarvestIgnoresUnconfiguredCropsBoneMealAndImmatureStates(){
        configureCustom();Location location=new Location(f.world,0,65,0);listener.onBreak(breakEvent(null,location,null));listener.onBreak(breakEvent(config(null),location,null));listener.onBreak(breakEvent(config("unknown"),location,null));listener.onBreak(breakEvent(config("wheat"),location,null));CropConfig config=config("carrot");CustomCropsBlockState state=mock(CustomCropsBlockState.class);CropBlock type=mock(CropBlock.class);when(state.type()).thenReturn(type);when(type.point(state)).thenReturn(1);when(config.maxPoints()).thenReturn(2);
        listener.onInteract(interact(config,state,new ItemStack(Material.BONE_MEAL)));listener.onInteract(interact(config,null,null));when(state.type()).thenReturn(mock(CustomCropsBlock.class));listener.onInteract(interact(config,state,null));when(state.type()).thenReturn(type);listener.onInteract(interact(config,state,null));listener.onInteract(interact(config("unknown"),state,null));
        when(type.point(state)).thenReturn(2);listener.onInteract(interact(config,state,new ItemStack(Material.STICK)));listener.onInteract(interact(config,state,null));listener.onBreak(breakEvent(config,null,null));listener.onBreak(breakEvent(config,new Location(null,0,0,0),null));Cooking.plugin=null;listener.onBreak(breakEvent(config,location,null));Cooking.plugin=f.plugin;f.server.getScheduler().performOneTick();
    }

    @Test void pendingHarvestExpiresAndScheduledInventoryRewriteSkipsOfflinePlayers(){
        configureCustom();Location location=new Location(f.world,0,65,0);listener.onBreak(breakEvent(config("carrot"),location,f.player));f.player.disconnect();f.server.getScheduler().performOneTick();f.player.reconnect();
        Item item=f.world.dropItem(location.clone().add(0.5,0.5,0.5),new ItemStack(Material.CARROT));LockSupport.parkNanos(250_000_000);listener.onItemSpawn(new ItemSpawnEvent(item));assertEquals(Material.CARROT,item.getItemStack().getType());f.server.getScheduler().performTicks(2);
        Item empty=mock(Item.class);when(empty.getItemStack()).thenReturn(null);listener.onItemSpawn(new ItemSpawnEvent(empty));when(empty.getItemStack()).thenReturn(new ItemStack(Material.AIR));listener.onItemSpawn(new ItemSpawnEvent(empty));when(empty.getItemStack()).thenReturn(new ItemStack(Material.CARROT));when(empty.getLocation()).thenReturn(null);listener.onItemSpawn(new ItemSpawnEvent(empty));when(empty.getLocation()).thenReturn(new Location(null,0,0,0));listener.onItemSpawn(new ItemSpawnEvent(empty));
    }

    @Test void qualityRollHandlesFloatingPointRoundingAtTheLargestValidRandomDraw(){
        Map<Integer,Double> weights=Map.of(1,33.0,2,17.0,3,33.0,4,32.0,5,19.0);CropsConfig.apply(weights,weights,Map.of());Random random=mock(Random.class);when(random.nextDouble()).thenReturn(Math.nextDown(1.0));assertEquals(5,CropHarvestQuality.roll(100,0.5,random));
    }

    @Test void newlyConvertedHarvestDropsOnlyItsOverflowWhenInventoryIsFull(){
        configureCustom();f.food("carrot",Material.BREAD);ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot;origin=Carrot)");f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,3));for(int i=1;i<36;i++)f.player.getInventory().setItem(i,new ItemStack(Material.STONE,64));Player full=f.fullStoragePlayer();
        listener.onBreak(breakEvent(config("carrot"),new Location(f.world,0,65,0),full));f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,5));
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(full);f.server.getScheduler().performOneTick();}
        assertEquals(3,f.inventoryCount(Material.CARROT));assertEquals(2,f.droppedCount(Material.BREAD));assertEquals(0,f.inventoryCount(Material.BREAD));
    }

    @Test void changedSlotsOnlyConvertRecognizedNewProduce(){
        configureCustom();f.food("carrot",Material.BREAD);ConversionLoader.conversions.put("v.carrot","vegetable(type=carrot;origin=Carrot)");f.player.getInventory().setItem(0,new ItemStack(Material.CARROT,3));f.player.getInventory().setItem(1,new ItemStack(Material.STONE));f.player.getInventory().setItem(2,new ItemStack(Material.AIR));
        listener.onBreak(breakEvent(config("carrot"),new Location(f.world,0,65,0),f.player));f.player.getInventory().setItem(1,new ItemStack(Material.CARROT,2));f.player.getInventory().setItem(3,new ItemStack(Material.STONE));f.player.getInventory().setItem(4,new ItemStack(Material.WHEAT_SEEDS));f.server.getScheduler().performOneTick();
        assertEquals(3,f.inventoryCount(Material.CARROT));assertEquals(2,f.inventoryCount(Material.BREAD));assertEquals(1,f.inventoryCount(Material.STONE));assertEquals(1,f.inventoryCount(Material.WHEAT_SEEDS));
    }

    private void configureCustom(){CropsConfig.apply(Map.of(),Map.of(),Map.of("carrot",new CropDefinition("carrot","customcrops","seed",0.5,Material.CARROTS),"wheat",new CropDefinition("wheat","vanilla","v.wheat_seeds",0.5,Material.WHEAT)));}
    private CropConfig config(String id){CropConfig config=mock(CropConfig.class);when(config.id()).thenReturn(id);return config;}
    private net.momirealms.customcrops.api.event.CropBreakEvent breakEvent(CropConfig config,Location location,Entity player){return new net.momirealms.customcrops.api.event.CropBreakEvent(player,null,config,"stage",location,null,null);}
    private CropInteractEvent interact(CropConfig config,CustomCropsBlockState state,ItemStack item){return new CropInteractEvent(f.player,item,new Location(f.world,0,65,0),state,EquipmentSlot.HAND,config,"stage");}
    private BlockPlaceEvent plant(Block block){return new BlockPlaceEvent(block,block.getState(),block.getRelative(BlockFace.DOWN),new ItemStack(Material.WHEAT_SEEDS),f.player,true,EquipmentSlot.HAND);}
}
