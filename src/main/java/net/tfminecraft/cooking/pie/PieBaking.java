package net.tfminecraft.cooking.pie;

import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/**
 * Bakes the pie in a tin in an oven, one second at a time while the oven is hot: it cooks after pies.yml's
 * cook-seconds and burns at burn-seconds, and its crust then shows cooked or burnt. Only a filled pie bakes.
 */
public final class PieBaking {

    private PieBaking() {}

    public static void tick(Furniture oven, Furniture tin) {
        int baked = PieTin.baked(tin);
        if (baked >= PieTin.BURNT || !PieTin.ready(tin) || !HeatSources.consumerHasHeat(oven)) return;
        int elapsed = PieTin.elapsed(tin) + 1;
        int next = baked;
        if (elapsed >= PieConfig.burnSeconds()) next = PieTin.BURNT;
        else if (elapsed >= PieConfig.cookSeconds()) next = PieTin.COOKED;
        PieTin.setBake(tin, elapsed, next);
        if (next != baked) PieTin.remodel(tin);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(tin);
    }
}
