package dev.lone.itemsadder.api.Events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Test stand-in for ItemsAdder's event; Cooking does not compile against ItemsAdder. */
public class ItemsAdderLoadDataEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
