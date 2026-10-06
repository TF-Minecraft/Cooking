package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;

class FoodParserCoverageTest {
    List<FoodItem> foods;
    List<TagTrack> tracks;
    Cooking previousPlugin;
    @BeforeEach void setup() {
        MockBukkit.mock(); previousPlugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods=new ArrayList<>(FoodLoader.oList); tracks=new ArrayList<>(TrackLoader.oList);
        FoodLoader.oList.clear(); TrackLoader.oList.clear();
        for(String id:List.of("bread","sauce")) {
            FoodItem food=new FoodItem(id,new MemoryConfiguration()); food.setModel(new FoodModel(new ItemStack(Material.BREAD)));
            FoodLoader.oList.add(food);
        }
        for(String id:List.of("freshness","warmth"))
            TrackLoader.oList.add(new TagTrack(id,true,List.of(new TagStep("fresh","Fresh",0,1,1),new TagStep("rotten","Rotten",1000,.5,.5))));
    }
    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods); TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock(); Cooking.plugin=previousPlugin;
    }

    @Test void fieldsRetainNestedValuesAndSkipTrailingTextWithoutAnAssignment() {
        assertEquals(Map.of("type","bread","sauce","sauce(type=sauce;quality=2)","origin","Wheat"),
                FoodParser.extractFields("type=bread;sauce=(sauce(type=sauce;quality=2));origin=Wheat;trailing"));
        assertEquals(Map.of("sauce","sauce(type=sauce)","name","Gravy"),
                FoodParser.extractFields("sauce=(sauce(type=sauce))name=Gravy"));
        assertNotNull(new FoodParser());
    }

    @Test void rangesAndMetadataParseIntoIndependentTemplates() {
        var result=FoodParser.parse("bread(type=bread;amount=2-3;quality=2-4;unique=false;origin=Rye;ingredients=Rye:Salt;tags=freshness.5,warmth.25,unknown.2,bad.1.2;ignored=yes)");
        assertNotNull(result); assertFalse(result.unique); assertTrue(result.explicitQuality);
        assertEquals("bread",result.template.getCategory()); assertEquals("Rye",result.template.getOrigin());
        assertTrue(result.template.getAmount()>=2&&result.template.getAmount()<=3);
        assertEquals(2,result.template.getQualityMin()); assertEquals(4,result.template.getQualityMax());
        assertEquals(2,result.template._parsedQualMin); assertEquals(4,result.template._parsedQualMax);
        assertEquals(List.of("Rye","Salt"),result.template.getIngredients());
        assertEquals(5,result.template.getTagTrack("freshness").getValue()); assertFalse(result.template.hasTagTrack("warmth"));
        assertNotSame(FoodLoader.getByString("bread"),result.template);
        assertEquals(1,FoodLoader.getByString("bread").getAmount());
        assertEquals(64,FoodParser.parse("bread(type=bread;amount=100)").template.getAmount());
        assertEquals(1,FoodParser.parse("bread(type=bread;amount=0)").template.getAmount());
        assertNotNull(FoodParser.parse("bread(type=bread;tags=;ingredients=;sauce=invalid)"));
    }

    @Test void bareTagsAndLegacyAgesUseTheRegisteredTracks() {
        var parsed=FoodParser.parse("bread(type=bread;tags=freshness,:dairy_freshness.5:warmth.2)");
        assertEquals(20,parsed.template.getTagTrack("freshness").getValue());
        assertEquals(2,parsed.template.getTagTrack("warmth").getValue());
    }

    @Test void nestedSauceAndLineageRoundTripThroughThePublicItemCodec() {
        FoodItem item=FoodParser.parse("bread(type=bread;quality=3;origin=Rye;ingredients=Rye:Salt;tags=freshness.5:warmth.2;sauce=(sauce(type=sauce;quality=2));sauce_name=Gravy)").template;
        item.setLineage(IngredientLineage.ofMain("Rye").withExtra("Salt"));
        String serialized=FoodParser.toString(item,4);
        var decoded=FoodParser.parse(serialized);
        assertEquals(4,decoded.template.getAmount()); assertEquals(item.getLineage(),decoded.template.getLineage());
        assertEquals("sauce",decoded.template.getSauce().getId()); assertEquals("Gravy",decoded.template.getSauceName());
        assertEquals(List.of("Rye","Salt"),decoded.template.getIngredients()); assertEquals(5,decoded.template.getTagTrack("freshness").getValue());
        ItemStack stack=ItemBuilder.stamp(new ItemStack(Material.BREAD),item,"Dinner"); stack.setAmount(2);
        assertEquals(2,FoodParser.parse(FoodParser.toString(stack)).template.getAmount());
        assertNull(FoodParser.toString(new ItemStack(Material.STONE)));
    }

    @Test void fieldsCanPrecedeTheTypeWithoutLosingValues() {
        var result=FoodParser.parse("bread(quality=3;tags=freshness.5;ingredients=Rye;type=bread;origin=Rye)");
        assertNotNull(result); assertEquals(3,result.template.getQualityMin());
        assertEquals(5,result.template.getTagTrack("freshness").getValue()); assertEquals(List.of("Rye"),result.template.getIngredients());
    }

    @Test void keysAreCaseInsensitiveAcrossServerLocales() {
        Locale original=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var result=FoodParser.parse("bread(TYPE=bread;QUALITY=3;ORIGIN=Rye)");
            assertNotNull(result); assertTrue(result.explicitQuality); assertEquals(3,result.template.getQualityMin());
            assertEquals("Rye",result.template.getOrigin());
        } finally { Locale.setDefault(original); }
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","bad","bread(","bread(type=bread","bread(type=bread))","bread(type=bread)junk","bread(type=missing)","bread()","bread(quality=3)","bread(type=bread;quality=bad)","bread(type=bread;amount=bad)","bread(type=bread;amount=6-2)","bread(type=bread;quality=4-2)","bread(type=bread;tags=freshness.bad)"})
    void malformedRecipesAreRejectedWithoutThrowing(String recipe) {
        assertNull(FoodParser.parse(recipe));
    }

    @Test void anUnknownOptionalTagDoesNotCrashOtherwiseValidRecipes() {
        var result=FoodParser.parse("bread(type=bread;tags=removed)");
        assertNotNull(result); assertTrue(result.template.getTagTracks().isEmpty());
    }

    @Test void boundedRangesCannotOverflowRandomSelection() {
        assertEquals(64,FoodParser.parse("bread(type=bread;amount=80-100)").template.getAmount());
        var quality=FoodParser.parse("bread(type=bread;quality=2147483647)");
        assertNotNull(quality);
        assertEquals(5,FoodItem.fromItem(ItemBuilder.buildSingle(quality.template,null)).getQualityMin());
    }
}
