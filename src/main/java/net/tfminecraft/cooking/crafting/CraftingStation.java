package net.tfminecraft.cooking.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.fishing.SeafoodCutting;
import net.tfminecraft.cooking.fishing.SeafoodPortions;
import net.tfminecraft.cooking.fishing.SeafoodYield;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionApplier;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;

public class CraftingStation {
    private Furniture f;
    private String id;
    private String furniture;
    private boolean singleOrigin;
    private int maxSlots;
    private String tool;
    private CraftingRecipe currentRecipe;
    private List<CraftingRecipe> recipes = new ArrayList<>();
    private HashMap<String, ItemStack> slots = new HashMap<>();
    /** Models for 1, 2, ... recipe inputs; when set, the inputs show as one level rising in the furniture. */
    private List<String> fillModels = new ArrayList<>();
    /** Item handed back for each input poured in (an empty cup); poured inputs stay in the furniture. */
    private String pourReturn = "none";
    /** Right-clicking the furniture this high or higher (blocks above its base) with an empty hand crafts; -1 = off. */
    private double pressHeight = -1;

    public CraftingStation(String key, ConfigurationSection config) {
        this.id = key;
        this.furniture = config.getString("furniture", "none");
        this.singleOrigin = config.getBoolean("single-origin", false);
        this.maxSlots = config.getInt("max-slots", 0);
        this.tool = config.getString("tool", "none");
        this.fillModels = config.getStringList("fill-models");
        this.pourReturn = config.getString("pour-return", "none");
        this.pressHeight = config.getDouble("press-height", -1);

        if(config.isConfigurationSection("recipes")) {
            for (String recipeKey : config.getConfigurationSection("recipes").getKeys(false)) {
                ConfigurationSection recipeSection = config.getConfigurationSection("recipes").getConfigurationSection(recipeKey);
                recipes.add(new CraftingRecipe(recipeKey, recipeSection));
            }
        }
    }

    public CraftingStation(Furniture f, CraftingStation station) {
        this.f = f;
        this.id = station.id;
        this.furniture = station.furniture;
        this.singleOrigin = station.singleOrigin;
        this.maxSlots = station.maxSlots;
        this.tool = station.tool;
        this.currentRecipe = station.currentRecipe;
        this.slots = new HashMap<>(station.slots);
        this.recipes = new ArrayList<>(station.recipes);
        this.fillModels = station.fillModels;
        this.pourReturn = station.pourReturn;
        this.pressHeight = station.pressHeight;
    }

    public Furniture getFurniture() {
        return f;
    }

    public String getId() {
        return id;
    }

    public String getBlockId() {
        return furniture;
    }

