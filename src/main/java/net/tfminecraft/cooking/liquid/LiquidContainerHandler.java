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
import net.tfminecraft.cooking.brewery.BrewLiquid;
import net.tfminecraft.cooking.condiment.CondimentConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.HeldStack;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
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
        BrewLiquid brew = BrewLiquid.from(hand);
        if (brew != null) {
            handleWine(furniture, player, brew);
            return;
        }
        if (hand.getType() == Material.GLASS_BOTTLE && FoodItem.fromItem(hand) == null) {
            handleVinegar(furniture, player);
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
        if (ItemCache.blocksPerBucket <= 0) {
            player.sendMessage("Could not empty this bucket into the container.");
            return;
        }
        if (!LiquidContainerState.canAccept(furniture, LiquidContainerState.TYPE_WATER)) {
            player.sendMessage("This container already has " + held(furniture) + ".");
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
        if (ItemCache.blocksPerBucket <= 0) {
            player.sendMessage("Could not empty this bucket into the container.");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack converted = MilkBucketConverter.convertIfNeeded(player, hand);
        if (converted != hand) {
            player.getInventory().setItemInMainHand(converted);
            player.updateInventory();
        }
        if (!LiquidContainerState.canAccept(furniture, LiquidContainerState.TYPE_MILK)) {
            player.sendMessage("This container already has " + held(furniture) + ".");
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
        if (LiquidContainerState.TYPE_WINE.equalsIgnoreCase(LiquidContainerState.getType(furniture))) {
            player.sendMessage("Use an empty glass bottle for the vinegar.");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();

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

        if (cup == null || cup.getType().isAir()) {
            player.sendMessage("Could not fill a cup.");
            return;
        }

        boolean lastCup = hand.getAmount() <= 1;
        if (!lastCup && !hasStorageSlot(player)) {
            player.sendMessage("You have no room to carry another cup.");
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

    /** Wine or cider poured in sours into vinegar (condiments.yml); each bottle is one level and restarts the clock. */
    private void handleWine(Furniture furniture, Player player, BrewLiquid brew) {
        String origin = CondimentConfig.vinegarOrigin(brew.name());
        if (origin == null) {
            player.sendMessage("Only wine or cider sours into vinegar.");
            return;
        }
        boolean sameWine = LiquidContainerState.isEmpty(furniture)
                || origin.equals(LiquidContainerState.getVinegarOrigin(furniture));
        if (!LiquidContainerState.canAccept(furniture, LiquidContainerState.TYPE_WINE) || !sameWine) {
            player.sendMessage("This container already has " + held(furniture) + ".");
            return;
        }
        if (LiquidContainerState.getBlocks(furniture) >= ItemCache.maxBlocks) {
            player.sendMessage("The container is full.");
            return;
        }
        if (LiquidContainerState.isEmpty(furniture)) LiquidContainerState.clear(furniture);
        LiquidContainerState.setType(furniture, LiquidContainerState.TYPE_WINE);
        LiquidContainerState.setSouring(furniture, origin, brew.stars(),
                System.currentTimeMillis() + CondimentConfig.vinegarMillis());
        LiquidContainerState.addBlocks(furniture, 1, ItemCache.maxBlocks);
        HeldStack.useOneKeepStack(player, new ItemStack(Material.GLASS_BOTTLE));
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BOTTLE_EMPTY, 1f, 1f);
        LiquidContainerDisplay.sync(furniture);
        markDirty(furniture);
    }

    /** An empty glass bottle draws one level of vinegar once the wine has soured. */
    private void handleVinegar(Furniture furniture, Player player) {
        if (LiquidContainerState.isEmpty(furniture)
                || !LiquidContainerState.TYPE_WINE.equalsIgnoreCase(LiquidContainerState.getType(furniture))) {
            player.sendMessage("Pour in wine or cider and let it sour to get vinegar.");
            return;
        }
        long left = LiquidContainerState.getSouredAt(furniture) - System.currentTimeMillis();
        if (left > 0) {
            long minutes = Math.max(1, (left + 59_999) / 60_000);
            player.sendMessage("The wine is still souring: about " + minutes + " minute" + (minutes == 1 ? "" : "s") + " left.");
            return;
        }
        FoodParser.Result parsed = FoodParser.parse(CondimentConfig.vinegarOutput()
                .replace("{origin}", LiquidContainerState.getVinegarOrigin(furniture)));
        ItemStack vinegar = parsed == null || parsed.template == null ? null
                : ItemBuilder.buildSingleWithQuality(parsed.template, null, LiquidContainerState.getVinegarQuality(furniture));
        if (vinegar == null) {
            player.sendMessage("Could not fill a bottle.");
            return;
        }
        LiquidContainerState.removeBlock(furniture);
        HeldStack.useOneKeepStack(player, vinegar);
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        LiquidContainerDisplay.sync(furniture);
        markDirty(furniture);
    }

    private static String held(Furniture furniture) {
        String type = LiquidContainerState.getType(furniture);
        return type == null ? "something else" : type;
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
