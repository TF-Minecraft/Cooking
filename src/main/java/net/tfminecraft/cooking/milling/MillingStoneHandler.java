package net.tfminecraft.cooking.milling;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.events.FurnitureBreakEvent;
import net.tfminecraft.events.FurnitureInteractEvent;
import net.tfminecraft.events.FurniturePlaceEvent;
import net.tfminecraft.furniture.Furniture;

public final class MillingStoneHandler implements Listener {

    @EventHandler
    public void onPlace(FurniturePlaceEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isMillingStone(furniture)) {
            return;
        }
        MillingStoneDisplay.showTop(furniture);
        markDirty(furniture);
    }

    @EventHandler
    public void onInteract(FurnitureInteractEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isMillingStone(furniture)) {
            return;
        }

        MillingRecipe recipe = MillingRecipeRegistry.getByFurnitureId(furniture.getId());
        if (recipe == null) {
            return;
        }

        MillingStoneStage stage = resolveStage(furniture);
        syncIfNeeded(furniture, stage);

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;

        if (!emptyHand) {
            if (stage == MillingStoneStage.EMPTY) {
                handleLoad(furniture, recipe, player, hand, event);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        switch (stage) {
            case LOADED -> handleMill(furniture, recipe, player);
            case READY -> handleTake(furniture, recipe, player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onBreak(FurnitureBreakEvent event) {
        Furniture furniture = event.getFurniture();
        if (!FurnitureCache.isMillingStone(furniture)) {
            return;
        }

        MillingRecipe recipe = MillingRecipeRegistry.getByFurnitureId(furniture.getId());
        MillingStoneAnimation.clearAnimating(furniture);

        MillingStoneStage stage = resolveStage(furniture);
        if (recipe != null) {
            if (stage == MillingStoneStage.READY) {
                dropFlour(furniture, recipe);
            } else if (stage == MillingStoneStage.LOADED) {
                dropWheatRefund(furniture, recipe);
            }
        }

        MillingStoneDisplay.clearAll(furniture);
        MillingStoneState.clear(furniture);
    }

    private void handleLoad(Furniture furniture, MillingRecipe recipe, Player player, ItemStack hand,
            FurnitureInteractEvent event) {
        if (hand.getAmount() < recipe.getInputCount()) {
            event.setCancelled(true);
            player.sendMessage("§cYou need " + recipe.getInputCount() + " wheat to fill the mill.");
            return;
        }

        InputMatch match = resolveInput(hand, recipe, player);
        if (!match.accepted) {
            event.setCancelled(true);
            player.sendMessage("§cThat doesn't go in the mill.");
            return;
        }

        hand.setAmount(hand.getAmount() - recipe.getInputCount());
        MillingStoneState.setOutputQuality(furniture, match.quality);
        MillingStoneState.setLineage(furniture, match.lineage);
        MillingStoneState.setStage(furniture, MillingStoneStage.LOADED);
        MillingStoneDisplay.syncVisuals(furniture, MillingStoneStage.LOADED);

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_CROP_PLANT, 1f, 1f);
        markDirty(furniture);
        event.setCancelled(true);
    }

    private void handleMill(Furniture furniture, MillingRecipe recipe, Player player) {
        if (MillingStoneAnimation.isAnimating(furniture)) {
            return;
        }

        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
        MillingStoneAnimation.playMill(furniture, recipe.getRevolutions(), recipe.getDurationTicks(), () -> {
            MillingStoneState.setStage(furniture, MillingStoneStage.READY);
            MillingStoneDisplay.syncVisuals(furniture, MillingStoneStage.READY);
            markDirty(furniture);
        });
    }

    private void handleTake(Furniture furniture, MillingRecipe recipe, Player player) {
        ItemStack flour = buildFlour(recipe, MillingStoneState.getOutputQuality(furniture),
                MillingStoneState.getLineage(furniture));
        if (flour == null) {
            player.sendMessage("§cFailed to create flour.");
            return;
        }

        ItemStack leftover = InventoryAdder.addItem(player, flour);
        if (leftover != null) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        MillingStoneDisplay.clearFlour(furniture);
        MillingStoneState.clear(furniture);
        player.swingMainHand();
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        markDirty(furniture);
    }

    private void dropFlour(Furniture furniture, MillingRecipe recipe) {
        ItemStack flour = buildFlour(recipe, MillingStoneState.getOutputQuality(furniture),
                MillingStoneState.getLineage(furniture));
        if (flour != null) {
            furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), flour);
        }
    }

    private void dropWheatRefund(Furniture furniture, MillingRecipe recipe) {
        ItemStack wheat = buildWheat(MillingStoneState.getOutputQuality(furniture),
                MillingStoneState.getLineage(furniture));
        if (wheat == null) {
            return;
        }
        wheat.setAmount(recipe.getInputCount());
        furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), wheat);
    }

    private static ItemStack buildFlour(MillingRecipe recipe, int quality, IngredientLineage lineage) {
        FoodItem template = FoodLoader.getByString(recipe.getOutputFood());
        if (template == null) {
            return null;
        }
        FoodItem flour = new FoodItem(template);
        flour.setCategory("grain");
        flour.setOrigin("Wheat");
        flour.setLineage(lineage == null || lineage.isEmpty() ? IngredientLineage.ofMain("Wheat") : lineage);
        return ItemBuilder.buildSingleWithQuality(flour, quality);
    }

    private static ItemStack buildWheat(int quality, IngredientLineage lineage) {
        FoodItem template = FoodLoader.getByString("wheat");
        if (template == null) {
            return null;
        }
        FoodItem wheat = new FoodItem(template);
        wheat.setCategory("grain");
        wheat.setOrigin("Wheat");
        wheat.setLineage(lineage == null || lineage.isEmpty() ? IngredientLineage.ofMain("Wheat") : lineage);
        return ItemBuilder.buildSingleWithQuality(wheat, quality);
    }

    private static MillingStoneStage resolveStage(Furniture furniture) {
        MillingStoneStage stored = MillingStoneState.getStage(furniture);
        MillingStoneStage inferred = MillingStoneStage.fromFurniture(furniture);
        if (stored == MillingStoneStage.EMPTY && inferred != MillingStoneStage.EMPTY) {
            MillingStoneState.setStage(furniture, inferred);
            return inferred;
        }
        return stored;
    }

    private static void syncIfNeeded(Furniture furniture, MillingStoneStage stage) {
        if (stage == MillingStoneStage.EMPTY) {
            MillingStoneDisplay.showTop(furniture);
            return;
        }
        if (stage == MillingStoneStage.LOADED && !furniture.hasActiveSlot(MillingStoneSlots.WHEAT[0])) {
            MillingStoneDisplay.syncVisuals(furniture, stage);
        } else if (stage == MillingStoneStage.READY && !furniture.hasActiveSlot(MillingStoneSlots.FLOUR)) {
            MillingStoneDisplay.syncVisuals(furniture, stage);
        } else {
            MillingStoneDisplay.showTop(furniture);
        }
    }

    private static InputMatch resolveInput(ItemStack hand, MillingRecipe recipe, Player player) {
        FoodItem foodItem = FoodItem.fromItem(hand);
        if (foodItem != null) {
            if (recipe.getInputFood() != null
                    && foodItem.getId().equalsIgnoreCase(recipe.getInputFood())) {
                return new InputMatch(true, foodItem.getQualityMin(), lineageOf(foodItem));
            }
            if (recipe.getInputMatcher() != null
                    && TLibs.getItemAPI().getChecker().checkItemWithPath(hand, recipe.getInputMatcher())) {
                return new InputMatch(true, foodItem.getQualityMin(), lineageOf(foodItem));
            }
        }

        if (recipe.getVanillaFallback() != null
                && TLibs.getItemAPI().getChecker().checkItemWithPath(hand, recipe.getVanillaFallback())) {
            FoodItem wheatTemplate = FoodLoader.getByString("wheat");
            int quality = wheatTemplate != null
                    ? OriginQualityResolver.resolve(player, wheatTemplate)
                    : OriginQualityResolver.resolve(player, null);
            return new InputMatch(true, quality, IngredientLineage.ofMain("Wheat"));
        }

        return new InputMatch(false, 1, IngredientLineage.empty());
    }

    private static IngredientLineage lineageOf(FoodItem foodItem) {
        if (foodItem.getLineage() != null && !foodItem.getLineage().isEmpty()) {
            return foodItem.getLineage();
        }
        String origin = foodItem.getOrigin();
        if (origin == null || origin.isBlank()) {
            origin = "Wheat";
        }
        return IngredientLineage.ofMain(origin);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }

    private record InputMatch(boolean accepted, int quality, IngredientLineage lineage) {}
}
