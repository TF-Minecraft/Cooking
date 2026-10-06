package net.tfminecraft.cooking.loader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.CategoryDictionary;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.heat.HeatLoader;
import net.tfminecraft.cooking.heat.HeatLookup;
import net.tfminecraft.cooking.heat.HeatSourceType;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.nutrition.NutritionConfig;
import net.tfminecraft.cooking.nutrition.NutritionLog;
import net.tfminecraft.cooking.quality.CompositionConfig;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionRole;
import net.tfminecraft.cooking.quality.PermissionEffectsConfig;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

class ConfigurationLoadersCoverageTest {
    @TempDir Path directory;
    Cooking previousPlugin;
    @BeforeEach void setup() {
        MockBukkit.mock(); previousPlugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    }
    @AfterEach void cleanup() throws Exception {
        new ConfigLoader().loadConfig(save(new YamlConfiguration()));
        QualityConfig.apply(1,5,null,null);
        CompositionConfig.apply(true,null,null,null,null,true,.12,.15,true);
        PermissionEffectsConfig.apply(null,null);
        NamingConfig.apply(List.of("sweet"),3,2,2,null,null,null);
        NutritionLog.configure(false,false,null);
        MockBukkit.unmock(); Cooking.plugin=previousPlugin;
    }

    @Test void mainConfigurationLoadsStationItemsAndMappingTablesAndReplacesStaleEntries() throws Exception {
        YamlConfiguration config=new YamlConfiguration();
        config.set("frying_pan","pan"); config.set("saucepan","sauce"); config.set("pot","pot");
        config.set("butter-churn","churn"); config.set("mixing-bowl","mixing"); config.set("liquid-container","jug");
        config.set("pot-soup-scoops",0); config.set("pot-soup-height-divisor",0); config.set("trough-items-per-click",0);
        config.set("water","v.water_bucket"); config.set("flour-item","v.wheat"); config.set("butter-churn-count",4);
        config.set("mixing-stir-count",5); config.set("oven-fuel",List.of("v.coal","v.charcoal"));
        config.set("mixing-ingredients.flour.input","v.wheat"); config.set("mixing-ingredients.flour.input-food","grain");
        config.set("mixing-ingredients.flour.model","v.paper"); config.set("mixing-ingredients.flour.output","v.bread");
        config.set("liquids",List.of("v.water_bucket v.paper","bad","too many fields"));
        config.set("colours",List.of("v.carrot aa5500","bad"));
        config.set("dictionary.grain","Grain"); config.set("dictionary.skipped","none");
        config.set("sauce-dict",List.of("aa5500 v.paper|v.bowl","bad"));
        new ConfigLoader().loadConfig(save(config));
        assertEquals("pan",FurnitureCache.fryingPan); assertEquals("sauce",FurnitureCache.saucePan);
        assertEquals("churn",FurnitureCache.butterChurn); assertEquals("mixing",FurnitureCache.mixingBowl); assertEquals("jug",FurnitureCache.liquidContainer);
        assertEquals(1,ItemCache.potSoupScoops); assertEquals(1,ItemCache.potSoupHeightDivisor); assertEquals(1,ItemCache.troughItemsPerClick);
        assertEquals(4,ItemCache.butterChurnCount); assertEquals(5,ItemCache.mixingStirCount);
        assertEquals(List.of("v.coal","v.charcoal"),ItemCache.ovenFuel);
        assertEquals(Map.of("v.water_bucket","v.paper"),ItemCache.liquidModels);
        assertEquals(Map.of("v.carrot","aa5500"),ItemCache.colourMap);
        assertEquals(Map.of("grain","Grain"),CategoryDictionary.dictionary);
        assertEquals(Map.of("aa5500","v.paper|v.bowl"),CategoryDictionary.sauceDict);
        var ingredient=ItemCache.mixingIngredients.get("flour");
        assertEquals("v.wheat",ingredient.getInput()); assertEquals("grain",ingredient.getInputFood());
        assertEquals("v.paper",ingredient.getModel()); assertEquals("v.bread",ingredient.getOutput());
        new ConfigLoader().loadConfig(save(new YamlConfiguration()));
        assertTrue(ItemCache.liquidModels.isEmpty()); assertTrue(ItemCache.colourMap.isEmpty());
        assertTrue(ItemCache.mixingIngredients.isEmpty()); assertTrue(CategoryDictionary.dictionary.isEmpty());
        assertTrue(CategoryDictionary.sauceDict.isEmpty()); assertTrue(ItemCache.ovenFuel.isEmpty());
        assertEquals(3,ItemCache.potSoupScoops); assertEquals(4,ItemCache.potSoupHeightDivisor);
    }

