package net.tfminecraft.cooking.mmoitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.crafting.ConditionalDisplay;
import net.Indyuce.mmoitems.api.crafting.ingredient.Ingredient;
import net.Indyuce.mmoitems.api.crafting.ingredient.IngredientType;
import net.Indyuce.mmoitems.manager.CraftingManager;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.fishing.CustomFishingCatalog;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.StationFoodTemplates;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

class MMOItemsIngredientTest {
    private MockedStatic<TLibs> tlibs;
    private ItemAPI api;
    private List<FoodItem> foods;
    private Map<String, String> conversions;
    private Cooking plugin;
    private List<TagTrack> tracks;
    private final Logger logger = Logger.getLogger("MMOItemsIngredientTest");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        tlibs = mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        when(api.getChecker().getAsStringPath(any())).thenAnswer(call -> {
            ItemStack item = call.getArgument(0);
            return item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                    ? "modeled.(type=" + item.getType().name().toLowerCase() + ")"
                    : "v." + item.getType().name().toLowerCase();
        });
        foods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        FoodLoader.oList.add(StationFoodTemplates.withTracks(CustomFishingCatalog.WHOLE_TYPE, "seafood"));
        FoodLoader.oList.add(StationFoodTemplates.template("wheat", "grain"));
        tracks = new ArrayList<>(TrackLoader.oList);
        TrackLoader.oList.clear();
        TrackLoader.oList.add(StationFoodTemplates.freshness());
        conversions = new HashMap<>(ConversionLoader.conversions);
        ConversionLoader.conversions.clear();
        ConversionLoader.conversions.put("V.WHEAT", "grain(type=wheat;origin=Wheat)");
        ConversionLoader.conversions.put("v.beef", "meat(type=missing;origin=Beef)");
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
    }

    @AfterEach
    void tearDown() {
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        ConversionLoader.conversions.clear();
        ConversionLoader.conversions.putAll(conversions);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        CustomFishingCatalog.load(new File("src/main/resources/custom-fishing.yml"));
        tlibs.close();
        MockBukkit.unmock();
        Cooking.plugin = plugin;
    }

    @Test
    void plainItemsCountAsTheFoodCookingConvertsThemInto() {
        FoodItem salmon = StationFood.describe(new ItemStack(Material.SALMON));
        assertEquals("seafood", salmon.getCategory());
        assertEquals(CustomFishingCatalog.WHOLE_TYPE, salmon.getId());
        assertEquals("Salmon", salmon.getOrigin());
        assertEquals(StationFood.PLAIN_QUALITY, salmon.getQualityMin());

        FoodItem wheat = StationFood.describe(new ItemStack(Material.WHEAT));
        assertEquals("grain", wheat.getCategory());
        assertEquals("wheat", wheat.getId());
        assertEquals("Wheat", wheat.getOrigin());
        assertEquals(StationFood.PLAIN_QUALITY, wheat.getQualityMin());
    }

    @Test
    void otherItemsAreNotStationFood() {
        assertNull(StationFood.describe(null));
        assertNull(StationFood.describe(new ItemStack(Material.AIR)));
        assertNull(StationFood.describe(new ItemStack(Material.BONE)));
        // A conversion whose food type no longer exists converts nothing.
        assertNull(StationFood.describe(new ItemStack(Material.BEEF)));

        ItemStack named = new ItemStack(Material.SALMON);
        var meta = named.getItemMeta();
        meta.setDisplayName("Prize Salmon");
        named.setItemMeta(meta);
        assertNull(StationFood.describe(named));

        var checker = api.getChecker();
        doReturn(null).when(checker).getAsStringPath(any());
        assertNull(StationFood.describe(new ItemStack(Material.SALMON)));
    }

    @Test
    void wholeFishNeedsItsFoodType() {
        FoodLoader.oList.removeIf(food -> food.getId().equals(CustomFishingCatalog.WHOLE_TYPE));
        assertNull(StationFood.describe(new ItemStack(Material.SALMON)));
    }

    @Test
    void cookingFoodIsItself() {
        FoodItem roast = StationFoodTemplates.template("roast", "meat");
        ItemStack stack = new ItemStack(Material.TROPICAL_FISH);
        try (MockedStatic<FoodItem> codec = mockStatic(FoodItem.class, CALLS_REAL_METHODS)) {
            codec.when(() -> FoodItem.fromItem(stack)).thenReturn(roast);
            assertSame(roast, StationFood.describe(stack));
        }
    }

    @Test
    void ingredientReadsItsLineAndMatchesFood() {
        CookingStationIngredient salmon = ingredient(
                "cooking{item=\"c.seafood(type=seafood_whole;origin=Salmon)\",amount=64,display=\"Fresh Salmon\"}");
        assertEquals("seafood(type=seafood_whole;origin=Salmon)", salmon.getPath());
        assertEquals("Fresh Salmon", salmon.getDisplayName());
        assertEquals(64, salmon.getAmount());
        assertEquals("cooking", salmon.getPrefix());
        assertEquals("&a 64 Fresh Salmon", salmon.formatDisplay("&a #amount# #item#"));
        assertEquals("cooking{item=\"seafood(type=seafood_whole;origin=Salmon)\",amount=64}", salmon.toString());

        assertTrue(salmon.matches(player(new ItemStack(Material.SALMON))));
        assertFalse(salmon.matches(player(new ItemStack(Material.COD))));
        assertFalse(ingredient("cooking{item=\"seafood(type=seafood_whole;origin=Salmon;quality=3)\"}")
                .matches(player(new ItemStack(Material.SALMON))));
    }

    @Test
    void displayDefaultsToTheOriginThenTheType() {
        assertEquals("Tropical Fish",
                ingredient("cooking{item=\"seafood(type=seafood_whole;origin=Tropical Fish)\"}").getDisplayName());
        assertEquals("Meat Red Meat", ingredient("cooking{item=\"meat(type=meat_red_meat)\"}").getDisplayName());
        assertEquals("Hot Pot", ingredient("cooking{item=\"soup(type=_hot_pot)\"}").getDisplayName());
        assertEquals("Seafood Whole",
                ingredient("cooking{item=\"seafood(type=seafood_whole;origin= )\"}").getDisplayName());
        assertEquals(1, ingredient("cooking{item=\"seafood(type=seafood_whole)\"}").getAmount());
    }

    @Test
    void ingredientLineNeedsAFoodType() {
        assertThrows(IllegalArgumentException.class, () -> ingredient("cooking{amount=2}"));
        assertThrows(IllegalArgumentException.class, () -> ingredient("cooking{item=\"c.\"}"));
        assertThrows(IllegalArgumentException.class, () -> ingredient("cooking{item=\"seafood\"}"));
        assertThrows(IllegalArgumentException.class, () -> ingredient("cooking{item=\"seafood(origin=Salmon)\"}"));
        assertThrows(IllegalArgumentException.class, () -> ingredient("cooking{item=\"seafood(type=)\"}"));
    }

    @Test
    void previewShowsTheFoodFreshAndFitsOneStack() {
        ItemStack built = new ItemStack(Material.SALMON);
        try (MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class)) {
            ArgumentCaptor<FoodItem> food = ArgumentCaptor.forClass(FoodItem.class);
            ArgumentCaptor<ItemStack> base = ArgumentCaptor.forClass(ItemStack.class);
            builder.when(() -> ItemBuilder.buildSingleWithQuality(food.capture(), base.capture(), anyInt())).thenReturn(built);
            ItemStack preview = ingredient("cooking{item=\"seafood(type=seafood_whole;origin=Salmon)\",amount=99}")
                    .generateItemStack(null, true);
            assertSame(built, preview);
            assertEquals(preview.getMaxStackSize(), preview.getAmount());
            builder.verify(() -> ItemBuilder.buildSingleWithQuality(any(), any(), eq(StationFood.PLAIN_QUALITY)));
            assertEquals(Material.SALMON, base.getValue().getType());
            assertEquals(0, food.getValue().getTagTrack("freshness").getValue());
            assertEquals("Salmon", food.getValue().getOrigin());
        }
    }

    @Test
    void refundIsTheLeastTheLineAccepts() {
        try (MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class)) {
            ArgumentCaptor<FoodItem> food = ArgumentCaptor.forClass(FoodItem.class);
            builder.when(() -> ItemBuilder.buildSingleWithQuality(food.capture(), any(), anyInt()))
                    .thenAnswer(call -> new ItemStack(Material.SALMON));

            ItemStack refund = ingredient("cooking{item=\"seafood(type=seafood_whole;origin=Salmon)\",amount=99}")
                    .generateItemStack(null, false);
            assertEquals(99, refund.getAmount());
            assertEquals(StationFoodTemplates.ROTTEN, food.getValue().getTagTrack("freshness").getValue());
            assertEquals(0, food.getValue().getTagTrack("salted").getValue());
            assertEquals(0, food.getValue().getTagTrack("warmth").getValue());
            builder.verify(() -> ItemBuilder.buildSingleWithQuality(any(), any(), eq(StationFood.PLAIN_QUALITY)));

            ingredient("cooking{item=\"seafood(type=seafood_whole;origin=Salmon;quality=3-5;tags=freshness.0)\"}")
                    .generateItemStack(null, false);
            assertEquals(0, food.getValue().getTagTrack("freshness").getValue());
            builder.verify(() -> ItemBuilder.buildSingleWithQuality(any(), any(), eq(3)));
        }
    }

    @Test
    void unknownFoodFallsBackToANamedPlaceholder() {
        ItemStack unknown = ingredient("cooking{item=\"seafood(type=kraken)\",amount=2,display=\"Kraken\"}")
                .generateItemStack(null, false);
        assertEquals(Material.PAPER, unknown.getType());
        assertEquals("Kraken", unknown.getItemMeta().getDisplayName());
        assertEquals(2, unknown.getAmount());

        try (MockedStatic<ItemBuilder> builder = mockStatic(ItemBuilder.class)) {
            builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
            ItemStack unbuilt = ingredient("cooking{item=\"seafood(type=seafood_whole;origin=Salmon)\"}")
                    .generateItemStack(null, true);
            assertEquals(Material.PAPER, unbuilt.getType());
            assertEquals("Salmon", unbuilt.getItemMeta().getDisplayName());
        }
    }

    @Test
    void playerIngredientKeepsTheStackAndItsFood() {
        ItemStack stack = new ItemStack(Material.SALMON, 5);
        CookingStationPlayerIngredient ingredient = player(stack);
        assertSame(stack, ingredient.getItem());
        assertEquals(5, ingredient.getAmount());
        assertEquals("Salmon", ingredient.getFood().getOrigin());
    }

    @Test
    void plainSourceIsTheItemThatBecomesTheFood() {
        ConversionLoader.conversions.put("ia.tfmc_cooking:tomato", "vegetable(type=wheat;origin=Tomato)");
        ConversionLoader.conversions.put("v.not_a_material", "grain(type=wheat;origin=Oats)");
        assertEquals(Material.SALMON, StationFood.plainSource("seafood(type=seafood_whole;origin=Salmon)").getType());
        assertEquals(Material.WHEAT, StationFood.plainSource("grain(type=wheat;origin=Wheat)").getType());
        assertNull(StationFood.plainSource("vegetable(type=wheat;origin=Tomato)"));
        assertNull(StationFood.plainSource("grain(type=wheat;origin=Oats)"));
        assertNull(StationFood.plainSource("meat(type=meat_red_meat;origin=Beef)"));
    }

    @Test
    void cookingMovesBackInFrontOfLaterTypes() {
        List<IngredientType<?>> types = new ArrayList<>(List.of(type("itemsadder"), type("mythicitem"),
                type("cooking"), type("vanilla")));
        CraftingManager crafting = mock(CraftingManager.class);
        when(crafting.getIngredients()).thenReturn(types);
        MMOItems previous = MMOItems.plugin;
        MMOItems.plugin = mock(MMOItems.class);
        try {
            when(MMOItems.plugin.getCrafting()).thenReturn(crafting);
            MMOItemsIngredients.claimFirst(logger);
        } finally {
            MMOItems.plugin = previous;
        }
        assertEquals(List.of("cooking", "itemsadder", "mythicitem", "vanilla"), ids(types));
        MMOItemsIngredients.claimFirst(crafting, logger);
        assertEquals(List.of("cooking", "itemsadder", "mythicitem", "vanilla"), ids(types));
        types.remove(0);
        MMOItemsIngredients.claimFirst(crafting, logger);
        assertEquals(List.of("itemsadder", "mythicitem", "vanilla"), ids(types));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void registersTheCookingTypeFirstInLine() {
        CraftingManager crafting = mock(CraftingManager.class);
        MMOItems previous = MMOItems.plugin;
        MMOItems.plugin = mock(MMOItems.class);
        try {
            when(MMOItems.plugin.getCrafting()).thenReturn(crafting);
            MMOItemsIngredients.register(logger);
        } finally {
            MMOItems.plugin = previous;
        }
        ArgumentCaptor<Function> reader = ArgumentCaptor.forClass(Function.class);
        ArgumentCaptor<Predicate> claims = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<Function> players = ArgumentCaptor.forClass(Function.class);
        verify(crafting).registerIngredient(eq("cooking"), reader.capture(), any(ConditionalDisplay.class),
                claims.capture(), players.capture());

        Object built = reader.getValue().apply(new MMOLineConfig("cooking{item=\"seafood(type=seafood_whole)\",amount=2}"));
        assertInstanceOf(CookingStationIngredient.class, built);
        assertEquals(2, ((Ingredient<?>) built).getAmount());
        assertTrue(claims.getValue().test(nbt(new ItemStack(Material.SALMON))));
        assertFalse(claims.getValue().test(nbt(new ItemStack(Material.BONE))));
        assertInstanceOf(CookingStationPlayerIngredient.class, players.getValue().apply(nbt(new ItemStack(Material.WHEAT))));
    }

    @Test
    void registersOnlyWhenMMOItemsIsInstalled() {
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<MMOItemsIngredients> registry = mockStatic(MMOItemsIngredients.class)) {
            assertFalse(MMOItemsSupport.registerIfPresent(plugins, logger));
            registry.verify(() -> MMOItemsIngredients.register(any(Logger.class)), never());

            when(plugins.getPlugin("MMOItems")).thenReturn(mock(Plugin.class));
            assertTrue(MMOItemsSupport.registerIfPresent(plugins, logger));
            registry.verify(() -> MMOItemsIngredients.register(logger));

            registry.when(() -> MMOItemsIngredients.register(any(Logger.class))).thenThrow(new NoClassDefFoundError("x"));
            assertFalse(MMOItemsSupport.registerIfPresent(plugins, logger));
            registry.when(() -> MMOItemsIngredients.register(any(Logger.class))).thenThrow(new IllegalStateException("x"));
            assertFalse(MMOItemsSupport.registerIfPresent(plugins, logger));

            assertTrue(MMOItemsSupport.claimFirstIfPresent(plugins, logger));
            registry.verify(() -> MMOItemsIngredients.claimFirst(logger));
            registry.when(() -> MMOItemsIngredients.claimFirst(any(Logger.class))).thenThrow(new NoSuchMethodError("x"));
            assertFalse(MMOItemsSupport.claimFirstIfPresent(plugins, logger));
            when(plugins.getPlugin("MMOItems")).thenReturn(null);
            assertFalse(MMOItemsSupport.claimFirstIfPresent(plugins, logger));
        }
    }

    private static IngredientType<?> type(String id) {
        IngredientType<?> type = mock(IngredientType.class);
        when(type.getId()).thenReturn(id);
        return type;
    }

    private static List<String> ids(List<IngredientType<?>> types) {
        return types.stream().map(IngredientType::getId).toList();
    }

    private static CookingStationIngredient ingredient(String line) {
        return new CookingStationIngredient(new MMOLineConfig(line));
    }

    private static CookingStationPlayerIngredient player(ItemStack stack) {
        return new CookingStationPlayerIngredient(nbt(stack));
    }

    private static NBTItem nbt(ItemStack stack) {
        NBTItem nbt = mock(NBTItem.class);
        when(nbt.getItem()).thenReturn(stack);
        return nbt;
    }
}
