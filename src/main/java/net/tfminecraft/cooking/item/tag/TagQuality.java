package net.tfminecraft.cooking.item.tag;

public final class TagQuality {
    private TagQuality() {}

    /** Sum of star offsets on the active step of each track. */
    public static int stars(Iterable<TagTrack> tracks) {
        if (tracks == null) {
            return 0;
        }
        int total = 0;
        for (TagTrack track : tracks) {
            if (track == null) {
                continue;
            }
            TagStep step = track.getCurrentStep();
            if (step != null) {
                total += step.getStars();
            }
        }
        return total;
    }
}
