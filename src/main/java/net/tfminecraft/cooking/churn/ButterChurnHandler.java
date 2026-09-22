package net.tfminecraft.cooking.churn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.BucketItems;
import net.tfminecraft.cooking.cup.MilkBucketConverter;
import net.tfminecraft.cooking.cup.MilkBucketSnapshot;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.QualityUtils;
import net.tfminecraft.events.FurnitureBreakEvent;
import net.tfminecraft.events.FurnitureInteractEvent;
import net.tfminecraft.events.FurniturePlaceEvent;
import net.tfminecraft.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.furniture.SlotDefinition;

public final class ButterChurnHandler implements Listener {

    private static final String MILK_SLOT = "input_1";
    private static final String STICK_SLOT = "stick";

    private final Map<UUID, Long> churnCooldown = new HashMap<>();

    public void resumeLoadedChurns() {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            resumeChurn(furniture);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> resumeChurnsInChunk(chunk), 1L);
    }

    @EventHandler
    public void onPlace(FurniturePlaceEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isButterChurn(furniture)) {
            return;
        }
        ensureStick(furniture);
    }

    @EventHandler
    public void onMilkAdd(FurnitureSlotItemAddEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isButterChurn(furniture)) {
            return;
        }

        Player player = event.getPlayer();
        if (!event.getSlot().getId().equals(MILK_SLOT)) {
            return;
        }
        if (furniture.hasActiveSlot(MILK_SLOT)) {
            event.setCancelled(true);
            player.sendMessage("The churn already has milk.");
            return;
        }

        ItemStack milkItem = MilkBucketConverter.convertIfNeeded(player, event.getItem());
        event.setItem(milkItem);

        ButterChurnState.setMilkSnapshot(
                furniture,
                MilkBucketSnapshot.readQuality(player, milkItem),
                MilkBucketSnapshot.readDairyFreshness(player, milkItem),
                MilkBucketSnapshot.readOrigin(player, milkItem));
        FoodItem milkFood = FoodItem.fromItem(milkItem);
        if (milkFood != null) {
            ButterChurnState.setMilkLineage(furniture, milkFood.getLineage());
        }
        ButterChurnState.setChurnCount(furniture, 0);
        ButterChurnAging.start(furniture);
        markDirty(furniture);

        player.getInventory().setItemInMainHand(BucketItems.empty());
        player.updateInventory();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
    }

    @EventHandler
    public void onTake(FurnitureSlotItemTakeEvent event) {
        Furniture furniture = event.getFurniture();
        if (FurnitureCache.isButterChurn(furniture)) {
            event.setCancelled(true);
            return;
        }
        if (FurnitureCache.isButterPlate(furniture)) {
            ItemStack slotItem = event.getItem();
            FoodItem butter = slotItem == null ? null : FoodItem.fromItem(slotItem);
            if (butter == null || !"butter".equalsIgnoreCase(butter.getId())) {
                event.setCancelled(true);
                return;
            }
            ItemStack hand = ItemUpdater.applyItemUpdate(slotItem.clone(), butter, null);
            if (hand != null) {
                event.setItem(hand);
            }
        }
    }

    @EventHandler
    public void onPlateAdd(FurnitureSlotItemAddEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isButterPlate(furniture)) {
            return;
        }
        FoodItem butter = FoodItem.fromItem(event.getItem());
        if (butter == null || !"butter".equalsIgnoreCase(butter.getId())) {
            event.setCancelled(true);
            return;
        }
        ItemStack display = ItemUpdater.applyItemUpdate(event.getItem().clone(), butter, furniture.getId());
        if (display != null) {
            event.setItem(display);
        }
    }

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isButterChurn(furniture)) {
            return;
        }

        ensureStick(furniture);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;
        boolean hasMilk = furniture.hasActiveSlot(MILK_SLOT);
        int required = ItemCache.butterChurnCount;

        if (furniture.getActiveSlots().size() == 1 && emptyHand) {
            furniture.getActiveSlot(STICK_SLOT).ifPresent(PlacedSlot::clearModel);
            return;
        }

        if (!hasMilk) {
            return;
        }

        Furniture carried = InteractibleFurniture.getInstance().getFurnitureManager().getByCarrier(player);
        if (carried != null && FurnitureCache.isButterPlate(carried) && carried.getType() != null) {
            handlePlateCollection(furniture, carried, player, required);
            return;
        }

        if (!emptyHand) {
            if (tryAddSalt(furniture, player, hand)) {
                return;
            }
            if (tryAddSpice(furniture, player, hand)) {
                return;
            }
            return;
        }

        handleChurn(furniture, player, required);
    }

    @EventHandler
    public void onBreak(FurnitureBreakEvent event) {
        Furniture furniture = event.getFurniture();
        if (FurnitureCache.isButterChurn(furniture)) {
            churnCooldown.remove(furniture.getEntityId());
            ButterChurnAging.stop(furniture);
        }
    }

    private void handleChurn(Furniture furniture, Player player, int required) {
        PlacedSlot stick = furniture.getActiveSlot(STICK_SLOT).orElse(null);
        if (stick == null) {
            return;
        }

        ItemDisplay display = (ItemDisplay) Bukkit.getEntity(stick.getDisplayStandId());
        if (display == null) {
            return;
        }

        if (isOnCooldown(furniture)) {
            return;
        }

        int count = ButterChurnState.getChurnCount(furniture);
        if (count >= required) {
            player.sendMessage("Use a butter plate to collect.");
            return;
        }

        playStickChurnAnimation(display);
        startCooldown(furniture, 20 * 50L);
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_COW_MILK, 1f, 1f);

        count = ButterChurnState.incrementChurnCount(furniture);
        markDirty(furniture);

        if (count >= required) {
            player.sendMessage("Butter ready - use a butter plate to collect.");
        } else {
            player.sendMessage("Churn " + count + "/" + required);
        }
    }

    private void handlePlateCollection(Furniture churn, Furniture plate, Player player, int required) {
        int count = ButterChurnState.getChurnCount(churn);
        if (count < required) {
            player.sendMessage("Churn the milk " + required + " times first (" + count + "/" + required + ")");
            return;
        }

        int milkQuality = ButterChurnState.getMilkQuality(churn);
        int dairyFreshness = ButterChurnState.getDairyFreshness(churn);
        ItemStack butter = ButterItems.fromMilkSnapshot(
                player,
                milkQuality,
                dairyFreshness,
                ButterChurnState.getMilkOrigin(churn),
                ButterChurnState.hasSalt(churn),
                ButterChurnState.getSaltQuality(churn),
                ButterChurnState.getSpiceOrigin(churn),
                ButterChurnState.getSpiceQuality(churn),
                ButterChurnState.getSpiceFreshness(churn),
                ButterChurnState.getMilkLineage(churn),
                ButterChurnState.getSaltLineage(churn),
                ButterChurnState.getSpiceLineage(churn));
        if (butter == null) {
            player.sendMessage("Could not create butter.");
            return;
        }

        FoodItem butterItem = FoodItem.fromItem(butter);
        ItemDisplay churnDisplay = (ItemDisplay) Bukkit.getEntity(churn.getEntityId());
        for (SlotDefinition def : plate.getType().getSlots().values()) {
            PlacedSlot slot = plate.getOrCreatePlacedSlot(def.getId());
            ItemStack display = butterItem == null
                    ? butter.clone()
                    : ItemUpdater.applyItemUpdate(butter.clone(), butterItem, plate.getId());
            slot.forceModel(display);
            if (churnDisplay != null) {
                slot.followParentTransform(churnDisplay);
            }
        }

        churn.getLoc().getWorld().playSound(churn.getLoc(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
        churn.removeActiveSlot(MILK_SLOT);
        ButterChurnAging.stop(churn);
        ButterChurnState.clear(churn);
        markDirty(churn);
        markDirty(plate);
    }

    private boolean tryAddSalt(Furniture furniture, Player player, ItemStack hand) {
        if (!ChurnExtras.isChurnSalt(hand)) {
            return false;
        }
        if (ButterChurnState.hasSalt(furniture)) {
            player.sendMessage("Already added salt.");
            return true;
        }

        FoodItem salt = FoodItem.fromItem(hand);
        if (salt == null) {
            return false;
        }

        ButterChurnState.setSalt(furniture, QualityUtils.clamp(salt.getQualityMin()));
        ButterChurnState.setSaltLineage(furniture, salt.getLineage());
        consumeOne(player, hand);
        markDirty(furniture);
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1.2f);
        player.sendMessage("Added salt.");
        return true;
    }

    private boolean tryAddSpice(Furniture furniture, Player player, ItemStack hand) {
        if (!ChurnExtras.isChurnSpice(hand)) {
            return false;
        }
        if (ButterChurnState.hasSpice(furniture)) {
            player.sendMessage("Already added spice.");
            return true;
        }

        FoodItem spice = FoodItem.fromItem(hand);
        if (spice == null || spice.getOrigin() == null || spice.getOrigin().isBlank()) {
            return false;
        }

        ButterChurnState.setSpice(
                furniture,
                spice.getOrigin(),
                QualityUtils.clamp(spice.getQualityMin()),
                ChurnExtras.readFreshness(spice));
        ButterChurnState.setSpiceLineage(furniture, spice.getLineage());
        consumeOne(player, hand);
        markDirty(furniture);
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
        player.sendMessage("Added " + spice.getOrigin() + ".");
        return true;
    }

    private static void consumeOne(Player player, ItemStack hand) {
        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        player.updateInventory();
    }

    private void resumeChurn(Furniture furniture) {
        if (!FurnitureCache.isButterChurn(furniture)) {
            return;
        }
        ensureStick(furniture);
        if (furniture.hasActiveSlot(MILK_SLOT)) {
            ButterChurnState.tickAge(furniture);
            ButterChurnAging.start(furniture);
            markDirty(furniture);
            return;
        }
        if (ButterChurnState.getChurnCount(furniture) > 0) {
            ButterChurnAging.stop(furniture);
            ButterChurnState.clear(furniture);
            markDirty(furniture);
        }
    }

    private void resumeChurnsInChunk(Chunk chunk) {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            if (!furniture.getLoc().getChunk().equals(chunk)) {
                continue;
            }
            resumeChurn(furniture);
        }
    }

    private void ensureStick(Furniture furniture) {
        if (!FurnitureCache.isButterChurn(furniture)) {
            return;
        }
        if (furniture.getType() == null || furniture.getType().getSlot(STICK_SLOT) == null) {
            return;
        }
        PlacedSlot stick = furniture.getActiveSlot(STICK_SLOT).orElse(null);
        if (stick == null || stick.getCurrentItem() == null) {
            furniture.getOrCreatePlacedSlot(STICK_SLOT).forceModel(new ItemStack(Material.STICK, 1));
        }
    }

    private boolean isOnCooldown(Furniture furniture) {
        Long until = churnCooldown.get(furniture.getEntityId());
        if (until == null) {
            return false;
        }
        return System.currentTimeMillis() < until;
    }

    private void startCooldown(Furniture furniture, long millis) {
        churnCooldown.put(furniture.getEntityId(), System.currentTimeMillis() + millis);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }

    private void playStickChurnAnimation(ItemDisplay display) {
        final Transformation start = display.getTransformation();
        final Transformation target = new Transformation(
                new Vector3f(
                        start.getTranslation().x(),
                        start.getTranslation().y() - 0.2f,
                        start.getTranslation().z()
                ),
                start.getLeftRotation(),
                start.getScale(),
                start.getRightRotation()
        );

        new BukkitRunnable() {
            int tick = 0;
            final int duration = 6;

            @Override
            public void run() {
                if (display.isDead()) {
                    cancel();
                    return;
                }

                float t = tick / (float) duration;
                if (tick > duration) {
                    t = 1f - (t - 1f);
                }

                Vector3f a = start.getTranslation();
                Vector3f b = target.getTranslation();
                Vector3f interpolated = new Vector3f(
                        a.x() + (b.x() - a.x()) * t,
                        a.y() + (b.y() - a.y()) * t,
                        a.z() + (b.z() - a.z()) * t
                );

                display.setTransformation(new Transformation(
                        interpolated,
                        start.getLeftRotation(),
                        start.getScale(),
                        start.getRightRotation()
                ));

                tick++;
                if (tick > duration * 2) {
                    cancel();
                }
            }
        }.runTaskTimer(Cooking.plugin, 0L, 1L);
    }
}
