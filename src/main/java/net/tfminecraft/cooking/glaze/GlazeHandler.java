package net.tfminecraft.cooking.glaze;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.FoodReturns;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/**
 * Right-clicking the roast on a fire pit or a meat hook with a glaze (honey, glazes.yml) glazes it: the roast gets
 * the glaze's track and one glaze is used, its empty bottle coming back. The roast's slot only takes roasts, so with
 * honey in hand the click reaches the furniture rather than a slot.
 */
public final class GlazeHandler implements Listener {
    private static final String CONTENT = "content";

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent e) {
        Furniture f = e.getFurniture();
        if (!FurnitureCache.isFirePit(f) && !FurnitureCache.isMeatHook(f)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return;
        FoodItem glazeFood = FoodItem.fromItem(IngredientConverter.convertIfNeeded(p, hand));
        GlazeConfig.Glaze glaze = GlazeConfig.glazeFor(glazeFood);
        if (glaze == null) return;
        e.setCancelled(true);

        PlacedSlot slot = f.getActiveSlot(CONTENT).orElse(null);
        ItemStack stack = slot == null ? null : slot.getCurrentItem();
        FoodItem roast = stack == null ? null : FoodItem.fromItem(stack);
        if (roast == null) {
            p.sendMessage("§cThere's no roast here to glaze.");
            return;
        }
        // The hook only rewrites a resting roast when it looks different, so bring its numbers up to now first.
        roast.updateAge();
        if (!GlazeConfig.isRoast(roast)) {
            p.sendMessage("§cOnly roasts can be glazed.");
            return;
        }
        CarvableRoastUtils.readCarveState(roast, stack);
        if (GlazeConfig.glazeOn(roast) != null) {
            p.sendMessage("§cThat roast is already glazed.");
            return;
        }
        if ("rotten".equals(CarvableRoastUtils.resolveCookTag(roast))) {
            p.sendMessage("§cThat roast has gone off.");
            return;
        }
        TagTrack track = TrackLoader.getByString(glaze.track());
        if (track == null) {
            p.sendMessage("§cThat glaze's track '" + glaze.track() + "' isn't in tags.yml.");
            return;
        }
        track = new TagTrack(track);
        track.setValue(0);
        roast.addOrModifyTrack(track);

        ItemStack updated = ItemUpdater.applyItemUpdate(stack, roast, f.getId());
        if (updated == null || updated.getType().isAir()) return;
        CarvableRoastUtils.writeCarveState(updated, roast);
        slot.setCurrentItem(updated);
        ModelData model = CarvableRoastUtils.getStageModelData(roast);
        if (model != null) slot.applyDisplayData(model.getDisplayData(f.getId()));
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);

        FoodReturns.useOne(p, glazeFood);
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.BLOCK_HONEY_BLOCK_PLACE, 1f, 1f);
    }
}
