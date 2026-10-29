package net.tfminecraft.cooking.utils;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Turns on {@link ItemRef}'s ItemsAdder templates and clears them whenever ItemsAdder loads its items
 * (startup and /iareload). Without ItemsAdder, or without that event, templates stay off.
 * Cooking does not compile against ItemsAdder, so the event class is looked up from ItemsAdder's own loader.
 */
public final class ItemsAdderReloadHook implements Listener {
    static final String LOAD_EVENT = "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent";

    private ItemsAdderReloadHook() {}

    /** Returns true when ItemsAdder is installed and the hook was registered. */
    public static boolean registerIfPresent(Plugin plugin) {
        return registerIfPresent(plugin, LOAD_EVENT);
    }

    static boolean registerIfPresent(Plugin plugin, String eventClass) {
        Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null) return false;
        try {
            Class<? extends Event> loadEvent =
                    Class.forName(eventClass, false, itemsAdder.getClass().getClassLoader()).asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(loadEvent, new ItemsAdderReloadHook(),
                    EventPriority.MONITOR, (listener, event) -> {
                        if (loadEvent.isInstance(event)) ItemRef.clearCache();
                    }, plugin);
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            // Without the reload signal a kept template could outlive /iareload, so keep none.
            plugin.getLogger().warning("Cannot listen for " + eventClass + "; Cooking builds ItemsAdder items"
                    + " fresh every time instead of keeping templates (" + e + ")");
            return false;
        }
        ItemRef.setCaching(true);
        return true;
    }
}
