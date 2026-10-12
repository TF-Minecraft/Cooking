package net.tfminecraft.cooking.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import net.tfminecraft.cooking.utils.PlayerActionContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.utils.InventoryAdder;

/**
 * Aging foods carry their own clock, so two pieces of the same meat never match exactly and a
 * bundle keeps them apart. After a click that may have put food into a bundle, equal foods in it
 * are merged the way inventories already stack them. Each merge keeps the more aged copy's data,
 * so putting old food in with fresh food can never make it fresher.
 */
public class BundleFoodStacker implements Listener {

    private static final String FRESHNESS = "freshness";

    private final Consumer<Runnable> nextTick;

    public BundleFoodStacker() {
        this(task -> Bukkit.getScheduler().runTask(Cooking.plugin, task));
    }

    BundleFoodStacker(Consumer<Runnable> nextTick) {
        this.nextTick = nextTick;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!isBundle(e.getCursor()) && !isBundle(e.getCurrentItem())) return;

        if (!(e.getWhoClicked() instanceof Player player)) return;
        PlayerActionContext context = PlayerActionContext.capture(player);
        if (context == null) return;
        Inventory clicked = e.getClickedInventory();
        int slot = e.getSlot();
        // The bundle changes after this event, so merge on the next tick. Read the cursor and the
        // slot again then: the player may have moved things in between.
        nextTick.accept(() -> {
            if (context.isCurrent()) stackBundles(player, clicked, slot);
        });
    }

    static void stackBundles(HumanEntity player, Inventory clicked, int slot) {
        ItemStack cursor = stack(player.getItemOnCursor());
        if (cursor != null) player.setItemOnCursor(cursor);

        if (clicked == null || !inView(player.getOpenInventory(), clicked)) return;
        ItemStack inSlot = stack(clicked.getItem(slot));
        if (inSlot != null) clicked.setItem(slot, inSlot);
    }

    private static boolean inView(InventoryView view, Inventory inv) {
        return view.getTopInventory() == inv || view.getBottomInventory() == inv;
    }

    /** The bundle with its foods merged, or null when it is not a bundle or nothing merged. */
    static ItemStack stack(ItemStack bundle) {
        if (!isBundle(bundle) || !(bundle.getItemMeta() instanceof BundleMeta meta)) return null;
        List<ItemStack> merged = merge(meta.getItems());
        if (merged == null) return null;

        ItemStack out = bundle.clone();
        meta.setItems(merged);
        out.setItemMeta(meta);
        return out;
    }

    /** Bundle contents with equal foods merged, keeping order, or null when nothing merged. */
    static List<ItemStack> merge(List<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>();
        boolean changed = false;

        for (ItemStack item : items) {
            FoodItem food = FoodItem.fromItem(item);
            int amount = item.getAmount();

            for (int i = 0; i < out.size() && food != null && amount > 0; i++) {
                ItemStack target = out.get(i);
                int room = target.getMaxStackSize() - target.getAmount();
                if (room <= 0 || !sameFood(food, item, target)) continue;

                int add = Math.min(room, amount);
                ItemStack kept = moreAged(target, item).clone();
                kept.setAmount(target.getAmount() + add);
                out.set(i, kept);
                amount -= add;
                changed = true;
            }

            if (amount > 0) {
                ItemStack rest = item.clone();
                rest.setAmount(amount);
                out.add(rest);
            }
        }
        return changed ? out : null;
    }

    private static boolean sameFood(FoodItem food, ItemStack item, ItemStack other) {
        if (item.getType() != other.getType()) return false;
        FoodItem otherFood = FoodItem.fromItem(other);
        return otherFood != null
                && InventoryAdder.equalsFood(food, otherFood)
                && ConversionManager.sameApartFromAging(item, other);
    }

    /**
     * Equal foods age at the same rate, so whichever is further along now is the older one.
     * Freshness decides, so food never gets fresher; the other aging tracks, such as warmth, only
     * break a tie.
     */
    static ItemStack moreAged(ItemStack a, ItemStack b) {
        double[] ageA = age(a);
        double[] ageB = age(b);
        int order = Double.compare(ageA[0], ageB[0]);
        if (order == 0) order = Double.compare(ageA[1], ageB[1]);
        return order >= 0 ? a : b;
    }

    /** How far the food has aged by now: freshness first, then every other aging track. */
    private static double[] age(ItemStack item) {
        FoodItem food = FoodItem.fromItem(item);
        food.updateAge();
        double[] age = new double[2];
        for (TagTrack track : food.getTagTracks()) {
            if (!track.isAgeable()) continue;
            int index = FRESHNESS.equals(track.getId()) ? 0 : 1;
            age[index] += track.getValue() + food.getAgeRemainder(track.getId());
        }
        return age;
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && Tag.ITEMS_BUNDLES.isTagged(item.getType());
    }
}