    @Test void heatConfigurationRecognizesSupportedTypesAndRejectsInvalidMappings() {
        YamlConfiguration config=new YamlConfiguration();
        config.set("heat.sources.oven.type","oven"); config.set("heat.sources.fire.type","campfire");
        config.set("heat.sources.bad.type","unknown");
        config.set("heat.consumers.pan.source","fire"); config.set("heat.consumers.pan.lookup","block-below");
        config.set("heat.consumers.bad.lookup","block-below"); config.set("heat.consumers.unknown.source","fire");
        config.set("heat.consumers.unknown.lookup","unknown");
        HeatLoader.load(config);
        assertTrue(HeatSources.isSource(furniture("OVEN"))); assertTrue(HeatSources.isSource(furniture("fire")));
        assertFalse(HeatSources.isSource(furniture("bad"))); assertTrue(HeatSources.isConsumer(furniture("PAN")));
        assertFalse(HeatSources.isConsumer(furniture("unknown"))); assertFalse(HeatSources.isConsumer(null));
        assertFalse(HeatSources.isSource(null)); assertFalse(HeatSources.hasHeat(null));
        assertFalse(HeatSources.hasHeat(furniture("unknown"))); assertTrue(HeatSources.findSource(null).isEmpty());
        assertTrue(HeatSources.findSource(furniture("unknown")).isEmpty()); assertFalse(HeatSources.stationHasHeat(null));
        assertFalse(HeatSources.stationHasHeat(furniture("unknown")));
        assertNull(HeatSourceType.fromConfig(null)); assertNull(HeatLookup.fromConfig(null)); assertNull(HeatLookup.fromConfig("other"));
        config=new YamlConfiguration(); config.createSection("heat"); HeatLoader.load(config);
        assertFalse(HeatSources.isSource(furniture("oven")));
    }

