package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemAgingCoverageTest {
    Cooking plugin;
    List<FoodItem> foods;
    List<TagTrack> tracks;
    @BeforeEach void setup() {
        MockBukkit.mock(); plugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods=new ArrayList<>(FoodLoader.oList); FoodLoader.oList.clear();
        tracks=new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
        TrackLoader.oList.add(new TagTrack("freshness",true,List.of(new TagStep("fresh","Fresh",0,1,1),new TagStep("stale","Stale",4,.8,.8))));
        TrackLoader.oList.add(new TagTrack("warmth",true,List.of(new TagStep("hot","Hot",0,1,1),new TagStep("cold","Cold",20,1,1))));
    }
    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods); TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock(); Cooking.plugin=plugin;
    }

    @Test void frequentUpdatesRetainPartialAgingUntilTheNextFreshnessStage() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("age.freshness",2.0);
        FoodItem food=food("bread",config); food.addOrModifyTrack(TrackLoader.getByString("freshness")); food.setLastUpdate(10_000);
        ItemStack stack=ItemBuilder.stamp(new ItemStack(Material.BREAD),food,"Bread");
        try(var clock=mockStatic(StackNormalizer.class,CALLS_REAL_METHODS)) {
            for(int tick=1;tick<=8;tick++) {
                clock.when(StackNormalizer::quantizedNow).thenReturn(10_000L+tick*1000L);
                stack=ItemUpdater.updateItem(stack,FoodItem.fromItem(stack),null);
                assertNotNull(stack);
                FoodItem aged=FoodItem.fromItem(stack);
                assertEquals(tick/2,aged.getTagTrack("freshness").getValue(),"tick "+tick);
                assertEquals(tick%2*.5,aged.getAgeRemainder("freshness"),.00001);
            }
        }
        assertEquals("stale",FoodItem.fromItem(stack).getTagTrack("freshness").getCurrentStep().getId());
    }

    @Test void customTagStageChangesTriggerVisualUpdatesEvenWhenBothMapToCustomEnum() {
        FoodItem food=food("custom",new MemoryConfiguration());
        food.addOrModifyTrack(new TagTrack("ferment",true,List.of(new TagStep("phase_one","Young",0,1,1),new TagStep("phase_two","Aged",1,2,1))));
        assertTrue(ItemUpdater.applyAging(food,1)); assertEquals("phase_two",food.getTagTrack("ferment").getCurrentStep().getId());
    }

    @Test void agingHandlesWarmthNestedSaucesAndNonAgeableTracks() {
        FoodItem food=food("bread",new MemoryConfiguration()); FoodItem sauce=food("sauce",new MemoryConfiguration());
        food.addOrModifyTrack(TrackLoader.getByString("warmth")); sauce.addOrModifyTrack(TrackLoader.getByString("freshness")); food.setSauce(sauce);
        food.addOrModifyTrack(new TagTrack("cooked",false,List.of(new TagStep("cooked","Cooked",0,1,1))));
        food.addOrModifyTrack(new TagTrack("empty",true,List.of()));
        assertFalse(ItemUpdater.applyAging(null,1)); assertFalse(ItemUpdater.applyAging(food,0));
        assertTrue(ItemUpdater.applyAging(food,20)); assertNull(food.getTagTrack("warmth"));
        assertEquals("stale",food.getSauce().getTagTrack("freshness").getCurrentStep().getId());
        assertEquals(0,food.getTagTrack("cooked").getValue());
        assertFalse(ItemUpdater.applyAging(food,1));
    }

    @Test void updaterPreservesStackAmountAndDisplayNamesAndRebuildsMissingLore() {
        FoodItem food=food("bread",new MemoryConfiguration());
        ItemStack stack=new ItemStack(Material.BREAD,3); var meta=stack.getItemMeta(); meta.setDisplayName("Family recipe"); stack.setItemMeta(meta);
        food.setModel(new FoodModel(new ItemStack(Material.PAPER)));
        ItemStack updated=ItemUpdater.applyItemUpdate(stack,food,null);
        assertEquals(3,updated.getAmount()); assertEquals(Material.PAPER,updated.getType());
        assertEquals("Family recipe",updated.getItemMeta().getDisplayName()); assertNotNull(FoodItem.fromItem(updated));
        ItemStack plain=mock(ItemStack.class); when(plain.getType()).thenReturn(Material.BREAD); when(plain.getAmount()).thenReturn(1);
        assertEquals("bread",ItemUpdater.applyItemUpdate(plain,food,null).getItemMeta().getDisplayName());
        assertFalse(ItemUpdater.needsLoreRebuild(java.util.Arrays.asList(null,"Nutrition 1","Food 2"),true));
        assertNull(ItemUpdater.applyItemUpdate(null,food,null)); assertNull(ItemUpdater.applyItemUpdate(stack,null,null));
        assertNull(ItemUpdater.updateItem(null,food,null)); assertNull(ItemUpdater.updateItem(stack,null,null));
        assertNull(ItemUpdater.updateItem(new ItemStack(Material.AIR),food,null));
        food.setModel(null); assertEquals("bread",ItemUpdater.applyItemUpdate(new ItemStack(Material.BREAD),food,null).getItemMeta().getDisplayName());
        assertNotNull(new ItemUpdater());
    }

    @Test void fixedFoodsRebuildBrokenLoreButLeaveCompleteItemsUntouched() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("update",false); FoodItem food=food("salt",config);
        ItemStack stack=new ItemStack(Material.PAPER);
        assertNotNull(ItemUpdater.updateItem(stack,food,null,true));
        assertNull(ItemUpdater.updateItem(stack,food,null,false));
        assertFalse(stack.getItemMeta().getPersistentDataContainer().has(Keys.LAST_UPDATE));
    }

    @Test void heldFoodsWaitForVisibleChangesWhileUnheldFoodsPersistClockUpdates() {
        FoodItem food=food("bread",new MemoryConfiguration()); food.addOrModifyTrack(TrackLoader.getByString("freshness")); food.setLastUpdate(10_000);
        ItemStack stack=ItemBuilder.stamp(new ItemStack(Material.BREAD),food,"Bread");
        try(var clock=mockStatic(StackNormalizer.class,CALLS_REAL_METHODS)) {
            clock.when(StackNormalizer::quantizedNow).thenReturn(11_000L);
            assertNull(ItemUpdater.updateItem(stack,FoodItem.fromItem(stack),null,true));
            clock.when(StackNormalizer::quantizedNow).thenReturn(14_000L);
            stack=ItemUpdater.updateItem(stack,FoodItem.fromItem(stack),null,true); assertNotNull(stack);
            assertEquals("stale",FoodItem.fromItem(stack).getTagTrack("freshness").getCurrentStep().getId());
            assertNull(ItemUpdater.updateItem(stack,FoodItem.fromItem(stack),null,false));
            clock.when(StackNormalizer::quantizedNow).thenReturn(15_000L);
            stack=ItemUpdater.updateItem(stack,FoodItem.fromItem(stack),null,false); assertNotNull(stack);
            var meta=stack.getItemMeta(); meta.getPersistentDataContainer().remove(Keys.LAST_UPDATE); stack.setItemMeta(meta);
            assertNotNull(ItemUpdater.updateItem(stack,FoodItem.fromItem(stack),null,false));
        }
        food.setSauce(food("sauce",new MemoryConfiguration())); food.getSauce().addOrModifyTrack(TrackLoader.getByString("warmth"));
        food.getSauce().getTagTrack("warmth").forceSetValue(20);
        assertNotNull(ItemUpdater.updateItem(new ItemStack(Material.PAPER),food,null,true));
        assertNull(food.getSauce().getTagTrack("warmth"));
    }

    @Test void ageScalingSaturatesInsteadOfWrappingAndRejectsInfiniteDurations() {
        assertEquals(Integer.MAX_VALUE,AgeScale.apply(Integer.MAX_VALUE-1,10,1,0).value());
        assertEquals(Integer.MAX_VALUE,AgeScale.apply(5,Long.MAX_VALUE,.1,0).value());
        assertEquals(1,AgeScale.clamp(Double.POSITIVE_INFINITY));
        assertEquals(Integer.MAX_VALUE,AgeScale.migrateTrackValue("dairy_freshness",Integer.MAX_VALUE));
    }

    @Test void unavailableModelsDoNotDestroyAnExistingItemOrProduceAnInvalidNewItem() {
        var api=mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class,RETURNS_DEEP_STUBS);
        when(api.getCreator().getItemFromPath("missing.model")).thenReturn(null);
        try(var tlibs=mockStatic(net.tfminecraft.tlibs.TLibs.class)) {
            tlibs.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
            MemoryConfiguration modelConfig=new MemoryConfiguration(); modelConfig.set("raw.gui.item","missing.model");
            FoodItem food=food("bread",new MemoryConfiguration()); food.setModel(new FoodModel("missing",modelConfig));
            ItemStack input=new ItemStack(Material.BREAD,3);
            assertEquals(Material.BREAD,ItemUpdater.applyItemUpdate(input,food,null).getType());
            assertNull(ItemBuilder.buildSingleWithQuality(food,2));
            food.setModel(new FoodModel("empty",new MemoryConfiguration()));
            assertNull(ItemBuilder.buildSingleWithQuality(food,2));
            food.setModel(null); assertNull(ItemBuilder.buildSingleWithQuality(food,2));
        }
    }

    private FoodItem food(String id,MemoryConfiguration config) {
        config.set("name",id); FoodItem food=new FoodItem(id,config); FoodLoader.oList.add(food); return food;
    }
}
