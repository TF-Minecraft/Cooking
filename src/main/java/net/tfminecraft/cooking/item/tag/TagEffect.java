package net.tfminecraft.cooking.item.tag;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A status effect a tag step gives whoever eats the food, written in tags.yml {@code effects} as
 * {@code "<effect> <seconds> [level]"}, e.g. {@code "poison 10 1"} for Poison I for 10 seconds. The effect is a
 * Minecraft effect id (nausea, poison, weakness...); the level defaults to 1.
 */
public record TagEffect(PotionEffectType type, int seconds, int level) {

    /** The effect this line names, or null (with a warning naming the track step) when it can't be read. */
    public static TagEffect parse(String line, String where) {
        String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
        if (parts.length < 2 || parts.length > 3) return invalid(line, where);
        NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
        PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
        if (type == null) return invalid(line, where);
        try {
            int seconds = Integer.parseInt(parts[1]);
            int level = parts.length == 3 ? Integer.parseInt(parts[2]) : 1;
            if (seconds <= 0 || level < 1) return invalid(line, where);
            return new TagEffect(type, seconds, level);
        } catch (NumberFormatException e) {
            return invalid(line, where);
        }
    }

    private static TagEffect invalid(String line, String where) {
        Bukkit.getLogger().warning("[Cooking] Invalid tag effect '" + line + "' at " + where
                + " (use \"<effect> <seconds> [level]\")");
        return null;
    }

    /** The effect to give: level 1 is amplifier 0, as Poison I. */
    public PotionEffect toPotionEffect() {
        return new PotionEffect(type, seconds * 20, level - 1);
    }
}
