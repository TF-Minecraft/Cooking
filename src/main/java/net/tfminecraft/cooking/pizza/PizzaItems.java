package net.tfminecraft.cooking.pizza;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionFreshnessApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/** Turns the layers on a peel into a slice of pizza. */
public final class PizzaItems {
    private static final String COOKED_TRACK = "cooked";

    private PizzaItems() {}

    /**
     * A slice of the pizza these layers make, dough first, at this bake (PizzaPeel.COOKED or BURNT): quality, freshness
     * and variety come from all of them, and the toppings name it (Cheese, Tomato and Basil Pizza Slice). Null when
     * pizzas.yml's output makes no item.
     */
    public static ItemStack build(Player player, List<ItemStack> stacks, int baked) {
        List<FoodItem> layers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            FoodItem food = FoodItem.fromItem(stack);
            if (food == null) continue;
            // A layer ages on the peel as it would anywhere else.
            food.updateAge();
            layers.add(food);
        }
        FoodParser.Result parsed = FoodParser.parse(PizzaConfig.output());
        if (layers.isEmpty() || parsed == null || parsed.template == null) return null;
        FoodItem pizza = parsed.template;
        CompositionResult composed = CompositionQualityResolver.compose(player, layers, CompositionContext.PIZZA);
        CompositionApplier.apply(pizza, composed);
        CompositionFreshnessApplier.keepOwnAgeing(pizza);
        applyTotals(pizza, layers);
        for (FoodItem food : layers) {
            SandwichLayer layer = PizzaConfig.layerFor(food);
            if (layer == null || PizzaConfig.isBase(layer) || PizzaConfig.isSauce(layer)) continue;
            // A topping named "" (salt) doesn't name the pizza.
            String name = layer.fillerName(food);
            if (!name.isEmpty()) pizza.addIngredient(name);
        }
        TagTrack cooked = TrackLoader.getByString(COOKED_TRACK);
        if (cooked != null) {
            TagTrack bake = new TagTrack(cooked);
            bake.setValue(baked);
            pizza.addOrModifyTrack(bake);
        }
        return ItemBuilder.buildComposedWithQuality(pizza, composed.getFinalQuality());
    }

    /**
     * The dough becomes the crust, which feeds what the slice type does in types.yml; each other layer adds its own
     * food shared between the slices, and nutrition is the average weighted by food. The pizza's bake then applies to
     * all of it, so a layer's own cooking (fried sausage) doesn't count twice.
     */
    static void applyTotals(FoodItem pizza, List<FoodItem> layers) {
        double food = pizza.getBaseFood();
        double nutrition = food * pizza.getBaseNutrition();
        for (FoodItem layer : layers) {
            if (PizzaConfig.isBase(PizzaConfig.layerFor(layer))) continue;
            double part = layer.getBaseFood() * kept(layer, true) / PizzaConfig.slices();
            food += part;
            nutrition += part * layer.getBaseNutrition() * kept(layer, false);
        }
        pizza.setBaseFood(food);
        if (food > 0) pizza.setBaseNutrition(nutrition / food);
    }

    /** What a layer's lasting tags do to it; its age becomes the pizza's freshness and its cooking the pizza's bake. */
    private static double kept(FoodItem layer, boolean forFood) {
        double multiplier = 1;
        for (TagTrack track : layer.getTagTracks()) {
            if (track.isAgeable() || COOKED_TRACK.equals(track.getId())) continue;
            TagStep step = track.getCurrentStep();
            if (step != null) multiplier *= forFood ? step.getFoodMultiplier() : step.getNutritionMultiplier();
        }
        return multiplier;
    }
}
