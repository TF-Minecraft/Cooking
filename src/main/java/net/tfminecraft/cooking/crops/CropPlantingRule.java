package net.tfminecraft.cooking.crops;

import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
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
    private static Set<Material> lowCover = Set.of();
    private static int lowCoverHeight = 0;
    private static Set<Material> exemptVanilla = Set.of(Material.NETHER_WART, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM);
    private static Set<String> exemptCustom = Set.of("yeast");

    public static void configure(ConfigurationSection section) {
        enabled = section == null || section.getBoolean("require-open-sky", true);
        allowGlass = section == null || section.getBoolean("allow-glass-roofs", true);
        allowedCover = materials(section, "allowed-cover", "allowed-cover", Set.of());
        ConfigurationSection low = section == null ? null : section.getConfigurationSection("low-cover");
        lowCover = materials(low, "materials", "low-cover.materials", Set.of());
        lowCoverHeight = low == null ? 0 : Math.max(0, low.getInt("max-height", 5));
        exemptVanilla = materials(section, "exempt-vanilla", "exempt-vanilla", Set.of(Material.NETHER_WART, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM));
        exemptCustom = section == null || !section.contains("exempt-custom") ? Set.of("yeast")
                : Set.copyOf(section.getStringList("exempt-custom").stream().map(id -> id.toLowerCase(Locale.ROOT)).toList());
    }

    private static Set<Material> materials(ConfigurationSection section, String key, String label, Set<Material> defaults) {
        if (section == null || !section.contains(key)) {
            return defaults;
        }
        Set<Material> result = new HashSet<>();
        for (String name : section.getStringList(key)) {
            if (name.startsWith("#")) {
                Tag<Material> tag = blockTag(name.substring(1));
                if (tag != null) {
                    result.addAll(tag.getValues());
                    continue;
                }
            } else {
                Material material = Material.matchMaterial(name);
                if (material != null && material.isBlock()) {
                    result.add(material);
                    continue;
                }
            }
            Bukkit.getLogger().warning("[Cooking] Invalid planting material in " + label + ": " + name);
        }
        return Set.copyOf(result);
    }

    /** {@code #slabs} or {@code #minecraft:slabs}; null when the tag is unknown. */
    private static Tag<Material> blockTag(String name) {
        NamespacedKey key = name.isEmpty() ? null : NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
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
        int crop = location.getBlockY();
        for (int y = crop + 1; y < world.getMaxHeight(); y++) {
            Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
            Material cover = block.getType();
            if (!cover.isAir() && !allowedCover.contains(cover)
                    && !(allowGlass && (cover.name().endsWith("GLASS") || cover.name().endsWith("GLASS_PANE")))
                    && !(y - crop <= lowCoverHeight && isLowCover(block, cover))) {
                return false;
            }
        }
        return true;
    }

    /** Low decoration such as slabs, stairs and signs; a double slab is a full block, so it stays a roof. */
    private static boolean isLowCover(Block block, Material cover) {
        return lowCover.contains(cover)
                && !(block.getBlockData() instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE);
    }
}
