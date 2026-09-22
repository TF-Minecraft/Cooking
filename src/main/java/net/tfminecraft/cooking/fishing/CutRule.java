package net.tfminecraft.cooking.fishing;

public record CutRule(
        String cut,
        String outputType,
        double foodPerCm,
        double minTotal,
        double maxTotal,
        double cmPerPortion,
        int maxPortions) {}
