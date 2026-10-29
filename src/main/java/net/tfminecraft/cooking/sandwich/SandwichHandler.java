package net.tfminecraft.cooking.sandwich;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.StationChunks;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

/**
 * Builds sandwiches on a board (sandwiches.yml). A slice of bread on an empty board starts one, fillings go on top and
 * another slice closes it. Right-clicking a closed sandwich picks it up; an empty hand on an open one takes back its
 * top layer. Each layer lies in its own sandwich_N slot of the furniture, stacked by the layers' thickness.
 */
public final class SandwichHandler implements Listener {
    static final String SLOT_PREFIX = "sandwich_";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Furniture f = event.getFurniture();
        if (!SandwichConfig.isSandwichFurniture(f)) return;
        List<String> slots = slotIds(f);
        if (slots.isEmpty()) return;
        List<PlacedSlot> layers = layers(f, slots);
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir();
        FoodItem food = emptyHand ? null : FoodItem.fromItem(hand);
        SandwichLayer layer = SandwichConfig.layerFor(food);

        if (layers.isEmpty()) {
            // Without a sandwich the board works as before; only a slice of bread starts one.
            if (layer == null || !layer.isBread()) return;
            event.setCancelled(true);
            if (!f.getActiveSlots().isEmpty()) {
                p.sendMessage("§7Clear the board before you start a sandwich on it.");
                return;
            }
            place(p, f, slots.get(0), hand, food, layer);
            return;
        }

        event.setCancelled(true);
        // A closed sandwich is done: any right-click picks it up, so the next click can start another.
        if (isClosed(layers)) {
            pickUp(p, f, layers);
            return;
        }
        if (layer == null) {
            if (emptyHand) takeTop(p, f, layers);
            else p.sendMessage("§7That doesn't go in a sandwich.");
            return;
        }
        if (layer.isBread()) {
            if (layers.size() == 1) p.sendMessage("§7Put a filling on the bread first.");
            else place(p, f, slots.get(layers.size()), hand, food, layer);
            return;
        }
        // A filling must leave room for the slice that closes the sandwich.
        if (layers.size() - 1 >= SandwichConfig.maxFillings() || layers.size() + 1 >= slots.size()) {
            p.sendMessage("§7There's no room for more; close the sandwich with a slice of bread.");
            return;
        }
        place(p, f, slots.get(layers.size()), hand, food, layer);
    }

    /** A sandwich left on a broken board drops whole when closed, and as its layers when not. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        Furniture f = event.getFurniture();
        if (!SandwichConfig.isSandwichFurniture(f)) return;
        List<PlacedSlot> layers = layers(f, slotIds(f));
        if (layers.isEmpty()) return;
        ItemStack sandwich = isClosed(layers) ? SandwichItems.build(event.getPlayer(), items(layers)) : null;
        for (int i = 0; i < layers.size(); i++) {
            PlacedSlot slot = layers.get(i);
            if (sandwich == null) slot.setCurrentItem(asHeld(slot.getCurrentItem()));
            else slot.setCurrentItem(i == 0 ? sandwich : null);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // InteractibleFurniture restores the chunk's furniture first.
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> {
            for (Furniture f : placed()) {
                if (StationChunks.isIn(f, chunk)) relayout(f);
            }
        }, 2L);
    }

    /** Stacks the layers of every loaded sandwich again, after a restart. */
    public void resumeLoaded() {
        for (Furniture f : placed()) relayout(f);
    }

    private static Iterable<Furniture> placed() {
        return new ArrayList<>(InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values());
    }

    private void relayout(Furniture f) {
        if (!SandwichConfig.isSandwichFurniture(f)) return;
        stack(layers(f, slotIds(f)));
    }

    private void place(Player p, Furniture f, String slotId, ItemStack hand, FoodItem food, SandwichLayer layer) {
        ItemStack one = hand.clone();
        one.setAmount(1);
        ItemStack shown = layer.model(food, one);
        f.getOrCreatePlacedSlot(slotId).forceModel(shown == null ? one : shown);
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
        stack(layers(f, slotIds(f)));
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
        p.swingMainHand();
    }

    private void takeTop(Player p, Furniture f, List<PlacedSlot> layers) {
        PlacedSlot top = layers.get(layers.size() - 1);
        ItemStack held = asHeld(top.getCurrentItem());
        clear(f, top);
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        give(p, held);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
        p.swingMainHand();
    }

    private void pickUp(Player p, Furniture f, List<PlacedSlot> layers) {
        ItemStack sandwich = SandwichItems.build(p, items(layers));
        if (sandwich == null) {
            p.sendMessage("§cThis sandwich can't be made right now.");
            return;
        }
        for (PlacedSlot slot : layers) clear(f, slot);
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        give(p, sandwich);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        p.swingMainHand();
        Bukkit.getPluginManager().callEvent(new DishCookedEvent(p, sandwich, "sandwich"));
    }

    private static void clear(Furniture f, PlacedSlot slot) {
        slot.clearModel();
        f.removeActiveSlot(slot.getId());
    }

    private static void give(Player p, ItemStack item) {
        ItemStack rest = InventoryAdder.addItem(p, item);
        if (rest != null) p.getWorld().dropItem(p.getLocation(), rest);
    }

    /** Lifts each layer onto the one below it; the layer models are drawn lying on the board. */
    private static void stack(List<PlacedSlot> layers) {
        double height = 0;
        for (PlacedSlot slot : layers) {
            DisplayData data = new DisplayData();
            data.setyPos((float) (height / 16));
            slot.applyDisplayData(data);
            SandwichLayer layer = SandwichConfig.layerFor(FoodItem.fromItem(slot.getCurrentItem()));
            height += layer == null ? 0 : layer.getThickness();
        }
    }

    /** The furniture's sandwich slots, bottom first. */
    static List<String> slotIds(Furniture f) {
        List<String> ids = new ArrayList<>();
        for (String id : f.getType().getSlots().keySet()) {
            if (id.startsWith(SLOT_PREFIX)) ids.add(id);
        }
        ids.sort(Comparator.comparingInt(SandwichHandler::level));
        return ids;
    }

    private static int level(String slotId) {
        try {
            return Integer.parseInt(slotId.substring(SLOT_PREFIX.length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The layers lying on the board, bottom first. */
    private static List<PlacedSlot> layers(Furniture f, List<String> slots) {
        List<PlacedSlot> layers = new ArrayList<>();
        for (String id : slots) {
            PlacedSlot slot = f.getActiveSlot(id).orElse(null);
            if (slot != null && slot.getCurrentItem() != null) layers.add(slot);
        }
        return layers;
    }

    private static List<ItemStack> items(List<PlacedSlot> layers) {
        List<ItemStack> items = new ArrayList<>();
        for (PlacedSlot slot : layers) items.add(slot.getCurrentItem());
        return items;
    }

    private static boolean isBread(PlacedSlot slot) {
        SandwichLayer layer = SandwichConfig.layerFor(FoodItem.fromItem(slot.getCurrentItem()));
        return layer != null && layer.isBread();
    }

    /** Closed once a slice of bread lies on a filling. */
    private static boolean isClosed(List<PlacedSlot> layers) {
        return layers.size() > 1 && isBread(layers.get(layers.size() - 1));
    }

    /** A layer as it looks in hand, keeping its food data. */
    private static ItemStack asHeld(ItemStack item) {
        FoodItem food = FoodItem.fromItem(item);
        if (food == null) return item;
        ItemStack held = ItemUpdater.applyItemUpdate(item.clone(), food, null);
        return held == null ? item : held;
    }
}
