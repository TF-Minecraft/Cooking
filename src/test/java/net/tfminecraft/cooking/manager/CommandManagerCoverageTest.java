package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.baking.*;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.husbandry.*;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.ModelLoader;
import net.tfminecraft.cooking.mixing.MixingBowlDisplay;
import net.tfminecraft.cooking.nutrition.NutritionConfig;
import net.tfminecraft.cooking.nutrition.NutritionDisplayService;
import net.tfminecraft.cooking.nutrition.NutritionLog;
import net.tfminecraft.cooking.quality.*;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.NameComposer;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class CommandManagerCoverageTest {
    private final CommandManager commands = new CommandManager();
    private final Command command = mock(Command.class);
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final Map<UUID, Furniture> furniture = new LinkedHashMap<>();
    private ServerMock server;
    private PlayerMock player;
    private Cooking previousPlugin;
    private Cooking plugin;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Admin");
        player.setOp(true);
        previousPlugin = Cooking.plugin;
        plugin = mock(Cooking.class);
        Cooking.plugin = plugin;
        InteractibleFurniture integration = mock(InteractibleFurniture.class);
        FurnitureManager manager = mock(FurnitureManager.class);
        when(integration.getFurnitureManager()).thenReturn(manager);
        when(manager.getPlacedFurniture()).thenReturn(furniture);
        staticMock(InteractibleFurniture.class).when(InteractibleFurniture::getInstance).thenReturn(integration);
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
        for (int i = statics.size() - 1; i >= 0; i--) statics.get(i).close();
        Cooking.plugin = previousPlugin;
    }

    @Test void helpUnknownCommandsAndConsoleOnlyPathsExplainWhatToDo() {
        run();
        assertTrue(messages().contains("§e/cooking reload"));
        run("unknown");
        assertTrue(messages().stream().anyMatch(s -> s.contains("Contexts:")));
        CommandSender console = mock(CommandSender.class);
        run(console, "heat");
        verify(console).sendMessage("§cOnly players can use this command.");
    }

    @Test void reloadReportsUnavailablePluginOrReloadedModelCounts() {
        Cooking.plugin = null;
        run("reload");
        assertEquals(List.of("§cPlugin not ready."), messages());
        Cooking.plugin = plugin;
        staticMock(ModelLoader.class).when(ModelLoader::get).thenReturn(List.of(mock(FoodModel.class)));
        List<FoodItem> foods = List.of(food("carrot"), food("beef"));
        staticMock(FoodLoader.class).when(FoodLoader::get).thenReturn(foods);
        run("RELOAD");
        verify(plugin).reloadAll();
        assertEquals(List.of("§aCooking reloaded (1 models, 2 food types)."), messages());
    }

    @Test void completionsRespectPermissionAndFilterCommandsPlayersAndSpecies() {
        player.setOp(false);
        assertTrue(tabs("").isEmpty());
        player.setOp(true);
        assertEquals(List.of("preview"), tabs("pr"));
        assertTrue(tabs("zz").isEmpty());
        server.addPlayer("zoe");
        server.addPlayer("Alice");
        server.addPlayer("Aaron");
        assertEquals(List.of("Aaron", "Admin", "Alice"), tabs("food", "a"));
        assertEquals(List.of("spawn", "save"), tabs("husbandry", "s"));
        assertEquals(List.of("save"), tabs("husbandry", "sa"));
        MockedStatic<HusbandryConfig> config = staticMock(HusbandryConfig.class);
        config.when(HusbandryConfig::species).thenReturn(Map.of(EntityType.COW, mock(HusbandrySpecies.class), EntityType.PIG, mock(HusbandrySpecies.class)));
        assertEquals(List.of("COW"), tabs("husbandry", "spawn", "c"));
        assertEquals(List.of("0", "20", "1000"), tabs("husbandry", "spawn", "cow", ""));
        assertEquals(List.of("0", "100", "200"), tabs("husbandry", "spawn", "cow", "1", ""));
        assertTrue(tabs().isEmpty());
        assertTrue(tabs("other", "").isEmpty());
        assertTrue(tabs("other", "spawn", "").isEmpty());
        assertTrue(tabs("husbandry", "save", "").isEmpty());
        assertTrue(tabs("other", "spawn", "cow", "").isEmpty());
        assertTrue(tabs("husbandry", "save", "cow", "").isEmpty());
        assertTrue(tabs("other", "spawn", "cow", "1", "").isEmpty());
        assertTrue(tabs("husbandry", "save", "cow", "1", "").isEmpty());
        assertTrue(tabs("husbandry", "spawn", "cow", "1", "2", "").isEmpty());
    }

    @Test void foodCommandValidatesArityTargetCharacterAndFoodRange() {
        MockedStatic<RPCharacters> characters = staticMock(RPCharacters.class);
        run("food");
        assertTrue(messages().getFirst().contains("Usage:"));
        run("food", "offline", "2");
        assertEquals(List.of("§cThat player is not online."), messages());
        run("food", "Admin", "2");
        assertEquals(List.of("§cThat player has no active character."), messages());
        RPCharacter character = mock(RPCharacter.class);
        characters.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
        run("food", "Admin", "NaN");
        assertEquals(List.of("§cFood must be a whole number."), messages());
        for (String value : List.of("-1", Integer.toString(NutritionConfig.maxFood() + 1))) {
            run("food", "Admin", value);
            assertTrue(messages().getFirst().contains("Food must be between"));
        }
        verify(character, never()).setFoodValue(anyInt());
    }

    @Test void foodUpdatesTheActiveCharacterDisplayPersistenceAndBothNotifications() {
        PlayerMock target = server.addPlayer("Target");
        target.setOp(true);
        RPCharacter character = mock(RPCharacter.class);
        AtomicInteger food = new AtomicInteger(80);
        when(character.getFoodValue()).thenAnswer(call -> food.get());
        doAnswer(call -> { food.set(call.getArgument(0)); return null; }).when(character).setFoodValue(anyInt());
        PlayerManager manager = mock(PlayerManager.class);
        MockedStatic<RPCharacters> characters = staticMock(RPCharacters.class);
        characters.when(() -> RPCharacters.getActiveCharacter(target)).thenReturn(character);
        characters.when(RPCharacters::getPlayerManager).thenReturn(manager);
        MockedStatic<NutritionDisplayService> display = staticMock(NutritionDisplayService.class);
        staticMock(NutritionLog.class);

        run("food", "Target", "100");

        assertEquals(100, food.get());
        verify(manager).savePlayer(target);
        display.verify(() -> NutritionDisplayService.sync(target, character, "admin"));
        assertEquals(List.of("§aSet Target's food from 80 to 100."), messages());
        assertEquals("§eYour food was set to 100 by Admin.", target.nextMessage());
        run(target, "food", "Target", "0");
        assertEquals(0, food.get());
        assertEquals("§aSet Target's food from 100 to 0.", target.nextMessage());
        assertNull(target.nextMessage());
    }

    @Test void husbandryRejectsUnauthorizedSendersAndMalformedSpawnArguments() {
        player.setOp(false);
        run("husbandry");
        assertEquals(List.of("§cNo permission."), messages());
        player.setOp(true);
        run("husbandry");
        assertEquals(2, messages().size());
        run("husbandry", "unsupported");
        assertEquals(2, messages().size());
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("cooking.admin")).thenReturn(true);
        run(console, "husbandry", "spawn", "cow");
        verify(console).sendMessage("§cOnly players can spawn animals.");
        run("husbandry", "spawn");
        assertTrue(messages().getFirst().contains("Usage:"));
        run("husbandry", "spawn", "not-an-entity");
        assertEquals(List.of("§cUnknown entity type."), messages());
        MockedStatic<HusbandryConfig> config = staticMock(HusbandryConfig.class);
        run("husbandry", "spawn", "COW");
        assertEquals(List.of("§cThat type is not a husbandry species."), messages());
        config.when(() -> HusbandryConfig.species(EntityType.COW)).thenReturn(mock(HusbandrySpecies.class));
        run("husbandry", "spawn", "cow", "bad");
        assertEquals(List.of("§cGenetics must be a number."), messages());
        run("husbandry", "spawn", "cow", "2", "bad");
        assertEquals(List.of("§cCare must be a number."), messages());
    }

    @Test void husbandrySpawnUsesDefaultsAndReportsClampedGeneticsAndCare() {
        MockedStatic<HusbandryConfig> config = staticMock(HusbandryConfig.class);
        config.when(() -> HusbandryConfig.species(EntityType.COW)).thenReturn(mock(HusbandrySpecies.class));
        config.when(HusbandryConfig::initialGeneticMax).thenReturn(20);
        config.when(HusbandryConfig::maxGenetics).thenReturn(1000);
        config.when(HusbandryConfig::careMax).thenReturn(200);
        MockedStatic<HusbandrySpawner> spawner = staticMock(HusbandrySpawner.class);
        run("husbandry", "spawn", "cow");
        spawner.verify(() -> HusbandrySpawner.spawn(player, EntityType.COW, 20, 0));
        assertEquals(List.of("§cFailed to spawn animal."), messages());
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getName()).thenReturn("Bess");
        spawner.when(() -> HusbandrySpawner.spawn(player, EntityType.COW, 2000, -2)).thenReturn(cow);
        run("husbandry", "spawn", "cow", "2000", "-2");
        assertEquals(List.of("§aSpawned Bess genetics 1000 care 0."), messages());
    }

    @Test void husbandrySaveRequiresRepositoryAndFlushesOnlyLoadedAnimals() {
        Cooking.plugin = null;
        run("husbandry", "save");
        assertEquals(List.of("§cHusbandry database is not open."), messages());
        Cooking.plugin = plugin;
        run("husbandry", "save");
        assertEquals(List.of("§cHusbandry database is not open."), messages());
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(plugin.getHusbandryRepository()).thenReturn(repository);
        MockedStatic<HusbandryEntities> entities = staticMock(HusbandryEntities.class);
        entities.when(HusbandryEntities::snapshotLoaded).thenReturn(List.of());
        run("husbandry", "save");
        verify(repository, never()).upsertAnimals(anyCollection());
        assertEquals(List.of("§aSaved 0 loaded animals."), messages());
        List<HusbandryAnimal> loaded = List.of(mock(HusbandryAnimal.class));
        entities.when(HusbandryEntities::snapshotLoaded).thenReturn(loaded);
        run("husbandry", "save");
        verify(repository).upsertAnimals(loaded);
        verify(repository, times(2)).checkpointWal(false);
        assertEquals(List.of("§aSaved 1 loaded animals."), messages());
    }

    @Test void buildingItemsPreservesTheCompleteArgumentString() {
        MockedStatic<ItemBuilder> builder = staticMock(ItemBuilder.class);
        run("builditem");
        assertTrue(messages().getFirst().contains("Usage:"));
        builder.verifyNoInteractions();
        run("builditem", "carrot", "quality=3");
        builder.verify(() -> ItemBuilder.buildFromString(player, "carrot quality=3", null));
        assertEquals(List.of("§aGenerated item(s) from string!"), messages());
    }

    @Test void nameTestsValidateParsingAndPassOnlyTheOptionalColourAsAnExtra() {
        MockedStatic<FoodParser> parser = staticMock(FoodParser.class);
        MockedStatic<NameComposer> names = staticMock(NameComposer.class);
        run("nametest");
        assertTrue(messages().getFirst().contains("Usage:"));
        run("nametest", "missing");
        assertEquals(List.of("§cFailed to parse food string."), messages());
        parser.when(() -> FoodParser.parse("empty")).thenReturn(new FoodParser.Result());
        run("nametest", "empty");
        assertEquals(List.of("§cFailed to parse food string."), messages());
        FoodItem food = food("soup");
        parser.when(() -> FoodParser.parse("soup origin=Carrot")).thenReturn(parsed(food));
        names.when(() -> NameComposer.compose(food, Map.of("colour", "#aabbcc"))).thenReturn("Carrot Soup");
        run("nametest", "soup", "origin=Carrot", "#aabbcc");
        assertEquals(List.of("§aComposed name: §fCarrot Soup"), messages());
        parser.when(() -> FoodParser.parse("soup")).thenReturn(parsed(food));
        names.when(() -> NameComposer.compose(food, Map.of())).thenReturn("Soup");
        run("nametest", "soup");
        assertEquals(List.of("§aComposed name: §fSoup"), messages());
    }

    @Test void qualityTestsValidateInputsAndInvokeTheRequestedResolver() {
        MockedStatic<FoodParser> parser = staticMock(FoodParser.class);
        run("qualitytest");
        assertEquals(2, messages().size());
        run("qualitytest", "pickup", "missing");
        assertEquals(List.of("§cFailed to parse food string."), messages());
        parser.when(() -> FoodParser.parse("empty")).thenReturn(new FoodParser.Result());
        run("qualitytest", "pickup", "empty");
        assertEquals(List.of("§cFailed to parse food string."), messages());
        FoodItem food = food("carrot");
        parser.when(() -> FoodParser.parse("carrot extra")).thenReturn(parsed(food));
        parser.when(() -> FoodParser.parse("carrot")).thenReturn(parsed(food));
        MockedStatic<OriginQualityResolver> origins = staticMock(OriginQualityResolver.class);
        origins.when(() -> OriginQualityResolver.resolve(player, food)).thenReturn(4);
        run("qualitytest", "pickup", "carrot", "extra");
        assertEquals(List.of("§aPickup quality: §f4"), messages());
        MockedStatic<CompositionQualityResolver> compositions = staticMock(CompositionQualityResolver.class);
        compositions.when(() -> CompositionQualityResolver.resolve(player, List.of(food), CompositionContext.CHURN)).thenReturn(3);
        compositions.when(() -> CompositionQualityResolver.resolve(player, List.of(food), CompositionContext.CUTTING_BOARD)).thenReturn(2);
        run("qualitytest", "compose", "carrot", "churn");
        assertEquals(List.of("§aComposition quality (CHURN): §f3"), messages());
        run("qualitytest", "compose", "carrot", "extra");
        compositions.verify(() -> CompositionQualityResolver.resolve(player, List.of(food), CompositionContext.CUTTING_BOARD));
        messages();
        run("qualitytest", "compose", "carrot");
        compositions.verify(() -> CompositionQualityResolver.resolve(player, List.of(food), CompositionContext.CUTTING_BOARD), times(2));
        assertEquals(List.of("§aComposition quality (CUTTING_BOARD): §f2"), messages());
        run("qualitytest", "unknown", "carrot");
        assertTrue(messages().getFirst().contains("Usage:"));
    }

    @Test void compositionContextsRemainUsableUnderATurkishServerLocale() {
        FoodItem food = food("carrot");
        MockedStatic<FoodParser> parser = staticMock(FoodParser.class);
        parser.when(() -> FoodParser.parse("carrot")).thenReturn(parsed(food));
        MockedStatic<CompositionQualityResolver> compositions = staticMock(CompositionQualityResolver.class);
        compositions.when(() -> CompositionQualityResolver.compose(player, List.of(food), CompositionContext.MIXING_BOWL))
                .thenReturn(new CompositionResult(2, 3, Map.of(), List.of(food), List.of(), List.of(), IngredientLineage.empty()));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            run("qualitytest", "compose", "carrot", "mixing_bowl");
            compositions.verify(() -> CompositionQualityResolver.resolve(player, List.of(food), CompositionContext.MIXING_BOWL));
            run("qualitytest", "compose2", "carrot", "mixing_bowl");
            compositions.verify(() -> CompositionQualityResolver.compose(player, List.of(food), CompositionContext.MIXING_BOWL));
        } finally { Locale.setDefault(previous); }
    }

    @Test void multiFoodCompositionRejectsEmptyOrUnparseableInputs() {
        MockedStatic<FoodParser> parser = staticMock(FoodParser.class);
        run("qualitytest", "compose2", "|");
        assertTrue(messages().getFirst().contains("Usage:"));
        run("qualitytest", "compose2", "missing");
        assertEquals(List.of("§cFailed to parse food string: missing"), messages());
        parser.when(() -> FoodParser.parse("empty")).thenReturn(new FoodParser.Result());
        run("qualitytest", "compose2", "empty");
        assertEquals(List.of("§cFailed to parse food string: empty"), messages());
    }

    @Test void multiFoodCompositionDisplaysRolesLineageAndFreshness() {
        FoodItem carrot = food("carrot");
        FoodItem beef = food("beef");
        FoodItem salt = food("salt");
        MockedStatic<FoodParser> parser = staticMock(FoodParser.class);
        parser.when(() -> FoodParser.parse("carrot")).thenReturn(parsed(carrot));
        parser.when(() -> FoodParser.parse("beef")).thenReturn(parsed(beef));
        parser.when(() -> FoodParser.parse("salt")).thenReturn(parsed(salt));
        IngredientLineage lineage = IngredientLineage.ofMain("Carrot").withMain("Beef").withExtra("Salt");
        CompositionResult result = new CompositionResult(3, 4, Map.of("freshness", 8, "cooked", 2),
                List.of(carrot, beef), List.of(salt), List.of(), lineage);
        MockedStatic<CompositionQualityResolver> compositions = staticMock(CompositionQualityResolver.class);
        compositions.when(() -> CompositionQualityResolver.compose(player, List.of(carrot, beef, salt), CompositionContext.BAKING)).thenReturn(result);
        run("qualitytest", "compose2", "carrot|", "|beef|salt", "baking");
        List<String> output = messages();
        assertTrue(output.contains("§7Baseline: §f3 §7→ Final: §f4"));
        assertTrue(output.contains("§7Mains (2): §fcarrot(q=3, cat=ingredient), beef(q=3, cat=ingredient)"));
        assertTrue(output.contains("§7Neutral (0): §f-"));
        assertTrue(output.contains("§7Lineage mains: §fCarrot, Beef"));
        assertTrue(output.stream().anyMatch(s -> s.contains("freshness=8") && s.contains("cooked=2")));
        parser.when(() -> FoodParser.parse("carrot suffix")).thenReturn(parsed(carrot));
        compositions.when(() -> CompositionQualityResolver.compose(player, List.of(carrot), CompositionContext.CUTTING_BOARD))
                .thenReturn(new CompositionResult(2, 2, Map.of(), List.of(carrot), List.of(), List.of(), IngredientLineage.empty()));
        run("qualitytest", "compose2", "carrot", "suffix");
        assertTrue(messages().contains("§7Lineage mains: §f-"));
    }

    @Test void previewValidatesTargetArgumentsAndNearbyBowl() {
        run("preview");
        assertEquals(2, messages().size());
        run("preview", "unsupported");
        assertTrue(messages().getFirst().contains("Unknown preview target"));
        run("preview", "mixing_bowl");
        assertTrue(messages().getFirst().contains("Usage:"));
        run("preview", "mixing_bowl", "all");
        assertTrue(messages().getFirst().contains("No mixing bowl"));
    }

    @Test void previewSelectsTheNearestUsableBowlAndDrivesOnlyTheRequestedLayers() {
        MockedStatic<FurnitureCache> cache = staticMock(FurnitureCache.class);
        Furniture far = furniture("far", 5);
        Furniture carried = furniture("carried", 0.25); when(carried.isCarried()).thenReturn(true);
        Furniture ordinary = furniture("table", 0.5);
        Furniture missingType = furniture("missing", 0.7); when(missingType.getType()).thenReturn(null);
        Furniture bowl = furniture("bowl", 1);
        for (Furniture f : List.of(far, carried, missingType, bowl)) cache.when(() -> FurnitureCache.isMixingBowl(f)).thenReturn(true);
        MockedStatic<MixingBowlDisplay> display = staticMock(MixingBowlDisplay.class);
        run("preview", "mixing_bowl", "unknown");
        assertTrue(messages().getFirst().contains("Unknown slot"));
        run("preview", "mixing_bowl", "flour");
        assertTrue(messages().getFirst().contains("No display model"));
        for (String slot : List.of("flour", "water", "yeast", "dough")) {
            display.when(() -> MixingBowlDisplay.showLayer(bowl, slot)).thenReturn(true);
            run("preview", "mixing_bowl", slot);
            assertTrue(messages().getFirst().contains("Previewing"));
        }
        display.clearInvocations();
        run("preview", "mixing_bowl", "all");
        for (String slot : List.of("flour", "water", "yeast")) display.verify(() -> MixingBowlDisplay.showLayer(bowl, slot));
        messages();
        run("preview", "mixing_bowl", "clear");
        for (String slot : List.of("flour", "water", "yeast", "dough")) display.verify(() -> MixingBowlDisplay.clearLayer(bowl, slot));
        assertTrue(messages().getFirst().contains("Cleared mixing bowl"));
        verify(ordinary, never()).getLoc();
    }

    @Test void furnitureInOtherWorldsDoesNotBreakHeatOrPreviewSelection() {
        Furniture other = furniture("other-world", 0);
        when(other.getLoc()).thenReturn(new Location(server.addSimpleWorld("other"), 0, 0, 0));
        Furniture near = furniture("near", 1);
        MockedStatic<HeatSources> heat = staticMock(HeatSources.class);
        assertDoesNotThrow(() -> run("heat"));
        assertTrue(messages().contains("§7Furniture: §fnear"));
        MockedStatic<FurnitureCache> cache = staticMock(FurnitureCache.class);
        cache.when(() -> FurnitureCache.isMixingBowl(other)).thenReturn(true);
        cache.when(() -> FurnitureCache.isMixingBowl(near)).thenReturn(true);
        MockedStatic<MixingBowlDisplay> display = staticMock(MixingBowlDisplay.class);
        assertDoesNotThrow(() -> run("preview", "mixing_bowl", "clear"));
        display.verify(() -> MixingBowlDisplay.clearLayer(near, "flour"));
        heat.verify(() -> HeatSources.isSource(near), times(2));
    }

    @Test void heatExplainsMissingFurnitureAndSourceConsumerStatus() {
        MockedStatic<HeatSources> heat = staticMock(HeatSources.class);
        run("heat");
        assertTrue(messages().getFirst().contains("No furniture"));
        Furniture far = furniture("far", 5);
        Furniture carried = furniture("carried", 0.2); when(carried.isCarried()).thenReturn(true);
        Furniture attached = furniture("attached", 0.3); when(attached.isAttached()).thenReturn(true);
        Furniture stove = furniture("stove", 1);
        heat.when(() -> HeatSources.isSource(stove)).thenReturn(true);
        heat.when(() -> HeatSources.hasHeat(stove)).thenReturn(true);
        heat.when(() -> HeatSources.isConsumer(stove)).thenReturn(true);
        heat.when(() -> HeatSources.findSource(stove)).thenReturn(Optional.empty());
        run("heat");
        List<String> output = messages();
        assertTrue(output.contains("§7hasHeat: §ftrue"));
        assertTrue(output.contains("§7findSource: §fnone"));
        heat.when(() -> HeatSources.findSource(stove)).thenReturn(Optional.of(far));
        run("heat");
        assertTrue(messages().stream().anyMatch(s -> s.contains("findSource: §ffar (")));
    }

    @Test void heatReportsNestedBakeTimersAndSkipsEmptyOrNonFoodSlots() {
        Furniture oven = furniture("oven", 1);
        Furniture tray = mock(Furniture.class); when(tray.getId()).thenReturn("bread_tray");
        PlacedFurnitureSlot empty = mock(PlacedFurnitureSlot.class);
        PlacedFurnitureSlot decoration = mock(PlacedFurnitureSlot.class); when(decoration.getNested()).thenReturn(mock(Furniture.class));
        PlacedFurnitureSlot nested = mock(PlacedFurnitureSlot.class); when(nested.getNested()).thenReturn(tray);
        Map<String, PlacedFurnitureSlot> nestedSlots = new LinkedHashMap<>();
        nestedSlots.put("empty", empty); nestedSlots.put("decoration", decoration); nestedSlots.put("tray", nested);
        when(oven.getActiveFurnitureSlots()).thenReturn(nestedSlots);
        MockedStatic<HeatSources> heat = staticMock(HeatSources.class);
        heat.when(() -> HeatSources.isConsumer(oven)).thenReturn(true);
        heat.when(() -> HeatSources.findSource(oven)).thenReturn(Optional.empty());
        List<String> slots = List.of("missing", "unavailable", "empty", "air", "stone", "raw", "cooked");
        BakingTrayRecipe recipe = new BakingTrayRecipe("bread", "bread_tray", Map.of("loaves", slots), null, new BakingTrayBake(60, 120, true));
        MockedStatic<BakingTrayRegistry> registry = staticMock(BakingTrayRegistry.class);
        registry.when(() -> BakingTrayRegistry.isTray(tray)).thenReturn(true);
        registry.when(() -> BakingTrayRegistry.getByFurniture(tray)).thenReturn(recipe);
        MockedStatic<FoodItem> foodItems = staticMock(FoodItem.class);
        MockedStatic<BakingTrayState> state = staticMock(BakingTrayState.class);
        for (String id : slots.subList(1, slots.size())) when(tray.hasActiveSlot(id)).thenReturn(true);
        when(tray.getActiveSlot("unavailable")).thenReturn(Optional.empty());
        place(tray, "empty", null);
        place(tray, "air", new ItemStack(Material.AIR));
        place(tray, "stone", new ItemStack(Material.STONE));
        ItemStack raw = new ItemStack(Material.WHEAT); place(tray, "raw", raw);
        ItemStack cooked = new ItemStack(Material.BREAD); place(tray, "cooked", cooked);
        FoodItem dough = food("dough");
        foodItems.when(() -> FoodItem.fromItem(raw)).thenReturn(dough);
        FoodItem baked = food("bread");
        TagTrack track = mock(TagTrack.class); when(track.getValue()).thenReturn(2); when(baked.getTagTrack("cooked")).thenReturn(track);
        foodItems.when(() -> FoodItem.fromItem(cooked)).thenReturn(baked);
        state.when(() -> BakingTrayState.getSlotElapsed(tray, "raw")).thenReturn(20);
        run("heat");
        List<String> output = messages();
        assertTrue(output.contains("§7recipe: §fbread"));
        assertTrue(output.contains("§7cook-seconds: §f60"));
        assertTrue(output.contains("§7burn-seconds: §f120"));
        assertTrue(output.contains("§7raw: §felapsed=20 cooked=-1"));
        assertTrue(output.contains("§7cooked: §felapsed=0 cooked=2"));
    }

    private void place(Furniture tray, String id, ItemStack item) {
        PlacedSlot slot = mock(PlacedSlot.class);
        when(slot.getCurrentItem()).thenReturn(item);
        when(tray.getActiveSlot(id)).thenReturn(Optional.of(slot));
    }
    private Furniture furniture(String id, double distance) {
        Furniture value = mock(Furniture.class);
        UUID uuid = UUID.randomUUID();
        when(value.getId()).thenReturn(id);
        when(value.getEntityId()).thenReturn(uuid);
        when(value.getLoc()).thenReturn(player.getLocation().clone().add(distance, 0, 0));
        when(value.getType()).thenReturn(mock(FurnitureType.class));
        when(value.getActiveFurnitureSlots()).thenReturn(Map.of());
        furniture.put(uuid, value);
        return value;
    }
    private FoodItem food(String id) {
        FoodItem value = mock(FoodItem.class);
        when(value.getId()).thenReturn(id);
        when(value.getQualityMin()).thenReturn(3);
        when(value.getCategory()).thenReturn("ingredient");
        return value;
    }
    private FoodParser.Result parsed(FoodItem item) {
        FoodParser.Result result = new FoodParser.Result(); result.template = item; return result;
    }
    private <T> MockedStatic<T> staticMock(Class<T> type) {
        MockedStatic<T> value = mockStatic(type); statics.add(value); return value;
    }
    private void run(String... args) { run(player, args); }
    private void run(CommandSender sender, String... args) { assertTrue(commands.onCommand(sender, command, "cooking", args)); }
    private List<String> tabs(String... args) { return commands.onTabComplete(player, command, "cooking", args); }
    private List<String> messages() {
        List<String> out = new ArrayList<>(); String value;
        while ((value = player.nextMessage()) != null) out.add(value);
        return out;
    }
}
