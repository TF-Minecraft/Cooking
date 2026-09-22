package net.tfminecraft.cooking.mixing;



import org.bukkit.Material;

import org.bukkit.Particle;

import org.bukkit.Sound;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;

import org.bukkit.event.Listener;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.InteractibleFurniture;

import net.tfminecraft.cooking.cache.FurnitureCache;

import net.tfminecraft.cooking.cache.ItemCache;

import net.tfminecraft.cooking.cup.CupItems;

import net.tfminecraft.cooking.item.FoodItem;

import net.tfminecraft.cooking.item.IngredientLineage;

import net.tfminecraft.cooking.item.tag.TagTrack;

import net.tfminecraft.cooking.loader.FoodLoader;

import net.tfminecraft.cooking.loader.TrackLoader;

import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.DoughMixinRules;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.QualityUtils;

import net.tfminecraft.events.FurnitureBreakEvent;

import net.tfminecraft.events.FurnitureInteractEvent;

import net.tfminecraft.events.FurnitureSlotItemTakeEvent;

import net.tfminecraft.furniture.Furniture;



public class MixingBowlHandler implements Listener {


    @EventHandler

    public void onInteract(FurnitureInteractEvent event) {

        Furniture furniture = event.getFurniture();

        if (!FurnitureCache.isMixingBowl(furniture)) {

            return;

        }



        Player player = event.getPlayer();

        ItemStack hand = player.getInventory().getItemInMainHand();

        boolean emptyHand = hand == null || hand.getType() == Material.AIR;



        MixingBowlStage stage = getStage(furniture);



        if (emptyHand) {

            if (stage == MixingBowlStage.HAS_YEAST) {

                handleStir(furniture, player, event);

            }

            return;

        }



        if (stage == MixingBowlStage.DOUGH_READY) {

            event.setCancelled(true);

            return;

        }



        if (stage == MixingBowlStage.HAS_YEAST) {

            handleMixinAdd(furniture, player, event, hand);

            return;

        }



        handleIngredientAdd(furniture, player, event, stage, hand);

    }



    @EventHandler

    public void onTake(FurnitureSlotItemTakeEvent event) {

        Furniture furniture = event.getFurniture();

        if (!FurnitureCache.isMixingBowl(furniture)) {

            return;

        }

        if (!event.getSlot().getId().equals(MixingBowlSlots.DOUGH)) {

            return;

        }

        if (getStage(furniture) != MixingBowlStage.DOUGH_READY) {

            event.setCancelled(true);

            event.getPlayer().sendMessage("§cKnead the dough first.");

            return;

        }



        ItemStack output = buildDough(furniture, event.getPlayer());

        if (output == null) {

            event.setCancelled(true);

            return;

        }



        event.setItem(output);

        MixingBowlState.clear(furniture);

        MixingBowlAnimation.clearAnimating(furniture);

        markDirty(furniture);

    }



    @EventHandler

    public void onBreak(FurnitureBreakEvent event) {

        Furniture furniture = event.getFurniture();

        if (!FurnitureCache.isMixingBowl(furniture)) {

            return;

        }

        MixingBowlAnimation.clearAnimating(furniture);

        if (getStage(furniture) == MixingBowlStage.DOUGH_READY) {

            ItemStack dough = buildDough(furniture, null);

            if (dough != null) {

                furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), dough);

            }

            MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.DOUGH);

        }

        MixingBowlState.clear(furniture);

    }



    private MixingBowlStage getStage(Furniture furniture) {

        return MixingBowlState.getStage(furniture);

    }



    private void handleIngredientAdd(Furniture furniture, Player player, FurnitureInteractEvent event,

            MixingBowlStage stage, ItemStack hand) {

        String expectedSlot = stage.nextIngredientSlot();

        if (expectedSlot == null) {

            return;

        }



        if (furniture.hasActiveSlot(expectedSlot)) {

            return;

        }



        if (!ItemCache.matchesMixingInput(expectedSlot, hand)) {

            sendWrongItemFeedback(player, stage);

            event.setCancelled(true);

            return;

        }

        if (MixingBowlSlots.FLOUR.equals(expectedSlot) || MixingBowlSlots.YEAST.equals(expectedSlot)) {

            if (FoodItem.fromItem(hand) == null) {

                player.sendMessage("§cUse converted cooking ingredients.");

                event.setCancelled(true);

                return;

            }

        }



        boolean waterCup = MixingBowlSlots.WATER.equals(expectedSlot) && ItemCache.isCupOfWater(hand);

        hand.setAmount(hand.getAmount() - 1);

        if (waterCup) {
            ItemStack empty = CupItems.emptyCup();
            if (hand.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(empty);
            } else {
                ItemStack leftover = InventoryAdder.addItem(player, empty);
                if (leftover != null) {
                    furniture.getLoc().getWorld().dropItemNaturally(furniture.getLoc(), leftover);
                }
            }
        }

        if (!MixingBowlDisplay.showLayer(furniture, expectedSlot)) {

            return;

        }



        storeIngredientQuality(furniture, expectedSlot, hand);

        MixingBowlState.setStage(furniture, stage.advance());

        player.swingMainHand();

        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);

        markDirty(furniture);

        event.setCancelled(true);

    }



    private void handleMixinAdd(Furniture furniture, Player player, FurnitureInteractEvent event, ItemStack hand) {

        ItemStack converted = IngredientConverter.convertIfNeeded(player, hand);
        if (converted != hand) {
            player.getInventory().setItemInMainHand(converted);
            hand = converted;
        }

        FoodItem foodItem = FoodItem.fromItem(hand);

        if (foodItem == null) {

            player.sendMessage("§cThat doesn't go in the bowl.");

            event.setCancelled(true);

            return;

        }



        String category = foodItem.getCategory();

        int quality = QualityUtils.clamp(foodItem.getQualityMin());



        if (DoughMixinRules.isSweetenerCategory(category)) {

            if (!DoughMixinRules.canAcceptSugar(MixingBowlState.hasSugar(furniture), foodItem, player)) {

                event.setCancelled(true);

                return;

            }

            hand.setAmount(hand.getAmount() - 1);

            MixingBowlState.setHasSugar(furniture, true);

            MixingBowlState.setSugarQuality(furniture, quality);
            MixingBowlState.setSugarFreshness(furniture, readFreshness(foodItem));
            MixingBowlState.setSugarOrigin(furniture, originOr(foodItem, "Sugar"));
            MixingBowlState.setSugarLineage(furniture, foodItem.getLineage());

        } else if (DoughMixinRules.isFruitCategory(category)) {

            if (!DoughMixinRules.canAcceptFruit(MixingBowlState.getFruitOrigins(furniture), foodItem, player)) {

                event.setCancelled(true);

                return;

            }

            String origin = foodItem.getOrigin();

            if (origin == null || origin.isBlank()) {

                player.sendMessage("§cThat doesn't go in the bowl.");

                event.setCancelled(true);

                return;

            }

            hand.setAmount(hand.getAmount() - 1);

            MixingBowlState.addFruit(furniture, origin, quality, readFreshness(foodItem), foodItem.getLineage());

        } else {

            player.sendMessage("§cThat doesn't go in the bowl.");

            event.setCancelled(true);

            return;

        }



        event.setCancelled(true);

        player.swingMainHand();

        furniture.getLoc().getWorld().playSound(furniture.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);

        markDirty(furniture);

    }



    private void storeIngredientQuality(Furniture furniture, String slotId, ItemStack hand) {

        FoodItem foodItem = FoodItem.fromItem(hand);

        if (foodItem == null) {

            return;

        }

        int quality = QualityUtils.clamp(foodItem.getQualityMin());

        int freshness = readFreshness(foodItem);

        if (MixingBowlSlots.FLOUR.equals(slotId)) {
            MixingBowlState.setFlourQuality(furniture, quality);
            MixingBowlState.setFlourFreshness(furniture, freshness);
            MixingBowlState.setFlourOrigin(furniture, originOr(foodItem, "Wheat"));
            MixingBowlState.setFlourLineage(furniture, foodItem.getLineage());
        } else if (MixingBowlSlots.YEAST.equals(slotId)) {
            MixingBowlState.setYeastQuality(furniture, quality);
            MixingBowlState.setYeastFreshness(furniture, freshness);
            MixingBowlState.setYeastOrigin(furniture, originOr(foodItem, "Yeast"));
            MixingBowlState.setYeastLineage(furniture, foodItem.getLineage());
        }

    }



    private void handleStir(Furniture furniture, Player player, FurnitureInteractEvent event) {

        if (MixingBowlAnimation.isAnimating(furniture)) {

            event.setCancelled(true);

            return;

        }



        int count = MixingBowlState.getMixCount(furniture);

        if (count >= ItemCache.mixingStirCount) {

            return;

        }



        event.setCancelled(true);

        player.swingMainHand();

        furniture.getLoc().getWorld().playSound(furniture.getLoc(), "block.loom.use", 1f, 1f);

        furniture.getLoc().getWorld().spawnParticle(

                Particle.CLOUD,

                furniture.getLoc().clone().add(0, 0.15, 0),

                6,

                0.12, 0.05, 0.12,

                0.01);

        MixingBowlAnimation.playStir(furniture);



        count++;

        MixingBowlState.setMixCount(furniture, count);

        markDirty(furniture);

        if (count >= ItemCache.mixingStirCount) {

            completeDough(furniture);

        }

    }



    private void completeDough(Furniture furniture) {

        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.FLOUR);

        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.WATER);

        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.YEAST);

        MixingBowlDisplay.showLayer(furniture, MixingBowlSlots.DOUGH);

        MixingBowlState.setStage(furniture, MixingBowlStage.DOUGH_READY);

        markDirty(furniture);

    }



    private static ItemStack buildDough(Furniture furniture, Player player) {

        FoodItem template = FoodLoader.getByString("dough");

        if (template == null) {

            return null;

        }

        List<FoodItem> inputs = new ArrayList<>();

        Integer flourQuality = MixingBowlState.getFlourQuality(furniture);
        Integer flourFreshness = MixingBowlState.getFlourFreshness(furniture);
        if (flourQuality != null) {
            inputs.add(ingredientStub(
                    "grain",
                    flourQuality,
                    flourFreshness,
                    MixingBowlState.getFlourOrigin(furniture),
                    MixingBowlState.getFlourLineage(furniture)));
        }

        Integer yeastQuality = MixingBowlState.getYeastQuality(furniture);
        Integer yeastFreshness = MixingBowlState.getYeastFreshness(furniture);
        if (yeastQuality != null) {
            inputs.add(ingredientStub(
                    "ingredient",
                    yeastQuality,
                    yeastFreshness,
                    MixingBowlState.getYeastOrigin(furniture),
                    MixingBowlState.getYeastLineage(furniture)));
        }

        if (MixingBowlState.hasSugar(furniture)) {
            Integer sugarQuality = MixingBowlState.getSugarQuality(furniture);
            Integer sugarFreshness = MixingBowlState.getSugarFreshness(furniture);
            if (sugarQuality != null) {
                inputs.add(ingredientStub(
                        "sweetener",
                        sugarQuality,
                        sugarFreshness,
                        MixingBowlState.getSugarOrigin(furniture),
                        MixingBowlState.getSugarLineage(furniture)));
            }
        }

        List<Integer> fruitQualities = MixingBowlState.getFruitQualities(furniture);
        List<Integer> fruitFreshness = MixingBowlState.getFruitFreshness(furniture);
        List<String> fruitOrigins = MixingBowlState.getFruitOrigins(furniture);
        List<IngredientLineage> fruitLineages = MixingBowlState.getFruitLineages(furniture);
        for (int i = 0; i < fruitQualities.size(); i++) {
            Integer freshness = i < fruitFreshness.size() ? fruitFreshness.get(i) : null;
            String origin = i < fruitOrigins.size() ? fruitOrigins.get(i) : null;
            IngredientLineage lineage = i < fruitLineages.size() ? fruitLineages.get(i) : IngredientLineage.empty();
            inputs.add(ingredientStub("fruit", fruitQualities.get(i), freshness, origin, lineage));
        }

        CompositionResult composed = CompositionQualityResolver.compose(player, inputs, CompositionContext.MIXING_BOWL);
        int quality = composed.getFinalQuality();



        boolean hasSugar = MixingBowlState.hasSugar(furniture);



        FoodItem dough = new FoodItem(template);

        dough.setCategory("grain");

        dough.setOrigin("Wheat");

        for (String origin : fruitOrigins) {

            dough.addIngredient(origin);

        }

        DoughMixinRules.applyDoughTags(dough, hasSugar, fruitOrigins);
        CompositionApplier.apply(dough, composed);

        return ItemBuilder.buildComposedWithQuality(dough, quality);

    }

    private static String originOr(FoodItem foodItem, String fallback) {
        String origin = foodItem.getOrigin();
        if (origin == null || origin.isBlank()) {
            return fallback;
        }
        return origin;
    }

    private static int readFreshness(FoodItem foodItem) {
        TagTrack track = foodItem.getTagTrack("freshness");
        return track == null ? 0 : track.getValue();
    }

    private static FoodItem ingredientStub(
            String category,
            int quality,
            Integer freshness,
            String origin,
            IngredientLineage lineage) {
        FoodItem template = FoodLoader.getByString("dough");
        FoodItem stub = new FoodItem(template);
        stub.setCategory(category);
        if (origin != null && !origin.isBlank()) {
            stub.setOrigin(origin);
        }
        if (lineage != null && !lineage.isEmpty()) {
            stub.setLineage(lineage);
        }
        stub.setQualityRange(quality, quality);
        if (freshness != null && freshness >= 0) {
            TagTrack fresh = new TagTrack(TrackLoader.getByString("freshness"));
            fresh.setValue(freshness);
            stub.addOrModifyTrack(fresh);
        }
        return stub;
    }



    private void markDirty(Furniture furniture) {

        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);

    }



    private void sendWrongItemFeedback(Player player, MixingBowlStage stage) {

        switch (stage) {

            case EMPTY -> player.sendMessage("§cAdd flour first.");

            case HAS_FLOUR -> player.sendMessage("§cAdd water next.");

            case HAS_WATER -> player.sendMessage("§cAdd yeast next.");

            default -> player.sendMessage("§cThat doesn't go in the bowl.");

        }

    }

}


