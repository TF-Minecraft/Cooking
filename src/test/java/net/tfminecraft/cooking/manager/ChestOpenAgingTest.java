package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

/** Opening a chest makes equal foods stack by giving them all the most aged copy's data. */
class ChestOpenAgingTest {
    private Cooking previous;
    private List<FoodItem> foods;
    private List<TagTrack> tracks;
    private ServerMock server;
    private PlayerMock player;
    private Inventory chest;
    private long now;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        tracks = new ArrayList<>(TrackLoader.oList);
        TrackLoader.oList.clear();
        TrackLoader.oList.add(new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Fresh", 0, 1, 1), new TagStep("stale", "Stale", 1_000_000, 1, 1))));
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "beef");
        FoodItem beef = new FoodItem("beef", config);
        beef.addOrModifyTrack(TrackLoader.getByString("freshness"));
        FoodLoader.oList.add(beef);
        chest = server.createInventory(null, 9);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        MockBukkit.unmock();
        Cooking.plugin = previous;
    }

    /** Beef whose stored freshness is value as of secondsAgo seconds ago. */
    private ItemStack beef(int amount, int value, long secondsAgo) {
        FoodItem food = new FoodItem(FoodLoader.getByString("beef"));
        food.getTagTrack("freshness").setValue(value);
        food.setLastUpdate(now - secondsAgo * 1000L);
        return ItemBuilder.stamp(new ItemStack(Material.BEEF, amount), food, "beef");
    }

    private void open() {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(chest);
        when(view.getPlayer()).thenReturn(player);
        new ConversionManager().onOpen(new InventoryOpenEvent(view));
    }

    private static long clock(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(Keys.LAST_UPDATE, PersistentDataType.LONG);
    }

    @Test
    void theFresherFoodTakesTheOlderDataWhereverEitherIs() {
        chest.setItem(0, beef(2, 0, 0));
        chest.setItem(5, beef(1, 0, 100));
        player.getInventory().setItem(3, beef(4, 0, 0));

        open();

        long older = now - 100_000L;
        assertEquals(older, clock(chest.getItem(0)));
        assertEquals(older, clock(chest.getItem(5)));
        assertEquals(older, clock(player.getInventory().getItem(3)));
        assertEquals(2, chest.getItem(0).getAmount());
        assertEquals(1, chest.getItem(5).getAmount());
        assertEquals(4, player.getInventory().getItem(3).getAmount());
    }

    @Test
    void equallyAgedFoodAllTakesTheFirstSlotsCopy() {
        // Ten points stored now and none stored ten seconds ago are the same age.
        chest.setItem(1, beef(1, 10, 0));
        chest.setItem(4, beef(1, 0, 10));

        open();

        assertEquals(now, clock(chest.getItem(1)));
        assertEquals(now, clock(chest.getItem(4)));
    }

    @Test
    void foodAtAnotherFreshnessStepKeepsItsOwnData() {
        chest.setItem(0, beef(1, 0, 0));
        chest.setItem(1, beef(1, 1_000_000, 0));
        chest.setItem(2, new ItemStack(Material.STONE));

        open();

        assertEquals(1_000_000, FoodItem.fromItem(chest.getItem(1)).getTagTrack("freshness").getValue());
        assertEquals(0, FoodItem.fromItem(chest.getItem(0)).getTagTrack("freshness").getValue());
        assertEquals(Material.STONE, chest.getItem(2).getType());
    }
}