    @Test void qualityAndCompositionFilesLoadRolesPermissionsAndNumericSettings() throws Exception {
        YamlConfiguration quality=new YamlConfiguration(); quality.set("pickup.min",2); quality.set("pickup.max",4);
        quality.set("composition.exclude-categories",List.of("SALT"," "));
        quality.set("nutrition-from-quality.2",.65); quality.set("nutrition-from-quality.invalid",10);
        new QualityConfigLoader().load(save(quality));
        assertEquals(2,QualityConfig.getPickupMin()); assertEquals(4,QualityConfig.getPickupMax());
        assertEquals(Set.of("salt"),QualityConfig.getExcludeCategories()); assertTrue(QualityConfig.isExcludedCategory("SALT"));
        assertFalse(QualityConfig.isExcludedCategory(null)); assertEquals(.65,QualityConfig.nutritionMultiplier(2));
        assertEquals(1.8,QualityConfig.nutritionMultiplier(99));
        YamlConfiguration config=new YamlConfiguration(); config.set("legacy-mode",false);
        config.set("roles.main",List.of("MEAT"," ")); config.set("roles.extra",List.of("SALT")); config.set("roles.neutral",List.of("WATER"));
        config.set("modifiers.enabled",false); config.set("modifiers.gap-up-chance-per-star",.25);
        config.set("modifiers.gap-down-chance-per-star",.35); config.set("modifiers.craft-quality-pct-bonus",false);
        config.set("context-overrides.cutting_board.main",List.of("WATER"," "));
        config.set("context-overrides.cutting_board.extra",List.of("MEAT"));
        config.set("context-overrides.cutting_board.neutral",List.of("SALT"));
        config.set("context-overrides.baking","scalar"); config.set("context-overrides.invalid.main",List.of("meat"));
        new CompositionConfigLoader().load(save(config));
        assertFalse(CompositionConfig.isLegacyMode()); assertFalse(CompositionConfig.isModifiersEnabled()); assertFalse(CompositionConfig.isCraftQualityPctBonus());
        assertEquals(.25,CompositionConfig.getGapUpChancePerStar()); assertEquals(.35,CompositionConfig.getGapDownChancePerStar());
        assertEquals(CompositionRole.MAIN,CompositionConfig.getRole(null)); assertEquals(CompositionRole.MAIN,CompositionConfig.getRole("MEAT"));
        assertEquals(CompositionRole.EXTRA,CompositionConfig.getRole("SALT")); assertEquals(CompositionRole.NEUTRAL,CompositionConfig.getRole("water"));
        assertEquals(CompositionRole.NEUTRAL,CompositionConfig.getRole("unknown"));
        assertEquals(CompositionRole.MAIN,CompositionConfig.getRole(null,CompositionContext.CUTTING_BOARD));
        assertEquals(CompositionRole.EXTRA,CompositionConfig.getRole("meat",CompositionContext.CUTTING_BOARD));
        assertEquals(CompositionRole.NEUTRAL,CompositionConfig.getRole("salt",CompositionContext.CUTTING_BOARD));
        assertEquals(CompositionRole.MAIN,CompositionConfig.getRole("water",CompositionContext.CUTTING_BOARD));
        assertEquals(CompositionRole.NEUTRAL,CompositionConfig.getRole("unknown",CompositionContext.CUTTING_BOARD));
        assertEquals(CompositionRole.MAIN,CompositionConfig.getRole("meat",null));
        var emptyRoles=new CompositionConfig.RoleSets(null,null,null);
        assertTrue(emptyRoles.getMains().isEmpty()); assertTrue(emptyRoles.getExtras().isEmpty()); assertTrue(emptyRoles.getNeutrals().isEmpty());
        for(CompositionContext context:CompositionContext.values()) assertTrue(context.filterExcludedCategories());
        new CompositionConfigLoader().load(save(new YamlConfiguration())); assertTrue(CompositionConfig.isLegacyMode());
        new QualityConfigLoader().load(save(new YamlConfiguration())); assertEquals(1,QualityConfig.getPickupMin());
    }

    @Test void namingFilesNormalizeAndReplaceNestedLabelsAndAdjectives() throws Exception {
        YamlConfiguration config=new YamlConfiguration(); config.set("prefix-tracks",List.of("sweet","freshness"));
        config.set("fillers.max",4); config.set("addons.max",3); config.set("dough-fillers.max",2);
        config.set("categories.addon",List.of("SPICE"," ")); config.set("origin-adjectives.FISH","Fishy"); config.set("origin-adjectives.empty"," ");
        config.set("tag-label-defaults.bread.freshness.fresh","Garden fresh"); config.set("tag-label-defaults.bread.freshness.blank"," ");
        config.set("tag-label-defaults.bad","scalar"); config.set("tag-label-defaults.bread.bad","scalar");
        config.createSection("tag-label-defaults.empty"); config.createSection("tag-label-defaults.bread.empty");
        new NamingLoader().load(save(config));
        assertEquals(List.of("sweet","freshness"),NamingConfig.getPrefixTracks()); assertEquals(4,NamingConfig.getFillerMax());
        assertEquals(3,NamingConfig.getAddonMax()); assertEquals(2,NamingConfig.getDoughFillerMax());
        assertEquals(Set.of("spice"),NamingConfig.getCategories("addon")); assertTrue(NamingConfig.isCategory("addon","SPICE"));
        assertFalse(NamingConfig.isCategory("addon",null)); assertTrue(NamingConfig.getCategories("unknown").isEmpty());
        assertEquals(Set.of("addon"),NamingConfig.allCategoryBuckets());
        assertEquals("Garden fresh",NamingConfig.getTagLabelDefault("BREAD","FRESHNESS","FRESH"));
        assertNull(NamingConfig.getTagLabelDefault(null,"freshness","fresh")); assertNull(NamingConfig.getTagLabelDefault("bread",null,"fresh"));
        assertNull(NamingConfig.getTagLabelDefault("bread","freshness",null));
        assertNull(NamingConfig.getTagLabelDefault("unknown","freshness","fresh")); assertNull(NamingConfig.getTagLabelDefault("bread","unknown","fresh"));
        assertEquals("Fishy",NamingConfig.getOriginAdjective("fish")); assertNull(NamingConfig.getOriginAdjective(null));
        new NamingLoader().load(save(new YamlConfiguration())); assertNull(NamingConfig.getOriginAdjective("fish"));
        NamingConfig.apply(null,0,0,0,null,null,null); assertEquals(1,NamingConfig.getFillerMax()); assertTrue(NamingConfig.getPrefixTracks().isEmpty());
    }

