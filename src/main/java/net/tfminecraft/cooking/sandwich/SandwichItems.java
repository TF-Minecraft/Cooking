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
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Turns the layers on a board into one sandwich. */
public final class SandwichItems {
    private static final String COOKED_TRACK = "cooked";

    private SandwichItems() {}

    /**
     * The sandwich these layers make, bottom slice first: quality, freshness and variety come from all of them, and
     * the fillings name it (Cheese and Tomato Sandwich). Its origin, which picks its look and name through types.yml
     * overrides, is the first kind its fillings make (a Hamburger), or else the first layer's sandwich-origin (a jam
     * sandwich), or else the output's. Null when sandwiches.yml's output makes no item.
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
        DishBonus.apply(sandwich, layers, SandwichConfig::layerFor, layer -> !layer.isBread(), SandwichConfig.bonuses());
        String marked = null;
        List<FoodItem> fillings = new ArrayList<>();
        List<SandwichLayer> made = new ArrayList<>();
        for (FoodItem food : layers) {
            SandwichLayer layer = SandwichConfig.layerFor(food);
            if (layer == null) continue;
            if (!layer.isBread()) {
                sandwich.addIngredient(layer.fillerName(food));
                fillings.add(food);
                made.add(layer);
            }
            if (marked == null) marked = layer.getSandwichOrigin();
        }
        SaladKind kind = SandwichConfig.kindFor(fillings, made);
        if (kind != null) marked = kind.getOrigin();
        if (marked != null) sandwich.setOrigin(marked);
        return ItemBuilder.buildComposedWithQuality(sandwich, composed.getFinalQuality());
    }

    /**
     * Food is what the layers hold between them. Nutrition is their average, weighted by how much each one feeds, as
     * eating them one after another would give.
     */
    public static void applyTotals(FoodItem sandwich, List<FoodItem> layers) {
        applyTotals(sandwich, layers, false);
    }

    /**
     * As {@link #applyTotals(FoodItem, List)}; bakedTogether leaves out each layer's own cooking, for a dish that is
     * baked as a whole (a pie's raw meat cooks with it), so its bake applies to all of it once.
     */
    public static void applyTotals(FoodItem dish, List<FoodItem> layers, boolean bakedTogether) {
        double food = 0;
        double nutrition = 0;
        for (FoodItem layer : layers) {
            double part = layer.getBaseFood() * kept(layer, true, bakedTogether);
            food += part;
            nutrition += part * layer.getBaseNutrition() * kept(layer, false, bakedTogether);
        }
        dish.setBaseFood(food);
        if (food > 0) dish.setBaseNutrition(nutrition / food);
    }

    /** What a layer's lasting tags (burnt bread) do to it; its age becomes the sandwich's own freshness instead. */
    private static double kept(FoodItem layer, boolean forFood, boolean bakedTogether) {
        double multiplier = 1;
        for (TagTrack track : layer.getTagTracks()) {
            if (track.isAgeable() || (bakedTogether && COOKED_TRACK.equals(track.getId()))) continue;
            TagStep step = track.getCurrentStep();
            if (step != null) multiplier *= forFood ? step.getFoodMultiplier() : step.getNutritionMultiplier();
        }
        return multiplier;
    }
}
