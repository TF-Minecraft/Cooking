package net.tfminecraft.cooking.popcorn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;

/**
 * One popcorn machine from popcorn-machines.yml: the furniture it is, what goes in (corn and toppings) and how much,
 * what each piece of corn pops into, how long it pops and the models and room its show uses.
 */
public final class PopcornMachine {
    private final String id;
    private final String furniture;
    private final List<String> inputs;
    private final String inputName;
    private final int maxInput;
    private final int yield;
    private final String output;
    private final int maxToppings;
    private final List<Topping> toppings;
    private final String lookTrack;
    private final String kettle;
    private final String kettleLoaded;
    private final String heap;
    private final List<String[]> heapLooks = new ArrayList<>();
    private final List<Double> heapHeights;
    private final String kettleSlot;
    private final String heapSlot;
    private final List<String> toppingSlots;
    private final int popTicks;
    private final double rim;
    private final double floor;
    private final double walls;

    public PopcornMachine(String id, ConfigurationSection config) {
        this.id = id;
        this.furniture = config.getString("furniture", id).toLowerCase(Locale.ROOT);
        this.inputs = config.getStringList("input");
        this.inputName = config.getString("input-name", "corn");
        this.maxInput = Math.max(1, config.getInt("max-input", 16));
        this.yield = Math.max(1, config.getInt("yield", 4));
        this.output = config.getString("output", "");
        this.maxToppings = Math.max(0, config.getInt("max-toppings", 2));
        this.toppings = Topping.load(config.getConfigurationSection("toppings"));
        this.lookTrack = config.getString("look-track", "popcorn_look");
        this.kettle = config.getString("models.kettle", "");
        this.kettleLoaded = config.getString("models.kettle-loaded", kettle);
        this.heap = config.getString("models.heap", "");
        for (String look : config.getStringList("models.heap-looks")) {
            String[] parts = look.trim().split("\\s+", 2);
            if (parts.length == 2) heapLooks.add(parts);
        }
        List<Double> heights = config.getDoubleList("animation.heap-heights");
        this.heapHeights = heights.isEmpty() ? List.of(0.25) : heights;
        this.kettleSlot = config.getString("slots.kettle", "kettle");
        this.heapSlot = config.getString("slots.heap", "heap");
        this.toppingSlots = config.getStringList("slots.toppings");
        this.popTicks = (int) Math.max(40, config.getDouble("animation.seconds", 8) * 20);
        this.rim = config.getDouble("animation.kettle-rim", 0.45);
        this.floor = config.getDouble("animation.floor", -0.25);
        this.walls = config.getDouble("animation.walls", 0.36);
    }

    public String getId() {
        return id;
    }

    /** The InteractibleFurniture type id. */
    public String getFurniture() {
        return furniture;
    }

    /** What goes in, as players read it: corn. */
    public String getInputName() {
        return inputName;
    }

    public int getMaxInput() {
        return maxInput;
    }

    /** Pieces each input pops into. */
    public int getYield() {
        return yield;
    }

    /** The food string of a popped piece (its quality comes from the corn). */
    public String getOutput() {
        return output;
    }

    public int getMaxToppings() {
        return maxToppings;
    }

    public List<Topping> getToppings() {
        return toppings;
    }

    /** The tags.yml track whose step gives the popcorn its look (buttered, caramel). */
    public String getLookTrack() {
        return lookTrack;
    }

    /** True when this food goes in to be popped (corn). */
    public boolean takes(FoodItem food) {
        for (String path : inputs) {
            if (CookingPathHandler.matches(food, path)) return true;
        }
        return false;
    }

    /** The topping this food is, or null. */
    public Topping toppingFor(FoodItem food) {
        for (Topping topping : toppings) {
            if (topping.matches(food)) return topping;
        }
        return null;
    }

    /** The kettle's item, with kernels in it while corn is loaded. */
    public String kettleFor(boolean loaded) {
        return loaded ? kettleLoaded : kettle;
    }

    /** How many heap levels there are (models.heap {level} 1..n, animation.heap-heights). */
    public int heapLevels() {
        return heapHeights.size();
    }

    /** The heap level this much input fills, 1 to heapLevels(); 0 for none. */
    public int heapLevel(int input) {
        if (input <= 0) return 0;
        return Math.min(heapLevels(), (int) Math.ceil(input * (double) heapLevels() / maxInput));
    }

    /** The heap's item at this level, in the look of the first heap-looks tag the popcorn has. */
    public String heapFor(int level, Collection<String> tags) {
        String template = heap;
        for (String[] look : heapLooks) {
            if (tags.contains(look[0])) {
                template = look[1];
                break;
            }
        }
        return template.replace("{level}", String.valueOf(level));
    }

    /** How high the heap at this level reaches above the floor, in blocks. */
    public double heapHeight(int level) {
        if (level <= 0) return 0;
        return heapHeights.get(Math.min(level, heapLevels()) - 1);
    }

    public String getKettleSlot() {
        return kettleSlot;
    }

    public String getHeapSlot() {
        return heapSlot;
    }

    /** The slots on the machine that show the toppings put in, one each. */
    public List<String> getToppingSlots() {
        return toppingSlots;
    }

    /** How long the machine pops, in ticks. */
    public int getPopTicks() {
        return popTicks;
    }

    /** Where kernels pop out of the kettle, in blocks above the furniture's centre. */
    public double getRim() {
        return rim;
    }

    /** The cabinet floor, in blocks from the furniture's centre (negative is below it). */
    public double getFloor() {
        return floor;
    }

    /** Half the inside width of the glass, in blocks. */
    public double getWalls() {
        return walls;
    }
}
