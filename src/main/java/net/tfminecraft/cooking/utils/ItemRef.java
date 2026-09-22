package net.tfminecraft.cooking.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import net.tfminecraft.tlibs.TLibs;
import org.bukkit.persistence.PersistentDataType;

public class ItemRef {

    public static ItemStack resolve(String ref) {
        return TLibs.getItemAPI().getCreator().getItemFromPath(ref);
    }

    public static ItemStack apply(String ref, ItemStack source) {
        ItemStack out = resolve(ref).clone();
        mergeMeta(out, source);
        return out;
    }

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
        srcPdc.getKeys().forEach(key -> {
            if (srcPdc.has(key, PersistentDataType.STRING)) {
                outPdc.set(key, PersistentDataType.STRING, srcPdc.get(key, PersistentDataType.STRING));
            } else if (srcPdc.has(key, PersistentDataType.INTEGER)) {
                outPdc.set(key, PersistentDataType.INTEGER, srcPdc.get(key, PersistentDataType.INTEGER));
            } else if (srcPdc.has(key, PersistentDataType.LONG)) {
                outPdc.set(key, PersistentDataType.LONG, srcPdc.get(key, PersistentDataType.LONG));
            } else if (srcPdc.has(key, PersistentDataType.DOUBLE)) {
                outPdc.set(key, PersistentDataType.DOUBLE, srcPdc.get(key, PersistentDataType.DOUBLE));
            }
        });

        out.setItemMeta(outMeta);
    }
}
