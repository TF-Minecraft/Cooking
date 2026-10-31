package net.tfminecraft.cooking.pie;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/**
 * A pie in its tin: the dough lining it in the tin's pie slot (which shows the whole pie), the fillings under its top
 * crust, unseen, and how far it has baked. The fillings and the bake are kept in the tin's variables, so they survive
 * restarts and trips in and out of the oven.
 */
public final class PieTin {
    static final String FILLINGS = "pie.fillings";
    static final String ELAPSED = "pie.elapsed";
    static final String BAKED = "pie.baked";
    public static final int RAW = 0;
    public static final int COOKED = 1;
    public static final int BURNT = 2;
    /** Counts as one of the dough's tags while fillings lie under its top crust. */
    static final String FILLED = "filled";
    private static final List<String> STATE_TAGS = List.of("raw", "cooked", "burnt");
    private static final String SEPARATOR = ",";

    private PieTin() {}

    /** The tin's pie slot while dough lies in it, or null. */
    static PlacedSlot crust(Furniture f) {
        PlacedSlot slot = f.getActiveSlot(PieConfig.slot()).orElse(null);
        return slot != null && slot.getCurrentItem() != null && !slot.getCurrentItem().getType().isAir() ? slot : null;
    }

    /** The fillings under the crust, first in first. */
    static List<ItemStack> fillings(Furniture f) {
        List<ItemStack> fillings = new ArrayList<>();
        if (!(f.getVariables().get(FILLINGS) instanceof String encoded)) return fillings;
        for (String part : encoded.split(SEPARATOR)) {
            ItemStack stack = PotReference.decodeStack(part);
            if (stack != null) fillings.add(stack);
        }
        return fillings;
    }

    static void setFillings(Furniture f, List<ItemStack> fillings) {
        if (fillings.isEmpty()) {
            f.getVariables().remove(FILLINGS);
            return;
        }
        List<String> parts = new ArrayList<>();
        for (ItemStack stack : fillings) parts.add(PotReference.encodeStack(stack));
        f.getVariables().put(FILLINGS, String.join(SEPARATOR, parts));
    }

    /** RAW, COOKED or BURNT. */
    public static int baked(Furniture f) {
        return Math.max(RAW, Math.min(BURNT, number(f, BAKED)));
    }

    /** Seconds the pie has spent in a hot oven. */
    static int elapsed(Furniture f) {
        return number(f, ELAPSED);
    }

    private static int number(Furniture f, String key) {
        return f.getVariables().get(key) instanceof Number n ? n.intValue() : 0;
    }

    static void setBake(Furniture f, int elapsed, int baked) {
        f.getVariables().put(ELAPSED, elapsed);
        f.getVariables().put(BAKED, baked);
    }

    /** Forgets the pie: its fillings and its bake. */
    static void clear(Furniture f) {
        f.getVariables().remove(FILLINGS);
        f.getVariables().remove(ELAPSED);
        f.getVariables().remove(BAKED);
    }

    /** Lined with dough and filled enough to bake. */
    public static boolean ready(Furniture f) {
        return crust(f) != null && fillings(f).size() >= PieConfig.minFillings();
    }

    /**
     * Why this tin can't go in an oven, or null when it can: an empty tin can, and so can a pie with its fillings;
     * dough alone can't.
     */
    public static String ovenRefusal(Furniture f) {
        if (crust(f) == null || ready(f)) return null;
        return "§cFill the pie before baking it.";
    }

    /**
     * Shows the dough as the pie looks at its bake (pies.yml crust models): raw, cooked or burnt counts as one of its
     * tags, and filled too while fillings lie under it. Nothing changes when no model exists.
     */
    static void remodel(Furniture f) {
        PlacedSlot slot = crust(f);
        if (slot == null) return;
        ItemStack item = slot.getCurrentItem();
        FoodItem food = FoodItem.fromItem(item);
        SandwichLayer crust = PieConfig.crustFor(food);
        if (crust == null) return;
        List<String> tags = new ArrayList<>(List.of(STATE_TAGS.get(baked(f))));
        if (!fillings(f).isEmpty()) tags.add(FILLED);
        ItemStack shown = crust.model(food, item, tags);
        if (shown != null) slot.forceModel(shown);
    }
}
