package net.tfminecraft.cooking.pizza;

import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/**
 * Bakes the pizza on a peel in an oven, one second at a time while the oven is hot: it cooks after pizzas.yml's
 * cook-seconds and burns at burn-seconds, and every layer then shows its cooked or burnt look.
 */
public final class PizzaBaking {

    private PizzaBaking() {}

    public static void tick(Furniture oven, Furniture peel) {
        int baked = PizzaPeel.baked(peel);
        if (baked >= PizzaPeel.BURNT || PizzaPeel.layers(peel).isEmpty() || !HeatSources.consumerHasHeat(oven)) return;
        int elapsed = PizzaPeel.elapsed(peel) + 1;
        int next = baked;
        if (elapsed >= PizzaConfig.burnSeconds()) next = PizzaPeel.BURNT;
        else if (elapsed >= PizzaConfig.cookSeconds()) next = PizzaPeel.COOKED;
        PizzaPeel.setBake(peel, elapsed, next);
        if (next != baked) {
            // Slices still to be cut take the new bake.
            PizzaPeel.forgetSlice(peel);
            PizzaPeel.remodel(peel);
        }
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(peel);
    }
}
