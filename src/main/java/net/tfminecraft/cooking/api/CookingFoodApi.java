package net.tfminecraft.cooking.api;

import java.util.OptionalInt;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.carve.CarveSequence;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.cooking.utils.QualityUtils;

/**
 * Food that other plugins hand out, such as animal products. Main thread only.
 * Quality is the Cooking star rating and is clamped to 1-5.
 */
public final class CookingFoodApi {

    private CookingFoodApi() {}

    /** One food from a Cooking string such as {@code food(type=egg;origin=Egg)}, or null if it is not a food. */
    public static ItemStack buildFood(String food, int quality) {
        return build(food, quality, null, -1);
    }

    /**
     * A roast from a Cooking string such as {@code meat(type=roast;origin=Beef)}, carved down to this many
     * meat cuts before its closing bone. The carve sequence floor still applies. Foods without a carve
     * sequence are returned whole. Null if the string is not a food.
     */
    public static ItemStack buildRoast(String food, int quality, int meatCuts) {
        return build(food, quality, null, Math.max(0, meatCuts));
    }

    /** A Cooking milk bucket from a goat, or from a cow for any other animal. Null if milk is not configured. */
    public static ItemStack buildMilk(EntityType origin, int quality) {
        return build(milkFood(origin), quality, new ItemStack(Material.MILK_BUCKET), -1);
    }

    /** The Cooking string {@link #buildMilk} uses for this animal. */
    public static String milkFood(EntityType origin) {
        return origin == EntityType.GOAT
                ? "food(type=milk_bucket;origin=Goat)"
                : "food(type=milk_bucket;origin=Cow)";
    }

    /** Stars stored on a Cooking item, or empty when the item has no Cooking quality. */
    public static OptionalInt qualityOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return OptionalInt.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return OptionalInt.empty();
        }
        Integer stored = meta.getPersistentDataContainer().get(Keys.QUALITY, PersistentDataType.INTEGER);
        return stored == null ? OptionalInt.empty() : OptionalInt.of(QualityUtils.clamp(stored));
    }

    /** Adds an item the way Cooking stacks food. Returns what did not fit, or null. */
    public static ItemStack addToInventory(Player player, ItemStack item) {
        return InventoryAdder.addItem(player, item);
    }

    private static ItemStack build(String food, int quality, ItemStack base, int meatCuts) {
        if (food == null || food.isBlank()) {
            return null;
        }
        FoodParser.Result parsed = FoodParser.parse(food);
        if (parsed == null || parsed.template == null) {
            return null;
        }
        ItemStack stack = ItemBuilder.buildSingleWithQuality(parsed.template, base, QualityUtils.clamp(quality));
        if (meatCuts < 0) {
            return stack;
        }
        FoodItem item = FoodItem.fromItem(stack);
        if (item == null) {
            return stack;
        }
        CarvableRoastUtils.readCarveState(item, stack);
        String seqId = item.getCarveSequencePdc() != null
                ? item.getCarveSequencePdc()
                : item.getCarveSequenceId();
        if (seqId == null) {
            return stack;
        }
        CarveSequence seq = CarveSequenceLoader.get(seqId);
        if (seq == null) {
            return stack;
        }
        CarvableRoastUtils.RoastPortion portion = CarvableRoastUtils.portion(seq, meatCuts);
        item.setCarveState(seqId, portion.nextIndex(), portion.remaining());
        CarvableRoastUtils.writeCarveState(stack, item);
        ItemStack updated = ItemUpdater.applyItemUpdate(stack, item, null);
        if (updated != null && !updated.getType().isAir()) {
            stack = updated;
        }
        return stack;
    }
}
