package net.tfminecraft.cooking.brewery;

import java.util.function.Function;
import java.util.regex.Pattern;

import org.bukkit.inventory.ItemStack;

/** A finished brew as the saucepan sees it. The reader is set only while BreweryX is enabled. */
public record BrewLiquid(String name, int stars, String colour, boolean alcoholic) {
    private static final Pattern FORMATTING = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-orx])");
    private static Function<ItemStack, BrewLiquid> reader;

    public static void setReader(Function<ItemStack, BrewLiquid> value) {
        reader = value;
    }

    /** The held brew, or null when the item is not a finished brew or BreweryX is not running. */
    public static BrewLiquid from(ItemStack item) {
        Function<ItemStack, BrewLiquid> current = reader;
        return current == null || item == null ? null : current.apply(item);
    }

    /** Brewery rates 1-10 and shows half stars. A half star rounds up, like Cooking's ingredient averages. */
    public static int starsFor(int breweryQuality) {
        return Math.clamp((breweryQuality + 1) / 2, 1, 5);
    }

    /** Recipe names such as "&6Golden Mead" keep their words but not their colour codes. */
    public static String plainName(String name) {
        return name == null ? "" : FORMATTING.matcher(name).replaceAll("").strip();
    }
}
