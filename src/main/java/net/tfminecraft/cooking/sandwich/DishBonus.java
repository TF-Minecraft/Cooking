package net.tfminecraft.cooking.sandwich;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * A tag a finished sandwich or pizza earns from its layers (sandwiches.yml / pizzas.yml bonuses), like a soup's
 * Seasoned or Thick: a step of a tags.yml track, whose multipliers then apply to the dish.
 *
 * <pre>
 * bonuses:
 *   loaded:
 *     track: dish_size
 *     count: fillings          # fillings (or toppings), or seasonings: different salts and peppers
 *     steps: {3: hearty, 5: loaded}
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
    private final TreeMap<Integer, String> steps = new TreeMap<>();

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
     * Gives the dish every bonus its layers earn. The layer function says which layer a food makes (null for none);
     * the filling test picks the layers that count as fillings or toppings (not bread, dough or sauce).
     */
    public static void apply(FoodItem dish, List<FoodItem> foods, Function<FoodItem, SandwichLayer> layerFor,
            Predicate<SandwichLayer> filling, List<DishBonus> bonuses) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, FoodItem> addonsByOrigin = new HashMap<>();
        Set<String> seasonings = new HashSet<>();
        Map<String, FoodItem> all = new HashMap<>();
        int fillings = 0;
        boolean allFresh = true;
        for (FoodItem food : foods) {
            SandwichLayer layer = layerFor.apply(food);
            if (layer == null) continue;
            all.put(String.valueOf(all.size()), food);
            counts.merge(layer.getId().toLowerCase(Locale.ROOT), 1, Integer::sum);
            if (filling.test(layer)) fillings++;
            String origin = food.getOrigin() == null ? "" : food.getOrigin().toLowerCase(Locale.ROOT);
            if (StationAddonRules.isAddonCategory(food.getCategory())) addonsByOrigin.putIfAbsent(origin, food);
            if (StationAddonRules.isSeasoningCategory(food.getCategory())) seasonings.add(origin);
            TagTrack age = food.getTagTrack(FRESHNESS);
            if (age != null && age.getCurrentStep() != age.getSteps().get(0)) allFresh = false;
        }
        String profile = StationAddonRules.classifyAddons(addonsByOrigin).name();
        boolean hasValuable = StationAddonRules.hasValuable(all);
        for (DishBonus bonus : bonuses) {
            int counted = "seasonings".equalsIgnoreCase(bonus.count) ? seasonings.size() : fillings;
            String earned = bonus.earned(counts, counted, profile, hasValuable, allFresh);
            if (earned != null) give(dish, bonus.track, earned);
        }
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

    private static void give(FoodItem dish, String trackId, String stepId) {
        TagTrack template = TrackLoader.getByString(trackId);
        if (template == null || template.getSteps().isEmpty()) return;
        TagTrack track = new TagTrack(template);
        int value = (int) track.getSteps().get(0).getRequiredValue();
        for (TagStep step : track.getSteps()) {
            if (step.getId().equalsIgnoreCase(stepId)) value = (int) step.getRequiredValue();
        }
        track.forceSetValue(value);
        dish.addOrModifyTrack(track);
    }
}
