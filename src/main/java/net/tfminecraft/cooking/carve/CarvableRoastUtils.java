package net.tfminecraft.cooking.carve;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.utils.Keys;

public final class CarvableRoastUtils {

    private CarvableRoastUtils() {}

    public static void readCarveState(FoodItem item, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        String seq = pdc.get(Keys.CARVE_SEQUENCE, PersistentDataType.STRING);
        if (seq == null) return;
        Integer next = pdc.get(Keys.CARVE_NEXT_INDEX, PersistentDataType.INTEGER);
        Integer remaining = pdc.get(Keys.CARVE_REMAINING, PersistentDataType.INTEGER);
        item.setCarveState(seq, next != null ? next : 0, remaining != null ? remaining : 0);
    }

    public static void writeCarveState(ItemStack stack, FoodItem item) {
        if (stack == null || !stack.hasItemMeta() || !item.hasCarveState()) return;
        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.CARVE_SEQUENCE, PersistentDataType.STRING, item.getCarveSequencePdc());
        pdc.set(Keys.CARVE_NEXT_INDEX, PersistentDataType.INTEGER, item.getCarveNextIndex());
        pdc.set(Keys.CARVE_REMAINING, PersistentDataType.INTEGER, item.getCarveRemaining());
        stack.setItemMeta(meta);
    }

    public static void initCarveState(ItemStack stack, FoodItem item) {
        String seqId = item.getCarveSequenceId();
        if (seqId == null) return;
        CarveSequence seq = CarveSequenceLoader.get(seqId);
        if (seq == null) return;
        item.setCarveState(seqId, 0, seq.getStartRemaining());
        writeCarveState(stack, item);
    }

    public static boolean isCarvable(FoodItem item) {
        return item != null && item.hasCarveState() && item.getCarveRemaining() > 0
                && item.getCarveNextIndex() < getSequence(item).getCuts().size();
    }

    public static boolean isCarvable(ItemStack stack) {
        FoodItem fi = FoodItem.fromItem(stack);
        if (fi == null) return false;
        readCarveState(fi, stack);
        return isCarvable(fi);
    }

    public static CarveSequence getSequence(FoodItem item) {
        return CarveSequenceLoader.get(item.getCarveSequencePdc());
    }

    public static double getRemainingFood(FoodItem item) {
        return remainingFood(item, getSequence(item));
    }

    /** Nutrition is the batch level. Cuts left do not shrink it. */
    public static double getRemainingNutrition(FoodItem item) {
        return item.getBaseNutrition();
    }

    public static double remainingFood(FoodItem item, CarveSequence seq) {
        if (seq == null) return item.getBaseFood();
        if (item.hasBaseOverride()) {
            int edible = Math.max(1, countFoodCuts(seq));
            return item.getBaseFood() * countFoodCutsFrom(seq, item.getCarveNextIndex()) / (double) edible;
        }
        return seq.sumRemainingFood(item.getCarveNextIndex());
    }

    public static double portionFood(double totalFood, int edibleCuts) {
        return totalFood / Math.max(1, edibleCuts);
    }

    public static int countFoodCuts(CarveSequence seq) {
        return countFoodCutsFrom(seq, 0);
    }

    public static int countFoodCutsFrom(CarveSequence seq, int fromIndex) {
        if (seq == null) {
            return 0;
        }
        int start = Math.max(0, fromIndex);
        int count = 0;
        List<CarveCut> cuts = seq.getCuts();
        for (int i = start; i < cuts.size(); i++) {
            if (cuts.get(i).isFoodCut()) {
                count++;
            }
        }
        return count;
    }

    /** Visual stage for IA models: raw_1 = whole bird, raw_6 = mostly carved. */
    public static int getVisualCarveStage(FoodItem item) {
        return item.getCarveNextIndex() + 1;
    }

    public static ModelData getStageModelData(FoodItem item) {
        if (!item.hasCarveState() || item.getCarveRemaining() <= 0) {
            FoodModel model = item.getModel();
            ModelData fallback = model != null ? model.getModel(item) : null;
            return fallback != null ? fallback : item.getModel().getModel(item);
        }
        String cookTag = resolveCookTag(item);
        ModelData staged = item.getModel().getModelByStageAndTag(getVisualCarveStage(item), cookTag);
        if (staged != null) return staged;
        FoodModel model = item.getModel();
        return model != null ? model.getModel(item) : null;
    }

    public static String resolveCookTag(FoodItem item) {
        TagTrack freshness = item.getTagTrack("freshness");
        if (freshness != null && freshness.getCurrentStep() != null
                && "rotten".equals(freshness.getCurrentStep().getId())) {
            return "rotten";
        }
        TagTrack cooked = item.getTagTrack("cooked");
        if (cooked != null && cooked.getCurrentStep() != null) {
            return cooked.getCurrentStep().getId();
        }
        return "raw";
    }

    public static void advanceAfterCarve(FoodItem item) {
        item.setCarveNextIndex(item.getCarveNextIndex() + 1);
        item.setCarveRemaining(Math.max(0, item.getCarveRemaining() - 1));
    }

    public static void copyInheritedTracks(FoodItem parent, FoodItem child) {
        for (String trackId : new String[] { "cooked", "freshness", "warmth" }) {
            TagTrack pt = parent.getTagTrack(trackId);
            if (pt != null) {
                TagTrack copy = new TagTrack(pt);
                child.addOrModifyTrack(copy);
            }
        }
        if (parent.getOrigin() != null) {
            child.setOrigin(parent.getOrigin());
        }
        child.setLineage(parent.getLineage());
        child.setQualityRange(parent.getQualityMin(), parent.getQualityMax());
    }
}
