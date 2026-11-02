package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.FoodScan;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

class TagManagerScanTest {
    Cooking plugin;
    List<FoodItem> foods;
    ServerMock server;
    PlayerMock player;
    TagManager tags = new TagManager();
    MockedStatic<FoodScan> scans;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "Steak");
        FoodLoader.oList.add(new FoodItem("steak", config));
        player = server.addPlayer();
        scans = mockStatic(FoodScan.class);
    }

    @AfterEach
    void cleanup() {
        scans.close();
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        MockBukkit.unmock();
        Cooking.plugin = plugin;
    }

    @Test
    void nonFoodAndUnknownFoodWaitUntilTheItemChanges() {
        assertEquals(ItemScanHandler.WHEN_CHANGED, tags.scan(player, null, 0, new ItemStack(Material.STONE)));
        ItemStack unknown = new ItemStack(Material.BEEF);
        var meta = unknown.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.FOOD_ID, PersistentDataType.STRING, "missing");
        unknown.setItemMeta(meta);
        assertEquals(ItemScanHandler.WHEN_CHANGED, tags.scan(player, null, 0, unknown));
        scans.verifyNoInteractions();
    }

    @Test
    void theSlotGetsWhatFoodScanBuildsWithHeldContextForTheSelectedOrOffhandSlot() {
        ItemStack steak = steak();
        ItemStack rebuilt = new ItemStack(Material.COOKED_BEEF, 2);
        scans.when(() -> FoodScan.update(any(), any(), any(), anyBoolean())).thenReturn(new FoodScan.Result(rebuilt, 123L));
        PlayerInventory inventory = player.getInventory();
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, steak);
        assertEquals(123L, tags.scan(player, inventory, 0, steak));
        scans.verify(() -> FoodScan.update(eq(steak), any(), argThat(food -> food.getId().equals("steak")), eq(true)));
        assertEquals(Material.COOKED_BEEF, inventory.getItem(0).getType());

        tags.update(player, inventory, 1, steak);
        scans.verify(() -> FoodScan.update(eq(steak), any(), any(), eq(false)));
        assertEquals(Material.COOKED_BEEF, inventory.getItem(1).getType());

        // An item being picked up, or no slot, is never written anywhere.
        Inventory chest = server.createInventory(null, 9);
        assertEquals(123L, tags.scan(player, null, 0, steak));
        assertEquals(123L, tags.scan(player, chest, -1, steak));
        assertTrue(chest.isEmpty());
    }

    @Test
    void nothingToWriteOrAFailedRebuildLeavesTheFoodInItsSlot() {
        ItemStack steak = steak();
        Inventory chest = server.createInventory(null, 9);
        chest.setItem(0, steak);
        scans.when(() -> FoodScan.update(any(), any(), any(), anyBoolean())).thenReturn(new FoodScan.Result(null, 5L));
        assertEquals(5L, tags.scan(player, chest, 0, steak));
        scans.when(() -> FoodScan.update(any(), any(), any(), anyBoolean()))
                .thenReturn(new FoodScan.Result(new ItemStack(Material.AIR), ItemScanHandler.EVERY_SCAN));
        assertEquals(ItemScanHandler.EVERY_SCAN, tags.scan(player, chest, 0, steak));
        assertEquals(steak, chest.getItem(0));
    }

    private ItemStack steak() {
        return ItemBuilder.stamp(new ItemStack(Material.BEEF, 2), new FoodItem(FoodLoader.getByString("steak")), "Steak");
    }
}
