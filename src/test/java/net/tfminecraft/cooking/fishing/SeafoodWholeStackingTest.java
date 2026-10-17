package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

/** Whole fish carry no per-catch CustomFishing data, so equal catches can stack. */
class SeafoodWholeStackingTest {
    private Cooking previous;
    private List<FoodItem> foods;
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        foods = new ArrayList<>(FoodLoader.oList);
        FoodLoader.oList.clear();
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "{inherit}");
        FoodLoader.oList.add(new FoodItem(CustomFishingCatalog.WHOLE_TYPE, config));
    }

    @AfterEach
    void tearDown() {
        FoodLoader.oList.clear();
        FoodLoader.oList.addAll(foods);
        Cooking.plugin = previous;
        MockBukkit.unmock();
    }

    /** A whole tuna as older versions built it: a CustomFishing id but no clean-catch marker. */
    private ItemStack whole() {
        return whole(Material.COD);
    }

    private ItemStack whole(Material material) {
        FoodItem food = new FoodItem(FoodLoader.getByString(CustomFishingCatalog.WHOLE_TYPE));
        food.setCatchSizeCm(80);
        food.setCustomFishingId("tfmc_tuna_fish");
        return ItemBuilder.stamp(new ItemStack(material), food, "Tuna");
    }

    private static boolean clean(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().has(Keys.CLEAN_CATCH, PersistentDataType.BYTE);
    }

    @Test
    void aPlainCatchKeepsItsTypeAndAmount() {
        ItemStack caught = mock(ItemStack.class);
        when(caught.getType()).thenReturn(Material.COD);
        when(caught.getAmount()).thenReturn(3);

        ItemStack look = SeafoodWholeItems.lookOf(caught);

        assertEquals(Material.COD, look.getType());
        assertEquals(3, look.getAmount());
    }

    @Test
    void aCatchWithDataOnlyHasItsLookCopied() {
        ItemStack caught = mock(ItemStack.class);
        ItemMeta from = mock(ItemMeta.class);
        when(caught.getType()).thenReturn(Material.COD);
        when(caught.getAmount()).thenReturn(1);
        when(caught.hasItemMeta()).thenReturn(true);
        when(caught.getItemMeta()).thenReturn(from);

        ItemStack look = SeafoodWholeItems.lookOf(caught);

        assertEquals(Material.COD, look.getType());
        verify(from).hasDisplayName();
        verify(from, never()).displayName();
        verify(from, never()).getPersistentDataContainer();
    }

    @Test
    void theLookIsTheNameItemNameModelDataAndItemModel() {
        ItemMeta from = mock(ItemMeta.class);
        ItemMeta to = mock(ItemMeta.class);
        Component name = Component.text("Tuna Fish");
        Component itemName = Component.text("Tuna");
        CustomModelDataComponent model = mock(CustomModelDataComponent.class);
        NamespacedKey itemModel = NamespacedKey.minecraft("cod");
        when(from.hasDisplayName()).thenReturn(true);
        when(from.displayName()).thenReturn(name);
        when(from.hasItemName()).thenReturn(true);
        when(from.itemName()).thenReturn(itemName);
        when(from.hasCustomModelDataComponent()).thenReturn(true);
        when(from.getCustomModelDataComponent()).thenReturn(model);
        when(from.hasItemModel()).thenReturn(true);
        when(from.getItemModel()).thenReturn(itemModel);

        assertSame(to, SeafoodWholeItems.copyLook(from, to));

        verify(to).displayName(name);
        verify(to).itemName(itemName);
        verify(to).setCustomModelDataComponent(model);
        verify(to).setItemModel(itemModel);
    }

    @Test
    void cleaningKeepsTheCookingDataAndLore() {
        ItemStack source = whole();
        // MockBukkit has no custom model data or item model components; copyLook covers those.
        ItemMeta meta = spy(source.getItemMeta());
        doReturn(false).when(meta).hasCustomModelDataComponent();
        doReturn(false).when(meta).hasItemModel();
        ItemStack fish = mock(ItemStack.class);
        when(fish.getType()).thenReturn(Material.COD);
        when(fish.getAmount()).thenReturn(2);
        when(fish.hasItemMeta()).thenReturn(true);
        when(fish.getItemMeta()).thenReturn(meta);

        ItemStack clean = SeafoodWholeItems.withoutCatchData(fish);

        assertEquals(2, clean.getAmount());
        assertEquals(meta.displayName(), clean.getItemMeta().displayName());
        assertEquals(meta.lore(), clean.getItemMeta().lore());
        var pdc = clean.getItemMeta().getPersistentDataContainer();
        assertEquals(CustomFishingCatalog.WHOLE_TYPE, pdc.get(Keys.FOOD_ID, PersistentDataType.STRING));
        assertEquals(80, pdc.get(Keys.CATCH_SIZE_CM, PersistentDataType.INTEGER));
    }

    @Test
    void onlyWholeCustomFishingFishBuiltOnTheCatchNeedCleaning() {
        ItemStack filet = new ItemStack(Material.COD);
        ItemMeta meta = filet.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.FOOD_ID, PersistentDataType.STRING, "seafood_fish_filet");
        filet.setItemMeta(meta);
        FoodItem vanilla = new FoodItem(FoodLoader.getByString(CustomFishingCatalog.WHOLE_TYPE));
        ItemStack cod = ItemBuilder.stamp(new ItemStack(Material.COD), vanilla, "Cod");

        assertFalse(SeafoodWholeItems.carriesCatchData(filet.getItemMeta().getPersistentDataContainer()));
        assertFalse(SeafoodWholeItems.carriesCatchData(cod.getItemMeta().getPersistentDataContainer()));
        assertTrue(SeafoodWholeItems.carriesCatchData(whole().getItemMeta().getPersistentDataContainer()));
    }

    @Test
    void newWholeFishAreMarkedClean() {
        ItemStack built = whole();
        try (var builder = mockStatic(ItemBuilder.class)) {
            builder.when(() -> ItemBuilder.buildSingleWithQuality(any(FoodItem.class), any(ItemStack.class), anyInt()))
                    .thenReturn(built, (ItemStack) null);
            ItemStack caught = mock(ItemStack.class);
            when(caught.getType()).thenReturn(Material.COD);
            when(caught.getAmount()).thenReturn(1);
            VanillaFish cod = new VanillaFish("COD", "Cod", "fish", 45);

            assertSame(built, SeafoodWholeItems.build(caught, cod, 2));
            assertNull(SeafoodWholeItems.build(caught, cod, 2));
        }
        assertTrue(clean(built));
        assertFalse(SeafoodWholeItems.carriesCatchData(built.getItemMeta().getPersistentDataContainer()));
    }

    @Test
    void theScanCleansOldWholeFishOfAnyFishMaterial() {
        PlayerMock player = server.addPlayer();
        ItemStack fish = whole(Material.SALMON);
        fish.setAmount(3);
        LegacyFishScan scan = new LegacyFishScan();
        assertFalse(scan.matches(new ItemStack(Material.STONE)));
        assertTrue(scan.matches(fish));

        try (var items = mockStatic(SeafoodWholeItems.class, CALLS_REAL_METHODS)) {
            items.when(() -> SeafoodWholeItems.copyLook(any(), any())).thenAnswer(call -> call.getArgument(1));
            player.getInventory().setItem(4, fish);
            scan.update(player, player.getInventory(), 4, fish);
        }

        ItemStack cleaned = player.getInventory().getItem(4);
        assertEquals(Material.SALMON, cleaned.getType());
        assertEquals(3, cleaned.getAmount());
        assertEquals(80, FoodItem.fromItem(cleaned).getCatchSizeCm());
        assertTrue(clean(cleaned));
        assertFalse(scan.matches(cleaned));
    }

    @Test
    void aHeldFishIsCleanedOnlyOnceItIsPutAway() {
        PlayerMock player = server.addPlayer();
        ItemStack fish = whole();
        int held = player.getInventory().getHeldItemSlot();
        player.getInventory().setItem(held, fish);

        new LegacyFishScan().update(player, player.getInventory(), held, fish);

        assertFalse(clean(player.getInventory().getItem(held)));
    }
}
