package net.tfminecraft.cooking.salad;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionFreshnessApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.sandwich.DishBonus;
import net.tfminecraft.cooking.sandwich.SandwichItems;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Tosses what's in a bowl into one salad. */
public final class SaladItems {

    private SaladItems() {}

    /**
     * The salad these ingredients and extras make: its food is what they all hold (like a sandwich's), its quality,
     * freshness and variety come from all of them, its ingredients name it (Tomato, Olive and Cheese Salad) and its
     * kind (salads.yml kinds) gives it its origin and look. Null when salads.yml's output makes no item.
     */
    public static ItemStack build(Player player, List<ItemStack> ingredients, List<ItemStack> extras) {
        List<FoodItem> shown = foods(ingredients);
        List<FoodItem> all = new ArrayList<>(shown);
        all.addAll(foods(extras));
        FoodParser.Result parsed = FoodParser.parse(SaladConfig.output());
        if (shown.isEmpty() || parsed == null || parsed.template == null) return null;
        FoodItem salad = parsed.template;
        CompositionResult composed = CompositionQualityResolver.compose(player, all, CompositionContext.SALAD);
        CompositionApplier.apply(salad, composed);
        CompositionFreshnessApplier.keepOwnAgeing(salad);
        SandwichItems.applyTotals(salad, all);
        List<FoodItem> named = new ArrayList<>();
        List<SandwichLayer> made = new ArrayList<>();
        for (FoodItem food : shown) {
            SandwichLayer layer = SaladConfig.ingredientFor(food);
            if (layer == null) continue;
            named.add(food);
            made.add(layer);
            salad.addIngredient(layer.fillerName(food));
        }
        SaladKind kind = SaladConfig.kindFor(named, made);
        if (kind != null) salad.setOrigin(kind.getOrigin());
        DishBonus.apply(salad, all, SaladConfig::layerFor, layer -> !SaladConfig.isExtra(layer), SaladConfig.bonuses());
        return ItemBuilder.buildComposedWithQuality(salad, composed.getFinalQuality());
    }

    private static List<FoodItem> foods(List<ItemStack> stacks) {
        List<FoodItem> foods = new ArrayList<>();
        for (ItemStack stack : stacks) {
            FoodItem food = FoodItem.fromItem(stack);
            if (food == null) continue;
            // An ingredient ages in the bowl as it would anywhere else.
            food.updateAge();
            foods.add(food);
        }
        return foods;
    }
}
