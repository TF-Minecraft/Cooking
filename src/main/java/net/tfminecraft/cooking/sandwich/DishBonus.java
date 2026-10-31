package net.tfminecraft.cooking.sandwich;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.StationAddonRules;

/**
 * A tag a finished dish earns from what's in it (sandwiches.yml, pizzas.yml, salads.yml, pies.yml and pot.yml
 * bonuses), like a soup's Seasoned or Thick: a step of a tags.yml track, whose multipliers then apply to the dish.
 * A bonus can keep its tag secret (TagTrack.isHidden) on a dish made well enough: a poison is hidden in good cooking.
 *
 * <pre>
 * bonuses:
 *   loaded:
 *     track: dish_size
 *     count: fillings          # fillings (or toppings), seasonings: different salts and peppers, or origin: the
 *     steps: {3: hearty, 5: loaded}   # dish's foods of origin below, raw or cooked, layer or not
 *   poison:
 *     track: poison
 *     count: origin
 *     origin: Red Mushroom
 *     steps: {1: tainted, 2: poisoned, 3: deadly}
 *     hide-at-stars: 3         # hidden when the dish ends at 3 stars or more, and
 *     hide-tags: [cooked]      # every food the bonus counts is at one of these tag steps (grilled), and
 *     hide-dish-tags: [cooked] # the dish itself is (its bake); each list only when given
 *   well_rounded:
 *     track: well_rounded
 *     layers: [cheese, vegetable]   # every one of these layers
 *     any-of: [jam, butter]         # and at least one of these
 *     min: 2                        # each of the layers above at least this often
 *     addons: rounded               # a chopped garnish and a spice (aromatic: two of one kind)
 *     valuable: true                # a food types.yml marks valuable (tomato, onion...)
 *     fresh: true                   # every layer still at its first freshness step
 * </pre>
 * A bonus with no count takes its track's first step, or the one named in step.
 */
public final class DishBonus {
    private static final String FRESHNESS = "freshness";
    private final String track;
    private final String step;
    private final List<String> layers = new ArrayList<>();
    private final List<String> anyOf = new ArrayList<>();
    private final int min;
    private final String addons;
    private final boolean valuable;
    private final boolean fresh;
    private final String count;
    private final String origin;
    private final TreeMap<Integer, String> steps = new TreeMap<>();
    private final int hideAtStars;
    private final List<String> hideTags = new ArrayList<>();
    private final List<String> hideDishTags = new ArrayList<>();

    public DishBonus(ConfigurationSection config) {
        this.track = config.getString("track", config.getName());
        this.step = config.getString("step");
        for (String id : config.getStringList("layers")) layers.add(id.toLowerCase(Locale.ROOT));
        for (String id : config.getStringList("any-of")) anyOf.add(id.toLowerCase(Locale.ROOT));
        this.min = Math.max(1, config.getInt("min", 1));
        String profile = config.getString("addons");
        this.addons = profile == null ? null : profile.toUpperCase(Locale.ROOT);
        this.valuable = config.getBoolean("valuable", false);
        this.fresh = config.getBoolean("fresh", false);
        this.count = config.getString("count");
        this.origin = config.getString("origin", "");
        this.hideAtStars = config.getInt("hide-at-stars", 0);
        for (String id : config.getStringList("hide-tags")) hideTags.add(id.toLowerCase(Locale.ROOT));
        for (String id : config.getStringList("hide-dish-tags")) hideDishTags.add(id.toLowerCase(Locale.ROOT));
        ConfigurationSection counted = config.getConfigurationSection("steps");
        if (counted != null) {
            for (String at : counted.getKeys(false)) {
                try {
                    steps.put(Integer.parseInt(at.trim()), counted.getString(at));
                } catch (NumberFormatException ignored) {
                    // Not a count: skip it.
                }
            }
        }
    }

    /** The bonuses in a sandwiches.yml or pizzas.yml bonuses section; none when it's missing. */
    public static List<DishBonus> load(ConfigurationSection section) {
        List<DishBonus> loaded = new ArrayList<>();
        if (section == null) return loaded;
        for (String id : section.getKeys(false)) {
            ConfigurationSection bonus = section.getConfigurationSection(id);
            if (bonus != null) loaded.add(new DishBonus(bonus));
        }
        return loaded;
    }

