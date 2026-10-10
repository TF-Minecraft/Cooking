package net.tfminecraft.cooking.mmoitems;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.PluginManager;

/**
 * Keeps MMOItems classes out of Cooking unless MMOItems is installed. Register from onLoad:
 * MMOItems reads its crafting stations when it enables, and the ingredient type must exist by then.
 */
public final class MMOItemsSupport {
    private MMOItemsSupport() {}

    public static boolean registerIfPresent(PluginManager plugins, Logger logger) {
        return run(plugins, logger, () -> MMOItemsIngredients.register(logger), "register");
    }

    /** Call once every plugin has enabled (the first server tick). */
    public static boolean claimFirstIfPresent(PluginManager plugins, Logger logger) {
        return run(plugins, logger, () -> MMOItemsIngredients.claimFirst(logger), "order");
    }

    private static boolean run(PluginManager plugins, Logger logger, Runnable step, String what) {
        if (plugins.getPlugin("MMOItems") == null) {
            return false;
        }
        try {
            step.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING, "Could not " + what + " the MMOItems cooking ingredient", e);
            return false;
        }
    }
}
