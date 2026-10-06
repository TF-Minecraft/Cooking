package net.tfminecraft.cooking.utils;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;

public final class StackNormalizer {

    private StackNormalizer() {}

    public static long quantizedNow() {
        return System.currentTimeMillis() / 1000L * 1000L;
    }

    public static boolean needsNormalize(FoodItem fi) {
        if (fi == null || !fi.shouldUpdate()) {
            return false;
        }
        if (fi.hasAgeRemainder()) {
            return true;
        }
        for (TagTrack track : fi.getTagTracks()) {
            if (!track.isAgeable() || track.getSteps().isEmpty()) {
                continue;
            }
            TagStep step = track.getCurrentStep();
            if (track.getValue() != (int) step.getRequiredValue()) {
                return true;
            }
        }
        if (fi.hasSauce() && needsNormalize(fi.getSauce())) {
            return true;
        }
        return false;
    }

    public static void normalize(FoodItem fi) {
        if (fi == null || !fi.shouldUpdate()) {
            return;
        }
        for (TagTrack track : fi.getTagTracks()) {
            if (!track.isAgeable() || track.getSteps().isEmpty()) {
                continue;
            }
            TagStep step = track.getCurrentStep();
            track.forceSetValue((int) step.getRequiredValue());
        }
        fi.clearAgeRemainders();
        if (fi.hasSauce()) {
            normalize(fi.getSauce());
        }
    }
}
