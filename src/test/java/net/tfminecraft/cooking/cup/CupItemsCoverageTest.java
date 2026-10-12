package net.tfminecraft.cooking.cup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.testsupport.TestCharacters;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class CupItemsCoverageTest {
    ServerMock server;
    PlayerMock player;
    Cooking previousPlugin;
    List<FoodItem> foods;
    List<TagTrack> tracks;
    Map<String,String> conversions;
    String empty, water;
    ItemAPI api;
    MockedStatic<TLibs> tlibs;

    @BeforeEach void setup() {
        server=MockBukkit.mock(); player=server.addPlayer(); previousPlugin=Cooking.plugin;
        Cooking.plugin=mock(Cooking.class); when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods=new ArrayList<>(FoodLoader.oList); FoodLoader.oList.clear();
        tracks=new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
        conversions=new HashMap<>(ConversionLoader.conversions); ConversionLoader.conversions.clear();
        empty=ItemCache.emptyCup; water=ItemCache.cupOfWater;
        ItemCache.emptyCup="cup.empty"; ItemCache.cupOfWater="cup.water";
        api=mock(ItemAPI.class,RETURNS_DEEP_STUBS); tlibs=mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        TrackLoader.oList.add(new TagTrack("freshness",false,List.of(new TagStep("fresh","Fresh",0,1,1,0,0))));
        food("milk_bucket",Material.MILK_BUCKET); food("cup_of_milk",Material.POTION);
        QualityConfig.apply(3,3,null,null);
    }

    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        ConversionLoader.conversions.clear(); ConversionLoader.conversions.putAll(conversions);
        ItemCache.emptyCup=empty; ItemCache.cupOfWater=water;
        QualityConfig.apply(1,5,null,null); tlibs.close(); MockBukkit.unmock(); Cooking.plugin=previousPlugin;
    }

    @Test void cupsCloneConfiguredItemsAndPreserveMilkOriginFreshnessAndQuality() {
        ItemStack base=new ItemStack(Material.BOWL);
        when(api.getCreator().getItemFromPath("cup.empty")).thenReturn(base);
        when(api.getCreator().getItemFromPath("cup.water")).thenReturn(base);
        assertEquals(base,CupItems.emptyCup()); assertNotSame(base,CupItems.emptyCup());
        assertEquals(base,CupItems.cupOfWater()); assertNotSame(base,CupItems.cupOfWater());
        assertMilk(CupItems.cupOfMilk(player,4,12,"Goat"),4,12,"Goat");
        assertMilk(CupItems.cupOfMilk(player,2,8),2,8,"Cow");
        assertMilk(CupItems.cupOfMilk(player,6),3,6,"Cow");
        assertMilk(CupItems.cupOfMilk(player,7,"Goat"),3,7,"Goat");
        assertEquals("Cow",DairyOrigin.orCow(null)); assertEquals("Cow",DairyOrigin.orCow("  "));
    }

    @Test void unavailableCupTemplatesReturnNoOutput() {
        when(api.getCreator().getItemFromPath("cup.empty")).thenReturn(null);
        when(api.getCreator().getItemFromPath("cup.water")).thenReturn(null);
        FoodLoader.oList.removeIf(f->f.getId().equals("cup_of_milk"));
        assertNull(CupItems.cupOfMilk(player,2,0)); assertNull(CupItems.cupOfMilk(player,0));
        assertNull(CupItems.emptyCup()); assertNull(CupItems.cupOfWater());
    }

    @Test void snapshotsReadMetadataAndUseSafeDefaultsForOtherItems() {
        for(ItemStack item:new ItemStack[]{null,new ItemStack(Material.STONE),new ItemStack(Material.MILK_BUCKET)}) {
            assertEquals(1,MilkBucketSnapshot.readQuality(item));
            assertEquals(0,MilkBucketSnapshot.readDairyFreshness(item));
            assertEquals("Cow",MilkBucketSnapshot.readOrigin(item));
        }
        FoodItem milk=FoodLoader.getByString("milk_bucket"); milk.setOrigin("Goat");
        TagTrack freshness=new TagTrack(TrackLoader.getByString("freshness")); freshness.setValue(22); milk.addOrModifyTrack(freshness);
        ItemStack stack=ItemBuilder.buildSingleWithQuality(milk,4);
        assertEquals(4,MilkBucketSnapshot.readQuality(stack)); assertEquals(22,MilkBucketSnapshot.readDairyFreshness(stack));
        assertEquals("Goat",MilkBucketSnapshot.readOrigin(stack));
        freshness.forceSetValue(-5); milk.addOrModifyTrack(freshness); milk.setOrigin(null);
        assertEquals(0,MilkBucketSnapshot.readDairyFreshness(ItemBuilder.buildSingleWithQuality(milk,1)));
        assertEquals("Cow",MilkBucketSnapshot.readOrigin(ItemBuilder.buildSingleWithQuality(milk,1)));
        milk.getTagTracks().clear(); assertEquals(0,MilkBucketSnapshot.readDairyFreshness(ItemBuilder.buildSingleWithQuality(milk,1)));
    }

    @Test void conversionPreservesStackSizeAndUsesOverridesOrPickupQuality() {
        ConversionLoader.conversions.put("v.milk_bucket","ingredient(type=milk_bucket;origin=Goat;tags=freshness.4)");
        ItemStack plain=new ItemStack(Material.MILK_BUCKET,3);
        ItemStack converted=MilkBucketConverter.convert(player,plain,5);
        assertEquals(3,converted.getAmount()); assertEquals(5,MilkBucketSnapshot.readQuality(converted));
        assertEquals("Goat",MilkBucketSnapshot.readOrigin(converted)); assertEquals(4,MilkBucketSnapshot.readDairyFreshness(converted));
        assertNull(FoodItem.fromItem(plain)); assertSame(converted,MilkBucketConverter.convert(player,converted));
        assertSame(converted,MilkBucketConverter.convertIfNeeded(player,converted));
        assertEquals(3,MilkBucketSnapshot.readQuality(player,plain));
        assertEquals(4,MilkBucketSnapshot.readDairyFreshness(player,plain));
        assertEquals("Goat",MilkBucketSnapshot.readOrigin(player,plain));
        when(api.getChecker().checkItemWithPath(plain,"v.milk_bucket")).thenReturn(true);
        assertEquals(3,MilkBucketSnapshot.readQuality(MilkBucketConverter.convertIfNeeded(player,plain)));
    }

    @Test void unrelatedOrUnconfiguredItemsAreLeftIntact() {
        ItemStack stone=new ItemStack(Material.STONE), milk=new ItemStack(Material.MILK_BUCKET);
        assertNull(MilkBucketConverter.convertIfNeeded(player,null)); assertSame(stone,MilkBucketConverter.convertIfNeeded(player,stone));
        assertNull(MilkBucketConverter.convert(player,null)); assertSame(stone,MilkBucketConverter.convert(player,stone));
        assertSame(milk,MilkBucketConverter.convert(player,milk));
        ConversionLoader.conversions.put("v.milk_bucket","invalid"); assertSame(milk,MilkBucketConverter.convert(player,milk));
        ConversionLoader.conversions.put("v.milk_bucket","ingredient(type=missing)"); assertSame(milk,MilkBucketConverter.convert(player,milk));
    }

    @Test void bucketFillAppliesDeferredConversionOnlyToUnconvertedMilkStillHeld() {
        MilkBucketConverter listener=new MilkBucketConverter();
        PlayerBucketFillEvent event=mock(PlayerBucketFillEvent.class); when(event.getPlayer()).thenReturn(player);
        when(event.getBucket()).thenReturn(Material.WATER_BUCKET); listener.onBucketFill(event);
        server.getScheduler().performOneTick();
        when(event.getBucket()).thenReturn(Material.MILK_BUCKET); listener.onBucketFill(event);
        ItemStack plain=new ItemStack(Material.MILK_BUCKET); when(event.getItemStack()).thenReturn(plain);
        listener.onBucketFill(event); server.getScheduler().performOneTick();
        ConversionLoader.conversions.put("v.milk_bucket","ingredient(type=milk_bucket;origin=Cow)");
        player.getInventory().setItemInMainHand(new ItemStack(Material.MILK_BUCKET,2));
        listener.onBucketFill(event); server.getScheduler().performOneTick();
        assertEquals(2,player.getInventory().getItemInMainHand().getAmount());
        assertEquals(3,MilkBucketSnapshot.readQuality(player.getInventory().getItemInMainHand()));
        ItemStack already=player.getInventory().getItemInMainHand();
        listener.onBucketFill(event); server.getScheduler().performOneTick(); assertEquals(already,player.getInventory().getItemInMainHand());
        listener.onBucketFill(event); player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        server.getScheduler().performOneTick(); assertEquals(Material.STONE,player.getInventory().getItemInMainHand().getType());
        listener.onBucketFill(event); player.getInventory().setItemInMainHand(null); server.getScheduler().performOneTick();
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @ParameterizedTest
    @ValueSource(strings={"current", "switch-back", "pending", "provider-lost", "provider-arrived", "reconnect", "offline"})
    void deferredMilkConversionBelongsToCapturedSessionAndConnection(String change) {
        UUID originalCharacter=UUID.randomUUID();
        try (TestCharacters characters=new TestCharacters(MockBukkit.createMockPlugin())) {
            characters.activate(player,originalCharacter);
            if (change.equals("provider-arrived")) characters.close();
            PlayerBucketFillEvent event=milkFill();
            new MilkBucketConverter().onBucketFill(event);
            assertNull(FoodItem.fromItem(player.getInventory().getItemInMainHand()));

            switch (change) {
                case "switch-back" -> {
                    characters.activate(player,UUID.randomUUID());
                    characters.activate(player,originalCharacter);
                }
                case "pending" -> characters.set(player,null);
                case "provider-lost" -> characters.close();
                case "provider-arrived" -> characters.register();
                case "reconnect" -> {
                    // Keep the old object's online flag true to exercise connection identity.
                    server.getPlayerList().disconnectPlayer(player);
                    PlayerMock replacement=new PlayerMock(server,player.getName(),player.getUniqueId());
                    server.addPlayer(replacement);
                    replacement.getInventory().setItemInMainHand(new ItemStack(Material.MILK_BUCKET,2));
                    assertTrue(player.isOnline());
                    assertNotSame(player,server.getPlayer(player.getUniqueId()));
                }
                case "offline" -> player.disconnect();
                default -> { }
            }

            server.getScheduler().performOneTick();
            ItemStack held=player.getInventory().getItemInMainHand();
            assertEquals(2,held.getAmount());
            if (change.equals("current")) {
                assertNotNull(FoodItem.fromItem(held));
                assertEquals(3,MilkBucketSnapshot.readQuality(held));
            } else {
                assertEquals(new ItemStack(Material.MILK_BUCKET,2),held);
            }
            if (change.equals("reconnect")) {
                assertEquals(new ItemStack(Material.MILK_BUCKET,2),server.getPlayer(player.getUniqueId()).getInventory().getItemInMainHand());
            }
        }
    }

    @Test void pendingCharacterCannotBeginConversionEvenIfReadyBeforeCallback() {
        try (TestCharacters characters=new TestCharacters(MockBukkit.createMockPlugin())) {
            PlayerBucketFillEvent event=milkFill();
            new MilkBucketConverter().onBucketFill(event);
            verify(event,never()).getItemStack();
            characters.activate(player,UUID.randomUUID());
            server.getScheduler().performOneTick();
            assertEquals(new ItemStack(Material.MILK_BUCKET,2),player.getInventory().getItemInMainHand());
        }
    }

    @Test void sessionChangeDuringQualityResolutionPreventsQueuedWrite() {
        try (TestCharacters characters=new TestCharacters(MockBukkit.createMockPlugin());
             MockedStatic<OriginQualityResolver> quality=mockStatic(OriginQualityResolver.class)) {
            characters.activate(player,UUID.randomUUID());
            PlayerBucketFillEvent event=milkFill();
            quality.when(() -> OriginQualityResolver.resolve(eq(player),any(FoodItem.class))).thenAnswer(call -> {
                characters.activate(player,UUID.randomUUID());
                return 3;
            });
            new MilkBucketConverter().onBucketFill(event);
            quality.verify(() -> OriginQualityResolver.resolve(eq(player),any(FoodItem.class)));
            server.getScheduler().performOneTick();
            assertEquals(new ItemStack(Material.MILK_BUCKET,2),player.getInventory().getItemInMainHand());
        }
    }

    private PlayerBucketFillEvent milkFill() {
        ConversionLoader.conversions.put("v.milk_bucket","ingredient(type=milk_bucket;origin=Cow)");
        player.getInventory().setItemInMainHand(new ItemStack(Material.MILK_BUCKET,2));
        PlayerBucketFillEvent event=mock(PlayerBucketFillEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getBucket()).thenReturn(Material.MILK_BUCKET);
        when(event.getItemStack()).thenReturn(new ItemStack(Material.MILK_BUCKET));
        return event;
    }

    private FoodItem food(String id,Material material) {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("name",id);
        FoodItem food=new FoodItem(id,config); food.setCategory("ingredient"); food.setModel(new FoodModel(new ItemStack(material)));
        FoodLoader.oList.add(food); return food;
    }
    private void assertMilk(ItemStack stack,int quality,int freshness,String origin) {
        FoodItem food=FoodItem.fromItem(stack); assertNotNull(food); assertEquals("cup_of_milk",food.getId());
        assertEquals(quality,food.getQualityMin()); assertEquals(freshness,food.getTagTrack("freshness").getValue());
        assertEquals(origin,food.getOrigin());
    }
}
