package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;

/** ItemsAdder (re)loading its items drops Cooking's kept ItemsAdder templates. */
class ItemsAdderReloadHookTest {
    private static final String SALT = "ia.tfmc_cooking:salt";

    private ServerMock server;
    private MockedStatic<TLibs> tlibs;
    private ItemCreator creator;
    private Plugin cooking;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        ItemAPI api = mock(ItemAPI.class);
        creator = mock(ItemCreator.class);
        when(api.getCreator()).thenReturn(creator);
        when(creator.getItemFromPath(anyString())).thenAnswer(call -> new ItemStack(Material.PAPER));
        tlibs = mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        ItemRef.setCaching(false);
        cooking = MockBukkit.createMockPlugin("Cooking");
    }

    @AfterEach
    void tearDown() {
        ItemRef.setCaching(false);
        HandlerList.unregisterAll(cooking);
        tlibs.close();
        MockBukkit.unmock();
    }

    /** An event Bukkit refuses to register: it has no static getHandlerList. */
    public static class NoHandlerList extends org.bukkit.event.Event {
        @Override
        public HandlerList getHandlers() {
            return new HandlerList();
        }
    }

    private void resolveTwice() {
        ItemRef.resolve(SALT);
        ItemRef.resolve(SALT);
    }

    @Test
    void withoutItemsAdderNothingIsRegisteredAndTemplatesStayOff() {
        assertFalse(ItemsAdderReloadHook.registerIfPresent(cooking));
        resolveTwice();
        verify(creator, times(2)).getItemFromPath(SALT);
    }

    @Test
    void everyItemsAdderLoadDropsTheKeptTemplates() {
        MockBukkit.createMockPlugin("ItemsAdder");
        assertTrue(ItemsAdderReloadHook.registerIfPresent(cooking));

        resolveTwice();
        verify(creator, times(1)).getItemFromPath(SALT);

        server.getPluginManager().callEvent(new ItemsAdderLoadDataEvent());
        resolveTwice();
        verify(creator, times(2)).getItemFromPath(SALT);

        server.getPluginManager().callEvent(new ItemsAdderLoadDataEvent());
        resolveTwice();
        verify(creator, times(3)).getItemFromPath(SALT);
    }

    @Test
    void anItemsAdderWithoutTheLoadEventLeavesTemplatesOff() {
        MockBukkit.createMockPlugin("ItemsAdder");
        assertFalse(ItemsAdderReloadHook.registerIfPresent(cooking, "dev.lone.itemsadder.api.Events.Missing"));
        assertFalse(ItemsAdderReloadHook.registerIfPresent(cooking, String.class.getName()));
        assertFalse(ItemsAdderReloadHook.registerIfPresent(cooking, NoHandlerList.class.getName()));
        resolveTwice();
        verify(creator, times(2)).getItemFromPath(SALT);
    }
}
