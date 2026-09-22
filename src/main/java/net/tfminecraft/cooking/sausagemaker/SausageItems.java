package net.tfminecraft.cooking.sausagemaker;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.ItemBuilder;

public final class SausageItems {

    private SausageItems() {}

    public static ItemStack fromMeats(Player player, Collection<FoodItem> meats) {
        FoodItem template = FoodLoader.getByString("sausage_chain");
        if (template == null) {
            return null;
        }

        CompositionResult composed = CompositionQualityResolver.compose(
                player, meats, CompositionContext.SAUSAGE_MAKER);

        FoodItem chain = new FoodItem(template);
        chain.setCategory("meat");
        chain.setOrigin("Mixed");
        applyBatchTotals(chain, meats);
        TagTrack cookedTemplate = TrackLoader.getByString("cooked");
        if (cookedTemplate != null) {
            TagTrack cooked = new TagTrack(cookedTemplate);
            cooked.setValue(0);
            chain.addOrModifyTrack(cooked);
        }
        CompositionApplier.apply(chain, composed);

        return ItemBuilder.buildComposedWithQuality(chain, composed.getFinalQuality());
    }

    /** Food is the summed amount. Nutrition is the average level, left unchanged when there are no meats. */
    public static void applyBatchTotals(FoodItem chain, Collection<FoodItem> meats) {
        double totalFood = 0;
        double nutritionSum = 0;
        int counted = 0;
        if (meats != null) {
            for (FoodItem meat : meats) {
                if (meat == null) {
                    continue;
                }
                totalFood += meat.getBaseFood();
                nutritionSum += meat.getBaseNutrition();
                counted++;
            }
        }
        chain.setBaseFood(totalFood);
        if (counted > 0) {
            chain.setBaseNutrition(nutritionSum / counted);
        }
    }
}
