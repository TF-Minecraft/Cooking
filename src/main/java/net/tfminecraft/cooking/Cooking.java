package net.tfminecraft.cooking;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.brewery.BreweryBridge;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.ModelLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.loader.ConfigLoader;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.loader.CraftingStationLoader;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.farming.FarmHarvestListener;
import net.tfminecraft.cooking.farming.FarmTrampleListener;
import net.tfminecraft.cooking.farming.FarmingLoader;
import net.tfminecraft.cooking.husbandry.HusbandryAnimalsCommand;
import net.tfminecraft.cooking.husbandry.HusbandryBreedListener;
import net.tfminecraft.cooking.husbandry.HusbandryCareListener;
import net.tfminecraft.cooking.husbandry.HusbandryConfig;
import net.tfminecraft.cooking.husbandry.HusbandryDamageListener;
import net.tfminecraft.cooking.husbandry.HusbandryDeathListener;
import net.tfminecraft.cooking.husbandry.HusbandryHarvestListener;
import net.tfminecraft.cooking.husbandry.HusbandryInspectListener;
import net.tfminecraft.cooking.husbandry.HusbandryLifecycleListener;
import net.tfminecraft.cooking.husbandry.HusbandryMountListener;
import net.tfminecraft.cooking.husbandry.HusbandryNeuterListener;
import net.tfminecraft.cooking.husbandry.HusbandryTamingListener;
import net.tfminecraft.cooking.crops.CropCustomCropsBridge;
import net.tfminecraft.cooking.fishing.CustomFishingBridge;
import net.tfminecraft.cooking.fishing.LegacyFishScan;
import net.tfminecraft.cooking.fishing.CustomFishingCatalog;
import net.tfminecraft.cooking.crops.CropGrowthListener;
import net.tfminecraft.cooking.crops.CropPlantingListener;
import net.tfminecraft.cooking.crops.CropsLoader;
import net.tfminecraft.cooking.husbandry.HusbandryLoader;
import net.tfminecraft.cooking.husbandry.HusbandryLocator;
import net.tfminecraft.cooking.husbandry.HusbandryRepository;
import net.tfminecraft.cooking.husbandry.HusbandryTickTask;
import net.tfminecraft.cooking.loader.PermissionEffectsLoader;
import net.tfminecraft.cooking.loader.CompositionConfigLoader;
import net.tfminecraft.cooking.loader.QualityConfigLoader;
import net.tfminecraft.cooking.manager.BundleFoodStacker;
import net.tfminecraft.cooking.manager.CommandManager;
import net.tfminecraft.cooking.manager.ConversionManager;
import net.tfminecraft.cooking.manager.CookingManager;
import net.tfminecraft.cooking.manager.CraftingManager;
import net.tfminecraft.cooking.manager.PlateManager;
import net.tfminecraft.cooking.manager.TagManager;
import net.tfminecraft.cooking.baking.BakingTrayAging;
import net.tfminecraft.cooking.baking.BakingTrayHandler;
import net.tfminecraft.cooking.baking.BakingTrayLoader;
import net.tfminecraft.cooking.pizza.PizzaConfig;
import net.tfminecraft.cooking.pizza.PizzaHandler;
import net.tfminecraft.cooking.condiment.CondimentConfig;
import net.tfminecraft.cooking.condiment.SapTapListener;
import net.tfminecraft.cooking.salad.SaladConfig;
import net.tfminecraft.cooking.salad.SaladHandler;
import net.tfminecraft.cooking.sandwich.SandwichConfig;
import net.tfminecraft.cooking.sandwich.SandwichHandler;
import net.tfminecraft.cooking.milling.MillingRecipeLoader;
import net.tfminecraft.cooking.trough.TroughAging;
import net.tfminecraft.cooking.trough.TroughHandler;
import net.tfminecraft.cooking.churn.ButterChurnAging;
import net.tfminecraft.cooking.churn.ButterChurnHandler;
import net.tfminecraft.cooking.cup.DrinkConsumeListener;
import net.tfminecraft.cooking.cup.MilkBucketConverter;
import net.tfminecraft.cooking.liquid.LiquidContainerAging;
import net.tfminecraft.cooking.liquid.LiquidContainerHandler;
import net.tfminecraft.cooking.hook.MeatHookHandler;
import net.tfminecraft.cooking.sausagemaker.SausageMakerHandler;
import net.tfminecraft.cooking.milling.MillingStoneHandler;
import net.tfminecraft.cooking.mixing.MixingBowlHandler;
import net.tfminecraft.cooking.heat.HeatPickupGuard;
import net.tfminecraft.cooking.mmoitems.MMOItemsSupport;
import net.tfminecraft.cooking.nutrition.BowlEatHandler;
import net.tfminecraft.cooking.nutrition.FoodConsumeListener;
import net.tfminecraft.cooking.nutrition.FoodLevelChangeGuard;
import net.tfminecraft.cooking.nutrition.NutritionDrainTask;
import net.tfminecraft.cooking.nutrition.NutritionLifecycleListener;
import net.tfminecraft.cooking.nutrition.RegenBlocker;
import net.tfminecraft.cooking.nutrition.SaturationGuard;
import net.tfminecraft.cooking.oven.OvenBurnManager;
import net.tfminecraft.cooking.oven.OvenCavityHandler;
import net.tfminecraft.cooking.oven.OvenCavityManager;
import net.tfminecraft.cooking.oven.OvenHandler;
import net.tfminecraft.cooking.oven.OvenLifecycleHandler;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.cooking.utils.ItemsAdderReloadHook;
import net.tfminecraft.tlibs.itemscan.ItemScanService;
import net.tfminecraft.tlibs.character.Characters;

