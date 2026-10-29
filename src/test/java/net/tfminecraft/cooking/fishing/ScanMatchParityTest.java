package net.tfminecraft.cooking.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.manager.TagManager;
import net.tfminecraft.cooking.utils.Keys;

/** The scan's key checks read the stack's data view and answer exactly as the old full-meta reads did. */
class ScanMatchParityTest {
    private Cooking previous;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        previous = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        when(Cooking.plugin.getName()).thenReturn("Cooking");
    }

    @AfterEach
    void tearDown() {
        Cooking.plugin = previous;
        MockBukkit.unmock();
    }

    // origin/main TagManager.matches
    private static boolean oldTagMatch(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(Keys.FOOD_ID, PersistentDataType.STRING);
    }

    // origin/main LegacyFishScan.matches, with the old carriesCatchData inlined
    private static boolean oldFishMatch(ItemStack stack) {
        if (stack == null || !Tag.ITEMS_FISHES.isTagged(stack.getType())) {
            return false;
        }
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        if (!data.has(Keys.FOOD_ID, PersistentDataType.STRING)) {
            return stack.getType() == Material.COD;
        }
        return CustomFishingCatalog.WHOLE_TYPE.equals(data.get(Keys.FOOD_ID, PersistentDataType.STRING))
                && data.has(Keys.CUSTOM_FISHING_ID, PersistentDataType.STRING)
                && !data.has(Keys.CLEAN_CATCH, PersistentDataType.BYTE);
    }

    private interface Edit {
        void apply(PersistentDataContainer data);
    }

    private static ItemStack stack(Material material, Edit edit) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        edit.apply(meta.getPersistentDataContainer());
        stack.setItemMeta(meta);
        return stack;
    }

    private static Map<String, ItemStack> samples() {
        Map<String, ItemStack> samples = new LinkedHashMap<>();
        samples.put("null", null);
        samples.put("air", new ItemStack(Material.AIR));
        for (Material material : List.of(Material.STONE, Material.BREAD, Material.COD, Material.SALMON,
                Material.TROPICAL_FISH, Material.PUFFERFISH, Material.COOKED_COD)) {
            String name = material.name().toLowerCase();
            samples.put(name + " without meta", new ItemStack(material));
            ItemStack renamed = new ItemStack(material);
            ItemMeta meta = renamed.getItemMeta();
            meta.setUnbreakable(true);
            renamed.setItemMeta(meta);
            samples.put(name + " with meta but no data", renamed);
            samples.put(name + " food", stack(material, d -> d.set(Keys.FOOD_ID, PersistentDataType.STRING, "bread")));
            samples.put(name + " food id stored as a number",
                    stack(material, d -> d.set(Keys.FOOD_ID, PersistentDataType.INTEGER, 3)));
            samples.put(name + " other cooking key only",
                    stack(material, d -> d.set(Keys.ORIGIN, PersistentDataType.STRING, "Bread")));
            samples.put(name + " food id under another plugin",
                    stack(material, d -> d.set(new NamespacedKey("other", "food_id"), PersistentDataType.STRING, "x")));
            samples.put(name + " old whole fish", stack(material, d -> {
                d.set(Keys.FOOD_ID, PersistentDataType.STRING, CustomFishingCatalog.WHOLE_TYPE);
                d.set(Keys.CUSTOM_FISHING_ID, PersistentDataType.STRING, "tfmc_tuna_fish");
            }));
            samples.put(name + " clean whole fish", stack(material, d -> {
                d.set(Keys.FOOD_ID, PersistentDataType.STRING, CustomFishingCatalog.WHOLE_TYPE);
                d.set(Keys.CUSTOM_FISHING_ID, PersistentDataType.STRING, "tfmc_tuna_fish");
                d.set(Keys.CLEAN_CATCH, PersistentDataType.BYTE, (byte) 1);
            }));
            samples.put(name + " whole fish without a catch", stack(material,
                    d -> d.set(Keys.FOOD_ID, PersistentDataType.STRING, CustomFishingCatalog.WHOLE_TYPE)));
            samples.put(name + " catch id stored as a number", stack(material, d -> {
                d.set(Keys.FOOD_ID, PersistentDataType.STRING, CustomFishingCatalog.WHOLE_TYPE);
                d.set(Keys.CUSTOM_FISHING_ID, PersistentDataType.INTEGER, 4);
            }));
        }
        return samples;
    }

    @Test
    void tagManagerMatchesExactlyWhatTheFullMetaReadMatched() {
        TagManager tags = new TagManager();
        List<String> matched = new ArrayList<>();
        samples().forEach((name, stack) -> {
            assertEquals(oldTagMatch(stack), tags.matches(stack), name);
            if (tags.matches(stack)) matched.add(name);
        });
        // Every material carrying the food id as text, and nothing else.
        assertEquals(7 * 5, matched.size(), matched.toString());
        assertTrue(matched.contains("bread food"));
        assertFalse(matched.contains("bread food id stored as a number"));
    }

    @Test
    void legacyFishScanMatchesExactlyWhatTheFullMetaReadMatched() {
        LegacyFishScan scan = new LegacyFishScan();
        List<String> matched = new ArrayList<>();
        samples().forEach((name, stack) -> {
            assertEquals(oldFishMatch(stack), scan.matches(stack), name);
            if (scan.matches(stack)) matched.add(name);
        });
        assertTrue(matched.contains("cod without meta"));
        assertTrue(matched.contains("cod food id stored as a number"));
        assertTrue(matched.contains("salmon old whole fish"));
        assertFalse(matched.contains("salmon clean whole fish"));
        assertFalse(matched.contains("salmon without meta"));
        assertFalse(matched.contains("cooked_cod without meta"));
    }
}
