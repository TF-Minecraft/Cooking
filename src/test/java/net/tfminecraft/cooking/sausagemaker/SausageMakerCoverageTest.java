package net.tfminecraft.cooking.sausagemaker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ItemDisplayMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.milling.MillingStoneAnimation;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

public class SausageMakerCoverageTest {
    Workshop env;
    Station station;
    SausageMakerHandler handler;
    @BeforeEach void open() { env = new Workshop(); station = env.station("sausage_maker"); handler = new SausageMakerHandler(); }
    @AfterEach void close() { env.close(); }

    void fill() {
        for (int n = 1; n <= 3; n++) {
            FoodItem food = env.food("meat_" + n, "Beef", n + 1);
            food.setBaseFood(n); food.setBaseNutrition(n * 2);
            station.place("meat_" + n, env.stack(food, Material.BEEF, 1));
        }
    }

    @Test
    void meatRulesAndPaperCasingRejectUnrelatedAndDisguisedItems() {
        assertFalse(SausageMeatRules.isMeat(null));
        assertFalse(SausageMeatRules.isMeat(env.food(null, "Beef", 2)));
        assertTrue(SausageMeatRules.isMeat(env.food("ROAST", "Beef", 2)));
        assertTrue(SausageMeatRules.isMeat(env.food("Meat_Cut", "Beef", 2)));
        assertFalse(SausageMeatRules.isMeat(env.food("soup", "Mixed", 2)));
        assertFalse(CasingPaper.isCasing(null));
        ItemStack usedPaper = new ItemStack(Material.PAPER); usedPaper.setAmount(0);
        assertFalse(CasingPaper.isCasing(usedPaper));
        assertTrue(CasingPaper.isCasing(new ItemStack(Material.PAPER, 2)));
        assertFalse(CasingPaper.isCasing(env.stack(env.food("soup", "Mixed", 2), Material.PAPER, 1)));
        ItemStack custom = new ItemStack(Material.PAPER);
        var meta = custom.getItemMeta(); meta.setDisplayName("Masher"); custom.setItemMeta(meta);
        assertFalse(CasingPaper.isCasing(custom));
    }

    @Test
    void meatSlotAdmissionLeavesOtherFurnitureAndDecorationsAlone() {
        var other = env.station("chair").add("meat_1", new ItemStack(Material.STONE));
        handler.onMeatAdd(other); assertFalse(other.isCancelled());
        var decor = station.add("decor", new ItemStack(Material.STONE));
        handler.onMeatAdd(decor); assertFalse(decor.isCancelled());
        var rejected = station.add("meat_2", new ItemStack(Material.STONE));
        handler.onMeatAdd(rejected); assertTrue(rejected.isCancelled());
        assertTrue(env.messages.contains("§cOnly meat can go in the sausage maker."));
        var meat = station.add("meat_3", env.stack(env.food("roast", "Pork", 3), Material.PORKCHOP, 1));
        handler.onMeatAdd(meat); assertFalse(meat.isCancelled());
    }

    @Test
    void meatInsertionFillsFirstEmptySlotConsumesOneAndRejectsAFullMachine() {
        var outside = env.station("chair").interact(); handler.onInteract(outside); assertFalse(outside.isCancelled());
        ItemStack hand = env.hold(env.stack(env.food("roast", "Pork", 4), Material.PORKCHOP, 4));
        station.place("meat_1", null);
        for (int n = 1; n <= 3; n++) {
            var event = station.interact(); handler.onInteract(event);
            assertTrue(event.isCancelled());
            assertEquals(1, station.active.get("meat_" + n).getCurrentItem().getAmount());
            assertEquals(4 - n, hand.getAmount());
        }
        handler.onInteract(station.interact());
        assertEquals(1, hand.getAmount());
        assertTrue(env.messages.contains("§cThe sausage maker is full."));
        verify(env.manager, times(3)).markDirty(station.furniture);
    }

