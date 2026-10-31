package net.tfminecraft.cooking.cooking;



import java.util.ArrayList;
import java.util.List;
import java.util.Map;



import org.bukkit.Material;

import org.bukkit.Particle;

import org.bukkit.Sound;

import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataType;



import net.tfminecraft.tlibs.TLibs;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

import net.tfminecraft.cooking.brewery.BrewLiquid;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.condiment.BottleRecipe;
import net.tfminecraft.cooking.condiment.CondimentConfig;

import net.tfminecraft.cooking.cup.BucketItems;

import net.tfminecraft.cooking.cup.CupItems;

import net.tfminecraft.cooking.enums.Method;

import net.tfminecraft.cooking.heat.HeatSources;

import net.tfminecraft.cooking.item.FoodItem;

import net.tfminecraft.cooking.item.tag.TagTrack;

import net.tfminecraft.cooking.loader.TrackLoader;

import net.tfminecraft.cooking.quality.CompositionContext;

import net.tfminecraft.cooking.quality.CompositionApplier;

import net.tfminecraft.cooking.quality.CompositionQualityResolver;

import net.tfminecraft.cooking.quality.CompositionResult;

import net.tfminecraft.cooking.utils.DisplayUtils;

import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.InventoryAdder;

import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.HeldStack;

import net.tfminecraft.cooking.utils.ItemBuilder;

import net.tfminecraft.cooking.utils.Keys;

import net.tfminecraft.cooking.utils.StationAddonRules;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;

import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;



public class SauceReference extends CookingReference {

    private static final String VAR_COLOURS = "sauce.colours";
    private static final String VAR_LIQUID = "sauce.liquid";
    private static final String VAR_BREW = "sauce.brew";
    private static final String VAR_BOILED = "sauce.boiled";
    private static final String VAR_WATER = "sauce.water";

    private FoodItem liquidSource;
    private BrewLiquid brew;
    /** Seconds the saucepan has boiled since its last liquid or ingredient went in (condiments.yml bottling). */
    private int boiled;
    /** Filled from a cup of water, which leaves no food as its liquid (a {@code liquid: water} bottling, bone stock). */
    private boolean water;

    public SauceReference(Furniture f, Method m) {

        super(f, m);

    }



    @Override

    public void tick() {

        super.tick();

        if (!HeatSources.stationHasHeat(f)) {
            return;
        }

        if (secondaries.containsKey("liquid")) {
            boiled++;
            f.getVariables().put(VAR_BOILED, boiled);
            if (boiled % 10 == 0) InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
        }

        handleCookingSlots();

        handleParticlesAndDanger();

    }

    

