package net.tfminecraft.cooking.brewery;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.dre.brewery.api.events.IngedientAddEvent;
import com.dre.brewery.recipe.BCauldronRecipe;
import com.dre.brewery.recipe.RecipeItem;
import com.dre.brewery.recipe.SimpleItem;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.tlibs.TLibs;

/** Replaces only Brewery's internal ingredient copy; Brewery consumes the original item normally. */
public final class BreweryIngredientListener implements Listener {
    public static Runnable register(JavaPlugin plugin) {
        CookingIngredientGate gate = new CookingIngredientGate();
        BreweryIngredientListener listener = new BreweryIngredientListener();
        // Brewery only appends to this list when it reloads, so the gate stays until cleanup. A cauldron
        // recipe would also carry it, but would break /brew simulate, which cannot describe custom items.
        BCauldronRecipe.acceptedCustom.add(gate);
        Runnable cleanup = () -> {
            HandlerList.unregisterAll(listener);
            BCauldronRecipe.acceptedCustom.removeIf(item -> item == gate);
        };
        try {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        } catch (RuntimeException | LinkageError exception) {
            cleanup.run();
            throw exception;
        }
        return cleanup;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIngredient(IngedientAddEvent event) {
        ItemStack input = event.getIngredient();
        try {
            if (!BrewingIngredientResolver.isCookingItem(input)) {
                // Preserve explicit custom ingredient recipes, but never treat another custom item as its base material.
                if (event.getRecipeItem() instanceof SimpleItem) {
                    String path = TLibs.getItemAPI().getChecker().getAsStringPath(input);
                    if (path.startsWith("ia.") || path.startsWith("m.")) event.setCancelled(true);
                }
                return;
            }
            String source = BrewingIngredientResolver.sourceFor(FoodItem.fromItem(input));
            ItemStack canonical = source == null ? null : ItemRef.resolve(source);
            if (canonical == null || canonical.getType().isAir() || BrewingIngredientResolver.isCookingItem(canonical)) {
                event.setCancelled(true);
                return;
            }
            RecipeItem match = RecipeItem.getMatchingRecipeItem(canonical, false);
            if (match == null || (!source.startsWith("v.") && match instanceof SimpleItem)) {
                event.setCancelled(true);
                return;
            }
            canonical = canonical.clone();
            canonical.setAmount(1);
            event.setIngredient(canonical);
        } catch (RuntimeException | LinkageError exception) {
            event.setCancelled(true);
            throw exception;
        }
    }
}
