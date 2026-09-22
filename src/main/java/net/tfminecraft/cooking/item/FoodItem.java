package net.tfminecraft.cooking.item;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.item.data.OverrideData;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ModelLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.cooking.utils.WarmthUtils;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.enums.Tag;

import java.util.*;

public class FoodItem {

    private final String id;
    private final String name;
    private long lastUpdate;
    private boolean update = true;
    private boolean valuable = false;
    private boolean mashable = false;
    private boolean edible = true;
    private Integer catchSizeCm;
    private String seafoodCutType;
    private String customFishingId;
    private String category = "";

    private Map<String, TagTrack> tags = new HashMap<>();

    private String origin = null;

    private int amount = 1;
    private int qualityMin = 0;
    private int qualityMax = 0;

    private double baseFood;
    private double baseNutrition;
    private boolean baseOverride;

    private FoodModel model = null;

    private Map<String, OverrideData> overrides = new HashMap<>();
    private Map<String, Double> age = new HashMap<>();
    private Map<String, Double> ageRemainder = new HashMap<>();
    private Map<String, Map<String, String>> tagLabels = new HashMap<>();
    private List<String> ingredients = new ArrayList<>();
    private IngredientLineage lineage = IngredientLineage.empty();
    public CookData cookData;

    private String typeLevelCarveSequence;
    private String carveSequencePdc;
    private int carveNextIndex;
    private int carveRemaining;

    private String sauceName;
    private FoodItem sauce;

    public int _parsedQualMin, _parsedQualMax;

    // --------------------------------------------------------------
    // CONSTRUCTOR FROM CONFIG
    // --------------------------------------------------------------
    public FoodItem(String key, ConfigurationSection config) {
        this.id = key;
        this.name = StringFormatter.formatHex(config.getString("name", "Unknown Food"));

        this.baseFood = config.getDouble("food", 1.0);
        this.baseNutrition = config.getDouble("nutrition", 1.0);
        this.update = config.getBoolean("update", true);
        this.valuable = config.getBoolean("valuable", false);
        this.mashable = config.getBoolean("mashable", false);
        this.edible = config.getBoolean("edible", true);
        this.typeLevelCarveSequence = config.getString("carve-sequence", null);

        String modelId = config.getString("model", null);
        if (modelId != null)
            this.model = ModelLoader.getById(modelId);

        // Load device-specific cooking states
        ConfigurationSection cooking = config.getConfigurationSection("cooking-options");
        if (cooking != null) {
            cookData = new CookData(key, cooking);
        } else {
            cookData = new CookData();
        }

        // Overrides (origin-based)
        ConfigurationSection overSec = config.getConfigurationSection("overrides");
        if (overSec != null) {
            for (String originKey : overSec.getKeys(false)) {
                ConfigurationSection o = overSec.getConfigurationSection(originKey);
                if (o != null) {
                    overrides.put(
                        originKey.toUpperCase(),
                        new OverrideData(
                            o.getString("name", null),
                            o.getString("model", null),
                            o.getString("carve-sequence", null),
                            readAge(o.getConfigurationSection("age"))
                        )
                    );
                }
            }
        }

        this.age.putAll(readAge(config.getConfigurationSection("age")));

        ConfigurationSection labelSec = config.getConfigurationSection("tag-labels");
        if (labelSec != null) {
            for (String trackId : labelSec.getKeys(false)) {
                ConfigurationSection stepSec = labelSec.getConfigurationSection(trackId);
                if (stepSec == null) continue;
                Map<String, String> stepLabels = new HashMap<>();
                for (String stepId : stepSec.getKeys(false)) {
                    String label = stepSec.getString(stepId);
                    if (label != null) {
                        stepLabels.put(stepId, StringFormatter.formatHex(label));
                    }
                }
                if (!stepLabels.isEmpty()) {
                    tagLabels.put(trackId, stepLabels);
                }
            }
        }
    }

