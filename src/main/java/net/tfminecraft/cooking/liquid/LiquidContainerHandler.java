package net.tfminecraft.cooking.liquid;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.BucketItems;
import net.tfminecraft.cooking.cup.CupItems;
import net.tfminecraft.cooking.cup.MilkBucketConverter;
import net.tfminecraft.cooking.cup.MilkBucketSnapshot;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class LiquidContainerHandler implements Listener {

    public void resumeLoadedContainers() {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            resumeContainer(furniture);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> resumeContainersInChunk(chunk), 1L);
    }

    @EventHandler
    public void onPlace(FurniturePlaceEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isLiquidContainer(furniture)) {
            return;
        }
        LiquidContainerState.clear(furniture);
        LiquidContainerDisplay.clearAll(furniture);
        markDirty(furniture);
    }

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isLiquidContainer(furniture)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("Hold a bucket or empty cup to use the container.");
            return;
        }

        if (hand.getType() == Material.WATER_BUCKET) {
            handleWaterBucket(furniture, player);
            return;
        }
        if (ItemCache.isMilkBucket(hand)) {
            handleMilkBucket(furniture, player);
            return;
        }
        if (ItemCache.isEmptyCup(hand)) {
            handleDispense(furniture, player);
            return;
        }

        player.sendMessage("Hold a water bucket, milk bucket, or empty cup.");
    }

    @EventHandler
    public void onTake(FurnitureSlotItemTakeEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isLiquidContainer(furniture)) {
            return;
        }
        String slotId = event.getSlot().getId();
        if (slotId != null && slotId.startsWith("liquid_")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(FurnitureBreakEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isLiquidContainer(furniture)) {
            return;
        }
        LiquidContainerAging.stop(furniture);
    }

    private void handleWaterBucket(Furniture furniture, Player player) {
        if (!LiquidContainerState.canAccept(furniture, LiquidContainerState.TYPE_WATER)) {
            player.sendMessage("This container already has milk.");
            return;
        }
        if (LiquidContainerState.getBlocks(furniture) >= ItemCache.maxBlocks) {
            player.sendMessage("The container is full.");
            return;
        }

        LiquidContainerState.setType(furniture, LiquidContainerState.TYPE_WATER);
        LiquidContainerState.addBlocks(furniture, ItemCache.blocksPerBucket, ItemCache.maxBlocks);
        player.getInventory().setItemInMainHand(BucketItems.empty());
        player.updateInventory();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);

        LiquidContainerDisplay.sync(furniture);
        LiquidContainerAging.stop(furniture);
        markDirty(furniture);
    }

    private void handleMilkBucket(Furniture furniture, Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack converted = MilkBucketConverter.convertIfNeeded(player, hand);
        if (converted != hand) {
            player.getInventory().setItemInMainHand(converted);
            player.updateInventory();
        }
        if (!LiquidContainerState.canAccept(furniture, LiquidContainerState.TYPE_MILK)) {
            player.sendMessage("This container already has water.");
            return;
        }
        if (LiquidContainerState.getBlocks(furniture) >= ItemCache.maxBlocks) {
            player.sendMessage("The container is full.");
            return;
        }

        boolean firstMilk = LiquidContainerState.isEmpty(furniture)
                || LiquidContainerState.getType(furniture) == null;
        if (firstMilk) {
            int quality = MilkBucketSnapshot.readQuality(player, converted);
            int freshness = MilkBucketSnapshot.readDairyFreshness(player, converted);
            String origin = MilkBucketSnapshot.readOrigin(player, converted);
            LiquidContainerState.setMilkSnapshot(furniture, quality, freshness, origin);
        } else {
            LiquidContainerState.tickAge(furniture);
        }

        LiquidContainerState.setType(furniture, LiquidContainerState.TYPE_MILK);
        LiquidContainerState.addBlocks(furniture, ItemCache.blocksPerBucket, ItemCache.maxBlocks);
        player.getInventory().setItemInMainHand(BucketItems.empty());
        player.updateInventory();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);

        LiquidContainerDisplay.sync(furniture);
        LiquidContainerAging.start(furniture);
        markDirty(furniture);
    }

    private void handleDispense(Furniture furniture, Player player) {
        if (LiquidContainerState.isEmpty(furniture)) {
            player.sendMessage("The container is empty.");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }

        LiquidContainerState.tickAge(furniture);
        String type = LiquidContainerState.getType(furniture);
        ItemStack cup;
        if (LiquidContainerState.TYPE_MILK.equalsIgnoreCase(type)) {
            cup = CupItems.cupOfMilk(
                    player,
                    LiquidContainerState.getMilkQuality(furniture),
                    LiquidContainerState.getDairyFreshness(furniture),
                    LiquidContainerState.getMilkOrigin(furniture));
        } else {
            cup = CupItems.cupOfWater();
        }

        if (cup == null) {
            player.sendMessage("Could not fill a cup.");
            return;
        }

        boolean lastCup = hand.getAmount() <= 1;
        if (!lastCup && !hasStorageSlot(player)) {
            player.sendMessage("Need a free inventory slot.");
            return;
        }

        LiquidContainerState.removeBlock(furniture);
        if (lastCup) {
            player.getInventory().setItemInMainHand(cup);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack leftover = InventoryAdder.addItem(player, cup);
            if (leftover != null) {
                furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), leftover);
            }
        }
        player.updateInventory();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);

        LiquidContainerDisplay.sync(furniture);
        if (LiquidContainerState.isEmpty(furniture)
                || !LiquidContainerState.TYPE_MILK.equalsIgnoreCase(LiquidContainerState.getType(furniture))) {
            LiquidContainerAging.stop(furniture);
        }
        markDirty(furniture);
    }

    private static boolean hasStorageSlot(Player player) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private void resumeContainer(Furniture furniture) {
        if (!FurnitureCache.isLiquidContainer(furniture)) {
            return;
        }
        LiquidContainerState.tickAge(furniture);
        LiquidContainerDisplay.sync(furniture);
        if (LiquidContainerState.TYPE_MILK.equalsIgnoreCase(LiquidContainerState.getType(furniture))
                && !LiquidContainerState.isEmpty(furniture)) {
            LiquidContainerAging.start(furniture);
        }
    }

    private void resumeContainersInChunk(Chunk chunk) {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            if (!furniture.getLoc().getChunk().equals(chunk)) {
                continue;
            }
            resumeContainer(furniture);
        }
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
