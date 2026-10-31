package net.tfminecraft.cooking.popcorn;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/**
 * What a popcorn machine holds, kept in its furniture variables so it survives restarts: its stage, and the corn and
 * toppings put in as food strings (each with its amount, stars and tags), so they come back as they went in.
 */
public final class PopcornMachineState {
    static final String VAR_STAGE = "popcorn.stage";
    static final String VAR_INPUT = "popcorn.input";
    static final String VAR_TOPPINGS = "popcorn.toppings";
    private static final String SEPARATOR = "|";

    /** Empty, being filled, popping, or holding popcorn ready to take. */
    public enum Stage { EMPTY, LOADING, RUNNING, READY }

    private PopcornMachineState() {}

    public static Stage stage(Furniture furniture) {
        Object value = furniture.getVariables().get(VAR_STAGE);
        if (value instanceof String name) {
            for (Stage stage : Stage.values()) {
                if (stage.name().equals(name)) return stage;
            }
        }
        return Stage.EMPTY;
    }

    public static void setStage(Furniture furniture, Stage stage) {
        furniture.getVariables().put(VAR_STAGE, stage.name());
    }

    /** The corn put in, as food strings. */
    public static List<String> input(Furniture furniture) {
        return read(furniture, VAR_INPUT);
    }

    /**
     * Puts one piece in: onto the last stack put in when it's the same food (keeping that stack's older data), so the
     * machine keeps a stack per kind of corn however many clicks filled it; else as a new stack.
     */
    public static void addInput(Furniture furniture, FoodItem piece) {
        List<String> list = read(furniture, VAR_INPUT);
        FoodItem last = list.isEmpty() ? null : parse(list.get(list.size() - 1));
        if (last != null && InventoryAdder.equalsFood(last, piece)) {
            list.set(list.size() - 1, FoodParser.toString(last, last.getAmount() + 1));
        } else {
            list.add(FoodParser.toString(piece, 1));
        }
        furniture.getVariables().put(VAR_INPUT, String.join(SEPARATOR, list));
    }

    /** The toppings put in, as food strings. */
    public static List<String> toppings(Furniture furniture) {
        return read(furniture, VAR_TOPPINGS);
    }

    public static void addTopping(Furniture furniture, String food) {
        add(furniture, VAR_TOPPINGS, food);
    }

    /** Empties the machine. */
    public static void clear(Furniture furniture) {
        furniture.getVariables().remove(VAR_STAGE);
        furniture.getVariables().remove(VAR_INPUT);
        furniture.getVariables().remove(VAR_TOPPINGS);
    }

    /** The foods these strings make, each with its amount; strings that no longer parse are skipped. */
    public static List<FoodItem> foods(List<String> strings) {
        List<FoodItem> foods = new ArrayList<>();
        for (String string : strings) {
            FoodItem food = parse(string);
            if (food != null) foods.add(food);
        }
        return foods;
    }

    private static FoodItem parse(String string) {
        FoodParser.Result parsed = FoodParser.parse(string);
        return parsed == null ? null : parsed.template;
    }

    /** How many pieces these foods are. */
    public static int count(List<FoodItem> foods) {
        int total = 0;
        for (FoodItem food : foods) total += food.getAmount();
        return total;
    }

    private static List<String> read(Furniture furniture, String key) {
        List<String> out = new ArrayList<>();
        if (furniture.getVariables().get(key) instanceof String joined && !joined.isEmpty()) {
            for (String part : joined.split("\\" + SEPARATOR)) {
                if (!part.isEmpty()) out.add(part);
            }
        }
        return out;
    }

    private static void add(Furniture furniture, String key, String food) {
        List<String> list = read(furniture, key);
        list.add(food);
        furniture.getVariables().put(key, String.join(SEPARATOR, list));
    }
}
