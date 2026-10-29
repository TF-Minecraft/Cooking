package net.tfminecraft.cooking.fishing;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.persistence.PersistentDataContainerView;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

public final class SeafoodWholeItems {
    private SeafoodWholeItems() {}

    public static FoodItem describe(FoodItem template, CatchMapping mapping, int sizeCm, int quality) {
        return describe(template, mapping.origin(), mapping.cut(), sizeCm, quality, mapping.id());
    }

    public static FoodItem describe(FoodItem template, String origin, String cut, int sizeCm, int quality,
            String customFishingId) {
        FoodItem item = new FoodItem(template);
        item.setCategory("seafood");
        item.setOrigin(origin);
        item.setLineage(IngredientLineage.ofMain(origin));
        item.setQualityRange(quality, quality);
        item.setCatchSizeCm(sizeCm);
        item.setSeafoodCutType(cut);
        item.setCustomFishingId(customFishingId);
        item.setAmount(1);
        return item;
    }

    public static ItemStack build(ItemStack caught, CatchMapping mapping, int sizeCm, int quality) {
        FoodItem template = templateOrNull();
        if (template == null || caught == null || mapping == null) {
            return null;
        }
        return build(caught, describe(template, mapping.origin(), mapping.cut(), sizeCm, quality, mapping.id()), quality);
    }

    public static ItemStack build(ItemStack caught, VanillaFish fish, int quality) {
        FoodItem template = templateOrNull();
        if (template == null || caught == null || fish == null) {
            return null;
        }
        return build(caught,
                describe(template, fish.origin(), fish.cut(), fish.sizeCm(), quality, null),
                quality);
    }

    /**
     * True for a whole fish built by an older version on the caught CustomFishing item itself, so
     * it still carries that item's per-catch data.
     */
    static boolean carriesCatchData(PersistentDataContainerView data) {
        return CustomFishingCatalog.WHOLE_TYPE.equals(data.get(Keys.FOOD_ID, PersistentDataType.STRING))
                && data.has(Keys.CUSTOM_FISHING_ID, PersistentDataType.STRING)
                && !data.has(Keys.CLEAN_CATCH, PersistentDataType.BYTE);
    }

    /** The whole fish with its Cooking data and look, but nothing else left over from the catch. */
    static ItemStack withoutCatchData(ItemStack whole) {
        // Lore as components: ItemRef.mergeMeta's legacy text round trip can lose formatting.
        ItemStack clean = lookOf(whole);
        ItemMeta from = whole.getItemMeta();
        ItemMeta to = clean.getItemMeta();
        to.lore(from.lore());
        from.getPersistentDataContainer().copyTo(to.getPersistentDataContainer(), true);
        to.getPersistentDataContainer().set(Keys.CLEAN_CATCH, PersistentDataType.BYTE, (byte) 1);
        clean.setItemMeta(to);
        return clean;
    }

    /**
     * The caught item's name and model on a fresh stack. CustomFishing tags every catch with its own
     * exact size, so a whole fish built on the caught item itself never stacks with another.
     */
    static ItemStack lookOf(ItemStack caught) {
        ItemStack look = new ItemStack(caught.getType(), caught.getAmount());
        if (caught.hasItemMeta()) look.setItemMeta(copyLook(caught.getItemMeta(), look.getItemMeta()));
        return look;
    }

    static ItemMeta copyLook(ItemMeta from, ItemMeta to) {
        if (from.hasDisplayName()) to.displayName(from.displayName());
        if (from.hasItemName()) to.itemName(from.itemName());
        if (from.hasCustomModelDataComponent()) to.setCustomModelDataComponent(from.getCustomModelDataComponent());
        if (from.hasItemModel()) to.setItemModel(from.getItemModel());
        return to;
    }

    private static FoodItem templateOrNull() {
        return FoodLoader.getByString(CustomFishingCatalog.WHOLE_TYPE);
    }

    private static ItemStack build(ItemStack caught, FoodItem item, int quality) {
        ItemStack whole = ItemBuilder.buildSingleWithQuality(item, lookOf(caught), quality);
        if (whole != null) {
            ItemMeta meta = whole.getItemMeta();
            meta.getPersistentDataContainer().set(Keys.CLEAN_CATCH, PersistentDataType.BYTE, (byte) 1);
            whole.setItemMeta(meta);
        }
        return whole;
    }
}
