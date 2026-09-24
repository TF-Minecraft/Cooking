package net.tfminecraft.cooking.utils;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;

public final class DoughRising {
    public static final String TRACK = "rising";

    private DoughRising() {}

    /** New dough starts unrisen. A track already on the item is left as it is. */
    public static void ensureUnrisen(FoodItem dough) {
        if (dough == null || dough.hasTagTrack(TRACK) || !"dough".equalsIgnoreCase(dough.getId())) {
            return;
        }
        TagTrack template = TrackLoader.getByString(TRACK);
        if (template == null) {
            return;
        }
        TagTrack rising = new TagTrack(template);
        rising.setValue(0);
        dough.addOrModifyTrack(rising);
    }
}
