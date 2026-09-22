package net.tfminecraft.cooking.utils;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;

public class Encoder {
    public static String getEncodedSlots(Furniture f) {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, PlacedSlot> entry : f.getActiveSlots().entrySet()) {
            ItemStack item = entry.getValue().getCurrentItem();
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            int model = (meta != null && meta.hasCustomModelData()) ? meta.getCustomModelData() : 0;

            if (!first) result.append(":");
            first = false;

            result.append(entry.getKey())
                .append(".")
                .append(item.getType().toString())
                .append(".")
                .append(model);
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

            // Split into slot, type, model
            String[] data = part.split("\\."); 
            if (data.length < 3) continue; // invalid format

            String slotId = data[0];                // "slot_1"
            String materialName = data[1];          // "IRON_INGOT"
            String modelStr = data[2];              // "5"

            // Convert material
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) continue; // ignore invalid material names

            int model;
            try {
                model = Integer.parseInt(modelStr);
            } catch (NumberFormatException e) {
                model = 0; // fallback
            }

            // Build item
            ItemStack item = new ItemStack(mat, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(model);
                item.setItemMeta(meta);
            }

            // Add to result
            result.put(slotId, item);
        }

        return result;
    }
}
