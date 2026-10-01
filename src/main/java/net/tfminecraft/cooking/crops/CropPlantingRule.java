package net.tfminecraft.cooking.crops;

import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Food crops need an open column, with configurable greenhouse covers. */
public final class CropPlantingRule {
    private static final Set<Material> OUTDOOR_CROPS = Set.of(
            Material.WHEAT, Material.POTATOES, Material.CARROTS, Material.BEETROOTS,
            Material.MELON_STEM, Material.PUMPKIN_STEM, Material.SUGAR_CANE,
            Material.CACTUS, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);

    private static boolean enabled = true;
    private static boolean allowGlass = true;
    private static Set<Material> allowedCover = Set.of();
    private static Set<Material> exemptVanilla = Set.of(Material.NETHER_WART, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM);
    private static Set<String> exemptCustom = Set.of("yeast");

    public static void configure(ConfigurationSection section) {
        enabled = section == null || section.getBoolean("require-open-sky", true);
        allowGlass = section == null || section.getBoolean("allow-glass-roofs", true);
        allowedCover = materials(section, "allowed-cover", Set.of());
        exemptVanilla = materials(section, "exempt-vanilla", Set.of(Material.NETHER_WART, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM));
        exemptCustom = section == null || !section.contains("exempt-custom") ? Set.of("yeast")
                : Set.copyOf(section.getStringList("exempt-custom").stream().map(id -> id.toLowerCase(Locale.ROOT)).toList());
    }

    private static Set<Material> materials(ConfigurationSection section, String key, Set<Material> defaults) {
        if (section == null || !section.contains(key)) {
            return defaults;
        }
        Set<Material> result = new HashSet<>();
        for (String name : section.getStringList(key)) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) {
                result.add(material);
            } else {
                org.bukkit.Bukkit.getLogger().warning("[Cooking] Invalid planting material in " + key + ": " + name);
            }
        }
        return Set.copyOf(result);
    }

    private CropPlantingRule() {}

    public static boolean requiresOpenSky(Material material) {
        return enabled && (OUTDOOR_CROPS.contains(material)
                || material == Material.NETHER_WART || material == Material.BROWN_MUSHROOM || material == Material.RED_MUSHROOM)
                && !exemptVanilla.contains(material);
    }

    public static boolean customRequiresOpenSky(String cropId) {
        return enabled && (cropId == null || !exemptCustom.contains(cropId.toLowerCase(Locale.ROOT)));
    }

    public static boolean hasOpenSky(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (int y = location.getBlockY() + 1; y < world.getMaxHeight(); y++) {
            Material cover = world.getBlockAt(location.getBlockX(), y, location.getBlockZ()).getType();
            if (!cover.isAir() && !allowedCover.contains(cover)
                    && !(allowGlass && (cover.name().endsWith("GLASS") || cover.name().endsWith("GLASS_PANE")))) {
                return false;
            }
        }
        return true;
    }
}
