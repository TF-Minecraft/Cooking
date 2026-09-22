package net.tfminecraft.cooking.fishing;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;

import org.bukkit.inventory.ItemStack;

public final class SeafoodPortions {
    private SeafoodPortions() {}

    public static FoodItem describe(FoodItem template, FoodItem whole, SeafoodYield yield) {
        FoodItem item = new FoodItem(template);
        item.setEdible(true);
        item.setCategory("seafood");
        item.setOrigin(whole.getOrigin());
        IngredientLineage lineage = whole.getLineage();
        if (lineage == null || lineage.mains().isEmpty()) {
            lineage = IngredientLineage.ofMain(whole.getOrigin());
        }
        item.setLineage(lineage);
        int quality = Math.max(1, whole.getQualityMin());
        item.setQualityRange(quality, quality);
        item.setCatchSizeCm(whole.getCatchSizeCm());
        item.setSeafoodCutType(whole.getSeafoodCutType());
        item.setCustomFishingId(whole.getCustomFishingId());
        item.setBaseNutrition(template.getBaseNutrition());
        item.setAmount(1);
        TagTrack freshness = TrackLoader.getByString("freshness");
        if (freshness != null) {
            TagTrack track = new TagTrack(freshness);
            track.setValue(0);
            item.addOrModifyTrack(track);
        }
        return item;
    }

    public static ItemStack build(FoodItem whole, SeafoodYield yield) {
        if (whole == null || yield == null) {
            return null;
        }
        FoodItem template = FoodLoader.getByString(yield.outputType());
        if (template == null) {
            return null;
        }
        FoodItem portion = describe(template, whole, yield);
        ItemStack stack = ItemBuilder.buildSingleWithQuality(portion, null, portion.getQualityMin());
        stack.setAmount(1);
        return stack;
    }
}