    /**
     * Gives the dish every bonus its foods earn. The layer function says which layer a food makes (null for none,
     * as for everything in a soup); the filling test picks the layers that count as fillings or toppings (not bread,
     * dough or sauce). Call it once the dish's quality and own tags (a pizza's bake) are set: a bonus that can hide its
     * tag decides that from them, after every bonus is in.
     */
    public static void apply(FoodItem dish, List<FoodItem> foods, Function<FoodItem, SandwichLayer> layerFor,
            Predicate<SandwichLayer> filling, List<DishBonus> bonuses) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, FoodItem> addonsByOrigin = new HashMap<>();
        Set<String> seasonings = new HashSet<>();
        Map<String, FoodItem> all = new HashMap<>();
        List<FoodItem> fillings = new ArrayList<>();
        List<FoodItem> seasoned = new ArrayList<>();
        boolean allFresh = true;
        for (FoodItem food : foods) {
            SandwichLayer layer = layerFor.apply(food);
            if (layer == null) continue;
            all.put(String.valueOf(all.size()), food);
            counts.merge(layer.getId().toLowerCase(Locale.ROOT), 1, Integer::sum);
            if (filling.test(layer)) fillings.add(food);
            String origin = food.getOrigin() == null ? "" : food.getOrigin().toLowerCase(Locale.ROOT);
            if (StationAddonRules.isAddonCategory(food.getCategory())) addonsByOrigin.putIfAbsent(origin, food);
            if (StationAddonRules.isSeasoningCategory(food.getCategory())) {
                seasonings.add(origin);
                seasoned.add(food);
            }
            TagTrack age = food.getTagTrack(FRESHNESS);
            if (age != null && age.getCurrentStep() != age.getSteps().get(0)) allFresh = false;
        }
        String profile = StationAddonRules.classifyAddons(addonsByOrigin).name();
        boolean hasValuable = StationAddonRules.hasValuable(all);
        Map<String, List<FoodItem>> hideable = new LinkedHashMap<>();
        Map<String, DishBonus> hiders = new HashMap<>();
        for (DishBonus bonus : bonuses) {
            List<FoodItem> counted;
            int amount;
            if ("seasonings".equalsIgnoreCase(bonus.count)) {
                counted = seasoned;
                amount = seasonings.size();
            } else if ("origin".equalsIgnoreCase(bonus.count)) {
                counted = bonus.ofOrigin(foods);
                amount = counted.size();
            } else {
                counted = bonus.count == null ? new ArrayList<>(all.values()) : fillings;
                amount = fillings.size();
            }
            String earned = bonus.earned(counts, amount, profile, hasValuable, allFresh);
            String given = earned == null ? null : give(dish, bonus.track, earned);
            if (given != null && bonus.hideAtStars > 0) {
                hideable.put(given, counted);
                hiders.put(given, bonus);
            }
        }
        // Every tag is in, so the dish's stars are final.
        hideable.forEach((trackId, counted) ->
                dish.getTagTrack(trackId).setHidden(hiders.get(trackId).hides(dish, counted)));
    }

    /** The foods of this bonus's origin among all of the dish's, whatever layer they make. */
    private List<FoodItem> ofOrigin(List<FoodItem> foods) {
        List<FoodItem> matching = new ArrayList<>();
        for (FoodItem food : foods) {
            if (origin.equalsIgnoreCase(food.getOrigin() == null ? "" : food.getOrigin())) matching.add(food);
        }
        return matching;
    }

    /**
     * True when the dish keeps this bonus's tag secret: it has at least hide-at-stars stars, it is at a hide-dish-tags
     * step (if any are given), and so is every food the bonus counted at a hide-tags step (if any are given).
     */
    boolean hides(FoodItem dish, List<FoodItem> counted) {
        if (dish.getEffectiveQuality() < hideAtStars) return false;
        if (!hideDishTags.isEmpty() && !atAny(dish, hideDishTags)) return false;
        for (FoodItem food : counted) {
            if (!hideTags.isEmpty() && !atAny(food, hideTags)) return false;
        }
        return true;
    }

    private static boolean atAny(FoodItem food, List<String> tags) {
        for (String id : food.getCurrentTagIds()) {
            if (tags.contains(id.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** The step this bonus gives, or null when the layers don't earn it ("" means the track's first step). */
    String earned(Map<String, Integer> counts, int counted, String profile, boolean hasValuable, boolean allFresh) {
        for (String layer : layers) {
            if (counts.getOrDefault(layer, 0) < min) return null;
        }
        if (!anyOf.isEmpty() && anyOf.stream().noneMatch(counts::containsKey)) return null;
        if (addons != null && !addons.equals(profile)) return null;
        if (valuable && !hasValuable) return null;
        if (fresh && !allFresh) return null;
        if (count == null) return step == null ? "" : step;
        Map.Entry<Integer, String> reached = steps.floorEntry(counted);
        return reached == null ? null : reached.getValue();
    }

    /** Gives the dish this step of the track; the track's id, or null when tags.yml has no such track. */
    private static String give(FoodItem dish, String trackId, String stepId) {
        TagTrack template = TrackLoader.getByString(trackId);
        if (template == null || template.getSteps().isEmpty()) return null;
        TagTrack track = new TagTrack(template);
        int value = (int) track.getSteps().get(0).getRequiredValue();
        for (TagStep step : track.getSteps()) {
            if (step.getId().equalsIgnoreCase(stepId)) value = (int) step.getRequiredValue();
        }
        track.forceSetValue(value);
        dish.addOrModifyTrack(track);
        return track.getId();
    }
}