public class Cooking extends JavaPlugin {

    public static Cooking plugin;
    private boolean incompatibleCharacterApi;

    private final CommandManager commands = new CommandManager();
    private final BreweryBridge breweryBridge = new BreweryBridge(this);

    private final ModelLoader modelLoader = new ModelLoader();
    private final FoodLoader foodLoader = new FoodLoader();
    private final CraftingStationLoader stationLoader = new CraftingStationLoader();
    private final TrackLoader trackLoader = new TrackLoader();
    private final ConfigLoader configLoader = new ConfigLoader();
    private final ConversionLoader conversionLoader = new ConversionLoader();
    private final CarveSequenceLoader carveSequenceLoader = new CarveSequenceLoader();
    private final NamingLoader namingLoader = new NamingLoader();
    private final QualityConfigLoader qualityConfigLoader = new QualityConfigLoader();
    private final CompositionConfigLoader compositionConfigLoader = new CompositionConfigLoader();
    private final PermissionEffectsLoader permissionEffectsLoader = new PermissionEffectsLoader();
    private final FarmingLoader farmingLoader = new FarmingLoader();
    private final CropsLoader cropsLoader = new CropsLoader();
    private final HusbandryLoader husbandryLoader = new HusbandryLoader();
    private HusbandryRepository husbandryRepository;
    private boolean husbandryEnabled;

    private final TagManager tagManager = new TagManager();
    private final LegacyFishScan legacyFishScan = new LegacyFishScan();
    private final CookingManager cookingManager = new CookingManager();
    private final PlateManager plateManager = new PlateManager();
    private final MixingBowlHandler mixingBowlHandler = new MixingBowlHandler();
    private final ButterChurnHandler butterChurnHandler = new ButterChurnHandler();
    private final LiquidContainerHandler liquidContainerHandler = new LiquidContainerHandler();
    private final MeatHookHandler meatHookHandler = new MeatHookHandler();
    private final SausageMakerHandler sausageMakerHandler = new SausageMakerHandler();
    private final BakingTrayLoader bakingTrayLoader = new BakingTrayLoader();
    private final MillingRecipeLoader millingRecipeLoader = new MillingRecipeLoader();
    private final BakingTrayHandler bakingTrayHandler = new BakingTrayHandler();
    private final SandwichConfig sandwichConfig = new SandwichConfig();
    private final CondimentConfig condimentConfig = new CondimentConfig();
    private final SandwichHandler sandwichHandler = new SandwichHandler();
    private final PizzaConfig pizzaConfig = new PizzaConfig();
    private final PizzaHandler pizzaHandler = new PizzaHandler();
    private final SaladConfig saladConfig = new SaladConfig();
    private final SaladHandler saladHandler = new SaladHandler();
    private final MillingStoneHandler millingStoneHandler = new MillingStoneHandler();
    private final TroughHandler troughHandler = new TroughHandler();
    private final TroughAging troughAging = new TroughAging();
    private final BakingTrayAging bakingTrayAging = new BakingTrayAging();
    private final OvenCavityManager ovenCavityManager = new OvenCavityManager();
    private final OvenBurnManager ovenBurnManager = new OvenBurnManager();
    private final OvenLifecycleHandler ovenLifecycleHandler = new OvenLifecycleHandler(ovenBurnManager);
    private final OvenHandler ovenHandler = new OvenHandler(ovenBurnManager);
    private final CraftingManager craftingManager = new CraftingManager();

