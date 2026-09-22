package net.tfminecraft.cooking.fishing;

import net.tfminecraft.cooking.util.LegacyModelData;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.OriginQualityResolver;

public final class LegacyFishConversion {
    private LegacyFishConversion() {}

    public static ItemStack convert(Player player, ItemStack stack) {
        if (stack == null || FoodItem.fromItem(stack) != null) {
            return null;
        }
        LegacyFishAdapter.Decision decision = LegacyFishAdapter.decide(
                false,
                stack.getType().name(),
                modelData(stack),
                CustomFishingItemFacts.lootId(stack),
                CustomFishingItemFacts.size(stack));
        if (!decision.replaces()) {
            return null;
        }
        int quality = OriginQualityResolver.resolve(player, null);
        ItemStack built = SeafoodWholeItems.build(stack, decision.mapping(), decision.sizeCm(), quality);
        if (built == null) {
            return null;
        }
        built.setAmount(stack.getAmount());
        return built;
    }

    private static Integer modelData(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !LegacyModelData.has(meta)) {
            return null;
        }
        return LegacyModelData.get(meta);
    }
}
