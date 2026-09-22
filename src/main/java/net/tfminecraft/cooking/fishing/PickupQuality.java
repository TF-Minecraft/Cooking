package net.tfminecraft.cooking.fishing;

import org.bukkit.entity.Player;

import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.quality.QualityConfig;

public final class PickupQuality {
    private PickupQuality() {}

    public static int finish(int rolled, Player player) {
        int min = QualityConfig.getPickupMin();
        int max = QualityConfig.getPickupMax();
        int inRange = Math.max(min, Math.min(max, rolled));
        return OriginQualityResolver.applyPickupPermissions(player, inRange);
    }
}
