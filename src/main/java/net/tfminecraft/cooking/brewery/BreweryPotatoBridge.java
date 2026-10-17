package net.tfminecraft.cooking.brewery;

import java.lang.reflect.Method;

import org.bukkit.Material;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.cooking.item.FoodItem;

/** Translates whole Cooking potatoes only inside Brewery's cauldron ingredient event. */
public final class BreweryPotatoBridge implements Listener {
    private final JavaPlugin plugin;
    private boolean registered;

    public BreweryPotatoBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("BreweryX".equals(event.getPlugin().getName())) {
            registerIfPresent();
        }
    }

    public void registerIfPresent() {
        Plugin brewery = plugin.getServer().getPluginManager().getPlugin("BreweryX");
        if (registered || brewery == null || !brewery.isEnabled()) return;

        try {
            // Keep BreweryX optional, without bundling or linking its implementation into Cooking.
            Class<? extends Event> eventType = Class.forName(
                    "com.dre.brewery.api.events.IngedientAddEvent", true,
                    brewery.getClass().getClassLoader()).asSubclass(Event.class);
            Method getIngredient = eventType.getMethod("getIngredient");
            Method setIngredient = eventType.getMethod("setIngredient", ItemStack.class);
            plugin.getServer().getPluginManager().registerEvent(eventType, this, EventPriority.HIGH,
                    (listener, event) -> translate(event, getIngredient, setIngredient), plugin, true);
            registered = true;
            plugin.getLogger().info("BreweryX whole-potato ingredient bridge enabled.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("BreweryX whole-potato bridge could not register: " + exception);
        }
    }

    private static void translate(Event event, Method getIngredient, Method setIngredient) throws EventException {
        try {
            FoodItem food = FoodItem.fromItem((ItemStack) getIngredient.invoke(event));
            if (food == null || !"vegetable_1".equals(food.getId())
                    || !"Potato".equalsIgnoreCase(food.getOrigin())) return;

            // Brewery consumes the original hand item; only its internal ingredient copy changes.
            // Freshness is deliberately unrestricted, so rotten whole potatoes also make vodka.
            setIngredient.invoke(event, new ItemStack(Material.POTATO));
        } catch (ReflectiveOperationException exception) {
            // Never consume a potato as an apple if the optional API changes or fails.
            ((Cancellable) event).setCancelled(true);
            throw new EventException(exception);
        }
    }
}
