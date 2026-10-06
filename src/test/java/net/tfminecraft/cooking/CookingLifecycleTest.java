package net.tfminecraft.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.cooking.baking.*;
import net.tfminecraft.cooking.churn.*;
import net.tfminecraft.cooking.crops.CropCustomCropsBridge;
import net.tfminecraft.cooking.crops.CropsLoader;
import net.tfminecraft.cooking.farming.FarmingLoader;
import net.tfminecraft.cooking.fishing.CustomFishingBridge;
import net.tfminecraft.cooking.fishing.CustomFishingCatalog;
import net.tfminecraft.cooking.fishing.LegacyFishScan;
import net.tfminecraft.cooking.hook.MeatHookHandler;
import net.tfminecraft.cooking.husbandry.*;
import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.liquid.*;
import net.tfminecraft.cooking.loader.*;
import net.tfminecraft.cooking.manager.*;
import net.tfminecraft.cooking.milling.*;
import net.tfminecraft.cooking.mixing.MixingBowlHandler;
import net.tfminecraft.cooking.nutrition.NutritionDrainTask;
import net.tfminecraft.cooking.nutrition.SaturationGuard;
import net.tfminecraft.cooking.oven.*;
import net.tfminecraft.cooking.sausagemaker.SausageMakerHandler;
import net.tfminecraft.cooking.trough.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.itemscan.ItemScanService;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class CookingLifecycleTest {
    private final Map<Class<?>, MockedConstruction<?>> constructions = new LinkedHashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private ServerMock server;
    private Cooking previousPlugin;
    private ItemAPI items;
    private HusbandryRepository repository;
    private MockedStatic<HusbandryRepository> repositories;
    private MockedStatic<ItemScanService> scans;
    private MockedStatic<SaturationGuard> saturation;
    private MockedStatic<NutritionDrainTask> nutrition;
    private MockedStatic<HusbandryLifecycleListener> husbandry;
    private MockedStatic<HusbandryTickTask> husbandryTicks;
    private MockedStatic<HusbandryLocator> locator;
    private MockedStatic<LiquidContainerAging> liquidAging;
    private MockedStatic<ButterChurnAging> churnAging;
    @TempDir Path directory;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        for (Class<?> type : List.of(ModelLoader.class, FoodLoader.class, CraftingStationLoader.class,
                TrackLoader.class, ConfigLoader.class, ConversionLoader.class, CarveSequenceLoader.class,
                NamingLoader.class, QualityConfigLoader.class, CompositionConfigLoader.class,
                PermissionEffectsLoader.class, FarmingLoader.class, CropsLoader.class, HusbandryLoader.class,
                TagManager.class, LegacyFishScan.class, CookingManager.class, PlateManager.class,
                MixingBowlHandler.class, ButterChurnHandler.class, LiquidContainerHandler.class,
                MeatHookHandler.class, SausageMakerHandler.class, BakingTrayLoader.class,
                MillingRecipeLoader.class, BakingTrayHandler.class, MillingStoneHandler.class,
                TroughHandler.class, TroughAging.class, BakingTrayAging.class, OvenCavityManager.class,
                OvenBurnManager.class, OvenLifecycleHandler.class, OvenHandler.class, CraftingManager.class)) {
            constructions.put(type, mockConstruction(type));
        }
        items = mock(ItemAPI.class);
        staticMock(TLibs.class).when(TLibs::getItemAPI).thenReturn(items);
        repository = mock(HusbandryRepository.class);
        repositories = staticMock(HusbandryRepository.class);
        repositories.when(() -> HusbandryRepository.open(any(File.class))).thenReturn(repository);
        scans = staticMock(ItemScanService.class);
        saturation = staticMock(SaturationGuard.class);
        nutrition = staticMock(NutritionDrainTask.class);
        husbandry = staticMock(HusbandryLifecycleListener.class);
        husbandryTicks = staticMock(HusbandryTickTask.class);
        locator = staticMock(HusbandryLocator.class);
        liquidAging = staticMock(LiquidContainerAging.class);
        churnAging = staticMock(ButterChurnAging.class);
        staticMock(CustomFishingCatalog.class);
        staticMock(CropCustomCropsBridge.class);
        staticMock(CustomFishingBridge.class);
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
        for (int i = statics.size() - 1; i >= 0; i--) statics.get(i).close();
        List<MockedConstruction<?>> scopes = new ArrayList<>(constructions.values());
        for (int i = scopes.size() - 1; i >= 0; i--) scopes.get(i).close();
        Cooking.plugin = previousPlugin;
    }

    @Test void enablingCreatesDefaultResourcesLoadsEveryConfigAndRegistersCommandsAndListeners() throws Exception {
        Cooking plugin = loadPlugin();

        assertSame(plugin, Cooking.plugin);
        assertSame(repository, plugin.getHusbandryRepository());
        assertSame(built(OvenBurnManager.class), plugin.getOvenBurnManager());
        assertTrue(new File(plugin.getDataFolder(), "Data").isDirectory());
        for (String name : List.of("models.yml", "types.yml", "crafting-stations.yml", "baking-trays.yml",
                "milling-recipes.yml", "cookware.yml", "tags.yml", "naming.yml", "config.yml", "conversions.yml",
                "carve-sequences.yml", "quality.yml", "composition.yml", "permission_effects.yml", "farming.yml",
                "crops.yml", "husbandry.yml", "custom-fishing.yml")) {
            Path resource = plugin.getDataFolder().toPath().resolve(name);
            assertTrue(Files.isRegularFile(resource), name);
            assertFalse(Files.readString(resource).isBlank(), name);
            new YamlConfiguration().load(resource.toFile());
        }
        assertConfigLoaded(plugin, ConfigLoader.class, "config.yml");
        assertConfigLoaded(plugin, ModelLoader.class, "models.yml");
        assertConfigLoaded(plugin, FoodLoader.class, "types.yml");
        assertConfigLoaded(plugin, BakingTrayLoader.class, "baking-trays.yml");
        assertConfigLoaded(plugin, MillingRecipeLoader.class, "milling-recipes.yml");
        assertConfigLoaded(plugin, CraftingStationLoader.class, "crafting-stations.yml");
        assertConfigLoaded(plugin, TrackLoader.class, "tags.yml");
        assertConfigLoaded(plugin, NamingLoader.class, "naming.yml");
        assertConfigLoaded(plugin, QualityConfigLoader.class, "quality.yml");
        assertConfigLoaded(plugin, CompositionConfigLoader.class, "composition.yml");
        assertConfigLoaded(plugin, PermissionEffectsLoader.class, "permission_effects.yml");
        assertConfigLoaded(plugin, ConversionLoader.class, "conversions.yml");
        assertConfigLoaded(plugin, FarmingLoader.class, "farming.yml");
        assertConfigLoaded(plugin, CropsLoader.class, "crops.yml");
        assertConfigLoaded(plugin, HusbandryLoader.class, "husbandry.yml");
        assertConfigLoaded(plugin, CarveSequenceLoader.class, "carve-sequences.yml");
        repositories.verify(() -> HusbandryRepository.open(new File(plugin.getDataFolder(), "Data/husbandry.db")));
        verify(items).registerPathHandler("c", CookingPathHandler.INSTANCE);
        assertInstanceOf(CommandManager.class, plugin.getCommand("cooking").getExecutor());
        assertSame(plugin.getCommand("cooking").getExecutor(), plugin.getCommand("cooking").getTabCompleter());
        assertInstanceOf(HusbandryAnimalsCommand.class, plugin.getCommand("animals").getExecutor());
        assertSame(plugin.getCommand("animals").getExecutor(), plugin.getCommand("animals").getTabCompleter());
        var caller = server.addPlayer();
        assertFalse(plugin.getCommand("cooking").testPermissionSilent(caller));
        caller.setOp(true);
        assertTrue(plugin.getCommand("cooking").testPermissionSilent(caller));
        assertTrue(plugin.getCommand("animals").getAliases().contains("livestock"));
        assertFalse(HandlerList.getRegisteredListeners(plugin).isEmpty());
        verify(built(CookingManager.class)).start();
        verify(built(PlateManager.class), never()).start();
    }

    @Test void nextTickResumesLoadedStateAndShutdownReleasesScansTasksDatabaseAndPathHandler() {
        ItemScanService scan = mock(ItemScanService.class);
        scans.when(ItemScanService::get).thenReturn(scan);
        Cooking plugin = loadPlugin();
        verify(scan).subscribe(built(TagManager.class));
        verify(scan).subscribe(built(LegacyFishScan.class));

        server.getScheduler().performOneTick();

        verify(built(PlateManager.class)).start();
        verify(built(MeatHookHandler.class)).start();
        verify(built(BakingTrayAging.class)).start();
        verify(built(TroughAging.class)).start();
        verify(built(OvenCavityManager.class)).start();
        verify(built(OvenLifecycleHandler.class)).resumeLoadedOvens();
        verify(built(CraftingManager.class)).resumeLoadedStations();
        verify(built(CookingManager.class)).resumeLoadedStations();
        verify(built(ButterChurnHandler.class)).resumeLoadedChurns();
        verify(built(LiquidContainerHandler.class)).resumeLoadedContainers();
        verify(built(MeatHookHandler.class)).resumeLoadedHooks();
        saturation.verify(SaturationGuard::start);
        nutrition.verify(NutritionDrainTask::start);
        husbandry.verify(HusbandryLifecycleListener::resumeLoadedWorlds);
        husbandryTicks.verify(HusbandryTickTask::start);
        locator.verify(HusbandryLocator::scanUnloaded);

        server.getPluginManager().disablePlugin(plugin);

        saturation.verify(SaturationGuard::stop);
        nutrition.verify(NutritionDrainTask::stop);
        husbandryTicks.verify(HusbandryTickTask::stop);
        husbandry.verify(HusbandryLifecycleListener::flushLoadedForDisable);
        verify(built(OvenBurnManager.class)).stopAll();
        liquidAging.verify(LiquidContainerAging::stopAll);
        churnAging.verify(ButterChurnAging::stopAll);
        verify(scan).unsubscribe(built(TagManager.class));
        verify(scan).unsubscribe(built(LegacyFishScan.class));
        verify(repository).close();
        assertNull(plugin.getHusbandryRepository());
        verify(items).unregisterPathHandler("c");
        plugin.onDisable();
        verify(repository, times(1)).close();
    }

    @Test void reloadRefreshesConfigStationsAndPeriodicNutritionAndHusbandryTasks() {
        Cooking plugin = loadPlugin();
        server.getScheduler().performOneTick();

        plugin.reloadAll();

        verify(built(ConfigLoader.class), times(2)).loadConfig(new File(plugin.getDataFolder(), "config.yml"));
        verify(built(CraftingManager.class)).rebuildStations();
        husbandry.verify(HusbandryLifecycleListener::applyStatsRevision, times(2));
        nutrition.verify(NutritionDrainTask::stop);
        nutrition.verify(NutritionDrainTask::start, times(2));
        husbandryTicks.verify(HusbandryTickTask::stop);
        husbandryTicks.verify(HusbandryTickTask::start, times(2));
    }

    @Test void configGenerationPreservesExistingUserFilesAndFolderCreationIsIdempotent() throws Exception {
        Cooking plugin = loadPlugin();
        Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        Files.writeString(config, "custom-setting: keep-me\n");
        plugin.createConfigs();
        assertEquals("custom-setting: keep-me\n", Files.readString(config));

        Cooking folders = mock(Cooking.class, CALLS_REAL_METHODS);
        File root = directory.resolve("fresh-plugin-data").toFile();
        doReturn(root).when(folders).getDataFolder();
        folders.createFolders();
        assertTrue(root.isDirectory());
        assertTrue(new File(root, "Data").isDirectory());
        folders.createFolders();
        assertTrue(new File(root, "Data").isDirectory());
    }

    @Test void databaseOpenFailureIsPropagatedInsteadOfEnablingAPartialPlugin() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        repositories.when(() -> HusbandryRepository.open(any(File.class))).thenThrow(failure);
        Throwable error = assertThrows(RuntimeException.class, this::loadPlugin);
        while (error.getCause() != null && error != failure) error = error.getCause();
        assertSame(failure, error);
        assertNotNull(Cooking.plugin);
        assertNull(Cooking.plugin.getHusbandryRepository());
        verify(built(CookingManager.class), never()).start();
    }

    @Test void databaseCloseFailureStillClearsTheRepositoryAndUnregistersThePath() {
        Cooking plugin = loadPlugin();
        doThrow(new IllegalStateException("close failed")).when(repository).close();

        assertDoesNotThrow(() -> server.getPluginManager().disablePlugin(plugin));

        assertNull(plugin.getHusbandryRepository());
        verify(items).unregisterPathHandler("c");
    }

    private Cooking loadPlugin() {
        MockBukkit.createMockPlugin("TLibs");
        MockBukkit.createMockPlugin("InteractibleFurniture");
        return MockBukkit.load(Cooking.class);
    }

    private <T> MockedStatic<T> staticMock(Class<T> type) {
        MockedStatic<T> value = mockStatic(type); statics.add(value); return value;
    }

    private <T> T built(Class<T> type) {
        List<?> values = constructions.get(type).constructed();
        assertEquals(1, values.size(), type.getSimpleName());
        return type.cast(values.getFirst());
    }

    private void assertConfigLoaded(Cooking plugin, Class<?> type, String name) {
        assertTrue(mockingDetails(built(type)).getInvocations().stream()
                .anyMatch(call -> call.getArguments().length == 1
                        && new File(plugin.getDataFolder(), name).equals(call.getArgument(0))), name);
    }
}