    @Test
    void emptyHandTakesTheLastMeatAndEmptyMachinesDoNothing() {
        var empty = station.interact(); handler.onInteract(empty); assertFalse(empty.isCancelled());
        fill();
        ItemStack expected = station.active.get("meat_3").getCurrentItem();
        var take = station.interact(); handler.onInteract(take);
        assertTrue(take.isCancelled());
        assertFalse(station.active.containsKey("meat_3"));
        assertEquals(expected, env.player.getInventory().getItemInMainHand());
        assertEquals(2, station.active.size());
        verify(env.manager).markDirty(station.furniture);
        env.hold(null);
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(eq(env.player), any())).thenAnswer(inv -> inv.getArgument(1));
            handler.onInteract(station.interact());
        }
        assertEquals(1, env.dropped.size());
        assertEquals("meat_2", env.resolve(env.dropped.get(0)).getId());
    }

    @Test
    void unsupportedFoodAndMissingMeatOrCasingAreRejectedWithoutConsumption() {
        ItemStack vegetable = env.hold(env.stack(env.food("vegetable", "Carrot", 3), Material.CARROT, 2));
        var wrongFood = station.interact(); handler.onInteract(wrongFood);
        assertTrue(wrongFood.isCancelled()); assertEquals(2, vegetable.getAmount());
        env.hold(new ItemStack(Material.STICK)); handler.onInteract(station.interact());
        assertTrue(env.messages.contains("§cNeed 3 meats."));
        fill(); station.place("meat_2", new ItemStack(Material.STONE));
        handler.onInteract(station.interact());
        assertEquals(3, station.active.size());
        fill(); handler.onInteract(station.interact());
        assertTrue(env.messages.contains("§cHold paper to casing."));
        assertEquals(3, station.active.size());
        assertTrue(env.outputs().isEmpty());
    }

    @Test
    void crankWaitsForAnimationThenConsumesOneCasingAndComposesTheThreeMeats() {
        fill();
        ItemDisplayMock parent = env.display(station.id);
        Transformation original = parent.getTransformation();
        ItemStack casing = env.hold(new ItemStack(Material.PAPER, 2));
        assertTrue(CasingPaper.isCasing(casing), casing.toString());
        var crank = station.interact(); handler.onInteract(crank);
        assertTrue(crank.isCancelled()); assertTrue(SausageMakerAnimation.isAnimating(station.furniture), env.messages.toString());
        assertEquals(2, casing.getAmount()); assertEquals(3, station.active.size());
        var repeat = station.interact(); handler.onInteract(repeat);
        assertTrue(repeat.isCancelled()); assertTrue(env.messages.contains("§cWait…"));
        env.server.getScheduler().performTicks(4);
        assertNotEquals(original.getLeftRotation(), parent.getTransformation().getLeftRotation());
        assertEquals(original.getTranslation(), parent.getTransformation().getTranslation());
        env.server.getScheduler().performTicks(6);
        assertEquals(original, parent.getTransformation());
        assertFalse(SausageMakerAnimation.isAnimating(station.furniture));
        assertTrue(station.active.isEmpty());
        assertEquals(1, casing.getAmount());
        assertEquals(1, env.outputs().size());
        assertEquals("sausage_chain", env.lastBuilt.getId());
        assertEquals("Mixed", env.lastBuilt.getOrigin());
        assertEquals("meat", env.lastBuilt.getCategory());
        assertEquals(6, env.lastBuilt.getBaseFood());
        assertEquals(4, env.lastBuilt.getBaseNutrition());
        assertEquals(0, env.lastBuilt.getTagTrack("cooked").getValue());
        env.composer.verify(() -> CompositionQualityResolver.compose(eq(env.player), argThat(items -> items.size() == 3), eq(CompositionContext.SAUSAGE_MAKER)));
        verify(env.manager).markDirty(station.furniture);
    }

    @Test
    void offhandCasingAndRejectedDeliveryDropTheChainOncePerCooldown() {
        ItemCache.sausageMakerCooldownTicks = 1200;
        fill();
        env.hold(new ItemStack(Material.STICK));
        env.player.getInventory().setItemInOffHand(new ItemStack(Material.PAPER, 2));
        try (MockedStatic<InventoryAdder> delivery = mockStatic(InventoryAdder.class)) {
            delivery.when(() -> InventoryAdder.addItem(eq(env.player), any())).thenAnswer(inv -> inv.getArgument(1));
            handler.onInteract(station.interact());
            assertEquals(1, env.player.getInventory().getItemInOffHand().getAmount());
            assertEquals(1, env.dropped.size());
            assertEquals("sausage_chain", env.resolve(env.dropped.get(0)).getId());
            assertTrue(station.active.isEmpty());
            fill();
            handler.onInteract(station.interact());
            assertEquals(1, env.dropped.size(), "A second crank during the cooldown cannot make another chain");
            assertEquals(3, station.active.size());
            handler.onBreak(new FurnitureBreakEvent(station.furniture, env.player));
            handler.onBreak(new FurnitureBreakEvent(env.station("chair").furniture, env.player));
            handler.onInteract(station.interact());
            assertEquals(2, env.dropped.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"offline", "removed-meat", "null-meat", "changed-meat", "removed-casing", "missing-template", "null-build", "air-build"})
    void completionRechecksPlayerIngredientsCasingAndOutputTemplate(String failure) {
        fill(); env.display(station.id);
        ItemStack casing = env.hold(new ItemStack(Material.PAPER, 2));
        handler.onInteract(station.interact());
        ItemStack air = new ItemStack(Material.AIR);
        switch (failure) {
            case "offline" -> when(env.player.isOnline()).thenReturn(false);
            case "removed-meat" -> station.active.remove("meat_2");
            case "null-meat" -> station.place("meat_2", null);
            case "changed-meat" -> station.place("meat_2", new ItemStack(Material.STONE));
            case "removed-casing" -> env.hold(new ItemStack(Material.STICK));
            case "missing-template" -> env.templates.remove("sausage_chain");
            case "null-build" -> env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenReturn(null);
            case "air-build" -> env.builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenReturn(air);
            default -> fail("Unknown completion failure");
        }
        env.server.getScheduler().performTicks(10);
        assertTrue(env.outputs().isEmpty()); assertTrue(env.dropped.isEmpty());
        assertEquals(2, casing.getAmount());
        assertTrue(station.active.containsKey("meat_1"));
        assertTrue(station.active.containsKey("meat_3"));
        assertFalse(SausageMakerAnimation.isAnimating(station.furniture));
    }

    @Test
    void batchTotalsIgnoreMissingMeatsAndKeepTemplateNutritionForAnEmptyBatch() {
        FoodItem chain = env.food("sausage_chain", "Mixed", 2); chain.setBaseNutrition(9);
        SausageItems.applyBatchTotals(chain, null);
        assertEquals(0, chain.getBaseFood()); assertEquals(9, chain.getBaseNutrition());
        FoodItem beef = env.food("meat_beef", "Beef", 2); beef.setBaseFood(5); beef.setBaseNutrition(3);
        FoodItem pork = env.food("meat_pork", "Pork", 2); pork.setBaseFood(7); pork.setBaseNutrition(5);
        SausageItems.applyBatchTotals(chain, Arrays.asList(beef, null, pork));
        assertEquals(12, chain.getBaseFood()); assertEquals(4, chain.getBaseNutrition());
        TrackLoader.oList.clear();
        assertNotNull(SausageItems.fromMeats(env.player, List.of(beef)));
        assertFalse(env.lastBuilt.hasTagTrack("cooked"));
    }

    @Test
    void animationCompletesImmediatelyWithoutADisplayAndStopsIfItsParentDisappears() {
        assertFalse(SausageMakerAnimation.isAnimating(null));
        Furniture noId = mock(Furniture.class);
        assertFalse(SausageMakerAnimation.isAnimating(noId));
        SausageMakerAnimation.clearAnimating(null); SausageMakerAnimation.clearAnimating(noId);
        AtomicInteger completions = new AtomicInteger();
        SausageMakerAnimation.playWobble(station.furniture, completions::incrementAndGet);
        SausageMakerAnimation.playWobble(station.furniture, null);
        assertEquals(1, completions.get());
        ItemDisplayMock parent = env.display(station.id);
        station.place("meat_2", env.stack(env.food("meat_beef", "Beef", 2), Material.BEEF, 1));
        SausageMakerAnimation.playWobble(station.furniture, completions::incrementAndGet);
        env.server.getScheduler().performOneTick();
        verify(station.active.get("meat_2")).followParentTransform(parent);
        parent.remove();
        env.server.getScheduler().performTicks(10);
        assertFalse(SausageMakerAnimation.isAnimating(station.furniture));
        assertEquals(1, completions.get());
    }

    @Test
    void zeroDurationWobbleNeverWritesANonFiniteTransform() {
        ItemCache.sausageMakerDurationTicks = 0;
        ItemDisplayMock parent = env.display(station.id);
        AtomicInteger completions = new AtomicInteger();
        SausageMakerAnimation.playWobble(station.furniture, completions::incrementAndGet);
        env.server.getScheduler().performOneTick();
        Quaternionf rotation = parent.getTransformation().getLeftRotation();
        assertTrue(Float.isFinite(rotation.x) && Float.isFinite(rotation.y) && Float.isFinite(rotation.z) && Float.isFinite(rotation.w));
        env.server.getScheduler().performTicks(3);
        assertEquals(1, completions.get());
    }

    /** Shared public fixture keeps the two processor suites on real inventories, food values and scheduler ticks. */
    public static final class Workshop implements AutoCloseable {
        public final ServerMock server = MockBukkit.mock();
        public final Player player = mock(Player.class);
        public final World world = mock(World.class);
        public final FurnitureManager manager = mock(FurnitureManager.class);
        public final ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        public final List<ItemStack> dropped = new ArrayList<>();
        public final List<String> messages = new ArrayList<>();
        public final List<Station> stations = new ArrayList<>();
        public final Map<String, FoodItem> templates = new LinkedHashMap<>();
        public final MockedStatic<ItemBuilder> builder;
        public final MockedStatic<ItemCache> cache;
        public final MockedStatic<CompositionQualityResolver> composer;
        public final MockedStatic<OriginQualityResolver> originQuality;
        public FoodItem lastBuilt;
        public int lastQuality;
        final Cooking previousPlugin = Cooking.plugin;
        final List<TagTrack> previousTracks = TrackLoader.oList;
        final String previousSausage = FurnitureCache.sausageMaker;
        final String previousMill = FurnitureCache.millingStone;
        final int previousDuration = ItemCache.sausageMakerDurationTicks;
        final float previousWobble = ItemCache.sausageMakerWobbleDegrees;
        final int previousCooldown = ItemCache.sausageMakerCooldownTicks;
        final List<MockedStatic<?>> mocks = new ArrayList<>();
        final Map<String, FoodItem> foods = new HashMap<>();
        final NamespacedKey key = NamespacedKey.fromString("test:processor_food");

        public Workshop() {
            Cooking.plugin = mock(Cooking.class);
            when(Cooking.plugin.getName()).thenReturn("Cooking");
            when(Cooking.plugin.namespace()).thenReturn("cooking");
            when(Cooking.plugin.isEnabled()).thenReturn(true);
            when(player.getInventory()).thenReturn(server.addPlayer().getInventory());
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
            when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.getName()).thenReturn("processors");
            when(world.dropItemNaturally(any(Location.class), any(ItemStack.class))).thenAnswer(inv -> {
                dropped.add(((ItemStack) inv.getArgument(1)).clone()); return mock(Item.class);
            });
            doAnswer(inv -> { messages.add(inv.getArgument(0)); return null; }).when(player).sendMessage(anyString());
            FurnitureCache.sausageMaker = "sausage_maker"; FurnitureCache.millingStone = "milling_stone";
            ItemCache.sausageMakerDurationTicks = 6; ItemCache.sausageMakerWobbleDegrees = 20; ItemCache.sausageMakerCooldownTicks = 10;
            TrackLoader.oList = new ArrayList<>(); new TrackLoader().load(new File("src/main/resources/tags.yml"));
            MockedStatic<FoodItem> codec = scoped(mockStatic(FoodItem.class, CALLS_REAL_METHODS));
            codec.when(() -> FoodItem.fromItem(any())).thenAnswer(inv -> resolve(inv.getArgument(0)));
            scoped(mockStatic(FoodLoader.class)).when(() -> FoodLoader.getByString(any())).thenAnswer(inv -> templates.get(inv.getArgument(0)));
            for (String id : List.of("sausage_chain", "wheat", "flour")) templates.put(id, food(id, "Wheat", 3));
            scoped(mockStatic(TLibs.class)).when(TLibs::getItemAPI).thenReturn(api);
            when(api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> new ItemStack(Material.STONE));
            when(api.getChecker().checkItemWithPath(any(), anyString())).thenAnswer(inv -> {
                ItemStack stack = inv.getArgument(0); String path = inv.getArgument(1);
                if (stack == null || stack.getType().isAir()) return false;
                FoodItem food = resolve(stack);
                boolean vanilla = !stack.getItemMeta().hasDisplayName()
                        && stack.getItemMeta().getPersistentDataContainer().getKeys().isEmpty();
                return switch (path) {
                    case "v.paper" -> stack.getType() == Material.PAPER && vanilla;
                    case "v.wheat" -> stack.getType() == Material.WHEAT && vanilla;
                    case "food:grain" -> food != null && food.getCategory().equals("grain");
                    default -> false;
                };
            });
            InteractibleFurniture plugin = mock(InteractibleFurniture.class);
            when(plugin.getFurnitureManager()).thenReturn(manager);
            scoped(mockStatic(InteractibleFurniture.class)).when(InteractibleFurniture::getInstance).thenReturn(plugin);
            cache = scoped(mockStatic(ItemCache.class));
            cache.when(() -> ItemCache.getMixingModel("flour")).thenReturn("model.flour");
            composer = scoped(mockStatic(CompositionQualityResolver.class));
            composer.when(() -> CompositionQualityResolver.compose(any(), any(), any())).thenReturn(
                    new CompositionResult(3, 4, Map.of(), List.of(), List.of(), List.of(), IngredientLineage.empty()));
            originQuality = scoped(mockStatic(OriginQualityResolver.class));
            originQuality.when(() -> OriginQualityResolver.resolve(any(), any())).thenReturn(3);
            builder = scoped(mockStatic(ItemBuilder.class));
            builder.when(() -> ItemBuilder.buildComposedWithQuality(any(), anyInt())).thenAnswer(inv -> build(inv.getArgument(0), inv.getArgument(1)));
            builder.when(() -> ItemBuilder.buildSingleWithQuality(any(FoodItem.class), anyInt())).thenAnswer(inv -> build(inv.getArgument(0), inv.getArgument(1)));
        }
        public <T> MockedStatic<T> scoped(MockedStatic<T> mock) { mocks.add(mock); return mock; }
        ItemStack build(FoodItem food, int quality) { lastBuilt = food; lastQuality = quality; return stack(food, Material.PAPER, 1); }
        public FoodItem food(String id, String origin, int quality) {
            MemoryConfiguration config = new MemoryConfiguration(); config.set("name", id == null ? "Unknown" : id); config.set("update", false);
            FoodItem food = new FoodItem(id, config); food.setCategory("meat"); food.setOrigin(origin); food.setQualityRange(quality, quality);
            food.setModel(new FoodModel(new ItemStack(Material.CARROT)));
            return food;
        }
        public ItemStack stack(FoodItem food, Material material, int amount) {
            ItemStack item = new ItemStack(material, amount); String id = UUID.randomUUID().toString(); foods.put(id, food);
            var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id); item.setItemMeta(meta); return item;
        }
        public FoodItem resolve(ItemStack stack) {
            if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
            if (stack.getItemMeta() == null) return null;
            return foods.get(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
        }
        public ItemStack hold(ItemStack stack) { player.getInventory().setItemInMainHand(stack); return player.getInventory().getItemInMainHand(); }
        public List<ItemStack> outputs() {
            List<ItemStack> output = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                FoodItem food = resolve(stack);
                if (food != null && (food.getId().equals("sausage_chain") || food.getId().equals("flour"))) output.add(stack);
            }
            return output;
        }
        public Station station(String kind) { Station station = new Station(this, kind); stations.add(station); return station; }
        public ItemDisplayMock display(UUID id) {
            ItemDisplayMock display = new ItemDisplayMock(server, id);
            display.setTransformation(new Transformation(new Vector3f(0.1f, 0.2f, 0.3f), new Quaternionf(), new Vector3f(1), new Quaternionf()));
            server.registerEntity(display); return display;
        }
        @Override public void close() {
            for (Station station : stations) { SausageMakerAnimation.clearAnimating(station.furniture); MillingStoneAnimation.clearAnimating(station.furniture); }
            for (int n = mocks.size() - 1; n >= 0; n--) mocks.get(n).close();
            TrackLoader.oList = previousTracks; Cooking.plugin = previousPlugin;
            FurnitureCache.sausageMaker = previousSausage; FurnitureCache.millingStone = previousMill;
            ItemCache.sausageMakerDurationTicks = previousDuration; ItemCache.sausageMakerWobbleDegrees = previousWobble; ItemCache.sausageMakerCooldownTicks = previousCooldown;
            MockBukkit.unmock();
        }
    }

    public static final class Station {
        public final Furniture furniture = mock(Furniture.class);
        public final FurnitureType type = mock(FurnitureType.class);
        public final UUID id = UUID.randomUUID();
        public final Map<String, SlotDefinition> definitions = new LinkedHashMap<>();
        public final Map<String, PlacedSlot> active = new LinkedHashMap<>();
        public final Map<String, Object> variables = new LinkedHashMap<>();
        final Workshop env;
        Station(Workshop env, String kind) {
            this.env = env;
            when(furniture.getId()).thenReturn(kind); when(furniture.getEntityId()).thenReturn(id); when(furniture.getType()).thenReturn(type);
            when(furniture.getLoc()).thenReturn(new Location(env.world, 1, 64, 1)); when(furniture.getVariables()).thenReturn(variables);
            when(type.getId()).thenReturn(kind); when(type.getSlots()).thenReturn(definitions);
            when(type.getSlot(anyString())).thenAnswer(inv -> definitions.get(inv.getArgument(0)));
            when(furniture.getActiveSlots()).thenReturn(active);
            when(furniture.hasActiveSlot(anyString())).thenAnswer(inv -> active.containsKey(inv.getArgument(0)));
            when(furniture.getActiveSlot(anyString())).thenAnswer(inv -> Optional.ofNullable(active.get(inv.getArgument(0))));
            when(furniture.getOrCreatePlacedSlot(anyString())).thenAnswer(inv -> {
                String slot = inv.getArgument(0); return active.containsKey(slot) ? active.get(slot) : place(slot, null);
            });
            doAnswer(inv -> { active.remove(inv.getArgument(0)); return null; }).when(furniture).removeActiveSlot(anyString());
        }
        public SlotDefinition define(String id) {
            return definitions.computeIfAbsent(id, key -> { SlotDefinition def = mock(SlotDefinition.class); when(def.getId()).thenReturn(key); return def; });
        }
        public PlacedSlot place(String id, ItemStack stack) {
            PlacedSlot slot = mock(PlacedSlot.class); ItemStack[] value = {stack};
            when(slot.getId()).thenReturn(id);
            when(slot.getCurrentItem()).thenAnswer(inv -> value[0]);
            doAnswer(inv -> { value[0] = inv.getArgument(0); active.put(id, slot); return null; }).when(slot).setCurrentItem(any());
            doAnswer(inv -> { value[0] = inv.getArgument(0); active.put(id, slot); return null; }).when(slot).forceModel(any());
            doAnswer(inv -> { value[0] = null; active.remove(id); return null; }).when(slot).clearModel();
            active.put(id, slot); return slot;
        }
        public FurnitureInteractEvent interact() { return new FurnitureInteractEvent(env.player, furniture); }
        public FurnitureSlotItemAddEvent add(String slot, ItemStack stack) { return new FurnitureSlotItemAddEvent(env.player, furniture, define(slot), stack); }
        public ItemDisplayMock display(String slot) {
            PlacedSlot placed = active.get(slot); UUID id = UUID.randomUUID(); when(placed.getDisplayStandId()).thenReturn(id); return env.display(id);
        }
    }
}
