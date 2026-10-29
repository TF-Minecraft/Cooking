package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import net.tfminecraft.tlibs.objects.api.subapi.ItemPathHandler;

/** ItemsAdder templates are built once per ref and every caller gets its own copy. */
class ItemRefTemplateCacheTest {
    private static final String SALT = "ia.tfmc_cooking:salt";
    private static final NamespacedKey MARK = new NamespacedKey("cookingtest", "mark");

    private MockedStatic<TLibs> tlibs;
    private ItemAPI api;
    private ItemCreator creator;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        api = mock(ItemAPI.class);
        creator = mock(ItemCreator.class);
        when(api.getCreator()).thenReturn(creator);
        tlibs = mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        when(creator.getItemFromPath(anyString())).thenAnswer(call -> named(Material.PAPER, call.getArgument(0)));
        ItemRef.setCaching(true);
    }

    @AfterEach
    void tearDown() {
        ItemRef.setCaching(false);
        tlibs.close();
        MockBukkit.unmock();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        return stack;
    }

    @SuppressWarnings("deprecation")
    private static String name(ItemStack stack) {
        return stack.getItemMeta().getDisplayName();
    }

    @Test
    void anItemsAdderRefIsBuiltOnceAndEveryResolveIsAnIndependentCopy() {
        ItemStack first = ItemRef.resolve(SALT);
        ItemStack second = ItemRef.resolve(SALT);
        ItemStack third = ItemRef.resolve(SALT);

        verify(creator, times(1)).getItemFromPath(SALT);
        assertEquals(first, second);
        assertEquals(second, third);
        assertNotSame(first, second);
        assertNotSame(second, third);

        // Whatever a caller does to its copy, later copies come out as built.
        for (ItemStack used : List.of(first, second)) {
            used.setAmount(12);
            ItemMeta meta = used.getItemMeta();
            meta.getPersistentDataContainer().set(MARK, PersistentDataType.STRING, "changed");
            used.setItemMeta(meta);
            used.setType(Material.STONE);
        }
        ItemStack fresh = ItemRef.resolve(SALT);
        assertEquals(Material.PAPER, fresh.getType());
        assertEquals(1, fresh.getAmount());
        assertEquals(SALT, name(fresh));
        assertFalse(fresh.getItemMeta().getPersistentDataContainer().has(MARK));
        assertEquals(third, fresh);
        verify(creator, times(1)).getItemFromPath(SALT);
    }

    @Test
    @SuppressWarnings("deprecation")
    void applySharesTheTemplateButMergesIntoItsOwnCopy() {
        ItemStack source = named(Material.APPLE, "Salted Apple");
        source.setAmount(5);
        ItemMeta meta = source.getItemMeta();
        meta.setLore(List.of("Nutrition 1", "Food 2"));
        meta.getPersistentDataContainer().set(MARK, PersistentDataType.STRING, "food");
        source.setItemMeta(meta);

        ItemStack first = ItemRef.apply(SALT, source);
        ItemStack second = ItemRef.apply(SALT, source);
        ItemStack plain = ItemRef.resolve(SALT);

        verify(creator, times(1)).getItemFromPath(SALT);
        assertNotSame(first, second);
        for (ItemStack out : List.of(first, second)) {
            assertEquals(Material.PAPER, out.getType());
            assertEquals(5, out.getAmount());
            assertEquals("Salted Apple", name(out));
            assertEquals(List.of("Nutrition 1", "Food 2"), out.getItemMeta().getLore());
            assertEquals("food", out.getItemMeta().getPersistentDataContainer().get(MARK, PersistentDataType.STRING));
        }
        // The merge never reached the kept template.
        assertEquals(1, plain.getAmount());
        assertEquals(SALT, name(plain));
        assertFalse(plain.getItemMeta().hasLore());
        assertFalse(plain.getItemMeta().getPersistentDataContainer().has(MARK));
    }

    @Test
    void applyOfAnUnknownRefStillReturnsNothing() {
        when(creator.getItemFromPath("ia.tfmc_cooking:missing")).thenReturn(null);
        assertNull(ItemRef.apply("ia.tfmc_cooking:missing", new ItemStack(Material.APPLE)));
        assertNull(ItemRef.apply("ia.tfmc_cooking:missing", null));
        verify(creator, times(2)).getItemFromPath("ia.tfmc_cooking:missing");
    }

    @Test
    void failedBuildsAreRetriedEveryTimeAndReturnedAsTLibsMadeThem() {
        ItemStack air = new ItemStack(Material.AIR);
        ItemStack dirt = new ItemStack(Material.DIRT);
        when(creator.getItemFromPath("ia.tfmc_cooking:none")).thenReturn(null);
        when(creator.getItemFromPath("ia.tfmc_cooking:air")).thenReturn(air);
        // TLibs hands out dirt when ItemsAdder is missing or has not loaded the item yet.
        when(creator.getItemFromPath("ia.tfmc_cooking:unloaded")).thenReturn(dirt);

        for (int i = 0; i < 3; i++) {
            assertNull(ItemRef.resolve("ia.tfmc_cooking:none"));
            assertSame(air, ItemRef.resolve("ia.tfmc_cooking:air"));
            assertSame(dirt, ItemRef.resolve("ia.tfmc_cooking:unloaded"));
        }
        verify(creator, times(3)).getItemFromPath("ia.tfmc_cooking:none");
        verify(creator, times(3)).getItemFromPath("ia.tfmc_cooking:air");
        verify(creator, times(3)).getItemFromPath("ia.tfmc_cooking:unloaded");

        // Once ItemsAdder has the item, it is kept from then on.
        ItemStack loaded = named(Material.PAPER, "Loaded");
        when(creator.getItemFromPath("ia.tfmc_cooking:unloaded")).thenReturn(loaded);
        assertSame(loaded, ItemRef.resolve("ia.tfmc_cooking:unloaded"));
        assertEquals(loaded, ItemRef.resolve("ia.tfmc_cooking:unloaded"));
        verify(creator, times(4)).getItemFromPath("ia.tfmc_cooking:unloaded");
    }

    @Test
    void refsThatAreNotPlainItemsAdderBuildsAreNeverKept() {
        ItemPathHandler cooking = mock(ItemPathHandler.class);
        when(api.getPathHandler("c")).thenReturn(cooking);
        List<String> refs = java.util.Arrays.asList("v.paper", "V.paper", "m.material.SALT", "M.material.SALT",
                "modeled.(type=paper;model=5)", "magic.staff", "c.bread", "C.bread", "fill_1", "ia.", ".salt", null);
        for (String ref : refs) {
            ItemStack built = named(Material.PAPER, String.valueOf(ref));
            when(creator.getItemFromPath(ref)).thenReturn(built);
            assertSame(built, ItemRef.resolve(ref), ref);
            assertSame(built, ItemRef.resolve(ref), ref);
            verify(creator, times(2)).getItemFromPath(ref);
        }
    }

    @Test
    void aPrefixThatGainsAPathHandlerIsNoLongerServedFromTheCache() {
        ItemStack kept = ItemRef.resolve("x.thing");
        assertEquals(kept, ItemRef.resolve("x.thing"));
        verify(creator, times(1)).getItemFromPath("x.thing");

        when(api.getPathHandler("x")).thenReturn(mock(ItemPathHandler.class));
        ItemRef.resolve("x.thing");
        verify(creator, times(2)).getItemFromPath("x.thing");
    }

    @Test
    void clearingTheCacheRebuildsEveryRef() {
        ItemRef.resolve(SALT);
        ItemRef.resolve(SALT);
        verify(creator, times(1)).getItemFromPath(SALT);

        ItemRef.clearCache();
        ItemRef.resolve(SALT);
        ItemRef.resolve(SALT);
        verify(creator, times(2)).getItemFromPath(SALT);
    }

    @Test
    void turningCachingOffBuildsFreshEveryTimeAndDropsWhatWasKept() {
        ItemRef.resolve(SALT);
        ItemRef.setCaching(false);
        ItemStack built = named(Material.PAPER, "Fresh");
        when(creator.getItemFromPath(SALT)).thenReturn(built);
        assertSame(built, ItemRef.resolve(SALT));
        assertSame(built, ItemRef.resolve(SALT));
        verify(creator, times(3)).getItemFromPath(SALT);

        ItemRef.setCaching(true);
        assertSame(built, ItemRef.resolve(SALT));
        assertNotSame(built, ItemRef.resolve(SALT));
        verify(creator, times(4)).getItemFromPath(SALT);
    }

    @Test
    void aNewTLibsCreatorStartsAnEmptyCache() {
        ItemRef.resolve(SALT);
        ItemCreator replacement = mock(ItemCreator.class);
        when(replacement.getItemFromPath(SALT)).thenReturn(named(Material.PAPER, "New"));
        when(api.getCreator()).thenReturn(replacement);

        assertEquals("New", name(ItemRef.resolve(SALT)));
        assertEquals("New", name(ItemRef.resolve(SALT)));
        verify(replacement, times(1)).getItemFromPath(SALT);
        verify(creator, times(1)).getItemFromPath(SALT);
    }

    @Test
    void aMissingCreatorFailsLikeBefore() {
        when(api.getCreator()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> ItemRef.resolve(SALT));
        assertThrows(NullPointerException.class, () -> ItemRef.apply(SALT, null));
    }
}
