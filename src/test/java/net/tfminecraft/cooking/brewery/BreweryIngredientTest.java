package net.tfminecraft.cooking.brewery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.api.BreweryApi;
import com.dre.brewery.api.events.IngedientAddEvent;
import com.dre.brewery.recipe.BCauldronRecipe;
import com.dre.brewery.recipe.Ingredient;
import com.dre.brewery.recipe.PluginItem;
import com.dre.brewery.recipe.RecipeItem;
import com.dre.brewery.recipe.SimpleItem;
import com.dre.brewery.utility.MinecraftVersion;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

class BreweryIngredientTest {
    private ServerMock server;
    private Cooking previousPlugin;
    private List<FoodItem> previousFoods;
    private Map<String, String> previousConversions;
    private List<BCauldronRecipe> recipes;
    private List<RecipeItem> accepted;
    private Set<Material> simple, materials;
    private int configCount;
    private MockedStatic<BreweryPlugin> brewery;
    private MockedStatic<TLibs> tlibs;
    private ItemAPI api;
    private BreweryIngredientListener listener;
    private Runnable cleanup;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        previousFoods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        for (String id : List.of("vegetable_1", "vegetable_2", "wheat", "fruit_1", "fruit_2", "egg", "vegetable_cut"))
            FoodLoader.oList.add(BrewingIngredientResolverTest.food(id, null));
        previousConversions = ConversionLoader.conversions;
        ConversionLoader.conversions = new HashMap<>(Map.of(
                "v.potato", "vegetable(type=vegetable_1;origin=Potato)",
                "v.wheat", "grain(type=wheat;origin=Wheat)",
                "v.apple", "fruit(type=fruit_1;origin=Apple)",
                "v.egg", "egg(type=egg;origin=Egg)",
                "ia.tfmc_cooking:grape", "fruit(type=fruit_2;origin=Grape)",
                "ia.tfmc_cooking:onion", "vegetable(type=vegetable_2;origin=Onion)"));
        brewery = mockStatic(BreweryPlugin.class);
        brewery.when(BreweryPlugin::getMCVersion).thenReturn(MinecraftVersion.V1_21_10);
        recipes = BCauldronRecipe.recipes; accepted = BCauldronRecipe.acceptedCustom;
        simple = BCauldronRecipe.acceptedSimple; materials = BCauldronRecipe.acceptedMaterials;
        configCount = BCauldronRecipe.numConfigRecipes;
        BCauldronRecipe.recipes = new ArrayList<>(); BCauldronRecipe.numConfigRecipes = 0;
        BCauldronRecipe.acceptedCustom = new ArrayList<>();
        BCauldronRecipe.acceptedSimple = new HashSet<>(Set.of(Material.APPLE, Material.POTATO, Material.WHEAT, Material.EGG));
        BCauldronRecipe.acceptedMaterials = new HashSet<>(BCauldronRecipe.acceptedSimple);
        api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        tlibs = mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(api);
        for (Material mat : List.of(Material.APPLE, Material.POTATO, Material.WHEAT, Material.EGG))
            when(api.getCreator().getItemFromPath("v." + mat.name().toLowerCase(java.util.Locale.ROOT))).thenReturn(new ItemStack(mat));
        when(api.getChecker().getAsStringPath(any())).thenAnswer(c -> "v." + ((ItemStack)c.getArgument(0)).getType().name());
        listener = new BreweryIngredientListener();
        cleanup = BreweryIngredientListener.register(MockBukkit.createMockPlugin("BreweryCookingTest"));
    }

    @AfterEach void teardown() {
        cleanup.run();
        BCauldronRecipe.recipes = recipes; BCauldronRecipe.numConfigRecipes = configCount;
        BCauldronRecipe.acceptedCustom = accepted; BCauldronRecipe.acceptedSimple = simple;
        BCauldronRecipe.acceptedMaterials = materials;
        ConversionLoader.conversions = previousConversions;
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(previousFoods);
        tlibs.close(); brewery.close(); MockBukkit.unmock(); Cooking.plugin = previousPlugin;
    }

    @Test void gateAdmitsRottenWheatBeforeAcceptanceWithoutMakingPlainFishAnIngredient() {
        ItemStack wheat = item("wheat", "Wheat", Material.TROPICAL_FISH);
        assertNotNull(RecipeItem.getMatchingRecipeItem(wheat, false));
        assertNull(RecipeItem.getMatchingRecipeItem(new ItemStack(Material.TROPICAL_FISH), false));
        IngedientAddEvent event = event(wheat);
        ItemStack before = wheat.clone();
        server.getPluginManager().callEvent(event);
        assertEquals(new ItemStack(Material.WHEAT), event.getIngredient());
        assertInstanceOf(SimpleItem.class, event.getRecipeItem());
        assertEquals(before, wheat);
        assertFalse(event.isCancelled());
        assertTrue(BCauldronRecipe.getAllRecipes().isEmpty());
    }

    @Test void replacesOnlyTheRecordedIdentityForVanillaAndCustomWholeFoods() {
        for (String[] identity : List.of(new String[]{"vegetable_1", "Potato", "POTATO"},
                new String[]{"fruit_1", "Apple", "APPLE"}, new String[]{"egg", "Egg", "EGG"})) {
            ItemStack held = item(identity[0], identity[1], Material.APPLE);
            ItemStack before = held.clone();
            IngedientAddEvent event = event(held);
            listener.onIngredient(event);
            assertEquals(new ItemStack(Material.valueOf(identity[2])), event.getIngredient());
            assertEquals(before, held);
        }
        ItemStack grape = new ItemStack(Material.APPLE); var meta = grape.getItemMeta();meta.setDisplayName("Grape");grape.setItemMeta(meta);
        when(api.getCreator().getItemFromPath("ia.tfmc_cooking:grape")).thenReturn(grape);
        PluginItem explicit = mock(PluginItem.class);
        when(explicit.matches(any(ItemStack.class))).thenAnswer(c -> grape.equals(c.getArgument(0)));
        BCauldronRecipe.acceptedCustom.add(explicit);
        IngedientAddEvent event = event(item("fruit_2", "Grape", Material.APPLE));
        listener.onIngredient(event);
        assertEquals(grape, event.getIngredient());
        assertSame(explicit, event.getRecipeItem());
        assertEquals(1, grape.getAmount());
    }

    @Test void rejectsUnmappedPreparedMissingAndInvalidFoodWithoutConsumingIt() {
        when(api.getCreator().getItemFromPath("ia.tfmc_cooking:onion")).thenReturn(new ItemStack(Material.APPLE));
        when(api.getCreator().getItemFromPath("ia.tfmc_cooking:grape")).thenReturn(new ItemStack(Material.STONE));
        for (ItemStack input : List.of(item("vegetable_2", "Onion", Material.APPLE),
                item("vegetable_cut", "Potato", Material.APPLE), item("missing", "Potato", Material.APPLE),
                item("fruit_2", "Grape", Material.APPLE))) {
            IngedientAddEvent event = event(input); listener.onIngredient(event);
            assertTrue(event.isCancelled()); assertSame(input, event.getIngredient()); assertEquals(10, input.getAmount());
        }
        for (ItemStack invalidCanonical : List.of(new ItemStack(Material.AIR), item("wheat", "Wheat", Material.WHEAT))) {
            when(api.getCreator().getItemFromPath("v.wheat")).thenReturn(invalidCanonical);
            IngedientAddEvent event = event(item("wheat", "Wheat", Material.TROPICAL_FISH));
            listener.onIngredient(event); assertTrue(event.isCancelled());
        }
        when(api.getCreator().getItemFromPath("v.wheat")).thenReturn(null);
        IngedientAddEvent missing = event(item("wheat", "Wheat", Material.TROPICAL_FISH));
        listener.onIngredient(missing);assertTrue(missing.isCancelled());
    }

    @Test void preservesPlainAndExplicitCustomIngredientsButBlocksUnmappedCustomBaseFallback() {
        for (String path : List.of("v.apple", "modeled.(type=apple)", "ia.other:onion", "m.food.ONION")) {
            ItemStack input = new ItemStack(Material.APPLE, 3);
            when(api.getChecker().getAsStringPath(input)).thenReturn(path);
            IngedientAddEvent event = event(input); listener.onIngredient(event);
            assertEquals(path.startsWith("ia.") || path.startsWith("m."), event.isCancelled(), path);
            assertSame(input, event.getIngredient());
        }
        RecipeItem explicit = mock(PluginItem.class);
        ItemStack input = new ItemStack(Material.APPLE, 4);
        IngedientAddEvent custom = new IngedientAddEvent(server.addPlayer(), null, null, input, explicit);
        listener.onIngredient(custom); assertFalse(custom.isCancelled()); assertSame(input, custom.getIngredient());
    }

    @Test void cancellationsAndResolverFailuresNeverTakeTheOriginalItem() {
        IngedientAddEvent cancelled = event(item("vegetable_1", "Potato", Material.APPLE));
        cancelled.setCancelled(true); ItemStack original = cancelled.getIngredient();
        server.getPluginManager().callEvent(cancelled); assertSame(original, cancelled.getIngredient());
        when(api.getCreator().getItemFromPath("v.wheat")).thenThrow(new IllegalStateException("catalog unavailable"));
        IngedientAddEvent failed = event(item("wheat", "Wheat", Material.TROPICAL_FISH));
        assertThrows(IllegalStateException.class, () -> listener.onIngredient(failed));
        assertTrue(failed.isCancelled()); assertEquals(10, failed.getIngredient().getAmount());
    }

    @Test void runtimeGateSurvivesAcceptedListRebuildWithoutARecipeAndIsRemovedOnClose() {
        RecipeItem gate = BCauldronRecipe.acceptedCustom.getFirst();
        assertFalse(gate.matches(mock(Ingredient.class))); assertFalse(gate.hasMaterials()); assertTrue(gate.getMaterials().isEmpty());
        assertEquals("cooking:identity_gate", gate.getDebugID()); assertEquals(System.identityHashCode(gate), gate.hashCode());
        assertEquals(gate, gate); assertNotEquals(gate, new CookingIngredientGate());
        assertThrows(IllegalStateException.class, () -> gate.toIngredient(new ItemStack(Material.APPLE)));
        assertThrows(IllegalStateException.class, gate::toIngredientGeneric);
        assertFalse(gate.matches((ItemStack)null));
        // Brewery reloads re-add every recipe's items without clearing the accepted list.
        BCauldronRecipe reloaded = new BCauldronRecipe("reloaded", "Reloaded");
        reloaded.setIngredients(List.of(new SimpleItem(Material.WHEAT)));
        BreweryApi.addCauldronRecipe(reloaded, false);
        assertSame(gate, BCauldronRecipe.acceptedCustom.getFirst());
        assertSame(gate, RecipeItem.getMatchingRecipeItem(item("wheat", "Wheat", Material.TROPICAL_FISH), false));
        BCauldronRecipe.recipes.clear();
        cleanup.run();assertTrue(BCauldronRecipe.acceptedCustom.isEmpty());
        assertTrue(HandlerList.getRegisteredListeners(server.getPluginManager().getPlugin("BreweryCookingTest")).isEmpty());
    }

    @Test void optionalBridgeHandlesMissingDisabledLateAndRepeatedEnablesAndClosesCleanly() {
        JavaPlugin owner = mock(JavaPlugin.class, RETURNS_DEEP_STUBS);
        Logger logger = mock(Logger.class);when(owner.getLogger()).thenReturn(logger);
        var manager = owner.getServer().getPluginManager();
        BreweryBridge bridge = new BreweryBridge(owner);bridge.close();
        when(manager.getPlugin("BreweryX")).thenReturn(null);bridge.registerIfPresent();
        Plugin optional = mock(Plugin.class);when(optional.getName()).thenReturn("BreweryX");
        when(manager.getPlugin("BreweryX")).thenReturn(optional);bridge.registerIfPresent();
        when(optional.isEnabled()).thenReturn(true);
        doThrow(new NoClassDefFoundError("test linkage")).doNothing().when(manager).registerEvents(any(), same(owner));
        bridge.registerIfPresent();verify(logger).warning(contains("could not register"));
        bridge.onPluginEnable(new PluginEnableEvent(optional));bridge.registerIfPresent();
        Plugin other = mock(Plugin.class);when(other.getName()).thenReturn("Other");bridge.onPluginEnable(new PluginEnableEvent(other));
        verify(logger).info("BreweryX Cooking ingredient resolver enabled.");
        int gates = BCauldronRecipe.acceptedCustom.size();
        bridge.onPluginDisable(new PluginDisableEvent(other));assertEquals(gates, BCauldronRecipe.acceptedCustom.size());
        bridge.onPluginDisable(new PluginDisableEvent(optional));assertEquals(gates - 1, BCauldronRecipe.acceptedCustom.size());
        bridge.registerIfPresent();verify(logger, times(2)).info("BreweryX Cooking ingredient resolver enabled.");
        bridge.close();bridge.close();
    }

    @Test void failedListenerRegistrationRemovesTheGate() {
        JavaPlugin owner = mock(JavaPlugin.class, RETURNS_DEEP_STUBS);
        var manager = owner.getServer().getPluginManager();
        doThrow(new IllegalStateException("listener registration failed")).when(manager).registerEvents(any(), same(owner));
        List<RecipeItem> before = new ArrayList<>(BCauldronRecipe.acceptedCustom);
        assertThrows(IllegalStateException.class, () -> BreweryIngredientListener.register(owner));
        assertEquals(before, BCauldronRecipe.acceptedCustom);
    }

    private IngedientAddEvent event(ItemStack item) {
        return new IngedientAddEvent(server.addPlayer(), null, null, item, RecipeItem.getMatchingRecipeItem(item, false));
    }
    private ItemStack item(String type, String origin, Material material) {
        ItemStack item = new ItemStack(material, 10);var meta = item.getItemMeta();
        meta.setDisplayName("Rotten Potato");meta.setCustomModelData(42007);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.FOOD_ID, PersistentDataType.STRING, type);pdc.set(Keys.ORIGIN, PersistentDataType.STRING, origin);
        pdc.set(Keys.QUALITY, PersistentDataType.INTEGER, 4);item.setItemMeta(meta);return item;
    }
}
