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
        return budgetedFood(seq, item.getCarveNextIndex(), budgetedFoodCuts(item, seq));
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

    /**
     * Meat portions a slaughtered animal yields, then the closing bone when the sequence has one.
     * The roast always starts at the first cut. Genetics only changes how many meat cuts follow
     * before that bone, never below the sequence floor.
     */
    public record RoastPortion(int nextIndex, int remaining) {}

    public static RoastPortion portion(CarveSequence seq, int requestedMeatCuts) {
        int food = countFoodCuts(seq);
        int min = food == 0 ? 0 : Math.min(food, Math.max(1, seq.getMinFoodCuts()));
        int meat = Math.max(min, Math.min(food, requestedMeatCuts));
        int bone = trailingItemIndex(seq) >= 0 ? 1 : 0;
        return new RoastPortion(0, meat + bone);
    }

    /** Index of a non-food cut that closes the sequence, or -1 when every cut is meat. */
    public static int trailingItemIndex(CarveSequence seq) {
        if (seq == null) return -1;
        List<CarveCut> cuts = seq.getCuts();
        for (int i = cuts.size() - 1; i >= 0; i--) {
            CarveCut cut = cuts.get(i);
            if (cut.isItemCut()) return i;
            if (cut.isFoodCut()) return -1;
        }
        return -1;
    }

    /** Visual stage for IA models: stage 1 is the whole roast, the last stage is bone. */
    public static int getVisualCarveStage(FoodItem item) {
        return visualCarveStage(item, getSequence(item));
    }

    public static int visualCarveStage(FoodItem item, CarveSequence seq) {
        if (item == null) return 1;
        if (seq == null || seq.getCuts().isEmpty()) {
            return Math.max(1, item.getCarveNextIndex() + 1);
        }
        int maxStage = Math.max(seq.getStartRemaining(), seq.getCuts().size());
        maxStage = Math.max(1, maxStage);
        CarveCut next = seq.getCut(item.getCarveNextIndex());
        if (next != null && !next.isFoodCut()) {
            return maxStage;
        }
        boolean bone = trailingItemIndex(seq) >= 0;
        int meatLeft = bone
                ? Math.max(0, item.getCarveRemaining() - 1)
                : Math.max(0, item.getCarveRemaining());
        int meatTaken = Math.max(0, item.getCarveNextIndex());
        int meatTotal = meatTaken + meatLeft;
        if (meatTotal <= 0 || meatTaken <= 0) {
            return meatTaken <= 0 ? 1 : maxStage;
        }
        int span = bone ? meatTotal : Math.max(1, meatTotal - 1);
        int stage = 1 + (int) Math.round(meatTaken * (double) (maxStage - 1) / span);
        if (stage < 1) return 1;
        return Math.min(maxStage, stage);
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
        advanceAfterCarve(item, getSequence(item));
    }

    public static void advanceAfterCarve(FoodItem item, CarveSequence seq) {
        int next = item.getCarveNextIndex() + 1;
        int remaining = Math.max(0, item.getCarveRemaining() - 1);
        if (remaining == 1 && seq != null) {
            int bone = trailingItemIndex(seq);
            CarveCut upcoming = seq.getCut(next);
            if (bone >= 0 && (upcoming == null || upcoming.isFoodCut())) {
                next = bone;
            }
        }
        item.setCarveNextIndex(next);
        item.setCarveRemaining(remaining);
    }

    private static int budgetedFoodCuts(FoodItem item, CarveSequence seq) {
        CarveCut next = seq.getCut(item.getCarveNextIndex());
        if (next != null && !next.isFoodCut()) {
            return 0;
        }
        if (trailingItemIndex(seq) >= 0) {
            return Math.max(0, item.getCarveRemaining() - 1);
        }
        return Math.max(0, item.getCarveRemaining());
    }

    private static double budgetedFood(CarveSequence seq, int fromIndex, int foodCuts) {
        if (seq == null || foodCuts <= 0) return 0;
        double total = 0;
        int counted = 0;
        List<CarveCut> cuts = seq.getCuts();
        for (int i = Math.max(0, fromIndex); i < cuts.size() && counted < foodCuts; i++) {
            CarveCut cut = cuts.get(i);
            if (!cut.isFoodCut()) continue;
            total += cut.getFood();
            counted++;
        }
        return total;
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
