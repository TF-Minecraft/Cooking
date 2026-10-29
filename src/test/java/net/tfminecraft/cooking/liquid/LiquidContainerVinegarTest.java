package net.tfminecraft.cooking.liquid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.brewery.BrewLiquid;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.condiment.CondimentConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Wine or cider poured into a liquid container sours into vinegar that an empty glass bottle draws out. */
class LiquidContainerVinegarTest {
    Workshop env;
    Station barrel;
    LiquidContainerHandler handler;
    String oldContainer, oldWater, oldMilk;
    int oldPerBucket, oldMaximum;
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        oldContainer = FurnitureCache.liquidContainer; oldWater = ItemCache.liquidBlockWater; oldMilk = ItemCache.liquidBlockMilk;
        oldPerBucket = ItemCache.blocksPerBucket; oldMaximum = ItemCache.maxBlocks;
        env = new Workshop(); handler = new LiquidContainerHandler();
        FurnitureCache.liquidContainer = "barrel"; ItemCache.liquidBlockWater = "model.water"; ItemCache.liquidBlockMilk = "model.milk";
        ItemCache.blocksPerBucket = 3; ItemCache.maxBlocks = 2;
        barrel = env.station("barrel"); for (String id : LiquidContainerSlots.all()) barrel.define(id);
        when(env.manager.getPlacedFurniture()).thenReturn(new LinkedHashMap<>());
        env.cache.when(() -> ItemCache.isEmptyCup(any())).thenAnswer(inv -> {
            ItemStack item = inv.getArgument(0); return item != null && item.getType() == Material.PAPER;
        });
        BrewLiquid.setReader(item -> item.getType() == Material.POTION && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? new BrewLiquid(item.getItemMeta().getDisplayName(), 4, "990000", true) : null);
        Path file = directory.resolve("condiments.yml");
        Files.writeString(file, """
                vinegar:
                  brews: {wine: Wine, cider: Cider}
                  minutes: 10
                  output: vinegar(type=vinegar;origin={origin})
                  models: {souring: model.souring, vinegar: model.vinegar}
                """);
        new CondimentConfig().load(file.toFile());
        MemoryConfiguration settings = new MemoryConfiguration();
        settings.set("name", "Vinegar"); settings.set("update", false);
        env.templates.put("vinegar", new FoodItem("vinegar", settings));
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenAnswer(inv -> {
            FoodItem built = inv.getArgument(0);
            assertEquals(4, (int) inv.getArgument(2));
            return env.stack(built, Material.HONEY_BOTTLE, 1);
        });
    }

    @AfterEach void close() throws Exception {
        BrewLiquid.setReader(null);
        new CondimentConfig().load(directory.resolve("missing.yml").toFile());
        LiquidContainerAging.stopAll(); env.server.getScheduler().cancelTasks(Cooking.plugin); env.close();
        FurnitureCache.liquidContainer = oldContainer; ItemCache.liquidBlockWater = oldWater; ItemCache.liquidBlockMilk = oldMilk;
        ItemCache.blocksPerBucket = oldPerBucket; ItemCache.maxBlocks = oldMaximum;
    }

    static ItemStack brew(String name, int amount) {
        ItemStack potion = new ItemStack(Material.POTION, amount);
        var meta = potion.getItemMeta(); meta.setDisplayName(name); potion.setItemMeta(meta);
        return potion;
    }

    void click(ItemStack hand) {
        env.player.getInventory().setItemInMainHand(hand);
        handler.onInteract(barrel.interact());
    }

    ItemStack hand() {
        return env.player.getInventory().getItemInMainHand();
    }

    @Test
    void wineSoursIntoVinegarThatAnEmptyBottleDrawsOneLevelAtATime() {
        click(new ItemStack(Material.GLASS_BOTTLE));
        assertTrue(env.messages.contains("Pour in wine or cider and let it sour to get vinegar."));
        click(brew("Wheat Beer", 1));
        assertTrue(env.messages.contains("Only wine or cider sours into vinegar."));

        click(brew("Red Wine", 2));
        assertEquals(LiquidContainerState.TYPE_WINE, LiquidContainerState.getType(barrel.furniture));
        assertEquals(1, LiquidContainerState.getBlocks(barrel.furniture));
        assertEquals("Wine", LiquidContainerState.getVinegarOrigin(barrel.furniture));
        assertEquals(4, LiquidContainerState.getVinegarQuality(barrel.furniture));
        assertEquals(Material.POTION, hand().getType());
        assertTrue(env.player.getInventory().contains(Material.GLASS_BOTTLE));
        verify(env.api.getCreator(), atLeastOnce()).getItemFromPath("model.souring");

        click(brew("Golden Cider", 1));
        assertTrue(env.messages.contains("This container already has wine."));
        click(brew("Red Wine", 1));
        assertEquals(2, LiquidContainerState.getBlocks(barrel.furniture));
        click(brew("Red Wine", 1));
        assertTrue(env.messages.contains("The container is full."));
        click(new ItemStack(Material.WATER_BUCKET));
        assertTrue(env.messages.contains("This container already has wine."));
        click(new ItemStack(Material.PAPER));
        assertTrue(env.messages.contains("Use an empty glass bottle for the vinegar."));

        click(new ItemStack(Material.GLASS_BOTTLE));
        assertTrue(env.messages.contains("The wine is still souring: about 10 minutes left."));
        barrel.variables.put(LiquidContainerState.VAR_SOURED_AT, System.currentTimeMillis() + 30_000L);
        click(new ItemStack(Material.GLASS_BOTTLE));
        assertTrue(env.messages.contains("The wine is still souring: about 1 minute left."));

        barrel.variables.put(LiquidContainerState.VAR_SOURED_AT, 0L);
        assertTrue(LiquidContainerState.isSoured(barrel.furniture, 1L));
        click(new ItemStack(Material.GLASS_BOTTLE, 2));
        assertEquals(1, hand().getAmount());
        assertTrue(env.player.getInventory().contains(Material.HONEY_BOTTLE));
        assertEquals(1, LiquidContainerState.getBlocks(barrel.furniture));
        click(new ItemStack(Material.GLASS_BOTTLE));
        assertTrue(LiquidContainerState.isEmpty(barrel.furniture));
        assertNull(LiquidContainerState.getVinegarOrigin(barrel.furniture));
        verify(env.api.getCreator(), atLeastOnce()).getItemFromPath("model.vinegar");

        // An emptied container takes cider next, with its own clock.
        click(brew("Golden Cider", 1));
        assertEquals("Cider", LiquidContainerState.getVinegarOrigin(barrel.furniture));
        assertTrue(LiquidContainerState.getSouredAt(barrel.furniture) > System.currentTimeMillis());
    }

    @Test
    void stateDefaultsAndFailedBottlesKeepTheVinegar() {
        assertEquals(1, LiquidContainerState.getVinegarQuality(barrel.furniture));
        assertEquals(0L, LiquidContainerState.getSouredAt(barrel.furniture));

        LiquidContainerState.setType(barrel.furniture, "milk"); LiquidContainerState.setBlocks(barrel.furniture, 1);
        click(brew("Red Wine", 1));
        assertTrue(env.messages.contains("This container already has milk."));
        click(new ItemStack(Material.GLASS_BOTTLE));
        assertTrue(env.messages.contains("Pour in wine or cider and let it sour to get vinegar."));

        LiquidContainerState.clear(barrel.furniture);
        click(brew("Red Wine", 1));
        barrel.variables.put(LiquidContainerState.VAR_SOURED_AT, 0L);
        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
        click(new ItemStack(Material.GLASS_BOTTLE));
        env.templates.remove("vinegar");
        click(new ItemStack(Material.GLASS_BOTTLE));
        assertEquals(2, env.messages.stream().filter("Could not fill a bottle."::equals).count());
        assertEquals(1, LiquidContainerState.getBlocks(barrel.furniture));

        // A container whose type was lost still refuses a different wine.
        LiquidContainerState.setType(barrel.furniture, null);
        LiquidContainerState.setBlocks(barrel.furniture, 1);
        barrel.variables.put(LiquidContainerState.VAR_VINEGAR_ORIGIN, "Cider");
        click(brew("Red Wine", 1));
        assertEquals(1, env.messages.stream().filter("This container already has something else."::equals).count());
    }
}
