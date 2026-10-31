package net.tfminecraft.cooking.item.data;

/**
 * One cooking option of a food type (types.yml cooking-options.&lt;method&gt;): the cooked tag it reaches, when, when
 * it burns, and optionally the food it turns into once it's done (output), such as chopped corn popping into popcorn.
 * An option with an output, or with raw-in-cookware: true (a mushroom boiling in the pot), doesn't make the food Raw
 * in hand: it starts raw once it's in that cookware.
 */
public class CookParameter {
    private int tag;
    private int time;
    private int burntime;
    private final String output;
    private final boolean rawInCookware;

    public CookParameter(int tag, int time, int burntime) {
        this(tag, time, burntime, null);
    }

    public CookParameter(int tag, int time, int burntime, String output) {
        this(tag, time, burntime, output, false);
    }

    public CookParameter(int tag, int time, int burntime, String output, boolean rawInCookware) {
        this.tag = tag;
        this.time = time;
        this.burntime = burntime;
        this.output = output == null || output.isBlank() ? null : output.trim();
        this.rawInCookware = rawInCookware;
    }
 
    public int getTag() {
        return tag;
    }
    public int getTime() {
        return time;
    }
    public int getBurnTime() {
        return burntime;
    }

    /** The food string this option turns the piece into once it's no longer raw, or null if it stays itself. */
    public String getOutput() {
        return output;
    }

    /** True when the food is only Raw once it's in this cookware: the option has an output or raw-in-cookware. */
    public boolean rawInCookwareOnly() {
        return output != null || rawInCookware;
    }

    /** The same timings without an output: what the piece keeps cooking by once it has turned into its output. */
    public CookParameter withoutOutput() {
        return output == null ? this : new CookParameter(tag, time, burntime);
    }
}
