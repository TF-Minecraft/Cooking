package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;

/** types.yml returns: honey used up in a station gives back its empty bottle. */
class FoodReturnsTest {
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private PlayerInventory inventory;
    private MockedStatic<ItemRef> refs;
    private Cooking previous;

    @BeforeEach
    void setUp() {
        inventory = MockBukkit.mock().addPlayer().getInventory();
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 65, 1));
        refs = mockStatic(ItemRef.class);
        refs.when(() -> ItemRef.resolve("v.glass_bottle")).thenAnswer(inv -> new ItemStack(Material.GLASS_BOTTLE, 3));
        refs.when(() -> ItemRef.resolve("v.air")).thenAnswer(inv -> new ItemStack(Material.AIR));
        refs.when(() -> ItemRef.resolve("missing")).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        refs.close();
        Cooking.plugin = previous;
        MockBukkit.unmock();
    }

    private static FoodItem food(String returns) {
        MemoryConfiguration type = new MemoryConfiguration();
        if (returns != null) type.set("returns", returns);
        return new FoodItem("honey", type);
    }

    private int count(Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    @Test
    void theReturnedItemIsOneFreshCopy_orNothing() {
        assertEquals(new ItemStack(Material.GLASS_BOTTLE), FoodReturns.returnedBy(food(" v.glass_bottle ")));
        assertEquals("v.glass_bottle", new FoodItem(food("v.glass_bottle")).getReturns(), "copies keep it");
        assertNull(FoodReturns.returnedBy(null));
        assertNull(FoodReturns.returnedBy(food(null)));
        assertNull(FoodReturns.returnedBy(food(" ")));
        assertNull(FoodReturns.returnedBy(food("missing")));
        assertNull(FoodReturns.returnedBy(food("v.air")));
    }

    @Test
    void usingOneGivesTheBottleBack_orJustUsesOne() {
        inventory.setItemInMainHand(new ItemStack(Material.HONEY_BOTTLE, 2));
        FoodReturns.useOne(player, food("v.glass_bottle"));
        assertEquals(1, inventory.getItemInMainHand().getAmount());
        assertEquals(1, count(Material.GLASS_BOTTLE));

        FoodReturns.useOne(player, food("v.glass_bottle"));
        assertEquals(Material.GLASS_BOTTLE, inventory.getItemInMainHand().getType(), "the last one leaves the bottle in hand");

        inventory.setItemInMainHand(new ItemStack(Material.SUGAR, 2));
        FoodReturns.useOne(player, food(null));
        assertEquals(1, inventory.getItemInMainHand().getAmount());
        FoodReturns.useOne(player, food(null));
        assertTrue(inventory.getItemInMainHand().getType().isAir());
    }

    @Test
    void afterAStationTookOne_theBottleGoesToTheEmptyHand_theInventory_orTheGround() {
        inventory.setItemInMainHand(null);
        FoodReturns.giveBack(player, food("v.glass_bottle"));
        assertEquals(new ItemStack(Material.GLASS_BOTTLE), inventory.getItemInMainHand());

        inventory.setItemInMainHand(new ItemStack(Material.HONEY_BOTTLE, 1));
        inventory.getItemInMainHand().setAmount(0);
        FoodReturns.giveBack(player, food("v.glass_bottle"));
        assertEquals(Material.GLASS_BOTTLE, inventory.getItemInMainHand().getType());

        inventory.setItemInMainHand(new ItemStack(Material.HONEY_BOTTLE, 1));
        FoodReturns.giveBack(player, food("v.glass_bottle"));
        assertEquals(Material.HONEY_BOTTLE, inventory.getItemInMainHand().getType());
        assertEquals(1, count(Material.GLASS_BOTTLE));

        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        inventory.setItemInOffHand(new ItemStack(Material.STONE, 64));
        FoodReturns.giveBack(player, food("v.glass_bottle"));
        verify(world).dropItemNaturally(any(Location.class), argThat(item -> item.getType() == Material.GLASS_BOTTLE));

        FoodReturns.giveBack(player, food(null));
        verifyNoMoreInteractions(world);
    }
}
