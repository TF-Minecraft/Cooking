package net.tfminecraft.cooking.manager;

import net.tfminecraft.cooking.util.LegacyModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.cooking.crops.CropsConfig;
import net.tfminecraft.cooking.fishing.LegacyFishConversion;
import net.tfminecraft.cooking.fishing.SeafoodWholeItems;
import net.tfminecraft.cooking.fishing.VanillaFishAdapter;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.cooking.utils.PlayerActionContext;

public class ConversionManager implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || PlayerActionContext.capture(player) == null) return;
        Inventory top = event.getView().getTopInventory();
        if (!stackingInventory(top)) return;
        if (event.getSlotType() != InventoryType.SlotType.CONTAINER
                && event.getSlotType() != InventoryType.SlotType.QUICKBAR) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && event.isShiftClick()) {
            shiftFood(event, top, clicked);
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            collectFood(event, top);
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (!stackableFood(cursor, current)) return;

        // Cancel even when full: differing clocks must not turn a stack attempt into a swap.
        event.setCancelled(true);
        int room = Math.min(current.getMaxStackSize(), clicked.getMaxStackSize()) - current.getAmount();
        int amount = Math.min(room, event.isRightClick() ? 1 : cursor.getAmount());
        if (amount <= 0) return;
        event.setCurrentItem(mergedFood(current, cursor, amount));
        event.getView().setCursor(remainder(cursor, cursor.getAmount() - amount));
    }

    private static boolean stackingInventory(Inventory inventory) {
        // Only unrestricted storage slots; machines, equipment and plugin menus own their rules.
        boolean storage = switch (inventory.getType()) {
            case CRAFTING, CHEST, BARREL, ENDER_CHEST, SHULKER_BOX -> true;
            default -> false;
        };
        InventoryHolder holder = inventory.getHolder();
        return storage && (holder == null || holder instanceof org.bukkit.block.BlockState
                || holder instanceof org.bukkit.block.DoubleChest || holder instanceof org.bukkit.entity.Entity);
    }

    private static void shiftFood(InventoryClickEvent event, Inventory top, Inventory source) {
        ItemStack item = event.getCurrentItem();
        if (FoodItem.fromItem(item) == null) return;
        Inventory target;
        List<Integer> slots = new ArrayList<>();
        if (top.getType() == InventoryType.CRAFTING) {
            if (source == top || event.getSlot() >= 36) return;
            target = source;
            int start = event.getSlot() < 9 ? 9 : 0;
            int end = event.getSlot() < 9 ? 36 : 9;
            for (int i = start; i < end; i++) slots.add(i);
        } else if (source == top) {
            target = event.getView().getBottomInventory();
            // Vanilla container quick-move visits the hotbar, then storage, in reverse order.
            for (int i = 8; i >= 0; i--) slots.add(i);
            for (int i = 35; i >= 9; i--) slots.add(i);
        } else {
            target = top;
            for (int i = 0; i < top.getSize(); i++) slots.add(i);
        }

        int remaining = item.getAmount();
        for (int slot : slots) {
            ItemStack current = target.getItem(slot);
            if (!stackableFood(item, current)) continue;
            int room = Math.min(current.getMaxStackSize(), target.getMaxStackSize()) - current.getAmount();
            int amount = Math.min(room, remaining);
            if (amount <= 0) continue;
            event.setCancelled(true);
            target.setItem(slot, mergedFood(current, item, amount));
            remaining -= amount;
            if (remaining == 0) break;
        }
        if (!event.isCancelled()) return;

        // Once a merge is handled here, finish the same move into empty slots without losing excess.
        for (int slot : slots) {
            if (remaining == 0) break;
            ItemStack current = target.getItem(slot);
            if (current != null && !current.isEmpty()) continue;
            int amount = Math.min(remaining, Math.min(item.getMaxStackSize(), target.getMaxStackSize()));
            target.setItem(slot, remainder(item, amount));
            remaining -= amount;
        }
        event.setCurrentItem(remainder(item, remaining));
    }

    private record Slot(Inventory inventory, int index) {
        ItemStack item() {
            return inventory.getItem(index);
        }
    }

    /**
     * Double-click gathers only exact copies, so food that aged apart would stay behind. When some
     * did, gather it the way vanilla does: the container or crafting grid first, then storage, the
     * hotbar and the off-hand, part stacks before full ones. Otherwise vanilla gathers as usual.
     */
    private static void collectFood(InventoryClickEvent event, Inventory top) {
        ItemStack cursor = event.getCursor();
        if (FoodItem.fromItem(cursor) == null) return;
        Inventory bottom = event.getView().getBottomInventory();
        boolean crafting = top.getType() == InventoryType.CRAFTING;
        List<Slot> order = new ArrayList<>();
        // The crafting result (slot 0) is never gathered.
        for (int i = crafting ? 1 : 0; i < top.getSize(); i++) order.add(new Slot(top, i));
        // The player view then lists the armour, head to feet.
        if (crafting) for (int i = 39; i >= 36; i--) order.add(new Slot(bottom, i));
        for (int i = 9; i < 36; i++) order.add(new Slot(bottom, i));
        for (int i = 0; i < 9; i++) order.add(new Slot(bottom, i));
        if (crafting) order.add(new Slot(bottom, 40));

        List<Slot> food = new ArrayList<>();
        boolean agedApart = false;
        for (Slot slot : order) {
            ItemStack item = slot.item();
            if (!stackableFood(cursor, item)) continue;
            food.add(slot);
            agedApart |= !item.isSimilar(cursor);
        }
        if (!agedApart) return;
        event.setCancelled(true);

        ItemStack gathered = cursor;
        int limit = Math.min(cursor.getMaxStackSize(), bottom.getMaxStackSize());
        for (boolean fullStacks : new boolean[] {false, true}) {
            for (Slot slot : food) {
                ItemStack item = slot.item();
                int room = limit - gathered.getAmount();
                // A slot emptied by the first pass is null in the second.
                if (room <= 0 || item == null || (item.getAmount() >= item.getMaxStackSize()) != fullStacks) continue;
                int amount = Math.min(room, item.getAmount());
                gathered = mergedFood(gathered, item, amount);
                slot.inventory().setItem(slot.index(), remainder(item, item.getAmount() - amount));
            }
        }
        event.getView().setCursor(gathered);
    }

    private static boolean stackableFood(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.getType() != b.getType()) return false;
        FoodItem foodA = FoodItem.fromItem(a);
        FoodItem foodB = FoodItem.fromItem(b);
        if (foodA == null || foodB == null) return false;
        foodA.updateAge();
        foodB.updateAge();
        return InventoryAdder.equalsFood(foodA, foodB) && sameApartFromAging(a, b);
    }

    /** current with amount more of incoming, keeping the more aged copy's data. */
    public static ItemStack mergedFood(ItemStack current, ItemStack incoming, int amount) {
        ItemStack merged = BundleFoodStacker.moreAged(current, incoming).clone();
        merged.setAmount(current.getAmount() + amount);
        return merged;
    }

    private static ItemStack remainder(ItemStack item, int amount) {
        if (amount == 0) return null;
        ItemStack rest = item.clone();
        rest.setAmount(amount);
        return rest;
    }

    @EventHandler(ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        if (!(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();
        // Paper fires pickup events before it refuses an item that belongs to another player.
        if (!mayTake(p, e.getItem())) return;
        if (FoodItem.fromItem(item) != null) {
            stackOntoAgedFood(e, p, item);
            return;
        }
        if (replaceLegacyFish(e, p, item)) {
            return;
        }
        if (replaceVanillaFish(e, p, item)) {
            return;
        }
        String result = ConversionLoader.getByItem(item);

        if (result != null) {
            FoodParser.Result parsed = FoodParser.parse(result);
            if (parsed == null || parsed.template == null) {
                return;
            }
            int quality = CropsConfig.isFarmFood(result)
                    ? 1
                    : OriginQualityResolver.resolve(p, parsed.template);
            giveConverted(e, p, ItemBuilder.buildSingleWithQuality(parsed.template, item, quality));
        }
    }

    /**
     * Paper only fires the pickup event when the item fits as it is. Food that only fits on an equal
     * food that aged differently never gets one, so offer it, leaving other plugins their say.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void attemptPickup(PlayerAttemptPickupItemEvent e) {
        Item entity = e.getItem();
        ItemStack item = entity.getItemStack();
        if (e.getRemaining() < item.getAmount()) return;
        Player player = e.getPlayer();
        if (!player.getCanPickupItems() || !mayTake(player, entity)) return;
        if (!InventoryAdder.hasStackFor(player, item)) return;
        Bukkit.getPluginManager().callEvent(new EntityPickupItemEvent(player, entity, 0));
    }

    private static boolean mayTake(Player player, Item entity) {
        UUID owner = entity.getOwner();
        return owner == null || owner.equals(player.getUniqueId());
    }

    /** Paper shrinks the ground item to what fits while the event runs; the rest is in getRemaining. */
    private static int groundAmount(EntityPickupItemEvent event) {
        return event.getItem().getItemStack().getAmount() + event.getRemaining();
    }

    /**
     * Food on the ground keeps the clock it was made with, so a vanilla pickup starts a new stack
     * beside an equal food that only aged differently. Add it the way Cooking adds food instead.
     */
    private void stackOntoAgedFood(EntityPickupItemEvent event, Player player, ItemStack item) {
        if (!InventoryAdder.hasStackFor(player, item)) return;
        event.setCancelled(true);
        ItemStack ground = item.clone();
        ground.setAmount(groundAmount(event));
        ItemStack leftover = InventoryAdder.addItem(player, ground);
        if (leftover == null) {
            event.getItem().remove();
        } else {
            event.getItem().setItemStack(leftover);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
    }

    private boolean replaceLegacyFish(EntityPickupItemEvent event, Player player, ItemStack item) {
        ItemStack stack = LegacyFishConversion.convert(player, item);
        if (stack == null) {
            return false;
        }
        giveConverted(event, player, stack);
        return true;
    }

    private boolean replaceVanillaFish(EntityPickupItemEvent event, Player player, ItemStack item) {
        VanillaFishAdapter.Decision decision = VanillaFishAdapter.decide(
                item.getType().name(),
                hasCustomModelData(item));
        if (!decision.replaces()) {
            return false;
        }
        int quality = OriginQualityResolver.resolve(player, null);
        ItemStack stack = SeafoodWholeItems.build(item, decision.fish(), quality);
        if (stack == null) {
            return false;
        }
        giveConverted(event, player, stack);
        return true;
    }

    private static boolean hasCustomModelData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && LegacyModelData.has(meta);
    }

    private void giveConverted(EntityPickupItemEvent event, Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        stack.setAmount(groundAmount(event));
        event.setCancelled(true);
        event.getItem().remove();
        ItemStack leftover = InventoryAdder.addItem(player, stack);
        if (leftover == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        Inventory top = e.getInventory();

        InventoryHolder holder = top.getHolder();
        if (holder != null &&
            !(holder instanceof org.bukkit.block.BlockState) &&
            !(holder instanceof org.bukkit.block.DoubleChest) &&
            !(holder instanceof org.bukkit.entity.Entity) &&
            !(holder instanceof Player)) {
            return;
        }

        Player p = (Player) e.getPlayer();
        Inventory bottom = p.getInventory();

        int topSize = top.getSize();
        int total = topSize + bottom.getSize();

        ItemStack[] items = new ItemStack[total];
        FoodItem[] foods = new FoodItem[total];
        for (int i = 0; i < total; i++) {
            items[i] = getSlot(i, top, bottom, topSize);
            foods[i] = items[i] == null ? null : FoodItem.fromItem(items[i]);
        }

        // Equal foods all take the data of the most aged one, so opening a chest never makes food
        // fresher. Ties go to the first slot.
        for (int i = 0; i < total; i++) {
            if (foods[i] == null) continue;

            List<Integer> group = new ArrayList<>(List.of(i));
            ItemStack oldest = items[i];
            for (int j = i + 1; j < total; j++) {
                if (foods[j] == null) continue;
                if (!InventoryAdder.equalsFood(foods[i], foods[j]) || !sameApartFromAging(items[i], items[j])) continue;
                group.add(j);
                foods[j] = null;
                oldest = BundleFoodStacker.moreAged(oldest, items[j]);
            }

            for (int k : group) {
                if (oldest.isSimilar(items[k])) continue;
                ItemStack clone = oldest.clone();
                clone.setAmount(items[k].getAmount());
                setSlot(k, clone, top, bottom, topSize);
            }
        }
    }

    /**
     * True when two food stacks differ only in how far they have aged, so one can be copied over
     * the other to let them stack. equalsFood matches by kind and tag step alone, so without this
     * a carved roast or a sausage chain would be replaced by a fresh copy of another. Catch size
     * changes nothing once a fish is caught, so fish of one kind and quality stack whatever their size.
     */
    public static boolean sameApartFromAging(ItemStack a, ItemStack b) {
        return withoutAging(a).isSimilar(withoutAging(b));
    }

    private static ItemStack withoutAging(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        var pdc = meta.getPersistentDataContainer();
        // The clock, the progress within each tag step, catch size, the clean-catch marker (fish
        // built from plain vanilla fish never got it), and the lore written from them. Food lore
        // is always rebuilt whole from the item's data (ItemBuilder.stamp), which is compared.
        pdc.remove(Keys.LAST_UPDATE);
        pdc.remove(Keys.AGE_REMAINDER);
        pdc.remove(Keys.TAGS);
        pdc.remove(Keys.LORE_INDEX_MAP);
        pdc.remove(Keys.CATCH_SIZE_CM);
        pdc.remove(Keys.CLEAN_CATCH);
        meta.lore(null);
        copy.setItemMeta(meta);
        return copy;
    }

    private ItemStack getSlot(int index, Inventory top, Inventory bottom, int topSize) {
        return index < topSize ? top.getItem(index) : bottom.getItem(index - topSize);
    }

    private void setSlot(int index, ItemStack item, Inventory top, Inventory bottom, int topSize) {
        if (index < topSize) top.setItem(index, item);
        else bottom.setItem(index - topSize, item);
    }
}
