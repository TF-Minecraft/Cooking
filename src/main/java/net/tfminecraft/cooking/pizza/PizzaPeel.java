package net.tfminecraft.cooking.pizza;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

/**
 * A pizza lying on a peel: its layers in the peel's pizza_N slots, bottom first, how far it has baked and how many of
 * its slices have been taken (kept in the peel's variables so they survive restarts and trips in and out of the oven).
 */
public final class PizzaPeel {
    static final String SLOT_PREFIX = "pizza_";
    static final String ELAPSED = "pizza.elapsed";
    static final String BAKED = "pizza.baked";
    static final String CUT = "pizza.cut";
    static final String SLICE = "pizza.slice";
    public static final int RAW = 0;
    public static final int COOKED = 1;
    public static final int BURNT = 2;
    private static final List<String> STATE_TAGS = List.of("raw", "cooked", "burnt");
    // The size each peel's layers were last stacked at.
    private static final Map<UUID, Float> stackedAt = new HashMap<>();

    private PizzaPeel() {}

    /** The peel's pizza slots, bottom first. */
    static List<String> slotIds(Furniture f) {
        List<String> ids = new ArrayList<>();
        for (String id : f.getType().getSlots().keySet()) {
            if (id.startsWith(SLOT_PREFIX)) ids.add(id);
        }
        ids.sort(Comparator.comparingInt(PizzaPeel::level));
        return ids;
    }

    private static int level(String slotId) {
        try {
            return Integer.parseInt(slotId.substring(SLOT_PREFIX.length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The layers lying on the peel, bottom first. */
    static List<PlacedSlot> layers(Furniture f) {
        List<PlacedSlot> layers = new ArrayList<>();
        for (String id : slotIds(f)) {
            PlacedSlot slot = f.getActiveSlot(id).orElse(null);
            if (slot != null && slot.getCurrentItem() != null) layers.add(slot);
        }
        return layers;
    }

    static List<ItemStack> items(List<PlacedSlot> layers) {
        List<ItemStack> items = new ArrayList<>();
        for (PlacedSlot slot : layers) items.add(slot.getCurrentItem());
        return items;
    }

    /** RAW, COOKED or BURNT. */
    public static int baked(Furniture f) {
        return number(f, BAKED);
    }

    /** Seconds the pizza has spent in a hot oven. */
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

    /** Forgets the pizza: its bake, its cuts and its slice. */
    static void clearBake(Furniture f) {
        f.getVariables().remove(ELAPSED);
        f.getVariables().remove(BAKED);
        f.getVariables().remove(CUT);
        f.getVariables().remove(SLICE);
    }

    /** Slices still on the peel. */
    static int left(Furniture f) {
        return Math.max(0, PizzaConfig.slices() - number(f, CUT));
    }

    static void setLeft(Furniture f, int left) {
        f.getVariables().put(CUT, PizzaConfig.slices() - left);
    }

    /** The slice cut from this pizza at its bake, kept so every slice is the same; null before the first cut. */
    static ItemStack slice(Furniture f) {
        return f.getVariables().get(SLICE) instanceof String encoded ? PotReference.decodeStack(encoded) : null;
    }

    static void keepSlice(Furniture f, ItemStack slice) {
        String encoded = PotReference.encodeStack(slice);
        if (encoded != null) f.getVariables().put(SLICE, encoded);
    }

    /** Baking further changes the slices still to be cut. */
    static void forgetSlice(Furniture f) {
        f.getVariables().remove(SLICE);
    }

    /**
     * The layer's look on the peel at the pizza's bake (raw, cooked or burnt) and with its slices taken: a model with
     * _left3 for three slices left, falling back to the whole one (pizzas.yml models).
     */
    static ItemStack shown(Furniture f, ItemStack one, FoodItem food, SandwichLayer layer) {
        int left = left(f);
        String cut = left < PizzaConfig.slices() ? "_left" + left : "";
        ItemStack shown = layer.model(food, one, List.of(STATE_TAGS.get(baked(f))), cut);
        return shown == null ? one : shown;
    }

    /** Shows every layer as it looks at the pizza's bake and cuts. */
    static void remodel(Furniture f) {
        for (PlacedSlot slot : layers(f)) {
            ItemStack item = slot.getCurrentItem();
            FoodItem food = FoodItem.fromItem(item);
            SandwichLayer layer = PizzaConfig.layerFor(food);
            if (layer != null) slot.forceModel(shown(f, item, food, layer));
        }
    }

    /** How big the peel is drawn: 1 on its own, smaller in an oven's cavity. */
    static float scale(Furniture f) {
        return Bukkit.getEntity(f.getEntityId()) instanceof ItemDisplay display
                ? display.getTransformation().getScale().y() : 1f;
    }

    /**
     * Lifts each layer onto the one below it; the layer models are drawn lying on the peel. The lift is a distance in
     * the world, so it shrinks with the peel (InteractibleFurniture scales the layers' models, not their lift).
     */
    static void stack(Furniture f) {
        float scale = scale(f);
        stackedAt.put(f.getEntityId(), scale);
        double height = 0;
        for (PlacedSlot slot : layers(f)) {
            DisplayData data = new DisplayData();
            data.setyPos((float) (height / 16 * scale));
            slot.applyDisplayData(data);
            SandwichLayer layer = PizzaConfig.layerFor(FoodItem.fromItem(slot.getCurrentItem()));
            height += layer == null ? 0 : layer.getThickness();
        }
    }

    /** Stacks the layers again when the peel is drawn at another size than they were stacked at (in or out of an oven). */
    static void restackIfResized(Furniture f) {
        Float at = stackedAt.get(f.getEntityId());
        if (at == null || at != scale(f)) stack(f);
    }
}
