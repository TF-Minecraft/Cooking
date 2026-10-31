package net.tfminecraft.cooking.pizza;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.StationChunks;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedFurnitureSlot;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/**
 * Builds pizzas on a peel (pizzas.yml). Dough on an empty peel starts one, a sauce can be spread straight on the dough
 * and toppings go on top; an empty hand takes back the top layer. Carried into a lit oven (sneak and right-click with
 * an empty hand, like a baking tray) the pizza bakes, and once it's baked an empty hand takes it off the peel a slice
 * at a time.
 */
public final class PizzaHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Furniture f = event.getFurniture();
        if (!PizzaConfig.isPeel(f)) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir();
        // Sneaking with an empty hand carries the peel, into the oven or out of it.
        if (emptyHand && p.isSneaking()) return;
        List<String> slots = PizzaPeel.slotIds(f);
        if (slots.isEmpty()) return;
        List<PlacedSlot> layers = PizzaPeel.layers(f);
        FoodItem food = emptyHand ? null : FoodItem.fromItem(hand);
        SandwichLayer layer = PizzaConfig.layerFor(food);
        int baked = PizzaPeel.baked(f);

        if (layers.isEmpty()) {
            // An empty peel is picked up with an empty hand; only food starts anything on it.
            if (food == null) return;
            event.setCancelled(true);
            if (f.isAttached()) {
                p.sendMessage("§7Take the peel out of the oven first.");
            } else if (!PizzaConfig.isBase(layer)) {
                p.sendMessage("§7Start a pizza with a piece of dough.");
            } else {
                PizzaPeel.setBake(f, 0, PizzaPeel.RAW);
                place(p, f, slots.get(0), hand, food, layer);
            }
            return;
        }

        if (emptyHand) {
            if (baked > PizzaPeel.RAW) {
                event.setCancelled(true);
                serve(p, f, layers, baked);
            } else if (!f.isAttached()) {
                event.setCancelled(true);
                takeTop(p, f, layers);
            }
            return;
        }

        event.setCancelled(true);
        if (f.isAttached()) {
            p.sendMessage("§7Take the peel out of the oven first.");
        } else if (baked > PizzaPeel.RAW) {
            p.sendMessage("§7This pizza is baked; take a slice with an empty hand.");
        } else if (layer == null) {
            p.sendMessage("§7That doesn't go on a pizza.");
        } else if (PizzaConfig.isBase(layer)) {
            p.sendMessage("§7There's already dough on this peel.");
        } else if (PizzaConfig.isSauce(layer)) {
            if (layers.size() == 1) place(p, f, slots.get(1), hand, food, layer);
            else if (PizzaConfig.isSauce(layerOf(layers.get(1)))) p.sendMessage("§7This pizza already has its sauce.");
            else p.sendMessage("§7Spread the sauce before the toppings.");
        } else if (toppings(layers) >= PizzaConfig.maxToppings() || layers.size() >= slots.size()) {
            p.sendMessage("§7There's no room for more toppings.");
        } else {
            place(p, f, slots.get(layers.size()), hand, food, layer);
        }
    }

    /** A pizza left on a broken peel drops as its slices left when baked, and as its layers when not. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        Furniture f = event.getFurniture();
        if (!PizzaConfig.isPeel(f)) return;
        List<PlacedSlot> layers = PizzaPeel.layers(f);
        if (layers.isEmpty()) return;
        int baked = PizzaPeel.baked(f);
        ItemStack slices = baked > PizzaPeel.RAW ? slice(event.getPlayer(), f, layers, baked) : null;
        if (slices != null) slices.setAmount(Math.max(1, PizzaPeel.left(f)));
        for (int i = 0; i < layers.size(); i++) {
            PlacedSlot slot = layers.get(i);
            if (slices == null) slot.setCurrentItem(asHeld(slot.getCurrentItem()));
            else slot.setCurrentItem(i == 0 ? slices : null);
        }
        PizzaPeel.clearBake(f);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // InteractibleFurniture restores the chunk's furniture first.
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> {
            for (Furniture f : StationChunks.furnitureIn(chunk)) relayout(f);
        }, 2L);
    }

    /**
     * Stacks the layers of every loaded pizza again after a restart, then keeps them lying on their peels as the peels
     * go into ovens, which draw them smaller, and back out.
     */
    public void start() {
        for (Furniture f : placed()) relayout(f);
        Bukkit.getScheduler().runTaskTimer(Cooking.plugin, () -> {
            for (Furniture f : placed()) eachPeel(f, PizzaPeel::restackIfResized);
        }, 10L, 10L);
    }

    private static Iterable<Furniture> placed() {
        return new ArrayList<>(InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values());
    }

    private static void relayout(Furniture f) {
        eachPeel(f, PizzaPeel::stack);
    }

    /** The peel, and the peels held in it (an oven's cavity holds its peel). */
    private static void eachPeel(Furniture f, Consumer<Furniture> action) {
        if (PizzaConfig.isPeel(f)) action.accept(f);
        for (PlacedFurnitureSlot slot : f.getActiveFurnitureSlots().values()) {
            if (slot.getNested() != null) eachPeel(slot.getNested(), action);
        }
    }

    private static void place(Player p, Furniture f, String slotId, ItemStack hand, FoodItem food, SandwichLayer layer) {
        ItemStack one = hand.clone();
        one.setAmount(1);
        f.getOrCreatePlacedSlot(slotId).forceModel(PizzaPeel.shown(f, one, food, layer));
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
        PizzaPeel.stack(f);
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
        p.swingMainHand();
    }

    private static void takeTop(Player p, Furniture f, List<PlacedSlot> layers) {
        PlacedSlot top = layers.get(layers.size() - 1);
        ItemStack held = asHeld(top.getCurrentItem());
        clear(f, top);
        if (layers.size() == 1) PizzaPeel.clearBake(f);
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        give(p, held);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
        p.swingMainHand();
    }

    /** Takes a slice off the baked pizza; the last one leaves the peel empty. */
    private static void serve(Player p, Furniture f, List<PlacedSlot> layers, int baked) {
        boolean first = PizzaPeel.left(f) == PizzaConfig.slices();
        ItemStack slice = slice(p, f, layers, baked);
        if (slice == null) {
            p.sendMessage("§cThis pizza can't be made right now.");
            return;
        }
        int left = PizzaPeel.left(f) - 1;
        if (left > 0) {
            PizzaPeel.setLeft(f, left);
            PizzaPeel.remodel(f);
        } else {
            for (PlacedSlot slot : layers) clear(f, slot);
            PizzaPeel.clearBake(f);
        }
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        give(p, slice);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        p.swingMainHand();
        // The pizza counts as cooked once, when it's first cut.
        if (first) Bukkit.getPluginManager().callEvent(new DishCookedEvent(p, slice, "pizza"));
    }

    /** One slice of the pizza: the one cut from it before, or a new one kept for the slices after it. */
    private static ItemStack slice(Player p, Furniture f, List<PlacedSlot> layers, int baked) {
        ItemStack kept = PizzaPeel.slice(f);
        if (kept != null) return kept;
        ItemStack slice = PizzaItems.build(p, PizzaPeel.items(layers), baked);
        if (slice != null) PizzaPeel.keepSlice(f, slice);
        return slice;
    }

    private static int toppings(List<PlacedSlot> layers) {
        int count = 0;
        for (PlacedSlot slot : layers) {
            SandwichLayer layer = layerOf(slot);
            if (!PizzaConfig.isBase(layer) && !PizzaConfig.isSauce(layer)) count++;
        }
        return count;
    }

    private static SandwichLayer layerOf(PlacedSlot slot) {
        return PizzaConfig.layerFor(FoodItem.fromItem(slot.getCurrentItem()));
    }

    private static void clear(Furniture f, PlacedSlot slot) {
        slot.clearModel();
        f.removeActiveSlot(slot.getId());
    }

    private static void give(Player p, ItemStack item) {
        ItemStack rest = InventoryAdder.addItem(p, item);
        if (rest != null) p.getWorld().dropItem(p.getLocation(), rest);
    }

    /** A layer as it looks in hand, keeping its food data. */
    private static ItemStack asHeld(ItemStack item) {
        FoodItem food = FoodItem.fromItem(item);
        if (food == null) return item;
        ItemStack held = ItemUpdater.applyItemUpdate(item.clone(), food, null);
        return held == null ? item : held;
    }
}
