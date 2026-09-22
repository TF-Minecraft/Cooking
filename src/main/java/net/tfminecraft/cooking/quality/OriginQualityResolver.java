package net.tfminecraft.cooking.quality;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.QualityUtils;

public final class OriginQualityResolver {
    private OriginQualityResolver() {}

    public static int resolve(Player player, FoodItem template) {
        int min = QualityConfig.getPickupMin();
        int max = QualityConfig.getPickupMax();
        int rolled = ThreadLocalRandom.current().nextInt(min, max + 1);
        return applyPickupPermissions(player, rolled);
    }

    public static int applyPickupPermissions(Player player, int rolled) {
        int bestMinQuality = QualityConfig.getPickupMin();
        int bestRollBias = 0;

        for (PermissionEffectsConfig.PickupEffect effect : PermissionEffectsConfig.getPickupEffects()) {
            if (player == null || !player.hasPermission(effect.permission())) {
                continue;
            }
            if (effect.minQuality() > bestMinQuality) {
                bestMinQuality = effect.minQuality();
            }
            if (effect.rollBias() > bestRollBias) {
                bestRollBias = effect.rollBias();
            }
        }

        return adjust(rolled, bestMinQuality, bestRollBias);
    }

    public static int adjust(int rolled, int minQuality, int rollBias) {
        int quality = rolled + rollBias;
        quality = Math.max(quality, minQuality);
        return QualityUtils.clamp(quality);
    }
}