    public boolean isSingleOrigin() {
        return singleOrigin;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public HashMap<String, ItemStack> getSlots() {
        return slots;
    }

    public boolean hasRecipe() {
        return currentRecipe != null;
    }

    public boolean hasFreeSlots() {
        int free = 0;
        for(SlotDefinition fs : f.getType().getSlots().values()) {
            if(!slots.containsKey(fs.getId()) && fs.getId().contains("input")) {
                free++;
            }
        }
        return free != 0;
    }

    public boolean canAddItem(ItemStack i) {
        if (CarvableRoastUtils.isCarvable(i)) return false;
        if(!hasFreeSlots()) return false;
        if(currentRecipe != null && !currentRecipe.canAdd(i)) return false;
        if(slots.size() >= maxSlots) return false;

        if(!singleOrigin) return true;

        FoodItem fi = FoodItem.fromItem(i);
        if(fi == null) return true;

        for(ItemStack stack : slots.values()) {
            FoodItem fs = FoodItem.fromItem(stack);
            if(fs != null && (fs.getOrigin() == null
                    ? fi.getOrigin() != null : !fs.getOrigin().equalsIgnoreCase(fi.getOrigin()))) {
                return false;
            }
        }
        return true;
    }

    public void add(ItemStack item, String slotId) {
        slots.put(slotId, item);

        if(currentRecipe == null) {
            for(CraftingRecipe cr : recipes) {
                if(cr.canAdd(item)) {
                    currentRecipe = cr;
                    break;
                }
            }
        }
    }

    public void rebuildFromFurniture() {
        slots.clear();
        currentRecipe = null;
        if (f == null || f.getType() == null) return;

        List<String> inputIds = f.getType().getSlots().keySet().stream()
                .filter(id -> id.contains("input"))
                .sorted()
                .collect(Collectors.toList());

        for (String slotId : inputIds) {
            f.getActiveSlot(slotId).ifPresent(ps -> {
                ItemStack item = ps.getCurrentItem();
                if (item != null && !item.getType().isAir()) {
                    add(item.clone(), slotId);
                }
            });
        }
        refreshFill();
    }

    public void addItem(FurnitureSlotItemAddEvent e) {
        ItemStack item = e.getItem();
        if(!canAddItem(item)) {
            e.setCancelled(true);
            return;
        }
        add(item, e.getSlot().getId());
        if (showsFill() && isFillInput(item)) {
            refreshFill();
            String slotId = e.getSlot().getId();
            e.setItem(slots.get(slotId));
            e.setDisplayData(centred(slotId));
            if (pours()) giveBack(e.getPlayer());
            return;
        }
        FoodItem fi = FoodItem.fromItem(item);
        if(fi != null) {
            if (fi.getCarveSequenceId() != null) {
                CarvableRoastUtils.readCarveState(fi, item);
                e.setDisplayData(CarvableRoastUtils.getStageModelData(fi).getDisplayData(e.getFurniture().getId()));
            } else {
                e.setDisplayData(fi.getModelData().getDisplayData(e.getFurniture().getId()));
            }
        }
    }

    public void removeItem(FurnitureSlotItemTakeEvent e) {
        if (pours() && isFillInput(e.getItem())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§7What's poured in can't go back into its container. Add more and press the handle.");
            return;
        }
        slots.remove(e.getSlot().getId());
        if(slots.isEmpty()) {
            currentRecipe = null;
        }
        // What comes out looks as it does in hand, not as it was drawn in the furniture (slices on the board).
        ItemStack held = asHeld(e.getItem());
        if (e.getPlayer() != null && FoodItem.fromItem(held) != null) {
            // Food goes in like a pickup, onto a stack of the same food made at another time, not only onto exact copies.
            ItemStack rest = InventoryAdder.addItem(e.getPlayer(), held);
            if (rest != null) e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), rest);
            held = held.clone();
            held.setAmount(0);
        }
        e.setItem(held);
        if (showsFill()) refreshFill();
    }

    public void craft() {
        craft(null);
    }

    public void craft(Player p) {
        if(currentRecipe == null) return;
        if(slots.isEmpty()) return;
        int ratio = currentRecipe.getRatio();
        if (ratio <= 0) return;
        // A finished item still in the station (a jar of jam, a cheese) is not an ingredient.
        CraftingRecipe recipe = currentRecipe;
        int available = slots.values().stream().filter(recipe::canAdd).mapToInt(ItemStack::getAmount).sum();
        if(available < ratio) return;
        if ("cut_seafood".equals(currentRecipe.getId())) {
            craftSeafood(p);
            return;
        }
        if (recipe.inherits()) {
            cutInPlace(p, recipe);
            return;
        }

        String origin = "none";
        List<FoodItem> consumedInputs = new ArrayList<>();

        int outputAmount = available / ratio;
        int used = outputAmount * ratio;
        List<ItemStack> remainingInputs = new ArrayList<>();
        List<String> usedSlots = new ArrayList<>();
        for (Map.Entry<String, ItemStack> entry : slots.entrySet()) {
            ItemStack source = entry.getValue();
            if (!recipe.canAdd(source)) continue;
            usedSlots.add(entry.getKey());
            int take = Math.min(used, source.getAmount());
            if (take > 0) {
                FoodItem fi = FoodItem.fromItem(source);
                if (fi != null) {
                    for (int i = 0; i < take; i++) consumedInputs.add(fi);
                    if (isSingleOrigin()) origin = fi.getOrigin();
                }
                used -= take;
            }
            if (take < source.getAmount()) {
                ItemStack remaining = source.clone();
                remaining.setAmount(source.getAmount() - take);
                remainingInputs.add(remaining);
            }
        }

        Built built = build(p, recipe, consumedInputs, origin);
        if (built == null) return;
        FoodItem item = built.food();
        ItemStack output = built.stack();
        output.setAmount(outputAmount * recipe.getYield());

        // Prepare the complete result before taking any ingredient from the board.
        var modelData = item.getModelData();
        var displayData = modelData == null ? null : modelData.getDisplayData(f.getId());
        for (String slotId : usedSlots) {
            f.getActiveSlot(slotId).ifPresent(PlacedSlot::clearModel);
            f.removeActiveSlot(slotId);
            slots.remove(slotId);
        }
        for (ItemStack remaining : remainingInputs) {
            boolean poured = showsFill() && isFillInput(remaining);
            if (pours() && poured) continue; // poured inputs already gave their container back
            f.getLoc().getWorld().dropItem(f.getLoc(), poured ? asHeld(remaining) : remaining)
                    .setVelocity(new Vector(Math.random()*0.2-0.1, 0.1, Math.random()*0.2-0.1));
        }
        currentRecipe = null;
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);

        boolean onBoard = false;
        for(SlotDefinition def : f.getType().getSlots().values()) {
            if(f.hasActiveSlot(def.getId())) continue;
            if(!def.getId().contains("input")) continue;
            PlacedSlot slot = f.getOrCreatePlacedSlot(def.getId());
            // The result shows its model for this furniture, if it has one (a cheese in the mould).
            ItemStack placed = ItemUpdater.applyItemUpdate(output.clone(), item, f.getId());
            if (placed == null) placed = output;
            slot.forceModel(placed);
            onBoard = true;
            if (showsFill() && recipe.fills()) slot.applyDisplayData(centred(def.getId()));
            else if (displayData != null) slot.applyDisplayData(displayData);

            // Attempt to match new currentRecipe based on the output item
            CraftingRecipe newRecipe = null;
            for (CraftingRecipe cr : recipes) {
                if (cr.canAdd(output)) {
                    newRecipe = cr;
                    break;
                }
            }
            slots.put(def.getId(), placed);

            // Assign new recipe or clear it
            currentRecipe = newRecipe;
            break;
        }

        if(!onBoard) {
            f.getLoc().getWorld().dropItem(
                f.getLoc(),
                output
            ).setVelocity(new Vector(Math.random()*0.2-0.1, 0.1, Math.random()*0.2-0.1));
        }

        if(p != null) {
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new net.tfminecraft.cooking.events.DishCookedEvent(p, output, "craft"));
        }
    }

    private record Built(FoodItem food, ItemStack stack) {}

    /** The recipe's output made from these inputs, or null when its output line doesn't make an item. */
    private Built build(Player p, CraftingRecipe recipe, List<FoodItem> inputs, String origin) {
        String data = recipe.getOutput();
        if(isSingleOrigin() && origin != null && !origin.equalsIgnoreCase("none")) {
            data = data.replace("{origin}", origin);
        }

        FoodParser.Result parsed = FoodParser.parse(data);
        if (parsed == null || parsed.template == null) return null;
        FoodItem item = parsed.template;

        if(recipe.isProcessed()) {
            item.addOrModifyTrack(TrackLoader.getByString("processed"));
        }

        CompositionResult composed = CompositionQualityResolver.compose(p, inputs, CompositionContext.CUTTING_BOARD);
        Map<String, Integer> ownAge = recipe.keepsOutputAge() ? ageOf(item) : null;
        CompositionApplier.apply(item, composed);
        if (ownAge != null) restoreAge(item, ownAge);
        if (recipe.inherits() && !inputs.isEmpty()) inherit(item, inputs.get(0));
        ItemStack output = ItemBuilder.buildSingleWithQuality(item, null, composed.getFinalQuality());
        if (output == null || output.getType().isAir()) return null;
        return new Built(item, output);
    }

    /**
     * An inheriting recipe cuts each input where it lies: a loaf's slices take its slot and stay what that loaf was,
     * so two different loaves can share the board.
     */
    private void cutInPlace(Player p, CraftingRecipe recipe) {
        List<ItemStack> outputs = new ArrayList<>();
        for (String slotId : new ArrayList<>(slots.keySet())) {
            ItemStack source = slots.get(slotId);
            FoodItem input = recipe.canAdd(source) ? FoodItem.fromItem(source) : null;
            if (input == null) continue;
            Built built = build(p, recipe, Collections.nCopies(source.getAmount(), input), input.getOrigin());
            if (built == null) continue;
            built.stack().setAmount(source.getAmount() * recipe.getYield());
            ItemStack placed = ItemUpdater.applyItemUpdate(built.stack().clone(), built.food(), f.getId());
            ItemStack shown = placed == null ? built.stack() : placed;
            slots.put(slotId, shown);
            ModelData model = built.food().getModelData();
            f.getActiveSlot(slotId).ifPresent(slot -> {
                slot.setCurrentItem(shown);
                if (model != null) slot.applyDisplayData(model.getDisplayData(f.getId()));
            });
            outputs.add(built.stack());
        }
        if (outputs.isEmpty()) return;
        currentRecipe = null;
        f.getLoc().getWorld().playSound(f.getLoc(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
        if (p == null) return;
        for (ItemStack output : outputs) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new DishCookedEvent(p, output, "craft"));
        }
    }

    private void craftSeafood(Player player) {
        String sourceId = null;
        ItemStack source = null;
        FoodItem whole = null;
        SeafoodYield yield = null;
        for (Map.Entry<String, ItemStack> entry : new ArrayList<>(slots.entrySet())) {
            FoodItem food = FoodItem.fromItem(entry.getValue());
            if (food == null || !"seafood_whole".equalsIgnoreCase(food.getId())) {
                continue;
            }
            SeafoodYield planned = SeafoodCutting.plan(food.getSeafoodCutType(), food.getCatchSizeCm());
            if (planned == null) {
                continue;
            }
            sourceId = entry.getKey();
            source = entry.getValue();
            whole = food;
            yield = planned;
            break;
        }
        if (sourceId == null) {
            return;
        }
        final String slotId = sourceId;
        final ItemStack held = source;
        ItemStack output = SeafoodPortions.build(whole, yield);
        if (output == null) {
            return;
        }

        if (source.getAmount() > 1) {
            source.setAmount(source.getAmount() - 1);
            slots.put(slotId, held);
            f.getActiveSlot(slotId).ifPresent(slot -> slot.setCurrentItem(held));
        } else {
            slots.remove(slotId);
            f.getActiveSlot(slotId).ifPresent(slot -> {
                slot.clearModel();
                f.removeActiveSlot(slotId);
            });
        }

        f.getLoc().getWorld().playSound(f.getLoc(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
        FoodItem portion = FoodItem.fromItem(output);
        boolean onBoard = false;
        for (SlotDefinition def : f.getType().getSlots().values()) {
            if (f.hasActiveSlot(def.getId())) continue;
            if (!def.getId().contains("input")) continue;
            PlacedSlot slot = f.getOrCreatePlacedSlot(def.getId());
            slot.forceModel(output);
            onBoard = true;
            if (portion != null && portion.getModelData() != null) {
                slot.applyDisplayData(portion.getModelData().getDisplayData(f.getId()));
            }
            slots.put(def.getId(), output);
            break;
        }
        if (!onBoard) {
            f.getLoc().getWorld().dropItem(f.getLoc(), output)
                    .setVelocity(new Vector(Math.random() * 0.2 - 0.1, 0.1, Math.random() * 0.2 - 0.1));
        }

        currentRecipe = null;
        for (ItemStack stack : slots.values()) {
            for (CraftingRecipe recipe : recipes) {
                if (recipe.canAdd(stack)) {
                    currentRecipe = recipe;
                    break;
                }
            }
            if (currentRecipe != null) {
                break;
            }
        }

        if (player != null) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new DishCookedEvent(player, output, "craft"));
        }
    }

    public void remove(FurnitureBreakEvent e) {
        pressOrBreak(e);
        if (!e.isCancelled()) release();
    }

    private void pressOrBreak(FurnitureBreakEvent e) {
        if(!e.hasPlayer()) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        if(tool.equalsIgnoreCase("none")) return;

        if(TLibs.getItemAPI().getChecker().checkItemWithPath(item, tool)) {
            if(slots.isEmpty()) return;
            e.setCancelled(true);
            craft(p);
        }
    }

    /** A slice of Sweet Banana Bread stays Sweet Banana: origin, fillers and every tag the output line doesn't set. */
    private static void inherit(FoodItem output, FoodItem input) {
        if (output.getOrigin() == null || output.getOrigin().isBlank()) output.setOrigin(input.getOrigin());
        for (String ingredient : input.getIngredients()) output.addIngredient(ingredient);
        for (TagTrack track : input.getTagTracks()) {
            if (!output.hasTagTrack(track.getId())) output.addOrModifyTrack(track);
        }
    }

    private static Map<String, Integer> ageOf(FoodItem item) {
        Map<String, Integer> age = new HashMap<>();
        for (TagTrack track : item.getTagTracks()) {
            if (track.isAgeable()) age.put(track.getId(), track.getValue());
        }
        return age;
    }

    /** Undoes the ingredients' age: a fresh cheese doesn't inherit how old its milk was. */
    private static void restoreAge(FoodItem item, Map<String, Integer> ownAge) {
        for (TagTrack track : new ArrayList<>(item.getTagTracks())) {
            if (!track.isAgeable()) continue;
            Integer value = ownAge.get(track.getId());
            if (value == null) item.removeTrack(track.getId());
            else track.setValue(value);
        }
    }

    /** True when the station shows its recipe inputs as one fill level, like milk poured into a mould. */
    public boolean showsFill() {
        return !fillModels.isEmpty();
    }

    private boolean isFillInput(ItemStack item) {
        for (CraftingRecipe cr : recipes) {
            if (cr.fills() && cr.canAdd(item)) return true;
        }
        return false;
    }

    /**
     * The n-th input (by slot id) shows the n-th fill model, centred on the furniture, so the layers stack into one
     * rising level. Each input draws its own layer: identical copies in the same place would flicker against each other.
     */
    private void refreshFill() {
        if (!showsFill()) return;
        List<String> inputs = slots.entrySet().stream().filter(e -> isFillInput(e.getValue()))
                .map(Map.Entry::getKey).sorted().collect(Collectors.toList());
        for (int n = 0; n < inputs.size(); n++) {
            String slotId = inputs.get(n);
            ItemStack input = slots.get(slotId);
            String model = fillModels.get(Math.min(n, fillModels.size() - 1));
            // The fill keeps the input's food data, so it still crafts and can be taken back out.
            ItemStack shown = ItemRef.apply(forOrigin(model, input), input);
            if (shown == null) continue;
            slots.put(slotId, shown);
            f.getActiveSlot(slotId).ifPresent(slot -> {
                slot.setCurrentItem(shown);
                slot.applyDisplayData(centred(slotId));
            });
        }
    }

    /** Fills a {origin} placeholder with the input's origin (Melon Slice becomes melon_slice), so each fruit gets its own fill. */
    private static String forOrigin(String model, ItemStack item) {
        if (!model.contains("{origin}")) return model;
        FoodItem fi = FoodItem.fromItem(item);
        String origin = fi == null || fi.getOrigin() == null ? "none" : fi.getOrigin();
        return model.replace("{origin}", origin.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    /** Moves a slot's display back to the furniture's centre, so fill and output models line up with the furniture model. */
    private DisplayData centred(String slotId) {
        DisplayData data = new DisplayData();
        SlotDefinition def = f.getType().getSlot(slotId);
        if (def == null) return data;
        Vector at = def.getOffset().add(def.getDisplayPosition());
        data.setxPos((float) -at.getX());
        data.setyPos((float) -at.getY());
        data.setzPos((float) -at.getZ());
        return data;
    }

    /** The look an item has outside the furniture (a cup of milk, a cheese), keeping its food data. */
    private ItemStack asHeld(ItemStack item) {
        FoodItem fi = FoodItem.fromItem(item);
        if (fi == null) return item;
        ItemStack held = ItemUpdater.applyItemUpdate(item, fi, null);
        return held == null ? item : held;
    }

    /** A broken station drops what was put in it, looking as it does in hand, not its fill or furniture models. */
    private void release() {
        for (Map.Entry<String, ItemStack> entry : slots.entrySet()) {
            // Poured inputs spill; their containers were handed back when they went in.
            ItemStack held = pours() && isFillInput(entry.getValue()) ? null : asHeld(entry.getValue());
            f.getActiveSlot(entry.getKey()).ifPresent(slot -> slot.setCurrentItem(held));
        }
    }

    private boolean pours() {
        return showsFill() && !pourReturn.equalsIgnoreCase("none");
    }

    private void giveBack(Player p) {
        ItemStack container = TLibs.getItemAPI().getCreator().getItemFromPath(pourReturn);
        if (container == null) return;
        for (ItemStack rest : p.getInventory().addItem(container).values()) {
            p.getWorld().dropItem(p.getLocation(), rest);
        }
    }

    /** A right-click with an empty hand on the top of the furniture (a press's handle) crafts, instead of a tool. */
    public void interact(FurnitureInteractEvent e) {
        if (pressHeight < 0 || e.getClickPoint() == null) return;
        Player p = e.getPlayer();
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return;
        if (e.getClickPoint().getY() - f.getLoc().getBlockY() < pressHeight) return;
        // The handle never takes items out or picks the furniture up.
        e.setCancelled(true);
        craft(p);
    }
}