    private void handleParticlesAndDanger() {

        if (!slots.isEmpty() || !secondaries.isEmpty()) {

            if (method == Method.SAUCEPAN) {

                f.getLoc().getWorld().spawnParticle(

                        Particle.CAMPFIRE_COSY_SMOKE,

                        f.getLoc(),

                        0,

                        0, 0.1, 0,

                        0.05

                );

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



    public boolean canAdd(Player player, ItemStack i) {

        if (!secondaries.containsKey("liquid")) return false;

        if (i == null || i.getType() == Material.AIR) return false;



        FoodItem fi = FoodItem.fromItem(i);

        if (fi == null) return false;



        String category = fi.getCategory().toLowerCase();



        if (StationAddonRules.isSeasoningCategory(category)) {

            return !hasSlot(category);

        }



        if (StationAddonRules.isSweetenerCategory(category)) {

            return StationAddonRules.canAcceptSweetener(slots, fi, player);

        }



        if (StationAddonRules.isAddonCategory(category)) {

            return StationAddonRules.canAcceptAddon(slots, fi, player);

        }



        if (category.equals("alcohol")) return true;



        // What a bottling recipe for this liquid still needs (tomato paste in vinegar for catsup).
        return bottlingTakes(fi);

    }



    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void scoop(Player p, ItemStack ladle) {

        if (!secondaries.containsKey("liquid")) return;



        String base = "sauce(type=sauce;origin=Mixed;tags=processed.0:warmth.0)";

        FoodItem sauce = FoodParser.parse(base).template;



        int flourCount = 0;

        boolean hasMilk = false;



        for (FoodItem fi : slots.values()) {

            String category = fi.getCategory().toLowerCase();

            if (category.equals("flour")) {

                flourCount++;

            }

        }



        for (String c : colours) {

            // A white brew (vodka) is not milk.
            if (brew == null && c.equalsIgnoreCase("ffffff")) {

                hasMilk = true;

                break;

            }

        }



        StationAddonRules.applySeasoningTag(sauce, slots);

        StationAddonRules.applyAddonTags(sauce, slots);

        StationAddonRules.applyFlavourfulTag(sauce, slots);

        StationAddonRules.applySweetTag(sauce, slots);



        TagTrack thickTrack = new TagTrack(TrackLoader.getByString("sauce_thickness"));

        if (flourCount >= 1) {

            thickTrack.setValue(1);

        } else {

            thickTrack.setValue(0);

        }

        sauce.addOrModifyTrack(thickTrack);



        if (hasMilk) {

            TagTrack creamyTrack = new TagTrack(TrackLoader.getByString("sauce_creamy"));

            creamyTrack.setValue(0);

            sauce.addOrModifyTrack(creamyTrack);

        }



        TagTrack cookedTrack = new TagTrack(TrackLoader.getByString("sauce_cooked"));

        cookedTrack.setValue(1);

        sauce.addOrModifyTrack(cookedTrack);

        TagTrack reduced = brew == null ? null : TrackLoader.getByString("reduced");
        if (reduced != null) {
            reduced = new TagTrack(reduced);
            reduced.setValue(0);
            sauce.addOrModifyTrack(reduced);
        }



        List<FoodItem> inputs = new ArrayList<>();
        if (liquidSource != null) {
            inputs.add(liquidSource);
        }
        inputs.addAll(slots.values());

        CompositionResult composed = CompositionQualityResolver.compose(p, inputs, CompositionContext.SAUCE_SCOOP);
        CompositionApplier.apply(sauce, composed);
        int quality = composed.getFinalQuality();

        ItemStack output = ItemBuilder.buildSingleWithQuality(sauce, ladle, quality);
        if (output == null) {
            p.sendMessage("§cThis sauce cannot be served right now.");
            return;
        }



        String colour = DisplayUtils.getMergedColour(colours);
        String displayName = applyNameTemplate(sauce, colour, "Sauce");

        ItemMeta m = output.getItemMeta();

        m.setDisplayName(StringFormatter.formatHex(displayName));
        // The name can't carry every colour (Paper stores #ffffff as named white), so keep it here too.
        m.getPersistentDataContainer().set(Keys.SAUCE_COLOUR, PersistentDataType.STRING, colour);

        output.setItemMeta(m);



        HeldStack.useOneIntoHand(p, output);

        p.swingMainHand();

        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 2f);

        org.bukkit.Bukkit.getPluginManager().callEvent(
                new net.tfminecraft.cooking.events.DishCookedEvent(p, output, "sauce"));

        clear();

    }





    @Override

    public void interact(FurnitureInteractEvent e) {

        Player p = e.getPlayer();

        if(isEmpty() && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) {

            return;

        }

        ItemStack item = p.getInventory().getItemInMainHand();

        if(ItemCache.isLadle(item)) {

            scoop(p, item);

            return;

        }

        if (item.getType() == Material.GLASS_BOTTLE && FoodItem.fromItem(item) == null) {
            bottle(p);
            return;
        }
        FoodItem bottled = secondaries.containsKey("liquid") ? null : FoodItem.fromItem(item);
        if (bottled != null && CondimentConfig.isSaucepanLiquid(bottled)) {
            pourBottled(p, item, bottled);
            return;
        }

        BrewLiquid pouredBrew = secondaries.containsKey("liquid") ? null : BrewLiquid.from(item);
        if (pouredBrew != null) {
            pourBrew(p, pouredBrew);
            return;
        }
        if (!secondaries.containsKey("liquid") && isWrongSaucepanWater(item)) {

            p.sendMessage("Use a cup of water to fill the saucepan.");

            return;

        }

        if(canPourSaucepanLiquid(item) && !secondaries.containsKey("liquid")) {

            if (f.getType() == null || f.getType().getSlot("liquid") == null) return;

            FoodItem poured = FoodItem.fromItem(item);
            if (poured != null) {
                liquidSource = new FoodItem(poured);
            }
            saveLiquid(poured != null ? item : null);
            water = ItemCache.isCupOfWater(item);
            if (water) f.getVariables().put(VAR_WATER, true);

            PlacedSlot slot = f.getOrCreatePlacedSlot("liquid");

            secondaries.put("liquid", -1);

            String modelPath = saucepanLiquidModel(item);

            slot.forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(modelPath));

            addColour(ItemCache.getColour(item));
            saveColours();

            consumeSaucepanPour(p, item);

            p.swingMainHand();

            updateModel();

            danger = 0;

            f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BUCKET_FILL, 1f, 2f);

            return;

        }

        item = convertForBottling(p, item);

        if(canAdd(p, item)) {

            for(String slot : f.getType().getSlots().keySet()) {

                if(add(slot, item)) {

                    resetBoil();
                    saveColours();

                    updateModel();

                    p.swingMainHand();

                    break;

                }

            }

        }

    }

    

