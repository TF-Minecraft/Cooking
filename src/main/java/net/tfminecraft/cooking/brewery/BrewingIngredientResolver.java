package net.tfminecraft.cooking.brewery;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.Keys;

/** Resolves whole ingredients back to their configured input, independently of their visual model. */
public final class BrewingIngredientResolver {
    private static final Set<String> IDENTITY_NEUTRAL = Set.of("freshness", "warmth");

    private BrewingIngredientResolver() {}

    public static boolean isCookingItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(Keys.FOOD_ID, PersistentDataType.STRING);
    }

    public static String sourceFor(FoodItem food) {
        if (food == null || food.getOrigin() == null || food.hasSauce() || !food.getIngredients().isEmpty()) return null;
        // Freshness and warmth never change identity. Other preparation states must remain raw.
        for (TagTrack track : food.getTagTracks()) {
            if (track.getCurrentStep() == null || IDENTITY_NEUTRAL.contains(track.getId())) continue;
            if (!"cooked".equals(track.getId()) || !"raw".equals(track.getCurrentStep().getId())) return null;
        }

        Set<String> sources = new HashSet<>();
        for (Map.Entry<String, String> entry : ConversionLoader.get().entrySet()) {
            String source = entry.getKey();
            String specification = entry.getValue();
            int open = specification.indexOf('(');
            if (open < 0 || !specification.endsWith(")")) continue;
            Map<String, String> fields = FoodParser.extractFields(specification.substring(open + 1, specification.length() - 1));
            if (food.getId().equalsIgnoreCase(fields.get("type"))
                    && food.getOrigin().equalsIgnoreCase(fields.get("origin"))) sources.add(source);
        }
        // Prefer the canonical vanilla name when several input variants describe the same food (e.g. eggs).
        String vanilla = "v." + food.getOrigin().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (sources.contains(vanilla)) return vanilla;
        return sources.size() == 1 ? sources.iterator().next() : null;
    }
}
