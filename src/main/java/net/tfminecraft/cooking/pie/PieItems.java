package net.tfminecraft.cooking.pie;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionFreshnessApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.salad.SaladKind;
import net.tfminecraft.cooking.sandwich.DishBonus;
import net.tfminecraft.cooking.sandwich.SandwichItems;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.WarmthUtils;

/** Turns the dough and fillings in a tin into one whole pie. */
public final class PieItems {
    private static final String COOKED_TRACK = "cooked";

    private PieItems() {}

    /**
     * The pie this dough and these fillings make at this bake (PieTin.COOKED or BURNT). Its food is what they all hold,
     * summed like a sandwich's but with the pie's bake in place of each one's own cooking; its quality, freshness and
     * variety come from all of them. Its kind (pies.yml kinds) gives it its origin and look, and its name: the kind's
     * name (Key Lime Pie), or else its fillings (Carrot and Onion Pie). It comes out hot. Null when pies.yml's output
     * makes no item.
     */
    public static ItemStack build(Player player, ItemStack dough, List<ItemStack> fillings, int baked) {
        List<FoodItem> all = new ArrayList<>();
        FoodItem crust = food(dough);
        if (crust != null) all.add(crust);
        List<FoodItem> filled = new ArrayList<>();
        for (ItemStack stack : fillings) {
            FoodItem food = food(stack);
            if (food != null) filled.add(food);
        }
        all.addAll(filled);
        FoodParser.Result parsed = FoodParser.parse(PieConfig.output());
        if (all.isEmpty() || parsed == null || parsed.template == null) return null;
        FoodItem pie = parsed.template;
        CompositionResult composed = CompositionQualityResolver.compose(player, all, CompositionContext.PIE);
        CompositionApplier.apply(pie, composed);
        CompositionFreshnessApplier.keepOwnAgeing(pie);
        SandwichItems.applyTotals(pie, all, true);
        TagTrack cooked = TrackLoader.getByString(COOKED_TRACK);
        if (cooked != null) {
            TagTrack bake = new TagTrack(cooked);
            bake.setValue(baked);
            pie.addOrModifyTrack(bake);
        }
        // After the bake: a bonus hidden in a well-made pie (a poison) needs it cooked, not burnt.
        DishBonus.apply(pie, all, PieConfig::layerFor, layer -> !PieConfig.isCrust(layer), PieConfig.bonuses());
        name(pie, filled);
        WarmthUtils.applyHot(pie);
        return ItemBuilder.buildComposedWithQuality(pie, composed.getFinalQuality());
    }

    /** Its kind sets its origin, and its name or, without one, the fillings' names. */
    private static void name(FoodItem pie, List<FoodItem> fillings) {
        List<FoodItem> named = new ArrayList<>();
        List<SandwichLayer> made = new ArrayList<>();
        for (FoodItem food : fillings) {
            SandwichLayer layer = PieConfig.fillingFor(food);
            if (layer == null) continue;
            named.add(food);
            made.add(layer);
        }
        SaladKind kind = PieConfig.kindFor(named, made);
        if (kind != null) pie.setOrigin(kind.getOrigin());
        if (kind != null && kind.getName() != null) {
            pie.addIngredient(kind.getName());
            return;
        }
        for (int i = 0; i < named.size(); i++) {
            String name = made.get(i).fillerName(named.get(i));
            if (!name.isEmpty()) pie.addIngredient(name);
        }
    }

    private static FoodItem food(ItemStack stack) {
        FoodItem food = FoodItem.fromItem(stack);
        // A layer ages in the tin as it would anywhere else.
        if (food != null) food.updateAge();
        return food;
    }
}