    @Test void permissionEffectsIgnoreInvalidRecordsAndExposeImmutableSnapshots() throws Exception {
        YamlConfiguration config=new YamlConfiguration();
        for(String group:List.of("pickup","composition")) {
            config.set(group+".scalar","bad"); config.set(group+".empty.permission"," ");
            config.createSection(group+".missing"); config.set(group+".chef.permission","cooking.chef");
        }
        config.set("pickup.chef.min-quality",3); config.set("pickup.chef.roll-bias",1);
        config.set("composition.chef.chance",.5); config.set("composition.chef.boost",2);
        new PermissionEffectsLoader().load(save(config));
        assertEquals(List.of(new PermissionEffectsConfig.PickupEffect("chef","cooking.chef",3,1)),PermissionEffectsConfig.getPickupEffects());
        assertEquals(List.of(new PermissionEffectsConfig.CompositionEffect("chef","cooking.chef",.5,2)),PermissionEffectsConfig.getCompositionEffects());
        assertThrows(UnsupportedOperationException.class,()->PermissionEffectsConfig.getPickupEffects().clear());
        new PermissionEffectsLoader().load(save(new YamlConfiguration())); assertTrue(PermissionEffectsConfig.getPickupEffects().isEmpty());
        assertTrue(PermissionEffectsConfig.getCompositionEffects().isEmpty());
    }

    @Test void nutritionSettingsValidateVarietyAndDietTiersAndKeepLegacyDrainUnits() {
        YamlConfiguration config=new YamlConfiguration(); config.set("nutrition.max-food",100); config.set("nutrition.respawn-food",150);
        config.set("nutrition.max-diet",20); config.set("nutrition.attribute-name","diet"); config.set("nutrition.drain-amount",2);
        config.set("nutrition.drain-interval",0); config.set("nutrition.lerp-step-rate",.5);
        config.set("nutrition.variety.enabled",false); config.set("nutrition.variety.history-meals",0);
        config.set("nutrition.variety.target-ingredients",0); config.set("nutrition.variety.max-penalty-percent",100);
        config.set("nutrition.variety.main-weight",-1); config.set("nutrition.variety.extra-weight",-1);
        config.set("nutrition.diet-tiers",List.of(Map.of("id","good","min-percent","60","label","Good"),Map.of("id","bad","min-percent",10),
                Map.of("id"," ","min-percent",0),Map.of("min-percent",0),Map.of("id","invalid","min-percent","bad")));
        NutritionConfig.load(config);
        assertEquals(100,NutritionConfig.maxFood()); assertEquals(100,NutritionConfig.respawnFood()); assertEquals(20,NutritionConfig.maxDiet());
        assertEquals("diet",NutritionConfig.attributeName()); assertEquals(2,NutritionConfig.drainAmount()); assertEquals(1,NutritionConfig.drainIntervalSeconds());
        assertEquals(.5,NutritionConfig.lerpStepRate()); assertFalse(NutritionConfig.varietyEnabled());
        assertEquals(1,NutritionConfig.varietyHistoryMeals()); assertEquals(2,NutritionConfig.varietyTargetIngredients());
        assertEquals(99,NutritionConfig.varietyMaxPenaltyPercent()); assertEquals(0,NutritionConfig.varietyMainWeight()); assertEquals(0,NutritionConfig.varietyExtraWeight());
        assertEquals(2,NutritionConfig.dietTiers().size()); assertEquals("bad",NutritionConfig.resolveTierPercent(-1).getId());
        assertEquals("good",NutritionConfig.resolveTier(20).getId()); assertEquals("Good",NutritionConfig.resolveTierPercent(1000).getLabel());
        config.set("nutrition.drain-interval",null); config.set("nutrition.drain-interval-ticks",60); config.set("nutrition.variety",null);
        config.set("nutrition.diet-tiers",List.of(Map.of("id","bad","min-percent","invalid"))); NutritionConfig.load(config);
        assertEquals(3,NutritionConfig.drainIntervalSeconds()); assertEquals(6,NutritionConfig.dietTiers().size()); assertTrue(NutritionConfig.varietyEnabled());
        config.set("nutrition.drain-interval-ticks",null); config.set("nutrition.diet-tiers",List.of()); config.set("nutrition.max-diet",0); NutritionConfig.load(config);
        assertEquals(120,NutritionConfig.drainIntervalSeconds()); assertEquals("terrible",NutritionConfig.resolveTier(10).getId());
        NutritionConfig.load(new YamlConfiguration()); assertEquals(200,NutritionConfig.maxFood()); assertEquals(80,NutritionConfig.respawnFood());
    }

