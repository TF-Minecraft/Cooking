package net.tfminecraft.cooking.popcorn;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.popcorn.PopcornMachineState.Stage;
import net.tfminecraft.cooking.testsupport.TestCharacters;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.PlayerActionContext;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

/** The popcorn machine with the popcorn-machines.yml shipped in the jar: load, pop, take the popcorn, break. */
@SuppressWarnings("unchecked")
class PopcornMachineCoverageTest {
    private static final NamespacedKey IA = new NamespacedKey("test", "ia");

    @TempDir Path dir;
    private ServerMock server;
    private PlayerMock player;
    private Cooking previous;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final Map<UUID, Object> entities = new HashMap<>();
    private final List<ItemDisplay> spawned = new ArrayList<>();
    private final List<ItemStack> dropped = new ArrayList<>();
    private World world;
    private PopcornMachineHandler handler;
    private PopcornMachine machine;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.isEnabled()).thenReturn(true);
        player = spy(new PlayerMock(server, "Popper"));
        server.addPlayer(player);
        doNothing().when(player).swingMainHand();
        tracks = TrackLoader.oList;
        TrackLoader.oList = new ArrayList<>();
        new TrackLoader().load(new File("src/main/resources/tags.yml"));
        foods = FoodLoader.oList;
        FoodLoader.oList = new ArrayList<>();
        food("popcorn", "#f3e3a6{prefixes}Popcorn", true);
        food("vegetable_2", "{inherit}", true);
        food("seasoning_1", "{inherit}", true);
        food("sweetener_1", "{inherit}", true);
        food("butter", "Butter", true);
        food("caramel", "Caramel", true);
        food("modelless", "Nothing", false);
        new NamingLoader().load(new File("src/main/resources/naming.yml"));

        ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        statics.add(mockStatic(TLibs.class));
        ((MockedStatic<TLibs>) statics.get(0)).when(TLibs::getItemAPI).thenReturn(api);
        when(api.getChecker().checkItemWithPath(any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1).equals(marker(inv.getArgument(0))));
        when(api.getCreator().getItemFromPath(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            if (ref.equals("ia.missing")) return new ItemStack(Material.DIRT);
            return item(Material.STICK, ref);
        });
        InteractibleFurniture plugin = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
        MockedStatic<InteractibleFurniture> furnitures = mockStatic(InteractibleFurniture.class);
        furnitures.when(InteractibleFurniture::getInstance).thenReturn(plugin);
        statics.add(furnitures);
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class))).thenAnswer(inv -> entities.get(inv.getArgument(0)));
        statics.add(bukkit);

        world = mock(World.class);
        when(world.spawn(any(Location.class), eq(ItemDisplay.class), any(Consumer.class))).thenAnswer(inv -> {
            ItemDisplay display = mock(ItemDisplay.class);
            ((Consumer<ItemDisplay>) inv.getArgument(2)).accept(display);
            spawned.add(display);
            return display;
        });
        when(world.dropItemNaturally(any(Location.class), any(ItemStack.class))).thenAnswer(inv -> {
            dropped.add(inv.getArgument(1));
            return null;
        });

        new PopcornMachineConfig().load(new File("src/main/resources/popcorn-machines.yml"));
        machine = PopcornMachineConfig.machines().get(0);
        handler = new PopcornMachineHandler();
        PopcornAnimation.random = new Random(7);
    }

    @AfterEach
    void tearDown() {
        for (int i = statics.size() - 1; i >= 0; i--) statics.get(i).close();
        new PopcornMachineConfig().load(new File(dir.toFile(), "none.yml"));
        NamingConfig.apply(List.of("sweet"), 3, 2, 2, null, null, null);
        FoodLoader.oList = foods;
        TrackLoader.oList = tracks;
        PopcornAnimation.random = new Random();
        MockBukkit.unmock();
        Cooking.plugin = previous;
    }

    // ---------------------------------------------------------------- fixtures

    private static void food(String id, String name, boolean model) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", name);
        FoodItem food = new FoodItem(id, config);
        food.addOrModifyTrack(TrackLoader.getByString("freshness"));
        if (model) food.setModel(new FoodModel(new ItemStack(Material.PAPER)));
        FoodLoader.oList.add(food);
    }

    private static ItemStack piece(String id, String category, String origin, int quality, int amount) {
        FoodItem food = new FoodItem(FoodLoader.getByString(id));
        food.setCategory(category);
        food.setOrigin(origin);
        food.setQualityRange(quality, quality);
        food.setLastUpdate(System.currentTimeMillis());
        ItemStack stack = ItemBuilder.stamp(new ItemStack(Material.PAPER, amount), food, ItemBuilder.displayName(food));
        return stack;
    }

    private static ItemStack corn(int quality, int amount) {
        return piece("vegetable_2", "vegetable", "Corn", quality, amount);
    }

    private static ItemStack salt(int amount) {
        return piece("seasoning_1", "salt", "Salt", 3, amount);
    }

    private static ItemStack caramel() {
        return piece("caramel", "condiment", "Sugar", 3, 1);
    }

    private static ItemStack item(Material material, String ref) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(IA, PersistentDataType.STRING, ref);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String marker(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(IA, PersistentDataType.STRING);
    }

    /** A furniture mock with these slots; their shown items are kept in models. */
    private final class Machine {
        final Furniture furniture = mock(Furniture.class);
        final Map<String, Object> variables = new HashMap<>();
        final Map<String, ItemStack> models = new HashMap<>();
        final Map<String, PlacedSlot> placed = new HashMap<>();
        ItemDisplay kettle;

        Machine(String id, String... slots) {
            FurnitureType type = mock(FurnitureType.class);
            when(furniture.getId()).thenReturn(id);
            when(furniture.getEntityId()).thenReturn(UUID.randomUUID());
            when(furniture.getLoc()).thenReturn(new Location(world, 0.5, 64.5, 0.5));
            when(furniture.getVariables()).thenReturn(variables);
            when(furniture.getType()).thenReturn(type);
            for (String slot : slots) {
                when(type.getSlot(slot)).thenReturn(mock(SlotDefinition.class));
                PlacedSlot placedSlot = mock(PlacedSlot.class);
                doAnswer(inv -> models.put(slot, inv.getArgument(0))).when(placedSlot).forceModel(any());
                doAnswer(inv -> models.remove(slot)).when(placedSlot).clearModel();
                placed.put(slot, placedSlot);
            }
            when(furniture.getOrCreatePlacedSlot(anyString())).thenAnswer(inv -> placed.get((String) inv.getArgument(0)));
            when(furniture.hasActiveSlot(anyString())).thenAnswer(inv -> models.containsKey((String) inv.getArgument(0)));
            when(furniture.getActiveSlot(anyString())).thenAnswer(inv -> Optional.ofNullable(placed.get((String) inv.getArgument(0))));
        }

        Machine() {
            this("popcorn_machine", "kettle", "heap", "topping_1", "topping_2");
        }

        /** Gives the kettle slot a display the animation can turn. */
        ItemDisplay withKettle() {
            UUID id = UUID.randomUUID();
            kettle = mock(ItemDisplay.class);
            Transformation[] state = {new Transformation(new Vector3f(0, 0.4f, 0), new Quaternionf(),
                    new Vector3f(1, 1, 1), new Quaternionf())};
            when(kettle.getTransformation()).thenAnswer(inv -> state[0]);
            doAnswer(inv -> { state[0] = inv.getArgument(0); return null; }).when(kettle).setTransformation(any());
            when(placed.get("kettle").getDisplayStandId()).thenReturn(id);
            entities.put(id, kettle);
            return kettle;
        }

        Stage stage() {
            return PopcornMachineState.stage(furniture);
        }

        FurnitureInteractEvent click() {
            FurnitureInteractEvent e = new FurnitureInteractEvent(player, furniture);
            handler.onInteract(e);
            return e;
        }

        FurnitureInteractEvent click(ItemStack held) {
            player.getInventory().setItemInMainHand(held);
            return click();
        }

        /** Puts n corn of these stars in, one right-click each. */
        void fill(int quality, int n) {
            click(corn(quality, n));
            for (int i = 1; i < n; i++) click();
        }

        String model(String slot) {
            return marker(models.get(slot));
        }
    }

    /** The popcorn in the player's inventory, stack by stack. */
    private List<ItemStack> popcornHeld() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            FoodItem food = stack == null ? null : FoodItem.fromItem(stack);
            if (food != null && food.getId().equals("popcorn")) out.add(stack);
        }
        return out;
    }

    private int popcornCount() {
        return popcornHeld().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private static String name(ItemStack stack) {
        return org.bukkit.ChatColor.stripColor(stack.getItemMeta().getDisplayName());
    }

    private static boolean nothing(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private MachineConfig custom(String yaml) throws Exception {
        Path file = dir.resolve("machines-" + UUID.randomUUID() + ".yml");
        Files.writeString(file, yaml);
        new PopcornMachineConfig().load(file.toFile());
        return new MachineConfig(PopcornMachineConfig.machines());
    }

    private record MachineConfig(List<PopcornMachine> machines) {}

    // ---------------------------------------------------------------- config

    @Test
    void theShippedMachineTakesSixteenCornAndTwoToppings() {
        assertEquals("popcorn_machine", machine.getId());
        assertEquals("popcorn_machine", machine.getFurniture());
        assertEquals("corn", machine.getInputName());
        assertEquals(16, machine.getMaxInput());
        assertEquals(4, machine.getYield());
        assertEquals(2, machine.getMaxToppings());
        assertEquals(List.of("caramel", "sugar", "butter", "salt"), machine.getToppings().stream().map(Topping::id).toList());
        assertEquals(160, machine.getPopTicks());
        assertEquals("ia.tfmc_cooking:popcorn_machine_kettle", machine.kettleFor(false));
        assertEquals("ia.tfmc_cooking:popcorn_machine_kettle_loaded", machine.kettleFor(true));
        assertEquals(4, machine.heapLevels());
        assertEquals(0, machine.heapLevel(0));
        assertEquals(1, machine.heapLevel(1));
        assertEquals(2, machine.heapLevel(5));
        assertEquals(4, machine.heapLevel(16));
        assertEquals("ia.tfmc_cooking:popcorn_machine_heap_3", machine.heapFor(3, List.of("topping_caramel")));
        assertEquals("ia.tfmc_cooking:popcorn_machine_heap_buttered_1", machine.heapFor(1, List.of("look_buttered")));
        assertEquals("popcorn_look", machine.getLookTrack());
        assertEquals("ia.tfmc_cooking:popcorn_machine_heap_caramel_2", machine.heapFor(2, List.of("look_caramel")));
        assertEquals(0, machine.heapHeight(0));
        assertEquals(0.5, machine.heapHeight(9));
        assertEquals(List.of("topping_1", "topping_2"), machine.getToppingSlots());
        assertTrue(machine.takes(FoodItem.fromItem(corn(3, 1))));
        assertFalse(machine.takes(FoodItem.fromItem(salt(1))));
        assertEquals("butter", machine.toppingFor(FoodItem.fromItem(piece("butter", "dairy", "Cow", 3, 1))).id());
        assertEquals("butter", machine.toppingFor(FoodItem.fromItem(piece("butter", "ingredient", "Cow", 3, 1))).id());
        assertNull(machine.toppingFor(FoodItem.fromItem(corn(3, 1))));
        assertEquals("caramel, sugar, butter or salt", PopcornMachineHandler.toppingNames(machine));

        Machine m = new Machine();
        assertSame(machine, PopcornMachineConfig.machineFor(m.furniture));
        assertNull(PopcornMachineConfig.machineFor(null));
        assertNull(PopcornMachineConfig.machineFor(new Machine(null).furniture));
        assertNull(PopcornMachineConfig.machineFor(new Machine("juicer").furniture));
    }

    @Test
    void configDefaults() throws Exception {
        List<PopcornMachine> loaded = custom("""
                version: 2
                bare: {}
                one:
                  max-toppings: 1
                  models:
                    heap-looks: [broken]
                  toppings:
                    salt:
                      item: c.salt
                    broken: nope
                """).machines();
        assertEquals(2, loaded.size());
        PopcornMachine bare = loaded.get(0);
        assertEquals("bare", bare.getFurniture());
        assertEquals(1, bare.heapLevels());
        assertEquals(0.25, bare.heapHeight(1));
        assertEquals("kettle", bare.getKettleSlot());
        assertEquals("heap", bare.getHeapSlot());
        assertTrue(bare.getToppingSlots().isEmpty());
        PopcornMachine defaults = new PopcornMachine("x", new MemoryConfiguration());
        assertEquals(0.45, defaults.getRim(), 1e-9);
        assertEquals(-0.25, defaults.getFloor(), 1e-9);
        assertEquals(0.36, defaults.getWalls(), 1e-9);
        assertEquals(160, defaults.getPopTicks());
        assertEquals("nothing else", PopcornMachineHandler.toppingNames(bare));
        PopcornMachine one = loaded.get(1);
        assertEquals("salt", PopcornMachineHandler.toppingNames(one));
        assertEquals("salt", one.getToppings().get(0).track(), "the track defaults to the topping's key");
    }

    @Test
    void stateSurvivesAsFoodStrings() {
        Machine m = new Machine();
        assertEquals(Stage.EMPTY, m.stage());
        m.variables.put(PopcornMachineState.VAR_STAGE, "BOGUS");
        assertEquals(Stage.EMPTY, m.stage());
        m.variables.put(PopcornMachineState.VAR_INPUT, 3);
        assertTrue(PopcornMachineState.input(m.furniture).isEmpty());
        m.variables.put(PopcornMachineState.VAR_INPUT, "not food||also not food");
        assertTrue(PopcornMachineState.foods(PopcornMachineState.input(m.furniture)).isEmpty());
        PopcornMachineState.clear(m.furniture);
        assertTrue(m.variables.isEmpty());
    }

    // ---------------------------------------------------------------- loading

    @Test
    void cornAndToppingsGoIn() {
        Machine m = new Machine();
        m.click(corn(4, 20));
        assertEquals(19, player.getInventory().getItemInMainHand().getAmount(), "one corn per click");
        assertNull(player.nextMessage(), "silently");
        assertEquals(Stage.LOADING, m.stage());
        assertEquals("ia.tfmc_cooking:popcorn_machine_kettle_loaded", m.model("kettle"));
        for (int i = 2; i <= 16; i++) m.click();
        assertEquals(4, player.getInventory().getItemInMainHand().getAmount());
        m.click(corn(4, 1));
        assertEquals("§cIt's full: it takes 16 corn.", player.nextMessage());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount(), "nothing taken when it's full");
        assertEquals(1, PopcornMachineState.input(m.furniture).size(), "sixteen clicks of the same corn make one stack");

        m.click(salt(3));
        assertNull(player.nextMessage());
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertNotNull(m.models.get("topping_1"));
        m.click();
        assertEquals("§cIt already has salt.", player.nextMessage());
        m.click(caramel());
        assertNull(player.nextMessage());
        assertNotNull(m.models.get("topping_2"));
        m.click(piece("sweetener_1", "sweetener", "Sugar", 3, 1));
        assertEquals("§cIt already has 2 toppings.", player.nextMessage());

        m.click(new ItemStack(Material.STONE));
        assertEquals("§cOnly corn and caramel, sugar, butter or salt go in.", player.nextMessage());

        ItemStack rotten = corn(5, 1);
        FoodItem food = FoodItem.fromItem(rotten);
        food.getTagTrack("freshness").setValue(200000);
        m.click(ItemBuilder.stamp(rotten, food, "Corn"));
        assertEquals("§cThat corn has gone off.", player.nextMessage());

        Machine elsewhere = new Machine("juicer");
        FurnitureInteractEvent ignored = elsewhere.click();
        assertFalse(ignored.isCancelled());
    }

    @Test
    void nothingStartsEmptyAndNoCharacterIsRefused() {
        Machine m = new Machine();
        FurnitureInteractEvent e = m.click(null);
        assertTrue(e.isCancelled());
        assertNull(player.nextMessage(), "an empty machine does nothing, silently");
        assertEquals(Stage.EMPTY, m.stage());
        try (TestCharacters characters = new TestCharacters(MockBukkit.createMockPlugin("CharacterProvider"))) {
            m.fill(3, 2);
            assertEquals(PlayerActionContext.NO_CHARACTER_MESSAGE, player.nextMessage());
            assertEquals(Stage.EMPTY, m.stage());
        }
    }

    // ---------------------------------------------------------------- popping and taking

    @Test
    void itPopsThenGivesFourPopcornPerCorn() {
        Machine m = new Machine();
        ItemDisplay kettle = m.withKettle();
        m.fill(4, 10);
        m.fill(2, 6);
        m.click(salt(1));
        m.click(caramel());
        m.click(null);
        assertEquals(Stage.RUNNING, m.stage());
        assertTrue(PopcornAnimation.isRunning(m.furniture));
        m.click();
        assertEquals("§7It's still popping.", lastMessage());

        server.getScheduler().performTicks(100);
        verify(kettle, atLeastOnce()).setTransformation(any());
        assertFalse(spawned.isEmpty(), "kernels fly");
        assertNotNull(m.model("heap"), "the heap rises while it pops");
        verify(world, atLeastOnce()).playSound(any(Location.class), eq(Sound.ENTITY_CHICKEN_EGG), anyFloat(), anyFloat());
        server.getScheduler().performTicks(80);
        assertFalse(PopcornAnimation.isRunning(m.furniture));
        assertEquals(Stage.READY, m.stage());
        assertEquals("ia.tfmc_cooking:popcorn_machine_heap_caramel_4", m.model("heap"));
        assertEquals("ia.tfmc_cooking:popcorn_machine_kettle", m.model("kettle"));
        assertNull(m.models.get("topping_1"), "the toppings have gone into the popcorn");
        for (ItemDisplay kernel : spawned) verify(kernel).remove();
        verify(world).playSound(any(Location.class), eq(Sound.BLOCK_NOTE_BLOCK_BELL), anyFloat(), anyFloat());

        // Whatever is in hand, the click takes the popcorn.
        m.click(salt(1));
        assertEquals(64, popcornCount());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount(), "the salt stays in hand");
        List<ItemStack> held = popcornHeld();
        assertEquals(2, held.size(), "four-star and two-star popcorn");
        assertEquals("Salted Caramel Popcorn", name(held.get(0)));
        assertEquals(Stage.EMPTY, m.stage());
        assertNull(m.models.get("heap"));
        assertNull(m.models.get("topping_1"));
    }

    @Test
    void theFirstToppingWithALookColoursThePopcorn() {
        ItemStack butter = piece("butter", "dairy", "Cow", 3, 1);
        Machine buttered = new Machine();
        buttered.fill(3, 4);
        buttered.click(butter.clone());
        buttered.click(caramel());
        PopcornMachineState.setStage(buttered.furniture, Stage.READY);
        PopcornMachineHandler.show(buttered.furniture, machine);
        assertEquals("ia.tfmc_cooking:popcorn_machine_heap_buttered_1", buttered.model("heap"));
        FoodItem first = FoodItem.fromItem(PopcornMachineHandler.popcorn(machine, buttered.furniture).get(0));
        assertTrue(first.getCurrentTagIds().contains("look_buttered"));
        assertFalse(first.getCurrentTagIds().contains("look_caramel"));
        assertTrue(first.hasTagTrack("topping_caramel"), "both toppings still go on");
        assertEquals("Buttered Caramel Popcorn", ItemBuilder.displayName(first).replaceAll("§.", ""));
        assertTrue(ItemBuilder.buildLore(first).stream().noneMatch(line -> line.contains("look")), "the look is hidden");

        Machine caramelFirst = new Machine();
        caramelFirst.fill(3, 4);
        caramelFirst.click(caramel());
        caramelFirst.click(butter.clone());
        FoodItem other = FoodItem.fromItem(PopcornMachineHandler.popcorn(machine, caramelFirst.furniture).get(0));
        assertTrue(other.getCurrentTagIds().contains("look_caramel"));
        assertEquals(List.of("look_caramel"), PopcornMachineHandler.looks(machine, caramelFirst.furniture));

        Machine salted = new Machine();
        salted.fill(3, 4);
        salted.click(salt(1));
        assertNull(PopcornMachineHandler.lookTopping(machine, salted.furniture), "salt gives no look");
        assertFalse(FoodItem.fromItem(PopcornMachineHandler.popcorn(machine, salted.furniture).get(0)).hasTagTrack("popcorn_look"));
    }

    @Test
    void aLookThatTagsYmlLacksIsSkipped() throws Exception {
        custom("""
                popcorn_machine:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  output: snack(type=popcorn;origin=Corn;tags=cooked.1)
                  look-track: nope
                  toppings:
                    salt: {item: c.salt, look: look_salt}
                odd_step:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  output: snack(type=popcorn;origin=Corn;tags=cooked.1)
                  toppings:
                    salt: {item: c.salt, look: look_salt}
                """);
        for (String id : List.of("popcorn_machine", "odd_step")) {
            PopcornMachine custom = PopcornMachineConfig.machines().stream().filter(x -> x.getId().equals(id)).findFirst().get();
            Machine m = new Machine(id);
            m.fill(3, 1);
            m.click(salt(1));
            assertFalse(FoodItem.fromItem(PopcornMachineHandler.popcorn(custom, m.furniture).get(0)).hasTagTrack("popcorn_look"));
        }
    }

    @Test
    void aRestartMidShowLeavesThePopcornDone() {
        Machine m = new Machine();
        m.fill(3, 4);
        PopcornMachineState.setStage(m.furniture, Stage.RUNNING);
        m.click(null);
        assertEquals(16, popcornCount());
        assertEquals(Stage.EMPTY, m.stage());
    }

    @Test
    void breakingGivesBackWhatWentInOrThePopcorn() {
        Machine m = new Machine();
        handler.onBreak(new FurnitureBreakEvent(new Machine("juicer").furniture, player));
        m.fill(3, 5);
        m.click(salt(1));
        handler.onBreak(new FurnitureBreakEvent(m.furniture, player));
        assertEquals(2, dropped.size());
        assertEquals(5, dropped.get(0).getAmount());
        assertTrue(m.variables.isEmpty());

        dropped.clear();
        m.fill(3, 2);
        m.click(null);
        server.getScheduler().performTicks(30);
        handler.onBreak(new FurnitureBreakEvent(m.furniture, player));
        assertFalse(PopcornAnimation.isRunning(m.furniture), "breaking stops the show");
        assertEquals(2, dropped.get(0).getAmount(), "mid-show, the corn comes back");
        server.getScheduler().performTicks(200);
        assertEquals(Stage.EMPTY, m.stage(), "a stopped show never finishes");

        dropped.clear();
        m.fill(3, 2);
        PopcornMachineState.setStage(m.furniture, Stage.READY);
        handler.onBreak(new FurnitureBreakEvent(m.furniture, player));
        assertEquals(8, dropped.get(0).getAmount(), "popped, the popcorn drops");
    }

    @Test
    void oddSetupsAreToldOrSkipped() throws Exception {
        custom("""
                popcorn_machine:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  input-name: ""
                  output: snack(type=popcorn;origin=Corn;tags=cooked.1)
                broken_output:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  output: snack(type=modelless;origin=Corn)
                no_output:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  output: "not food"
                """);
        Machine m = new Machine("popcorn_machine");
        m.fill(3, 1);
        m.click(null);
        server.getScheduler().performTicks(200);
        m.click(null);
        assertEquals(4, popcornCount());

        for (String id : List.of("broken_output", "no_output")) {
            Machine b = new Machine(id);
            b.fill(3, 1);
            b.click(null);
            assertEquals("§cThis machine can't make anything: check its output in popcorn-machines.yml.", lastMessage());
            assertEquals(Stage.LOADING, b.stage());
        }

    }

    @Test
    void popcornThatDoesntFitDropsAtThePlayersFeet() throws Exception {
        custom("""
                popcorn_machine:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  yield: 8
                  output: snack(type=popcorn;origin=Corn;tags=cooked.1)
                  models:
                    kettle: ""
                """);
        Machine m = new Machine();
        m.fill(3, 16);
        PopcornMachineState.setStage(m.furniture, Stage.READY);
        for (int i = 0; i < player.getInventory().getSize(); i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        player.getInventory().setItemInMainHand(null);
        m.click();
        assertEquals(64, popcornCount(), "64 fit in the empty hand slot");
        verify(world, never()).dropItemNaturally(any(), any());
        assertTrue(player.getWorld().getEntities().stream().anyMatch(e -> e instanceof org.bukkit.entity.Item),
                "the other 64 drop at the player's feet");
        assertNull(m.models.get("kettle"), "a blank kettle model shows nothing");
    }

    @Test
    void theShowCopesWithoutAKettleOrHeapAndWithWildKernels() throws Exception {
        custom("""
                popcorn_machine:
                  input: [c.vegetable(type=vegetable_2;origin=Corn)]
                  output: snack(type=popcorn;origin=Corn;tags=cooked.1)
                  slots:
                    heap: none
                  animation:
                    seconds: 2
                    walls: 0.001
                    floor: -100
                """);
        Machine m = new Machine("popcorn_machine", "kettle");
        when(m.placed.get("kettle").getDisplayStandId()).thenReturn(UUID.randomUUID());
        m.fill(3, 2);
        m.click(null);
        server.getScheduler().performTicks(60);
        assertEquals(Stage.READY, m.stage());
        assertFalse(spawned.isEmpty());

        Machine bare = new Machine("popcorn_machine");
        when(bare.furniture.getType()).thenReturn(null);
        bare.fill(3, 2);
        bare.click(null);
        server.getScheduler().performTicks(60);
        assertEquals(Stage.READY, bare.stage());

        // A dead kettle is left alone; stopping a machine that isn't popping does nothing.
        Machine dead = new Machine();
        dead.withKettle();
        when(dead.kettle.isDead()).thenReturn(true);
        dead.fill(3, 2);
        dead.click(null);
        server.getScheduler().performTicks(60);
        verify(dead.kettle, never()).setTransformation(any());
        PopcornAnimation.stop(dead.furniture);
    }

    private String lastMessage() {
        String last = null;
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) last = message;
        return last;
    }
}