    @Override
    public void onLoad() {
        MMOItemsSupport.registerIfPresent(getServer().getPluginManager(), getLogger());
    }

    @Override
    public void onEnable() {
        try {
            // Registration may arrive later; this checks API compatibility only.
            Characters.available();
        } catch (LinkageError error) {
            incompatibleCharacterApi = true;
            getLogger().severe("Cooking requires TLibs 2.3.0 or newer; disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        plugin = this;

        createFolders();
        createConfigs();
        loadConfigs();
        TLibs.getItemAPI().registerPathHandler("c", CookingPathHandler.INSTANCE);
        husbandryEnabled = HusbandryConfig.enabled();
        if (husbandryEnabled) {
            openHusbandryDatabase();
            HusbandryLifecycleListener.applyStatsRevision();
            net.tfminecraft.cooking.husbandry.HusbandryIdentity.start(this, husbandryRepository);
        } else {
            getLogger().info("Husbandry is disabled in husbandry.yml; animals are left to other plugins.");
        }
        registerListeners();

        cookingManager.start();
        Bukkit.getScheduler().runTask(this, () -> {
            plateManager.start();
            meatHookHandler.start();
            bakingTrayAging.start();
            troughAging.start();
            ovenCavityManager.start();
            ovenLifecycleHandler.resumeLoadedOvens();
            craftingManager.resumeLoadedStations();
            cookingManager.resumeLoadedStations();
            butterChurnHandler.resumeLoadedChurns();
            liquidContainerHandler.resumeLoadedContainers();
            meatHookHandler.resumeLoadedHooks();
            sandwichHandler.resumeLoaded();
            pizzaHandler.start();
            warnIfCharacterIdentityMissing(getLogger());
            SaturationGuard.start();
            NutritionDrainTask.start();
            Bukkit.getOnlinePlayers().forEach(NutritionLifecycleListener::initializeCurrent);
            if (husbandryEnabled) {
                HusbandryLifecycleListener.resumeLoadedWorlds();
                HusbandryTickTask.start();
                HusbandryLocator.scanUnloaded();
            }
            MMOItemsSupport.claimFirstIfPresent(getServer().getPluginManager(), getLogger());
        });

        getCommand("cooking").setExecutor(commands);
        getCommand("cooking").setTabCompleter(commands);
        if (husbandryEnabled) {
            HusbandryAnimalsCommand animals = new HusbandryAnimalsCommand();
            getServer().getPluginManager().registerEvents(animals, this);
            getCommand("animals").setExecutor(animals);
            getCommand("animals").setTabCompleter(animals);
        } else {
            releaseAnimalsCommand();
        }
        if (ItemScanService.get() != null) {
            ItemScanService.get().subscribe(tagManager);
            ItemScanService.get().subscribe(legacyFishScan);
        }
    }

    /** Runs once every plugin has enabled, when RPCharacters 2.16.0+ has registered its provider. */
    static void warnIfCharacterIdentityMissing(Logger logger) {
        Plugin characters = Bukkit.getPluginManager().getPlugin("RPCharacters");
        if (characters == null || !characters.isEnabled() || Characters.available()) return;
        logger.warning("RPCharacters " + characters.getPluginMeta().getVersion()
                + " provides no character identity; Cooking needs RPCharacters 2.16.0 or newer."
                + " Until then food and drink give no nutrition, hunger does not drain and the"
                + " hunger bar stays frozen.");
    }

    @Override
    public void onDisable() {
        if (incompatibleCharacterApi) return;
        ItemRef.setCaching(false);
        breweryBridge.close();
        SaturationGuard.stop();
        NutritionDrainTask.stop();
        HusbandryTickTask.stop();
        HusbandryLifecycleListener.flushLoadedForDisable();
        ovenBurnManager.stopAll();
        LiquidContainerAging.stopAll();
        ButterChurnAging.stopAll();
        if (ItemScanService.get() != null) {
            ItemScanService.get().unsubscribe(tagManager);
            ItemScanService.get().unsubscribe(legacyFishScan);
        }
        closeHusbandryDatabase();
        TLibs.getItemAPI().unregisterPathHandler("c");
    }

    // ----------------------------------------------------------------------
    //  Config Loading
    // ----------------------------------------------------------------------
    public void loadConfigs() {
        configLoader.loadConfig(new File(getDataFolder(), "config.yml"));
        modelLoader.load(new File(getDataFolder(), "models.yml"));
        foodLoader.load(new File(getDataFolder(), "types.yml"));
        bakingTrayLoader.load(new File(getDataFolder(), "baking-trays.yml"));
        sandwichConfig.load(new File(getDataFolder(), "sandwiches.yml"));
        pizzaConfig.load(new File(getDataFolder(), "pizzas.yml"));
        condimentConfig.load(new File(getDataFolder(), "condiments.yml"));
        saladConfig.load(new File(getDataFolder(), "salads.yml"));
        millingRecipeLoader.load(new File(getDataFolder(), "milling-recipes.yml"));
        stationLoader.load(new File(getDataFolder(), "crafting-stations.yml"));
        trackLoader.load(new File(getDataFolder(), "tags.yml"));
        namingLoader.load(new File(getDataFolder(), "naming.yml"));
        qualityConfigLoader.load(new File(getDataFolder(), "quality.yml"));
        compositionConfigLoader.load(new File(getDataFolder(), "composition.yml"));
        permissionEffectsLoader.load(new File(getDataFolder(), "permission_effects.yml"));
        conversionLoader.load(new File(getDataFolder(), "conversions.yml"));
        farmingLoader.load(new File(getDataFolder(), "farming.yml"));
        cropsLoader.load(new File(getDataFolder(), "crops.yml"));
        husbandryLoader.load(new File(getDataFolder(), "husbandry.yml"));
        carveSequenceLoader.load(new File(getDataFolder(), "carve-sequences.yml"));
        CustomFishingCatalog.load(new File(getDataFolder(), "custom-fishing.yml"));
    }

    public void reloadAll() {
        ItemRef.clearCache();
        loadConfigs();
        HusbandryLifecycleListener.applyStatsRevision();
        craftingManager.rebuildStations();
        NutritionDrainTask.stop();
        NutritionDrainTask.start();
        if (HusbandryConfig.enabled() != husbandryEnabled) {
            getLogger().warning("husbandry.yml enabled changed; restart the server to apply it.");
        }
        if (husbandryEnabled) {
            HusbandryTickTask.stop();
            HusbandryTickTask.start();
        }
    }

    /** True when Cooking runs livestock; read once at startup from husbandry.yml {@code enabled}. */
    public boolean isHusbandryEnabled() {
        return husbandryEnabled;
    }

    /** Frees /animals and /livestock so the plugin that runs livestock can register them. */
    private void releaseAnimalsCommand() {
        PluginCommand animals = getCommand("animals");
        CommandMap map = getServer().getCommandMap();
        animals.unregister(map);
        Map<String, Command> known = map.getKnownCommands();
        for (String label : List.copyOf(known.keySet())) {
            if (known.get(label) == animals) {
                known.remove(label);
            }
        }
    }

    // ----------------------------------------------------------------------
    //  Listeners
    // ----------------------------------------------------------------------
    public void registerListeners() {
        getServer().getPluginManager().registerEvents(breweryBridge, this);
        breweryBridge.registerIfPresent();
        getServer().getPluginManager().registerEvents(plateManager, this);
        getServer().getPluginManager().registerEvents(craftingManager, this);
        getServer().getPluginManager().registerEvents(cookingManager, this);
        getServer().getPluginManager().registerEvents(mixingBowlHandler, this);
        getServer().getPluginManager().registerEvents(butterChurnHandler, this);
        getServer().getPluginManager().registerEvents(meatHookHandler, this);
        getServer().getPluginManager().registerEvents(sausageMakerHandler, this);
        getServer().getPluginManager().registerEvents(bakingTrayHandler, this);
        getServer().getPluginManager().registerEvents(sandwichHandler, this);
        getServer().getPluginManager().registerEvents(pizzaHandler, this);
        getServer().getPluginManager().registerEvents(new SapTapListener(), this);
        getServer().getPluginManager().registerEvents(saladHandler, this);
        getServer().getPluginManager().registerEvents(millingStoneHandler, this);
        getServer().getPluginManager().registerEvents(troughHandler, this);
        getServer().getPluginManager().registerEvents(ovenLifecycleHandler, this);
        getServer().getPluginManager().registerEvents(ovenHandler, this);
        getServer().getPluginManager().registerEvents(new OvenCavityHandler(), this);
        getServer().getPluginManager().registerEvents(new HeatPickupGuard(), this);
        getServer().getPluginManager().registerEvents(new FarmHarvestListener(), this);
        getServer().getPluginManager().registerEvents(new FarmTrampleListener(), this);
        getServer().getPluginManager().registerEvents(new CropGrowthListener(), this);
        getServer().getPluginManager().registerEvents(new CropPlantingListener(), this);
        CropCustomCropsBridge customCropsBridge = new CropCustomCropsBridge();
        getServer().getPluginManager().registerEvents(customCropsBridge, this);
        CropCustomCropsBridge.tryRegister(this);
        getServer().getPluginManager().registerEvents(new CustomFishingBridge(), this);
        CustomFishingBridge.tryRegister(this);
        ItemsAdderReloadHook.registerIfPresent(this);
        if (husbandryEnabled) {
            getServer().getPluginManager().registerEvents(new HusbandryLifecycleListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryCareListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryTamingListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryDeathListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryHarvestListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryBreedListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryNeuterListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryInspectListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryMountListener(), this);
            getServer().getPluginManager().registerEvents(new HusbandryDamageListener(), this);
        }
        getServer().getPluginManager().registerEvents(new ConversionManager(), this);
        getServer().getPluginManager().registerEvents(new BundleFoodStacker(), this);
        getServer().getPluginManager().registerEvents(new RegenBlocker(), this);
        getServer().getPluginManager().registerEvents(new SaturationGuard(), this);
        getServer().getPluginManager().registerEvents(new NutritionLifecycleListener(), this);
        getServer().getPluginManager().registerEvents(new FoodLevelChangeGuard(), this);
        getServer().getPluginManager().registerEvents(new FoodConsumeListener(), this);
        getServer().getPluginManager().registerEvents(new DrinkConsumeListener(), this);
        getServer().getPluginManager().registerEvents(new BowlEatHandler(plateManager), this);
        getServer().getPluginManager().registerEvents(new MilkBucketConverter(), this);
        getServer().getPluginManager().registerEvents(liquidContainerHandler, this);
    }

    public OvenBurnManager getOvenBurnManager() {
        return ovenBurnManager;
    }

    public HusbandryRepository getHusbandryRepository() {
        return husbandryRepository;
    }

    private void openHusbandryDatabase() {
        File dbFile = new File(new File(getDataFolder(), "Data"), "husbandry.db");
        try {
            husbandryRepository = HusbandryRepository.open(dbFile);
        } catch (RuntimeException ex) {
            getLogger().severe("Failed to open husbandry SQLite database: " + ex.getMessage());
            throw ex;
        }
    }

    private void closeHusbandryDatabase() {
        if (husbandryRepository == null) {
            return;
        }
        try {
            husbandryRepository.close();
        } catch (RuntimeException ex) {
            getLogger().warning("Failed to close husbandry SQLite database: " + ex.getMessage());
        } finally {
            husbandryRepository = null;
        }
    }

    // ----------------------------------------------------------------------
    //  Folders
    // ----------------------------------------------------------------------
    public void createFolders() {
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        File subFolder = new File(getDataFolder(), "Data");
        if (!subFolder.exists())
            subFolder.mkdir();
    }

    // ----------------------------------------------------------------------
    //  Config Generation
    // ----------------------------------------------------------------------
    public void createConfigs() {
        String[] files = {
                "models.yml",
                "types.yml",
                "crafting-stations.yml",
                "baking-trays.yml",
                "sandwiches.yml",
                "pizzas.yml",
                "condiments.yml",
                "salads.yml",
                "milling-recipes.yml",
                "cookware.yml",
                "tags.yml",
                "naming.yml",
                "config.yml",
                "conversions.yml",
                "carve-sequences.yml",
                "quality.yml",
                "composition.yml",
                "permission_effects.yml",
                "farming.yml",
                "crops.yml",
                "husbandry.yml",
                "custom-fishing.yml"
        };

        for (String s : files) {
            File newConfigFile = new File(getDataFolder(), s);
            if (!newConfigFile.exists()) {
                newConfigFile.getParentFile().mkdirs();
                saveResource(s, false);
            }
        }
    }
}
