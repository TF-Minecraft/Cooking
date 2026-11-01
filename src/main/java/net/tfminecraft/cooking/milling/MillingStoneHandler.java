package net.tfminecraft.cooking.milling;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

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

        List<MillingRecipe> recipes = MillingRecipeRegistry.getAllForFurniture(furniture.getId());
        if (recipes.isEmpty()) {
            return;
        }

        MillingStoneStage stage = resolveStage(furniture);
        MillingRecipe recipe = loadedRecipe(furniture);
        syncIfNeeded(furniture, stage, recipe);

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType() == Material.AIR;

        if (!emptyHand) {
            if (stage == MillingStoneStage.EMPTY) {
                handleLoad(furniture, recipes, player, hand, event);
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

        MillingRecipe recipe = loadedRecipe(furniture);
        MillingStoneStage stage = resolveStage(furniture);
        if (stage != MillingStoneStage.EMPTY && (recipe == null
                || (stage == MillingStoneStage.READY && !dropFlour(furniture, recipe))
                || (stage == MillingStoneStage.LOADED && !dropWheatRefund(furniture, recipe)))) {
            event.setCancelled(true);
            org.bukkit.Bukkit.getLogger().warning("[Cooking] Cannot return contents from milling stone '"
                    + furniture.getId() + "' (recipe=" + (recipe == null ? "missing" : recipe.getId())
                    + "). Break cancelled to preserve its contents.");
            if (event.getPlayer() != null) event.getPlayer().sendMessage(
                    "§cThe mill's contents could not be returned. Please ask staff to check its recipe.");
            return;
        }

        MillingStoneAnimation.clearAnimating(furniture);
        MillingStoneDisplay.clearAll(furniture);
        MillingStoneState.clear(furniture);
    }

    private void handleLoad(Furniture furniture, List<MillingRecipe> recipes, Player player, ItemStack hand,
            FurnitureInteractEvent event) {
        MillingRecipe recipe = null;
        InputMatch match = null;
        for (MillingRecipe candidate : recipes) {
            match = resolveInput(hand, candidate, player);
            if (match.accepted) {
                recipe = candidate;
                break;
            }
        }
        if (recipe == null) {
            event.setCancelled(true);
            player.sendMessage("§cThat doesn't go in the mill.");
            return;
        }

        if (hand.getAmount() < recipe.getInputCount()) {
            event.setCancelled(true);
            player.sendMessage("§cYou need " + recipe.getInputCount() + " " + recipe.getInputName()
                    + " to fill the mill.");
            return;
        }

        hand.setAmount(hand.getAmount() - recipe.getInputCount());
        MillingStoneState.setOutputQuality(furniture, match.quality);
        MillingStoneState.setLineage(furniture, match.lineage);
        MillingStoneState.setRecipe(furniture, recipe.getId());
        MillingStoneState.setStage(furniture, MillingStoneStage.LOADED);
        MillingStoneDisplay.syncVisuals(furniture, MillingStoneStage.LOADED, recipe);

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
            MillingStoneDisplay.syncVisuals(furniture, MillingStoneStage.READY, recipe);
            markDirty(furniture);
        });
    }

    private void handleTake(Furniture furniture, MillingRecipe recipe, Player player) {
        ItemStack flour = buildFlour(recipe, MillingStoneState.getOutputQuality(furniture),
                MillingStoneState.getLineage(furniture));
        if (flour == null || flour.getType().isAir()) {
            player.sendMessage("§cFailed to create " + recipe.getOutputFood().replace('_', ' ') + ".");
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

    private boolean dropFlour(Furniture furniture, MillingRecipe recipe) {
        ItemStack flour = buildFlour(recipe, MillingStoneState.getOutputQuality(furniture),
                MillingStoneState.getLineage(furniture));
        if (flour == null || flour.getType().isAir()) {
            return false;
        }
        furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), flour);
        return true;
    }

    private boolean dropWheatRefund(Furniture furniture, MillingRecipe recipe) {
        ItemStack wheat = buildWheat(recipe, MillingStoneState.getOutputQuality(furniture),
                MillingStoneState.getLineage(furniture));
        if (wheat == null || wheat.getType().isAir()) {
            return false;
        }
        wheat.setAmount(recipe.getInputCount());
        furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), wheat);
        return true;
    }

    private static ItemStack buildFlour(MillingRecipe recipe, int quality, IngredientLineage lineage) {
        FoodItem template = FoodLoader.getByString(recipe.getOutputFood());
        if (template == null) {
            return null;
        }
        FoodItem flour = new FoodItem(template);
        flour.setCategory(recipe.getCategory());
        flour.setOrigin(recipe.getOrigin());
        flour.setLineage(lineage == null || lineage.isEmpty() ? IngredientLineage.ofMain(recipe.getOrigin()) : lineage);
        ItemStack built = ItemBuilder.buildSingleWithQuality(flour, quality);
        if (built != null && !built.getType().isAir()) built.setAmount(recipe.getOutputAmount());
        return built;
    }

    /**
     * What a loaded stone gives back: the recipe's refund item (sugar cane), its input food path rebuilt (roasted cocoa
     * beans), or its input as food (wheat).
     */
    private static ItemStack buildWheat(MillingRecipe recipe, int quality, IngredientLineage lineage) {
        if (recipe.getRefund() != null) {
            ItemStack refund = TLibs.getItemAPI().getCreator().getItemFromPath(recipe.getRefund());
            return refund == null ? null : refund.clone();
        }
        if (recipe.getRefundPath() != null) {
            FoodParser.Result parsed = FoodParser.parse(recipe.getRefundPath());
            if (parsed == null || parsed.template == null) return null;
            FoodItem input = new FoodItem(parsed.template);
            input.setOrigin(recipe.getOrigin());
            input.setLineage(lineage == null || lineage.isEmpty() ? IngredientLineage.ofMain(recipe.getOrigin()) : lineage);
            return ItemBuilder.buildSingleWithQuality(input, quality);
        }
        FoodItem template = FoodLoader.getByString(recipe.getRefundFood());
        if (template == null) {
            return null;
        }
        FoodItem wheat = new FoodItem(template);
        wheat.setCategory(MillingRecipe.DEFAULT_CATEGORY);
        wheat.setOrigin(recipe.getOrigin());
        wheat.setLineage(lineage == null || lineage.isEmpty() ? IngredientLineage.ofMain(recipe.getOrigin()) : lineage);
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

    /** The recipe this stone was loaded with, or its first recipe (an empty stone, or one loaded before ids were saved). */
    private static MillingRecipe loadedRecipe(Furniture furniture) {
        return MillingRecipeRegistry.getForFurniture(furniture.getId(), MillingStoneState.getRecipe(furniture));
    }

    private static void syncIfNeeded(Furniture furniture, MillingStoneStage stage, MillingRecipe recipe) {
        if (stage == MillingStoneStage.EMPTY) {
            MillingStoneDisplay.showTop(furniture);
            return;
        }
        if (stage == MillingStoneStage.LOADED && !furniture.hasActiveSlot(MillingStoneSlots.WHEAT[0])) {
            MillingStoneDisplay.syncVisuals(furniture, stage, recipe);
        } else if (stage == MillingStoneStage.READY && !furniture.hasActiveSlot(MillingStoneSlots.FLOUR)) {
            MillingStoneDisplay.syncVisuals(furniture, stage, recipe);
        } else {
            MillingStoneDisplay.showTop(furniture);
        }
    }

    private static InputMatch resolveInput(ItemStack hand, MillingRecipe recipe, Player player) {
        FoodItem foodItem = FoodItem.fromItem(hand);
        if (foodItem != null) {
            if (recipe.getInputFood() != null
                    && foodItem.getId().equalsIgnoreCase(recipe.getInputFood())) {
                return new InputMatch(true, foodItem.getQualityMin(), lineageOf(foodItem, recipe));
            }
            if (recipe.getInputMatcher() != null
                    && TLibs.getItemAPI().getChecker().checkItemWithPath(hand, recipe.getInputMatcher())) {
                return new InputMatch(true, foodItem.getQualityMin(), lineageOf(foodItem, recipe));
            }
        }

        if (recipe.getVanillaFallback() != null
                && TLibs.getItemAPI().getChecker().checkItemWithPath(hand, recipe.getVanillaFallback())) {
            FoodItem wheatTemplate = FoodLoader.getByString(recipe.getRefundFood());
            int quality = wheatTemplate != null
                    ? OriginQualityResolver.resolve(player, wheatTemplate)
                    : OriginQualityResolver.resolve(player, null);
            return new InputMatch(true, quality, IngredientLineage.ofMain(recipe.getOrigin()));
        }

        return new InputMatch(false, 1, IngredientLineage.empty());
    }

    private static IngredientLineage lineageOf(FoodItem foodItem, MillingRecipe recipe) {
        if (foodItem.getLineage() != null && !foodItem.getLineage().isEmpty()) {
            return foodItem.getLineage();
        }
        String origin = foodItem.getOrigin();
        if (origin == null || origin.isBlank()) {
            origin = recipe.getOrigin();
        }
        return IngredientLineage.ofMain(origin);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }

    private record InputMatch(boolean accepted, int quality, IngredientLineage lineage) {}
}
