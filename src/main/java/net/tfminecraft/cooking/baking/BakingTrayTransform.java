package net.tfminecraft.cooking.baking;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;

public final class BakingTrayTransform {
    private static final String COOKED_TRACK = "cooked";

    private BakingTrayTransform() {}

    public static FoodItem doughToLoaf(FoodItem dough, BakingTrayFill fill) {
        if (dough == null || fill == null) {
            return null;
        }

        FoodItem breadTemplate = FoodLoader.getByString(fill.getFood());
        if (breadTemplate == null) {
            return null;
        }

        FoodItem loaf = new FoodItem(breadTemplate);

        String category = dough.getCategory();
        if (category == null || category.isBlank()) {
            category = fill.getCategory();
        }
        loaf.setCategory(category);

        if (dough.getOrigin() != null) {
            loaf.setOrigin(dough.getOrigin());
        }

        loaf.setQualityRange(dough.getQualityMin(), dough.getQualityMax());

        loaf.setLineage(dough.getLineage());

        for (String ingredient : dough.getIngredients()) {
            loaf.addIngredient(ingredient);
        }

        for (TagTrack track : dough.getTagTracks()) {
            if (COOKED_TRACK.equals(track.getId())) {
                continue;
            }
            loaf.addOrModifyTrack(new TagTrack(track));
        }

        applyTagSpec(loaf, fill.getTags());

        return loaf;
    }

    private static void applyTagSpec(FoodItem item, String tagsSpec) {
        if (item == null || tagsSpec == null || tagsSpec.isBlank()) {
            return;
        }

        for (String part : tagsSpec.split(":")) {
            if (!part.contains(".")) {
                continue;
            }

            String[] kv = part.split("\\.", 2);
            if (kv.length != 2) {
                continue;
            }

            TagTrack baseTrack = TrackLoader.getByString(kv[0]);
            if (baseTrack == null) {
                continue;
            }

            int value;
            try {
                value = Integer.parseInt(kv[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            TagTrack track = new TagTrack(baseTrack);
            track.setValue(value);
            item.addOrModifyTrack(track);
        }
    }
}
