package net.tfminecraft.cooking.cooking;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.enums.Tag;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.item.tag.TagQuality;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.utils.FoodParser;

/**
 * A cooking option that names an output (types.yml cooking-options.&lt;method&gt;.output) turns the piece into that
 * food once it's done: chopped corn in the frying pan pops into popcorn. The piece cooks raw, then becomes the output,
 * cooked (or burnt, if it burnt first), and keeps cooking by the same clock, so popcorn left too long burns as popcorn.
 */
public final class CookingOutput {
    private static final String COOKED_TRACK = "cooked";

    private CookingOutput() {}

    /**
     * A piece whose type has no doneness of its own (cooking-options with only outputs or raw-in-cookware) starts raw
     * once it's in the cookware, so the usual raw, cooked, burnt steps can follow.
     */
    static void startRaw(FoodItem piece, Method method) {
        CookParameter parameter = piece.getCookData().getParameters().get(method);
        if (parameter == null || !parameter.rawInCookwareOnly() || piece.getCookData().cooksInPlace()
                || piece.hasTagTrack(COOKED_TRACK)) {
            return;
        }
        piece.addTagTrack(COOKED_TRACK);
    }

    /**
     * A piece taken off before it turned is still what went in: drops the raw track {@link #startRaw} gave it.
     * True when the piece is such an unturned input.
     */
    static boolean restoreInput(FoodItem piece, Method method) {
        if (output(piece, method) == null || piece.getCookData().cooksInPlace()) {
            return false;
        }
        if (piece.hasTagTrack(COOKED_TRACK) && !piece.hasTag(Tag.RAW)) {
            return false;
        }
        piece.removeTrack(COOKED_TRACK);
        return true;
    }

    /**
     * The food the piece has become, once it is no longer raw, or the piece itself. The output keeps the piece's
     * stars, its ageing tracks (freshness, warmth), lineage and ingredients, and takes its origin when the output string
     * names none. Its own tags come from the output string; preparation tags such as Chopped stay behind.
     */
    static FoodItem turn(FoodItem piece, Method method) {
        String output = output(piece, method);
        if (output == null || !piece.hasTagTrack(COOKED_TRACK) || piece.hasTag(Tag.RAW)) {
            return piece;
        }
        FoodParser.Result parsed = FoodParser.parse(output);
        if (parsed == null || parsed.template == null) {
            return piece;
        }
        FoodItem turned = parsed.template;
        if (turned.getOrigin() == null || turned.getOrigin().isBlank()) {
            turned.setOrigin(piece.getOrigin());
        }
        List<TagTrack> kept = new ArrayList<>();
        for (TagTrack track : piece.getTagTracks()) {
            if (track.isAgeable() || COOKED_TRACK.equals(track.getId())) {
                kept.add(track);
            }
        }
        for (TagTrack track : kept) {
            turned.addOrModifyTrack(track);
            turned.setAgeRemainder(track.getId(), piece.getAgeRemainder(track.getId()));
        }
        int stars = piece.getEffectiveQuality() - TagQuality.stars(kept);
        turned.setQualityRange(stars, stars);
        turned.setLineage(piece.getLineage());
        for (String ingredient : piece.getIngredients()) {
            turned.addIngredient(ingredient);
        }
        CookData clock = new CookData(turned, piece.getCookData());
        clock.getParameters().replaceAll((m, parameter) -> parameter.withoutOutput());
        turned.cookData = clock;
        turned.setLastUpdate(piece.getLastUpdate());
        return turned;
    }

    private static String output(FoodItem piece, Method method) {
        CookParameter parameter = piece.getCookData().getParameters().get(method);
        return parameter == null ? null : parameter.getOutput();
    }
}
