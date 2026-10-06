package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.enums.Tag;
import net.tfminecraft.cooking.item.data.OverrideData;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.ModelLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

class FoodItemCoverageTest {
    List<FoodItem> foods;
    List<FoodModel> models;
    List<TagTrack> tracks;
    Cooking previousPlugin;

    @BeforeEach void setup() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        models = new ArrayList<>(ModelLoader.models);
        tracks = new ArrayList<>(TrackLoader.oList);
        FoodLoader.oList.clear(); ModelLoader.models.clear(); TrackLoader.oList.clear();
        QualityConfig.apply(1, 5, null, null);
        TrackLoader.oList.add(track("cooked", false, 0, "raw", "cooked", "burnt"));
        TrackLoader.oList.add(track("freshness", true, 2, "fresh", "stale", "rotten"));
        TrackLoader.oList.add(track("warmth", true, 1, "hot", "warm", "cold"));
    }

    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods);
        ModelLoader.models.clear(); ModelLoader.models.addAll(models);
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock();
        Cooking.plugin = previousPlugin;
    }

    @Test void configurationAndCopiesPreserveFoodMetadataWithoutSharingMutableState() {
        MemoryConfiguration config = config();
        config.set("valuable", true); config.set("mashable", true);
        config.set("cooking-options.pot.time", 10);
        config.set("age.freshness", 2);
        config.set("tag-labels.freshness.fresh", "Garden fresh");
        config.set("tag-labels.invalid", "scalar");
        config.createSection("tag-labels.empty");
        config.set("overrides.carrot.name", "Carrot stew");
        config.set("overrides.invalid", "scalar");
        config.set("model", "bread");
        FoodModel model = new FoodModel(new ItemStack(Material.BREAD));
        ModelLoader.models.add(model);
        config.set("model", model.getId());
        FoodItem original = new FoodItem("stew", config);
        original.setCategory("soup"); original.setOrigin("Carrot"); original.setAmount(3);
        original.setQualityRange(2, 4); original.setBaseFood(7); original.setBaseNutrition(9);
        original.setCatchSizeCm(28); original.setSeafoodCutType("fillet");
        original.setCustomFishingId("lake_trout");
        original.setCarveState("roast", 1, 2);
        original.setLineage(IngredientLineage.ofMain("Carrot").withExtra("Salt"));
        original.addIngredient("Carrot"); original.addIngredient("Carrot");
        original.setAgeRemainder("freshness", .25);
        original.addTagTrack("freshness");
        FoodItem sauce = food("sauce"); original.setSauce(sauce); original.setSauceName("Gravy");
        FoodItem copy = new FoodItem(original);
        assertEquals("stew", copy.getId()); assertEquals("Food", copy.getName());
        assertTrue(copy.isValuable()); assertTrue(copy.isMashable()); assertTrue(copy.isEdible());
        assertEquals("soup", copy.getCategory()); assertEquals("Carrot", copy.getOrigin());
        assertEquals(3, copy.getAmount()); assertEquals(2, copy.getQualityMin()); assertEquals(4, copy.getQualityMax());
        assertEquals(7, copy.getBaseFood()); assertEquals(9, copy.getBaseNutrition()); assertTrue(copy.hasBaseOverride());
        assertEquals(28, copy.getCatchSizeCm()); assertEquals("fillet", copy.getSeafoodCutType());
        assertEquals("lake_trout", copy.getCustomFishingId());
        assertEquals("roast", copy.getCarveSequencePdc()); assertEquals(1, copy.getCarveNextIndex());
        assertEquals(2, copy.getCarveRemaining()); assertEquals(original.getLineage(), copy.getLineage());
        assertEquals(List.of("Carrot"), copy.getIngredients()); assertEquals(.25, copy.getAgeRemainder("freshness"));
        assertEquals("Garden fresh", copy.getTagLabel("freshness", "fresh"));
        assertNotSame(original.getModel(), copy.getModel()); assertNotSame(sauce, copy.getSauce());
        assertEquals("Gravy", copy.getSauceName()); assertTrue(copy.hasSauceName());
        copy.getIngredients().add("Onion"); copy.getTagTrack("freshness").setValue(10);
        assertEquals(List.of("Carrot"), original.getIngredients());
        assertEquals(0, original.getTagTrack("freshness").getValue());
        assertTrue(copy.getLastUpdate() > 0); assertTrue(copy.hasTagTrack("cooked"));
        assertNull(copy.getTagLabel(null, "fresh")); assertNull(copy.getTagLabel("freshness", null));
        assertNull(copy.getTagLabel("unknown", "fresh"));
        copy.setMashable(false); copy.setEdible(false); assertFalse(copy.isMashable()); assertFalse(copy.isEdible());
        copy.setSeafoodCutType(" "); copy.setCustomFishingId(null);
        assertNull(copy.getSeafoodCutType()); assertNull(copy.getCustomFishingId());
        copy.setLineage(null); assertTrue(copy.getLineage().isEmpty());
        copy.setCarveNextIndex(2); copy.setCarveRemaining(1);
        assertEquals(2, copy.getCarveNextIndex()); assertEquals(1, copy.getCarveRemaining());
    }

    @Test void copiedCookingOptionsAreIndependentOfTheirTemplate() {
        MemoryConfiguration config = config(); config.set("cooking-options.pot.time", 10);
        FoodItem original = new FoodItem("stew", config);
        FoodItem copy = new FoodItem(original);
        copy.getCookData().getParameters().clear();
        assertTrue(original.getCookData().hasMethod(Method.POT));
        assertFalse(copy.canBeCooked());
    }

    @Test void originOverridesAreIndependentOfTheServersDefaultLocale() {
        MemoryConfiguration config = config();
        config.set("age.freshness", 3); config.set("carve-sequence", "default");
        config.set("overrides.fish.age.freshness", 2);
        config.set("overrides.fish.carve-sequence", "fish_cuts");
        FoodItem food = new FoodItem("fish", config);
        food.setOrigin("fish");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(2, food.resolveAgeMultiplier("FRESHNESS"));
            assertEquals("fish_cuts", food.getCarveSequenceId());
        } finally { Locale.setDefault(previous); }
    }

    @Test void ageConfigurationUsesOriginOverrideThenTypeThenDefault() {
        MemoryConfiguration config = config(); config.set("age.freshness", 3);
        config.set("carve-sequence", "default");
        config.set("overrides.carrot.age.freshness", 2);
        config.set("overrides.carrot.carve-sequence", "carrot_cuts");
        FoodItem food = new FoodItem("stew", config);
        assertEquals(3, food.resolveAgeMultiplier("FRESHNESS"));
        assertEquals(1, food.resolveAgeMultiplier(null)); assertEquals(1, food.resolveAgeMultiplier("warmth"));
        assertEquals("default", food.getCarveSequenceId());
        food.setOrigin("unknown"); assertEquals(3, food.resolveAgeMultiplier("freshness"));
        assertEquals("default", food.getCarveSequenceId());
        food.setOrigin("carrot"); assertEquals(2, food.resolveAgeMultiplier("freshness"));
        assertEquals(1, food.resolveAgeMultiplier("warmth")); assertEquals("carrot_cuts", food.getCarveSequenceId());
        food.getOverrides().put("ONION", new OverrideData(null, null, null)); food.setOrigin("onion");
        assertEquals("default", food.getCarveSequenceId());
    }

    @Test void ageRemaindersRoundTripAndIgnoreMalformedEntries() {
        FoodItem food = food("bread"); assertFalse(food.hasAgeRemainder()); assertNull(food.encodeAgeRemainder());
        food.setAgeRemainder(null, .2); assertEquals(0, food.getAgeRemainder(null));
        food.setAgeRemainder("FRESHNESS", .25); food.setAgeRemainder("warmth", .5);
        FoodItem copy = food("copy"); copy.decodeAgeRemainder(food.encodeAgeRemainder());
        assertEquals(.25, copy.getAgeRemainder("freshness")); assertEquals(.5, copy.getAgeRemainder("WARMTH"));
        copy.decodeAgeRemainder("bad;:2;empty:;negative:-1;zero:0;nan:NaN;text:bad;freshness:0.75");
        assertEquals(.75, copy.getAgeRemainder("freshness")); assertEquals(0, copy.getAgeRemainder("negative"));
        assertEquals(0, copy.getAgeRemainder("warmth"));
        copy.setAgeRemainder("freshness", 0); assertFalse(copy.hasAgeRemainder());
        copy.decodeAgeRemainder(null); copy.decodeAgeRemainder(" ");
        food.clearAgeRemainders(); assertFalse(food.hasAgeRemainder());
    }

    @ParameterizedTest @ValueSource(doubles={Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void corruptAgeRemaindersCannotPoisonFutureAging(double value) {
        FoodItem food = food("bread");
        food.setAgeRemainder("freshness", value); assertFalse(food.hasAgeRemainder());
        food.decodeAgeRemainder("freshness:" + value); assertFalse(food.hasAgeRemainder());
    }

    @Test void ageUpdateOnlyCountsWholeSecondsAndExpiresRoomTemperature() {
        FoodItem food = food("bread");
        food.addTagTrack("freshness"); food.addTagTrack("warmth"); food.addTagTrack("cooked");
        food.setLastUpdate(System.currentTimeMillis()+60000); food.updateAge();
        assertEquals(0, food.getTagTrack("freshness").getValue());
        food.setLastUpdate(System.currentTimeMillis()); food.updateAge();
        assertEquals(0, food.getTagTrack("freshness").getValue());
        food.setLastUpdate(System.currentTimeMillis()-5000); food.updateAge();
        assertTrue(food.getTagTrack("freshness").getValue() >= 5);
        assertEquals(0, food.getTagTrack("cooked").getValue());
        assertTrue(food.hasTagTrack("warmth"));
        food.setLastUpdate(System.currentTimeMillis()-20000); food.updateAge();
        assertFalse(food.hasTagTrack("warmth"));
        ItemStack stack = new ItemStack(Material.BREAD); food.updateAge(stack);
        assertTrue(stack.getItemMeta().getPersistentDataContainer().has(Keys.LAST_UPDATE));
        FoodItem frozen = new FoodItem("salt", "Salt", false);
        frozen.setAgeRemainder("freshness", .4);
        edit(stack, m -> m.getPersistentDataContainer().set(Keys.AGE_REMAINDER, PersistentDataType.STRING, "freshness:0.4"));
        assertSame(stack, frozen.updateAge(stack));
        assertFalse(stack.getItemMeta().getPersistentDataContainer().has(Keys.LAST_UPDATE));
        assertFalse(stack.getItemMeta().getPersistentDataContainer().has(Keys.AGE_REMAINDER));
        assertSame(Material.AIR, frozen.updateAge(new ItemStack(Material.AIR)).getType());
    }

    @Test void tagsAreSortedAndComparedByCurrentStageAndDriveEdibilityAndNutrition() {
        FoodItem food = food("bread"); food.setQualityRange(1,1);
        food.setBaseFood(10); food.setBaseNutrition(10);
        food.addOrModifyTrack(null); food.removeTrack(null);
        food.addTagTrack("freshness"); food.addTagTrack("warmth"); food.addTagTrack("cooked");
        food.addOrModifyTrack(track("equal", false, 1, "custom"));
        assertEquals("cooked", food.getTagTracks().getFirst().getId());
        assertEquals("freshness", food.getTagTracks().getLast().getId());
        assertTrue(food.hasTag(Tag.RAW)); assertFalse(food.hasTag(Tag.PROCESSED));
        food.setEdibleWhenCooked(true); assertFalse(food.isEdible());
        food.getTagTrack("cooked").setValue(10); assertTrue(food.isEdible());
        FoodItem copy = new FoodItem(food); assertTrue(food.sameTags(copy));
        copy.getTagTrack("freshness").setValue(10); assertFalse(food.sameTags(copy));
        copy.removeTrack("freshness"); assertFalse(food.sameTags(copy));
        assertEquals(10, food.getFinalFood()); assertEquals(2.8, food.getFinalNutrition());
        FoodItem sauce = food("sauce"); sauce.setQualityRange(1,1); sauce.setBaseFood(2); sauce.setBaseNutrition(5);
        food.setSauce(sauce); assertEquals(12, food.getFinalFood()); assertEquals(4.2, food.getFinalNutrition(), .0001);
        food.setSauceName(""); assertFalse(food.hasSauceName());
    }

    @Test void modelLookupUsesTheOriginAndFallsBackWhenOverrideIsMissing() {
        FoodItem food = food("bread"); FoodModel base = food.getModel();
        FoodModel alternate = new FoodModel("fish_model", modelConfig()); ModelLoader.models.add(alternate);
        food.getOverrides().put("FISH", new OverrideData(null, "fish_model", null));
        food.getOverrides().put("NULL", null);
        food.setOrigin("onion"); assertSame(base, food.getModel());
        food.setOrigin("fish"); assertSame(alternate, food.getModel());
        assertNotNull(food.getModelData());
        ModelLoader.models.clear(); assertSame(base, food.getModel());
        food.setCarveState("unregistered", 1, 2);
        assertNotNull(food.getModelData());
        assertEquals(food.getBaseFood(), food.getFinalFood());
        assertEquals(.6, food.getFinalNutrition());
    }

    @Test void stampedFoodRestoresIdentityTagsIngredientsSauceAndCookingProgress() {
        FoodItem base = food("bread"); base.cookData.getParameters().put(Method.POT, new net.tfminecraft.cooking.item.data.CookParameter(1,10,30));
        base.setCategory("bread"); FoodLoader.oList.add(base);
        FoodItem sauce = food("sauce"); FoodLoader.oList.add(sauce);
        FoodItem item = new FoodItem(base);
        item.setOrigin("Wheat"); item.setQualityRange(3,3); item.setBaseFood(6); item.setBaseNutrition(4);
        item.addTagTrack("freshness"); item.addTagTrack("warmth");
        item.setAgeRemainder("freshness", .2); item.setLastUpdate(12345L);
        item.setSauce(sauce); item.setSauceName("Gravy"); item.addIngredient("Wheat"); item.addIngredient("Salt");
        item.setLineage(IngredientLineage.ofMain("Wheat").withExtra("Salt"));
        item.setCatchSizeCm(20); item.setSeafoodCutType("fillet"); item.setCustomFishingId("river");
        item.getCookData().restore(Method.POT, 7);
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.BREAD), item, "Dinner");
        edit(stack, m -> { var p=m.getPersistentDataContainer(); p.set(Keys.CARVE_SEQUENCE,PersistentDataType.STRING,"roast");
            p.set(Keys.CARVE_NEXT_INDEX,PersistentDataType.INTEGER,1); p.set(Keys.CARVE_REMAINING,PersistentDataType.INTEGER,2); });
        FoodItem restored = FoodItem.fromItem(stack);
        assertNotNull(restored); assertEquals("bread",restored.getId()); assertEquals("bread",restored.getCategory());
        assertEquals("Wheat",restored.getOrigin()); assertEquals(3,restored.getQualityMin());
        assertEquals(6,restored.getBaseFood()); assertEquals(4,restored.getBaseNutrition());
        assertEquals(.2,restored.getAgeRemainder("freshness")); assertEquals(12345,restored.getLastUpdate());
        assertEquals("sauce",restored.getSauce().getId()); assertEquals("Gravy",restored.getSauceName());
        assertEquals(List.of("Wheat","Salt"),restored.getIngredients()); assertEquals(item.getLineage(),restored.getLineage());
        assertEquals(20,restored.getCatchSizeCm()); assertEquals("fillet",restored.getSeafoodCutType());
        assertEquals("river",restored.getCustomFishingId()); assertEquals(Method.POT,restored.getCookData().getCurrentMethod());
        assertEquals(7,restored.getCookData().getCurrentTime()); assertEquals(1,restored.getCarveNextIndex());
        assertEquals(2,restored.getCarveRemaining());
    }

    @Test void unknownOrDamagedItemMetadataIsIgnoredWithoutLosingRecognizedFood() {
        assertNull(FoodItem.fromItem(null)); assertNull(FoodItem.fromItem(new ItemStack(Material.AIR)));
        ItemStack stack = new ItemStack(Material.BREAD);
        edit(stack,m -> m.setDisplayName("Plain bread")); assertNull(FoodItem.fromItem(stack));
        edit(stack,m -> m.getPersistentDataContainer().set(Keys.FOOD_ID,PersistentDataType.STRING,"unknown"));
        assertNull(FoodItem.fromItem(stack));
        FoodItem base = new FoodItem("unknown", config()); FoodLoader.oList.add(base);
        edit(stack,m -> { var p=m.getPersistentDataContainer();
            p.set(Keys.TAGS,PersistentDataType.STRING,"bad;freshness.bad;freshness.1.2;missing.1;freshness.10;warmth.1");
            p.set(Keys.COOK_METHOD,PersistentDataType.STRING,"invalid");
            p.set(Keys.CARVE_SEQUENCE,PersistentDataType.STRING,"removed");
            p.set(Keys.INGREDIENTS,PersistentDataType.STRING,"Wheat: :Salt");
            p.set(Keys.SAUCE,PersistentDataType.STRING,"not_a_food"); });
        long before=System.currentTimeMillis(); FoodItem restored=FoodItem.fromItem(stack);
        assertNotNull(restored); assertNotNull(restored.getModel()); assertEquals(10,restored.getTagTrack("freshness").getValue());
        assertEquals(1,restored.getTagTrack("warmth").getValue()); assertFalse(restored.getCookData().isBeingCooked());
        assertEquals(0,restored.getCarveRemaining()); assertEquals(0,restored.getCarveNextIndex());
        assertTrue(restored.getLastUpdate()>=before); assertNull(restored.getSauce());
        assertEquals(List.of("Wheat","Salt"),restored.getIngredients());
        edit(stack,m -> m.getPersistentDataContainer().set(Keys.COOK_METHOD,PersistentDataType.STRING,"POT"));
        assertEquals(0,FoodItem.fromItem(stack).getCookData().getCurrentTime());
        FoodLoader.oList.clear(); FoodLoader.oList.add(new FoodItem("unknown","Salt",false));
        assertEquals(0,FoodItem.fromItem(stack).getLastUpdate());
    }

    static MemoryConfiguration config() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("name","Food");
        config.set("food",2); config.set("nutrition",2); return config;
    }
    static MemoryConfiguration modelConfig() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("default.gui.item","v.bread"); return config;
    }
    static FoodItem food(String id) {
        FoodItem food=new FoodItem(id,config()); food.setModel(new FoodModel(new ItemStack(Material.BREAD))); return food;
    }
    static TagTrack track(String id,boolean ageable,int index,String... steps) {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("ageable",ageable);
        for(int i=0;i<steps.length;i++) { config.set(steps[i]+".value",i*10); config.set(steps[i]+".name",steps[i]); }
        return new TagTrack(id,index,config);
    }
    static void edit(ItemStack stack,Consumer<ItemMeta> edit) {
        ItemMeta meta=stack.getItemMeta(); edit.accept(meta); stack.setItemMeta(meta);
    }
}
