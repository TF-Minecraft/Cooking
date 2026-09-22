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

    public CraftingRecipe(String id, ConfigurationSection config) {
        this.id = id;
        this.inputs = config.getStringList("inputs");
        this.output = config.getString("output", "none");
        this.ratio = config.getInt("ratio", 1);
        this.processed = config.getBoolean("processed", false);
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
}
