package net.tfminecraft.cooking.mmoitems;

import java.util.List;
import java.util.logging.Logger;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.crafting.ConditionalDisplay;
import net.Indyuce.mmoitems.api.crafting.ingredient.IngredientType;
import net.Indyuce.mmoitems.manager.CraftingManager;

/**
 * Registers the {@code cooking} crafting station ingredient. MMOItems checks newly registered
 * types first, so Cooking food and the plain items Cooking converts are claimed here before the
 * {@code vanilla} type, which only accepts unnamed items. Use {@code cooking{...}} for them.
 */
public final class MMOItemsIngredients {
    public static final String ID = "cooking";

    private MMOItemsIngredients() {}

    static void register(Logger logger) {
        register(MMOItems.plugin.getCrafting(), logger);
    }

    static void register(CraftingManager crafting, Logger logger) {
        crafting.registerIngredient(ID, CookingStationIngredient::new,
                new ConditionalDisplay("&a✔ &7#amount# #item#", "&c✖ &7#amount# #item#"),
                nbt -> StationFood.describe(nbt.getItem()) != null,
                CookingStationPlayerIngredient::new);
        logger.info("Registered the MMOItems crafting station ingredient cooking{item=...}.");
    }

    static void claimFirst(Logger logger) {
        claimFirst(MMOItems.plugin.getCrafting(), logger);
    }

    /**
     * MMOItems registers its ItemsAdder (and Nexo, Oraxen, MythicMobs) types while it enables,
     * after onLoad, and each goes in front. Cooking food built on an ItemsAdder model would then
     * be read as an ItemsAdder item, so put cooking back in front once everything has enabled.
     */
    static void claimFirst(CraftingManager crafting, Logger logger) {
        List<IngredientType<?>> types = crafting.getIngredients();
        for (int i = 1; i < types.size(); i++) {
            if (ID.equals(types.get(i).getId())) {
                types.add(0, types.remove(i));
                logger.info("Moved the cooking station ingredient ahead of " + types.get(1).getId() + ".");
                return;
            }
        }
    }
}
