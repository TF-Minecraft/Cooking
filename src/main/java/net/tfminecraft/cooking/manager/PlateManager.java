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
import org.bukkit.event.EventPriority;
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
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
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
        // Also clears plates saved with leftover sauce before the take handler existed.
        if(hasLeftoverSauce(f, null)) {
            f.removeActiveSlot("sauce");
            InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(f);
        }
        boolean changed = false;
        for(PlacedSlot slot : f.getActiveSlots().values()) {
            if(slot == null || slot.getId().contains("display")) continue;
            ItemStack item = slot.getCurrentItem();
            if(item == null) continue;
            FoodItem fi = FoodItem.fromItem(item);
            if(fi == null) continue;
            item = ItemUpdater.updateItem(item, fi, f.getId());
            if(item == null || item.getType().isAir()) continue;
            slot.forceModel(item);
            changed = true;
        }
        if (changed) InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
    }

    public void clear(Furniture f) {
        for(PlacedSlot slot : new ArrayList<>(f.getActiveSlots().values())) {
            if (slot == null) continue;
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
            if (slot == null) continue;
            if(!slot.getId().contains("display")) {
                ItemStack item = slot.getCurrentItem();
                if(item == null) continue;
                // addSauce stores an ItemsAdder visual here, without Cooking food metadata.
                if(slot.getId().equals("sauce")) return true;
                FoodItem sauce = FoodItem.fromItem(item);
                if(sauce == null) continue;
                if(sauce.hasSauce() || "sauce".equalsIgnoreCase(sauce.getCategory())) return true;
            }
        }
        return false;
    }

    /**
     * True when the plate shows a sauce visual but no food would remain once {@code leavingSlot}
     * is gone. A leftover sauce visual makes hasSauce reject the next dish on the plate.
     */
    boolean hasLeftoverSauce(Furniture f, String leavingSlot) {
        if(!f.hasActiveSlot("sauce")) return false;
        return !hasFood(f, leavingSlot);
    }

    // An active food slot counts even without a loaded item: restore can leave the item unset
    // while the display still shows the food.
    private boolean hasFood(Furniture f, String ignoredSlot) {
        for(String id : f.getActiveSlots().keySet()) {
            if(id.equals("sauce") || id.contains("display") || id.equals(ignoredSlot)) continue;
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void takeItem(FurnitureSlotItemTakeEvent e) {
        Furniture f = e.getFurniture();
        if(!FurnitureCache.isPlate(f)) return;
        // The taken slot is still active here; InteractibleFurniture removes it and saves the plate next.
        if(hasLeftoverSauce(f, e.getSlot().getId())) {
            f.removeActiveSlot("sauce");
        }
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void addSauce(Player p, Furniture f, FoodItem sauce, ItemStack base) {
        if (hasSauce(f)) return;
        Map<PlacedSlot, ItemStack> updates = new java.util.LinkedHashMap<>();
        for (PlacedSlot slot : f.getActiveSlots().values()) {
            if (slot == null || slot.getId().equals("sauce") || slot.getId().contains("display")) continue;
            ItemStack item = slot.getCurrentItem();
            if (item == null) continue;
            FoodItem parsed = FoodItem.fromItem(item);
            if (parsed == null) continue;
            FoodItem fi = new FoodItem(parsed);
            fi.setSauce(new FoodItem(sauce));
            fi.setSauceName(base.getItemMeta().getDisplayName());
            ItemStack updated = ItemUpdater.applyItemUpdate(item.clone(), fi, f.getId());
            if (updated == null || updated.getType().isAir()) return;
            updates.put(slot, updated);
        }
        if (updates.isEmpty()) return;
        ItemStack emptyLadle = TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.ladle);
        if (emptyLadle == null || emptyLadle.getType().isAir()) return;

        updates.forEach(PlacedSlot::forceModel);
        p.getInventory().setItemInMainHand(emptyLadle.clone());
        p.swingMainHand();
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);

        if (f.getType() == null || f.getType().getSlot("sauce") == null) return;
        ItemMeta baseMeta = base.getItemMeta();
        String saucePath = CategoryDictionary.getSauceItemPath(sauceColour(
            baseMeta.getPersistentDataContainer().get(Keys.SAUCE_COLOUR, PersistentDataType.STRING),
            baseMeta.getDisplayName()), 1);
        ItemStack visual = TLibs.getItemAPI().getCreator().getItemFromPath(saucePath);
        if (visual != null && !visual.getType().isAir()) f.getOrCreatePlacedSlot("sauce").forceModel(visual);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
    }

    /** Sauce colour stored at scoop time; ladles scooped before it existed fall back to the name's colour. */
    static String sauceColour(String stored, String displayName) {
        return stored != null ? stored : StringFormatter.extractHexColor(displayName);
    }

    public void addSoup(Player p, Furniture f, FoodItem soup, ItemStack base) {
        if (f.getType() == null || f.getType().getSlot("food_item") == null) return;
        PlacedSlot current = f.getActiveSlot("food_item").orElse(null);
        if (current != null && current.getCurrentItem() != null && !current.getCurrentItem().getType().isAir()) return;
        ItemStack emptyLadle = TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.ladle);
        if (emptyLadle == null || emptyLadle.getType().isAir()) return;
        Map<String, ItemStack> map = Encoder.decodeSlots(base.getItemMeta().getPersistentDataContainer().get(Keys.SLOT_DATA, PersistentDataType.STRING));
        for(Map.Entry<String, ItemStack> entry : map.entrySet()) {
            if(f.hasActiveSlot(entry.getKey())) continue;
            if (f.getType().getSlot(entry.getKey()) == null) continue;
            PlacedSlot placed = f.getOrCreatePlacedSlot(entry.getKey());
            placed.forceModel(entry.getValue());
            DisplayData spread = BowlIngredientLayout.offsetFor(entry.getKey());
            if (spread != null) {
                placed.applyDisplayData(spread);
            }
        }
        f.getOrCreatePlacedSlot("food_item").forceModel(base);
        p.getInventory().setItemInMainHand(emptyLadle.clone());
        p.swingMainHand();
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
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
        if(!FurnitureCache.isCookingFurniture(f)) return;
        ItemStack item = e.getItem();
        FoodItem fi = FoodItem.fromItem(item);
        if(fi == null) return;
        var model = fi.getModelData();
        if (model != null) e.setDisplayData(model.getDisplayData(f.getId()));
    }
}
