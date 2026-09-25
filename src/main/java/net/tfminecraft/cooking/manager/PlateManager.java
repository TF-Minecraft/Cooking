package net.tfminecraft.cooking.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.CategoryDictionary;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.Encoder;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

public class PlateManager implements Listener{

    public void start() {
        tickCycle();
    }

    public void tickCycle() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for(Map.Entry<UUID, Furniture> entry : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().entrySet()) {
                    Furniture f = entry.getValue();
                    if(FurnitureCache.isPlate(f)) {
                        update(f);
                    }
                }
            }
        }.runTaskTimer(Cooking.plugin, 20L, 20L);
    }

    public void update(Furniture f) {
        for(PlacedSlot slot : f.getActiveSlots().values()) {
            if(slot.getId().contains("display")) continue;
            ItemStack item = slot.getCurrentItem();
            if(item == null) continue;
            FoodItem fi = FoodItem.fromItem(item);
            if(fi == null) continue;
            item = ItemUpdater.updateItem(item, fi, f.getId());
            if(item == null) continue;
            slot.forceModel(item);
        }
    }

    public void clear(Furniture f) {
        for(PlacedSlot slot : new ArrayList<>(f.getActiveSlots().values())) {
            if(slot.getId().contains("display") || FurnitureCache.isBowl(f)) {
                slot.clearModel();
            }
        }
    }

    @EventHandler
    public void remove(FurnitureBreakEvent e) {
        Furniture f = e.getFurniture();
        if(FurnitureCache.isMealHolder(f)) {
            clear(f);
        }
    }

    public boolean hasSauce(Furniture f) {
        for(PlacedSlot slot : new ArrayList<>(f.getActiveSlots().values())) {
            if(!slot.getId().contains("display")) {
                ItemStack item = slot.getCurrentItem();
                if(item == null) continue;
                // addSauce stores an ItemsAdder visual here, without Cooking food metadata.
                if(slot.getId().equals("sauce")) return true;
                FoodItem sauce = FoodItem.fromItem(item);
                if(sauce == null) continue;
                if(sauce.getCategory().equalsIgnoreCase("sauce")) return true;
            }
        }
        return false;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void addSauce(Player p, Furniture f, FoodItem sauce, ItemStack base) {
        if (hasSauce(f)) return;

        // Replace player ladle with empty ladle
        p.getInventory().setItemInMainHand(
            TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.ladle)
        );
        p.swingMainHand();

        for (PlacedSlot slot : f.getActiveSlots().values()) {

            if (slot.getId().contains("display")) continue;

            ItemStack item = slot.getCurrentItem();
            if (item == null) continue;

            FoodItem fi = FoodItem.fromItem(item);
            if (fi == null) continue;

            // Apply sauce
            fi.setSauce(new FoodItem(sauce));
            fi.setSauceName(base.getItemMeta().getDisplayName());

            // Update the item
            item = ItemUpdater.applyItemUpdate(item, fi, f.getId());
            if (item == null) continue;

            slot.forceModel(item);
        }

        if (f.getType() == null || f.getType().getSlot("sauce") == null) return;
        if(f.hasActiveSlot("sauce")) return;
        String saucePath = CategoryDictionary.getSauceItemPath(
            StringFormatter.extractHexColor(base.getItemMeta().getDisplayName()), 1);
        f.getOrCreatePlacedSlot("sauce").forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(saucePath));
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f); //TODO SOUND
    }

    public void addSoup(Player p, Furniture f, FoodItem soup, ItemStack base) {
        p.getInventory().setItemInMainHand(
            TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.ladle)
        );
        p.swingMainHand();
        Map<String, ItemStack> map = Encoder.decodeSlots(base.getItemMeta().getPersistentDataContainer().get(Keys.SLOT_DATA, PersistentDataType.STRING));
        for(Map.Entry<String, ItemStack> entry : map.entrySet()) {
            if(f.hasActiveSlot(entry.getKey())) continue;
            if (f.getType() == null || f.getType().getSlot(entry.getKey()) == null) continue;
            PlacedSlot placed = f.getOrCreatePlacedSlot(entry.getKey());
            placed.forceModel(entry.getValue());
            DisplayData spread = BowlIngredientLayout.offsetFor(entry.getKey());
            if (spread != null) {
                placed.applyDisplayData(spread);
            }
        }
        if (f.getType() == null || f.getType().getSlot("food_item") == null) return;
        f.getOrCreatePlacedSlot("food_item").forceModel(base);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f); //TODO SOUND
    }

    @EventHandler
    public void interact(FurnitureInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if(item == null || item.getType().equals(Material.AIR)) {
            return;
        }
        Furniture f = e.getFurniture();
        if(FurnitureCache.isPlate(f)) {
            FoodItem fi = FoodItem.fromItem(item);
            if(fi == null) return;
            if(fi.getCategory().equalsIgnoreCase("sauce")) {
                e.setCancelled(true);
                addSauce(p, f, fi, item);
            }
        }
        if(FurnitureCache.isBowl(f)) {
            FoodItem fi = FoodItem.fromItem(item);
            if(fi == null) return;
            if(fi.getCategory().equalsIgnoreCase("soup")) {
                e.setCancelled(true);
                addSoup(p, f, fi, item);
            }
        }
    }

    @EventHandler
    public void addItem(FurnitureSlotItemAddEvent e) {
        Furniture f = e.getFurniture();
        if(FurnitureCache.isMealHolder(f));
        ItemStack item = e.getItem();
        FoodItem fi = FoodItem.fromItem(item);
        if(fi == null) return;
        e.setDisplayData(fi.getModelData().getDisplayData(f.getId()));
    }
}
