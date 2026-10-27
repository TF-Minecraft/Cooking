package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Ladles stack, so using one must leave the rest of the held stack with the player. */
class HeldStackTest {
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final Location feet = new Location(world, 1, 65, 1);
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = MockBukkit.mock().addPlayer().getInventory();
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(feet);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private int count(Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    @Test
    void aSingleLadleBecomesTheResultInTheHand() {
        inventory.setItemInMainHand(new ItemStack(Material.STICK));

        HeldStack.useOneIntoHand(player, new ItemStack(Material.PAPER));

        assertEquals(new ItemStack(Material.PAPER), inventory.getItemInMainHand());
        assertEquals(0, count(Material.STICK));
    }

    @Test
    void scoopingWithAStackHoldsTheResultAndKeepsTheSpareLadles() {
        inventory.setItemInMainHand(new ItemStack(Material.STICK, 3));

        HeldStack.useOneIntoHand(player, new ItemStack(Material.PAPER));

        assertEquals(new ItemStack(Material.PAPER), inventory.getItemInMainHand());
        assertEquals(2, count(Material.STICK));
        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test
    void aSingleFilledLadleBecomesTheEmptyLadleInTheHand() {
        inventory.setItemInMainHand(new ItemStack(Material.PAPER));

        HeldStack.useOneKeepStack(player, new ItemStack(Material.STICK));

        assertEquals(new ItemStack(Material.STICK), inventory.getItemInMainHand());
        assertEquals(0, count(Material.PAPER));
    }

    @Test
    void usingOneOfAFilledStackKeepsTheRestInHandAndGivesTheEmptyLadle() {
        inventory.setItemInMainHand(new ItemStack(Material.PAPER, 3));

        HeldStack.useOneKeepStack(player, new ItemStack(Material.STICK));

        assertEquals(new ItemStack(Material.PAPER, 2), inventory.getItemInMainHand());
        assertEquals(1, count(Material.STICK));
        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test
    void whatDoesNotFitIsDroppedAtThePlayersFeet() {
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        inventory.setItemInMainHand(new ItemStack(Material.STICK, 3));

        HeldStack.useOneIntoHand(player, new ItemStack(Material.PAPER));

        assertEquals(new ItemStack(Material.PAPER), inventory.getItemInMainHand());
        verify(world).dropItemNaturally(any(), argThat(item -> item.getType() == Material.STICK && item.getAmount() == 2));

        inventory.setItemInMainHand(new ItemStack(Material.PAPER, 2));
        HeldStack.useOneKeepStack(player, new ItemStack(Material.BOWL));

        assertEquals(new ItemStack(Material.PAPER), inventory.getItemInMainHand());
        verify(world).dropItemNaturally(any(), argThat(item -> item.getType() == Material.BOWL));
    }
}
