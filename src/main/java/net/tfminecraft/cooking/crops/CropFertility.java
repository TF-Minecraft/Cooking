package net.tfminecraft.cooking.crops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.simplefactions.map.fertility.FertilityProvinceResolver;

public final class CropFertility {

    private CropFertility() {}

    public static boolean mapActive() {
        Plugin simpleFactions = Bukkit.getPluginManager().getPlugin("SimpleFactions");
        if (simpleFactions == null || !simpleFactions.isEnabled()) {
            return false;
        }
        try {
            return FertilityProvinceResolver.isActive();
        } catch (NoClassDefFoundError | NoSuchMethodError | RuntimeException ignored) {
            return false;
        }
    }

    public static int at(Location location) {
        Plugin simpleFactions = Bukkit.getPluginManager().getPlugin("SimpleFactions");
        if (simpleFactions == null || !simpleFactions.isEnabled()) {
            return 0;
        }
        try {
            return lookup(location);
        } catch (NoClassDefFoundError | NoSuchMethodError | RuntimeException ignored) {
            return 0;
        }
    }

    private static int lookup(Location location) {
        if (!FertilityProvinceResolver.isActive()) {
            return 0;
        }
        return Math.max(0, Math.min(100, FertilityProvinceResolver.fertilityAt(location)));
    }
}
