package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

class LegacyFishScanSkipTest {

    @Test
    void onlyMatchingFishAreCheckedOnEveryScan() {
        LegacyFishScan scan = spy(new LegacyFishScan());
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack stack = mock(ItemStack.class);
        doNothing().when(scan).update(any(), any(), anyInt(), any());

        doReturn(false).when(scan).matches(stack);
        assertEquals(ItemScanHandler.WHEN_CHANGED, scan.scan(player, inventory, 3, stack));
        verify(scan, never()).update(any(), any(), anyInt(), any());

        doReturn(true).when(scan).matches(stack);
        assertEquals(ItemScanHandler.EVERY_SCAN, scan.scan(player, inventory, 3, stack));
        verify(scan).update(player, inventory, 3, stack);
    }
}
