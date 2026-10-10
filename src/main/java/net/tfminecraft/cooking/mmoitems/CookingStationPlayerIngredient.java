package net.tfminecraft.cooking.mmoitems;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.api.crafting.ingredient.inventory.PlayerIngredient;
import net.tfminecraft.cooking.item.FoodItem;

/** An inventory stack MMOItems sorted into the cooking ingredient type, with the food it counts as. */
public class CookingStationPlayerIngredient extends PlayerIngredient {
    private final FoodItem food;

    public CookingStationPlayerIngredient(NBTItem item) {
        super(item);
        food = StationFood.describe(item.getItem());
    }

    public FoodItem getFood() {
        return food;
    }
}
