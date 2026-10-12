package net.tfminecraft.cooking.nutrition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.tfminecraft.cooking.testsupport.TestCharacters;
import net.tfminecraft.cooking.utils.PlayerActionContext;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.PermadeathService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private TestCharacters characters;

    @BeforeEach void setup() {
        server=MockBukkit.mock(); previousPlugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getLogger()).thenReturn(mock(Logger.class));
        player=spy(new PlayerMock(server,"Eater")); server.addPlayer(player);
        character=new RPCharacter(player); character.setFoodValue(100);
        characters=new TestCharacters(MockBukkit.createMockPlugin("CharacterProvider"));
        characters.activate(player,character.getId());
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
        characters.close(); rp.close(); tlibs.close(); MockBukkit.unmock(); Cooking.plugin=previousPlugin;
    }

    @Test void lifecycleEventsRestoreHudAndResetDeathFoodOutsideStartedBattles() {
        MockBukkit.createMockPlugin("RPCharacters");
        NutritionLifecycleListener listener=new NutritionLifecycleListener();
        NutritionLifecycleListener.initializeCurrent(player);
        assertEquals(10,player.getFoodLevel());
        PlayerRespawnEvent respawn=mock(PlayerRespawnEvent.class); when(respawn.getPlayer()).thenReturn(player);
        character.setFoodValue(50); listener.onPlayerRespawn(respawn); assertEquals(5,player.getFoodLevel());
        character.setRawDietScore(20);
        listener.onCharacterStart(new CharacterSessionStartEvent(player,characters.activate(player,character.getId())));
        assertEquals(20,character.getDietScore()); assertNotNull(character.getLastDietTierId());
        PlayerDeathEvent death=mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);
        rp.when(()->RPCharacters.getActiveCharacter(player)).thenReturn(null); die(listener,death);
        assertEquals(50,character.getFoodValue());
        rp.when(()->RPCharacters.getActiveCharacter(player)).thenReturn(character);
        die(listener,death); assertEquals(80,character.getFoodValue());
        MockBukkit.createMockPlugin("SimpleFactions");
        Battle battle=mock(Battle.class); when(battle.hasStarted()).thenReturn(true);
        when(battle.getSideByMemberId(player.getUniqueId())).thenReturn(mock(BattleSide.class));
        BattleManager.addBattle(battle);
        character.setFoodValue(15); die(listener,death); assertEquals(15,character.getFoodValue());
    }

    @Test void battleLookupRequiresAnEnabledStartedBattleAndToleratesUnavailableIntegrations() {
        UUID playerId = player.getUniqueId();
        assertFalse(BattleFoodGate.inStartedBattle(null)); assertFalse(BattleFoodGate.inStartedBattle(player));
        Plugin factions=MockBukkit.createMockPlugin("SimpleFactions");
        assertFalse(BattleFoodGate.inStartedBattle(player));
        Battle battle=mock(Battle.class);
        when(battle.getSideByMemberId(playerId)).thenReturn(mock(BattleSide.class));
        BattleManager.addBattle(battle); assertFalse(BattleFoodGate.inStartedBattle(player));
        when(battle.hasStarted()).thenReturn(true); assertTrue(BattleFoodGate.inStartedBattle(player));
        doThrow(new NoClassDefFoundError("older dependency")).when(battle).getSideByMemberId(playerId);
        assertFalse(BattleFoodGate.inStartedBattle(player));
        doThrow(new IllegalStateException("offline")).when(battle).getSideByMemberId(playerId);
        assertFalse(BattleFoodGate.inStartedBattle(player));
        server.getPluginManager().disablePlugin(factions); assertFalse(BattleFoodGate.inStartedBattle(player));
    }

    @Test void saturationGuardsHandleFoodEventsAndOnePeriodicTask() {
        SaturationGuard guard=new SaturationGuard();
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
        PlayerMock duringSync=player;
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
        ItemStack cookedStack=stack("cooked",true,"meat",Material.COOKED_BEEF); cookedStack.setAmount(2);
        player.getInventory().setItemInMainHand(cookedStack);
        PlayerItemConsumeEvent cooked=consume(cookedStack,EquipmentSlot.HAND);
        listener.onConsume(cooked); assertFalse(cooked.isCancelled());
        assertEquals(100,character.getFoodValue()); verifyNoInteractions(players);
        assertEquals(2,player.getInventory().getItemInMainHand().getAmount());
        spendOne(EquipmentSlot.HAND); // Vanilla commits consumption after the event returns.
        server.getScheduler().performOneTick();
        assertEquals(110,character.getFoodValue());
        assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
        verify(players).savePlayer(player);
    }

    @Test void bowlEatingRequiresAnEmptyHandAndConsumesOnlyEdibleSoup() {
        MockBukkit.createMockPlugin("RPCharacters");
        var bowlLocation=player.getLocation();
        Furniture bowl=mock(Furniture.class); when(bowl.getId()).thenReturn("bowl"); when(bowl.getLoc()).thenReturn(bowlLocation);
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
        player.getInventory().setItemInOffHand(milk);
        listener.onDrink(consume(milk,EquipmentSlot.OFF_HAND));
        assertEquals(100,character.getFoodValue()); verifyNoInteractions(players);
        player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick(); assertEquals(Material.BOWL,player.getInventory().getItemInOffHand().getType());
        assertEquals(110,character.getFoodValue());
        ItemStack untrackedMilk=new ItemStack(Material.HONEY_BOTTLE);
        when(api.getChecker().checkItemWithPath(untrackedMilk,"cup.milk")).thenReturn(true);
        listener.onDrink(consume(untrackedMilk,EquipmentSlot.HAND)); server.getScheduler().performOneTick();
        water.setAmount(2); listener.onDrink(consume(water,EquipmentSlot.HAND));
        player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick(); assertEquals(Material.GLASS_BOTTLE,player.getInventory().getItemInMainHand().getType());
    }

    @Test void staleStartCannotInitializeAReplacementGenerationOfTheSameCharacter() {
        MockBukkit.createMockPlugin("RPCharacters");
        NutritionLifecycleListener listener = new NutritionLifecycleListener();
        CharacterSession original = characters.activate(player, character.getId());
        characters.activate(player, UUID.randomUUID());
        CharacterSession replacement = characters.activate(player, character.getId());
        character.setRawDietScore(40); player.setFoodLevel(17); player.setSaturation(6);

        listener.onCharacterStart(new CharacterSessionStartEvent(player, original));
        assertEquals(17, player.getFoodLevel()); assertEquals(6, player.getSaturation());
        assertNull(character.getLastDietTierId()); verifyNoInteractions(players);
        characters.set(player, null);
        listener.onCharacterStart(new CharacterSessionStartEvent(player, replacement));
        assertEquals(17, player.getFoodLevel()); verifyNoInteractions(players);
        characters.set(player, replacement);
        listener.onCharacterStart(new CharacterSessionStartEvent(player, replacement));
        assertEquals(10, player.getFoodLevel()); assertEquals(0, player.getSaturation());
        assertNotNull(character.getLastDietTierId()); verify(players).savePlayer(player);
    }

    @Test void deathAndRespawnWaitForSharedReadinessDespiteLoadedRpcData() {
        MockBukkit.createMockPlugin("RPCharacters");
        characters.set(player, null);
        character.setFoodValue(15); player.setFoodLevel(17); player.setSaturation(6);
        NutritionLifecycleListener listener = new NutritionLifecycleListener();
        PlayerDeathEvent death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class); when(respawn.getPlayer()).thenReturn(player);

        die(listener, death); listener.onPlayerRespawn(respawn);

        assertEquals(15, character.getFoodValue()); assertEquals(17, player.getFoodLevel());
        assertEquals(6, player.getSaturation()); verifyNoInteractions(players);
    }

    @Test void deathBattleLookupReentryCannotResetOrSaveTheReplacementSession() {
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(15);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);
        try (MockedStatic<BattleFoodGate> gate = mockStatic(BattleFoodGate.class)) {
            gate.when(() -> BattleFoodGate.inStartedBattle(player)).thenAnswer(call -> {
                characters.activate(player, UUID.randomUUID());
                characters.activate(player, character.getId());
                return false;
            });
            die(new NutritionLifecycleListener(), death);
        }
        assertEquals(15, character.getFoodValue()); verifyNoInteractions(players);
    }

    @Test void permakillResetsNeitherTheDeadCharacterNorItsReplacement() {
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(15);
        RPCharacter replacement = new RPCharacter(player); replacement.setFoodValue(90);
        NutritionLifecycleListener listener = new NutritionLifecycleListener();
        PlayerDeathEvent death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);

        listener.captureDeath(death);
        // RPCharacters' permakill switches to the replacement at MONITOR, before Cooking's handler.
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(replacement);
        characters.activate(player, replacement.getId());
        listener.onPlayerDeath(death);

        assertEquals(15, character.getFoodValue()); assertEquals(90, replacement.getFoodValue());
        verifyNoInteractions(players);
        // The next ordinary death resets the replacement as usual.
        die(listener, death);
        assertEquals(80, replacement.getFoodValue()); verify(players).savePlayer(player);
    }

    @Test void cancelledDeathsAndUncapturedDeathsLeaveFoodAlone() {
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(15);
        NutritionLifecycleListener listener = new NutritionLifecycleListener();
        PlayerDeathEvent death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);

        listener.captureDeath(death); when(death.isCancelled()).thenReturn(true); listener.onPlayerDeath(death);
        when(death.isCancelled()).thenReturn(false); listener.onPlayerDeath(death);

        assertEquals(15, character.getFoodValue()); verifyNoInteractions(players);
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 200})
    void commandPermakillDelayedDeathPreservesSuccessorFood(int successorFood) {
        Plugin plugin = MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(119);
        RPCharacter successor = new RPCharacter(player);
        successor.setFoodValue(successorFood);
        NutritionLifecycleListener listener = new NutritionLifecycleListener();
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);

        // Administrative permakill activates the successor before scheduling the body's death.
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(successor);
        characters.activate(player, successor.getId());
        PermadeathService.markPendingPermakillSounds(player);
        try {
            server.getScheduler().runTask(plugin, () -> die(listener, death));
            server.getScheduler().performOneTick();

            assertEquals(119, character.getFoodValue());
            assertEquals(successorFood, successor.getFoodValue());
            verifyNoInteractions(players);

            // RPCharacters consumes this flag on respawn; later ordinary deaths still reset food.
            assertTrue(PermadeathService.consumePendingPermakillSounds(player));
            PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
            when(respawn.getPlayer()).thenReturn(player);
            player.setFoodLevel(8);
            listener.onPlayerRespawn(respawn);
            assertEquals(successorFood, successor.getFoodValue());
            assertEquals(20, player.getFoodLevel());
            die(listener, death);
            assertEquals(80, successor.getFoodValue());
            verify(players).savePlayer(player);
        } finally {
            PermadeathService.clearPendingPermadeathRespawn(player);
        }
    }

    private static void die(NutritionLifecycleListener listener, PlayerDeathEvent death) {
        listener.captureDeath(death); listener.onPlayerDeath(death);
    }

    @Test void pendingIdentityDoesNotQueueFoodOrSaturationWrites() {
        MockBukkit.createMockPlugin("RPCharacters");
        characters.set(player, null); player.setFoodLevel(17); player.setSaturation(6);
        FoodLevelChangeEvent food = new FoodLevelChangeEvent(player, 4);
        new FoodLevelChangeGuard().onFoodLevelChange(food);
        new SaturationGuard().onFoodLevelChange(new FoodLevelChangeEvent(player, 4));
        characters.activate(player, character.getId());
        server.getScheduler().performOneTick();
        assertTrue(food.isCancelled());
        assertEquals(17, player.getFoodLevel()); assertEquals(6, player.getSaturation());
    }

    @Test void deferredFoodSaturationAndCupWorkCannotFollowAnActorThroughAtoBtoA() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack water = new ItemStack(Material.POTION);
        when(api.getChecker().checkItemWithPath(water, "cup.water")).thenReturn(true);
        player.getInventory().setHeldItemSlot(3);
        new FoodLevelChangeGuard().onFoodLevelChange(new FoodLevelChangeEvent(player, 4));
        new SaturationGuard().onFoodLevelChange(new FoodLevelChangeEvent(player, 4));
        new DrinkConsumeListener().onDrink(consume(water, EquipmentSlot.HAND));
        characters.activate(player, UUID.randomUUID());
        characters.activate(player, character.getId());
        player.setFoodLevel(17); player.setSaturation(6);
        player.getInventory().setItem(3, new ItemStack(Material.GLASS_BOTTLE));

        server.getScheduler().performOneTick();

        assertEquals(17, player.getFoodLevel()); assertEquals(6, player.getSaturation());
        assertEquals(Material.GLASS_BOTTLE, player.getInventory().getItem(3).getType());
    }

    @Test void standaloneSaturationWorkStopsIfAProviderArrivesBeforeItsCallback() {
        characters.close();
        SaturationGuard guard = new SaturationGuard();
        player.setSaturation(5);
        guard.onFoodLevelChange(new FoodLevelChangeEvent(player, 4));
        server.getScheduler().performOneTick(); assertEquals(0, player.getSaturation());
        player.setSaturation(5);
        guard.onFoodLevelChange(new FoodLevelChangeEvent(player, 4));
        characters.register();
        server.getScheduler().performOneTick(); assertEquals(5, player.getSaturation());
    }

    @Test void milkBucketIsSpentBeforeNutritionCanSwitchToAReplacementBody() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack milk = stack("milk_bucket", true, "drink", Material.MILK_BUCKET);
        player.getInventory().setItemInOffHand(milk);
        doAnswer(call -> {
            assertEquals(Material.BUCKET, player.getInventory().getItemInOffHand().getType(),
                    "The outgoing character must already have spent the milk");
            characters.activate(player, UUID.randomUUID());
            player.getInventory().setItemInOffHand(new ItemStack(Material.MILK_BUCKET, 2));
            return null;
        }).when(players).savePlayer(player);

        new DrinkConsumeListener().onMilkBucket(consume(milk, EquipmentSlot.OFF_HAND));

        assertEquals(110, character.getFoodValue());
        assertEquals(Material.MILK_BUCKET, player.getInventory().getItemInOffHand().getType());
        assertEquals(2, player.getInventory().getItemInOffHand().getAmount());
        assertTrue(player.getInventory().all(Material.BUCKET).isEmpty());
        verify(players).savePlayer(player);
    }

    @Test void aMissingMilkBucketCannotGrantNutritionAndCreativeMilkRemainsUnspent() {
        MockBukkit.createMockPlugin("RPCharacters");
        DrinkConsumeListener listener = new DrinkConsumeListener();
        ItemStack milk = stack("milk_bucket", true, "drink", Material.MILK_BUCKET);
        PlayerItemConsumeEvent missing = consume(milk, EquipmentSlot.OFF_HAND);
        listener.onMilkBucket(missing);
        assertTrue(missing.isCancelled());
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);

        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItemInOffHand(milk);
        listener.onMilkBucket(consume(milk, EquipmentSlot.OFF_HAND));
        assertEquals(110, character.getFoodValue());
        assertEquals(Material.MILK_BUCKET, player.getInventory().getItemInOffHand().getType());
        assertTrue(player.getInventory().all(Material.BUCKET).isEmpty());
        verify(players).savePlayer(player);
    }

    @Test void emptyCupReplacementPrecedesNutritionAndCannotOverwriteTheSuccessor() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack milk = stack("cup_of_milk", true, "drink", Material.POTION);
        player.getInventory().setItemInOffHand(milk);
        doAnswer(call -> {
            assertEquals(Material.BOWL, player.getInventory().getItemInOffHand().getType(),
                    "The consumed milk must have become its empty cup before nutrition callbacks");
            characters.activate(player, UUID.randomUUID());
            player.getInventory().setItemInOffHand(new ItemStack(Material.POTION, 2));
            return null;
        }).when(players).savePlayer(player);

        new DrinkConsumeListener().onDrink(consume(milk, EquipmentSlot.OFF_HAND));
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        // Simulate vanilla consuming the milk and leaving its bottle after the event.
        player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick();

        assertEquals(110, character.getFoodValue());
        assertEquals(Material.POTION, player.getInventory().getItemInOffHand().getType());
        assertEquals(2, player.getInventory().getItemInOffHand().getAmount());
        verify(players).savePlayer(player);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelledOrStaleFoodCallbacksCannotGrantNutrition(boolean cancelled) {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack cooked = stack("cooked_callback", true, "meat", Material.COOKED_BEEF);
        cooked.setAmount(2); player.getInventory().setItemInMainHand(cooked);
        PlayerItemConsumeEvent event = consume(cooked, EquipmentSlot.HAND);
        new FoodConsumeListener().onConsume(event);
        assertEquals(100, character.getFoodValue());
        if (cancelled) {
            event.setCancelled(true); // A later listener vetoes vanilla consumption.
        } else {
            spendOne(EquipmentSlot.HAND);
            characters.activate(player, UUID.randomUUID());
            characters.activate(player, character.getId());
        }

        server.getScheduler().performOneTick();

        assertEquals(100, character.getFoodValue());
        assertEquals(0, VarietyService.current(player).meals());
        assertEquals(cancelled ? 2 : 1, player.getInventory().getItemInMainHand().getAmount());
        verifyNoInteractions(players);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelledOrStaleMilkCupCallbacksCannotGrantNutritionOrReplaceItems(boolean cancelled) {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack milk = stack("cup_of_milk", true, "drink", Material.POTION);
        player.getInventory().setItemInOffHand(milk);
        PlayerItemConsumeEvent event = consume(milk, EquipmentSlot.OFF_HAND);
        new DrinkConsumeListener().onDrink(event);
        assertEquals(100, character.getFoodValue());
        if (cancelled) {
            event.setCancelled(true);
        } else {
            player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
            characters.activate(player, UUID.randomUUID());
            characters.activate(player, character.getId());
        }

        server.getScheduler().performOneTick();

        assertEquals(100, character.getFoodValue());
        assertEquals(0, VarietyService.current(player).meals());
        assertEquals(cancelled ? Material.POTION : Material.GLASS_BOTTLE,
                player.getInventory().getItemInOffHand().getType());
        verifyNoInteractions(players);
    }

    @Test void bowlDetachesFoodBeforeNutritionCallbacksAndSkipsStaleActorFeedback() {
        MockBukkit.createMockPlugin("RPCharacters");
        Furniture bowl = mock(Furniture.class);
        var bowlLocation = player.getLocation();
        when(bowl.getId()).thenReturn("bowl"); when(bowl.getLoc()).thenReturn(bowlLocation);
        PlacedSlot slot = mock(PlacedSlot.class);
        when(slot.getCurrentItem()).thenReturn(stack("soup", true, "soup", Material.MUSHROOM_STEW));
        when(bowl.hasActiveSlot("food_item")).thenReturn(true);
        when(bowl.getActiveSlots()).thenReturn(new HashMap<>(Map.of("food_item", slot)));
        PlateManager plates = mock(PlateManager.class);
        FurnitureInteractEvent event = mock(FurnitureInteractEvent.class);
        when(event.getFurniture()).thenReturn(bowl); when(event.getPlayer()).thenReturn(player);
        try (MockedStatic<InteractibleFurniture> furniture = mockStatic(InteractibleFurniture.class)) {
            InteractibleFurniture plugin = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
            furniture.when(InteractibleFurniture::getInstance).thenReturn(plugin);
            doAnswer(call -> {
                verify(plates).clear(bowl);
                verify(plugin.getFurnitureManager()).markDirty(bowl);
                characters.activate(player, UUID.randomUUID());
                return null;
            }).when(players).savePlayer(player);

            new BowlEatHandler(plates).onInteract(event);

            verify(plates).clear(bowl); verify(players).savePlayer(player);
            verify(player, never()).swingMainHand();
            assertEquals(110, character.getFoodValue());
        }
    }

    @Test void emptyCupBuilderReentryCannotOverwriteTheReplacementHandOrApplyNutrition() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack milk = stack("cup_of_milk", true, "drink", Material.POTION);
        player.getInventory().setItemInOffHand(milk);
        when(api.getCreator().getItemFromPath("cup.empty")).thenAnswer(call -> {
            characters.activate(player, UUID.randomUUID());
            player.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND, 2));
            return new ItemStack(Material.BOWL);
        });
        try (MockedStatic<NutritionService> nutrition = mockStatic(NutritionService.class)) {
            new DrinkConsumeListener().onDrink(consume(milk, EquipmentSlot.OFF_HAND));
            player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
            server.getScheduler().performOneTick();

            assertEquals(Material.DIAMOND, player.getInventory().getItemInOffHand().getType());
            assertEquals(2, player.getInventory().getItemInOffHand().getAmount());
            nutrition.verifyNoInteractions();
            verifyNoInteractions(players);
        }
    }

    @Test void pendingFoodIntakeIsCancelledAndCanBeRetriedAfterReadiness() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack cooked = stack("pending_cooked", true, "meat", Material.COOKED_BEEF);
        cooked.setAmount(2); player.getInventory().setItemInMainHand(cooked);
        characters.set(player, null);
        FoodConsumeListener listener = new FoodConsumeListener();
        PlayerItemConsumeEvent pending = consume(cooked, EquipmentSlot.HAND);

        listener.onConsume(pending);

        assertTrue(pending.isCancelled());
        assertEquals(PlayerActionContext.NO_CHARACTER_MESSAGE, player.nextMessage());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        characters.activate(player, character.getId());
        server.getScheduler().performOneTick();
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);

        PlayerItemConsumeEvent ready = consume(cooked, EquipmentSlot.HAND);
        listener.onConsume(ready);
        assertFalse(ready.isCancelled());
        spendOne(EquipmentSlot.HAND);
        server.getScheduler().performOneTick();
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(110, character.getFoodValue()); verify(players).savePlayer(player);
    }

    @Test void pendingMilkBucketIntakePreservesItsInputUntilAReadyRetry() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack milk = stack("milk_bucket", true, "drink", Material.MILK_BUCKET);
        player.getInventory().setItemInOffHand(milk);
        characters.set(player, null);
        DrinkConsumeListener listener = new DrinkConsumeListener();
        PlayerItemConsumeEvent pending = consume(milk, EquipmentSlot.OFF_HAND);

        listener.onMilkBucket(pending);

        assertTrue(pending.isCancelled());
        assertEquals(PlayerActionContext.NO_CHARACTER_MESSAGE, player.nextMessage());
        assertEquals(Material.MILK_BUCKET, player.getInventory().getItemInOffHand().getType());
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        characters.activate(player, character.getId());
        server.getScheduler().performOneTick();
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);

        listener.onMilkBucket(consume(milk, EquipmentSlot.OFF_HAND));
        assertEquals(Material.BUCKET, player.getInventory().getItemInOffHand().getType());
        assertEquals(110, character.getFoodValue()); verify(players).savePlayer(player);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cup.water", "cup_of_milk"})
    void pendingCupIsKeptWithAMessageLikeRefusedFood(String cup) {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack drink = cup.equals("cup.water") ? new ItemStack(Material.POTION) : stack(cup, true, "drink", Material.POTION);
        when(api.getChecker().checkItemWithPath(drink, "cup.water")).thenReturn(cup.equals("cup.water"));
        when(api.getChecker().checkItemWithPath(drink, "cup.milk")).thenReturn(!cup.equals("cup.water"));
        player.getInventory().setItemInMainHand(drink);
        characters.set(player, null);
        DrinkConsumeListener listener = new DrinkConsumeListener();
        PlayerItemConsumeEvent pending = consume(drink, EquipmentSlot.HAND);

        listener.onCupWithoutCharacter(pending);

        assertTrue(pending.isCancelled());
        assertEquals(PlayerActionContext.NO_CHARACTER_MESSAGE, player.nextMessage());
        assertEquals(drink, player.getInventory().getItemInMainHand());
        characters.activate(player, character.getId());
        PlayerItemConsumeEvent ready = consume(drink, EquipmentSlot.HAND);
        listener.onCupWithoutCharacter(ready);
        assertFalse(ready.isCancelled()); assertNull(player.nextMessage());
    }

    @Test void cupRefusalIgnoresOtherItems() {
        characters.set(player, null);
        PlayerItemConsumeEvent bread = consume(new ItemStack(Material.BREAD), EquipmentSlot.HAND);
        PlayerItemConsumeEvent nothing = mock(PlayerItemConsumeEvent.class);
        new DrinkConsumeListener().onCupWithoutCharacter(bread);
        new DrinkConsumeListener().onCupWithoutCharacter(nothing);
        assertFalse(bread.isCancelled()); verify(nothing, never()).setCancelled(anyBoolean());
        assertNull(player.nextMessage());
    }

    @Test void pendingMilkCupIntakeDoesNotQueueWorkForTheNextReadySession() {
        MockBukkit.createMockPlugin("RPCharacters");
        ItemStack milk = stack("cup_of_milk", true, "drink", Material.POTION);
        player.getInventory().setItemInOffHand(milk);
        characters.set(player, null);
        DrinkConsumeListener listener = new DrinkConsumeListener();

        listener.onDrink(consume(milk, EquipmentSlot.OFF_HAND));

        assertEquals(Material.POTION, player.getInventory().getItemInOffHand().getType());
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
        characters.activate(player, character.getId());
        server.getScheduler().performOneTick();
        assertEquals(Material.GLASS_BOTTLE, player.getInventory().getItemInOffHand().getType());
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);

        player.getInventory().setItemInOffHand(milk);
        listener.onDrink(consume(milk, EquipmentSlot.OFF_HAND));
        player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick();
        assertEquals(Material.BOWL, player.getInventory().getItemInOffHand().getType());
        assertEquals(110, character.getFoodValue()); verify(players).savePlayer(player);
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

    private void spendOne(EquipmentSlot hand) {
        ItemStack remaining = player.getInventory().getItem(hand).clone();
        remaining.setAmount(remaining.getAmount() - 1);
        player.getInventory().setItem(hand, remaining.getAmount() == 0 ? null : remaining);
    }
}
