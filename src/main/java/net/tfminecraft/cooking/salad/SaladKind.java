package net.tfminecraft.cooking.salad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.SandwichLayer;

/**
 * One kind of dish from salads.yml or sandwiches.yml kinds (a Greek salad, a Hamburger): what its ingredients must be.
 * A dish of this kind takes the kind's name as its origin, so types.yml overrides give it its own look and name.
 *
 * <pre>
 * Greek:
 *   layers: [cheese]            # every one of these ingredient layers
 *   only: [fruit]               # nothing but these layers
 *   origins: [Olive]            # every one of these origins
 *   any-origin: [Tomato]        # at least one of these
 *   mostly: Carrot              # at least half the ingredients are this
 *   tags:                       # every food of a layer has one of its tag steps (fried meat, not raw)
 *     meat: [cooked]
 * </pre>
 * A kind with no rules takes any dish, so the last one catches the rest.
 */
public final class SaladKind {
    private final String origin;
    private final List<String> layers = new ArrayList<>();
    private final List<String> only = new ArrayList<>();
    private final List<String> origins = new ArrayList<>();
    private final List<String> anyOrigin = new ArrayList<>();
    private final String mostly;
    private final Map<String, List<String>> tags = new HashMap<>();

    public SaladKind(String origin, ConfigurationSection config) {
        this.origin = origin;
        lower(config.getStringList("layers"), layers);
        lower(config.getStringList("only"), only);
        lower(config.getStringList("origins"), origins);
        lower(config.getStringList("any-origin"), anyOrigin);
        String most = config.getString("mostly");
        this.mostly = most == null ? null : most.toLowerCase(Locale.ROOT);
        ConfigurationSection tagged = config.getConfigurationSection("tags");
        if (tagged != null) {
            for (String layer : tagged.getKeys(false)) {
                List<String> steps = new ArrayList<>();
                lower(tagged.getStringList(layer), steps);
                tags.put(layer.toLowerCase(Locale.ROOT), steps);
            }
        }
    }

    private static void lower(List<String> from, List<String> to) {
        for (String s : from) to.add(s.toLowerCase(Locale.ROOT));
    }

    /** The origin a dish of this kind takes (Greek, Hamburger). */
    public String getOrigin() {
        return origin;
    }

    /** Whether these ingredients, with the layers they make, are this kind of dish. */
    public boolean matches(List<FoodItem> foods, List<SandwichLayer> made) {
        Map<String, Integer> byOrigin = new HashMap<>();
        List<String> layerIds = new ArrayList<>();
        for (int i = 0; i < foods.size(); i++) {
            String id = made.get(i).getId().toLowerCase(Locale.ROOT);
            if (!only.isEmpty() && !only.contains(id)) return false;
            if (tags.containsKey(id) && !hasAny(foods.get(i), tags.get(id))) return false;
            layerIds.add(id);
            String from = foods.get(i).getOrigin();
            byOrigin.merge(from == null ? "" : from.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        if (!layerIds.containsAll(layers)) return false;
        if (!byOrigin.keySet().containsAll(origins)) return false;
        if (!anyOrigin.isEmpty() && anyOrigin.stream().noneMatch(byOrigin::containsKey)) return false;
        return mostly == null || byOrigin.getOrDefault(mostly, 0) * 2 >= foods.size();
    }

    /** The food is at one of these tag steps (cooked), whatever track they're on. */
    private static boolean hasAny(FoodItem food, List<String> steps) {
        for (String step : food.getCurrentTagIds()) {
            if (steps.contains(step.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
