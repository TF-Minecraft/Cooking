package net.tfminecraft.cooking.crafting;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

public class CraftingRecipe {
    private String id;
    private List<String> inputs = new ArrayList<>();
    private String output;
    private int ratio;
    private boolean processed;
    /** The output keeps the age its output line gives it instead of averaging the ingredients' age. */
    private boolean keepOutputAge;
    /** Outputs per recipe unit (a loaf gives 4 slices). */
    private int yield;
    /** The output is the input cut differently: it keeps the input's origin, fillers and tags. One input at a time. */
    private boolean inherit;
    /** With inherit, each output takes an even share of its input's food (a pie's slices hold what the pie did). */
    private boolean shareFood;
    /** On a station with fill-models, its inputs show as the rising fill. False keeps them as themselves (olives in the cheese press). */
    private boolean fill;
    /** One of each input line makes a unit (boiled rice and a fish filet roll into sushi), whatever their origins. */
    private boolean combine;
    /** The input line (1 = the first) whose origin fills the output's {origin}; 0 for none. */
    private int originFrom;

    public CraftingRecipe(String id, ConfigurationSection config) {
        this.id = id;
        this.inputs = config.getStringList("inputs");
        this.output = config.getString("output", "none");
        this.ratio = config.getInt("ratio", 1);
        this.processed = config.getBoolean("processed", false);
        this.keepOutputAge = config.getBoolean("keep-output-age", false);
        this.yield = Math.max(1, config.getInt("yield", 1));
        this.inherit = config.getBoolean("inherit", false);
        this.shareFood = config.getBoolean("share-food", false);
        this.fill = config.getBoolean("fill", true);
        this.combine = config.getBoolean("combine", false);
        this.originFrom = config.getInt("origin-from", 0);
    }

    public boolean canAdd(ItemStack item) {
        return line(item) >= 0;
    }

    /** The input line this item fills (0 = the first), or -1. */
    public int line(ItemStack item) {
        for (int i = 0; i < inputs.size(); i++) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, inputs.get(i))) return i;
        }
        return -1;
    }

    public String getId() {
        return id;
    }
    public List<String> getInputs() {
        return inputs;
    }
    public String getOutput() {
        return output;
    }
    public int getRatio() {
        return ratio;
    }
    public boolean isProcessed() {
        return processed;
    }
    public boolean keepsOutputAge() {
        return keepOutputAge;
    }
    public int getYield() {
        return yield;
    }
    public boolean inherits() {
        return inherit;
    }
    public boolean sharesFood() {
        return shareFood;
    }
    public boolean fills() {
        return fill;
    }
    public boolean combines() {
        return combine;
    }
    public int getOriginFrom() {
        return originFrom;
    }
}
