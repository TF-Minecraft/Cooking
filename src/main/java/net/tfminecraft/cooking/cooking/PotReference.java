package net.tfminecraft.cooking.cooking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.tfminecraft.cooking.pot.MashItems;
import net.tfminecraft.cooking.pot.MashRecipe;
import net.tfminecraft.cooking.pot.PotConfig;
import net.tfminecraft.cooking.pot.PotLiquid;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.DisplayUtils;
import net.tfminecraft.cooking.utils.Encoder;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.HeldStack;
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

/**
 * The soup pot. Filled with a water bucket (or a pot.yml liquid such as stock) and heated, it boils pieces that the
 * masher turns into soup. Empty, it takes a pot.yml mash's ingredients instead, which the masher mashes without water
 * or heat and the ladle serves.
 */
public class PotReference extends CookingReference {
    private static final String VAR_SOUP_SERVINGS = "pot.soupServings";
    private static final String VAR_EXTRAS = "pot.extras";
    private static final String VAR_LIQUID = "pot.liquid";
    private static final String VAR_LIQUID_FOOD = "pot.liquidFood";
    private static final String VAR_MASH = "pot.mash";
    private static final String VAR_MASHED = "pot.mashed";
    private static final String VAR_MASH_SERVINGS = "pot.mashServings";
    private static final String WATER_COLOUR = "3d85c6";
    static final String MASH_NOT_WATER =
            "§cThere's a mash in this pot. Take it back out with an empty hand or ladle it out before adding water.";
    static final int MAX_MAINS = 5;

    private final Map<String, ItemStack> extraItems = new HashMap<>();
    /** The pot.yml liquid poured in instead of water (stock) and that bottle's food, or null for water. */
    private PotLiquid liquid;
    private FoodItem liquidFood;

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

        // A mash needs no heat, and its boiled potatoes must not cook on.
        if (heated && !isMashing()) {
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
        PlacedSlot slot = firstFoodSlot();
        if (slot == null) return null;
        return FoodItem.fromItem(slot.getCurrentItem());
    }

    /** Thickness lives on the first ingredient. Later pieces keep the model of what was held. */
    public void setMain(FoodItem fi) {
        if (fi == null) return;
        PlacedSlot slot = firstFoodSlot();
        if (slot == null) return;
        ItemStack stack = slot.getCurrentItem();
        ItemStack updated = ItemUpdater.applyItemUpdate(stack, fi, f.getId());
        if (updated == null) return;
        slot.forceModel(updated);
    }

