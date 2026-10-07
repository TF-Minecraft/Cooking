package net.tfminecraft.cooking.manager;

import net.tfminecraft.cooking.util.LegacyModelData;

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

public class ConversionManager implements Listener {

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
            !(holder instanceof org.bukkit.entity.Entity) &&
            !(holder instanceof Player)) {
            return;
        }

        Player p = (Player) e.getPlayer();
        Inventory bottom = p.getInventory();

        int topSize = top.getSize();
        int total = topSize + bottom.getSize();

        for (int i = total - 1; i >= 0; i--) {
            ItemStack a = getSlot(i, top, bottom, topSize);
            if (a == null) continue;

            FoodItem fa = FoodItem.fromItem(a);
            if (fa == null) continue;

            for (int j = 0; j < total; j++) {
                if (i == j) continue;

                ItemStack b = getSlot(j, top, bottom, topSize);
                if (b == null) continue;

                FoodItem fb = FoodItem.fromItem(b);
                if (fb == null) continue;

                if (InventoryAdder.equalsFood(fa, fb) && sameApartFromAging(a, b)) {
                    ItemStack clone = b.clone();
                    clone.setAmount(a.getAmount());
                    setSlot(i, clone, top, bottom, topSize);
                    break;
                }
            }
        }
    }

    /**
     * True when two food stacks differ only in how far they have aged, so one can be copied over
     * the other to let them stack. equalsFood matches by kind and tag step alone, so without this
     * a carved roast, a sausage chain or a big catch would be replaced by a fresh copy of another.
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
        // The clock, the progress within each tag step, and the lore written from them. Food lore
        // is always rebuilt whole from the item's data (ItemBuilder.stamp), which is compared.
        pdc.remove(Keys.LAST_UPDATE);
        pdc.remove(Keys.AGE_REMAINDER);
        pdc.remove(Keys.TAGS);
        pdc.remove(Keys.LORE_INDEX_MAP);
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
