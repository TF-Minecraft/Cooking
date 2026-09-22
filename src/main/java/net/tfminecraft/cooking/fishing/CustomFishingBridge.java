package net.tfminecraft.cooking.fishing;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.cooking.Cooking;

public final class CustomFishingBridge implements Listener {
    private static boolean registered;

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin() != null && "CustomFishing".equals(event.getPlugin().getName()) && Cooking.plugin != null) {
            tryRegister(Cooking.plugin);
        }
    }

    public static void tryRegister(JavaPlugin plugin) {
        if (registered || plugin == null) {
            return;
        }
        Plugin customFishing = Bukkit.getPluginManager().getPlugin("CustomFishing");
        if (customFishing == null || !customFishing.isEnabled()) {
            return;
        }
        try {
            Class<?> listener = Class.forName(
                    "net.tfminecraft.cooking.fishing.CustomFishingCatchListener",
                    true,
                    CustomFishingBridge.class.getClassLoader());
            listener.getMethod("register", JavaPlugin.class).invoke(null, plugin);
            registered = true;
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessException
                | InvocationTargetException | NoSuchMethodException exception) {
            plugin.getLogger().warning("CustomFishing is enabled but the catch bridge failed to register: "
                    + exception.getMessage());
        }
    }
}
