package net.tfminecraft.cooking.carve;



import org.bukkit.Location;

import org.bukkit.Sound;

import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;

import org.bukkit.util.Vector;



import me.Plugins.TLibs.TLibs;

import net.tfminecraft.cooking.cache.ItemCache;

import net.tfminecraft.cooking.item.FoodItem;

import java.util.List;



import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionContext;

import net.tfminecraft.cooking.quality.CompositionQualityResolver;

import net.tfminecraft.cooking.quality.CompositionResult;

import net.tfminecraft.cooking.utils.FoodParser;

import net.tfminecraft.cooking.utils.InventoryAdder;

import net.tfminecraft.cooking.utils.ItemBuilder;

import net.tfminecraft.cooking.utils.ItemRef;

import net.tfminecraft.cooking.utils.ItemUpdater;

import net.tfminecraft.furniture.Furniture;

import net.tfminecraft.furniture.PlacedSlot;



public final class CarveHandler {



    private CarveHandler() {}



    public static boolean tryCarve(Player player, Furniture furniture, PlacedSlot slot, ItemStack tool) {

        if (player == null || furniture == null || slot == null || tool == null) return false;

        if (ItemCache.carveTool == null || !TLibs.getItemAPI().getChecker().checkItemWithPath(tool, ItemCache.carveTool)) {

            return false;

        }



        ItemStack stack = slot.getCurrentItem();

        if (stack == null) return false;



        FoodItem roast = FoodItem.fromItem(stack);

        if (roast == null) return false;

        CarvableRoastUtils.readCarveState(roast, stack);

        if (!CarvableRoastUtils.isCarvable(roast)) return false;



        CarveSequence sequence = CarvableRoastUtils.getSequence(roast);

        CarveCut cut = sequence.getCut(roast.getCarveNextIndex());

        if (cut == null) return false;



        ItemStack reward = buildReward(player, roast, stack, cut, sequence);

        if (reward == null) return false;



        Location dropLoc = furniture.getLoc();

        giveReward(player, dropLoc, reward);



        CarvableRoastUtils.advanceAfterCarve(roast);

        dropLoc.getWorld().playSound(dropLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);



        if (roast.getCarveRemaining() <= 0) {

            slot.clearModel();

            furniture.removeActiveSlot(slot.getId());

            return true;

        }



        ItemStack updated = ItemUpdater.applyItemUpdate(stack, roast, furniture.getId());

        if (updated == null) return true;

        CarvableRoastUtils.writeCarveState(updated, roast);

        slot.setCurrentItem(updated);

        slot.applyDisplayData(CarvableRoastUtils.getStageModelData(roast).getDisplayData(furniture.getId()));

        return true;

    }



    private static ItemStack buildReward(Player player, FoodItem roast, ItemStack stack, CarveCut cut, CarveSequence sequence) {

        if (cut.isItemCut()) {

            ItemStack reward = ItemRef.resolve(cut.getItemRef());

            if (reward == null) return null;

            reward.setAmount(cut.getAmount());

            return reward;

        }



        if (!cut.isFoodCut()) return null;



        FoodParser.Result parsed = FoodParser.parse(cut.getOutput());

        if (parsed == null || parsed.template == null) return null;



        FoodItem partTemplate = new FoodItem(parsed.template);

        CarvableRoastUtils.copyInheritedTracks(roast, partTemplate);

        if (roast.hasBaseOverride()) {
            int edible = CarvableRoastUtils.countFoodCuts(sequence);
            partTemplate.setBaseFood(CarvableRoastUtils.portionFood(roast.getBaseFood(), edible));
            partTemplate.setBaseNutrition(roast.getBaseNutrition());
        }

        CompositionResult composed = CompositionQualityResolver.compose(player, List.of(roast), CompositionContext.CARVE);
        CompositionApplier.apply(partTemplate, composed);

        return ItemBuilder.buildSingleWithQuality(partTemplate, stack, composed.getFinalQuality());

    }



    private static void giveReward(Player player, Location dropLoc, ItemStack reward) {

        ItemStack leftover = InventoryAdder.addItem(player, reward);

        if (leftover == null) {

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

        } else {

            dropLoc.getWorld().dropItemNaturally(dropLoc, leftover)

                    .setVelocity(new Vector(Math.random() * 0.2 - 0.1, 0.1, Math.random() * 0.2 - 0.1));

        }

    }



    public static boolean tryCarveFirstCarvableSlot(Player player, Furniture furniture, ItemStack tool) {

        for (PlacedSlot active : furniture.getActiveSlots().values()) {

            if (tryCarve(player, furniture, active, tool)) {

                return true;

            }

        }

        return false;

    }

}


