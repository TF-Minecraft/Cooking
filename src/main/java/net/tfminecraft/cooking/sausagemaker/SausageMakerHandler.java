package net.tfminecraft.cooking.sausagemaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class SausageMakerHandler implements Listener {

    public static final String[] MEAT_SLOTS = { "meat_1", "meat_2", "meat_3" };

    private final Map<UUID, Long> crankCooldown = new HashMap<>();

    @EventHandler
    public void onMeatAdd(FurnitureSlotItemAddEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isSausageMaker(furniture)) {
            return;
        }

        String slotId = event.getSlot().getId();
        if (!isMeatSlot(slotId)) {
            return;
        }

        FoodItem food = FoodItem.fromItem(event.getItem());
        if (!SausageMeatRules.isMeat(food)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cOnly meat can go in the sausage maker.");
        }
    }

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isSausageMaker(furniture)) {
            return;
        }

        if (SausageMakerAnimation.isAnimating(furniture)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cWait…");
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;
        FoodItem heldFood = emptyHand ? null : FoodItem.fromItem(hand);

        if (SausageMeatRules.isMeat(heldFood)) {
            event.setCancelled(true);
            if (!tryFillFirstEmpty(furniture, player, hand)) {
                player.sendMessage("§cThe sausage maker is full.");
            }
            return;
        }

        if (!emptyHand && heldFood != null) {
            event.setCancelled(true);
            player.sendMessage("§cOnly meat can go in the sausage maker.");
            return;
        }

        if (emptyHand) {
            if (tryTakeLast(furniture, player)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!hasAllMeats(furniture)) {
            event.setCancelled(true);
            player.sendMessage("§cNeed 3 meats.");
            return;
        }

        if (!hasPaper(player)) {
            event.setCancelled(true);
            player.sendMessage("§cHold paper to casing.");
            return;
        }

        if (isOnCooldown(furniture)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        player.swingMainHand();
        startCrank(furniture, player);
    }

    @EventHandler
    public void onBreak(FurnitureBreakEvent event) {
        Furniture furniture = event.getFurniture();
        if (FurnitureCache.isSausageMaker(furniture)) {
            crankCooldown.remove(furniture.getEntityId());
            SausageMakerAnimation.clearAnimating(furniture);
        }
    }

    private void startCrank(Furniture furniture, Player player) {
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.BLOCK_WET_GRASS_BREAK, 1f, 0.8f);
        furniture.getLoc().getWorld().spawnParticle(
                Particle.ITEM,
                furniture.getLoc().clone().add(0, 0.2, 0),
                8,
                0.15, 0.05, 0.15,
                0.02,
                new ItemStack(Material.PORKCHOP));

        SausageMakerAnimation.playWobble(furniture, () -> finishCrank(furniture, player));
        startCooldown(furniture, ItemCache.sausageMakerCooldownTicks);
    }

    private void finishCrank(Furniture furniture, Player player) {
        List<FoodItem> meats = collectMeats(furniture);
        if (meats.size() < MEAT_SLOTS.length) {
            return;
        }

        ItemStack chain = SausageItems.fromMeats(player, meats);
        if (chain == null) {
            player.sendMessage("§cCould not make sausage chain.");
            return;
        }

        ItemStack leftover = InventoryAdder.addItem(player, chain);
        if (leftover != null) {
            furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), leftover);
        }

        clearMeatSlots(furniture);
        consumePaper(player);
        markDirty(furniture);
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
    }

    private static boolean tryFillFirstEmpty(Furniture furniture, Player player, ItemStack hand) {
        String slotId = findFirstEmpty(furniture);
        if (slotId == null) {
            return false;
        }

        ItemStack toPlace = hand.clone();
        toPlace.setAmount(1);
        PlacedSlot slot = furniture.getOrCreatePlacedSlot(slotId);
        slot.setCurrentItem(toPlace);
        slot.forceModel(toPlace);
        hand.setAmount(hand.getAmount() - 1);
        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        markDirty(furniture);
        return true;
    }

    private static boolean tryTakeLast(Furniture furniture, Player player) {
        String slotId = findLastOccupied(furniture);
        if (slotId == null) {
            return false;
        }

        PlacedSlot slot = furniture.getActiveSlot(slotId).orElse(null);
        if (slot == null) {
            return false;
        }
        ItemStack item = slot.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }

        ItemStack leftover = InventoryAdder.addItem(player, item.clone());
        if (leftover != null) {
            furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), leftover);
        }
        slot.clearModel();
        furniture.removeActiveSlot(slotId);
        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        markDirty(furniture);
        return true;
    }

    private static String findFirstEmpty(Furniture furniture) {
        for (String slotId : MEAT_SLOTS) {
            if (!isOccupied(furniture, slotId)) {
                return slotId;
            }
        }
        return null;
    }

    private static String findLastOccupied(Furniture furniture) {
        for (int i = MEAT_SLOTS.length - 1; i >= 0; i--) {
            if (isOccupied(furniture, MEAT_SLOTS[i])) {
                return MEAT_SLOTS[i];
            }
        }
        return null;
    }

    private static boolean isOccupied(Furniture furniture, String slotId) {
        if (!furniture.hasActiveSlot(slotId)) {
            return false;
        }
        ItemStack stack = furniture.getActiveSlot(slotId).map(PlacedSlot::getCurrentItem).orElse(null);
        return SausageMeatRules.isMeat(stack == null ? null : FoodItem.fromItem(stack));
    }

    private static List<FoodItem> collectMeats(Furniture furniture) {
        List<FoodItem> meats = new ArrayList<>();
        for (String slotId : MEAT_SLOTS) {
            PlacedSlot slot = furniture.getActiveSlot(slotId).orElse(null);
            if (slot == null) {
                continue;
            }
            ItemStack stack = slot.getCurrentItem();
            FoodItem food = stack == null ? null : FoodItem.fromItem(stack);
            if (SausageMeatRules.isMeat(food)) {
                meats.add(food);
            }
        }
        return meats;
    }

    private static void clearMeatSlots(Furniture furniture) {
        for (String slotId : MEAT_SLOTS) {
            if (!furniture.hasActiveSlot(slotId)) {
                continue;
            }
            furniture.getActiveSlot(slotId).ifPresent(slot -> {
                slot.clearModel();
                furniture.removeActiveSlot(slotId);
            });
        }
    }

    private static boolean hasAllMeats(Furniture furniture) {
        for (String slotId : MEAT_SLOTS) {
            if (!furniture.hasActiveSlot(slotId)) {
                return false;
            }
            ItemStack stack = furniture.getActiveSlot(slotId).map(PlacedSlot::getCurrentItem).orElse(null);
            if (!SausageMeatRules.isMeat(stack == null ? null : FoodItem.fromItem(stack))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasPaper(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();
        return (main != null && main.getType() == Material.PAPER)
                || (off != null && off.getType() == Material.PAPER);
    }

    private static void consumePaper(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (main != null && main.getType() == Material.PAPER) {
            main.setAmount(main.getAmount() - 1);
            return;
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == Material.PAPER) {
            off.setAmount(off.getAmount() - 1);
        }
    }

    private static boolean isMeatSlot(String slotId) {
        for (String meatSlot : MEAT_SLOTS) {
            if (meatSlot.equals(slotId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnCooldown(Furniture furniture) {
        Long until = crankCooldown.get(furniture.getEntityId());
        if (until == null) {
            return false;
        }
        return System.currentTimeMillis() < until;
    }

    private void startCooldown(Furniture furniture, int ticks) {
        crankCooldown.put(furniture.getEntityId(), System.currentTimeMillis() + ticks * 50L);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
