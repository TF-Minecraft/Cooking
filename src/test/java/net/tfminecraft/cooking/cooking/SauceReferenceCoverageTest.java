package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SauceReferenceCoverageTest {
    private CookingReferencesTest.Environment env;

    @BeforeEach void setUp() { env = new CookingReferencesTest.Environment(); }
    @AfterEach void tearDown() { env.close(); }

    @Test
    void unavailableSauceOutputPreservesTheBatchAndLadleUntilRenderingRecovers() {
        SauceReference sauce = new SauceReference(env.furniture, Method.SAUCEPAN);
        env.player.getInventory().setItemInMainHand(
                env.stack(env.food("milk", "milk", "Milk"), Material.WHITE_DYE, 1));
        sauce.interact(env.interaction());
        env.player.getInventory().setItemInMainHand(
                env.stack(env.food("salt", "salt", "Salt"), Material.SUGAR, 2));
        sauce.interact(env.interaction());

        Map<String, FoodItem> ingredients = Map.copyOf(sauce.getSlots());
        Map<String, Integer> liquid = Map.copyOf(sauce.getSecondaries());
        List<String> colours = List.copyOf(sauce.getColours());
        Map<String, Object> saved = Map.copyOf(env.variables);
        Map<String, ItemStack> displayed = new LinkedHashMap<>();
        env.active.forEach((id, slot) -> displayed.put(id, slot.getCurrentItem().clone()));
        assertEquals(1, ingredients.size());
        assertEquals(Map.of("liquid", -1), liquid);
        ItemStack ladle = new ItemStack(Material.PAPER);
        env.player.getInventory().setItemInMainHand(ladle);
        clearInvocations(env.player, env.world);
        when(ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);

        assertDoesNotThrow(() -> sauce.scoop(env.player, ladle));

        assertEquals(ladle, env.player.getInventory().getItemInMainHand());
        assertEquals(ingredients, sauce.getSlots());
        assertEquals(liquid, sauce.getSecondaries());
        assertEquals(colours, sauce.getColours());
        assertEquals(saved, env.variables);
        assertEquals(displayed.keySet(), env.active.keySet());
        displayed.forEach((id, item) -> assertEquals(item, env.active.get(id).getCurrentItem()));
        assertFalse(sauce.isEmpty());
        assertEquals(0, cookedEvents());
        verify(env.player, never()).swingMainHand();
        verify(env.player).sendMessage(argThat((String message) ->
                message != null && message.toLowerCase(Locale.ROOT).contains("sauce")));

        // A saucepan contains one complete batch; a failed scoop must leave it collectable.
        when(ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenAnswer(invocation ->
                env.stack(invocation.getArgument(0), Material.PAPER, 1));
        sauce.scoop(env.player, ladle);

        assertTrue(sauce.isEmpty());
        assertTrue(env.active.isEmpty());
        assertTrue(env.player.getInventory().getItemInMainHand().getItemMeta()
                .getPersistentDataContainer().has(Keys.SAUCE_COLOUR, PersistentDataType.STRING));
        assertEquals(1, cookedEvents());
        sauce.scoop(env.player, ladle);
        assertEquals(1, cookedEvents(), "An empty saucepan must not produce a second serving");
    }

    private long cookedEvents() {
        return env.server.getPluginManager().getFiredEvents()
                .filter(DishCookedEvent.class::isInstance).count();
    }
}