    /** A finished alcoholic brew replaces the water or milk, so both add-in slots stay free. */
    private void pourBrew(Player p, BrewLiquid poured) {
        if (!poured.alcoholic()) {
            p.sendMessage("§cOnly alcoholic brews can go in the saucepan.");
            return;
        }
        if (f.getType() == null || f.getType().getSlot("liquid") == null) return;
        brew = poured;
        liquidSource = brewIngredient(poured);
        saveLiquid(null);
        f.getVariables().put(VAR_BREW, poured.stars() + ":" + poured.name());
        secondaries.put("liquid", -1);
        if (poured.colour() != null) addColour(poured.colour());
        saveColours();
        HeldStack.useOneKeepStack(p, new ItemStack(Material.GLASS_BOTTLE));
        p.swingMainHand();
        updateModel();
        danger = 0;
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BOTTLE_EMPTY, 1f, 1f);
    }

    /** The brew counts toward the sauce's stars like the milk it replaces. A bottled brew never spoils, so it is fresh. */
    private static FoodItem brewIngredient(BrewLiquid poured) {
        FoodItem ingredient = FoodParser.parse("sauce(type=sauce;origin=Mixed)").template;
        ingredient.setCategory("alcohol");
        ingredient.setOrigin(poured.name());
        ingredient.setQualityRange(poured.stars(), poured.stars());
        TagTrack fresh = TrackLoader.getByString("freshness");
        if (fresh != null) {
            fresh = new TagTrack(fresh);
            fresh.setValue(0);
            ingredient.addOrModifyTrack(fresh);
        }
        return ingredient;
    }

    /** The brew leads the sauce's name, e.g. "Red Wine Sauce". */
    @Override
    public List<String> getIngredients() {
        List<String> names = super.getIngredients();
        if (brew == null || brew.name().isEmpty()) return names;
        List<String> withBrew = new ArrayList<>();
        withBrew.add(brew.name());
        withBrew.addAll(names);
        return withBrew;
    }

    public void updateModel() {

        String path = getLiquidItemPath();

        if (f.getType() == null || f.getType().getSlot("liquid") == null) return;

        f.getOrCreatePlacedSlot("liquid").forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(path));

    }

    @Override
    public void clear() {
        liquidSource = null;
        brew = null;
        boiled = 0;
        water = false;
        super.clear();
        if (f == null) return;
        f.getVariables().remove(VAR_BOILED);
        f.getVariables().remove(VAR_WATER);
        f.getVariables().remove(VAR_COLOURS);
        f.getVariables().remove(VAR_LIQUID);
        f.getVariables().remove(VAR_BREW);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
    }

    /**
     * The base rebuild keeps raw food only. A chunk reload or restart dropped the liquid, so the
     * saucepan still showed sauce that the ladle could no longer scoop.
     */
    @Override
    public void rebuildFromFurniture() {
        super.rebuildFromFurniture();
        if (f == null || f.getType() == null) return;
        restoreIngredients();
        if (!f.hasActiveSlot("liquid")) return;
        secondaries.put("liquid", -1);
        ItemStack liquid = PotReference.decodeStack(stringVariable(VAR_LIQUID));
        liquidSource = liquid == null ? null : FoodItem.fromItem(liquid);
        brew = restoreBrew(stringVariable(VAR_BREW));
        if (brew != null) liquidSource = brewIngredient(brew);
        boiled = f.getVariables().get(VAR_BOILED) instanceof Number seconds ? seconds.intValue() : 0;
        water = Boolean.TRUE.equals(f.getVariables().get(VAR_WATER));
        restoreColours();
    }

    /** A bottled liquid (vinegar, sap) is poured in like water; the empty bottle goes back to the player. */
    private void pourBottled(Player p, ItemStack item, FoodItem poured) {
        if (f.getType() == null || f.getType().getSlot("liquid") == null) return;
        liquidSource = new FoodItem(poured);
        saveLiquid(item);
        secondaries.put("liquid", -1);
        addColour(ItemCache.getColour(item));
        saveColours();
        HeldStack.useOneKeepStack(p, new ItemStack(Material.GLASS_BOTTLE));
        p.swingMainHand();
        updateModel();
        danger = 0;
        resetBoil();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BOTTLE_EMPTY, 1f, 1f);
    }

    /** The bottling recipe this saucepan holds exactly (vinegar, tomato paste and sugar make catsup), or null. */
    BottleRecipe matchingBottle() {
        for (BottleRecipe recipe : CondimentConfig.bottles()) {
            if (recipe.matches(liquidSource, water, slots.values())) return recipe;
        }
        return null;
    }

    private boolean bottlingTakes(FoodItem candidate) {
        for (BottleRecipe recipe : CondimentConfig.bottles()) {
            if (recipe.usesLiquid(liquidSource, water) && recipe.takes(candidate, slots.values())) return true;
        }
        return false;
    }

    /**
     * Carving hands out vanilla bones. One that a bottling recipe needs becomes its Cooking food first
     * (conversions.yml), as picking it up would have made it.
     */
    private ItemStack convertForBottling(Player p, ItemStack item) {
        if (!secondaries.containsKey("liquid") || FoodItem.fromItem(item) != null) return item;
        ItemStack converted = IngredientConverter.convertIfNeeded(p, item);
        FoodItem food = FoodItem.fromItem(converted);
        if (food == null || !bottlingTakes(food)) return item;
        p.getInventory().setItemInMainHand(converted);
        // setItemInMainHand stores a copy, so take from the hand itself or nothing is used up.
        return p.getInventory().getItemInMainHand();
    }

    /** An empty glass bottle takes what the saucepan made (catsup, mustard, syrup) once it has boiled long enough. */
    private void bottle(Player p) {
        if (!secondaries.containsKey("liquid")) {
            p.sendMessage("§7The saucepan is empty.");
            return;
        }
        BottleRecipe recipe = matchingBottle();
        if (recipe == null) {
            p.sendMessage("§7Nothing in this saucepan goes in a bottle. Use a ladle for sauce.");
            return;
        }
        if (boiled < recipe.getBoilSeconds()) {
            p.sendMessage("§7It needs to boil a while longer (" + (recipe.getBoilSeconds() - boiled) + "s).");
            return;
        }
        FoodParser.Result parsed = FoodParser.parse(recipe.getOutput());
        ItemStack output = null;
        if (parsed != null && parsed.template != null) {
            List<FoodItem> inputs = new ArrayList<>();
            // A cup of water adds nothing to the stock's quality; only its bones do.
            if (liquidSource != null) inputs.add(liquidSource);
            inputs.addAll(slots.values());
            CompositionResult composed = CompositionQualityResolver.compose(p, inputs, CompositionContext.SAUCE_SCOOP);
            CompositionApplier.apply(parsed.template, composed);
            output = ItemBuilder.buildSingleWithQuality(parsed.template, null, composed.getFinalQuality());
        }
        if (output == null) {
            p.sendMessage("§cThis can't be bottled right now.");
            return;
        }
        HeldStack.useOneIntoHand(p, output);
        p.swingMainHand();
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new net.tfminecraft.cooking.events.DishCookedEvent(p, output, "bottle"));
        clear();
    }

    /** Boiling counts again from when the last thing went in. */
    private void resetBoil() {
        boiled = 0;
        f.getVariables().put(VAR_BOILED, 0);
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
    }

    /** Seasonings, sweeteners and other additions are not raw, so the base rebuild skips them. */
    private void restoreIngredients() {
        for (String slotId : f.getType().getSlots().keySet()) {
            if (!slotId.contains("input") || slots.containsKey(slotId)) continue;
            PlacedSlot placed = f.getActiveSlot(slotId).orElse(null);
            if (placed == null) continue;
            FoodItem fi = FoodItem.fromItem(placed.getCurrentItem());
            if (fi != null) slots.put(slotId, fi);
        }
    }

    /** Saucepans filled before colours were saved fall back to their ingredients' colours. */
    private void restoreColours() {
        String saved = stringVariable(VAR_COLOURS);
        if (saved != null && !saved.isBlank()) {
            for (String hex : saved.split(",")) addColour(hex);
            return;
        }
        for (String slotId : slots.keySet()) {
            f.getActiveSlot(slotId).ifPresent(placed -> addColour(ItemCache.getColour(placed.getCurrentItem())));
        }
    }

    /** Saved as "stars:name". Names may contain colons, the star count never does. */
    private static BrewLiquid restoreBrew(String saved) {
        int split = saved == null ? -1 : saved.indexOf(':');
        if (split <= 0) return null;
        try {
            return new BrewLiquid(saved.substring(split + 1), Integer.parseInt(saved.substring(0, split)), null, true);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private String stringVariable(String key) {
        return f.getVariables().get(key) instanceof String text ? text : null;
    }

    private void saveColours() {
        f.getVariables().put(VAR_COLOURS, String.join(",", colours));
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(f);
    }

    /** Keeps the poured liquid's food data for the scoop's quality after a reload. */
    private void saveLiquid(ItemStack poured) {
        String payload = null;
        if (poured != null) {
            ItemStack single = poured.clone();
            single.setAmount(1);
            payload = PotReference.encodeStack(single);
        }
        if (payload == null) f.getVariables().remove(VAR_LIQUID);
        else f.getVariables().put(VAR_LIQUID, payload);
    }

    private static boolean canPourSaucepanLiquid(ItemStack item) {
        return ItemCache.isCupOfWater(item)
                || ItemCache.isCupOfMilk(item)
                || ItemCache.isMilkBucket(item);
    }

    private static boolean isWrongSaucepanWater(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (canPourSaucepanLiquid(item)) {
            return false;
        }
        return item.getType() == Material.POTION
                || item.getType() == Material.WATER_BUCKET
                || ItemCache.isWater(item)
                || ItemCache.isLiquid(item);
    }

    private static String saucepanLiquidModel(ItemStack item) {
        String path = ItemCache.getLiquidModel(item);
        if (path != null && !path.isBlank()) {
            return path;
        }
        if (ItemCache.isCupOfMilk(item) || ItemCache.isMilkBucket(item)) {
            String milk = ItemCache.getLiquidModel("v.milk_bucket");
            if (milk != null) {
                return milk;
            }
        }
        return ItemCache.liquidFallback;
    }

    private static void consumeSaucepanPour(Player player, ItemStack hand) {
        boolean waterCup = ItemCache.isCupOfWater(hand);
        boolean milkCup = ItemCache.isCupOfMilk(hand);
        boolean milkBucket = ItemCache.isMilkBucket(hand);
        hand.setAmount(hand.getAmount() - 1);
        if (waterCup || milkCup) {
            ItemStack empty = CupItems.emptyCup();
            if (hand.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(empty);
            } else {
                ItemStack leftover = InventoryAdder.addItem(player, empty);
                if (leftover != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            player.updateInventory();
            return;
        }
        if (milkBucket) {
            if (hand.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(BucketItems.empty());
            } else {
                ItemStack leftover = InventoryAdder.addItem(player, BucketItems.empty());
                if (leftover != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            player.updateInventory();
        }
    }
}

