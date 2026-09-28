package net.tfminecraft.cooking.cup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A drunk cup leaves a glass bottle in its slot, which becomes an empty cup there and nowhere else. */
class EmptyCupReturnTest {
	private PlayerMock player;
	private PlayerInventory inventory;

	@BeforeEach
	void setUp() {
		player = MockBukkit.mock().addPlayer();
		inventory = player.getInventory();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private static ItemStack cup() {
		return new ItemStack(Material.BOWL);
	}

	@Test
	void theBottleInTheSlotDrunkFromBecomesACup() {
		inventory.setItem(3, new ItemStack(Material.GLASS_BOTTLE));

		DrinkConsumeListener.replaceWithEmptyCup(player, 1, 3, EmptyCupReturnTest::cup);

		assertEquals(Material.BOWL, inventory.getItem(3).getType());
	}

	@Test
	void switchingSlotsDoesNotOverwriteTheNewItem() {
		inventory.setItem(3, new ItemStack(Material.GLASS_BOTTLE));
		inventory.setItem(5, new ItemStack(Material.DIAMOND_SWORD));
		inventory.setHeldItemSlot(5);

		DrinkConsumeListener.replaceWithEmptyCup(player, 1, 3, EmptyCupReturnTest::cup);

		assertEquals(Material.BOWL, inventory.getItem(3).getType());
		assertEquals(Material.DIAMOND_SWORD, inventory.getItem(5).getType());
	}

	@Test
	void aBottleMovedAwayGetsNoCupAsWell() {
		inventory.setItem(3, new ItemStack(Material.BREAD));

		DrinkConsumeListener.replaceWithEmptyCup(player, 1, 3, () -> fail("no cup should be made"));

		assertEquals(Material.BREAD, inventory.getItem(3).getType());
	}

	@Test
	void theOffHandIsUsedWhenTheCupWasDrunkFromIt() {
		inventory.setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));

		DrinkConsumeListener.replaceWithEmptyCup(player, 1, -1, EmptyCupReturnTest::cup);

		assertEquals(Material.BOWL, inventory.getItemInOffHand().getType());
	}
}
