package net.tfminecraft.cooking.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import net.tfminecraft.tlibs.TLibs;

public class ItemRef {

    public static ItemStack resolve(String ref) {
        return TLibs.getItemAPI().getCreator().getItemFromPath(ref);
    }

    public static ItemStack apply(String ref, ItemStack source) {
        ItemStack template = resolve(ref);
        if (template == null) return null;
        ItemStack out = template.clone();
        mergeMeta(out, source);
        return out;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void mergeMeta(ItemStack out, ItemStack source) {
        if (source == null) return;
        out.setAmount(source.getAmount());
        ItemMeta srcMeta = source.getItemMeta();
        if (srcMeta == null) return;
        ItemMeta outMeta = out.getItemMeta();
        if (outMeta == null) return;

        if (srcMeta.hasDisplayName()) outMeta.setDisplayName(srcMeta.getDisplayName());
        if (srcMeta.hasLore()) outMeta.setLore(srcMeta.getLore());

        PersistentDataContainer srcPdc = srcMeta.getPersistentDataContainer();
        PersistentDataContainer outPdc = outMeta.getPersistentDataContainer();
        srcPdc.copyTo(outPdc, true);

        out.setItemMeta(outMeta);
    }
}
