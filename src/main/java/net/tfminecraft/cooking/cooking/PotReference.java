package net.tfminecraft.cooking.cooking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cup.BucketItems;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.enums.Tag;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.DisplayUtils;
import net.tfminecraft.cooking.utils.Encoder;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.WarmthUtils;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.cooking.utils.StationAddonRules;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

public class PotReference extends CookingReference {
    private static final String VAR_SOUP_SERVINGS = "pot.soupServings";
    private static final String VAR_EXTRAS = "pot.extras";
    static final int MAX_MAINS = 5;

    private final Map<String, ItemStack> extraItems = new HashMap<>();

    private int temperature = 0;          // 0–20
    private final int MAX_TEMPERATURE = 20;

    public PotReference(Furniture f, Method m) {
        super(f, m);
    }

    @Override
    public void tick() {
        super.tick();
        boolean heated = HeatSources.stationHasHeat(f);
        if (secondaries.containsKey("liquid")) {
            if (heated) {
                if (temperature < MAX_TEMPERATURE) {
                    temperature++;
                }
            } else if (temperature > 0) {
                temperature--;
            }
        }

        if (heated) {
            handleCookingSlots();
        }

        if (isBoiling()) {
            new BukkitRunnable() {
                int i = 0;
                @Override
                public void run() {
                    handleParticles();
                    i++;
                    if (i == 10) this.cancel();
                }
            }.runTaskTimer(Cooking.plugin, 0L, 2L);
        }
    }

    public FoodItem getMain() {
        for(PlacedSlot slot : f.getActiveSlots().values()) {
            ItemStack stack = slot.getCurrentItem();
            if(stack == null) continue;
            FoodItem item = FoodItem.fromItem(stack);
            if(item == null) continue;
            return item;
        }
        return null;
    }

    public void setMain(FoodItem fi) {
        for(PlacedSlot slot : f.getActiveSlots().values()) {
            ItemStack stack = slot.getCurrentItem();
            if(stack == null) continue;
            FoodItem item = FoodItem.fromItem(stack);
            if(item == null) continue;
            slot.forceModel(ItemUpdater.applyItemUpdate(stack, fi, f.getId()));
        }
    }
    
