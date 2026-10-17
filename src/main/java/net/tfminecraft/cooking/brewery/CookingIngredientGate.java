package net.tfminecraft.cooking.brewery;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.dre.brewery.recipe.Ingredient;
import com.dre.brewery.recipe.RecipeItem;

/** Lets metadata-bearing Cooking items reach the resolver before Brewery rejects their visual base. */
final class CookingIngredientGate extends RecipeItem {
    CookingIngredientGate() { setAmount(1); makeImmutable(); }
    @Override public boolean matches(ItemStack item) { return BrewingIngredientResolver.isCookingItem(item); }
    @Override public boolean matches(Ingredient ingredient) { return false; }
    @Override public boolean hasMaterials() { return false; }
    @Override public List<Material> getMaterials() { return List.of(); }
    @Override public String getDebugID() { return "cooking:identity_gate"; }
    @Override public Ingredient toIngredient(ItemStack item) { throw new IllegalStateException("Cooking ingredient must be resolved first"); }
    @Override public Ingredient toIngredientGeneric() { throw new IllegalStateException("Cooking gate is not a brewing ingredient"); }
    @Override public boolean equals(Object other) { return this == other; }
    @Override public int hashCode() { return System.identityHashCode(this); }
}
