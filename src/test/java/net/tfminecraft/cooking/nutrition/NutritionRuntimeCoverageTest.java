package net.tfminecraft.cooking.nutrition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes.AttributeInstance;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.testsupport.TestCharacters;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class NutritionRuntimeCoverageTest {
    @TempDir Path directory;
    private ServerMock server;
    private PlayerMock player;
    private RPCharacter character;
    private PlayerManager players;
    private MockedStatic<RPCharacters> rp;
    private Cooking previousPlugin;
    private List<TagTrack> tracks;
    private TestCharacters characters;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getLogger()).thenReturn(mock(Logger.class));
        player = spy(new PlayerMock(server, "Eater")); server.addPlayer(player);
        character = new RPCharacter(player);
        characters = new TestCharacters(MockBukkit.createMockPlugin("CharacterProvider"));
        characters.activate(player, character.getId());
        players = mock(PlayerManager.class);
        rp = mockStatic(RPCharacters.class);
        rp.when(RPCharacters::getPlayerManager).thenReturn(players);
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
        tracks = new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
        NutritionConfig.load(new YamlConfiguration()); QualityConfig.apply(1,5,null,null);
        NutritionLog.configure(false,false,null);
    }

    @AfterEach void cleanup() {
        NutritionDrainTask.stop(); SaturationGuard.stop();
        NutritionLog.configure(false,false,null); NutritionConfig.load(new YamlConfiguration());
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        characters.close(); rp.close(); MockBukkit.unmock(); Cooking.plugin = previousPlugin;
    }

    @Test void eatingUpdatesTheCharacterHistoryHudAndPersistenceOnlyWhenFoodIsGained() {
        FoodItem food = food(10, 500);
        character.setFoodValue(100);
        NutritionService.tryApplyEat(null, food); NutritionService.tryApplyEat(player, null);
        NutritionService.tryApplyEat(player, food); // Dependency not enabled yet.
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        MockBukkit.createMockPlugin("RPCharacters");
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(null);
        NutritionService.tryApplyEat(player, food); assertEquals(100, character.getFoodValue());
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
        NutritionService.tryApplyEat(player, food);
        assertEquals(110, character.getFoodValue()); assertTrue(character.getRawDietScore() > 0);
        assertEquals(11, player.getFoodLevel()); assertEquals(0, player.getSaturation());
        assertEquals(1, VarietyService.current(player).meals()); verify(players).savePlayer(player);
        character.setFoodValue(198); NutritionService.tryApplyEat(player, food);
        assertEquals(200, character.getFoodValue());
        clearInvocations(players);
        NutritionService.tryApplyEat(player, food);
        character.setFoodValue(100); NutritionService.tryApplyEat(player, food(0,20));
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        character.setRawDietScore(0); character.setDietScore(0);
        NutritionService.tryApplyEat(player, food(1,0));
        assertEquals(101, character.getFoodValue()); assertEquals(0, character.getDietScore());
    }

    @Test void extremeConfiguredFoodValuesFillThePoolWithoutIntegerOverflow() {
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(10);
        NutritionService.tryApplyEat(player, food(Integer.MAX_VALUE,20));
        assertEquals(200, character.getFoodValue(), "Eating must never empty an existing food pool");
        verify(players).savePlayer(player);
    }

    @Test void characterStorageCapsDoNotAwardNutritionForFoodThatWasNeverGained() {
        MockBukkit.createMockPlugin("RPCharacters");
        configure("nutrition.max-food",400);
        character.setFoodValue(RPCharacter.MAX_FOOD_VALUE);
        NutritionService.tryApplyEat(player,food(20,500));
        assertEquals(RPCharacter.MAX_FOOD_VALUE,character.getFoodValue());
        assertEquals(0,VarietyService.current(player).meals());
        assertEquals(0,character.getRawDietScore()); verifyNoInteractions(players);
    }

    @Test void displaySynchronizationClampsFoodClearsSaturationAndAlwaysReleasesItsGuard() {
        assertEquals(0,NutritionDisplayService.toFoodLevel(-1));
        assertEquals(20,NutritionDisplayService.toFoodLevel(Integer.MAX_VALUE));
        assertFalse(NutritionDisplayService.isSyncing(null));
        NutritionDisplayService.sync(null,character); NutritionDisplayService.sync(player,null);
        NutritionDisplayService.syncFromPlayer(null);
        NutritionDisplayService.syncFromPlayer(player);
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(100); player.setSaturation(4);
        NutritionDisplayService.sync(player,character);
        assertEquals(10,player.getFoodLevel()); assertEquals(0,player.getSaturation());
        NutritionDisplayService.sync(player,character); assertFalse(NutritionDisplayService.isSyncing(player));
        character.setFoodValue(80); NutritionDisplayService.syncFromPlayer(player);
        assertEquals(8,player.getFoodLevel());
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(null);
        NutritionDisplayService.syncFromPlayer(player); assertEquals(8,player.getFoodLevel());
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
        PlayerMock failing = player;
        doAnswer(call -> {
            assertTrue(NutritionDisplayService.isSyncing(failing));
            throw new IllegalStateException("Disconnected during HUD update");
        }).when(failing).setFoodLevel(anyInt());
        character.setFoodValue(20);
        assertThrows(IllegalStateException.class,()->NutritionDisplayService.sync(failing,character));
        assertFalse(NutritionDisplayService.isSyncing(failing));
    }

    @Test void drainSchedulesOnceHonorsIntervalsAndHandlesMissingOrEmptyCharacters() {
        configure("nutrition.drain-interval",2,"nutrition.drain-amount",3);
        NutritionDrainTask.stop(); NutritionDrainTask.start(); NutritionDrainTask.start();
        server.getScheduler().performTicks(20); assertEquals(200,character.getFoodValue());
        MockBukkit.createMockPlugin("RPCharacters");
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(null);
        server.getScheduler().performTicks(20); verifyNoInteractions(players);
        rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
        server.getScheduler().performTicks(20); assertEquals(200,character.getFoodValue());
        server.getScheduler().performTicks(20); assertEquals(197,character.getFoodValue());
        verify(players).savePlayer(player);
        character.setFoodValue(0); server.getScheduler().performTicks(40);
        assertEquals(0,character.getFoodValue());
        configure("nutrition.drain-interval",1,"nutrition.drain-amount",0);
        character.setFoodValue(20); server.getScheduler().performTicks(20);
        assertEquals(20,character.getFoodValue());
        NutritionDrainTask.stop(); NutritionDrainTask.stop();
        configure("nutrition.drain-interval",1,"nutrition.drain-amount",3);
        server.getScheduler().performTicks(40); assertEquals(20,character.getFoodValue());
    }

    @Test void invalidNegativeDrainCannotGenerateFood() {
        MockBukkit.createMockPlugin("RPCharacters");
        configure("nutrition.drain-interval",1,"nutrition.drain-amount",-3);
        character.setFoodValue(20); NutritionDrainTask.start();
        server.getScheduler().performTicks(20);
        assertEquals(20,character.getFoodValue()); verifyNoInteractions(players);
    }

    @Test void varietyHistoryAndTierMessagesTrackActualChanges() {
        assertEquals(0,VarietyService.current(null).meals());
        VarietyService.recordMeal(null,IngredientLineage.ofMain("Wheat"));
        VarietyService.applyEffective(null,null);
        VarietyService.recordMeal(player,IngredientLineage.ofMain("Wheat"));
        assertEquals(1,VarietyService.current(player).meals());
        character.setRawDietScore(20); VarietyService.applyEffective(player,character);
        assertTrue(character.getDietScore() <= 20);
        configure("nutrition.variety.enabled",false);
        VarietyService.recordMeal(player,IngredientLineage.ofMain("Carrot"));
        assertEquals(1,VarietyService.current(player).meals());
        VarietyService.applyEffective(player,character); assertEquals(20,character.getDietScore());
        assertFalse(DietTierService.seedIfAbsent(null,character)); assertFalse(DietTierService.seedIfAbsent(player,null));
        assertFalse(DietTierService.seedIfAbsent(player,character));
        verifyNoInteractions(players);
        MockBukkit.createMockPlugin("RPCharacters"); character.setLastDietTierId(" ");
        assertTrue(DietTierService.seedIfAbsent(player,character)); verify(players).savePlayer(player);
        assertFalse(DietTierService.seedIfAbsent(player,character));
        DietTierService.checkAndNotify(null,character,null); DietTierService.checkAndNotify(player,null,null);
        DietTierService.checkAndNotify(player,character,null); assertNull(player.nextMessage());
        character.setDietScore(0); DietTierService.checkAndNotify(player,character,null);
        assertTrue(player.nextMessage().contains("Your diet is now")); assertNull(player.nextMessage());
        configure("nutrition.variety.enabled",true);
        character.setDietScore(40); DietTierService.checkAndNotify(player,character,VarietyService.current(player));
        assertTrue(player.nextMessage().contains("Your diet is now"));
        assertTrue(player.nextMessage().contains("nutrition penalty")); assertNull(player.nextMessage());
    }

    @Test void attributeBridgeOnlyUpdatesAvailableConfiguredAttributes() {
        MockBukkit.createMockPlugin("RPCharacters");
        NutritionAttributeBridge.apply(null,character); NutritionAttributeBridge.apply(player,null);
        NutritionAttributeBridge.apply(player,character);
        MockBukkit.createMockPlugin("MMOCore");
        try (MockedStatic<PlayerData> data = mockStatic(PlayerData.class)) {
            PlayerData pd = mock(PlayerData.class,RETURNS_DEEP_STUBS);
            data.when(() -> PlayerData.get(player)).thenReturn(pd);
            String key = NutritionConfig.attributeName();
            when(pd.getAttributes().getInstance(key)).thenReturn(null);
            NutritionAttributeBridge.apply(player,character);
            AttributeInstance attribute = mock(AttributeInstance.class);
            when(pd.getAttributes().getInstance(key)).thenReturn(attribute);
            character.setDietScore(12); NutritionAttributeBridge.apply(player,character);
            verify(attribute).setBase(12);
            when(attribute.getBase()).thenReturn(12);
            NutritionAttributeBridge.apply(player,character);
            verify(attribute).setBase(12);
        }
    }

    @Test void attributeBridgeGivesBackHealthAndManaClampedWhileTheBuffsAreReplaced() {
        MockBukkit.createMockPlugin("RPCharacters");
        MockBukkit.createMockPlugin("MMOCore");
        try (MockedStatic<PlayerData> data = mockStatic(PlayerData.class)) {
            PlayerData pd = mock(PlayerData.class,RETURNS_DEEP_STUBS);
            data.when(() -> PlayerData.get(player)).thenReturn(pd);
            AttributeInstance attribute = mock(AttributeInstance.class);
            when(pd.getAttributes().getInstance(NutritionConfig.attributeName())).thenReturn(attribute);
            when(pd.getStats().getStat("MAX_MANA")).thenReturn(30.0);
            when(pd.getStats().getStat("MAX_STAMINA")).thenReturn(10.0);
            when(pd.getStats().getStat("MAX_STELLIUM")).thenReturn(40.0);
            double[] mana = {30};
            when(pd.getMana()).thenAnswer(call -> mana[0]);
            when(pd.getStamina()).thenReturn(5.0);
            when(pd.getStellium()).thenReturn(50.0, 40.0);
            player.setHealth(20);
            doAnswer(call -> { player.setHealth(16); mana[0] = 21; return null; }).when(attribute).setBase(36);
            character.setDietScore(36); NutritionAttributeBridge.apply(player,character);
            assertEquals(20, player.getHealth());
            verify(pd).setMana(30.0, ResourceUpdateReason.CLAMPING);
            verify(pd,never()).setStamina(anyDouble(),any(ResourceUpdateReason.class));
            verify(pd,never()).setStellium(anyDouble(),any(ResourceUpdateReason.class));

            doAnswer(call -> {
                player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(18); player.setHealth(14); return null;
            }).when(attribute).setBase(10);
            character.setDietScore(10); NutritionAttributeBridge.apply(player,character);
            assertEquals(18, player.getHealth());
        }
    }

    @Test void nutritionLogWritesSanitizedRecordsAndHandlesFilesystemFailures() throws Exception {
        NutritionLog.configure(true,false,null); assertTrue(NutritionLog.isEnabled());
        NutritionLog.append("IGNORED",player,character,null);
        NutritionLog.configure(true,true,directory.toFile());
        NutritionLog.append("MEAL with\nnewline",player,character,"first\nsecond\rthird");
        NutritionLog.append(null,null,null," ");
        Path log=directory.resolve("logs/nutrition.log");
        String text=Files.readString(log);
        assertTrue(text.contains("event=MEAL_with_newline")); assertTrue(text.contains("first second third"));
        assertTrue(text.contains("character=-")); assertTrue(text.contains("event=-"));
        NutritionLog.configure(true,true,directory.toFile());
        assertFalse(Files.readString(log).contains("MEAL"));
        Files.delete(log); Files.createDirectory(log); Files.writeString(log.resolve("child"),"occupied");
        assertDoesNotThrow(()->NutritionLog.configure(true,true,directory.toFile()));
        verify(Cooking.plugin.getLogger()).warning(contains("Failed to wipe"));
        verify(Cooking.plugin.getLogger()).warning(contains("Failed to write"));
        verify(Cooking.plugin.getLogger(),never()).severe(anyString());
        Cooking saved=Cooking.plugin; Cooking.plugin=null;
        try { assertDoesNotThrow(()->NutritionLog.append("WRITE_FAIL",null,null,null)); }
        finally { Cooking.plugin=saved; }
        NutritionLog.configure(false,false,directory.toFile()); assertFalse(NutritionLog.isEnabled());
    }

    @Test void emptyHistoryAndZeroWeightsHaveNoPenaltyAndNoFoodGainLeavesDietUntouched() {
        assertEquals(20,DietMath.lerpRaw(20,40,0,200,1));
        assertEquals(20,DietMath.lerpRaw(20,40,1,0,1));
        assertTrue(VarietyHistory.of(null,10).isEmpty());
        assertEquals("",VarietyHistoryCodec.encode(null));
        assertEquals("",VarietyHistoryCodec.encode(VarietyHistory.empty()));
        VarietyHistory history=VarietyHistory.of(List.of(IngredientLineage.ofMain("Wheat")),5);
        var noWeights=VarietyMath.evaluate(history,0,0,8,60);
        assertEquals(0,noWeights.penaltyPercent()); assertEquals(100,noWeights.progressPercent());
    }

    @Test void loadedRpcDataCannotAuthorizeNutritionWhileSharedIdentityIsUnavailableOrPending() {
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(100);
        player.setFoodLevel(17); player.setSaturation(6);
        characters.close();
        NutritionService.tryApplyEat(player, food(10, 40));
        NutritionDisplayService.sync(player, character);
        assertNull(NutritionContext.capture(player));
        characters.register(); characters.set(player, null);
        NutritionService.tryApplyEat(player, food(10, 40));
        NutritionDisplayService.syncFromPlayer(player);
        assertFalse(DietTierService.seedIfAbsent(player, character));
        assertEquals(100, character.getFoodValue());
        assertEquals(0, character.getRawDietScore());
        assertEquals(0, VarietyService.current(player).meals());
        assertEquals(17, player.getFoodLevel()); assertEquals(6, player.getSaturation());
        verifyNoInteractions(players);
        characters.activate(player, character.getId());
        NutritionService.tryApplyEat(player, food(10, 40));
        assertEquals(110, character.getFoodValue());
        verify(players).savePlayer(player);
    }

    @Test void bridgeChecksRpcOwnerCharacterAndCanonicalIdBeforeExposingMutableData() {
        MockBukkit.createMockPlugin("RPCharacters");
        String originalId = character.getId();
        character.setOwner(server.addPlayer("Other"));
        assertNull(NutritionContext.capture(player));
        character.setOwner(player);
        character.setId(UUID.randomUUID().toString());
        assertNull(NutritionContext.capture(player));
        character.setId("malformed");
        assertNull(NutritionContext.capture(player));
        character.setId(originalId.toUpperCase(Locale.ROOT));
        assertSame(character, NutritionContext.capture(player).character());
        assertNull(NutritionContext.capture(player, new RPCharacter(player)));
        verifyNoInteractions(players);
    }

    @Test void returningToTheSameCharacterDoesNotReviveCapturedNutritionWork() {
        MockBukkit.createMockPlugin("RPCharacters");
        NutritionContext old = NutritionContext.capture(player);
        assertNotNull(old);
        characters.activate(player, UUID.randomUUID());
        characters.activate(player, character.getId());
        character.setFoodValue(20); player.setFoodLevel(17); player.setSaturation(6);
        NutritionDisplayService.sync(old, "stale");
        NutritionAttributeBridge.apply(old);
        assertFalse(old.save());
        assertFalse(DietTierService.seedIfAbsent(old));
        assertEquals(17, player.getFoodLevel()); assertEquals(6, player.getSaturation());
        assertNull(character.getLastDietTierId());
        assertFalse(NutritionDisplayService.isSyncing(player));
        verifyNoInteractions(players);
    }

    @Test void nestedHudSyncKeepsTheOuterGuardUntilItsOwnFinallyCompletes() {
        MockBukkit.createMockPlugin("RPCharacters");
        NutritionContext context = NutritionContext.capture(player);
        character.setFoodValue(50);
        AtomicInteger writes = new AtomicInteger();
        doAnswer(call -> {
            assertTrue(NutritionDisplayService.isSyncing(player));
            if (writes.getAndIncrement() == 0) {
                NutritionDisplayService.sync(context, "inner");
                assertTrue(NutritionDisplayService.isSyncing(player));
            }
            return call.callRealMethod();
        }).when(player).setFoodLevel(anyInt());

        NutritionDisplayService.sync(context, "outer");

        assertEquals(2, writes.get());
        assertEquals(5, player.getFoodLevel());
        assertFalse(NutritionDisplayService.isSyncing(player));
    }

    @Test void attributeReentryAfterSetBaseStopsAllOutgoingResourceRestoration() {
        MockBukkit.createMockPlugin("RPCharacters"); MockBukkit.createMockPlugin("MMOCore");
        try (MockedStatic<PlayerData> data = mockStatic(PlayerData.class)) {
            AttributeInstance attribute = mock(AttributeInstance.class);
            PlayerData pd = attributeData(data, attribute);
            when(pd.getMana()).thenReturn(30.0);
            character.setDietScore(36);
            doAnswer(call -> {
                player.setHealth(16);
                characters.activate(player, UUID.randomUUID());
                characters.activate(player, character.getId());
                return null;
            }).when(attribute).setBase(36);

            NutritionAttributeBridge.apply(player, character);

            assertEquals(16, player.getHealth());
            verify(pd, never()).setMana(anyDouble(), any(ResourceUpdateReason.class));
            verify(pd, never()).setStamina(anyDouble(), any(ResourceUpdateReason.class));
            verify(pd, never()).setStellium(anyDouble(), any(ResourceUpdateReason.class));
        }
    }

    @Test void healthRestorationCallbackStopsSubsequentManaRestoration() {
        MockBukkit.createMockPlugin("RPCharacters"); MockBukkit.createMockPlugin("MMOCore");
        try (MockedStatic<PlayerData> data = mockStatic(PlayerData.class)) {
            AttributeInstance attribute = mock(AttributeInstance.class);
            PlayerData pd = attributeData(data, attribute);
            double[] mana = {30}; when(pd.getMana()).thenAnswer(call -> mana[0]);
            doAnswer(call -> {
                player.setHealth(16); mana[0] = 20; return null;
            }).when(attribute).setBase(36);
            doAnswer(call -> {
                call.callRealMethod();
                if ((double) call.getArgument(0) == 20) characters.set(player, null);
                return null;
            }).when(player).setHealth(anyDouble());
            character.setDietScore(36);

            NutritionAttributeBridge.apply(player, character);

            assertEquals(20, player.getHealth());
            verify(pd, never()).setMana(anyDouble(), any(ResourceUpdateReason.class));
        }
    }

    @Test void manaRestorationCallbackStopsLaterStaminaAndStelliumWrites() {
        MockBukkit.createMockPlugin("RPCharacters"); MockBukkit.createMockPlugin("MMOCore");
        try (MockedStatic<PlayerData> data = mockStatic(PlayerData.class)) {
            AttributeInstance attribute = mock(AttributeInstance.class);
            PlayerData pd = attributeData(data, attribute);
            double[] values = {30, 8, 30};
            when(pd.getMana()).thenAnswer(call -> values[0]);
            when(pd.getStamina()).thenAnswer(call -> values[1]);
            when(pd.getStellium()).thenAnswer(call -> values[2]);
            doAnswer(call -> {
                values[0] = 20; values[1] = 3; values[2] = 10; return null;
            }).when(attribute).setBase(36);
            doAnswer(call -> {
                characters.close(); return null;
            }).when(pd).setMana(30.0, ResourceUpdateReason.CLAMPING);
            character.setDietScore(36);

            NutritionAttributeBridge.apply(player, character);

            verify(pd).setMana(30.0, ResourceUpdateReason.CLAMPING);
            verify(pd, never()).setStamina(anyDouble(), any(ResourceUpdateReason.class));
            verify(pd, never()).setStellium(anyDouble(), any(ResourceUpdateReason.class));
        }
    }

    @Test void saveReentryCannotApplyOutgoingHudToTheReplacementCharacter() {
        MockBukkit.createMockPlugin("RPCharacters");
        character.setFoodValue(100);
        RPCharacter replacement = new RPCharacter(player);
        doAnswer(call -> {
            rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(replacement);
            characters.activate(player, replacement.getId());
            player.setFoodLevel(17); player.setSaturation(6);
            return null;
        }).when(players).savePlayer(player);

        NutritionService.tryApplyEat(player, food(10, 0));

        assertEquals(110, character.getFoodValue());
        assertEquals(200, replacement.getFoodValue());
        assertEquals(0, replacement.getRawDietScore());
        assertEquals(17, player.getFoodLevel()); assertEquals(6, player.getSaturation());
        verify(players).savePlayer(player);
    }

    @Test void drainWaitsForReadinessEvenWhenRpcStillHasAnActiveCharacter() {
        MockBukkit.createMockPlugin("RPCharacters");
        configure("nutrition.drain-interval", 1, "nutrition.drain-amount", 3);
        character.setFoodValue(100); characters.set(player, null);
        NutritionDrainTask.start(); server.getScheduler().performTicks(20);
        assertEquals(100, character.getFoodValue()); verifyNoInteractions(players);
        characters.activate(player, character.getId());
        server.getScheduler().performTicks(20);
        assertEquals(97, character.getFoodValue()); verify(players).savePlayer(player);
    }

    private PlayerData attributeData(MockedStatic<PlayerData> data, AttributeInstance attribute) {
        PlayerData pd = mock(PlayerData.class, RETURNS_DEEP_STUBS);
        data.when(() -> PlayerData.get(player)).thenReturn(pd);
        when(pd.getAttributes().getInstance(NutritionConfig.attributeName())).thenReturn(attribute);
        when(pd.getStats().getStat("MAX_MANA")).thenReturn(30.0);
        when(pd.getStats().getStat("MAX_STAMINA")).thenReturn(10.0);
        when(pd.getStats().getStat("MAX_STELLIUM")).thenReturn(40.0);
        return pd;
    }

    private FoodItem food(double food, double nutrition) {
        YamlConfiguration config = new YamlConfiguration(); config.set("food",food); config.set("nutrition",nutrition);
        FoodItem item = new FoodItem("meal",config); item.setQualityRange(1,1);
        item.setOrigin("Wheat"); item.setLineage(IngredientLineage.ofMain("Wheat")); return item;
    }

    private void configure(Object... entries) {
        YamlConfiguration config = new YamlConfiguration();
        for(int i=0;i<entries.length;i+=2) config.set((String)entries[i],entries[i+1]);
        NutritionConfig.load(config);
    }
}
