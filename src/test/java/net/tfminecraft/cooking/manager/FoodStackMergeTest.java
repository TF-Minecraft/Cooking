package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.kyori.adventure.text.Component;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.utils.Keys;

/**
 * Opening a chest copies one food over another equal one so the two stack. Only the aging may
 * differ; anything else, such as how far a roast has been carved, must stay with its own item.
 */
class FoodStackMergeTest {
	private static Cooking previous;

	@BeforeAll
	static void setUp() {
		MockBukkit.mock();
		previous = Cooking.plugin;
		Cooking plugin = mock(Cooking.class);
		when(plugin.namespace()).thenReturn("cooking");
		when(plugin.getName()).thenReturn("Cooking");
		Cooking.plugin = plugin;
	}

	@AfterAll
	static void tearDown() {
		Cooking.plugin = previous;
		MockBukkit.unmock();
	}

	private static ItemStack roast(int amount, long lastUpdate, String tags, String lore) {
		ItemStack item = new ItemStack(Material.COOKED_BEEF, amount);
		ItemMeta meta = item.getItemMeta();
		var pdc = meta.getPersistentDataContainer();
		pdc.set(Keys.FOOD_ID, PersistentDataType.STRING, "beef_roast");
		pdc.set(Keys.LAST_UPDATE, PersistentDataType.LONG, lastUpdate);
		pdc.set(Keys.TAGS, PersistentDataType.STRING, tags);
		meta.lore(List.of(Component.text(lore)));
		item.setItemMeta(meta);
		return item;
	}

	private static ItemStack with(ItemStack item, java.util.function.Consumer<ItemMeta> change) {
		ItemStack copy = item.clone();
		ItemMeta meta = copy.getItemMeta();
		change.accept(meta);
		copy.setItemMeta(meta);
		return copy;
	}

	@Test
	void foodsThatOnlyAgedDifferentlyCanMerge() {
		ItemStack older = roast(3, 1_000L, "cooked.3", "Fresh");
		ItemStack newer = roast(1, 9_000L, "cooked.7", "Fresh, 2 minutes");

		assertTrue(ConversionManager.sameApartFromAging(older, newer));
	}

	@Test
	void aCarvedRoastIsNotReplacedByAWholeOne() {
		ItemStack whole = roast(1, 1_000L, "cooked.3", "Fresh");
		ItemStack carved = with(whole, m -> m.getPersistentDataContainer()
				.set(Keys.CARVE_REMAINING, PersistentDataType.INTEGER, 2));
		ItemStack full = with(whole, m -> m.getPersistentDataContainer()
				.set(Keys.CARVE_REMAINING, PersistentDataType.INTEGER, 8));

		assertFalse(ConversionManager.sameApartFromAging(carved, full));
	}

	@Test
	void anyOtherDifferenceKeepsTheItemsApart() {
		ItemStack plain = roast(1, 1_000L, "cooked.3", "Fresh");

		assertFalse(ConversionManager.sameApartFromAging(plain, with(plain, m -> m.getPersistentDataContainer()
				.set(Keys.BASE_FOOD, PersistentDataType.DOUBLE, 12.0))));
		assertFalse(ConversionManager.sameApartFromAging(plain, with(plain, m -> m.getPersistentDataContainer()
				.set(Keys.CATCH_SIZE_CM, PersistentDataType.INTEGER, 90))));
		assertFalse(ConversionManager.sameApartFromAging(plain, new ItemStack(Material.COOKED_PORKCHOP)));
	}
}
