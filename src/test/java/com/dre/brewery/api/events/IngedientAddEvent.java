package com.dre.brewery.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Test-only contract for BreweryX 3.7.0's optional ingredient event. */
public class IngedientAddEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private ItemStack ingredient;
    private boolean cancelled;
    public boolean failRead;
    public boolean failWrite;

    public IngedientAddEvent(ItemStack ingredient) { this.ingredient = ingredient; }
    public ItemStack getIngredient() {
        if (failRead) throw new IllegalStateException("read failed");
        return ingredient;
    }
    public void setIngredient(ItemStack ingredient) {
        if (failWrite) throw new IllegalStateException("write failed");
        this.ingredient = ingredient;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
