package net.tfminecraft.cooking.cup;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.PlayerActionContext;

public final class MilkBucketConverter implements Listener {

    public static ItemStack convertIfNeeded(Player player, ItemStack stack) {
        if (stack == null || stack.getType() != Material.MILK_BUCKET) {
            return stack;
        }
        FoodItem existing = FoodItem.fromItem(stack);
        if (existing != null && "milk_bucket".equalsIgnoreCase(existing.getId())) {
            return stack;
        }
        return convert(player, stack);
    }

    public static ItemStack convert(Player player, ItemStack stack) {
        return convert(player, stack, null);
    }

    public static ItemStack convert(Player player, ItemStack stack, Integer qualityOverride) {
        if (stack == null || stack.getType() != Material.MILK_BUCKET) {
            return stack;
        }
        FoodItem existing = FoodItem.fromItem(stack);
        if (existing != null && "milk_bucket".equalsIgnoreCase(existing.getId())) {
            return stack;
        }

        String result = ConversionLoader.getByItem(stack);
        if (result == null) {
            result = ConversionLoader.getByString("v.milk_bucket");
        }
        if (result == null) {
            return stack;
        }

        FoodParser.Result parsed = FoodParser.parse(result);
        if (parsed == null || parsed.template == null) {
            return stack;
        }

        int quality = qualityOverride != null
                ? net.tfminecraft.cooking.utils.QualityUtils.clamp(qualityOverride)
                : OriginQualityResolver.resolve(player, parsed.template);
        ItemStack converted = ItemBuilder.buildSingleWithQuality(parsed.template, stack, quality);
        converted.setAmount(stack.getAmount());
        return converted;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.getBucket() != Material.MILK_BUCKET) {
            return;
        }

        Player player = event.getPlayer();
        PlayerActionContext context = PlayerActionContext.capture(player);
        if (context == null) {
            return;
        }
        ItemStack filled = event.getItemStack();
        ItemStack converted = convert(player, filled);
        if (converted == null || converted.equals(filled) || !context.isCurrent()) {
            return;
        }

        Bukkit.getScheduler().runTask(Cooking.plugin, () -> {
            if (!context.isCurrent()) {
                return;
            }
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null && main.getType() == Material.MILK_BUCKET && FoodItem.fromItem(main) == null) {
                converted.setAmount(main.getAmount());
                player.getInventory().setItemInMainHand(converted);
                player.updateInventory();
            }
        });
    }
}
