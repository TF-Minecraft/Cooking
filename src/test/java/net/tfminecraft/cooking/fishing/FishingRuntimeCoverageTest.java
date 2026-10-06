package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.event.FishingLootSpawnEvent;
import net.momirealms.customfishing.api.mechanic.context.Context;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import net.momirealms.customfishing.api.mechanic.item.ItemManager;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.PermissionEffectsConfig;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.util.LegacyModelData;
import net.tfminecraft.cooking.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class FishingRuntimeCoverageTest {
    Workshop env;
    PluginManager plugins;
    Plugin customFishing;
    MockedStatic<Bukkit> bukkit;
    boolean oldRegistered;
    CatchMapping tuna = new CatchMapping("tuna", "Tuna", "fish");

    @BeforeEach void setup() throws Exception {
        var field=CustomFishingBridge.class.getDeclaredField("registered"); field.setAccessible(true);
        oldRegistered=field.getBoolean(null); field.setBoolean(null,false);
        env=new Workshop(); plugins=mock(PluginManager.class); customFishing=mock(Plugin.class);
        when(customFishing.getName()).thenReturn("CustomFishing"); when(customFishing.isEnabled()).thenReturn(true);
        when(plugins.getPlugin("CustomFishing")).thenReturn(customFishing);
        Server server=mock(Server.class); when(server.getPluginManager()).thenReturn(plugins);
        when(Cooking.plugin.getServer()).thenReturn(server); when(Cooking.plugin.getLogger()).thenReturn(Logger.getLogger("fishing-test"));
        bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS); bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        CustomFishingCatalog.replace(Map.of("tuna",tuna),Map.of("rod",new RodBand(3,3)),new RodBand(2,2),Map.of(),Map.of(),Map.of(17,"tuna"),45);
        QualityConfig.apply(1,5,null,null); PermissionEffectsConfig.apply(List.of(),List.of());
        env.originQuality.when(()->net.tfminecraft.cooking.quality.OriginQualityResolver.applyPickupPermissions(any(),anyInt()))
                .thenAnswer(inv->inv.getArgument(1));
        env.templates.put(CustomFishingCatalog.WHOLE_TYPE,env.food(CustomFishingCatalog.WHOLE_TYPE,null,1));
        env.builder.when(()->ItemBuilder.buildSingleWithQuality(any(FoodItem.class),any(ItemStack.class),anyInt()))
                .thenAnswer(inv->env.stack(inv.getArgument(0),Material.COD,1));
    }
    @AfterEach void cleanup() throws Exception {
        bukkit.close(); env.close();
        var field=CustomFishingBridge.class.getDeclaredField("registered"); field.setAccessible(true); field.setBoolean(null,oldRegistered);
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @Test void optionalIntegrationRegistersOnceAndCanRetryAfterRegistrationFailure() {
        CustomFishingBridge bridge=new CustomFishingBridge();
        CustomFishingBridge.tryRegister(null);
        when(plugins.getPlugin("CustomFishing")).thenReturn(null); CustomFishingBridge.tryRegister(Cooking.plugin);
        when(plugins.getPlugin("CustomFishing")).thenReturn(customFishing); when(customFishing.isEnabled()).thenReturn(false);
        CustomFishingBridge.tryRegister(Cooking.plugin); when(customFishing.isEnabled()).thenReturn(true);
        bridge.onPluginEnable(new PluginEnableEvent(mock(Plugin.class)));
        doThrow(new IllegalStateException("registration unavailable")).doNothing().when(plugins).registerEvents(any(),eq(Cooking.plugin));
        CustomFishingBridge.tryRegister(Cooking.plugin);
        bridge.onPluginEnable(new PluginEnableEvent(customFishing));
        CustomFishingBridge.tryRegister(Cooking.plugin);
        verify(plugins,times(2)).registerEvents(isA(CustomFishingCatchListener.class),eq(Cooking.plugin));
    }

    @Test void optionalItemFactsHandleUnavailableAndFailingProviders() {
        ItemStack stack=new ItemStack(Material.COD);
        assertNull(CustomFishingItemFacts.lootId(null)); assertNull(CustomFishingItemFacts.size(null));
        when(plugins.getPlugin("CustomFishing")).thenReturn(null); assertNull(CustomFishingItemFacts.lootId(stack));
        when(plugins.getPlugin("CustomFishing")).thenReturn(customFishing); when(customFishing.isEnabled()).thenReturn(false);
        assertNull(CustomFishingItemFacts.size(stack)); when(customFishing.isEnabled()).thenReturn(true);
        try(var api=mockStatic(BukkitCustomFishingPlugin.class)) {
            assertNull(CustomFishingItemFacts.lootId(stack));
            BukkitCustomFishingPlugin instance=mock(BukkitCustomFishingPlugin.class); ItemManager items=mock(ItemManager.class);
            api.when(BukkitCustomFishingPlugin::getInstance).thenReturn(instance); when(instance.getItemManager()).thenReturn(items);
            when(items.getFishSize(stack)).thenReturn(null);
            assertNull(CustomFishingItemFacts.lootId(stack)); assertNull(CustomFishingItemFacts.size(stack));
            when(items.getCustomFishingItemID(stack)).thenReturn(" ","tuna"); when(items.getFishSize(stack)).thenReturn(23.5f);
            assertNull(CustomFishingItemFacts.lootId(stack)); assertEquals("tuna",CustomFishingItemFacts.lootId(stack));
            assertEquals(23.5,CustomFishingItemFacts.size(stack));
            when(items.getFishSize(stack)).thenThrow(new IllegalArgumentException("invalid metadata"));
            assertNull(CustomFishingItemFacts.size(stack));
            api.when(BukkitCustomFishingPlugin::getInstance).thenThrow(new IllegalStateException("not initialized"));
            assertNull(CustomFishingItemFacts.lootId(stack));
        }
    }

    @Test void catchesKeepUnknownAndMalformedLootAndConvertValidFishWithTheirSize() {
        CustomFishingCatchListener.register(Cooking.plugin);
        ArgumentCaptor<Listener> captor=ArgumentCaptor.forClass(Listener.class); verify(plugins).registerEvents(captor.capture(),eq(Cooking.plugin));
        CustomFishingCatchListener listener=(CustomFishingCatchListener)captor.getValue();
        listener.onLoot(null); listener.onLoot(mock(FishingLootSpawnEvent.class));
        FishingLootSpawnEvent event=mock(FishingLootSpawnEvent.class); Context<Player> context=mock(Context.class);
        when(event.getContext()).thenReturn(context); when(event.getPlayer()).thenReturn(env.player);
        listener.onLoot(event);
        Loot loot=mock(Loot.class); when(loot.id()).thenReturn("tuna"); when(event.getLoot()).thenReturn(loot);
        Item entity=mock(Item.class); ItemStack caught=new ItemStack(Material.COD); when(entity.getItemStack()).thenReturn(caught); when(event.getEntity()).thenReturn(entity);
        listener.onLoot(event); verify(entity,never()).setItemStack(any());
        when(context.arg(ContextKeys.SIZE)).thenReturn(23.5f); when(context.arg(ContextKeys.ROD)).thenReturn("rod");
        env.templates.clear(); listener.onLoot(event); verify(entity,never()).setItemStack(any());
        env.templates.put(CustomFishingCatalog.WHOLE_TYPE,env.food(CustomFishingCatalog.WHOLE_TYPE,null,1));
        listener.onLoot(event);
        ArgumentCaptor<ItemStack> output=ArgumentCaptor.forClass(ItemStack.class); verify(entity).setItemStack(output.capture());
        FoodItem fish=env.resolve(output.getValue()); assertEquals("Tuna",fish.getOrigin()); assertEquals(24,fish.getCatchSizeCm());
        assertEquals("tuna",fish.getCustomFishingId()); assertEquals(3,fish.getQualityMin());
        assertEquals(1,output.getValue().getAmount());
    }

    @Test void legacyConversionPreservesStackCountAndOnlyRewritesRecognizedFish() {
        when(plugins.getPlugin("CustomFishing")).thenReturn(null);
        try(var models=mockStatic(LegacyModelData.class)) {
        ItemStack noMeta=mock(ItemStack.class); when(noMeta.getType()).thenReturn(Material.COD);
        assertNull(LegacyFishConversion.convert(null,noMeta));
        assertNull(LegacyFishConversion.convert(null,null));
        assertNull(LegacyFishConversion.convert(null,env.stack(env.food("already","Tuna",2),Material.COD,2)));
        assertNull(LegacyFishConversion.convert(null,new ItemStack(Material.COD)));
        ItemStack external=mock(ItemStack.class); when(external.getType()).thenReturn(Material.COD); when(external.hasItemMeta()).thenReturn(true);
        assertNull(LegacyFishConversion.convert(null,external));
        ItemMeta meta=mock(ItemMeta.class); when(external.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(new ItemStack(Material.COD).getItemMeta().getPersistentDataContainer());
            assertNull(LegacyFishConversion.convert(null,external));
            models.when(()->LegacyModelData.has(meta)).thenReturn(true); models.when(()->LegacyModelData.get(meta)).thenReturn(17);
            when(external.getAmount()).thenReturn(7);
            ItemStack converted=LegacyFishConversion.convert(env.player,external);
            assertEquals(7,converted.getAmount()); assertEquals(45,env.resolve(converted).getCatchSizeCm());
            LegacyFishScan scan=new LegacyFishScan(); assertFalse(scan.matches(null)); assertFalse(scan.matches(new ItemStack(Material.SALMON)));
            assertTrue(scan.matches(external)); assertFalse(scan.matches(converted));
            scan.update(env.player,null,0,external); scan.update(env.player,env.player.getInventory(),-1,external);
            scan.update(env.player,env.player.getInventory(),4,external); assertEquals(7,env.player.getInventory().getItem(4).getAmount());
            scan.update(env.player,env.player.getInventory(),5,new ItemStack(Material.COD)); assertNull(env.player.getInventory().getItem(5));
            env.templates.clear(); assertNull(LegacyFishConversion.convert(env.player,external));
        }
        assertFalse(LegacyFishAdapter.decide(false,"COD",null,null,null).replaces());
    }

    @Test void wholeFishBuildersHandleMissingTemplatesAndDescribeVanillaFish() {
        ItemStack caught=new ItemStack(Material.COD);
        FoodItem described=SeafoodWholeItems.describe(env.templates.get(CustomFishingCatalog.WHOLE_TYPE),tuna,25,4);
        assertEquals("tuna",described.getCustomFishingId()); assertEquals(25,described.getCatchSizeCm());
        assertNull(SeafoodWholeItems.build(null,tuna,20,3)); assertNull(SeafoodWholeItems.build(caught,(CatchMapping)null,20,3));
        assertNull(SeafoodWholeItems.build(null,new VanillaFish("COD","Cod","fish",30),3));
        assertNull(SeafoodWholeItems.build(caught,(VanillaFish)null,3));
        FoodItem fish=env.resolve(SeafoodWholeItems.build(caught,new VanillaFish("COD","Cod","fish",30),4));
        assertEquals("Cod",fish.getOrigin()); assertEquals("seafood",fish.getCategory()); assertEquals(30,fish.getCatchSizeCm());
        assertNull(fish.getCustomFishingId()); assertEquals("fish",fish.getSeafoodCutType());
        env.templates.clear(); assertNull(SeafoodWholeItems.build(caught,tuna,20,3));
        assertNull(SeafoodWholeItems.build(caught,new VanillaFish("COD","Cod","fish",30),3));
    }
}
