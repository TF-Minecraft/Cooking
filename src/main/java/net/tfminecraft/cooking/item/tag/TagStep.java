package net.tfminecraft.cooking.item.tag;

import org.bukkit.configuration.ConfigurationSection;
import net.tfminecraft.cooking.enums.Tag;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class TagStep {
    private final String id;
    private Tag tag;
    private final String name;
    private final long requiredValue;

    private final double foodMultiplier;
    private final double nutritionMultiplier;
    private final double qualityReduce;
    private final double craftQualityPct;

    public TagStep(String key, ConfigurationSection config) {
        this(
                key,
                StringFormatter.formatHex(config.getString("name", "Tag")),
                config.getInt("value", 0),
                config.getDouble("food-mult", 1.0),
                config.getDouble("nutrition-mult", 1.0),
                config.getDouble("quality-reduce", 0.0),
                config.getDouble("craft-quality-pct", 0.0));
    }

    public TagStep(String key, String name, long requiredValue, double foodMultiplier, double nutritionMultiplier) {
        this(key, name, requiredValue, foodMultiplier, nutritionMultiplier, 0.0, 0.0);
    }

    public TagStep(String key, String name, long requiredValue, double foodMultiplier, double nutritionMultiplier,
            double qualityReduce, double craftQualityPct) {
        this.id = key;
        this.name = name != null ? name : "Tag";
        try {
            this.tag = Tag.valueOf(key.toUpperCase());
        } catch (Exception e) {
            this.tag = Tag.CUSTOM;
        }
        this.requiredValue = requiredValue;
        this.foodMultiplier = foodMultiplier;
        this.nutritionMultiplier = nutritionMultiplier;
        this.qualityReduce = qualityReduce;
        this.craftQualityPct = craftQualityPct;
    }

    public Tag getTag() {
        return tag;
    }

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public long getRequiredValue() {
        return requiredValue;
    }


    public double getFoodMultiplier() { return foodMultiplier; }
    public double getNutritionMultiplier() { return nutritionMultiplier; }
    public double getQualityReduce() { return qualityReduce; }
    public double getCraftQualityPct() { return craftQualityPct; }
}

