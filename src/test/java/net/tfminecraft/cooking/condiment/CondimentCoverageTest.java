package net.tfminecraft.cooking.condiment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

class CondimentCoverageTest {
    static final String CONFIG = """
            vinegar:
              brews:
                Wine: Wine
                cidre: Cider
              minutes: 2
              output: vinegar(type=vinegar;origin={origin})
              models:
                souring: model.wine
                vinegar: model.vinegar
            sap:
              logs: [OAK_LOG, NOT_A_BLOCK]
              cooldown-minutes: 3
              output: sap(type=tree_sap;origin=Tree)
            saucepan:
              liquids: [c.vinegar]
              bottles:
                catsup:
                  liquid: c.vinegar
                  ingredients:
                    - c.paste(type=paste;origin=Tomato)
                    - c.sweetener
                  boil-seconds: 20
                  output: condiment(type=catsup;origin=Tomato)
                broken: 3
            """;

    ServerMock server;
    Cooking previousPlugin;
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        server = MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        load(CONFIG);
    }

    @AfterEach void close() throws Exception {
        new CondimentConfig().load(directory.resolve("missing.yml").toFile());
        Cooking.plugin = previousPlugin;
        MockBukkit.unmock();
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("condiments.yml");
        Files.writeString(file, yaml);
        new CondimentConfig().load(file.toFile());
    }

    static FoodItem food(String id, String category, String origin) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("update", false);
        FoodItem food = new FoodItem(id, config);
        food.setCategory(category);
        food.setOrigin(origin);
        return food;
    }

    @Test
    void configReadsVinegarSapAndBottlesAndFallsBackToDefaults() throws Exception {
        assertEquals("Wine", CondimentConfig.vinegarOrigin("Mighty Red WINE"));
        assertEquals("Cider", CondimentConfig.vinegarOrigin("Golden Cidre"));
        assertNull(CondimentConfig.vinegarOrigin("Wheat Beer"));
        assertNull(CondimentConfig.vinegarOrigin(null));
        assertEquals(120_000L, CondimentConfig.vinegarMillis());
        assertEquals("vinegar(type=vinegar;origin={origin})", CondimentConfig.vinegarOutput());
        assertEquals("model.wine", CondimentConfig.souringModel());
        assertEquals("model.vinegar", CondimentConfig.vinegarModel());
        assertTrue(CondimentConfig.isSapLog(Material.OAK_LOG));
        assertFalse(CondimentConfig.isSapLog(Material.STONE));
        assertEquals(180_000L, CondimentConfig.sapCooldownMillis());
        assertEquals("sap(type=tree_sap;origin=Tree)", CondimentConfig.sapOutput());
        assertTrue(CondimentConfig.isSaucepanLiquid(food("vinegar", "vinegar", "Wine")));
        assertFalse(CondimentConfig.isSaucepanLiquid(food("sap", "sap", "Tree")));
        assertEquals(List.of("catsup"), CondimentConfig.bottles().stream().map(BottleRecipe::getId).toList());

        new CondimentConfig().load(directory.resolve("missing.yml").toFile());
        assertNull(CondimentConfig.vinegarOrigin("Red Wine"));
        assertEquals(3_600_000L, CondimentConfig.vinegarMillis());
        assertEquals("", CondimentConfig.vinegarOutput());
        assertFalse(CondimentConfig.isSapLog(Material.OAK_LOG));
        assertEquals(1_200_000L, CondimentConfig.sapCooldownMillis());
        assertTrue(CondimentConfig.bottles().isEmpty());
    }

    @Test
    void aRecipeTakesEachOfItsIngredientsOnceAndMatchesOnlyItsExactPan() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(CONFIG);
        BottleRecipe catsup = new BottleRecipe("catsup", config.getConfigurationSection("saucepan.bottles.catsup"));
        FoodItem vinegar = food("vinegar", "vinegar", "Wine");
        FoodItem paste = food("paste", "paste", "Tomato");
        FoodItem sugar = food("sweetener_1", "sweetener", "Sugar");
        FoodItem carrot = food("paste", "paste", "Carrot");
        assertEquals("catsup", catsup.getId());
        assertEquals(20, catsup.getBoilSeconds());
        assertEquals("condiment(type=catsup;origin=Tomato)", catsup.getOutput());

        assertTrue(catsup.usesLiquid(vinegar, false));
        assertFalse(catsup.usesLiquid(paste, false));
        // A cup of water is no vinegar.
        assertFalse(catsup.usesLiquid(null, true));
        assertTrue(catsup.takes(paste, List.of()));
        assertFalse(catsup.takes(carrot, List.of()));
        assertFalse(catsup.takes(paste, List.of(paste)));
        assertTrue(catsup.takes(sugar, List.of(paste)));
        assertFalse(catsup.takes(sugar, List.of(carrot)));

        assertTrue(catsup.matches(vinegar, false, List.of(sugar, paste)));
        assertFalse(catsup.matches(vinegar, false, List.of(paste)));
        assertFalse(catsup.matches(vinegar, false, List.of(paste, sugar, sugar)));
        assertFalse(catsup.matches(paste, false, List.of(paste, sugar)));

        // liquid: water is the saucepan's cup of water, which pours no food; bones go in once each.
        MemoryConfiguration water = new MemoryConfiguration();
        water.set("liquid", " Water ");
        water.set("ingredients", List.of("c.bone", "c.bone"));
        BottleRecipe stock = new BottleRecipe("stock", water);
        FoodItem bone = food("bone", "bone", "Bone");
        assertTrue(stock.usesLiquid(null, true));
        assertFalse(stock.usesLiquid(vinegar, false));
        assertTrue(stock.takes(bone, List.of(bone)));
        assertFalse(stock.takes(bone, List.of(bone, bone)));
        assertTrue(stock.matches(null, true, List.of(bone, bone)));
        assertFalse(stock.matches(null, false, List.of(bone, bone)));
        assertFalse(stock.matches(null, true, List.of(bone)));

        MemoryConfiguration negative = new MemoryConfiguration();
        negative.set("boil-seconds", -5);
        assertEquals(0, new BottleRecipe("odd", negative).getBoilSeconds());
        BottleRecipe bare = new BottleRecipe("bare", new MemoryConfiguration());
        assertEquals(30, bare.getBoilSeconds());
        assertEquals("", bare.getOutput());
    }

    @Test
    void anEmptyBottleOnALogCollectsSapUntilTheLogHasToRecover() {
        WorldMock world = server.addSimpleWorld("woods");
        Block log = world.getBlockAt(3, 64, -7);
        log.setType(Material.OAK_LOG);
        PlayerMock player = server.addPlayer();
        ItemStack sap = new ItemStack(Material.HONEY_BOTTLE);
        SapTapListener listener = new SapTapListener();
        try (MockedStatic<FoodParser> parser = mockStatic(FoodParser.class);
             MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class)) {
            FoodParser.Result parsed = new FoodParser.Result();
            parsed.template = food("tree_sap", "sap", "Tree");
            parser.when(() -> FoodParser.parse("sap(type=tree_sap;origin=Tree)")).thenReturn(parsed);
            builder.when(() -> ItemBuilder.buildSingle(parsed.template, null)).thenAnswer(inv -> sap.clone());

            player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE, 2));
            PlayerInteractEvent left = tap(player, Action.LEFT_CLICK_BLOCK, log, EquipmentSlot.HAND);
            PlayerInteractEvent offHand = tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.OFF_HAND);
            PlayerInteractEvent air = tap(player, Action.RIGHT_CLICK_BLOCK, null, EquipmentSlot.HAND);
            PlayerInteractEvent stone = tap(player, Action.RIGHT_CLICK_BLOCK, world.getBlockAt(0, 0, 0), EquipmentSlot.HAND);
            for (PlayerInteractEvent ignored : List.of(left, offHand, air, stone)) listener.onTap(ignored);
            player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
            PlayerInteractEvent stick = tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND);
            listener.onTap(stick);
            FoodItem drink = food("bottle", "drink", "Glass");
            try (MockedStatic<FoodItem> foods = mockStatic(FoodItem.class)) {
                foods.when(() -> FoodItem.fromItem(any())).thenReturn(drink);
                player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
                listener.onTap(tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND));
            }
            assertTrue(player.getInventory().contains(Material.GLASS_BOTTLE));
            assertFalse(player.getInventory().contains(Material.HONEY_BOTTLE));
            assertFalse(left.useInteractedBlock() == org.bukkit.event.Event.Result.DENY);

            player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE, 2));
            PlayerInteractEvent first = tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND);
            listener.onTap(first);
            assertTrue(first.useInteractedBlock() == org.bukkit.event.Event.Result.DENY);
            assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
            assertTrue(player.getInventory().contains(Material.HONEY_BOTTLE));
            NamespacedKey key = SapTapListener.keyFor(log);
            assertEquals("sap_3_64_-7", key.getKey());
            long ready = log.getChunk().getPersistentDataContainer().get(key, PersistentDataType.LONG);
            assertTrue(Math.abs(System.currentTimeMillis() + 180_000L - ready) < 5_000);

            listener.onTap(tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND));
            assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
            log.getChunk().getPersistentDataContainer().set(key, PersistentDataType.LONG, System.currentTimeMillis() + 30_000L);
            listener.onTap(tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND));
            assertEquals("§7This log has given its sap. Try again in 3 minutes.", player.nextMessage());
            assertEquals("§7This log has given its sap. Try again in 1 minute.", player.nextMessage());

            log.getChunk().getPersistentDataContainer().set(key, PersistentDataType.LONG, 0L);
            builder.when(() -> ItemBuilder.buildSingle(parsed.template, null)).thenReturn(null);
            listener.onTap(tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND));
            parser.when(() -> FoodParser.parse(anyString())).thenReturn(new FoodParser.Result());
            listener.onTap(tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND));
            parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
            listener.onTap(tap(player, Action.RIGHT_CLICK_BLOCK, log, EquipmentSlot.HAND));
            for (int i = 0; i < 3; i++) assertEquals("§cNo sap can be collected right now.", player.nextMessage());
            assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        }
    }

    static PlayerInteractEvent tap(PlayerMock player, Action action, Block block, EquipmentSlot hand) {
        return new PlayerInteractEvent(player, action, player.getInventory().getItemInMainHand(), block, BlockFace.UP, hand);
    }
}
