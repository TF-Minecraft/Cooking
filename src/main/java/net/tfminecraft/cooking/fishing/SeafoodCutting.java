package net.tfminecraft.cooking.fishing;

public final class SeafoodCutting {
    private SeafoodCutting() {}

    public static SeafoodYield plan(String cut, Integer sizeCm) {
        if (sizeCm == null || sizeCm < 1 || cut == null || cut.isBlank()) {
            return null;
        }
        CutRule rule = CustomFishingCatalog.cutRule(cut);
        if (rule == null) {
            return null;
        }
        return new SeafoodYield(rule.outputType(), 1, 0);
    }
}
