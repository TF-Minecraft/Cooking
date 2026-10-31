package net.tfminecraft.cooking.salad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.cooking.PotReference;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.manager.BowlIngredientLayout;
import net.tfminecraft.cooking.sandwich.SandwichLayer;
import net.tfminecraft.cooking.utils.HeldStack;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

/**
 * Tosses salads in a placed bowl (salads.yml). Right-clicking an empty bowl with an ingredient lays it in the bowl
 * (input_1, input_2, ...); dressings and seasonings go in unseen. Sneaking with an empty hand takes the last thing
 * back. An empty-hand right-click tosses the salad, which then stays in the bowl (food_item) until it's eaten there
 * (BowlEatHandler); it can't be taken out.
 */
public final class SaladHandler implements Listener {
    static final String INPUT_PREFIX = "input_";
    static final String SALAD_SLOT = "food_item";
    static final String EXTRAS = "salad_extras";
    private static final String SEPARATOR = ",";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Furniture f = event.getFurniture();
        if (!SaladConfig.isSaladFurniture(f)) return;
        // A soup or a tossed salad is eaten from the bowl; BowlEatHandler handles that.
        if (has(f, SALAD_SLOT)) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir();
        List<PlacedSlot> shown = ingredients(f);
        List<ItemStack> extras = extras(f);

