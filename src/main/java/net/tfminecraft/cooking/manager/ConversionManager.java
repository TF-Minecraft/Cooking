package net.tfminecraft.cooking.manager;

import net.tfminecraft.cooking.util.LegacyModelData;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
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

public class ConversionManager implements Listener {

    @EventHandler
    public void pickup(EntityPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        if (FoodItem.fromItem(item) != null) return;
        if (!(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();
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
        stack.setAmount(event.getItem().getItemStack().getAmount());
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

                if (InventoryAdder.equalsFood(fa, fb)) {
                    ItemStack clone = b.clone();
                    clone.setAmount(a.getAmount());
                    setSlot(i, clone, top, bottom, topSize);
                    break;
                }
            }
        }
    }

    private ItemStack getSlot(int index, Inventory top, Inventory bottom, int topSize) {
        return index < topSize ? top.getItem(index) : bottom.getItem(index - topSize);
    }

    private void setSlot(int index, ItemStack item, Inventory top, Inventory bottom, int topSize) {
        if (index < topSize) top.setItem(index, item);
        else bottom.setItem(index - topSize, item);
    }
}
