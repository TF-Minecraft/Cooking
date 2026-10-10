package net.tfminecraft.cooking.mmoitems;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.PluginManager;

/**
 * Keeps MMOItems classes out of Cooking unless MMOItems is installed. Call from onLoad: MMOItems
 * reads its crafting stations when it enables, and the ingredient type must exist by then.
 */
public final class MMOItemsSupport {
    private MMOItemsSupport() {}

    public static boolean registerIfPresent(PluginManager plugins, Logger logger) {
        if (plugins.getPlugin("MMOItems") == null) {
            return false;
        }
        try {
            MMOItemsIngredients.register(logger);
            return true;
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING, "Could not register the MMOItems cooking ingredient", e);
            return false;
        }
    }
}