    FoodItem(String key, String displayName, boolean update) {
        this.id = key;
        this.name = displayName;
        this.update = update;
        this.cookData = new CookData();
    }

    // --------------------------------------------------------------
    // DEEP COPY
    // --------------------------------------------------------------
    public FoodItem(FoodItem other) {
        this.id = other.id;
        this.name = other.name;
        this.category = other.category;
        this.update = other.update;
        this.valuable = other.valuable;
        this.mashable = other.mashable;
        this.edible = other.edible;
        this.catchSizeCm = other.catchSizeCm;
        this.seafoodCutType = other.seafoodCutType;
        this.customFishingId = other.customFishingId;

        this.origin = other.origin;

        this.amount = other.amount;
        this.qualityMin = other.qualityMin;
        this.qualityMax = other.qualityMax;
        this.baseFood = other.baseFood;
        this.baseNutrition = other.baseNutrition;
        this.baseOverride = other.baseOverride;

        this.overrides = new HashMap<>(other.overrides);
        this.age = new HashMap<>(other.age);
        this.ageRemainder = new HashMap<>(other.ageRemainder);
        this.tagLabels = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : other.tagLabels.entrySet()) {
            this.tagLabels.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        this.typeLevelCarveSequence = other.typeLevelCarveSequence;
        this.carveSequencePdc = other.carveSequencePdc;
        this.carveNextIndex = other.carveNextIndex;
        this.carveRemaining = other.carveRemaining;
        this.ingredients = new ArrayList<>(other.ingredients);
        this.lineage = other.lineage == null ? IngredientLineage.empty() : other.lineage;

        this.model = (other.model == null ? null : new FoodModel(other.model));

        this.cookData = new CookData(this, other.cookData);
        if (other.sauce != null) {
            this.sauce = new FoodItem(other.sauce);
            this.sauceName = other.sauceName; // copy name
        }


        this._parsedQualMin = other._parsedQualMin;
        this._parsedQualMax = other._parsedQualMax;

        this.lastUpdate = System.currentTimeMillis();

        for (TagTrack t : other.tags.values())
            this.tags.put(t.getId(), new TagTrack(t));
        if(this.canBeCooked() && !hasTagTrack("cooked")) addOrModifyTrack(TrackLoader.getByString("cooked"));
    }


    // --------------------------------------------------------------
    // AGE UPDATE
    // --------------------------------------------------------------
    public ItemStack updateAge(ItemStack item) {
        if (!shouldUpdate()) {
            ItemMeta m = item.getItemMeta();
            if (m != null) {
                var pdc = m.getPersistentDataContainer();
                pdc.remove(Keys.LAST_UPDATE);
                pdc.remove(Keys.AGE_REMAINDER);
                item.setItemMeta(m);
            }
            return item;
        }
        updateAge();
        ItemMeta m = item.getItemMeta();
        var pdc = m.getPersistentDataContainer();
        pdc.set(Keys.LAST_UPDATE, PersistentDataType.LONG, System.currentTimeMillis());
        item.setItemMeta(m);
        return item;
    }
    public void updateAge() {
        if (!shouldUpdate()) {
            return;
        }
        long now = System.currentTimeMillis();

        long elapsed = now - lastUpdate;
        if (elapsed <= 0) return;

        int seconds = (int) (elapsed / 1000);
        if (seconds <= 0) {
            return;
        }

        for (TagTrack track : tags.values()) {
            if (!track.isAgeable()) continue;
            AgeScale.Scaled scaled = AgeScale.apply(
                    track.getValue(),
                    seconds,
                    resolveAgeMultiplier(track.getId()),
                    getAgeRemainder(track.getId()));
            if ("warmth".equals(track.getId())) {
                track.forceSetValue(scaled.value());
            } else {
                track.setValue(scaled.value());
            }
            setAgeRemainder(track.getId(), scaled.leftover());
        }
        WarmthUtils.expireIfRoomTemp(this);

        lastUpdate = now;
    }


