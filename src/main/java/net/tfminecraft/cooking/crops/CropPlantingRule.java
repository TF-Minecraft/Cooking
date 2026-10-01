package net.tfminecraft.cooking.crops;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** Food crops need an open column above them, even under transparent roofs. */
public final class CropPlantingRule {
    private static final Set<Material> OUTDOOR_CROPS = Set.of(
            Material.WHEAT, Material.POTATOES, Material.CARROTS, Material.BEETROOTS,
            Material.MELON_STEM, Material.PUMPKIN_STEM, Material.SUGAR_CANE,
            Material.CACTUS, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);

    private CropPlantingRule() {}

    public static boolean requiresOpenSky(Material material) {
        return OUTDOOR_CROPS.contains(material);
    }

    public static boolean customRequiresOpenSky(String cropId) {
        // Yeast is the fungal crop in the CustomCrops catalog.
        return !"yeast".equalsIgnoreCase(cropId);
    }

    public static boolean hasOpenSky(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (int y = location.getBlockY() + 1; y < world.getMaxHeight(); y++) {
            if (!world.getBlockAt(location.getBlockX(), y, location.getBlockZ()).getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
