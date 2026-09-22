package net.tfminecraft.cooking.item.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

public class ModelData {
    private final int weight;
    private List<String> tags = new ArrayList<>();

    private final String guiRef;
    private final String displayRef;

    private HashMap<String, String> overrides = new HashMap<>();

    private DisplayData displayData;
    private HashMap<String, DisplayData> furnitureDisplayData = new HashMap<>();

    private final ItemStack directItem;
    private final int stage;

    /** Load from YAML state section */
    public ModelData(ConfigurationSection stateConfig) {
        this(stateConfig, Map.of());
    }

    /** Load from YAML state section, inheriting model-root furniture poses. */
    public ModelData(ConfigurationSection stateConfig, Map<String, DisplayData> parentFurnitureDisplay) {
        ConfigurationSection gui = stateConfig.getConfigurationSection("gui");
        ConfigurationSection display = stateConfig.getConfigurationSection("display");
        weight = stateConfig.getInt("weight", 0);
        stage = stateConfig.getInt("stage", -1);
        directItem = null;
        if (stateConfig.contains("tags")) tags = stateConfig.getStringList("tags");
        if (gui != null) {
            this.guiRef = gui.getString("item", "v.air");
        } else {
            this.guiRef = "v.air";
        }

        if (display != null) {
            this.displayRef = display.getString("item", "v.air");
            if (display.contains("furniture")) {
                for (String s : display.getStringList("furniture")) {
                    String[] parts = s.split("\\s+", 2);
                    if (parts.length < 2) continue;
                    overrides.put(parts[0], parts[1]);
                }
            }
        } else {
            this.displayRef = "v.air";
        }
        if (stateConfig.isConfigurationSection("display-data")) {
            this.displayData = new DisplayData(stateConfig.getConfigurationSection("display-data"));
        } else {
            this.displayData = new DisplayData();
        }
        if (parentFurnitureDisplay != null) {
            this.furnitureDisplayData.putAll(parentFurnitureDisplay);
        }
        this.furnitureDisplayData.putAll(parseFurnitureDisplayData(stateConfig.getConfigurationSection("display-data-furniture")));
    }

    /** Deep copy constructor */
    public ModelData(ModelData other) {
        this.guiRef = other.guiRef;
        this.displayRef = other.displayRef;
        this.weight = other.weight;
        this.displayData = other.displayData;
        this.overrides = new HashMap<>(other.overrides);
        this.furnitureDisplayData = new HashMap<>(other.furnitureDisplayData);
        this.tags.addAll(other.tags);
        this.stage = other.stage;
        this.directItem = other.directItem != null ? other.directItem.clone() : null;
    }

    public ModelData(ItemStack item) {
        this.guiRef = null;
        this.displayRef = null;
        this.weight = 0;
        this.directItem = item.clone();
        this.stage = -1;
        this.displayData = new DisplayData();
        displayData.setxRot(90f);
        displayData.setzRot(90f);
        displayData.setyPos(-0.25f);
    }

    public ItemStack apply(String furniture, ItemStack source) {
        if (directItem != null) {
            ItemStack out = directItem.clone();
            ItemRef.mergeMeta(out, source);
            return out;
        }
        String ref;
        if (furniture != null && overrides.containsKey(furniture)) {
            ref = overrides.get(furniture);
        } else if (furniture != null) {
            ref = displayRef;
        } else {
            ref = guiRef;
        }
        return ItemRef.apply(ref, source);
    }

    public int getWeight() { return weight; }
    public int getStage() { return stage; }
    public List<String> getTags() { return tags; }

    public DisplayData getDisplayData() {
        return displayData;
    }

    public DisplayData getDisplayData(String furnitureId) {
        if (furnitureId != null) {
            DisplayData furniture = furnitureDisplayData.get(furnitureId.toLowerCase());
            if (furniture != null) {
                return furniture;
            }
        }
        return displayData;
    }

    public static Map<String, DisplayData> parseFurnitureDisplayData(ConfigurationSection section) {
        Map<String, DisplayData> map = new HashMap<>();
        if (section == null) {
            return map;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection furniture = section.getConfigurationSection(key);
            if (furniture == null) {
                continue;
            }
            map.put(key.toLowerCase(), new DisplayData(furniture));
        }
        return map;
    }
}
