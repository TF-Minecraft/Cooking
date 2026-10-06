package net.tfminecraft.cooking.nutrition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.MemberRemoval;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.DrinkConsumeListener;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.manager.PlateManager;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.lifecycle.CharacterActivatedEvent;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class NutritionListenersCoverageTest {
    private ServerMock server;
    private PlayerMock player;
    private RPCharacter character;
    private PlayerManager players;
    private Cooking previousPlugin;
    private MockedStatic<RPCharacters> rp;
    private MockedStatic<TLibs> tlibs;
    private ItemAPI api;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private List<Battle> battles;
    private String previousWater, previousMilk, previousEmpty, previousBowl;

    @BeforeEach void setup() {
        server=MockBukkit.mock(); previousPlugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getLogger()).thenReturn(mock(Logger.class));
        player=server.addPlayer(); character=new RPCharacter(player); character.setFoodValue(100);
        players=mock(PlayerManager.class); rp=mockStatic(RPCharacters.class);
        rp.when(()->RPCharacters.getActiveCharacter(player)).thenReturn(character);
        rp.when(RPCharacters::getPlayerManager).thenReturn(players);
        tlibs=mockStatic(TLibs.class); api=mock(ItemAPI.class,RETURNS_DEEP_STUBS);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        foods=new ArrayList<>(FoodLoader.oList); tracks=new ArrayList<>(TrackLoader.oList);
        battles=new ArrayList<>(BattleManager.get()); BattleManager.get().clear();
        FoodLoader.oList.clear(); TrackLoader.oList.clear();
        previousWater=ItemCache.cupOfWater; previousMilk=ItemCache.cupOfMilk; previousEmpty=ItemCache.emptyCup;
        previousBowl=FurnitureCache.bowl;
        ItemCache.cupOfWater="cup.water"; ItemCache.cupOfMilk="cup.milk"; ItemCache.emptyCup="cup.empty";
        FurnitureCache.bowl="bowl";
        when(api.getCreator().getItemFromPath("cup.empty")).thenReturn(new ItemStack(Material.BOWL));
        NutritionConfig.load(new YamlConfiguration()); NutritionLog.configure(false,false,null);
    }

    @AfterEach void cleanup() {
        SaturationGuard.stop(); NutritionDrainTask.stop();
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods); TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        ItemCache.cupOfWater=previousWater; ItemCache.cupOfMilk=previousMilk; ItemCache.emptyCup=previousEmpty;
        FurnitureCache.bowl=previousBowl;
        BattleManager.get().clear(); BattleManager.get().addAll(battles);
        rp.close(); tlibs.close(); MockBukkit.unmock(); Cooking.plugin=previousPlugin;
    }

    @Test void lifecycleEventsRestoreHudAndResetDeathFoodOutsideStartedBattles() {
        MockBukkit.createMockPlugin("RPCharacters");
        NutritionLifecycleListener listener=new NutritionLifecycleListener();
        listener.onJoin(new PlayerJoinEvent(player,"joined"));
        assertEquals(20,player.getFoodLevel()); server.getScheduler().performTicks(2);
        assertEquals(10,player.getFoodLevel());
        PlayerRespawnEvent respawn=mock(PlayerRespawnEvent.class); when(respawn.getPlayer()).thenReturn(player);
        character.setFoodValue(50); listener.onPlayerRespawn(respawn); assertEquals(5,player.getFoodLevel());
        listener.onCharacterActivated(new CharacterActivatedEvent(null,player.getUniqueId(),character,null));
        listener.onCharacterActivated(new CharacterActivatedEvent(player,player.getUniqueId(),null,null));
        character.setRawDietScore(20);
        listener.onCharacterActivated(new CharacterActivatedEvent(player,player.getUniqueId(),character,null));
        assertEquals(20,character.getDietScore()); assertNotNull(character.getLastDietTierId());
        PlayerDeathEvent death=mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);
        rp.when(()->RPCharacters.getActiveCharacter(player)).thenReturn(null); listener.onPlayerDeath(death);
        assertEquals(50,character.getFoodValue());
        rp.when(()->RPCharacters.getActiveCharacter(player)).thenReturn(character);
        listener.onPlayerDeath(death); assertEquals(80,character.getFoodValue());
        MockBukkit.createMockPlugin("SimpleFactions");
        Battle battle=mock(Battle.class); when(battle.hasStarted()).thenReturn(true);
        when(battle.getSideByMemberId(player.getUniqueId())).thenReturn(mock(BattleSide.class));
        BattleManager.addBattle(battle);
        character.setFoodValue(15); listener.onPlayerDeath(death); assertEquals(15,character.getFoodValue());
    }

    @Test void battleLookupRequiresAnEnabledStartedBattleAndToleratesUnavailableIntegrations() {
        assertFalse(BattleFoodGate.inStartedBattle(null)); assertFalse(BattleFoodGate.inStartedBattle(player));
        Plugin factions=MockBukkit.createMockPlugin("SimpleFactions");
        assertFalse(BattleFoodGate.inStartedBattle(player));
        Battle battle=mock(Battle.class);
        when(battle.getSideByMemberId(player.getUniqueId())).thenReturn(mock(BattleSide.class));
        BattleManager.addBattle(battle); assertFalse(BattleFoodGate.inStartedBattle(player));
        when(battle.hasStarted()).thenReturn(true); assertTrue(BattleFoodGate.inStartedBattle(player));
        doThrow(new NoClassDefFoundError("older dependency")).when(battle).getSideByMemberId(player.getUniqueId());
        assertFalse(BattleFoodGate.inStartedBattle(player));
        doThrow(new IllegalStateException("offline")).when(battle).getSideByMemberId(player.getUniqueId());
        assertFalse(BattleFoodGate.inStartedBattle(player));
        server.getPluginManager().disablePlugin(factions); assertFalse(BattleFoodGate.inStartedBattle(player));
    }

    @Test void saturationGuardsHandleJoinEventsAndOnePeriodicTask() {
        SaturationGuard guard=new SaturationGuard();
        player.setSaturation(5); guard.onJoin(new PlayerJoinEvent(player,"joined"));
        assertEquals(0,player.getSaturation());
        guard.onFoodLevelChange(new FoodLevelChangeEvent(mock(HumanEntity.class),4));
        player.setSaturation(4); guard.onFoodLevelChange(new FoodLevelChangeEvent(player,4));
        assertEquals(4,player.getSaturation()); server.getScheduler().performOneTick(); assertEquals(0,player.getSaturation());
        SaturationGuard.stop(); SaturationGuard.start(); SaturationGuard.start();
        player.setSaturation(5); server.getScheduler().performTicks(40); assertEquals(0,player.getSaturation());
        MockBukkit.createMockPlugin("RPCharacters");
        player.setSaturation(6); server.getScheduler().performTicks(40); assertEquals(0,player.getSaturation());
        server.getScheduler().performTicks(40);
        SaturationGuard.stop(); SaturationGuard.stop();
        player.setSaturation(5); server.getScheduler().performTicks(40); assertEquals(5,player.getSaturation());
    }

    @Test void foodLevelGuardDefersVanillaChangesButAllowsItsOwnSyncEvents() {
        FoodLevelChangeGuard guard=new FoodLevelChangeGuard();
        FoodLevelChangeEvent nonPlayer=new FoodLevelChangeEvent(mock(HumanEntity.class),1);
        guard.onFoodLevelChange(nonPlayer); assertFalse(nonPlayer.isCancelled());
        FoodLevelChangeEvent unavailable=new FoodLevelChangeEvent(player,1);
        guard.onFoodLevelChange(unavailable); assertTrue(unavailable.isCancelled()); server.getScheduler().performOneTick();
        MockBukkit.createMockPlugin("RPCharacters");
        FoodLevelChangeEvent event=new FoodLevelChangeEvent(player,1);
        guard.onFoodLevelChange(event); assertTrue(event.isCancelled());
        server.getScheduler().performOneTick(); assertEquals(10,player.getFoodLevel());
        PlayerMock duringSync=spy(player);
        doAnswer(call->{
            FoodLevelChangeEvent nested=new FoodLevelChangeEvent(duringSync,call.getArgument(0));
            guard.onFoodLevelChange(nested); assertFalse(nested.isCancelled()); return null;
        }).when(duringSync).setFoodLevel(anyInt());
        character.setFoodValue(40); NutritionDisplayService.sync(duringSync,character);
        assertFalse(NutritionDisplayService.isSyncing(duringSync));
    }

    @Test void naturalFoodRegenerationIsBlockedAndOtherHealingIsPreserved() {
        RegenBlocker listener=new RegenBlocker();
        EntityRegainHealthEvent other=new EntityRegainHealthEvent(mock(Entity.class),2,RegainReason.SATIATED);
        listener.onRegainHealth(other); assertFalse(other.isCancelled());
        EntityRegainHealthEvent satiated=new EntityRegainHealthEvent(player,2,RegainReason.SATIATED);
        listener.onRegainHealth(satiated); assertTrue(satiated.isCancelled());
        EntityRegainHealthEvent magic=new EntityRegainHealthEvent(player,2,RegainReason.MAGIC);
        listener.onRegainHealth(magic); assertFalse(magic.isCancelled());
        EntityRegainHealthEvent fast=new EntityRegainHealthEvent(player,2,RegainReason.MAGIC,true);
        listener.onRegainHealth(fast); assertTrue(fast.isCancelled());
        EntityRegainHealthEvent brokenBridge=mock(EntityRegainHealthEvent.class);
        when(brokenBridge.getEntity()).thenReturn(player); when(brokenBridge.getRegainReason()).thenReturn(RegainReason.MAGIC);
        when(brokenBridge.isFastRegen()).thenThrow(new IllegalStateException("bridge failed"));
        assertDoesNotThrow(()->listener.onRegainHealth(brokenBridge));
        verify(brokenBridge,never()).setCancelled(true);
    }

    @Test void regenerationCompatibilityWorksWhenTheServerApiHasNoFastRegenMethod() throws Exception {
        // Exercise the older API contract in its own class loader, without changing live static fields.
        byte[] legacyEvent=new ByteBuddy().redefine(EntityRegainHealthEvent.class)
                .visit(new MemberRemoval().stripMethods(named("isFastRegen"))).make().getBytes();
        Map<String,byte[]> classes=Map.of(EntityRegainHealthEvent.class.getName(),legacyEvent,
                RegenBlocker.class.getName(),ClassFileLocator.ForClassLoader.read(RegenBlocker.class));
        ClassLoader loader=new ByteArrayClassLoader.ChildFirst(getClass().getClassLoader(),classes,
                RegenBlocker.class.getProtectionDomain(),ByteArrayClassLoader.PersistenceHandler.LATENT,
                PackageDefinitionStrategy.Trivial.INSTANCE);
        Class<?> eventType=loader.loadClass(EntityRegainHealthEvent.class.getName());
        Class<?> listenerType=loader.loadClass(RegenBlocker.class.getName());
        Object event=eventType.getConstructor(Entity.class,double.class,RegainReason.class).newInstance(player,2,RegainReason.MAGIC);
        listenerType.getMethod("onRegainHealth",eventType).invoke(listenerType.getConstructor().newInstance(),event);
        assertEquals(false,eventType.getMethod("isCancelled").invoke(event));
    }

    @Test void foodConsumptionRejectsRawFoodAndLeavesDedicatedDrinkHandlingAlone() {
        MockBukkit.createMockPlugin("RPCharacters");
        FoodConsumeListener listener=new FoodConsumeListener();
        PlayerItemConsumeEvent absent=mock(PlayerItemConsumeEvent.class); listener.onConsume(absent);
        ItemStack water=new ItemStack(Material.POTION);
        when(api.getChecker().checkItemWithPath(water,"cup.water")).thenReturn(true);
        for(ItemStack stack:List.of(water,new ItemStack(Material.MILK_BUCKET),new ItemStack(Material.APPLE),stack("cup_of_milk",true,"drink",Material.POTION))) {
            PlayerItemConsumeEvent event=consume(stack,EquipmentSlot.HAND); listener.onConsume(event); assertFalse(event.isCancelled());
        }
        assertEquals(100,character.getFoodValue());
        PlayerItemConsumeEvent raw=consume(stack("raw",false,"meat",Material.BEEF),EquipmentSlot.HAND);
        listener.onConsume(raw); assertTrue(raw.isCancelled()); assertTrue(player.nextMessage().contains("prepared"));
        PlayerItemConsumeEvent cooked=consume(stack("cooked",true,"meat",Material.COOKED_BEEF),EquipmentSlot.HAND);
        listener.onConsume(cooked); assertFalse(cooked.isCancelled()); assertEquals(110,character.getFoodValue());
    }

    @Test void bowlEatingRequiresAnEmptyHandAndConsumesOnlyEdibleSoup() {
        MockBukkit.createMockPlugin("RPCharacters");
        Furniture bowl=mock(Furniture.class); when(bowl.getId()).thenReturn("bowl"); when(bowl.getLoc()).thenReturn(player.getLocation());
        Map<String,PlacedSlot> slots=new HashMap<>(); when(bowl.getActiveSlots()).thenReturn(slots);
        when(bowl.hasActiveSlot(anyString())).thenAnswer(call->slots.containsKey(call.getArgument(0)));
        PlateManager plates=mock(PlateManager.class); BowlEatHandler listener=new BowlEatHandler(plates);
        FurnitureInteractEvent event=mock(FurnitureInteractEvent.class); when(event.getFurniture()).thenReturn(bowl); when(event.getPlayer()).thenReturn(player);
        when(bowl.getId()).thenReturn("other"); listener.onInteract(event); when(bowl.getId()).thenReturn("bowl");
        player.setSneaking(true); listener.onInteract(event); player.setSneaking(false);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK)); listener.onInteract(event);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR)); listener.onInteract(event);
        slots.put("food_item",null); listener.onInteract(event);
        PlacedSlot slot=mock(PlacedSlot.class); slots.put("food_item",slot); listener.onInteract(event);
        when(slot.getCurrentItem()).thenReturn(new ItemStack(Material.APPLE)); listener.onInteract(event);
        when(slot.getCurrentItem()).thenReturn(stack("grain",true,"grain",Material.BREAD)); listener.onInteract(event);
        verifyNoInteractions(plates); assertEquals(100,character.getFoodValue());
        try(MockedStatic<InteractibleFurniture> furniture=mockStatic(InteractibleFurniture.class)) {
            InteractibleFurniture plugin=mock(InteractibleFurniture.class,RETURNS_DEEP_STUBS);
            furniture.when(InteractibleFurniture::getInstance).thenReturn(plugin);
            when(slot.getCurrentItem()).thenReturn(stack("soup",true,"soup",Material.MUSHROOM_STEW));
            listener.onInteract(event); verify(event).setCancelled(true); verify(plates).clear(bowl);
            verify(plugin.getFurnitureManager()).markDirty(bowl); assertEquals(110,character.getFoodValue());
            clearInvocations(plates,plugin.getFurnitureManager());
            when(slot.getCurrentItem()).thenReturn(stack("raw_soup",false,"soup",Material.MUSHROOM_STEW));
            listener.onInteract(event);
            verifyNoInteractions(plates,plugin.getFurnitureManager()); assertEquals(110,character.getFoodValue());
        }
    }

    @Test void milkBucketsUseTheActualHandAndReturnExactlyOneBucketOutsideCreative() {
        MockBukkit.createMockPlugin("RPCharacters"); DrinkConsumeListener listener=new DrinkConsumeListener();
        PlayerItemConsumeEvent absent=mock(PlayerItemConsumeEvent.class); listener.onMilkBucket(absent);
        listener.onMilkBucket(consume(new ItemStack(Material.APPLE),EquipmentSlot.HAND));
        ItemStack milk=stack("milk_bucket",true,"drink",Material.MILK_BUCKET);
        player.getInventory().setItemInMainHand(milk);
        PlayerItemConsumeEvent event=consume(milk,EquipmentSlot.HAND); listener.onMilkBucket(event);
        assertTrue(event.isCancelled()); assertEquals(110,character.getFoodValue());
        assertEquals(Material.BUCKET,player.getInventory().getItemInMainHand().getType());
        player.setGameMode(GameMode.CREATIVE); player.getInventory().setItemInOffHand(new ItemStack(Material.MILK_BUCKET));
        listener.onMilkBucket(consume(new ItemStack(Material.MILK_BUCKET),EquipmentSlot.OFF_HAND));
        assertEquals(Material.MILK_BUCKET,player.getInventory().getItemInOffHand().getType()); player.setGameMode(GameMode.SURVIVAL);
        listener.onMilkBucket(consume(new ItemStack(Material.MILK_BUCKET),EquipmentSlot.HAND));
        assertEquals(Material.BUCKET,player.getInventory().getItemInOffHand().getType());
        player.getInventory().setItemInMainHand(new ItemStack(Material.MILK_BUCKET,2));
        listener.onMilkBucket(consume(new ItemStack(Material.MILK_BUCKET),EquipmentSlot.OFF_HAND));
        assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
        player.getInventory().clear(); listener.onMilkBucket(consume(new ItemStack(Material.MILK_BUCKET),EquipmentSlot.HAND));
        assertEquals(0,player.getInventory().all(Material.BUCKET).size());
        for(int i=0;i<player.getInventory().getSize();i++) player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        player.getInventory().setItemInOffHand(new ItemStack(Material.MILK_BUCKET,2));
        long before=player.getWorld().getEntities().stream().filter(e->e instanceof org.bukkit.entity.Item).count();
        listener.onMilkBucket(consume(new ItemStack(Material.MILK_BUCKET),EquipmentSlot.OFF_HAND));
        assertEquals(1,player.getInventory().getItemInOffHand().getAmount());
        assertEquals(before+1,player.getWorld().getEntities().stream().filter(e->e instanceof org.bukkit.entity.Item).count());
    }

    @Test void drinkReplacementUsesTheOriginalSlotAfterVanillaLeavesABottle() {
        MockBukkit.createMockPlugin("RPCharacters"); DrinkConsumeListener listener=new DrinkConsumeListener();
        listener.onDrink(mock(PlayerItemConsumeEvent.class));
        listener.onDrink(consume(new ItemStack(Material.APPLE),EquipmentSlot.HAND));
        ItemStack water=new ItemStack(Material.POTION);
        when(api.getChecker().checkItemWithPath(water,"cup.water")).thenReturn(true);
        player.getInventory().setHeldItemSlot(3); listener.onDrink(consume(water,EquipmentSlot.HAND));
        player.getInventory().setItem(3,new ItemStack(Material.GLASS_BOTTLE)); player.getInventory().setHeldItemSlot(4);
        server.getScheduler().performOneTick(); assertEquals(Material.BOWL,player.getInventory().getItem(3).getType());
        ItemStack milk=stack("cup_of_milk",true,"drink",Material.POTION);
        listener.onDrink(consume(milk,EquipmentSlot.OFF_HAND)); player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick(); assertEquals(Material.BOWL,player.getInventory().getItemInOffHand().getType());
        assertEquals(110,character.getFoodValue());
        ItemStack untrackedMilk=new ItemStack(Material.HONEY_BOTTLE);
        when(api.getChecker().checkItemWithPath(untrackedMilk,"cup.milk")).thenReturn(true);
        listener.onDrink(consume(untrackedMilk,EquipmentSlot.HAND)); server.getScheduler().performOneTick();
        water.setAmount(2); listener.onDrink(consume(water,EquipmentSlot.HAND));
        player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick(); assertEquals(Material.GLASS_BOTTLE,player.getInventory().getItemInMainHand().getType());
    }

    private ItemStack stack(String id,boolean edible,String category,Material material) {
        YamlConfiguration config=new YamlConfiguration(); config.set("food",10); config.set("edible",edible);
        FoodItem food=new FoodItem(id,config); food.setCategory(category); food.setQualityRange(1,1); FoodLoader.oList.add(food);
        ItemStack stack=new ItemStack(material); var meta=stack.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.FOOD_ID,PersistentDataType.STRING,id);
        stack.setItemMeta(meta); return stack;
    }
    private PlayerItemConsumeEvent consume(ItemStack stack,EquipmentSlot hand) {
        return new PlayerItemConsumeEvent(player,stack,hand);
    }
}
