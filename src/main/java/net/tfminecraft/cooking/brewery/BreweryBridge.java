package net.tfminecraft.cooking.brewery;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads the Brewery-linked classes only while the optional plugin is available. */
public final class BreweryBridge implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private Runnable cleanup;

    public BreweryBridge(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("BreweryX".equals(event.getPlugin().getName())) registerIfPresent();
    }

    public void registerIfPresent() {
        Plugin brewery = plugin.getServer().getPluginManager().getPlugin("BreweryX");
        if (cleanup != null || brewery == null || !brewery.isEnabled()) return;
        try {
            Class<?> listener = Class.forName("net.tfminecraft.cooking.brewery.BreweryIngredientListener",
                    true, BreweryBridge.class.getClassLoader());
            cleanup = (Runnable) listener.getMethod("register", JavaPlugin.class).invoke(null, plugin);
            plugin.getLogger().info("BreweryX Cooking ingredient resolver enabled.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("BreweryX Cooking ingredient resolver could not register: " + exception);
        }
    }

    @Override public void close() {
        if (cleanup == null) return;
        cleanup.run();
        cleanup = null;
    }
}
