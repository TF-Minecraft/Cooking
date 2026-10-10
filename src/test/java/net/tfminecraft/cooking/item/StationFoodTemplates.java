package net.tfminecraft.cooking.item;

import java.util.List;

import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;

/** Builds bare food templates for tests outside this package. */
public final class StationFoodTemplates {
    public static final int ROTTEN = 259200;

    private StationFoodTemplates() {}

    public static FoodItem template(String id, String category) {
        FoodItem food = new FoodItem(id, id, false);
        food.setCategory(category);
        return food;
    }

    public static TagTrack freshness() {
        return new TagTrack("freshness", true, List.of(
                new TagStep("fresh", "Fresh", 0, 1, 1),
                new TagStep("rotten", "Rotten", ROTTEN, 1, 1)));
    }

    /** A template whose freshness ages and whose seasoning does not. */
    public static FoodItem withTracks(String id, String category) {
        FoodItem food = template(id, category);
        food.addOrModifyTrack(freshness());
        food.addOrModifyTrack(new TagTrack("salted", false, List.of(new TagStep("salted", "Salted", 1, 1, 1))));
        food.addOrModifyTrack(new TagTrack("warmth", true, List.of()));
        return food;
    }
}
