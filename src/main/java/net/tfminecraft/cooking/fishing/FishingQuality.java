package net.tfminecraft.cooking.fishing;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import net.tfminecraft.cooking.quality.OriginQualityResolver;

public final class FishingQuality {
    private FishingQuality() {}

    public static int roll(String rodId, Player player) {
        RodBand band = CustomFishingCatalog.band(rodId);
        int span = Math.max(1, band.max() - band.min() + 1);
        int rolled = band.min() + ThreadLocalRandom.current().nextInt(span);
        return OriginQualityResolver.applyPickupPermissions(player, rolled);
    }

    public static int finish(String rodId, int rolled, Player player) {
        RodBand band = CustomFishingCatalog.band(rodId);
        int inBand = Math.max(band.min(), Math.min(band.max(), rolled));
        return OriginQualityResolver.applyPickupPermissions(player, inBand);
    }
}
