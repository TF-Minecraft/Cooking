package net.tfminecraft.cooking.pie;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/**
 * Makes pies in a pie tin (pies.yml), the round cousin of the bread tray. Dough on an empty tin lines it, then fillings
 * go in under its top crust; an empty hand takes back what went in last. Carried into a lit oven (sneak and
 * right-click with an empty hand, like a baking tray) a filled pie bakes, and once it's baked an empty hand takes the
 * whole pie out, to be cut into slices on the cutting board.
 */
public final class PieHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Furniture f = event.getFurniture();
        if (!PieConfig.isTin(f)) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir();
        // Sneaking with an empty hand carries the tin, into the oven or out of it.
        if (emptyHand && p.isSneaking()) return;
        PlacedSlot crust = PieTin.crust(f);
        List<ItemStack> fillings = PieTin.fillings(f);
        int baked = PieTin.baked(f);

        if (emptyHand) {
            // An empty tin is picked up with an empty hand.
            if (crust == null) return;
            if (baked > PieTin.RAW) {
                event.setCancelled(true);
                takePie(p, f, crust, fillings, baked);
            } else if (!f.isAttached()) {
                event.setCancelled(true);
                takeLast(p, f, crust, fillings);
            }
            return;
        }

        FoodItem food = FoodItem.fromItem(hand);
        if (food == null) return;
        event.setCancelled(true);
        SandwichLayer layer = PieConfig.layerFor(food);
        if (f.isAttached()) {
            p.sendMessage("§7Take the tin out of the oven first.");
        } else if (crust == null) {
            if (PieConfig.isCrust(layer)) line(p, f, hand);
            else p.sendMessage("§7Line the tin with a piece of dough first.");
        } else if (baked > PieTin.RAW) {
            p.sendMessage("§7This pie is baked; take it out with an empty hand.");
        } else if (layer == null) {
            p.sendMessage("§7That doesn't go in a pie.");
        } else if (PieConfig.isCrust(layer)) {
            p.sendMessage("§7There's already dough in this tin.");
        } else if (fillings.size() >= PieConfig.maxFillings()) {
            p.sendMessage("§7The pie is full; bake it in the oven.");
        } else {
            fill(p, f, hand, fillings);
        }
    }

    /** A broken tin drops its baked pie whole, or its dough and fillings when it hasn't baked. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        Furniture f = event.getFurniture();
        if (!PieConfig.isTin(f)) return;
        PlacedSlot crust = PieTin.crust(f);
        if (crust == null) return;
        List<ItemStack> fillings = PieTin.fillings(f);
        int baked = PieTin.baked(f);
        ItemStack pie = baked > PieTin.RAW ? PieItems.build(event.getPlayer(), crust.getCurrentItem(), fillings, baked) : null;
        List<ItemStack> drops = new ArrayList<>();
        if (pie != null) {
            drops.add(pie);
        } else {
            drops.add(asHeld(crust.getCurrentItem()));
            drops.addAll(fillings);
        }
        clear(f, crust);
        for (ItemStack drop : drops) f.getLoc().getWorld().dropItem(f.getLoc(), drop);
    }

    private static void line(Player p, Furniture f, ItemStack hand) {
        PlacedSlot slot = f.getOrCreatePlacedSlot(PieConfig.slot());
        slot.forceModel(one(hand));
        PieTin.setBake(f, 0, PieTin.RAW);
        PieTin.remodel(f);
        use(p, hand);
        done(p, f, Sound.ENTITY_ITEM_FRAME_ADD_ITEM);
    }

    private static void fill(Player p, Furniture f, ItemStack hand, List<ItemStack> fillings) {
        fillings.add(one(hand));
        PieTin.setFillings(f, fillings);
        // A new filling has to bake through with the rest.
        PieTin.setBake(f, 0, PieTin.RAW);
        PieTin.remodel(f);
        use(p, hand);
        done(p, f, Sound.BLOCK_HONEY_BLOCK_PLACE);
    }

    /** Takes back what went in last: the last filling, or with none, the dough. */
    private static void takeLast(Player p, Furniture f, PlacedSlot crust, List<ItemStack> fillings) {
        ItemStack back;
        if (!fillings.isEmpty()) {
            back = fillings.remove(fillings.size() - 1);
            PieTin.setFillings(f, fillings);
            PieTin.remodel(f);
        } else {
            back = asHeld(crust.getCurrentItem());
            clear(f, crust);
        }
        give(p, back);
        done(p, f, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM);
    }

    /** Takes the baked pie out whole, leaving the tin empty. */
    private static void takePie(Player p, Furniture f, PlacedSlot crust, List<ItemStack> fillings, int baked) {
        ItemStack pie = PieItems.build(p, crust.getCurrentItem(), fillings, baked);
        if (pie == null) {
            p.sendMessage("§cThis pie can't be made right now.");
            return;
        }
        clear(f, crust);
        give(p, pie);
        done(p, f, Sound.ENTITY_ITEM_PICKUP);
        Bukkit.getPluginManager().callEvent(new DishCookedEvent(p, pie, "pie"));
    }

    private static ItemStack one(ItemStack hand) {
        ItemStack one = hand.clone();
        one.setAmount(1);
        return one;
    }

    private static void use(Player p, ItemStack hand) {
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
    }

    private static void done(Player p, Furniture f, Sound sound) {
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        f.getLoc().getWorld().playSound(f.getLoc(), sound, 1f, 1f);
        p.swingMainHand();
    }

    private static void clear(Furniture f, PlacedSlot crust) {
        crust.clearModel();
        f.removeActiveSlot(crust.getId());
        PieTin.clear(f);
    }

    private static void give(Player p, ItemStack item) {
        ItemStack rest = InventoryAdder.addItem(p, item);
        if (rest != null) p.getWorld().dropItem(p.getLocation(), rest);
    }

    /** The dough as it looks in hand, keeping its food data. */
    private static ItemStack asHeld(ItemStack item) {
        FoodItem food = FoodItem.fromItem(item);
        if (food == null) return item;
        ItemStack held = ItemUpdater.applyItemUpdate(item.clone(), food, null);
        return held == null ? item : held;
    }
}
