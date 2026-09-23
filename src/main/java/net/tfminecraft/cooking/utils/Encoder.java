package net.tfminecraft.cooking.utils;

import net.tfminecraft.cooking.util.LegacyModelData;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public class Encoder {
    public static String getEncodedSlots(Furniture f) {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, PlacedSlot> entry : f.getActiveSlots().entrySet()) {
            ItemStack item = entry.getValue().getCurrentItem();
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            int model = (meta != null && LegacyModelData.has(meta)) ? LegacyModelData.get(meta) : 0;
            String itemModel = null;
            if (meta != null && meta.hasItemModel() && meta.getItemModel() != null) {
                itemModel = meta.getItemModel().toString();
            }

            if (!first) result.append(":");
            first = false;

            result.append(formatSlot(entry.getKey(), item.getType().toString(), model, itemModel));
        }

        return result.toString();
    }

    public static Map<String, ItemStack> decodeSlots(String encoded) {
        Map<String, ItemStack> result = new java.util.HashMap<>();

        if (encoded == null || encoded.isEmpty()) return result;

        // Split slot sections: "slot_1.IRON_INGOT.5"
        String[] parts = encoded.split(":");

        for (String part : parts) {
            if (part.isEmpty()) continue;

            ParsedSlot parsed = parseSlot(part);
            if (parsed == null) continue;

            Material mat = Material.matchMaterial(parsed.material());
            if (mat == null) continue;

            ItemStack item = new ItemStack(mat, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                LegacyModelData.set(meta, parsed.customModelData());
                if (parsed.itemModel() != null) {
                    NamespacedKey key = NamespacedKey.fromString(parsed.itemModel());
                    if (key != null) {
                        meta.setItemModel(key);
                    }
                }
                item.setItemMeta(meta);
            }

            result.put(parsed.slotId(), item);
        }

        return result;
    }

    /** slot.MATERIAL.customModelData[.namespace~key]. ':' in the key is stored as '~'. */
    static String formatSlot(String slotId, String material, int customModelData, String itemModel) {
        StringBuilder result = new StringBuilder();
        result.append(slotId).append('.').append(material).append('.').append(customModelData);
        if (itemModel != null && !itemModel.isEmpty()) {
            // Slot records are separated by ':'. Resource locations use ':' too.
            result.append('.').append(itemModel.replace(':', '~'));
        }
        return result.toString();
    }

    static ParsedSlot parseSlot(String part) {
        if (part == null || part.isEmpty()) return null;
        String[] data = part.split("\\.", 4);
        if (data.length < 3 || data[0].isEmpty() || data[1].isEmpty()) return null;
        int model;
        try {
            model = Integer.parseInt(data[2]);
        } catch (NumberFormatException e) {
            model = 0;
        }
        String itemModel = null;
        if (data.length >= 4 && !data[3].isEmpty()) {
            int tilde = data[3].indexOf('~');
            if (tilde > 0) {
                itemModel = data[3].substring(0, tilde) + ":" + data[3].substring(tilde + 1);
            } else {
                itemModel = data[3];
            }
        }
        return new ParsedSlot(data[0], data[1], model, itemModel);
    }

    record ParsedSlot(String slotId, String material, int customModelData, String itemModel) {}
}
