package net.tfminecraft.cooking.cup;

import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.nutrition.NutritionService;
import net.tfminecraft.cooking.utils.InventoryAdder;

public final class DrinkConsumeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMilkBucket(PlayerItemConsumeEvent event) {
        ItemStack consumed = event.getItem();
        if (consumed == null || consumed.getType() != Material.MILK_BUCKET) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        FoodItem food = FoodItem.fromItem(consumed);
        if (food != null && "milk_bucket".equalsIgnoreCase(food.getId())) {
            food.updateAge();
            NutritionService.tryApplyEat(player, food);
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        emptyDrunkBucket(player, event.getHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack consumed = event.getItem();
        if (consumed == null) {
            return;
        }

        boolean water = ItemCache.isCupOfWater(consumed);
        boolean milk = ItemCache.isCupOfMilk(consumed);
        if (!water && !milk) {
            return;
        }

        Player player = event.getPlayer();

        if (milk) {
            FoodItem food = FoodItem.fromItem(consumed);
            if (food != null) {
                food.updateAge();
                NutritionService.tryApplyEat(player, food);
            }
        }

        int amountBefore = consumed.getAmount();
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        int slot = hand == EquipmentSlot.OFF_HAND ? -1 : player.getInventory().getHeldItemSlot();
        Bukkit.getScheduler().runTask(Cooking.plugin, () -> replaceWithEmptyCup(player, amountBefore, slot));
    }

    /**
     * Swaps the glass bottle vanilla leaves in the slot the cup was drunk from for an empty cup.
     * Only that slot, and only if it still holds the bottle: switching slots within the tick
     * must neither leave the bottle and add a cup, nor overwrite whatever is held instead.
     */
    static void replaceWithEmptyCup(Player player, int amountBefore, int slot) {
        replaceWithEmptyCup(player, amountBefore, slot, CupItems::emptyCup);
    }

    static void replaceWithEmptyCup(Player player, int amountBefore, int slot, Supplier<ItemStack> emptyCup) {
        if (amountBefore > 1) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack left = slot < 0 ? inv.getItemInOffHand() : inv.getItem(slot);
        if (left != null && left.getType() != Material.GLASS_BOTTLE && left.getType() != Material.AIR) {
            return;
        }
        ItemStack empty = emptyCup.get();
        if (slot < 0) {
            inv.setItemInOffHand(empty);
        } else {
            inv.setItem(slot, empty);
        }
    }

    private static void emptyDrunkBucket(Player player, EquipmentSlot hand) {
        PlayerInventory inv = player.getInventory();
        EquipmentSlot slot = hand != null ? hand : EquipmentSlot.HAND;
        ItemStack used = inv.getItem(slot);
        if (used == null || used.getType() != Material.MILK_BUCKET) {
            ItemStack main = inv.getItemInMainHand();
            ItemStack off = inv.getItemInOffHand();
            if (main != null && main.getType() == Material.MILK_BUCKET) {
                slot = EquipmentSlot.HAND;
                used = main;
            } else if (off != null && off.getType() == Material.MILK_BUCKET) {
                slot = EquipmentSlot.OFF_HAND;
                used = off;
            } else {
                return;
            }
        }
        int amount = used.getAmount();
        if (amount > 1) {
            used.setAmount(amount - 1);
            ItemStack leftover = InventoryAdder.addItem(player, BucketItems.empty());
            if (leftover != null && leftover.getAmount() > 0 && player.getWorld() != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            return;
        }
        inv.setItem(slot, BucketItems.empty());
    }
}