        if (emptyHand) {
            if (shown.isEmpty() && extras.isEmpty()) return;
            event.setCancelled(true);
            if (p.isSneaking()) takeLast(p, f, shown, extras);
            else toss(p, f, shown, extras);
            return;
        }
        FoodItem food = FoodItem.fromItem(hand);
        SandwichLayer ingredient = SaladConfig.ingredientFor(food);
        if (ingredient != null) {
            event.setCancelled(true);
            String slot = freeSlot(f);
            if (slot == null || shown.size() >= SaladConfig.maxIngredients()) {
                p.sendMessage("§7The bowl is full; toss the salad with an empty hand.");
                return;
            }
            addIngredient(p, f, slot, hand);
            return;
        }
        SandwichLayer extra = SaladConfig.extraFor(food);
        if (extra == null) return;
        event.setCancelled(true);
        String refused = refuseExtra(extras, food, extra);
        if (refused != null) {
            p.sendMessage("§7" + refused);
            return;
        }
        addExtra(p, f, hand, extras, extra);
    }

    /** Why this extra can't go in, or null when it can. */
    static String refuseExtra(List<ItemStack> extras, FoodItem food, SandwichLayer extra) {
        if (extras.size() >= SaladConfig.maxExtras()) return "There's enough dressing and seasoning in it already.";
        String origin = origin(food);
        for (ItemStack stack : extras) {
            FoodItem in = FoodItem.fromItem(stack);
            if (in == null) continue;
            if (SaladConfig.isOnce(extra) && extra == SaladConfig.extraFor(in)) return "It already has a dressing.";
            if (origin.equals(origin(in))) return "That's already in it.";
        }
        return null;
    }

    /** What's left in a broken bowl that hasn't been tossed drops; a tossed salad goes with the bowl, like soup. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        Furniture f = event.getFurniture();
        if (!SaladConfig.isSaladFurniture(f) || has(f, SALAD_SLOT)) return;
        List<ItemStack> drops = new ArrayList<>();
        for (PlacedSlot slot : ingredients(f)) {
            drops.add(asHeld(slot.getCurrentItem()));
            clear(f, slot);
        }
        drops.addAll(extras(f));
        f.getVariables().remove(EXTRAS);
        for (ItemStack drop : drops) f.getLoc().getWorld().dropItem(f.getLoc(), drop);
    }

    private void addIngredient(Player p, Furniture f, String slotId, ItemStack hand) {
        ItemStack one = hand.clone();
        one.setAmount(1);
        PlacedSlot slot = f.getOrCreatePlacedSlot(slotId);
        slot.forceModel(one);
        DisplayData spread = BowlIngredientLayout.offsetFor(slotId);
        if (spread != null) slot.applyDisplayData(spread);
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
        done(p, f, Sound.ENTITY_ITEM_FRAME_ADD_ITEM);
    }

    private void addExtra(Player p, Furniture f, ItemStack hand, List<ItemStack> extras, SandwichLayer extra) {
        ItemStack one = hand.clone();
        one.setAmount(1);
        extras.add(one);
        setExtras(f, extras);
        if (SaladConfig.returnsBottle(extra)) {
            HeldStack.useOneKeepStack(p, new ItemStack(Material.GLASS_BOTTLE));
        } else {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        }
        done(p, f, Sound.ITEM_BOTTLE_EMPTY);
    }

    /** Takes back what went in last: the last ingredient, or with none, the last extra. */
    private void takeLast(Player p, Furniture f, List<PlacedSlot> shown, List<ItemStack> extras) {
        ItemStack back;
        if (!shown.isEmpty()) {
            PlacedSlot last = shown.get(shown.size() - 1);
            back = asHeld(last.getCurrentItem());
            clear(f, last);
        } else {
            back = extras.remove(extras.size() - 1);
            setExtras(f, extras);
        }
        give(p, back);
        done(p, f, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM);
    }

    private void toss(Player p, Furniture f, List<PlacedSlot> shown, List<ItemStack> extras) {
        if (shown.size() < SaladConfig.minIngredients()) {
            p.sendMessage("§7Add at least " + SaladConfig.minIngredients() + " ingredients before you toss it.");
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (PlacedSlot slot : shown) items.add(slot.getCurrentItem());
        ItemStack salad = SaladItems.build(p, items, extras);
        if (salad == null) {
            p.sendMessage("§cThis salad can't be made right now.");
            return;
        }
        // The salad itself stays out of sight: the ingredients in the bowl show it.
        PlacedSlot slot = f.getOrCreatePlacedSlot(SALAD_SLOT);
        slot.forceModel(salad);
        DisplayData hidden = new DisplayData();
        hidden.setxScale(0.001f);
        hidden.setyScale(0.001f);
        hidden.setzScale(0.001f);
        slot.applyDisplayData(hidden);
        f.getVariables().remove(EXTRAS);
        done(p, f, Sound.BLOCK_GRASS_STEP);
        Bukkit.getPluginManager().callEvent(new DishCookedEvent(p, salad, "salad"));
    }

    private static void done(Player p, Furniture f, Sound sound) {
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        f.getLoc().getWorld().playSound(f.getLoc(), sound, 1f, 1f);
        p.swingMainHand();
    }

    /** True while a bowl holds salad ingredients or extras that haven't been tossed yet. */
    public static boolean isMaking(Furniture f) {
        return SaladConfig.isSaladFurniture(f) && !has(f, SALAD_SLOT) && (!ingredients(f).isEmpty() || !extras(f).isEmpty());
    }

    private static boolean has(Furniture f, String slotId) {
        PlacedSlot slot = f.getActiveSlot(slotId).orElse(null);
        return slot != null && slot.getCurrentItem() != null && !slot.getCurrentItem().getType().isAir();
    }

    /** The bowl's ingredient slots that hold something, in order. */
    static List<PlacedSlot> ingredients(Furniture f) {
        List<PlacedSlot> shown = new ArrayList<>();
        for (int i = 1; f.getType().getSlot(INPUT_PREFIX + i) != null; i++) {
            if (has(f, INPUT_PREFIX + i)) shown.add(f.getActiveSlot(INPUT_PREFIX + i).get());
        }
        return shown;
    }

    private static String freeSlot(Furniture f) {
        for (int i = 1; f.getType().getSlot(INPUT_PREFIX + i) != null; i++) {
            if (!has(f, INPUT_PREFIX + i)) return INPUT_PREFIX + i;
        }
        return null;
    }

    /** The dressings and seasonings in the bowl, kept in its variables so they survive restarts. */
    static List<ItemStack> extras(Furniture f) {
        List<ItemStack> extras = new ArrayList<>();
        if (!(f.getVariables().get(EXTRAS) instanceof String encoded)) return extras;
        for (String part : encoded.split(SEPARATOR)) {
            ItemStack stack = PotReference.decodeStack(part);
            if (stack != null) extras.add(stack);
        }
        return extras;
    }

    private static void setExtras(Furniture f, List<ItemStack> extras) {
        if (extras.isEmpty()) {
            f.getVariables().remove(EXTRAS);
            return;
        }
        List<String> parts = new ArrayList<>();
        for (ItemStack stack : extras) parts.add(PotReference.encodeStack(stack));
        f.getVariables().put(EXTRAS, String.join(SEPARATOR, parts));
    }

    private static String origin(FoodItem food) {
        return food.getOrigin() == null ? "" : food.getOrigin().toLowerCase(Locale.ROOT);
    }

    private static void clear(Furniture f, PlacedSlot slot) {
        slot.clearModel();
        f.removeActiveSlot(slot.getId());
    }

    private static void give(Player p, ItemStack item) {
        ItemStack rest = InventoryAdder.addItem(p, item);
        if (rest != null) p.getWorld().dropItem(p.getLocation(), rest);
    }

    /** An ingredient as it looks in hand, keeping its food data. */
    private static ItemStack asHeld(ItemStack item) {
        FoodItem food = FoodItem.fromItem(item);
        if (food == null) return item;
        ItemStack held = ItemUpdater.applyItemUpdate(item.clone(), food, null);
        return held == null ? item : held;
    }

}
