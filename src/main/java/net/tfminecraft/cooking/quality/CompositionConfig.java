package net.tfminecraft.cooking.quality;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class CompositionConfig {
    private static boolean legacyMode = true;
    private static Set<String> mainCategories = Set.of();
    private static Set<String> extraCategories = defaultExtras();
    private static Set<String> neutralCategories = Set.of();
    private static Map<CompositionContext, RoleSets> contextOverrides = Map.of();
    private static boolean modifiersEnabled = true;
    private static double gapUpChancePerStar = 0.12;
    private static double gapDownChancePerStar = 0.15;
    private static boolean craftQualityPctBonus = true;

    private CompositionConfig() {}

    public static final class RoleSets {
        private final Set<String> mains;
        private final Set<String> extras;
        private final Set<String> neutrals;

        public RoleSets(Set<String> mains, Set<String> extras, Set<String> neutrals) {
            this.mains = mains == null ? Set.of() : Set.copyOf(mains);
            this.extras = extras == null ? Set.of() : Set.copyOf(extras);
            this.neutrals = neutrals == null ? Set.of() : Set.copyOf(neutrals);
        }

        public Set<String> getMains() {
            return mains;
        }

        public Set<String> getExtras() {
            return extras;
        }

        public Set<String> getNeutrals() {
            return neutrals;
        }
    }

    private static Set<String> defaultExtras() {
        Set<String> set = new HashSet<>();
        set.add("seasoning");
        set.add("sweetener");
        set.add("flour");
        set.add("salt");
        set.add("pepper");
        return set;
    }

    public static void apply(
            boolean legacy,
            Set<String> mains,
            Set<String> extras,
            Set<String> neutrals,
            Map<CompositionContext, RoleSets> overrides,
            boolean modifiersOn,
            double gapUp,
            double gapDown,
            boolean craftPctBonus
    ) {
        legacyMode = legacy;
        mainCategories = mains == null || mains.isEmpty() ? Set.of() : Set.copyOf(mains);
        extraCategories = extras == null || extras.isEmpty() ? defaultExtras() : Set.copyOf(extras);
        neutralCategories = neutrals == null ? Set.of() : Set.copyOf(neutrals);
        contextOverrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        modifiersEnabled = modifiersOn;
        gapUpChancePerStar = Math.max(0, gapUp);
        gapDownChancePerStar = Math.max(0, gapDown);
        craftQualityPctBonus = craftPctBonus;
    }

    public static CompositionRole getRole(String category) {
        if (category == null) {
            return CompositionRole.MAIN;
        }
        String key = category.toLowerCase(Locale.ROOT);
        if (neutralCategories.contains(key)) {
            return CompositionRole.NEUTRAL;
        }
        if (extraCategories.contains(key)) {
            return CompositionRole.EXTRA;
        }
        if (!mainCategories.isEmpty() && !mainCategories.contains(key)) {
            return CompositionRole.NEUTRAL;
        }
        return CompositionRole.MAIN;
    }

    public static CompositionRole getRole(String category, CompositionContext context) {
        if (category == null) {
            return CompositionRole.MAIN;
        }
        String key = category.toLowerCase(Locale.ROOT);

        if (context != null) {
            RoleSets override = contextOverrides.get(context);
            if (override != null) {
                if (override.getNeutrals().contains(key)) {
                    return CompositionRole.NEUTRAL;
                }
                if (override.getExtras().contains(key)) {
                    return CompositionRole.EXTRA;
                }
                if (override.getMains().contains(key)) {
                    return CompositionRole.MAIN;
                }
            }
        }
        return getRole(category);
    }

    public static boolean isLegacyMode() {
        return legacyMode;
    }

    public static boolean isModifiersEnabled() {
        return modifiersEnabled;
    }

    public static double getGapUpChancePerStar() {
        return gapUpChancePerStar;
    }

    public static double getGapDownChancePerStar() {
        return gapDownChancePerStar;
    }

    public static boolean isCraftQualityPctBonus() {
        return craftQualityPctBonus;
    }
}
