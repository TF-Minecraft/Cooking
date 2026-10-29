package net.tfminecraft.cooking.sandwich;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionFreshnessApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Turns the layers on a board into one sandwich. */
public final class SandwichItems {

    private SandwichItems() {}

    /**
     * The sandwich these layers make, bottom slice first: quality, freshness and variety come from all of them, and
     * the fillings name it (Cheese and Tomato Sandwich). The first layer with a sandwich-origin sets its origin, so a
     * jam sandwich looks like one. Null when sandwiches.yml's output makes no item.
     */
    public static ItemStack build(Player player, List<ItemStack> stacks) {
        List<FoodItem> layers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            FoodItem food = FoodItem.fromItem(stack);
            if (food == null) continue;
            // A layer ages on the board as it would anywhere else.
            food.updateAge();
            layers.add(food);
        }
        FoodParser.Result parsed = FoodParser.parse(SandwichConfig.output());
        if (layers.isEmpty() || parsed == null || parsed.template == null) return null;
        FoodItem sandwich = parsed.template;
        CompositionResult composed = CompositionQualityResolver.compose(player, layers, CompositionContext.SANDWICH);
        CompositionApplier.apply(sandwich, composed);
        CompositionFreshnessApplier.keepOwnAgeing(sandwich);
        applyTotals(sandwich, layers);
        boolean marked = false;
        for (FoodItem food : layers) {
            SandwichLayer layer = SandwichConfig.layerFor(food);
            if (layer == null) continue;
            if (!layer.isBread()) sandwich.addIngredient(layer.fillerName(food));
            if (layer.getSandwichOrigin() != null && !marked) {
                sandwich.setOrigin(layer.getSandwichOrigin());
                marked = true;
            }
        }
        return ItemBuilder.buildComposedWithQuality(sandwich, composed.getFinalQuality());
    }

    /**
     * Food is what the layers hold between them. Nutrition is their average, weighted by how much each one feeds, as
     * eating them one after another would give.
     */
    static void applyTotals(FoodItem sandwich, List<FoodItem> layers) {
        double food = 0;
        double nutrition = 0;
        for (FoodItem layer : layers) {
            double part = layer.getBaseFood() * kept(layer, true);
            food += part;
            nutrition += part * layer.getBaseNutrition() * kept(layer, false);
        }
        sandwich.setBaseFood(food);
        if (food > 0) sandwich.setBaseNutrition(nutrition / food);
    }

    /** What a layer's lasting tags (burnt bread) do to it; its age becomes the sandwich's own freshness instead. */
    private static double kept(FoodItem layer, boolean forFood) {
        double multiplier = 1;
        for (TagTrack track : layer.getTagTracks()) {
            if (track.isAgeable()) continue;
            TagStep step = track.getCurrentStep();
            if (step != null) multiplier *= forFood ? step.getFoodMultiplier() : step.getNutritionMultiplier();
        }
        return multiplier;
    }
}
