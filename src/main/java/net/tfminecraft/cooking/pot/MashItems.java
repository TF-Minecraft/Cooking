package net.tfminecraft.cooking.pot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionFreshnessApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.sandwich.SandwichItems;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;

/** A ladle of mash out of the pot, and the mark that says it's still in the ladle until it's plated. */
public final class MashItems {

    private MashItems() {}

    /**
     * One serving of the mash these ingredients make. Quality, freshness and warmth come from all of them, its food is
     * theirs summed as a sandwich sums its layers and split over the recipe's servings, and each extra in the pot gives
     * its track (Creamy, Seasoned). Null when pot.yml's output makes no item.
     */
    public static ItemStack serve(Player player, MashRecipe recipe, Collection<FoodItem> parts, Collection<String> extrasIn,
            ItemStack ladle) {
        FoodParser.Result parsed = FoodParser.parse(recipe.getOutput());
        if (parsed == null || parsed.template == null) return null;
        List<FoodItem> aged = new ArrayList<>();
        for (FoodItem part : parts) {
            FoodItem copy = new FoodItem(part);
            // A piece ages in the pot as it would anywhere else.
            copy.updateAge();
            aged.add(copy);
        }
        FoodItem mash = parsed.template;
        CompositionResult composed = CompositionQualityResolver.compose(player, aged, CompositionContext.SOUP_SCOOP);
        CompositionApplier.apply(mash, composed);
        CompositionFreshnessApplier.keepOwnAgeing(mash);
        SandwichItems.applyTotals(mash, aged);
        mash.setBaseFood(mash.getBaseFood() / recipe.getServings());
        for (MashRecipe.Extra extra : recipe.getExtras()) {
            if (extrasIn.contains(extra.id())) addTrack(mash, extra.track());
        }
        ItemStack serving = ItemBuilder.buildSingleWithQuality(mash, ladle, composed.getFinalQuality());
        if (serving == null) return null;
        ItemMeta meta = serving.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.LADLED, PersistentDataType.BYTE, (byte) 1);
        serving.setItemMeta(meta);
        return serving;
    }

    /** Gives the food this tags.yml track at its first step (Rich, Creamy, Seasoned); an unknown track does nothing. */
    public static void addTrack(FoodItem food, String trackId) {
        TagTrack template = TrackLoader.getByString(trackId);
        if (template == null) return;
        TagTrack track = new TagTrack(template);
        track.setValue(0);
        food.addOrModifyTrack(track);
    }

    /** True for a serving still in its ladle: plating it gives the ladle back. */
    public static boolean isLadled(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(Keys.LADLED, PersistentDataType.BYTE);
    }

    /** One serving out of its ladle, as it sits on a plate. */
    public static ItemStack unladled(ItemStack stack) {
        ItemStack serving = stack.clone();
        serving.setAmount(1);
        ItemMeta meta = serving.getItemMeta();
        meta.getPersistentDataContainer().remove(Keys.LADLED);
        serving.setItemMeta(meta);
        return serving;
    }
}
