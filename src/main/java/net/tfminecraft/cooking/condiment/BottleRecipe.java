package net.tfminecraft.cooking.condiment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;

/**
 * A saucepan bottling from condiments.yml: what the saucepan holds (its liquid and exactly these ingredients) and the
 * food an empty glass bottle takes out once it has boiled long enough. Paths are Cooking food paths (c.paste(origin=Tomato));
 * the liquid {@value #WATER} is a cup of water, which isn't food. Extras (vanilla in hot chocolate) may go in once each
 * besides the ingredients and give the output their tags.yml track; a container other than a glass bottle (an empty
 * cup, a bowl) takes the output instead.
 */
public final class BottleRecipe {
    /** The liquid line for a saucepan filled with a cup of water (bone stock). */
    public static final String WATER = "water";

    private final String id;
    private final String liquid;
    private final List<String> ingredients;
    private final int boilSeconds;
    private final String output;
    /** Extra path -> the tags.yml track it gives the output. */
    private final Map<String, String> extras = new LinkedHashMap<>();
    /** The item path that takes the output, or null for an empty glass bottle. */
    private final String container;
    private final String containerName;

    public BottleRecipe(String id, ConfigurationSection config) {
        this.id = id;
        this.liquid = config.getString("liquid", "");
        this.ingredients = config.getStringList("ingredients");
        this.boilSeconds = Math.max(0, config.getInt("boil-seconds", 30));
        this.output = config.getString("output", "");
        ConfigurationSection extraSection = config.getConfigurationSection("extras");
        if (extraSection != null) {
            for (String key : extraSection.getKeys(false)) {
                ConfigurationSection extra = extraSection.getConfigurationSection(key);
                if (extra == null || extra.getString("path", "").isBlank()) continue;
                extras.put(extra.getString("path"), extra.getString("track", ""));
            }
        }
        String path = config.getString("container", "");
        this.container = path.isBlank() ? null : path;
        this.containerName = config.getString("container-name", container == null ? "an empty glass bottle" : "an empty cup");
    }

    public String getId() {
        return id;
    }

    public int getBoilSeconds() {
        return boilSeconds;
    }

    /** The bottled food's line (condiment(type=catsup;...)). */
    public String getOutput() {
        return output;
    }

    /** How a player reads the container that takes the output ("an empty cup"). */
    public String getContainerName() {
        return containerName;
    }

    /** True when this held item takes the output: the recipe's container, or else a plain empty glass bottle. */
    public boolean takesContainer(ItemStack item) {
        if (item == null) return false;
        if (container == null) return item.getType() == Material.GLASS_BOTTLE;
        return TLibs.getItemAPI().getChecker().checkItemWithPath(item, container);
    }

    /** True when this held item is the container of some recipe (so it's for bottling, not an ingredient). */
    public boolean isContainer(ItemStack item) {
        return container != null && takesContainer(item);
    }

    /** Gives the output the track of each extra in the saucepan, at its first step (Vanilla Hot Chocolate). */
    public void applyExtras(FoodItem output, Collection<FoodItem> present) {
        for (String extra : sort(present).extras) {
            TagTrack track = TrackLoader.getByString(extras.get(extra));
            if (track == null) continue;
            track = new TagTrack(track);
            track.setValue(0);
            output.addOrModifyTrack(track);
        }
    }

    /** True when the saucepan's liquid is this recipe's: the poured food, or a cup of water for {@value #WATER}. */
    public boolean usesLiquid(FoodItem liquidFood, boolean water) {
        if (WATER.equalsIgnoreCase(liquid.trim())) return water;
        return CookingPathHandler.matches(liquidFood, liquid);
    }

    /** True when this food is one of the recipe's ingredients or extras not already in the saucepan. */
    public boolean takes(FoodItem candidate, Collection<FoodItem> present) {
        Sorted sorted = sort(present);
        if (!sorted.fits) return false;
        if (firstMatch(sorted.open, candidate) != null) return true;
        String extra = firstMatch(extras.keySet(), candidate);
        return extra != null && !sorted.extras.contains(extra);
    }

    /** The saucepan holds this liquid and exactly these ingredients, each once, and any extras once each. */
    public boolean matches(FoodItem liquidFood, boolean water, Collection<FoodItem> present) {
        if (!usesLiquid(liquidFood, water)) return false;
        Sorted sorted = sort(present);
        return sorted.fits && sorted.open.isEmpty();
    }

    /**
     * Sorts what's in the saucepan into the ingredient paths still open and the extras in it. It doesn't fit when
     * something is neither an open ingredient nor an extra not in yet.
     */
    private Sorted sort(Collection<FoodItem> present) {
        List<String> open = new ArrayList<>(ingredients);
        List<String> used = new ArrayList<>();
        boolean fits = true;
        for (FoodItem food : present) {
            String filled = firstMatch(open, food);
            if (filled != null) {
                open.remove(filled);
                continue;
            }
            String extra = firstMatch(extras.keySet(), food);
            if (extra == null || used.contains(extra)) fits = false;
            else used.add(extra);
        }
        return new Sorted(open, used, fits);
    }

    private record Sorted(List<String> open, List<String> extras, boolean fits) {}

    private static String firstMatch(Collection<String> paths, FoodItem food) {
        for (String path : paths) {
            if (CookingPathHandler.matches(food, path)) return path;
        }
        return null;
    }
}
