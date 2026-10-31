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
 * One kind of salad from salads.yml kinds (Fruit, Greek, Carrot, Mixed): what its ingredients must be. A salad of this
 * kind takes the kind's name as its origin, so types.yml overrides give it its own look.
 *
 * <pre>
 * Greek:
 *   layers: [cheese]            # every one of these ingredient layers
 *   only: [fruit]               # nothing but these layers
 *   origins: [Olive]            # every one of these origins
 *   any-origin: [Tomato]        # at least one of these
 *   mostly: Carrot              # at least half the ingredients are this
 * </pre>
 * A kind with no rules takes any salad, so the last one catches the rest.
 */
public final class SaladKind {
    private final String origin;
    private final List<String> layers = new ArrayList<>();
    private final List<String> only = new ArrayList<>();
    private final List<String> origins = new ArrayList<>();
    private final List<String> anyOrigin = new ArrayList<>();
    private final String mostly;

    public SaladKind(String origin, ConfigurationSection config) {
        this.origin = origin;
        lower(config.getStringList("layers"), layers);
        lower(config.getStringList("only"), only);
        lower(config.getStringList("origins"), origins);
        lower(config.getStringList("any-origin"), anyOrigin);
        String most = config.getString("mostly");
        this.mostly = most == null ? null : most.toLowerCase(Locale.ROOT);
    }

    private static void lower(List<String> from, List<String> to) {
        for (String s : from) to.add(s.toLowerCase(Locale.ROOT));
    }

    /** The origin a salad of this kind takes (Greek). */
    public String getOrigin() {
        return origin;
    }

    /** Whether these ingredients, with the layers they make, are this kind of salad. */
    public boolean matches(List<FoodItem> foods, List<SandwichLayer> made) {
        Map<String, Integer> byOrigin = new HashMap<>();
        List<String> layerIds = new ArrayList<>();
        for (int i = 0; i < foods.size(); i++) {
            String id = made.get(i).getId().toLowerCase(Locale.ROOT);
            if (!only.isEmpty() && !only.contains(id)) return false;
            layerIds.add(id);
            String from = foods.get(i).getOrigin();
            byOrigin.merge(from == null ? "" : from.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        if (!layerIds.containsAll(layers)) return false;
        if (!byOrigin.keySet().containsAll(origins)) return false;
        if (!anyOrigin.isEmpty() && anyOrigin.stream().noneMatch(byOrigin::containsKey)) return false;
        return mostly == null || byOrigin.getOrDefault(mostly, 0) * 2 >= foods.size();
    }
}
