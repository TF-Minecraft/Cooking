package net.tfminecraft.cooking.milling;

/**
 * One milling-recipes.yml entry. A stone can hold several (wheat into flour, sugar cane into sugar); the first whose
 * input matches the held stack is loaded, and its id is saved on the stone.
 *
 * <pre>
 * sugar:
 *   furniture: milling_stone
 *   input-count: 8
 *   vanilla-fallback: v.sugar_cane   # a vanilla item it takes (quality rolled as on pickup)
 *   input-name: sugar cane           # "You need 8 sugar cane to fill the mill." (default wheat)
 *   input-display: v.sugar_cane      # what the loaded stone shows (default wheat)
 *   refund: v.sugar_cane             # an item given back when a loaded stone is broken (default the input-food, or wheat)
 *   output-food: sweetener_1         # types.yml type it grinds into
 *   category: sweetener              # the output's category (default grain)
 *   origin: Sugar                    # the output's origin (default Wheat)
 *   output-display: v.sugar          # the pile shown when it's ground (default the output's mixing model, or flour)
 *   output-amount: 8                 # how many it grinds into (default 1)
 * </pre>
 */
public final class MillingRecipe {
    static final String DEFAULT_ORIGIN = "Wheat";
    static final String DEFAULT_CATEGORY = "grain";
    static final String DEFAULT_INPUT_NAME = "wheat";
    static final String DEFAULT_REFUND_FOOD = "wheat";

    private final String id;
    private final String furnitureId;
    private final int inputCount;
    private final String inputFood;
    private final String inputMatcher;
    private final String vanillaFallback;
    private final String outputFood;
    private final int revolutions;
    private final int durationTicks;
    private final String origin;
    private final String category;
    private final String inputName;
    private final String inputDisplay;
    private final String outputDisplay;
    private final String refund;
    private final int outputAmount;

    public MillingRecipe(String id, String furnitureId, int inputCount, String inputFood, String inputMatcher,
            String vanillaFallback, String outputFood, int revolutions, int durationTicks) {
        this(id, furnitureId, inputCount, inputFood, inputMatcher, vanillaFallback, outputFood, revolutions,
                durationTicks, null, null, null, null, null, null, 1);
    }

    public MillingRecipe(String id, String furnitureId, int inputCount, String inputFood, String inputMatcher,
            String vanillaFallback, String outputFood, int revolutions, int durationTicks, String origin,
            String category, String inputName, String inputDisplay, String outputDisplay, String refund,
            int outputAmount) {
        this.id = id;
        this.furnitureId = furnitureId;
        this.inputCount = inputCount;
        this.inputFood = inputFood;
        this.inputMatcher = inputMatcher;
        this.vanillaFallback = vanillaFallback;
        this.outputFood = outputFood;
        this.revolutions = revolutions;
        this.durationTicks = durationTicks;
        this.origin = orDefault(origin, DEFAULT_ORIGIN);
        this.category = orDefault(category, DEFAULT_CATEGORY);
        this.inputName = orDefault(inputName, DEFAULT_INPUT_NAME);
        this.inputDisplay = blankToNull(inputDisplay);
        this.outputDisplay = blankToNull(outputDisplay);
        this.refund = blankToNull(refund);
        this.outputAmount = Math.max(1, outputAmount);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getId() {
        return id;
    }

    public String getFurnitureId() {
        return furnitureId;
    }

    public int getInputCount() {
        return inputCount;
    }

    public String getInputFood() {
        return inputFood;
    }

    public String getInputMatcher() {
        return inputMatcher;
    }

    public String getVanillaFallback() {
        return vanillaFallback;
    }

    public String getOutputFood() {
        return outputFood;
    }

    public int getRevolutions() {
        return revolutions;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    /** The output's (and a food refund's) origin: Wheat, Sugar. */
    public String getOrigin() {
        return origin;
    }

    /** The output's category: grain, sweetener. */
    public String getCategory() {
        return category;
    }

    /** What the input is called in the "You need 8 ..." message. */
    public String getInputName() {
        return inputName;
    }

    /** The item path the loaded stone shows, or null for wheat. */
    public String getInputDisplay() {
        return inputDisplay;
    }

    /** The item path the ground pile shows, or null for the output's mixing model. */
    public String getOutputDisplay() {
        return outputDisplay;
    }

    /** The item path given back for a loaded stone that's broken, or null to give back the input as food. */
    public String getRefund() {
        return refund;
    }

    /** How many the batch grinds into. */
    public int getOutputAmount() {
        return outputAmount;
    }

    /** The types.yml food a loaded stone gives back when it has no refund item: its input-food, or wheat. */
    public String getRefundFood() {
        return inputFood == null || inputFood.isBlank() ? DEFAULT_REFUND_FOOD : inputFood;
    }
}