    @Test void malformedAndMissingFilesDoNotPreventOtherConfigurationFromLoading() throws Exception {
        File missing=directory.resolve("missing.yml").toFile(); Path invalid=directory.resolve("invalid.yml"); Files.writeString(invalid,"bad: [unterminated");
        List<Consumer<File>> loaders=List.of(new ConfigLoader()::loadConfig,new NamingLoader()::load,new QualityConfigLoader()::load,
                new CompositionConfigLoader()::load,new PermissionEffectsLoader()::load);
        PrintStream previous=System.err; ByteArrayOutputStream errors=new ByteArrayOutputStream();
        try(PrintStream capture=new PrintStream(errors)) { System.setErr(capture);
            for(Consumer<File> loader:loaders) { assertDoesNotThrow(()->loader.accept(missing)); assertDoesNotThrow(()->loader.accept(invalid.toFile())); }
        } finally { System.setErr(previous); }
        assertTrue(errors.toString().contains("FileNotFoundException")); assertTrue(errors.toString().contains("InvalidConfigurationException"));
        assertTrue(NutritionConfig.maxFood()>0); assertEquals(1,QualityConfig.getPickupMin());
    }

    @Test void categoryAndContextNamesRemainStableInTurkishLocale() throws Exception {
        YamlConfiguration config=new YamlConfiguration(); config.set("roles.main",List.of("FISH"));
        config.set("context-overrides.mixing_bowl.extra",List.of("FISH"));
        File composition=save(config); config=new YamlConfiguration(); config.set("categories.addon",List.of("SPICE"));
        config.set("origin-adjectives.FISH","Fishy"); File naming=save(config);
        Locale previous=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            new CompositionConfigLoader().load(composition); new NamingLoader().load(naming);
            assertEquals(CompositionRole.MAIN,CompositionConfig.getRole("fish"));
            assertEquals(CompositionRole.EXTRA,CompositionConfig.getRole("fish",CompositionContext.MIXING_BOWL));
            assertTrue(NamingConfig.isCategory("addon","spice")); assertEquals("Fishy",NamingConfig.getOriginAdjective("fish"));
        } finally { Locale.setDefault(previous); }
    }

    @Test void qualityCategoriesAndHeatTypesIgnoreTheServerLocale() throws Exception {
        YamlConfiguration config=new YamlConfiguration(); config.set("composition.exclude-categories",List.of("FISH"));
        File quality=save(config); Locale previous=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            new QualityConfigLoader().load(quality);
            assertAll(()->assertTrue(QualityConfig.isExcludedCategory("fish")),
                    ()->assertEquals(HeatSourceType.CAMPFIRE,HeatSourceType.fromConfig("CAMPFIRE")));
        } finally { Locale.setDefault(previous); }
    }

    private File save(YamlConfiguration config) throws Exception {
        config.set("logging",false); File file=Files.createTempFile(directory,"config-",".yml").toFile(); config.save(file); return file;
    }
    private static Furniture furniture(String id) { Furniture furniture=mock(Furniture.class); when(furniture.getId()).thenReturn(id); return furniture; }
}