    // --------------------------------------------------------------
    // GETTERS / SETTERS
    // --------------------------------------------------------------
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean shouldUpdate() { return update; }
    public boolean isValuable() { return valuable; }
    public boolean isMashable() { return mashable; }
    public void setMashable(boolean mashable) { this.mashable = mashable; }
    public boolean isEdible() { return edible; }
    public void setEdible(boolean edible) { this.edible = edible; }
    public Integer getCatchSizeCm() { return catchSizeCm; }
    public void setCatchSizeCm(Integer catchSizeCm) { this.catchSizeCm = catchSizeCm; }
    public String getSeafoodCutType() { return seafoodCutType; }
    public void setSeafoodCutType(String seafoodCutType) { this.seafoodCutType = blankToNull(seafoodCutType); }
    public String getCustomFishingId() { return customFishingId; }
    public void setCustomFishingId(String customFishingId) { this.customFishingId = blankToNull(customFishingId); }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public String getTagLabel(String trackId, String stepId) {
        if (trackId == null || stepId == null) return null;
        Map<String, String> steps = tagLabels.get(trackId);
        if (steps == null) return null;
        return steps.get(stepId);
    }

    public String getCategory() { return category; }
    public void setCategory(String cat) { this.category = cat; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public CookData getCookData() { return cookData; }

    public int getQualityMin() { return qualityMin; }
    public int getQualityMax() { return qualityMax; }
    public void setQualityRange(int min, int max) {
        this.qualityMin = min;
        this.qualityMax = max;
    }

    public double getBaseFood() { return baseFood; }

    public void setBaseFood(double food) {
        this.baseFood = food;
        this.baseOverride = true;
    }

    public double getBaseNutrition() { return baseNutrition; }

    public void setBaseNutrition(double nutrition) {
        this.baseNutrition = nutrition;
        this.baseOverride = true;
    }

    public boolean hasBaseOverride() {
        return baseOverride;
    }

    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long ts) { this.lastUpdate = ts; }

    public void setSauceName(String name) { this.sauceName = name; }
    public String getSauceName() { return sauceName; }
    public boolean hasSauceName() { return sauceName != null && !sauceName.isEmpty(); }

    public void setSauce(FoodItem s) { this.sauce = s; }
    public FoodItem getSauce() { return sauce; }
    public boolean hasSauce() { return sauce != null; }


    public FoodModel getModel() {
        if(overrides.isEmpty()) return model;
        for(Map.Entry<String, OverrideData> override : overrides.entrySet()) {
            if(override.getValue() == null) continue;
            if(!override.getKey().equalsIgnoreCase(origin)) continue;
            FoodModel ov = ModelLoader.getById(override.getValue().getModel());
            if (ov != null) {
                return ov;
            }
        }
        return model;
    }

    public void setModel(FoodModel foodModel) {
        model = foodModel;
    }

    public ModelData getModelData() {
        if (hasCarveState() && carveRemaining > 0) {
            return net.tfminecraft.cooking.carve.CarvableRoastUtils.getStageModelData(this);
        }
        ModelData data = getModel().getModel(this);
        return data != null ? data : model.getModel(this);
    }

    public Map<String, OverrideData> getOverrides() { return overrides; }
    public List<String> getIngredients() { return ingredients; }
    public IngredientLineage getLineage() {
        return lineage == null ? IngredientLineage.empty() : lineage;
    }
    public void setLineage(IngredientLineage lineage) {
        this.lineage = lineage == null ? IngredientLineage.empty() : lineage;
    }

    public void addIngredient(String ingredient) {
        if(ingredients.contains(ingredient)) return;
        ingredients.add(ingredient); 
    }

    public void setOrigin(String o) { origin = o; }
    public String getOrigin() { return origin; }

    public double resolveAgeMultiplier(String trackId) {
        if (trackId == null) {
            return AgeScale.DEFAULT;
        }
        if (origin != null) {
            OverrideData od = overrides.get(origin.toUpperCase());
            if (od != null) {
                Double override = od.getAge(trackId);
                if (override != null) {
                    return AgeScale.clamp(override);
                }
            }
        }
        Double type = age.get(trackId.toLowerCase());
        return type == null ? AgeScale.DEFAULT : AgeScale.clamp(type);
    }

