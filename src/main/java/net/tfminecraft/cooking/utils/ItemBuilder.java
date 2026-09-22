package net.tfminecraft.cooking.utils;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineageCodec;
import net.tfminecraft.cooking.carve.CarveSequence;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.item.data.OverrideData;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.cache.CategoryDictionary;
import net.tfminecraft.cooking.enums.Tag;
import net.tfminecraft.cooking.quality.OriginQualityResolver;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ItemBuilder {

    public static List<ItemStack> build(FoodItem template, boolean unique, ItemStack base) {
        List<ItemStack> list = new ArrayList<>();
        int amount = template.getAmount();

        if (unique) {
            for (int i = 0; i < amount; i++)
                list.add(buildSingle(template, base));
            return list;
        }

        ItemStack stack = buildSingle(template, base);
        stack.setAmount(amount);
        list.add(stack);

        return list;
    }

    public static ItemStack buildSingle(FoodItem template, ItemStack base) {
        int qualMin = template._parsedQualMin;
        int qualMax = template._parsedQualMax;
        if (qualMax < qualMin || qualMin < 1) {
            qualMin = Math.max(1, template.getQualityMin());
            qualMax = Math.max(qualMin, template.getQualityMax());
        }
        int q = ThreadLocalRandom.current().nextInt(qualMin, qualMax + 1);
        return buildSingleWithQuality(template, base, QualityUtils.clamp(q));
    }

    public static ItemStack buildSingleWithQuality(FoodItem template, int quality) {
        return buildSingleWithQuality(template, null, quality);
    }

    public static ItemStack buildComposedWithQuality(FoodItem template, int quality) {
        return buildSingleWithQuality(template, null, quality, NameComposer.compose(template, Map.of()));
    }

    public static ItemStack buildSingleWithQuality(FoodItem template, ItemStack base, int quality) {
        return buildSingleWithQuality(template, base, quality, null);
    }

    private static ItemStack buildSingleWithQuality(FoodItem template, ItemStack base, int quality,
            String displayNameOverride) {

        FoodItem item = new FoodItem(template);

        item.setQualityRange(quality, quality);

        item.setAmount(1);

        String origin = item.getOrigin();
        String displayName = displayNameOverride != null ? displayNameOverride : item.getName();

        if (displayNameOverride == null) {
            if (origin != null) {
                OverrideData od = item.getOverrides().get(origin.toUpperCase());
                if (od != null) {
                    if (od.getName() != null) displayName = od.getName();
                }
            }
            if (displayName.contains("{inherit}") && base != null) {
                displayName = displayName.replace("{inherit}", StringFormatter.getName(base));
            } else if (displayName.contains("{inherit}")) {
                displayName = displayName.replace("{inherit}", WordUtils.capitalize(origin != null ? origin : "unknown"));
            }
        }
        
        if(item.getModel() == null) item.setModel(new FoodModel(base));
        if (item.getCarveSequenceId() != null) {
            CarveSequence seq = CarveSequenceLoader.get(item.getCarveSequenceId());
            if (seq != null) {
                item.setCarveState(item.getCarveSequenceId(), 0, seq.getStartRemaining());
            }
        }
        ModelData model = item.getModelData();

        ItemStack stack = model.apply(null, new ItemStack(Material.DIRT));
        stamp(stack, item, displayName);

        if (item.getCarveSequenceId() != null) {
            net.tfminecraft.cooking.carve.CarvableRoastUtils.initCarveState(stack, item);
        }

        return stack;
    }

    static boolean writesAgeClock(FoodItem item) {
        return item != null && item.shouldUpdate();
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static ItemStack stamp(ItemStack stack, FoodItem item, String displayName) {
        if (stack == null || item == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (displayName != null) {
            meta.setDisplayName(displayName);
        }

        Map<String, Integer> indexMap = new HashMap<>();
        meta.setLore(buildLore(item, indexMap));

        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.FOOD_ID, PersistentDataType.STRING, item.getId());
        pdc.set(Keys.CATEGORY, PersistentDataType.STRING, item.getCategory() != null ? item.getCategory() : "");

        if (item.getOrigin() != null) {
            pdc.set(Keys.ORIGIN, PersistentDataType.STRING, item.getOrigin());
        } else {
            pdc.remove(Keys.ORIGIN);
        }

        pdc.set(Keys.QUALITY, PersistentDataType.INTEGER, item.getQualityMin());

        if (item.hasBaseOverride()) {
            pdc.set(Keys.BASE_FOOD, PersistentDataType.DOUBLE, item.getBaseFood());
            pdc.set(Keys.BASE_NUTRITION, PersistentDataType.DOUBLE, item.getBaseNutrition());
        } else {
            pdc.remove(Keys.BASE_FOOD);
            pdc.remove(Keys.BASE_NUTRITION);
        }

        if (!writesAgeClock(item)) {
            pdc.remove(Keys.LAST_UPDATE);
            pdc.remove(Keys.AGE_REMAINDER);
        } else {
            long lastUpdate = item.getLastUpdate() > 0 ? item.getLastUpdate() : System.currentTimeMillis();
            pdc.set(Keys.LAST_UPDATE, PersistentDataType.LONG, lastUpdate);

            String remainder = item.encodeAgeRemainder();
            if (remainder != null) {
                pdc.set(Keys.AGE_REMAINDER, PersistentDataType.STRING, remainder);
            } else {
                pdc.remove(Keys.AGE_REMAINDER);
            }
        }

        if (!item.getTagTracks().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (TagTrack t : item.getTagTracks()) {
                if (!first) {
                    sb.append(";");
                }
                sb.append(t.getId()).append(".").append(t.getValue());
                first = false;
            }
            pdc.set(Keys.TAGS, PersistentDataType.STRING, sb.toString());
        } else {
            pdc.remove(Keys.TAGS);
        }

        if (item.hasSauce()) {
            pdc.set(Keys.SAUCE, PersistentDataType.STRING, FoodParser.toString(item.getSauce(), 1));
        } else {
            pdc.remove(Keys.SAUCE);
        }
        if (item.hasSauceName()) {
            pdc.set(Keys.SAUCE_NAME, PersistentDataType.STRING, item.getSauceName());
        } else {
            pdc.remove(Keys.SAUCE_NAME);
        }

        if (!item.getIngredients().isEmpty()) {
            StringBuilder ingSb = new StringBuilder();
            boolean ingFirst = true;
            for (String ing : item.getIngredients()) {
                if (!ingFirst) {
                    ingSb.append(':');
                }
                ingSb.append(ing);
                ingFirst = false;
            }
            pdc.set(Keys.INGREDIENTS, PersistentDataType.STRING, ingSb.toString());
        } else {
            pdc.remove(Keys.INGREDIENTS);
        }

        String lineage = IngredientLineageCodec.encode(item.getLineage());
        if (!lineage.isEmpty()) {
            pdc.set(Keys.LINEAGE, PersistentDataType.STRING, lineage);
        } else {
            pdc.remove(Keys.LINEAGE);
        }

        if (item.getCatchSizeCm() != null) {
            pdc.set(Keys.CATCH_SIZE_CM, PersistentDataType.INTEGER, item.getCatchSizeCm());
        } else {
            pdc.remove(Keys.CATCH_SIZE_CM);
        }
        if (item.getSeafoodCutType() != null) {
            pdc.set(Keys.SEAFOOD_CUT_TYPE, PersistentDataType.STRING, item.getSeafoodCutType());
        } else {
            pdc.remove(Keys.SEAFOOD_CUT_TYPE);
        }
        if (item.getCustomFishingId() != null) {
            pdc.set(Keys.CUSTOM_FISHING_ID, PersistentDataType.STRING, item.getCustomFishingId());
        } else {
            pdc.remove(Keys.CUSTOM_FISHING_ID);
        }

        if (!indexMap.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (var e : indexMap.entrySet()) {
                if (!first) {
                    sb.append(";");
                }
                sb.append(e.getKey()).append(".").append(e.getValue());
                first = false;
            }
            pdc.set(Keys.LORE_INDEX_MAP, PersistentDataType.STRING, sb.toString());
        } else {
            pdc.remove(Keys.LORE_INDEX_MAP);
        }

        if (item.getModel() != null) {
            pdc.set(Keys.MODEL, PersistentDataType.STRING, item.getModel().getId());
        }

        var cookData = item.getCookData();
        if (cookData != null && cookData.isBeingCooked() && cookData.getCurrentMethod() != null) {
            pdc.set(Keys.COOK_METHOD, PersistentDataType.STRING, cookData.getCurrentMethod().name());
            pdc.set(Keys.COOK_TIME, PersistentDataType.INTEGER, cookData.getCurrentTime());
        } else {
            pdc.remove(Keys.COOK_METHOD);
            pdc.remove(Keys.COOK_TIME);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public static List<String> buildLore(FoodItem item) {
        return buildLore(item, new HashMap<>());
    }

    static List<String> buildLore(FoodItem item, Map<String, Integer> indexMap) {
        String category = CategoryDictionary.getName(item.getCategory() != null ? item.getCategory() : "");
        String origin = item.hasTag(Tag.PROCESSED)
                ? null
                : "§7Origin: " + (item.getOrigin() != null ? item.getOrigin() : "§fNone");
        String sauceLine = null;
        if (item.hasSauce()) {
            FoodItem sauce = item.getSauce();
            String sauceName = item.hasSauceName() ? item.getSauceName() : "Sauce";
            sauceLine = "§6" + sauceName + " §8("
                    + DisplayUtils.getSauceStatString(sauce.getFinalFood(), sauce.getFinalNutrition()) + "§8)";
        }
        return assembleLore(
                category,
                origin,
                item.getQualityMin(),
                StringFormatter.formatHex("#d4ad77Nutrition §f" + item.getFinalNutrition()),
                StringFormatter.formatHex("#d4ad77Food §f" + item.getFinalFood()),
                item.getIngredients(),
                sauceLine,
                item,
                item.getTagTracks(),
                indexMap);
    }

    static List<String> assembleLore(
            String categoryLine,
            String originLine,
            int quality,
            String nutritionLine,
            String foodLine,
            List<String> ingredients,
            String sauceLine,
            FoodItem tagLabels,
            List<TagTrack> tracks,
            Map<String, Integer> indexMap) {
        List<String> lore = new ArrayList<>();
        Map<String, Integer> indexes = indexMap != null ? indexMap : new HashMap<>();
        lore.add(categoryLine);
        if (originLine != null) {
            lore.add(originLine);
        }
        lore.add(buildStars(quality, 5));
        lore.add(nutritionLine);
        indexes.put("nutrition", lore.size() - 1);
        lore.add(foodLine);
        indexes.put("food", lore.size() - 1);

        if (ingredients != null && !ingredients.isEmpty()) {
            lore.add(StringFormatter.formatHex("#dbb072Ingredients:"));
            StringBuilder line = new StringBuilder("§7");
            int max = 20;
            for (String ing : ingredients) {
                String part = ing + ", ";
                if (line.length() + part.length() > max + 4) {
                    lore.add(line.toString());
                    line = new StringBuilder("§7");
                }
                line.append(part);
            }
            if (!line.toString().trim().equals("§7-")) {
                lore.add(line.toString().replaceAll(", $", ""));
            }
        }

        if (sauceLine != null) {
            lore.add(" ");
            lore.add(sauceLine);
            indexes.put("sauce", lore.size() - 1);
        }

        lore.add(" ");
        boolean firstTag = true;
        if (tracks != null) {
            for (TagTrack t : tracks) {
                TagStep step = t.getCurrentStep();
                if (step == null) {
                    continue;
                }
                if (!shouldShowTagLore(tagLabels, t, step)) {
                    continue;
                }
                lore.add(DisplayUtils.getDisplayString(
                        TagDisplayNames.resolve(tagLabels, t, step),
                        step.getFoodMultiplier(),
                        step.getNutritionMultiplier(),
                        step.getCraftQualityPct()));
                if (firstTag) {
                    indexes.put("tags", lore.size() - 1);
                    firstTag = false;
                }
            }
        }
        return lore;
    }

    public static String qualityStars(int q) {
        return buildStars(QualityUtils.clamp(q), 5);
    }

    private static String buildStars(int q, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= max; i++)
            sb.append(i <= q ? "§6★" : "§7☆");
        return sb.toString();
    }

    public static void buildFromString(Player p, String string, ItemStack base) {
        FoodParser.Result parsed = FoodParser.parse(CookingPathHandler.stripPrefix(string));
        if (parsed == null || parsed.template == null) {
            p.sendMessage("§cInvalid item string!");
            return;
        }

        FoodItem template = parsed.template;
        boolean unique = parsed.unique;

        List<ItemStack> stacks;
        if (parsed.explicitQuality) {
            stacks = ItemBuilder.build(template, unique, base);
        } else {
            stacks = buildWithOriginQuality(p, template, unique, base);
        }
        boolean sound = true;

        for (ItemStack is : stacks) {
            ItemStack leftover = InventoryAdder.addItem(p, is);

            if (leftover == null) {
                if (sound) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    sound = false;
                }
            } else {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }
    }


    public static ItemStack buildSingleString(String string, ItemStack base) {
        FoodParser.Result parsed = FoodParser.parse(CookingPathHandler.stripPrefix(string));
        FoodItem template = parsed.template;
        if (parsed.explicitQuality) {
            return ItemBuilder.buildSingle(template, base);
        }
        return ItemBuilder.buildSingleWithQuality(template, base, OriginQualityResolver.resolve(null, template));
    }

    private static List<ItemStack> buildWithOriginQuality(Player player, FoodItem template, boolean unique,
            ItemStack base) {
        List<ItemStack> list = new ArrayList<>();
        int amount = template.getAmount();

        if (unique) {
            for (int i = 0; i < amount; i++) {
                int quality = OriginQualityResolver.resolve(player, template);
                list.add(buildSingleWithQuality(template, base, quality));
            }
            return list;
        }

        int quality = OriginQualityResolver.resolve(player, template);
        ItemStack stack = buildSingleWithQuality(template, base, quality);
        stack.setAmount(amount);
        list.add(stack);
        return list;
    }

    static boolean shouldShowTagLore(FoodItem item, TagTrack track, TagStep step) {
        if (step.getCraftQualityPct() > 0) {
            return true;
        }
        if (step.getFoodMultiplier() != 1.0 || step.getNutritionMultiplier() != 1.0) {
            return true;
        }
        String name = TagDisplayNames.resolve(item, track, step);
        return name != null && !stripHexPrefix(name).isBlank();
    }

    private static String stripHexPrefix(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.charAt(0) == '#' && name.length() > 7) {
            String hex = name.substring(1, 7);
            if (hex.matches("[0-9a-fA-F]{6}")) {
                return name.substring(7);
            }
        }
        return name;
    }
}
