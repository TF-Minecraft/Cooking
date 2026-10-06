package net.tfminecraft.cooking.loader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

class ResourceLoadersCoverageTest {
    @TempDir Path directory;
    private List<FoodItem> foods;
    private List<FoodModel> models;
    private List<TagTrack> tracks;
    private Map<String,String> conversions;

    @BeforeEach void setup() {
        MockBukkit.mock();
        foods = new ArrayList<>(FoodLoader.oList);
        models = new ArrayList<>(ModelLoader.models);
        tracks = new ArrayList<>(TrackLoader.oList);
        conversions = new HashMap<>(ConversionLoader.conversions);
        FoodLoader.oList.clear(); ModelLoader.models.clear(); TrackLoader.oList.clear();
        ConversionLoader.conversions.clear();
    }

    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods);
        ModelLoader.models.clear(); ModelLoader.models.addAll(models);
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        ConversionLoader.conversions.clear(); ConversionLoader.conversions.putAll(conversions);
        MockBukkit.unmock();
    }

    @Test void filesLoadInDependencyOrderAndReloadReplacesRemovedEntries() throws Exception {
        YamlConfiguration model = new YamlConfiguration();
        model.set("bread.default.gui.item", "v.bread"); model.set("bread.default.weight", 7);
        new ModelLoader().load(save(model));
        assertEquals(1, ModelLoader.get().size());
        assertEquals(7, ModelLoader.getById("BREAD").getStates().get("default").getWeight());
        assertNull(ModelLoader.getById("missing"));

        YamlConfiguration track = new YamlConfiguration();
        track.set("freshness.ageable", true);
        track.set("freshness.fresh.name", "Fresh"); track.set("freshness.fresh.value", 0);
        track.set("freshness.stale.name", "Stale"); track.set("freshness.stale.value", 10);
        track.set("warmth.hot.name", "Hot");
        new TrackLoader().load(save(track));
        assertEquals(2, TrackLoader.get().size());
        TagTrack freshness = TrackLoader.getByString("FRESHNESS");
        assertTrue(freshness.isAgeable()); assertEquals(0, freshness.getIndex());
        assertEquals(1, TrackLoader.getByString("warmth").getIndex());
        assertEquals("fresh", freshness.getCurrentStep().getId());
        freshness.setValue(10); assertEquals("stale", freshness.getCurrentStep().getId());
        assertNull(TrackLoader.getByString("missing"));

        YamlConfiguration food = new YamlConfiguration();
        food.set("loaf.name", "Bread"); food.set("loaf.model", "bread");
        food.set("loaf.food", 5); food.set("loaf.nutrition", 3);
        new FoodLoader().load(save(food));
        assertEquals(1, FoodLoader.get().size());
        FoodItem loaf = FoodLoader.getByString("LOAF");
        assertEquals("Bread", loaf.getName()); assertEquals(5, loaf.getBaseFood());
        assertEquals(3, loaf.getBaseNutrition()); assertNotNull(loaf.getModel());
        assertNull(FoodLoader.getByString("missing"));

        File empty = save(new YamlConfiguration());
        new FoodLoader().load(empty); new TrackLoader().load(empty); new ModelLoader().load(empty);
        assertTrue(FoodLoader.get().isEmpty()); assertTrue(TrackLoader.get().isEmpty());
        assertTrue(ModelLoader.get().isEmpty());
    }

    @Test void scalarEntriesDoNotPreventValidFoodsModelsAndTracksFromLoading() throws Exception {
        YamlConfiguration food = new YamlConfiguration();
        food.set("invalid", "scalar"); food.set("loaf.name", "Bread");
        YamlConfiguration track = new YamlConfiguration();
        track.set("invalid", "scalar"); track.set("freshness.fresh.value", 0);
        track.set("freshness.invalid", "scalar"); track.set("freshness.stale.value", 10);
        YamlConfiguration model = new YamlConfiguration();
        model.set("invalid", "scalar"); model.set("bread.default.gui.item", "v.bread");
        File foodFile = save(food), trackFile = save(track), modelFile = save(model);
        assertAll(() -> assertDoesNotThrow(() -> new FoodLoader().load(foodFile)),
                () -> assertDoesNotThrow(() -> new TrackLoader().load(trackFile)),
                () -> assertDoesNotThrow(() -> new ModelLoader().load(modelFile)));
        assertEquals(List.of("loaf"), FoodLoader.get().stream().map(FoodItem::getId).toList());
        assertEquals(List.of("bread"), ModelLoader.get().stream().map(FoodModel::getId).toList());
        assertEquals(1, TrackLoader.get().size());
        assertEquals(2, TrackLoader.getByString("freshness").getSteps().size());
    }

    @Test void conversionStringsKeepStructuredFoodExpressionsAndIgnoreIncompleteRecords() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("conversions", List.of(" ", "missing", " v.wheat  food(type=loaf, amount=2) "));
        new ConversionLoader().load(save(config));
        assertEquals(Map.of("v.wheat", "food(type=loaf, amount=2)"), ConversionLoader.get());
        assertEquals("food(type=loaf, amount=2)", ConversionLoader.getByString("v.wheat"));
        assertNull(ConversionLoader.getByString("v.stone"));
        ItemStack wheat = new ItemStack(Material.WHEAT);
        try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
            ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
            tlibs.when(TLibs::getItemAPI).thenReturn(api);
            when(api.getChecker().checkItemWithPath(wheat, "v.wheat")).thenReturn(true);
            assertEquals("food(type=loaf, amount=2)", ConversionLoader.getByItem(wheat));
            assertNull(ConversionLoader.getByItem(new ItemStack(Material.STONE)));
        }
        new ConversionLoader().load(save(new YamlConfiguration()));
        assertTrue(ConversionLoader.get().isEmpty());
    }

    @Test void missingAndMalformedFilesReportErrorsWithoutCrashingInitialLoading() throws Exception {
        File missing = directory.resolve("missing.yml").toFile();
        Path invalid = directory.resolve("invalid.yml"); Files.writeString(invalid, "bad: [unterminated");
        List<Consumer<File>> loaders = List.of(new FoodLoader()::load, new TrackLoader()::load,
                new ModelLoader()::load, new ConversionLoader()::load);
        PrintStream previous = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(errors)) {
            System.setErr(capture);
            for (Consumer<File> loader : loaders) {
                assertDoesNotThrow(() -> loader.accept(missing));
                assertDoesNotThrow(() -> loader.accept(invalid.toFile()));
            }
        } finally { System.setErr(previous); }
        assertTrue(errors.toString().contains("FileNotFoundException"));
        assertTrue(errors.toString().contains("InvalidConfigurationException"));
        assertTrue(FoodLoader.get().isEmpty()); assertTrue(TrackLoader.get().isEmpty());
        assertTrue(ModelLoader.get().isEmpty()); assertTrue(ConversionLoader.get().isEmpty());
    }

    private File save(YamlConfiguration config) throws Exception {
        File file = Files.createTempFile(directory, "resource-", ".yml").toFile();
        config.save(file); return file;
    }
}
