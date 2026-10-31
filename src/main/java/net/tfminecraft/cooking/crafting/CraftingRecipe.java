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
    /** On a station with fill-models, its inputs show as the rising fill. False keeps them as themselves (olives in the cheese press). */
    private boolean fill;

    public CraftingRecipe(String id, ConfigurationSection config) {
        this.id = id;
        this.inputs = config.getStringList("inputs");
        this.output = config.getString("output", "none");
        this.ratio = config.getInt("ratio", 1);
        this.processed = config.getBoolean("processed", false);
        this.keepOutputAge = config.getBoolean("keep-output-age", false);
        this.yield = Math.max(1, config.getInt("yield", 1));
        this.inherit = config.getBoolean("inherit", false);
        this.fill = config.getBoolean("fill", true);
    }

    public boolean canAdd(ItemStack item) {
        for(String input : inputs) {
            if(TLibs.getItemAPI().getChecker().checkItemWithPath(item, input)) return true;
        }
        return false;
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
    public boolean fills() {
        return fill;
    }
}