    private PlacedSlot firstFoodSlot() {
        for (PlacedSlot slot : f.getActiveSlots().values()) {
            ItemStack stack = slot.getCurrentItem();
            if (stack == null) continue;
            if (FoodItem.fromItem(stack) == null) continue;
            return slot;
        }
        return null;
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
        if (liquid != null) MashItems.addTrack(soup, liquid.track());
        FoodItem mi = getMain();
        if(mi != null && mi.hasTagTrack("soup_thickness")) {
            soup.addOrModifyTrack(mi.getTagTrack("soup_thickness"));
            setMain(mi);
        }

        // ---------- BUILD RESULT ----------
        List<FoodItem> inputs = new ArrayList<>(slots.values());
        if (liquidFood != null) inputs.add(liquidFood);
        CompositionResult composed = CompositionQualityResolver.compose(p, inputs, CompositionContext.SOUP_SCOOP);
        CompositionApplier.apply(soup, composed);
        soup.setBaseFood(scoopFood(soup.getBaseFood()));
        int quality = composed.getFinalQuality();

        ItemStack output = ItemBuilder.buildSingleWithQuality(soup, ladle, quality);
        if (output == null) {
            p.sendMessage("§cThis soup cannot be served right now.");
            return;
        }

        String displayName = applyNameTemplate(soup, DisplayUtils.getMergedColour(colours), "Soup");
        ItemMeta m = output.getItemMeta();
        m.setDisplayName(StringFormatter.formatHex(displayName));
        m.getPersistentDataContainer().set(Keys.SLOT_DATA, PersistentDataType.STRING, Encoder.getEncodedSlots(f));
        output.setItemMeta(m);

        // ---------- GIVE TO PLAYER ----------
        HeldStack.useOneIntoHand(p, output);
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
            if (item.hasTag(Tag.MASHED)) {
                if (data.isBeingCooked()) data.stop();
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

    @Override
    protected void applySlotUpdate(String slot, FoodItem item) {
        // The display and running cooking state are decoded separately. Both cooking
        // transitions and mashing must retain the thickness already on the main item.
        PlacedSlot mainSlot = firstFoodSlot();
        if (mainSlot != null && f.getActiveSlots().get(slot) == mainSlot) {
            FoodItem displayed = FoodItem.fromItem(mainSlot.getCurrentItem());
            if (displayed.hasTagTrack("soup_thickness")) {
                item.addOrModifyTrack(new TagTrack(displayed.getTagTrack("soup_thickness")));
            }
        }
        super.applySlotUpdate(slot, item);
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
            if (!f.hasActiveSlot(entry.getKey())) continue;
            PlacedSlot slot = f.getActiveSlot(entry.getKey()).get();
            slot.applyDisplayData(mashedDisplay());
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
            if (isMashing()) {
                if (!isMashed()) takeBackFromMash(p);
                return;
            }
            take(p);
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (isHeldSoup(item)) {
            e.setCancelled(true);
            return;
        }
        if(ItemCache.isLadle(item)) {
            if (isSoup()) {
                scoop(p, item);
                e.setCancelled(true);
            } else if (isMashed()) {
                scoopMash(p, item);
                e.setCancelled(true);
            }
            return;
        }
        if(ItemCache.isMasher(item)) {
            if (isMashing()) mashDish(p);
            else mash(p);
            e.setCancelled(true);
            return;
        }
        if (!secondaries.containsKey("liquid")) {
            FoodItem held = FoodItem.fromItem(item);
            if (addToMash(p, item, held)) {
                e.setCancelled(true);
                return;
            }
            PotLiquid pouring = held == null ? null : PotConfig.liquidFor(held);
            if (isMashing()) {
                boolean water = pouring != null || ItemCache.isPotWaterInput(item) || isWrongPotWaterSource(item);
                p.sendMessage(water ? MASH_NOT_WATER : "§cThat doesn't go in this mash.");
                e.setCancelled(true);
                return;
            }
            if (isWrongPotWaterSource(item)) {
                p.sendMessage("Use a water bucket to fill the pot.");
                return;
            }
            if (ItemCache.isPotWaterInput(item)) {
                if (!fill(WATER_COLOUR)) return;
                p.getInventory().setItemInMainHand(BucketItems.empty());
                p.updateInventory();
                p.swingMainHand();
                f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
                e.setCancelled(true);
                return;
            }
            if (pouring != null) {
                pour(p, item, held, pouring);
                e.setCancelled(true);
                return;
            }
        } else if (goesInMashOnly(item)) {
            p.sendMessage("§cThat goes in a mash, which is made in an empty pot without water.");
            e.setCancelled(true);
            return;
        }
        if(canAdd(p, item)) {
            FoodItem incoming = FoodItem.fromItem(item);
            String extraKey = incoming == null ? null : extraSlotKey(incoming.getCategory());
            if (extraKey != null) {
                acceptExtra(p, item, extraKey);
                e.setCancelled(true);
                return;
            }
            for(String slot : f.getType().getSlots().keySet()) {
                if(add(slot, item)) {
                    updateModel();
                    p.swingMainHand();
                    thickenSoup();
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }

    /** Scooped soup or mash keeps the ladle's place in the hand, so it must not be used as a ladle. */
    private static boolean isHeldSoup(ItemStack item) {
        if (MashItems.isLadled(item)) return true;
        FoodItem food = FoodItem.fromItem(item);
        if (food == null) {
            return false;
        }
        return "soup".equalsIgnoreCase(food.getId()) || "soup".equalsIgnoreCase(food.getCategory());
    }

    /** Fills the empty pot with water or a pot.yml liquid of this colour; false when it has no liquid slot. */
    private boolean fill(String colour) {
        if (f.getType() == null || f.getType().getSlot("liquid") == null) return false;
        PlacedSlot slot = f.getOrCreatePlacedSlot("liquid");
        secondaries.put("liquid", -1);
        slot.forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.potLiquidDisplay));
        addColour(colour);
        updateModel();
        danger = 0;
        temperature = 0;
        return true;
    }

    /** A bottle of stock fills the pot like a water bucket; the soup made from it earns the liquid's track (Rich). */
    private void pour(Player p, ItemStack item, FoodItem food, PotLiquid poured) {
        if (!fill(poured.colour())) return;
        liquid = poured;
        liquidFood = new FoodItem(food);
        ItemStack single = item.clone();
        single.setAmount(1);
        f.getVariables().put(VAR_LIQUID, poured.id());
        String payload = encodeStack(single);
        if (payload != null) f.getVariables().put(VAR_LIQUID_FOOD, payload);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        useOneGivingBack(p, item, poured.returns());
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BOTTLE_EMPTY, 1f, 1f);
    }

    /** Uses one of the held item; the player gets the item at that path back for it (an empty bottle or cup), if any. */
    private static void useOneGivingBack(Player p, ItemStack item, String returns) {
        ItemStack back = returns == null || returns.isBlank() ? null : TLibs.getItemAPI().getCreator().getItemFromPath(returns);
        if (back == null || back.getType().isAir()) item.setAmount(item.getAmount() - 1);
        else HeldStack.useOneKeepStack(p, back);
    }

    /** Butter or milk held over water: it belongs in a mash, which the pot makes without water. */
    private boolean goesInMashOnly(ItemStack item) {
        FoodItem food = FoodItem.fromItem(item);
        if (food == null || !PotConfig.isMashIngredient(food) || extraSlotKey(food.getCategory()) != null) return false;
        return !food.canBeCooked() || !food.getCookData().hasMethod(method);
    }

    /** True while the empty pot holds a mash's ingredients, mashed or not. */
    public boolean isMashing() {
        return f != null && f.getVariables().containsKey(VAR_MASH);
    }

    /** True once the masher has mashed them; the ladle serves it. */
    public boolean isMashed() {
        return mashedId() != null;
    }

    private String mashedId() {
        return f != null && f.getVariables().get(VAR_MASHED) instanceof String id ? id : null;
    }

    /** The pieces in the pot (not its extras). */
    private List<FoodItem> mashIngredients() {
        List<FoodItem> pieces = new ArrayList<>();
        for (Map.Entry<String, FoodItem> entry : slots.entrySet()) {
            if (!isExtraSlot(entry.getKey())) pieces.add(entry.getValue());
        }
        return pieces;
    }

    /** The pot.yml extra ids in the pot (milk, salt). */
    private Set<String> mashExtras() {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : slots.keySet()) {
            if (isExtraSlot(key)) ids.add(key.substring("extra_".length()));
        }
        return ids;
    }

    /**
     * An empty pot takes a mash's ingredients without water or heat, and its extras once a piece is in. True when it
     * took the item, or refused it because the mash is already mashed.
     */
    private boolean addToMash(Player p, ItemStack item, FoodItem food) {
        if (food == null) return false;
        if (isMashed()) {
            p.sendMessage("§cLadle the mash out first.");
            return true;
        }
        List<FoodItem> pieces = mashIngredients();
        Set<String> extras = mashExtras();
        for (MashRecipe recipe : PotConfig.mashes()) {
            if (!recipe.holds(pieces, extras)) continue;
            if (recipe.takesIngredient(food, pieces)) {
                addMashPiece(p, item);
                return true;
            }
            MashRecipe.Extra extra = recipe.extraFor(food);
            if (extra != null && !pieces.isEmpty() && !extras.contains(extra.id())) {
                addMashExtra(p, item, extra);
                return true;
            }
        }
        return false;
    }

    private void addMashPiece(Player p, ItemStack item) {
        for (String slot : f.getType().getSlots().keySet()) {
            if (add(slot, item)) {
                f.getVariables().put(VAR_MASH, true);
                InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
                p.swingMainHand();
                return;
            }
        }
        p.sendMessage("§cThe pot is full.");
    }

    private void addMashExtra(Player p, ItemStack item, MashRecipe.Extra extra) {
        if (!storeExtra(item, "extra_" + extra.id())) return;
        useOneGivingBack(p, item, extra.returns());
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
    }

    /** Until it's mashed, an empty hand takes back the last piece, then the extras, and an empty pot is a pot again. */
    private void takeBackFromMash(Player p) {
        String key = null;
        for (String slotId : f.getType().getSlots().keySet()) {
            if (slots.containsKey(slotId) && f.hasActiveSlot(slotId)) key = slotId;
        }
        ItemStack back;
        if (key != null) {
            PlacedSlot placed = f.getActiveSlot(key).get();
            back = ItemUpdater.applyItemUpdate(placed.getCurrentItem(), slots.get(key), null);
            placed.clearModel();
        } else {
            key = extraItems.keySet().iterator().next();
            back = extraItems.remove(key);
            saveExtras(extraItems);
        }
        slots.remove(key);
        p.getInventory().setItemInMainHand(back);
        if (slots.isEmpty()) {
            f.getVariables().remove(VAR_MASH);
            f.getVariables().remove(VAR_EXTRAS);
        }
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
    }

    /** The masher mashes a pot that holds exactly a mash's ingredients; otherwise it says what's needed. */
    private void mashDish(Player p) {
        if (isMashed()) {
            p.sendMessage("§7It's mashed. Serve it with a ladle.");
            return;
        }
        List<FoodItem> pieces = mashIngredients();
        Set<String> extras = mashExtras();
        MashRecipe holding = null;
        for (MashRecipe recipe : PotConfig.mashes()) {
            if (!recipe.holds(pieces, extras)) continue;
            if (recipe.complete(pieces)) {
                f.getVariables().put(VAR_MASHED, recipe.getId());
                f.getVariables().put(VAR_MASH_SERVINGS, recipe.getServings());
                InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
                showMash(recipe);
                p.swingMainHand();
                f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
                return;
            }
            if (holding == null) holding = recipe;
        }
        p.sendMessage(holding == null ? "§cThere's nothing here to mash." : "§cIt needs exactly " + holding.getHint() + " to mash.");
    }

    /** The pieces sink out of sight and the pot shows the mash, as low as the servings taken. */
    private void showMash(MashRecipe recipe) {
        for (String key : slots.keySet()) {
            f.getActiveSlot(key).ifPresent(placed -> placed.applyDisplayData(mashedDisplay()));
        }
        if (f.getType().getSlot("liquid") == null) return;
        f.getOrCreatePlacedSlot("liquid").forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(recipe.getDisplay()));
        applyLevel(recipe.getServings(), remainingMashServings(recipe));
    }

