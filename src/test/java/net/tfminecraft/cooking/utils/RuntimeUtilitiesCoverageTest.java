package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.util.LegacyModelData;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class RuntimeUtilitiesCoverageTest {
    ServerMock server;
    Cooking plugin;
    List<FoodItem> foods;
    List<TagTrack> tracks;
    @BeforeEach void setup() {
        server=MockBukkit.mock(); plugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods=new ArrayList<>(FoodLoader.oList); FoodLoader.oList.clear(); tracks=new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
    }
    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods); TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock(); Cooking.plugin=plugin;
    }

    @Test void inventoryAdditionMergesEquivalentFoodsAndReturnsUnstoredRemainders() {
        Player player=mock(Player.class); PlayerInventory inventory=mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(6); when(inventory.getMaxStackSize()).thenReturn(64);
        FoodItem bread=food("bread"); ItemStack full=stack(bread,Material.BREAD,64), partial=stack(bread,Material.BREAD,62);
        when(inventory.getItem(0)).thenReturn(new ItemStack(Material.STONE)); when(inventory.getItem(1)).thenReturn(full);
        when(inventory.getItem(2)).thenReturn(stack(food("other"),Material.BREAD,1)); when(inventory.getItem(4)).thenReturn(partial);
        ItemStack input=stack(bread,Material.BREAD,4); when(inventory.addItem(any(ItemStack.class))).thenAnswer(call->{
            ItemStack remaining=call.getArgument(0); assertEquals(2,remaining.getAmount()); return new HashMap<>(Map.of(0,remaining)); });
        assertEquals(2,InventoryAdder.addItem(player,input).getAmount()); assertEquals(64,partial.getAmount()); assertEquals(4,input.getAmount());
        partial.setAmount(60); assertNull(InventoryAdder.addItem(player,input)); assertEquals(64,partial.getAmount());
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        assertNull(InventoryAdder.addItem(player,input));
        ItemStack ordinary=new ItemStack(Material.STONE); assertNull(InventoryAdder.addItem(player,ordinary));
        when(inventory.addItem(ordinary)).thenReturn(new HashMap<>(Map.of(0,ordinary))); assertSame(ordinary,InventoryAdder.addItem(player,ordinary));
        assertNotNull(new InventoryAdder());
    }

    @Test void foodMergingRespectsTheItemsMaximumStackSize() {
        var player=server.addPlayer(); FoodItem milk=food("milk");
        player.getInventory().setItem(0,stack(milk,Material.POTION,1));
        assertNull(InventoryAdder.addItem(player,stack(milk,Material.POTION,1)));
        assertEquals(1,player.getInventory().getItem(0).getAmount()); assertEquals(1,player.getInventory().getItem(1).getAmount());
    }

    @Test void foodEqualityRejectsDifferentRecipesTagsOriginsQualityAndSauces() {
        FoodItem first=food("bread"), second=new FoodItem(first); assertTrue(InventoryAdder.equalsFood(first,second));
        second.setCategory("other"); assertFalse(InventoryAdder.equalsFood(first,second)); second.setCategory(first.getCategory());
        assertFalse(InventoryAdder.equalsFood(first,food("different")));
        second.setOrigin("Rye"); assertFalse(InventoryAdder.equalsFood(first,second)); first.setOrigin("RYE"); assertTrue(InventoryAdder.equalsFood(first,second));
        second.setOrigin("Wheat"); assertFalse(InventoryAdder.equalsFood(first,second)); second.setOrigin("rye");
        second.setQualityRange(5,5); assertFalse(InventoryAdder.equalsFood(first,second)); second.setQualityRange(2,2);
        first.addOrModifyTrack(track("freshness","fresh",false)); assertFalse(InventoryAdder.equalsFood(first,second));
        second.addOrModifyTrack(track("freshness","rotten",false)); assertFalse(InventoryAdder.equalsFood(first,second));
        second.addOrModifyTrack(track("freshness","fresh",false)); assertTrue(InventoryAdder.equalsFood(first,second));
        second.setSauce(food("sauce")); assertFalse(InventoryAdder.equalsFood(first,second));
        second.setSauce(null); first.setSauce(food("sauce")); assertFalse(InventoryAdder.equalsFood(first,second));
    }

    @Test void differentCarveProgressOrFoodBudgetsCannotMerge() {
        var player=server.addPlayer();
        FoodItem first=food("roast"), second=new FoodItem(first);
        first.setCarveState("roast",0,3); second.setCarveState("roast",1,2);
        player.getInventory().setItem(0,stack(first,Material.BEEF,1));
        assertNull(InventoryAdder.addItem(player,stack(second,Material.BEEF,1)));
        assertEquals(1,player.getInventory().getItem(0).getAmount()); assertEquals(1,player.getInventory().getItem(1).getAmount());
        assertEquals(3,FoodItem.fromItem(player.getInventory().getItem(0)).getCarveRemaining());
        assertEquals(2,FoodItem.fromItem(player.getInventory().getItem(1)).getCarveRemaining());
        player.getInventory().clear();
        FoodItem meal=food("meal"), other=new FoodItem(meal); meal.setBaseFood(10); other.setBaseFood(1);
        player.getInventory().setItem(0,stack(meal,Material.BREAD,1));
        assertNull(InventoryAdder.addItem(player,stack(other,Material.BREAD,1)));
        assertEquals(1,player.getInventory().getItem(0).getAmount()); assertEquals(1,player.getInventory().getItem(1).getAmount());
    }

    @Test void slotEncodingRoundTripsResourceLocationsAndSkipsMalformedEntries() {
        Furniture furniture=mock(Furniture.class); Map<String,PlacedSlot> slots=new LinkedHashMap<>(); when(furniture.getActiveSlots()).thenReturn(slots);
        var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);
        var component=mock(org.bukkit.inventory.meta.components.CustomModelDataComponent.class);
        when(meta.getCustomModelDataComponent()).thenReturn(component); when(component.getFloats()).thenReturn(List.of(23f));
        when(meta.hasItemModel()).thenReturn(true); when(meta.getItemModel()).thenReturn(new NamespacedKey("cooking","dish"));
        ItemStack model=mock(ItemStack.class); when(model.getType()).thenReturn(Material.PAPER); when(model.getItemMeta()).thenReturn(meta);
        PlacedSlot first=mock(PlacedSlot.class), empty=mock(PlacedSlot.class), air=mock(PlacedSlot.class);
        when(first.getCurrentItem()).thenReturn(model); when(air.getCurrentItem()).thenReturn(new ItemStack(Material.AIR));
        slots.put("first",first); slots.put("empty",empty); slots.put("air",air);
        String encoded=Encoder.getEncodedSlots(furniture); assertEquals("first.PAPER.23.cooking~dish:air.AIR.0",encoded);
        when(component.getFloats()).thenReturn(List.of()); assertTrue(Encoder.getEncodedSlots(furniture).startsWith("first.PAPER.0"));
        when(meta.hasItemModel()).thenReturn(false); assertTrue(Encoder.getEncodedSlots(furniture).startsWith("first.PAPER.0:"));
        try(var stacks=mockConstruction(ItemStack.class,(stack,context)->{
            Material material=(Material)context.arguments().getFirst(); when(stack.getType()).thenReturn(material);
            if(material.isAir()) return;
            var decodedMeta=mock(org.bukkit.inventory.meta.ItemMeta.class);
            var decodedComponent=mock(org.bukkit.inventory.meta.components.CustomModelDataComponent.class);
            when(decodedMeta.getCustomModelDataComponent()).thenReturn(decodedComponent);
            var itemModel=new java.util.concurrent.atomic.AtomicReference<NamespacedKey>();
            doAnswer(call->{ itemModel.set(call.getArgument(0)); return null; }).when(decodedMeta).setItemModel(any());
            when(decodedMeta.getItemModel()).thenAnswer(call->itemModel.get()); when(stack.getItemMeta()).thenReturn(decodedMeta);
        })) {
            Map<String,ItemStack> decoded=Encoder.decodeSlots(encoded);
            assertEquals(new NamespacedKey("cooking","dish"),decoded.get("first").getItemMeta().getItemModel()); assertEquals(Material.AIR,decoded.get("air").getType());
            verify(decoded.get("first").getItemMeta().getCustomModelDataComponent()).setFloats(List.of(23f));
            assertTrue(Encoder.decodeSlots(null).isEmpty()); assertTrue(Encoder.decodeSlots("").isEmpty());
            decoded=Encoder.decodeSlots(":bad:.PAPER.2:slot.NOT_A_MATERIAL.2:good.PAPER.bad.INVALID~KEY");
            assertEquals(1,decoded.size()); assertEquals(Material.PAPER,decoded.get("good").getType());
        }
        assertNotNull(new Encoder()); assertNotNull(new Keys());
        assertEquals("custom_model",Encoder.parseSlot("slot.PAPER.0.custom_model").itemModel());
        assertFalse(LegacyModelData.has(meta)); assertThrows(IllegalStateException.class,()->LegacyModelData.get(meta));
        LegacyModelData.set(meta,null); verify(meta).setCustomModelDataComponent(null);
        LegacyModelData.set(meta,7); verify(component).setFlags(List.of()); verify(component).setStrings(List.of()); verify(component).setColors(List.of());
    }

    @Test void qualityHelpersUseFoodAndSeedMetadataWithSafeDefaults() {
        assertEquals(1,QualityUtils.average((int[])null)); assertEquals(1,QualityUtils.average()); assertEquals(3,QualityUtils.average(2,3));
        assertEquals(5,QualityUtils.fromStack(null,10)); assertEquals(1,QualityUtils.fromStack(new ItemStack(Material.STONE),-1));
        assertEquals(2,QualityUtils.fromStack(stack(food("bread"),Material.BREAD,1),1));
        assertEquals(1,QualityUtils.fromSeedStack(null)); assertEquals(1,QualityUtils.fromSeedStack(new ItemStack(Material.AIR)));
        assertEquals(1,QualityUtils.fromSeedStack(new ItemStack(Material.WHEAT_SEEDS)));
        ItemStack seeds=new ItemStack(Material.WHEAT_SEEDS); var meta=seeds.getItemMeta(); meta.getPersistentDataContainer().set(Keys.QUALITY,PersistentDataType.INTEGER,4); seeds.setItemMeta(meta);
        assertEquals(4,QualityUtils.fromSeedStack(seeds)); assertEquals(2,QualityUtils.fromSeedStack(stack(food("seed"),Material.WHEAT_SEEDS,1)));
    }

    @Test void normalizationHandlesSaucesEmptyTracksAndItemsThatNeverAge() {
        StackNormalizer.normalize(null); assertFalse(StackNormalizer.needsNormalize(null));
        MemoryConfiguration fixed=new MemoryConfiguration(); fixed.set("update",false); FoodItem salt=new FoodItem("salt",fixed);
        StackNormalizer.normalize(salt); assertFalse(StackNormalizer.needsNormalize(salt));
        FoodItem main=food("main"), sauce=food("sauce"); main.setSauce(sauce);
        main.addOrModifyTrack(new TagTrack("empty",true,List.of())); main.addOrModifyTrack(track("cooked","cooked",false));
        sauce.addOrModifyTrack(track("freshness","fresh",true)); sauce.getTagTrack("freshness").forceSetValue(5);
        assertTrue(StackNormalizer.needsNormalize(main)); StackNormalizer.normalize(main); assertFalse(StackNormalizer.needsNormalize(main));
        assertEquals(0,sauce.getTagTrack("freshness").getValue());
        DoughRising.ensureUnrisen(null); DoughRising.ensureUnrisen(main);
        FoodItem dough=food("dough"); DoughRising.ensureUnrisen(dough); assertFalse(dough.hasTagTrack("rising"));
        TrackLoader.oList.add(track("rising","unrisen",true)); DoughRising.ensureUnrisen(dough); assertTrue(dough.hasTagTrack("rising"));
        dough.getTagTrack("rising").forceSetValue(10); DoughRising.ensureUnrisen(dough); assertEquals(10,dough.getTagTrack("rising").getValue());
        assertFalse(WarmthUtils.applyHot(main)); TrackLoader.oList.add(track("warmth","hot",true));
        assertFalse(WarmthUtils.applyHot(null)); assertTrue(WarmthUtils.applyHot(main));
        assertFalse(WarmthUtils.expireIfRoomTemp(null)); assertFalse(WarmthUtils.expireIfRoomTemp(sauce)); assertFalse(WarmthUtils.expireIfRoomTemp(main));
        main.getTagTrack("warmth").forceSetValue(20); assertTrue(WarmthUtils.expireIfRoomTemp(main));
        main.addOrModifyTrack(new TagTrack("warmth",true,List.of())); main.getTagTrack("warmth").forceSetValue(20); assertTrue(WarmthUtils.expireIfRoomTemp(main));
        assertTrue(WarmthUtils.isHeated(main,0)); assertFalse(WarmthUtils.isHeated(main,1)); assertFalse(WarmthUtils.isHeated(sauce,0));
    }

    @Test void displayHelpersPresentPositiveNegativeAndNeutralModifiers() {
        assertEquals("Fresh",DisplayUtils.getDisplayString("Fresh",1,1));
        assertTrue(DisplayUtils.getDisplayString("Fresh",.5,1.5,.1,2).contains("-50% Food"));
        assertTrue(DisplayUtils.getDisplayString("Fresh",1.5,.5,0,-1).contains("-1★"));
        assertTrue(DisplayUtils.getDisplayString("Fresh",1,1,.2).contains("Craft Quality +20%"));
        assertTrue(DisplayUtils.getDisplayString("Fresh",1,1,0,2).contains("+2★"));
        assertEquals("#000000",DisplayUtils.getMergedColour(List.of())); assertEquals("#abcdef",DisplayUtils.getMergedColour(List.of("#abcdef")));
        assertEquals("#7f7f7f",DisplayUtils.getMergedColour(List.of("000000","ffffff")));
        assertEquals("&f",DisplayUtils.getNameColour(null)); assertEquals("&f",DisplayUtils.getNameColour("#000000")); assertEquals("#ffffff",DisplayUtils.getNameColour("#ffffff"));
        assertEquals("§7No effect",DisplayUtils.getSauceStatString(0,0));
        assertEquals("§a+1.0 Food§7, §c-2.0 Nutrition",DisplayUtils.getSauceStatString(1,-2));
        assertEquals("§c-1.0 Food§7, §a+2.0 Nutrition",DisplayUtils.getSauceStatString(-1,2));
        assertEquals("§a+2.0 Nutrition",DisplayUtils.getSauceStatString(0,2)); assertNotNull(new DisplayUtils());
    }

    private FoodItem food(String id) { FoodItem item=new FoodItem(id,new MemoryConfiguration()); item.setCategory("food"); item.setQualityRange(2,2); FoodLoader.oList.add(item); return item; }
    private ItemStack stack(FoodItem food,Material material,int amount) {
        ItemStack stack=ItemBuilder.stamp(new ItemStack(material,amount),food,food.getId());
        net.tfminecraft.cooking.carve.CarvableRoastUtils.writeCarveState(stack,food); return stack;
    }
    private TagTrack track(String id,String step,boolean ageable) { return new TagTrack(id,ageable,List.of(new TagStep(step,step,0,1,1))); }
}
