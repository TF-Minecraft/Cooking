package net.tfminecraft.cooking.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.carve.CarveCut;
import net.tfminecraft.cooking.carve.CarveSequence;
import net.tfminecraft.cooking.husbandry.HusbandryAnimal;
import net.tfminecraft.cooking.husbandry.HusbandryHarvest;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;

class CookingFoodApiTest {
    private Cooking previousPlugin;

    @BeforeEach void setUp() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
        Cooking.plugin = previousPlugin;
    }

    @Test void milkComesFromGoatsOrOtherwiseCows() {
        assertEquals("food(type=milk_bucket;origin=Goat)", CookingFoodApi.milkFood(EntityType.GOAT));
        assertEquals("food(type=milk_bucket;origin=Cow)", CookingFoodApi.milkFood(EntityType.COW));
        assertEquals("food(type=milk_bucket;origin=Cow)", CookingFoodApi.milkFood(null));
    }

    @Test void foodAndMilkAreBuiltWholeAtClampedStars() {
        FoodItem egg = food("egg");
        ItemStack built = new ItemStack(Material.EGG);
        try (MockedStatic<FoodParser> parser = mockStatic(FoodParser.class);
             MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class);
             MockedStatic<FoodItem> codec = mockStatic(FoodItem.class)) {
            parser.when(() -> FoodParser.parse(anyString())).thenReturn(parsed(egg));
            builder.when(() -> ItemBuilder.buildSingleWithQuality(eq(egg), any(), anyInt())).thenReturn(built);

            assertSame(built, CookingFoodApi.buildFood("food(type=egg)", 9));
            builder.verify(() -> ItemBuilder.buildSingleWithQuality(egg, null, 5));
            assertSame(built, CookingFoodApi.buildMilk(EntityType.GOAT, 0));
            parser.verify(() -> FoodParser.parse("food(type=milk_bucket;origin=Goat)"));
            builder.verify(() -> ItemBuilder.buildSingleWithQuality(eq(egg),
                    argThat(base -> base != null && base.getType() == Material.MILK_BUCKET), eq(1)));
            codec.verifyNoInteractions();
        }
    }

    @Test void roastsNeverAskForFewerThanZeroCuts() {
        FoodItem roast = food("roast");
        roast.setCarveState("roast_seq", 0, 3);
        ItemStack built = new ItemStack(Material.BEEF);
        CarveSequence sequence = new CarveSequence("roast_seq", 1, List.of(CarveCut.food("slice", 4, 4),
                CarveCut.food("slice", 4, 4), CarveCut.item("bone", 1, 0, 0)));
        try (MockedStatic<FoodParser> parser = mockStatic(FoodParser.class);
             MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class);
             MockedStatic<FoodItem> codec = mockStatic(FoodItem.class, CALLS_REAL_METHODS);
             MockedStatic<CarveSequenceLoader> sequences = mockStatic(CarveSequenceLoader.class);
             MockedStatic<ItemUpdater> updater = mockStatic(ItemUpdater.class)) {
            parser.when(() -> FoodParser.parse("roast")).thenReturn(parsed(roast));
            builder.when(() -> ItemBuilder.buildSingleWithQuality(eq(roast), any(), anyInt())).thenReturn(built);
            codec.when(() -> FoodItem.fromItem(built)).thenReturn(roast);
            sequences.when(() -> CarveSequenceLoader.get("roast_seq")).thenReturn(sequence);

            CookingFoodApi.buildRoast("roast", 3, -4);

            assertEquals(2, roast.getCarveRemaining(), "The sequence floor keeps one cut and the bone");
        }
    }

    @Test void qualityIsReadOnlyFromCookingItems() {
        assertEquals(OptionalInt.empty(), CookingFoodApi.qualityOf(null));
        assertEquals(OptionalInt.empty(), CookingFoodApi.qualityOf(new ItemStack(Material.AIR)));
        assertEquals(OptionalInt.empty(), CookingFoodApi.qualityOf(new ItemStack(Material.WHEAT)));
        ItemStack named = new ItemStack(Material.WHEAT);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Wheat");
        named.setItemMeta(meta);
        assertEquals(OptionalInt.empty(), CookingFoodApi.qualityOf(named));
        meta.getPersistentDataContainer().set(Keys.QUALITY, PersistentDataType.INTEGER, 7);
        named.setItemMeta(meta);
        assertEquals(OptionalInt.of(5), CookingFoodApi.qualityOf(named));
    }

    @Test void inventoryAdditionUsesCookingFoodStacking() {
        Player player = mock(Player.class);
        ItemStack item = new ItemStack(Material.EGG);
        ItemStack leftover = new ItemStack(Material.EGG, 2);
        try (MockedStatic<InventoryAdder> adder = mockStatic(InventoryAdder.class)) {
            adder.when(() -> InventoryAdder.addItem(player, item)).thenReturn(leftover);
            assertSame(leftover, CookingFoodApi.addToInventory(player, item));
        }
    }

    @Test void husbandryMilksThroughTheApiOnlyWithAnAnimal() {
        assertNull(HusbandryHarvest.buildMilk(null, EntityType.COW));
        try (MockedStatic<CookingFoodApi> api = mockStatic(CookingFoodApi.class)) {
            ItemStack milk = new ItemStack(Material.MILK_BUCKET);
            api.when(() -> CookingFoodApi.buildMilk(eq(EntityType.GOAT), anyInt())).thenReturn(milk);
            assertSame(milk, HusbandryHarvest.buildMilk(new HusbandryAnimal(UUID.randomUUID(), "GOAT", "Nanny"), EntityType.GOAT));
        }
    }

    private static FoodItem food(String id) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", id);
        config.set("update", false);
        return new FoodItem(id, config);
    }

    private static FoodParser.Result parsed(FoodItem template) {
        FoodParser.Result result = new FoodParser.Result();
        result.template = template;
        return result;
    }
}
