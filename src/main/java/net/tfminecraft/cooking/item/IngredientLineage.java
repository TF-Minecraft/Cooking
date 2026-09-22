package net.tfminecraft.cooking.item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.cooking.quality.CompositionConfig;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionRole;

/**
 * Ordered, case-insensitive origins that went into a food item.
 * Mains and extras stay in the buckets they had when they were added.
 */
public final class IngredientLineage {
    private static final IngredientLineage EMPTY = new IngredientLineage(Map.of(), Map.of());

    private final Map<String, String> mains;
    private final Map<String, String> extras;

    private IngredientLineage(Map<String, String> mains, Map<String, String> extras) {
        this.mains = mains;
        this.extras = extras;
    }

    public static IngredientLineage empty() {
        return EMPTY;
    }

    public static IngredientLineage ofMain(String origin) {
        return empty().withMain(origin);
    }

    public static IngredientLineage ofExtra(String origin) {
        return empty().withExtra(origin);
    }

    public static IngredientLineage from(Collection<FoodItem> inputs, CompositionContext context) {
        IngredientLineage result = empty();
        if (inputs == null) {
            return result;
        }
        for (FoodItem item : inputs) {
            if (item == null) {
                continue;
            }
            IngredientLineage existing = item.getLineage();
            if (existing != null && !existing.isEmpty()) {
                result = result.merge(existing);
                continue;
            }
            CompositionRole role = CompositionConfig.getRole(item.getCategory(), context);
            if (role == CompositionRole.EXTRA) {
                result = result.withExtra(item.getOrigin());
            } else if (role == CompositionRole.MAIN) {
                result = result.withMain(item.getOrigin());
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return mains.isEmpty() && extras.isEmpty();
    }

    public List<String> mains() {
        return List.copyOf(mains.values());
    }

    public List<String> extras() {
        return List.copyOf(extras.values());
    }

    public IngredientLineage withMain(String origin) {
        String canonical = canonical(origin);
        if (canonical == null) {
            return this;
        }
        String key = key(canonical);
        if (mains.containsKey(key)) {
            return this;
        }
        Map<String, String> nextMains = copy(mains);
        nextMains.put(key, canonical);
        Map<String, String> nextExtras = copy(extras);
        nextExtras.remove(key);
        return new IngredientLineage(nextMains, nextExtras);
    }

    public IngredientLineage withExtra(String origin) {
        String canonical = canonical(origin);
        if (canonical == null) {
            return this;
        }
        String key = key(canonical);
        if (mains.containsKey(key) || extras.containsKey(key)) {
            return this;
        }
        Map<String, String> nextExtras = copy(extras);
        nextExtras.put(key, canonical);
        return new IngredientLineage(mains, nextExtras);
    }

    public IngredientLineage merge(IngredientLineage other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        IngredientLineage result = this;
        for (String main : other.mains.values()) {
            result = result.withMain(main);
        }
        for (String extra : other.extras.values()) {
            result = result.withExtra(extra);
        }
        return result;
    }

    public static IngredientLineage forEat(FoodItem food) {
        if (food == null) {
            return empty();
        }
        IngredientLineage lineage = food.getLineage();
        if (lineage == null || lineage.isEmpty()) {
            lineage = ofMain(food.getOrigin());
        }
        FoodItem sauce = food.getSauce();
        if (sauce != null && sauce != food) {
            lineage = lineage.merge(forEat(sauce));
        }
        return lineage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IngredientLineage other)) {
            return false;
        }
        return mains.equals(other.mains) && extras.equals(other.extras);
    }

    @Override
    public int hashCode() {
        return mains.hashCode() * 31 + extras.hashCode();
    }

    private static String canonical(String origin) {
        if (origin == null) {
            return null;
        }
        String trimmed = origin.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("Mixed")) {
            return null;
        }
        return trimmed;
    }

    private static String key(String canonical) {
        return canonical.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> copy(Map<String, String> source) {
        return new LinkedHashMap<>(source);
    }
}