    private void handleParticles() {
        if (!slots.isEmpty() || !secondaries.isEmpty()) {
            if (method == Method.POT) {
                if(f.getActiveSlot("liquid").isEmpty()) return;
                Entity display = Bukkit.getEntity(f.getActiveSlot("liquid").get().getDisplayStandId());
                if (display == null) return;

                Location loc = display.getLocation();

                // Temperature-based bubbling chance
                double boilChance = (double) temperature / MAX_TEMPERATURE;
                if (Math.random() > boilChance) return;

                // Generate 3–7 bubble bursts
                int bursts = 3 + (int)(Math.random() * 5);

                for (int i = 0; i < bursts; i++) {

                    double x = (Math.random() * 0.4) - 0.2;
                    double z = (Math.random() * 0.4) - 0.2;

                    Location p = loc.clone().add(x, -0.2, z);

                    loc.getWorld().spawnParticle(
                        Particle.BUBBLE_POP,
                        p,
                        1,
                        0, 0.05, 0,
                        0.05
                    );

                    if (Math.random() < 0.25) {
                        loc.getWorld().spawnParticle(
                            Particle.SPLASH,
                            p.clone().add(0, 0.05, 0),
                            2,
                            0.02, 0.02, 0.02,
                            0.02
                        );
                    }
                }

                // (keep your booming sound variants)
                double soundRoll = Math.random();
                if (soundRoll < 0.5) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.4f, 1.2f);
                } else if (soundRoll < 0.7) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.6f, 1.1f);
                } else if (soundRoll < 0.75) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_POP, 0.3f, 2.2f);
                }
            }
        }
    }

    public boolean isSoup() {
        for(FoodItem item : slots.values()) {
            if(item.hasTag(Tag.MASHED)) return true;
        }
        return false;
    }

    public boolean isBoiling() {
        return temperature >= MAX_TEMPERATURE;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void scoop(Player p, ItemStack ladle) {
        if (!isSoup()) return;

        // ---------- BASE STRING ----------
        String base = "soup(type=soup;origin=Mixed;tags=processed.0:warmth.0)";

        // ---------- BUILD BASE ----------
        FoodItem soup = FoodParser.parse(base).template;

        StationAddonRules.applySeasoningTag(soup, slots);
        StationAddonRules.applyAddonTags(soup, slots);
        StationAddonRules.applyFlavourfulTag(soup, slots);
        FoodItem mi = getMain();
        if(mi != null && mi.hasTagTrack("soup_thickness")) {
            soup.addOrModifyTrack(mi.getTagTrack("soup_thickness"));
            setMain(mi);
        }

        // ---------- BUILD RESULT ----------
        CompositionResult composed = CompositionQualityResolver.compose(p, slots.values(), CompositionContext.SOUP_SCOOP);
        CompositionApplier.apply(soup, composed);
        soup.setBaseFood(scoopFood(soup.getBaseFood(), soupScoops()));
        int quality = composed.getFinalQuality();

        ItemStack output = ItemBuilder.buildSingleWithQuality(soup, ladle, quality);

        String displayName = applyNameTemplate(soup, DisplayUtils.getMergedColour(colours), "Soup");
        ItemMeta m = output.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex(displayName));
        m.getPersistentDataContainer().set(Keys.SLOT_DATA, PersistentDataType.STRING, Encoder.getEncodedSlots(f));
        output.setItemMeta(m);

        // ---------- GIVE TO PLAYER ----------
        p.getInventory().setItemInMainHand(output);
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f); //TODO SOUND
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new net.tfminecraft.cooking.events.DishCookedEvent(p, output, "pot"));
        int remaining = remainingSoupServings() - 1;
        setRemainingSoupServings(remaining);
        if (remaining <= 0) {
            clear();
            return;
        }
        applySoupLevel();
    }

    private void handleCookingSlots() {
        FoodItem main = getMain();
        if(!isEmpty() && isSoup() && main != null) {
            TagTrack track = main.getTagTrack("soup_thickness");
            if(track != null) {
                track.setValue(track.getValue()+1);
            } else {
                main.addOrModifyTrack(new TagTrack(TrackLoader.getByString("soup_thickness")));
            }
            setMain(main);
        }
        for (Map.Entry<String, FoodItem> entry : slots.entrySet()) {
            String slot = entry.getKey();
            if (isExtraSlot(slot)) continue;
            FoodItem item = entry.getValue();
            CookData data = item.getCookData();
            if (!item.canBeCooked()) continue;
            if (isSoup() && data.isBeingCooked()) {
                data.stop();
                continue;
            }
            if (!data.isBeingCooked()) {
                data.start(Method.POT);
            }
            boolean progressed = data.tick();
            if (!progressed) continue;
            applySlotUpdate(slot, item);
        }
    }

    public boolean canAdd(Player p, ItemStack i) {
        if(!isBoiling()) {
            p.sendMessage("§cWater isn't boiling yet...");
            return false;
        }
        if (!secondaries.containsKey("liquid")) return false;
        if(!slots.isEmpty() && !isSoup()) return false;
        if (i == null || i.getType() == Material.AIR) return false;

        // Convert item to FoodItem
        FoodItem fi = FoodItem.fromItem(i);
        if (fi == null) return false;

        String cat = fi.getCategory().toLowerCase();

        if (extraSlotKey(cat) != null) {
            if (!isSoup()) {
                if (p != null) p.sendMessage("§cMash the pot into soup first.");
                return false;
            }
            if (hasSlot(cat)) {
                if (p != null) p.sendMessage("§cThat is already in the pot.");
                return false;
            }
            return true;
        }

        if(!fi.canBeCooked()) return false;

        CookData data = fi.getCookData();
        if(!data.hasMethod(method)) return false;
        if (mainCount() >= MAX_MAINS) {
            if (p != null) p.sendMessage("§cThe pot is full.");
            return false;
        }
        return true;
    }

    public void take(Player p) {
        if(isSoup()) return;
        for(String key : slots.keySet()) {
            if(!f.hasActiveSlot(key)) continue;
            PlacedSlot slot = f.getActiveSlot(key).get();
            if(slot.getCurrentItem() == null) continue;
            ItemStack stack = slot.getCurrentItem();
            FoodItem fi = slots.get(key);
            if (WarmthUtils.isHeated(fi, 3)) {
                WarmthUtils.applyHot(fi);
            }
            stack = ItemUpdater.applyItemUpdate(stack, fi, f.getId());
            p.getInventory().setItemInMainHand(stack);
            f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f); //TODO SOUND
            slot.clearModel();
            slots.clear();
            break;
        }
    }

    public static boolean canMash(FoodItem item) {
        return item != null && item.isMashable();
    }

    /** Mash marks the piece boiled so the bowl snapshot uses the boiled model. */
    public static void markBoiled(FoodItem item) {
        TagTrack cooked = item.getTagTrack("cooked");
        if (cooked == null) {
            TagTrack template = TrackLoader.getByString("cooked");
            if (template == null) {
                return;
            }
            cooked = new TagTrack(template);
            cooked.forceSetValue(3);
            item.addOrModifyTrack(cooked);
            return;
        }
        cooked.forceSetValue(3);
    }

    public void mash(Player p) {
        boolean found = false;
        for(Map.Entry<String, FoodItem> entry : slots.entrySet()) {
            if (isExtraSlot(entry.getKey())) continue;
            FoodItem item = entry.getValue();
            if(!canMash(item)) continue;
            markBoiled(item);
            item.getCookData().stop();
            item.addOrModifyTrack(new TagTrack(TrackLoader.getByString("mashed")));
            applySlotUpdate(entry.getKey(), item);
            updateModel();
            DisplayData mashed = new DisplayData();
            mashed.setxScale(0);
            mashed.setyScale(0);
            mashed.setzScale(0);
            mashed.setyPos(-0.4f);
            if (!f.hasActiveSlot(entry.getKey())) continue;
            PlacedSlot slot = f.getActiveSlot(entry.getKey()).get();
            slot.applyDisplayData(mashed);
            found = true;
        }
        if(found) {
            ensureSoupServings();
            p.swingMainHand();
            f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f); //TODO SOUND
        }
    }

    @Override
    public void interact(FurnitureInteractEvent e) {
        Player p = e.getPlayer();
        if(p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
            if(isEmpty()) return;
            take(p);
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if(ItemCache.isLadle(item)) {
            scoop(p, item);
            return;
        }
        if(ItemCache.isMasher(item)) {
            mash(p);
            return;
        }
        if (!secondaries.containsKey("liquid")) {
            if (isWrongPotWaterSource(item)) {
                p.sendMessage("Use a water bucket to fill the pot.");
                return;
            }
            if (ItemCache.isPotWaterInput(item)) {
                if (f.getType() == null || f.getType().getSlot("liquid") == null) return;
                PlacedSlot slot = f.getOrCreatePlacedSlot("liquid");
                secondaries.put("liquid", -1);
                slot.forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.potLiquidDisplay));
                addColour("3d85c6");
                p.getInventory().setItemInMainHand(BucketItems.empty());
                p.updateInventory();
                p.swingMainHand();
                updateModel();
                danger = 0;
                temperature = 0;
                f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
                return;
            }
        }
        if(canAdd(p, item)) {
            FoodItem incoming = FoodItem.fromItem(item);
            String extraKey = incoming == null ? null : extraSlotKey(incoming.getCategory());
            if (extraKey != null) {
                acceptExtra(p, item, extraKey);
                return;
            }
            for(String slot : f.getType().getSlots().keySet()) {
                if(add(slot, item)) {
                    updateModel();
                    p.swingMainHand();
                    thickenSoup();
                    break;
                }
            }
        }
    }

    private void acceptExtra(Player p, ItemStack item, String extraKey) {
        ItemStack stored = item.clone();
        stored.setAmount(1);
        FoodItem food = FoodItem.fromItem(stored);
        if (food == null) return;
        extraItems.put(extraKey, stored);
        slots.put(extraKey, food);
        saveExtras();
        item.setAmount(item.getAmount() - 1);
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
        thickenSoup();
    }

    private void thickenSoup() {
        FoodItem main = getMain();
        if(main != null && slots.size() > 1) {
            TagTrack track = main.getTagTrack("soup_thickness");
            if(track != null) {
                track.setValue(track.getValue()+600);
            }
            setMain(main);
        }
    }

    public static String extraSlotKey(String category) {
        if (category == null) return null;
        String cat = category.toLowerCase();
        if (cat.equals("salt") || cat.equals("pepper") || cat.equals("garnish") || cat.equals("spice")) {
            return "extra_" + cat;
        }
        return null;
    }

    public static boolean isExtraSlot(String slotId) {
        return slotId != null && slotId.startsWith("extra_");
    }

    private int mainCount() {
        int count = 0;
        for (String key : slots.keySet()) {
            if (key.contains("input")) count++;
        }
        return count;
    }

    private void saveExtras() {
        if (f == null) return;
        if (extraItems.isEmpty()) {
            f.getVariables().remove(VAR_EXTRAS);
        } else {
            StringBuilder encoded = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, ItemStack> entry : extraItems.entrySet()) {
                String payload = encodeStack(entry.getValue());
                if (payload == null) continue;
                if (!first) encoded.append('|');
                first = false;
                encoded.append(entry.getKey()).append('.').append(payload);
            }
            if (encoded.isEmpty()) {
                f.getVariables().remove(VAR_EXTRAS);
            } else {
                f.getVariables().put(VAR_EXTRAS, encoded.toString());
            }
        }
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
    }

    private void restoreExtras() {
        extraItems.clear();
        if (f == null) return;
        Object raw = f.getVariables().get(VAR_EXTRAS);
        if (!(raw instanceof String text) || text.isBlank()) return;
        for (String part : text.split("\\|")) {
            int dot = part.indexOf('.');
            if (dot <= 0 || dot >= part.length() - 1) continue;
            String key = part.substring(0, dot);
            if (!isExtraSlot(key)) continue;
            ItemStack stack = decodeStack(part.substring(dot + 1));
            if (stack == null) continue;
            FoodItem food = FoodItem.fromItem(stack);
            if (food == null) continue;
            extraItems.put(key, stack);
            slots.put(key, food);
        }
    }

    // Preserve the existing serialized item format so previously saved graves remain readable.
    @SuppressWarnings("deprecation")
    static String encodeStack(ItemStack stack) {
        if (stack == null) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeObject(stack);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ignored) {
            return null;
        }
    }

    // Preserve the existing serialized item format so previously saved graves remain readable.
    @SuppressWarnings("deprecation")
    static ItemStack decodeStack(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object read = in.readObject();
                if (read instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (IOException | ClassNotFoundException ignored) {
            return null;
        }
        return null;
    }

    public void updateModel() {
        if(!isSoup()) return;
        String path = getLiquidItemPath();
        if (f.getType() == null || f.getType().getSlot("liquid") == null) return;
        f.getOrCreatePlacedSlot("liquid").forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(path));
        applySoupLevel();
    }

    @Override
    public void rebuildFromFurniture() {
        super.rebuildFromFurniture();
        restoreExtras();
        if (f != null && f.hasActiveSlot("liquid")) {
            secondaries.put("liquid", -1);
        }
        applySoupLevel();
    }

    /** Whole-pot food split across scoops. Nutrition stays the template level. */
    public static double scoopFood(double templateFood, int scoops) {
        return templateFood / Math.max(1, scoops);
    }

    private static int soupScoops() {
        return Math.max(1, ItemCache.potSoupScoops);
    }

    private static int soupHeightDivisor() {
        return Math.max(1, ItemCache.potSoupHeightDivisor);
    }

    private void ensureSoupServings() {
        if (!f.getVariables().containsKey(VAR_SOUP_SERVINGS)) {
            setRemainingSoupServings(soupScoops());
        }
    }

    private int remainingSoupServings() {
        Object value = f.getVariables().get(VAR_SOUP_SERVINGS);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return soupScoops();
            }
        }
        return soupScoops();
    }

    private void setRemainingSoupServings(int remaining) {
        if (remaining <= 0) {
            f.getVariables().remove(VAR_SOUP_SERVINGS);
        } else {
            f.getVariables().put(VAR_SOUP_SERVINGS, remaining);
        }
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
    }

    private void applySoupLevel() {
        if (f == null || f.getType() == null) {
            return;
        }
        int remaining = remainingSoupServings();
        int scoopsTaken = soupScoops() - remaining;
        if (scoopsTaken <= 0) {
            return;
        }
        SlotDefinition liquid = f.getType().getSlot("liquid");
        if (liquid == null) {
            return;
        }
        float step = (float) liquid.getDisplayScale().getY() / soupHeightDivisor();
        DisplayData data = new DisplayData();
        data.setyPos(-step * scoopsTaken);
        f.getActiveSlot("liquid").ifPresent(slot -> slot.applyDisplayData(data));
    }

    private static boolean isWrongPotWaterSource(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return ItemCache.isCupOfWater(item)
                || ItemCache.isWater(item)
                || ItemCache.isLiquid(item);
    }
    
    @Override
    public void clear() {
        extraItems.clear();
        f.getVariables().remove(VAR_SOUP_SERVINGS);
        f.getVariables().remove(VAR_EXTRAS);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        if(isSoup()) super.clear();
        else super.remove();
    }
}