    public double getAgeRemainder(String trackId) {
        if (trackId == null) {
            return 0;
        }
        return ageRemainder.getOrDefault(trackId.toLowerCase(), 0.0);
    }

    public void setAgeRemainder(String trackId, double leftover) {
        if (trackId == null) {
            return;
        }
        if (leftover <= 0) {
            ageRemainder.remove(trackId.toLowerCase());
            return;
        }
        ageRemainder.put(trackId.toLowerCase(), leftover);
    }

    public boolean hasAgeRemainder() {
        return !ageRemainder.isEmpty();
    }

    public void clearAgeRemainders() {
        ageRemainder.clear();
    }

    public String encodeAgeRemainder() {
        if (ageRemainder.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> entry : ageRemainder.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            if (!first) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
            first = false;
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public void decodeAgeRemainder(String raw) {
        ageRemainder.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String entry : raw.split(";")) {
            int sep = entry.indexOf(':');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            try {
                String trackId = entry.substring(0, sep).toLowerCase();
                double leftover = Double.parseDouble(entry.substring(sep + 1));
                if (leftover > 0) {
                    ageRemainder.put(trackId, leftover);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static Map<String, Double> readAge(ConfigurationSection section) {
        Map<String, Double> map = new HashMap<>();
        if (section == null) {
            return map;
        }
        for (String key : section.getKeys(false)) {
            map.put(key.toLowerCase(), section.getDouble(key, AgeScale.DEFAULT));
        }
        return map;
    }

    public String getCarveSequenceId() {
        if (origin != null) {
            OverrideData od = overrides.get(origin.toUpperCase());
            if (od != null && od.getCarveSequence() != null) {
                return od.getCarveSequence();
            }
        }
        return typeLevelCarveSequence;
    }

    public boolean hasCarveState() {
        return carveSequencePdc != null && !carveSequencePdc.isEmpty();
    }

    public String getCarveSequencePdc() { return carveSequencePdc; }
    public int getCarveNextIndex() { return carveNextIndex; }
    public int getCarveRemaining() { return carveRemaining; }

    public void setCarveState(String sequenceId, int nextIndex, int remaining) {
        this.carveSequencePdc = sequenceId;
        this.carveNextIndex = nextIndex;
        this.carveRemaining = remaining;
    }

    public void setCarveNextIndex(int index) { this.carveNextIndex = index; }
    public void setCarveRemaining(int remaining) { this.carveRemaining = remaining; }

    public List<TagTrack> getTagTracks() {
        List<TagTrack> list = new ArrayList<>(tags.values());
        list.sort(new TagSort());
        return list;
    }

    private class TagSort implements Comparator<TagTrack> {
        public int compare(TagTrack t1, TagTrack t2) {
            if (t1.getIndex() < t2.getIndex()) return -1;
            if (t1.getIndex() > t2.getIndex()) return 1;
            return 0;
        }
    }

    public TagTrack getTagTrack(String s) { 
        return tags.get(s);
    }

    public void addOrModifyTrack(TagTrack t) {
        updateAge();
        if (t == null) {
            return;
        }
        tags.put(t.getId(), new TagTrack(t));
    }

    public void addTagTrack(String trackId) {
        updateAge();
        tags.put(trackId, new TagTrack(TrackLoader.getByString(trackId)));
    }

    public boolean hasTagTrack(String tag) {
        return tags.containsKey(tag);
    }

    public void removeTrack(String id) {
        if (id == null) {
            return;
        }
        tags.remove(id);
    }

    public List<TagStep> getCurrentTags() {
        List<TagStep> current = new ArrayList<>();
        for(TagTrack track : tags.values()) {
            current.add(track.getCurrentStep());
        }
        return current;
    }
    public List<String> getCurrentTagIds() {
        List<String> current = new ArrayList<>();
        for(TagTrack track : tags.values()) {
            current.add(track.getCurrentStep().getId());
        }
        return current;
    }

    public boolean sameTags(FoodItem other) {
        List<String> myTags = getCurrentTagIds();
        List<String> otherTags = other.getCurrentTagIds();
        if(myTags.size() != otherTags.size()) return false;
        for(String t : myTags) {
            if(!otherTags.contains(t)) return false;
        }
        return true;
    }

    public boolean hasTag(Tag tag) {
        for(TagStep t : getCurrentTags()) {
            if(t.getTag().equals(tag)) return true;
        }
        return false;
    }

    //state stuff
    public boolean canBeCooked() {
        return !cookData.getParameters().isEmpty();
    }


    // --------------------------------------------------------------
    // FINAL VALUES (QUALITY + TAGS + COOK STATE)
    // --------------------------------------------------------------
    public double getFinalFood() {
        if (hasCarveState() && carveRemaining > 0) {
            return applyMultipliers(net.tfminecraft.cooking.carve.CarvableRoastUtils.getRemainingFood(this), 0)
                    + sauceFinal(0);
        }
        return applyMultipliers(baseFood, 0) + sauceFinal(0);
    }

    public double getFinalNutrition() {
        if (hasCarveState() && carveRemaining > 0) {
            return applyMultipliers(net.tfminecraft.cooking.carve.CarvableRoastUtils.getRemainingNutrition(this), 1)
                    + sauceFinal(1);
        }
        return applyMultipliers(baseNutrition, 1) + sauceFinal(1);
    }

    private double sauceFinal(int type) {
        if (!hasSauce()) {
            return 0;
        }
        return type == 1 ? getSauce().getFinalNutrition() : getSauce().getFinalFood();
    }

    private double applyMultipliers(double base, int type) {
        int stars = Math.max(1, getQualityMin());
        double qualityMultiplier = type == 1
                ? net.tfminecraft.cooking.quality.QualityConfig.nutritionMultiplier(stars)
                : 1.0 + (stars - 1) * 0.20;

        double result = base * qualityMultiplier;

        for (TagTrack t : tags.values()) {
            TagStep step = t.getCurrentStep();
            if (step != null) {
                if (type == 1) result *= step.getNutritionMultiplier();
                else           result *= step.getFoodMultiplier();
            }
        }

        return Math.round(result * 10.0) / 10.0;
    }


    // --------------------------------------------------------------
    // LOAD FROM PDC
    // --------------------------------------------------------------
    public static FoodItem fromItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;

        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        String id = pdc.get(Keys.FOOD_ID, PersistentDataType.STRING);
        if (id == null) return null;

        FoodItem base = net.tfminecraft.cooking.loader.FoodLoader.getByString(id);
        if (base == null) return null;

        FoodItem out = new FoodItem(base);

        String cat = pdc.get(Keys.CATEGORY, PersistentDataType.STRING);
        if (cat != null) out.setCategory(cat);

        String originStr = pdc.get(Keys.ORIGIN, PersistentDataType.STRING);
        if (originStr != null) out.setOrigin(originStr);

        Integer qual = pdc.get(Keys.QUALITY, PersistentDataType.INTEGER);
        if (qual != null) out.setQualityRange(qual, qual);

        Double storedFood = pdc.get(Keys.BASE_FOOD, PersistentDataType.DOUBLE);
        if (storedFood != null) {
            out.setBaseFood(storedFood);
        }
        Double storedNutrition = pdc.get(Keys.BASE_NUTRITION, PersistentDataType.DOUBLE);
        if (storedNutrition != null) {
            out.setBaseNutrition(storedNutrition);
        }

        // Tag tracks
        String tagData = pdc.get(Keys.TAGS, PersistentDataType.STRING);
        if (tagData != null && !tagData.isEmpty()) {

            for (String entry : tagData.split(";")) {
                if (!entry.contains(".")) continue;

                String[] kv = entry.split("\\.");
                if (kv.length != 2) continue;

                String trackId = kv[0];
                int value;
                try { value = Integer.parseInt(kv[1]); }
                catch (Exception e) { continue; }

                int migratedValue = AgeScale.migrateTrackValue(trackId, value);
                String migratedId = AgeScale.migrateTrackId(trackId);
                TagTrack baseTrack = TrackLoader.getByString(migratedId);
                if (baseTrack == null) continue;

                TagTrack t = new TagTrack(baseTrack);
                if ("warmth".equals(t.getId())) {
                    t.forceSetValue(migratedValue);
                } else {
                    t.setValue(migratedValue);
                }
                out.addOrModifyTrack(t);
            }
        }

        out.decodeAgeRemainder(pdc.get(Keys.AGE_REMAINDER, PersistentDataType.STRING));

        if (!out.shouldUpdate()) {
            out.setLastUpdate(0);
        } else {
            Long lastUpdate = pdc.get(Keys.LAST_UPDATE, PersistentDataType.LONG);
            if (lastUpdate != null) {
                out.setLastUpdate(lastUpdate);
            } else {
                out.setLastUpdate(System.currentTimeMillis());
            }
        }

        // --- Load sauce ---
        String sauceData = pdc.get(Keys.SAUCE, PersistentDataType.STRING);
        if (sauceData != null && !sauceData.isEmpty()) {

            // Full nested parse
            FoodParser.Result sauceResult = FoodParser.parse(sauceData);

            if (sauceResult != null && sauceResult.template != null) {
                FoodItem sauceItem = sauceResult.template;

                // Restore sauce category explicitly (FoodParser.parse preserves it)
                out.setSauce(sauceItem);

                // Debug optional
                // Bukkit.getLogger().info("Loaded sauce: " + sauceItem.getId());
            }
        }

        // --- Load sauce name ---
        String sauceNameData = pdc.get(Keys.SAUCE_NAME, PersistentDataType.STRING);
        if (sauceNameData != null && !sauceNameData.isEmpty()) {
            out.setSauceName(sauceNameData);
        }

        out.setLineage(IngredientLineageCodec.decode(pdc.get(Keys.LINEAGE, PersistentDataType.STRING)));
        out.setCatchSizeCm(pdc.get(Keys.CATCH_SIZE_CM, PersistentDataType.INTEGER));
        out.setSeafoodCutType(pdc.get(Keys.SEAFOOD_CUT_TYPE, PersistentDataType.STRING));
        out.setCustomFishingId(pdc.get(Keys.CUSTOM_FISHING_ID, PersistentDataType.STRING));

        String ingredientsData = pdc.get(Keys.INGREDIENTS, PersistentDataType.STRING);
        if (ingredientsData != null && !ingredientsData.isEmpty()) {
            for (String ing : ingredientsData.split(":")) {
                if (ing != null && !ing.isBlank()) {
                    out.addIngredient(ing);
                }
            }
        }

        if(out.getModel() == null) {
            out.model = new FoodModel(stack);
        }

        String cookMethod = pdc.get(Keys.COOK_METHOD, PersistentDataType.STRING);
        if (cookMethod != null && !cookMethod.isEmpty()) {
            try {
                Integer cookTime = pdc.get(Keys.COOK_TIME, PersistentDataType.INTEGER);
                out.getCookData().restore(Method.valueOf(cookMethod), cookTime != null ? cookTime : 0);
            } catch (IllegalArgumentException ignored) {
            }
        }

        String carveSeq = pdc.get(Keys.CARVE_SEQUENCE, PersistentDataType.STRING);
        if (carveSeq != null) {
            Integer next = pdc.get(Keys.CARVE_NEXT_INDEX, PersistentDataType.INTEGER);
            Integer remaining = pdc.get(Keys.CARVE_REMAINING, PersistentDataType.INTEGER);
            out.setCarveState(carveSeq, next != null ? next : 0, remaining != null ? remaining : 0);
        }

        return out;
    }
}
