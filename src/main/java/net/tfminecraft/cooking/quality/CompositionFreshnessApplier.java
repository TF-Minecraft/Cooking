package net.tfminecraft.cooking.quality;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;

public final class CompositionFreshnessApplier {
    private CompositionFreshnessApplier() {}

    /**
     * Drops the ageing a dish took from its ingredients but doesn't do itself: a pizza keeps its freshness, not its
     * cheese's ripening or its dough's rise. A type that lists no age keeps all of it.
     */
    public static void keepOwnAgeing(FoodItem dish) {
        if (dish.getAgeingTracks().isEmpty()) {
            return;
        }
        List<String> foreign = new ArrayList<>();
        for (TagTrack track : dish.getTagTracks()) {
            if (track.isAgeable() && !dish.getAgeingTracks().contains(track.getId())) foreign.add(track.getId());
        }
        foreign.forEach(dish::removeTrack);
    }

    public static void applyTracks(FoodItem target, Map<String, Integer> freshnessTracks) {
        if (target == null || freshnessTracks == null || freshnessTracks.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : freshnessTracks.entrySet()) {
            TagTrack template = TrackLoader.getByString(entry.getKey());
            if (template == null) {
                continue;
            }
            TagTrack track = new TagTrack(template);
            track.setValue(entry.getValue());
            target.addOrModifyTrack(track);
        }
    }
}
