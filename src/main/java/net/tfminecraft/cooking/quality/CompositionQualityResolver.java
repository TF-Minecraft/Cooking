package net.tfminecraft.cooking.quality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.utils.QualityUtils;

public final class CompositionQualityResolver {
    private CompositionQualityResolver() {}

    public static int resolve(Player player, Collection<FoodItem> inputs, CompositionContext context) {
        if (CompositionConfig.isLegacyMode()) {
            return legacyResolve(player, inputs, context);
        }
        return compose(player, inputs, context).getFinalQuality();
    }

    public static CompositionResult compose(Player player, Collection<FoodItem> inputs, CompositionContext context) {
        CompositionPartition partition = partitionByRole(inputs, context);
        List<FoodItem> mains = partition.getMains();
        List<FoodItem> extras = partition.getExtras();

        int baseline = resolveMainsQuality(mains);
        Map<String, Integer> freshnessTracks = resolveMainsFreshness(mains);

        int quality = baseline;
        if (CompositionConfig.isModifiersEnabled()) {
            quality = applyExtras(baseline, extras);
        }

        if (context != null && context.allowsChefBoost() && player != null) {
            quality = applyChefBoost(player, quality);
        }

        IngredientLineage lineage = IngredientLineage.from(inputs, context);
        return new CompositionResult(
                baseline,
                QualityUtils.clamp(quality),
                freshnessTracks,
                mains,
                extras,
                partition.getNeutral(),
                lineage
        );
    }

    public static CompositionPartition partitionByRole(Collection<FoodItem> inputs, CompositionContext context) {
        CompositionPartition partition = new CompositionPartition();
        if (inputs == null) {
            return partition;
        }

        for (FoodItem item : inputs) {
            if (item == null) {
                continue;
            }
            switch (CompositionConfig.getRole(item.getCategory(), context)) {
                case EXTRA -> partition.getExtras().add(item);
                case NEUTRAL -> partition.getNeutral().add(item);
                default -> partition.getMains().add(item);
            }
        }
        return partition;
    }

    public static int resolveMainsQuality(List<FoodItem> mains) {
        if (mains == null || mains.isEmpty()) {
            return QualityConfig.getPickupMin();
        }

        int[] qualities = mains.stream()
                .mapToInt(item -> QualityUtils.clamp(item.getQualityMin()))
                .toArray();
        return QualityUtils.average(qualities);
    }

    public static Map<String, Integer> resolveMainsFreshness(List<FoodItem> mains) {
        Map<String, Integer> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        if (mains == null) {
            return Map.of();
        }

        for (FoodItem item : mains) {
            for (TagTrack track : item.getTagTracks()) {
                if (!track.isAgeable()) {
                    continue;
                }
                String id = track.getId();
                sums.merge(id, track.getValue(), Integer::sum);
                counts.merge(id, 1, Integer::sum);
            }
        }

        Map<String, Integer> averaged = new HashMap<>();
        for (Map.Entry<String, Integer> entry : sums.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 1);
            averaged.put(entry.getKey(), (int) Math.round(entry.getValue() / (double) count));
        }
        return averaged;
    }

    public static int applyExtras(int baseline, List<FoodItem> extras) {
        int quality = baseline;
        if (extras == null || extras.isEmpty()) {
            return QualityUtils.clamp(quality);
        }

        double gapUp = CompositionConfig.getGapUpChancePerStar();
        double gapDown = CompositionConfig.getGapDownChancePerStar();

        for (FoodItem extra : extras) {
            int extraStars = QualityUtils.clamp(extra.getQualityMin());
            int gap = extraStars - baseline;

            if (gap > 0) {
                double craftPct = sumCraftQualityPct(extra);
                double multiplier = CompositionConfig.isCraftQualityPctBonus() ? (1.0 + craftPct) : 1.0;
                double chance = gap * gapUp * multiplier;
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    quality++;
                }
            } else if (gap < 0) {
                double chance = Math.abs(gap) * gapDown;
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    quality--;
                }
            }

            for (TagTrack track : extra.getTagTracks()) {
                if (!track.isAgeable()) {
                    continue;
                }
                TagStep step = track.getCurrentStep();
                if (step == null) {
                    continue;
                }
                double reduce = step.getQualityReduce();
                if (reduce > 0 && ThreadLocalRandom.current().nextDouble() < reduce) {
                    quality--;
                }
            }
        }

        return QualityUtils.clamp(quality);
    }

    private static double sumCraftQualityPct(FoodItem item) {
        double sum = 0;
        for (TagTrack track : item.getTagTracks()) {
            TagStep step = track.getCurrentStep();
            if (step != null) {
                sum += step.getCraftQualityPct();
            }
        }
        return sum;
    }

    // Exercise the retained composition compatibility entry point.
    @SuppressWarnings("deprecation")
    private static int legacyResolve(Player player, Collection<FoodItem> inputs, CompositionContext context) {
        List<Integer> qualities = new ArrayList<>();
        if (inputs != null) {
            for (FoodItem item : inputs) {
                if (item == null) {
                    continue;
                }
                if (context != null && context.filterExcludedCategories()
                        && QualityConfig.isExcludedCategory(item.getCategory())) {
                    continue;
                }
                qualities.add(QualityUtils.clamp(item.getQualityMin()));
            }
        }

        int inherited = qualities.isEmpty()
                ? QualityConfig.getPickupMin()
                : QualityUtils.average(qualities.stream().mapToInt(Integer::intValue).toArray());

        if (context != null && context.allowsChefBoost() && player != null) {
            inherited = applyChefBoost(player, inherited);
        }

        return QualityUtils.clamp(inherited);
    }

    private static int applyChefBoost(Player player, int quality) {
        PermissionEffectsConfig.CompositionEffect best = null;
        for (PermissionEffectsConfig.CompositionEffect effect : PermissionEffectsConfig.getCompositionEffects()) {
            if (!player.hasPermission(effect.permission())) {
                continue;
            }
            if (best == null || effect.chance() > best.chance()) {
                best = effect;
            }
        }

        if (best == null || best.chance() <= 0) {
            return quality;
        }

        if (ThreadLocalRandom.current().nextDouble() < best.chance()) {
            return QualityUtils.clamp(quality + best.boost());
        }

        return quality;
    }
}