    /** The ladle takes one serving of the mash; the last one empties the pot. */
    private void scoopMash(Player p, ItemStack ladle) {
        MashRecipe recipe = PotConfig.mash(mashedId());
        ItemStack output = recipe == null ? null : MashItems.serve(p, recipe, slots.values(), mashExtras(), ladle);
        if (output == null) {
            p.sendMessage("§cThis can't be served right now.");
            return;
        }
        HeldStack.useOneIntoHand(p, output);
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new net.tfminecraft.cooking.events.DishCookedEvent(p, output, "pot"));
        int remaining = remainingMashServings(recipe) - 1;
        if (remaining <= 0) {
            clear();
            return;
        }
        f.getVariables().put(VAR_MASH_SERVINGS, remaining);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        applyLevel(recipe.getServings(), remaining);
    }

    private int remainingMashServings(MashRecipe recipe) {
        return f.getVariables().get(VAR_MASH_SERVINGS) instanceof Number number ? number.intValue() : recipe.getServings();
    }

    private void acceptExtra(Player p, ItemStack item, String extraKey) {
        if (!storeExtra(item, extraKey)) return;
        item.setAmount(item.getAmount() - 1);
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
        thickenSoup();
    }

    /** Keeps one of the held item as this extra, saved on the pot; false when it isn't food or can't be saved. */
    private boolean storeExtra(ItemStack item, String extraKey) {
        ItemStack stored = item.clone();
        stored.setAmount(1);
        FoodItem food = FoodItem.fromItem(stored);
        if (food == null) return false;
        Map<String, ItemStack> updated = new HashMap<>(extraItems);
        updated.put(extraKey, stored);
        if (!saveExtras(updated)) return false;
        extraItems.put(extraKey, stored);
        slots.put(extraKey, food);
        return true;
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

    private boolean saveExtras(Map<String, ItemStack> updated) {
        if (f == null) return false;
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, ItemStack> entry : updated.entrySet()) {
            String payload = encodeStack(entry.getValue());
            if (payload == null) return false;
            if (!encoded.isEmpty()) encoded.append('|');
            encoded.append(entry.getKey()).append('.').append(payload);
        }
        f.getVariables().put(VAR_EXTRAS, encoded.toString());
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        return true;
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
    public static String encodeStack(ItemStack stack) {
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
    public static ItemStack decodeStack(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object read = in.readObject();
                if (read instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException ignored) {
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

    /** Mashed pieces sink out of sight; the soup liquid shows them. */
    private static DisplayData mashedDisplay() {
        DisplayData mashed = new DisplayData();
        mashed.setxScale(0);
        mashed.setyScale(0);
        mashed.setzScale(0);
        mashed.setyPos(-0.4f);
        return mashed;
    }

    @Override
    public void rebuildFromFurniture() {
        super.rebuildFromFurniture();
        restoreMains();
        restoreExtras();
        restoreLiquid();
        if (isMashing()) {
            restoreMash();
            return;
        }
        if (f != null && f.hasActiveSlot("liquid")) {
            secondaries.put("liquid", -1);
        }
        restoreColours();
        applySoupLevel();
    }

    /** The stock a pot was filled with, so its soup is still Rich after a reload. */
    private void restoreLiquid() {
        if (f == null) return;
        liquid = f.getVariables().get(VAR_LIQUID) instanceof String id ? PotConfig.liquid(id) : null;
        ItemStack poured = decodeStack(f.getVariables().get(VAR_LIQUID_FOOD) instanceof String text ? text : null);
        liquidFood = poured == null ? null : FoodItem.fromItem(poured);
    }

    /** Butter and the like aren't kept by the base rebuild; a mashed pot shows its mash again. Its slot isn't water. */
    private void restoreMash() {
        if (f.getType() == null) return;
        for (String slotId : f.getType().getSlots().keySet()) {
            if (!slotId.contains("input") || slots.containsKey(slotId)) continue;
            FoodItem fi = f.getActiveSlot(slotId).map(placed -> FoodItem.fromItem(placed.getCurrentItem())).orElse(null);
            if (fi != null) slots.put(slotId, fi);
        }
        MashRecipe recipe = PotConfig.mash(mashedId());
        if (recipe != null) showMash(recipe);
    }

    /**
     * The base rebuild keeps raw food only. Soup is mashed and boiled pieces are cooked, so a
     * chunk reload or restart would drop them: the ladle found no soup and a break dropped them.
     */
    private void restoreMains() {
        if (f == null || f.getType() == null) return;
        for (String slotId : f.getType().getSlots().keySet()) {
            if (!slotId.contains("input") || slots.containsKey(slotId)) continue;
            PlacedSlot placed = f.getActiveSlot(slotId).orElse(null);
            if (placed == null) continue;
            FoodItem fi = FoodItem.fromItem(placed.getCurrentItem());
            if (!keepsOnRebuild(fi)) continue;
            slots.put(slotId, fi);
            if (fi.hasTag(Tag.MASHED)) {
                placed.applyDisplayData(mashedDisplay());
            }
        }
    }

    /** True for food the pot holds as a main: soup pieces and anything that boils. */
    public static boolean keepsOnRebuild(FoodItem fi) {
        if (fi == null) return false;
        if (fi.hasTag(Tag.MASHED)) return true;
        return fi.canBeCooked() && fi.getCookData().hasMethod(Method.POT);
    }

    /** Colours are not saved, so rebuild them in the order the pot was filled: water (or stock) first. */
    private void restoreColours() {
        if (f == null || f.getType() == null || !secondaries.containsKey("liquid")) return;
        addColour(liquid != null ? liquid.colour() : WATER_COLOUR);
        for (String slotId : f.getType().getSlots().keySet()) {
            if (!slots.containsKey(slotId) || isExtraSlot(slotId)) continue;
            f.getActiveSlot(slotId).ifPresent(placed -> addColour(ItemCache.getColour(placed.getCurrentItem())));
        }
    }

    /** Each scoop keeps the soup template food. Serving count does not scale it. */
    public static double scoopFood(double templateFood) {
        return templateFood;
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
        if (f != null) applyLevel(soupScoops(), remainingSoupServings());
    }

    /** Lowers the liquid slot a step for each serving taken out of the total. */
    private void applyLevel(int total, int remaining) {
        if (f.getType() == null) {
            return;
        }
        int scoopsTaken = total - remaining;
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
        return ItemCache.isCupOfWater(item)
                || ItemCache.isWater(item)
                || ItemCache.isLiquid(item);
    }
    
    @Override
    public void clear() {
        // A mashed pot's pieces are the mash: like soup, they go with it rather than drop when the pot breaks.
        boolean mashed = isMashed();
        extraItems.clear();
        liquid = null;
        liquidFood = null;
        for (String key : List.of(VAR_SOUP_SERVINGS, VAR_EXTRAS, VAR_LIQUID, VAR_LIQUID_FOOD, VAR_MASH, VAR_MASHED,
                VAR_MASH_SERVINGS)) {
            f.getVariables().remove(key);
        }
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        if (mashed) f.getActiveSlot("liquid").ifPresent(PlacedSlot::clearModel);
        if(isSoup() || mashed) super.clear();
        else super.remove();
    }
}
