package net.tfminecraft.cooking.brewery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import com.dre.brewery.api.events.IngedientAddEvent;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.StationFoodTemplates;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.Keys;

class BreweryPotatoBridgeTest {
    private Cooking previousPlugin;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private JavaPlugin owner;
    private PluginManager manager;
    private Plugin brewery;
    private Logger logger;
    private BreweryPotatoBridge bridge;

    @BeforeEach void setUp() {
        MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        tracks = new ArrayList<>(TrackLoader.oList);
        FoodLoader.oList.clear();
        TrackLoader.oList.clear();
        TrackLoader.oList.add(StationFoodTemplates.freshness());
        for (String id : List.of("vegetable_1", "vegetable_cut", "vegetable_chopped", "soup", "apple")) {
            FoodLoader.oList.add(StationFoodTemplates.template(id, "vegetable"));
        }
        owner = mock(JavaPlugin.class, RETURNS_DEEP_STUBS);
        manager = owner.getServer().getPluginManager();
        brewery = mock(Plugin.class);
        when(brewery.getName()).thenReturn("BreweryX");
        when(brewery.isEnabled()).thenReturn(true);
        when(manager.getPlugin("BreweryX")).thenReturn(brewery);
        logger = mock(Logger.class);
        when(owner.getLogger()).thenReturn(logger);
        bridge = new BreweryPotatoBridge(owner);
    }

    @AfterEach void tearDown() {
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock();
        Cooking.plugin = previousPlugin;
    }

    @Test void translatesFreshStaleAndRottenWholePotatoesWithoutChangingTheOriginalStack() throws Exception {
        EventExecutor executor = register();
        for (int age : List.of(0, 86400, StationFoodTemplates.ROTTEN)) {
            ItemStack held = food("vegetable_1", "pOtAtO", age == 0 ? Material.POTATO : Material.APPLE, age);
            ItemStack before = held.clone();
            IngedientAddEvent event = new IngedientAddEvent(held.clone());
            executor.execute(bridge, event);
            assertEquals(new ItemStack(Material.POTATO), event.getIngredient());
            assertEquals(before, held, "quantity, model, quality and freshness must remain on the held item");
            assertFalse(event.isCancelled());
        }
    }

    @Test void leavesVanillaApplesPotatoesOtherOriginsAndProcessedFoodsUntouched() throws Exception {
        EventExecutor executor = register();
        List<ItemStack> inputs = new ArrayList<>(List.of(new ItemStack(Material.APPLE), new ItemStack(Material.POTATO),
                food("vegetable_1", "Carrot", Material.APPLE, StationFoodTemplates.ROTTEN),
                food("vegetable_1", null, Material.APPLE, 0),
                food("vegetable_cut", "Potato", Material.APPLE, 0),
                food("vegetable_chopped", "Potato", Material.APPLE, 0),
                food("soup", "Potato", Material.APPLE, 0),
                food("apple", "Apple", Material.APPLE, 0)));
        inputs.add(null);
        for (ItemStack input : inputs) {
            IngedientAddEvent event = new IngedientAddEvent(input);
            executor.execute(bridge, event);
            assertSame(input, event.getIngredient());
        }
    }

    @Test void registersOnceAtHighPriorityAndRespectsOtherPluginsCancellations() {
        register();
        bridge.registerIfPresent();
        bridge.onPluginEnable(new PluginEnableEvent(brewery));
        Plugin other = mock(Plugin.class);
        when(other.getName()).thenReturn("ItemsAdder");
        bridge.onPluginEnable(new PluginEnableEvent(other));
        verify(manager, times(1)).registerEvent(eq(IngedientAddEvent.class), same(bridge),
                eq(EventPriority.HIGH), any(), same(owner), eq(true));
    }

    @Test void actualEventDispatchSkipsCancelledAdditionsAndTranslatesAcceptedOnes() {
        JavaPlugin realOwner = MockBukkit.createMockPlugin("PotatoBridgeTest");
        MockBukkit.createMockPlugin("BreweryX");
        BreweryPotatoBridge realBridge = new BreweryPotatoBridge(realOwner);
        realBridge.registerIfPresent();
        ItemStack original = food("vegetable_1", "Potato", Material.APPLE, StationFoodTemplates.ROTTEN);
        IngedientAddEvent cancelled = new IngedientAddEvent(original);
        cancelled.setCancelled(true);
        realOwner.getServer().getPluginManager().callEvent(cancelled);
        assertSame(original, cancelled.getIngredient());
        assertTrue(cancelled.isCancelled());

        IngedientAddEvent accepted = new IngedientAddEvent(original.clone());
        realOwner.getServer().getPluginManager().callEvent(accepted);
        assertEquals(new ItemStack(Material.POTATO), accepted.getIngredient());
        assertEquals(10, original.getAmount());
        assertEquals(Material.APPLE, original.getType());
    }

    @Test void staysOptionalAndRegistersWhenBreweryEnablesLater() {
        when(manager.getPlugin("BreweryX")).thenReturn(null);
        bridge.registerIfPresent();
        when(manager.getPlugin("BreweryX")).thenReturn(brewery);
        when(brewery.isEnabled()).thenReturn(false);
        bridge.registerIfPresent();
        verify(manager, never()).registerEvent(any(), any(), any(), any(), any(), anyBoolean());
        when(brewery.isEnabled()).thenReturn(true);
        bridge.onPluginEnable(new PluginEnableEvent(brewery));
        verify(manager).registerEvent(eq(IngedientAddEvent.class), same(bridge), any(), any(), same(owner), eq(true));
    }

    @Test void reportsRegistrationFailureAndCanRetry() {
        doThrow(new NoClassDefFoundError("optional API")).doNothing().when(manager)
                .registerEvent(any(), any(), any(), any(), any(), anyBoolean());
        bridge.registerIfPresent();
        verify(logger).warning(contains("whole-potato bridge could not register"));
        bridge.registerIfPresent();
        verify(logger).info("BreweryX whole-potato ingredient bridge enabled.");
    }

    @Test void cancelsInsteadOfRecordingAnAppleIfTheOptionalApiFails() {
        EventExecutor executor = register();
        for (boolean readFailure : List.of(true, false)) {
            IngedientAddEvent event = new IngedientAddEvent(food("vegetable_1", "Potato", Material.APPLE, 0));
            event.failRead = readFailure;
            event.failWrite = !readFailure;
            assertThrows(EventException.class, () -> executor.execute(bridge, event));
            assertTrue(event.isCancelled());
        }
    }

    private EventExecutor register() {
        bridge.registerIfPresent();
        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(manager).registerEvent(eq(IngedientAddEvent.class), any(Listener.class), eq(EventPriority.HIGH),
                executor.capture(), same(owner), eq(true));
        return executor.getValue();
    }

    private ItemStack food(String id, String origin, Material material, int age) {
        ItemStack item = new ItemStack(material, 10);
        var meta = item.getItemMeta();
        meta.setDisplayName("Rotten Potato"); // Names alone must never identify an ingredient.
        meta.setCustomModelData(42007);
        var data = meta.getPersistentDataContainer();
        data.set(Keys.FOOD_ID, PersistentDataType.STRING, id);
        if (origin != null) data.set(Keys.ORIGIN, PersistentDataType.STRING, origin);
        data.set(Keys.QUALITY, PersistentDataType.INTEGER, 4);
        data.set(Keys.TAGS, PersistentDataType.STRING, "freshness." + age);
        item.setItemMeta(meta);
        return item;
    }
}
