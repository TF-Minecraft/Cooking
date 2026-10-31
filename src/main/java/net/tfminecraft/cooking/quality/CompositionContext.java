package net.tfminecraft.cooking.quality;

public enum CompositionContext {
    CUTTING_BOARD(true),
    MIXING_BOWL(true),
    SAUCE_SCOOP(true),
    SOUP_SCOOP(true),
    CARVE(true),
    CHURN(true),
    FRYING_PAN(true),
    SAUSAGE_MAKER(true),
    SANDWICH(true),
    PIZZA(true),
    SALAD(true),
    PIE(true),
    MILLING(false),
    BAKING(false);

    private final boolean allowsChefBoost;
    @Deprecated
    private final boolean filterExcludedCategories;

    CompositionContext(boolean allowsChefBoost) {
        this(allowsChefBoost, true);
    }

    @Deprecated
    CompositionContext(boolean allowsChefBoost, boolean filterExcludedCategories) {
        this.allowsChefBoost = allowsChefBoost;
        this.filterExcludedCategories = filterExcludedCategories;
    }

    public boolean allowsChefBoost() {
        return allowsChefBoost;
    }

    @Deprecated
    public boolean filterExcludedCategories() {
        return filterExcludedCategories;
    }
}
