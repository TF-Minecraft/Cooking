package net.tfminecraft.cooking.cooking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.WarmthUtils;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public class FryingReference extends CookingReference {

    private FoodItem butterExtra;

    public FryingReference(Furniture f, Method m) {
        super(f, m);
    }

    @Override
    public void tick() {
        super.tick();
        if (!HeatSources.stationHasHeat(f)) {
            if (secondaries.containsKey("butter")) {
                clearButterSecondary();
            }
            return;
        }
        handleCookingSlots();
        handleParticlesAndDanger();
    }

    private void handleParticlesAndDanger() {
        if (!slots.isEmpty() || !secondaries.isEmpty()) {
            if (method == Method.FRYING_PAN) {
                f.getLoc().getWorld().spawnParticle(
                        Particle.CAMPFIRE_COSY_SMOKE,
                        f.getLoc(),
                        0,
                        0, 0.1, 0,
                        0.05
                );
                if (!secondaries.containsKey("butter")) danger++;
                if (danger >= 10) burnAllSlots();
            }
        }
    }

    private void handleCookingSlots() {
        for (Map.Entry<String, FoodItem> entry : slots.entrySet()) {
            String slot = entry.getKey();
            FoodItem item = entry.getValue();
            if (!item.canBeCooked()) continue;
            if (!item.getCookData().tick()) continue;
            applySlotUpdate(slot, item);
        }
    }

    private void burnAllSlots() {
        for (Map.Entry<String, FoodItem> entry : slots.entrySet()) {
            String slot = entry.getKey();
            FoodItem item = entry.getValue();
            if (!item.canBeCooked()) continue;
            item.getCookData().setCurrentTime(item.getCookData().getParameters().get(method).getBurnTime());
            super.applySlotUpdate(slot, item);
        }
    }

    @Override
    public void interact(FurnitureInteractEvent e) {
        Player p = e.getPlayer();
        if (isEmpty() && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!ItemCache.isButter(item) || secondaries.containsKey("butter")) {
            return;
        }
        if (!HeatSources.stationHasHeat(f)) {
            p.sendMessage("§cThe pan needs heat from an oven below.");
            return;
        }
        if (f.getType() == null || f.getType().getSlot("butter") == null) {
            return;
        }

        ItemStack converted = IngredientConverter.convertIfNeeded(p, item);
        if (converted != item) {
            p.getInventory().setItemInMainHand(converted);
            item = converted;
        }

        FoodItem butterFi = FoodItem.fromItem(item);
        if (butterFi == null) {
            p.sendMessage("§cThat butter can't be used for frying.");
            return;
        }

        ItemStack display = ItemUpdater.applyItemUpdate(item.clone(), butterFi, f.getId());
        display.setAmount(1);
        item.setAmount(item.getAmount() - 1);
        butterExtra = new FoodItem(butterFi);

        PlacedSlot slot = f.getOrCreatePlacedSlot("butter");
        slot.forceModel(display);

        secondaries.put("butter", 30);
        p.swingMainHand();
        danger = 0;
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.BLOCK_LAVA_EXTINGUISH, 1f, 1f);
        e.setCancelled(true);
    }

    @Override
    public void rebuildFromFurniture() {
        butterExtra = null;
        super.rebuildFromFurniture();
        restoreButterExtraFromSlot();
    }

    private void restoreButterExtraFromSlot() {
        if (!secondaries.containsKey("butter") || !f.hasActiveSlot("butter")) {
            return;
        }
        f.getActiveSlot("butter").ifPresent(placedSlot -> {
            ItemStack stack = placedSlot.getCurrentItem();
            if (stack == null || stack.getType().isAir() || !ItemCache.isButter(stack)) {
                return;
            }
            FoodItem fi = FoodItem.fromItem(stack);
            if (fi != null) {
                butterExtra = new FoodItem(fi);
                ItemStack display = ItemUpdater.applyItemUpdate(stack.clone(), butterExtra, f.getId());
                if (display != null) {
                    placedSlot.forceModel(display);
                }
            }
        });
    }

    @Override
    public void removeSecondary(Map.Entry<String, Integer> entry) {
        if ("butter".equals(entry.getKey())) {
            butterExtra = null;
        }
        super.removeSecondary(entry);
    }

    @Override
    public void clear() {
        butterExtra = null;
        super.clear();
    }

    @Override
    public void slotRemove(FurnitureSlotItemTakeEvent e) {
        FoodItem fi = slots.remove(e.getSlot().getId());
        ItemStack item = e.getItem();
        if (fi == null) {
            return;
        }
        if (!fi.canBeCooked()) {
            e.setCancelled(true);
            return;
        }
        CookData data = fi.getCookData();
        if (!data.hasMethod(method)) {
            e.setCancelled(true);
            return;
        }
        if (data.getCurrentTime() < 5) {
            slots.put(e.getSlot().getId(), fi);
            return;
        }
        if (!WarmthUtils.applyHot(fi)) {
            slots.put(e.getSlot().getId(), fi);
            return;
        }

        if (butterExtra != null) {
            List<FoodItem> inputs = new ArrayList<>();
            inputs.add(fi);
            inputs.add(butterExtra);
            CompositionResult composed = CompositionQualityResolver.compose(
                    e.getPlayer(), inputs, CompositionContext.FRYING_PAN);
            CompositionApplier.apply(fi, composed);
        }

        item = ItemUpdater.applyItemUpdate(item, fi, f.getId());
        if (item == null) {
            slots.put(e.getSlot().getId(), fi);
            return;
        }

        clearButterSecondary();
        e.setItem(item);
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new net.tfminecraft.cooking.events.DishCookedEvent(e.getPlayer(), item, "station"));
    }

    private void clearButterSecondary() {
        butterExtra = null;
        if (secondaries.containsKey("butter")) {
            removeSecondary(Map.entry("butter", secondaries.remove("butter")));
        }
    }
}
