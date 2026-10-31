package net.tfminecraft.cooking.popcorn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.popcorn.PopcornMachineState.Stage;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.cooking.utils.PlayerActionContext;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/**
 * Popcorn machines (popcorn-machines.yml). Right-click one with corn to put one in (up to max-input), or with a
 * topping (salt, sugar, butter, caramel; up to max-toppings, each once). Right-click it with an empty hand to start
 * it: it pops (PopcornAnimation), and once it stops, a right-click takes out the popcorn: yield pieces per corn, each
 * with the corn's stars and every topping. Breaking it gives back what
 * went in, or the popcorn once it's popped.
 */
public final class PopcornMachineHandler implements Listener {
    private static final String FRESHNESS = "freshness";
    private static final String ROTTEN = "rotten";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent e) {
        Furniture furniture = e.getFurniture();
        PopcornMachine machine = PopcornMachineConfig.machineFor(furniture);
        if (machine == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        if (PlayerActionContext.captureOrRefuse(player) == null) return;

        Stage stage = PopcornMachineState.stage(furniture);
        if (stage == Stage.RUNNING) {
            if (PopcornAnimation.isRunning(furniture)) {
                player.sendMessage("§7It's still popping.");
                return;
            }
            // A restart cut the show short: the popcorn is done.
            stage = Stage.READY;
            done(furniture, machine);
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (stage == Stage.READY) {
            collect(player, furniture, machine);
        } else if (isEmpty(hand)) {
            start(player, furniture, machine);
        } else {
            load(player, furniture, machine, hand);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent e) {
        Furniture furniture = e.getFurniture();
        PopcornMachine machine = PopcornMachineConfig.machineFor(furniture);
        if (machine == null) return;
        PopcornAnimation.stop(furniture);
        List<ItemStack> drops = PopcornMachineState.stage(furniture) == Stage.READY
                ? popcorn(machine, furniture) : refund(furniture);
        Location at = furniture.getLoc();
        for (ItemStack drop : drops) at.getWorld().dropItemNaturally(at, drop);
        PopcornMachineState.clear(furniture);
    }

    private void load(Player player, Furniture furniture, PopcornMachine machine, ItemStack hand) {
        FoodItem food = FoodItem.fromItem(IngredientConverter.convertIfNeeded(player, hand));
        String name = machine.getInputName();
        if (food != null && machine.takes(food)) {
            if (isRotten(food)) {
                player.sendMessage("§cThat " + name + " has gone off.");
                return;
            }
            // One per click, so a player holding more puts in just as many as they want.
            int have = PopcornMachineState.count(PopcornMachineState.foods(PopcornMachineState.input(furniture)));
            if (have >= machine.getMaxInput()) {
                player.sendMessage("§cIt's full: it takes " + machine.getMaxInput() + " " + name + ".");
                return;
            }
            PopcornMachineState.addInput(furniture, food);
            player.getInventory().setItemInMainHand(less(hand, 1));
        } else {
            Topping topping = machine.toppingFor(food);
            if (topping == null) {
                player.sendMessage("§cOnly " + name + " and " + toppingNames(machine) + " go in.");
                return;
            }
            List<FoodItem> toppings = PopcornMachineState.foods(PopcornMachineState.toppings(furniture));
            if (toppings.size() >= machine.getMaxToppings()) {
                player.sendMessage("§cIt already has " + machine.getMaxToppings() + " toppings.");
                return;
            }
            for (FoodItem other : toppings) {
                if (machine.toppingFor(other) == topping) {
                    player.sendMessage("§cIt already has " + lower(topping.name()) + ".");
                    return;
                }
            }
            PopcornMachineState.addTopping(furniture, FoodParser.toString(food, 1));
            player.getInventory().setItemInMainHand(less(hand, 1));
        }
        PopcornMachineState.setStage(furniture, Stage.LOADING);
        show(furniture, machine);
        markDirty(furniture);
        player.swingMainHand();
        sound(furniture, Sound.ITEM_BUNDLE_INSERT, 1f);
    }

    private void start(Player player, Furniture furniture, PopcornMachine machine) {
        List<FoodItem> input = PopcornMachineState.foods(PopcornMachineState.input(furniture));
        if (input.isEmpty()) return;
        List<ItemStack> popcorn = popcorn(machine, furniture);
        if (popcorn.isEmpty()) {
            player.sendMessage("§cThis machine can't make anything: check its output in popcorn-machines.yml.");
            return;
        }
        PopcornMachineState.setStage(furniture, Stage.RUNNING);
        show(furniture, machine);
        markDirty(furniture);
        player.swingMainHand();
        sound(furniture, Sound.BLOCK_LEVER_CLICK, 1.4f);
        PopcornAnimation.play(furniture, machine, popcorn.get(0), PopcornMachineState.count(input),
                looks(machine, furniture), () -> done(furniture, machine));
    }

    private static void done(Furniture furniture, PopcornMachine machine) {
        PopcornMachineState.setStage(furniture, Stage.READY);
        show(furniture, machine);
        markDirty(furniture);
    }

    /** The popcorn goes to the player (what doesn't fit drops at their feet) and the machine is empty again. */
    private void collect(Player player, Furniture furniture, PopcornMachine machine) {
        for (ItemStack piece : popcorn(machine, furniture)) {
            ItemStack left = InventoryAdder.addItem(player, piece);
            if (left != null) player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        PopcornMachineState.clear(furniture);
        show(furniture, machine);
        markDirty(furniture);
        player.swingMainHand();
        sound(furniture, Sound.ENTITY_ITEM_PICKUP, 1f);
    }

    /**
     * The popcorn the machine's corn makes: yield pieces per corn, with that corn's stars and lineage, every topping's
     * track and the look of the first topping put in that has one, in stacks no bigger than the item stacks.
     */
    static List<ItemStack> popcorn(PopcornMachine machine, Furniture furniture) {
        List<String> tracks = tracks(machine, furniture);
        Topping look = lookTopping(machine, furniture);
        List<ItemStack> out = new ArrayList<>();
        for (FoodItem corn : PopcornMachineState.foods(PopcornMachineState.input(furniture))) {
            FoodParser.Result parsed = FoodParser.parse(machine.getOutput());
            if (parsed == null || parsed.template == null) continue;
            FoodItem food = parsed.template;
            for (String track : tracks) food.addTagTrack(track);
            if (look != null) giveStep(food, machine.getLookTrack(), look.look());
            if (!corn.getLineage().isEmpty()) food.setLineage(corn.getLineage());
            ItemStack stack = ItemBuilder.buildSingleWithQuality(food, null, corn.getQualityMin());
            if (stack == null) continue;
            int amount = corn.getAmount() * machine.getYield();
            while (amount > 0) {
                ItemStack part = stack.clone();
                part.setAmount(Math.min(amount, stack.getMaxStackSize()));
                amount -= part.getAmount();
                out.add(part);
            }
        }
        return out;
    }

    /** The corn and toppings put in, as items again. */
    static List<ItemStack> refund(Furniture furniture) {
        List<FoodItem> foods = PopcornMachineState.foods(PopcornMachineState.input(furniture));
        foods.addAll(PopcornMachineState.foods(PopcornMachineState.toppings(furniture)));
        List<ItemStack> out = new ArrayList<>();
        for (FoodItem food : foods) out.addAll(ItemBuilder.build(food, false, null));
        return out;
    }

    /** The tags.yml tracks of the toppings put in. */
    static List<String> tracks(PopcornMachine machine, Furniture furniture) {
        List<String> tracks = new ArrayList<>();
        for (FoodItem food : PopcornMachineState.foods(PopcornMachineState.toppings(furniture))) {
            Topping topping = machine.toppingFor(food);
            if (topping != null) tracks.add(topping.track());
        }
        return tracks;
    }

    /** The first topping put in that gives the popcorn a look: butter then caramel makes buttered popcorn. */
    static Topping lookTopping(PopcornMachine machine, Furniture furniture) {
        for (FoodItem food : PopcornMachineState.foods(PopcornMachineState.toppings(furniture))) {
            Topping topping = machine.toppingFor(food);
            if (topping != null && topping.look() != null) return topping;
        }
        return null;
    }

    /** The look step the popcorn gets, as a list of tag ids for the heap's look (empty for plain popcorn). */
    static List<String> looks(PopcornMachine machine, Furniture furniture) {
        Topping look = lookTopping(machine, furniture);
        return look == null ? List.of() : List.of(look.look());
    }

    /** Puts the food's track at this step; nothing when tags.yml has no such track or step. */
    private static void giveStep(FoodItem food, String trackId, String stepId) {
        TagTrack template = TrackLoader.getByString(trackId);
        if (template == null) return;
        TagTrack track = new TagTrack(template);
        for (TagStep step : track.getSteps()) {
            if (step.getId().equalsIgnoreCase(stepId)) {
                track.forceSetValue((int) step.getRequiredValue());
                food.addOrModifyTrack(track);
                return;
            }
        }
    }

    /**
     * Shows what's in the machine: kernels in the kettle while corn waits in it, each topping on its slot until it has
     * gone into the popcorn, and the heap once it has popped.
     */
    static void show(Furniture furniture, PopcornMachine machine) {
        Stage stage = PopcornMachineState.stage(furniture);
        boolean loaded = !PopcornMachineState.input(furniture).isEmpty();
        model(furniture, machine.getKettleSlot(),
                resolve(machine.kettleFor(loaded && (stage == Stage.LOADING || stage == Stage.RUNNING))));
        List<String> toppings = PopcornMachineState.toppings(furniture);
        List<String> slots = machine.getToppingSlots();
        for (int i = 0; i < slots.size(); i++) {
            boolean shown = i < toppings.size() && stage != Stage.READY;
            model(furniture, slots.get(i), shown ? ItemBuilder.buildSingleString(toppings.get(i), null) : null);
        }
        ItemStack heap = null;
        if (stage == Stage.READY) {
            int input = PopcornMachineState.count(PopcornMachineState.foods(PopcornMachineState.input(furniture)));
            heap = resolve(machine.heapFor(machine.heapLevel(input), looks(machine, furniture)));
        }
        model(furniture, machine.getHeapSlot(), heap);
    }

    private static ItemStack resolve(String ref) {
        return ref == null || ref.isBlank() ? null : ItemRef.resolve(ref);
    }

    /** Shows the item on the slot, or clears it for null; slots the furniture doesn't have are skipped. */
    private static void model(Furniture furniture, String slot, ItemStack item) {
        if (furniture.getType() == null || furniture.getType().getSlot(slot) == null) return;
        if (item != null) {
            furniture.getOrCreatePlacedSlot(slot).forceModel(item);
        } else if (furniture.hasActiveSlot(slot)) {
            furniture.getOrCreatePlacedSlot(slot).clearModel();
        }
    }

    private static boolean isRotten(FoodItem food) {
        TagTrack freshness = food.getTagTrack(FRESHNESS);
        TagStep step = freshness == null ? null : freshness.getCurrentStep();
        return step != null && ROTTEN.equals(step.getId());
    }

    /** "salt, sugar, butter or caramel". */
    static String toppingNames(PopcornMachine machine) {
        List<String> names = new ArrayList<>();
        for (Topping topping : machine.getToppings()) names.add(lower(topping.name()));
        if (names.isEmpty()) return "nothing else";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    /** The stack with this many taken off; null when none are left. */
    private static ItemStack less(ItemStack stack, int taken) {
        if (stack.getAmount() <= taken) return null;
        ItemStack rest = stack.clone();
        rest.setAmount(stack.getAmount() - taken);
        return rest;
    }

    private static void sound(Furniture furniture, Sound sound, float pitch) {
        Location at = furniture.getLoc();
        at.getWorld().playSound(at, sound, 1f, pitch);
    }

    private static void markDirty(Furniture furniture) {
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
    }
}
