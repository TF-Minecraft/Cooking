package net.tfminecraft.cooking.item;

import net.tfminecraft.cooking.utils.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class NamingRulesCoverageTest {
    Cooking plugin;
    List<TagTrack> tracks;
    @BeforeEach void setup() {
        MockBukkit.mock(); plugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        tracks=new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
        NamingConfig.apply(List.of("sweet","empty","freshness","colour","unknown"),3,2,2,null,
                Map.of("food",Map.of("sweet",Map.of("sweet","Sugar-coated"))),Map.of("apple","Apple"));
    }
    @AfterEach void cleanup() {
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks); NamingConfig.apply(List.of("sweet"),3,2,2,null,null,null);
        MockBukkit.unmock(); Cooking.plugin=plugin;
    }

    @Test void namesApplyIngredientAndTagLabelsWithoutDuplicatingFillers() {
        FoodItem food=food("meal","food","bread");
        TagTrack sweet=track("sweet","sweet","Sweet"); food.addOrModifyTrack(sweet);
        assertEquals("Sugar-coated",TagDisplayNames.resolve(food,sweet,sweet.getCurrentStep()));
        assertEquals("Unknown",TagDisplayNames.resolve(food,sweet,null)); assertEquals("Sweet",TagDisplayNames.resolve(null,sweet,sweet.getCurrentStep()));
        food.setCategory(null); assertEquals("Sweet",TagDisplayNames.resolve(food,sweet,sweet.getCurrentStep()));
        food.setCategory(""); assertEquals("Sweet",TagDisplayNames.resolve(food,sweet,sweet.getCurrentStep())); food.setCategory("food");
        MemoryConfiguration settings=new MemoryConfiguration(); settings.set("name","{colour}{prefixes}{ingredients} Pie"); settings.set("tag-labels.sweet.sweet","Sugary");
        FoodItem named=new FoodItem("pie",settings); named.setCategory("food"); named.addIngredient("apple"); named.addOrModifyTrack(sweet);
        assertEquals("Sugary",TagDisplayNames.resolve(named,sweet,sweet.getCurrentStep()));
        assertTrue(NameComposer.compose(named,Map.of("colour","#ffffff")).endsWith("Sugary Apple Pie"));
        settings.set("name", "{ingredients}Pie");
        FoodItem emptyPie = new FoodItem("empty_pie", settings);
        assertEquals("Mixed Pie", NameComposer.compose(emptyPie, null));
        emptyPie.addIngredient("apple");
        assertEquals("Apple Pie", NameComposer.compose(emptyPie, null));
        settings.set("name", "{ingredients}{colour}");
        assertEquals("", NameComposer.compose(new FoodItem("empty", settings), null));
        assertEquals("",NameComposer.compose(null,null)); assertEquals("",NameComposer.formatPrefixes(null));
        named.addOrModifyTrack(new TagTrack("empty",false,List.of())); named.addOrModifyTrack(track("freshness","fresh",""));
        named.addOrModifyTrack(track("colour","red","#ff0000Red"));
        assertTrue(NameComposer.formatPrefixes(named).endsWith("Red "));
        named.addOrModifyTrack(track("colour","red","#zzzzzzRed")); assertTrue(NameComposer.formatPrefixes(named).endsWith("#zzzzzzRed "));
        Map<String,String> extras=new HashMap<>(); extras.put("colour",null); assertNotNull(NameComposer.compose(named,extras));
        FoodItem unnamed=new FoodItem("blank",null,true); assertEquals("",NameComposer.compose(unnamed,null));
        assertEquals("",NameComposer.formatFillerPhrase(null)); assertEquals("Mixed Soup",NameComposer.formatFillerPhrase(List.of(),"Soup"));
        assertEquals("Apple ",NameComposer.formatFillerPhrase(Arrays.asList(null," ","apple","apple")));
        assertEquals("A and B ",NameComposer.formatFillerPhrase(List.of("A","B")));
        assertEquals("A and B Soup",NameComposer.formatFillerPhrase(List.of("A","B"),"Soup"));
        assertEquals("A, B and C ",NameComposer.formatFillerPhrase(List.of("A","B","C","D")));
        assertEquals("A, B and C Soup",NameComposer.formatFillerPhrase(List.of("A","B","C"),"Soup"));
    }

    @Test void addonRulesRejectDuplicatesAndLimitsAndClassifyDistinctFlavours() {
        Player player=mock(Player.class); FoodItem garnish=food("garnish","garnish","Basil"), spice=food("spice","spice","Pepper"), sugar=food("sugar","sweetener",null);
        Map<String,FoodItem> slots=new HashMap<>(); slots.put("empty",null);
        assertFalse(StationAddonRules.hasDuplicateOrigin(slots, garnish));
        assertFalse(StationAddonRules.canAcceptAddon(slots,null,player)); assertFalse(StationAddonRules.canAcceptAddon(slots,sugar,player));
        assertTrue(StationAddonRules.canAcceptAddon(slots,garnish,player)); assertEquals(StationAddonRules.AddonProfile.NONE,StationAddonRules.classifyAddons(slots));
        slots.put("first",garnish); assertFalse(StationAddonRules.canAcceptAddon(slots,garnish,player)); verify(player).sendMessage("§cThat ingredient is already in there.");
        assertEquals(StationAddonRules.AddonProfile.SINGLE,StationAddonRules.classifyAddons(slots));
        slots.put("second",spice); assertEquals(StationAddonRules.AddonProfile.ROUNDED,StationAddonRules.classifyAddons(slots));
        assertFalse(StationAddonRules.canAcceptAddon(slots,food("herb","garnish","Parsley"),player)); verify(player).sendMessage("§cYou can't add more garnish or spice.");
        slots.put("second",food("herb","garnish",null)); assertEquals(StationAddonRules.AddonProfile.AROMATIC,StationAddonRules.classifyAddons(slots));
        assertFalse(StationAddonRules.hasDuplicateOrigin(slots,sugar)); sugar.setOrigin(" "); assertFalse(StationAddonRules.hasDuplicateOrigin(slots,sugar));
        assertTrue(StationAddonRules.canAcceptSweetener(slots,sugar,player)); slots.put("sugar",sugar);
        assertFalse(StationAddonRules.canAcceptSweetener(slots,sugar,player)); verify(player).sendMessage("§cSugar is already in the pan.");
        assertFalse(StationAddonRules.canAcceptSweetener(slots,null,player)); assertFalse(StationAddonRules.canAcceptSweetener(slots,garnish,player));
        assertFalse(StationAddonRules.hasValuable(null)); assertFalse(StationAddonRules.hasValuable(slots));
        MemoryConfiguration valuable=new MemoryConfiguration(); valuable.set("valuable",true); slots.put("valuable",new FoodItem("valuable",valuable)); assertTrue(StationAddonRules.hasValuable(slots));
    }

    @Test void configuredTagsApplyToSweetSeasonedValuableAndMixedDishes() {
        for(String id:List.of("sweet","seasoning","aromatic","rounded","flavourful","fruity","richly_fruity")) TrackLoader.oList.add(track(id,id,id));
        FoodItem product=food("product","food",null), sugar=food("sugar","sweetener",null), salt=food("salt","salt",null);
        Map<String,FoodItem> slots=new HashMap<>(); slots.put("empty",null);
        StationAddonRules.applySweetTag(product,slots); StationAddonRules.applySeasoningTag(product,slots); StationAddonRules.applyAddonTags(product,slots);
        StationAddonRules.applyFlavourfulTag(null,slots); StationAddonRules.applyFlavourfulTag(product,slots); assertFalse(product.hasTagTrack("sweet"));
        slots.put("sugar",sugar); StationAddonRules.applySweetTag(product,slots); assertTrue(product.hasTagTrack("sweet"));
        slots.put("salt",salt); StationAddonRules.applySeasoningTag(product,slots); assertEquals(0,product.getTagTrack("seasoning").getValue());
        slots.put("pepper",food("pepper","pepper",null)); StationAddonRules.applySeasoningTag(product,slots); assertEquals(1,product.getTagTrack("seasoning").getValue());
        slots.put("one",food("herb","garnish","Basil")); slots.put("two",food("herb2","garnish","Parsley"));
        StationAddonRules.applyAddonTags(product,slots); assertTrue(product.hasTagTrack("aromatic"));
        slots.put("two",food("spice","spice","Cumin")); StationAddonRules.applyAddonTags(product,slots); assertTrue(product.hasTagTrack("rounded"));
        MemoryConfiguration valuable=new MemoryConfiguration(); valuable.set("valuable",true); slots.put("valuable",new FoodItem("valuable",valuable));
        StationAddonRules.applyFlavourfulTag(product,slots); assertTrue(product.hasTagTrack("flavourful"));
        DoughMixinRules.applyDoughTags(product,true,List.of("Apple")); assertTrue(product.hasTagTrack("fruity"));
        DoughMixinRules.applyDoughTags(product,false,List.of("Apple","Pear")); assertTrue(product.hasTagTrack("richly_fruity"));
        DoughMixinRules.applyDoughTags(product,false,null);
    }

    @Test void missingOptionalTagTemplatesDoNotCrashFoodPreparation() {
        FoodItem product=food("product","food",null); MemoryConfiguration valuable=new MemoryConfiguration(); valuable.set("valuable",true);
        Map<String,FoodItem> slots=new HashMap<>(); slots.put("sugar",food("sugar","sweetener",null)); slots.put("salt",food("salt","salt",null));
        slots.put("valuable",new FoodItem("valuable",valuable)); slots.put("one",food("garnish","garnish","Basil")); slots.put("two",food("garnish2","garnish","Parsley"));
        assertDoesNotThrow(()->StationAddonRules.applySweetTag(product,slots)); assertDoesNotThrow(()->StationAddonRules.applySeasoningTag(product,slots));
        assertDoesNotThrow(()->StationAddonRules.applyFlavourfulTag(product,slots)); assertDoesNotThrow(()->StationAddonRules.applyAddonTags(product,slots));
        slots.put("two",food("spice","spice","Cumin")); assertDoesNotThrow(()->StationAddonRules.applyAddonTags(product,slots));
        assertDoesNotThrow(()->DoughMixinRules.applyDoughTags(product,true,List.of("Apple")));
        assertDoesNotThrow(()->DoughMixinRules.applyDoughTags(product,true,List.of("Apple","Pear")));
        assertTrue(product.getTagTracks().isEmpty());
    }

    @Test void addonClassificationIsIndependentOfTheServerLocale() {
        Locale old=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(StationAddonRules.AddonProfile.ROUNDED,StationAddonRules.classifyAddons(Map.of(
                    "one",food("garnish","GARNISH","Basil"),"two",food("spice","SPICE","Cumin"))));
        } finally { Locale.setDefault(old); }
    }

    @Test void doughIngredientLimitsGiveUsefulFeedback() {
        Player player=mock(Player.class); FoodItem fruit=food("apple","fruit","Apple"), sugar=food("sugar","sweetener",null);
        assertTrue(DoughMixinRules.canAcceptSugar(false,sugar,player)); assertFalse(DoughMixinRules.canAcceptSugar(true,sugar,player));
        assertFalse(DoughMixinRules.canAcceptSugar(false,null,player)); assertFalse(DoughMixinRules.canAcceptSugar(false,fruit,player));
        assertTrue(DoughMixinRules.canAcceptFruit(List.of(),fruit,player)); assertFalse(DoughMixinRules.canAcceptFruit(List.of("Apple"),fruit,player));
        assertFalse(DoughMixinRules.canAcceptFruit(List.of("Pear","Plum"),fruit,player));
        assertFalse(DoughMixinRules.canAcceptFruit(List.of(),null,player)); assertFalse(DoughMixinRules.canAcceptFruit(List.of(),sugar,player));
    }

    private FoodItem food(String id,String category,String origin) { FoodItem item=new FoodItem(id,new MemoryConfiguration()); item.setCategory(category); item.setOrigin(origin); return item; }
    private TagTrack track(String id,String step,String name) { return new TagTrack(id,false,List.of(new TagStep(step,name,0,1,1))); }
}
