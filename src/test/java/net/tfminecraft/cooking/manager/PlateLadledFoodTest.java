package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.manager.MealManagersCoverageTest.Kitchen;
import net.tfminecraft.cooking.pot.MashItems;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.Keys;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;

/** A ladle of mashed potatoes goes onto a plate as one of its foods, and the ladle comes back empty. */
class PlateLadledFoodTest {
    Kitchen kitchen;
    Workshop env;
    Station plate;
    PlateManager plates;

    @BeforeEach void open() {
        kitchen = new Kitchen();
        env = kitchen.env;
        plate = kitchen.station("plate");
        plates = new PlateManager();
        for (String id : List.of("display_1", "item_1", "item_2", "sauce", "rest")) {
            SlotDefinition slot = plate.define(id);
            when(slot.isInteractible()).thenReturn(!id.equals("sauce"));
        }
        when(plate.definitions.get("rest").isFurnitureSlot()).thenReturn(true);
    }

    @AfterEach void close() { kitchen.close(); }

    ItemStack ladled(int amount) {
        ItemStack stack = env.stack(kitchen.food("mashed_potatoes", "mash"), Material.PAPER, amount);
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.LADLED, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    FurnitureInteractEvent serve(ItemStack held) {
        env.hold(held);
        FurnitureInteractEvent event = plate.interact();
        plates.interact(event);
        return event;
    }

    @Test
    void aLadleOfMashTakesAFreeFoodSlotBesideWhatIsThereAndTheLadleComesBack() {
        PlacedSlot carrot = plate.place("item_1", env.stack(kitchen.food("carrot", "vegetable"), Material.CARROT, 1));
        assertTrue(serve(ladled(2)).isCancelled());
        PlacedSlot mash = plate.active.get("item_2");
        assertNotNull(mash);
        FoodItem plated = env.resolve(mash.getCurrentItem());
        assertEquals("mashed_potatoes", plated.getId());
        assertFalse(MashItems.isLadled(mash.getCurrentItem()), "On the plate it's a serving, not a ladle");
        verify(mash).applyDisplayData(any());
        verify(carrot, never()).forceModel(any());
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
        assertTrue(env.player.getInventory().contains(Material.STICK, 1), "The empty ladle comes back");
        verify(env.manager).markDirty(plate.furniture);

        // Three foods fill a plate: nothing more goes on, and the ladle stays full.
        serve(ladled(1));
        assertTrue(env.messages.contains("§7There's no room left on this plate."));
        assertTrue(MashItems.isLadled(env.player.getInventory().getItemInMainHand()));
    }

    @Test
    void aPlateThatCannotShowTheServingOrGiveTheLadleBackKeepsTheLadleFull() {
        when(env.api.getCreator().getItemFromPath("tool:ladle")).thenReturn(null);
        serve(ladled(1));
        when(env.api.getCreator().getItemFromPath("tool:ladle")).thenReturn(new ItemStack(Material.AIR));
        serve(ladled(1));
        when(env.api.getCreator().getItemFromPath("tool:ladle")).thenAnswer(inv -> new ItemStack(Material.STICK));
        kitchen.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        serve(ladled(1));
        kitchen.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(new ItemStack(Material.AIR));
        serve(ladled(1));
        assertFalse(plate.active.containsKey("item_1"));
        assertTrue(MashItems.isLadled(env.player.getInventory().getItemInMainHand()));

        // A serving without a model still goes on, in the slot's own pose.
        kitchen.updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenAnswer(inv -> new ItemStack(Material.PAPER));
        FoodItem bare = kitchen.food("mashed_potatoes", "mash");
        bare.setModel(null);
        ItemStack held = env.stack(bare, Material.PAPER, 1);
        var meta = held.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.LADLED, PersistentDataType.BYTE, (byte) 1);
        held.setItemMeta(meta);
        serve(held);
        verify(plate.active.get("item_1"), never()).applyDisplayData(any());

        // Furniture without a type has no slots to fill.
        when(plate.furniture.getType()).thenReturn(null);
        assertNull(PlateManager.freeFoodSlot(plate.furniture));
    }
}
