package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class PlateManagerTest {

    @Test
    void sauceCheckIgnoresUnrecognizedItemsWithoutRemovingThem() {
        Furniture plate = new Furniture("plate", null, UUID.randomUUID());
        ItemStack unrecognized = new ItemStack() {
            @Override
            public boolean hasItemMeta() {
                return false;
            }
        };
        PlacedSlot slot = new PlacedSlot(plate, "food_item");
        slot.setModel(unrecognized);
        plate.getActiveSlots().put(slot.getId(), slot);

        assertFalse(new PlateManager().hasSauce(plate));
        assertSame(unrecognized, slot.getCurrentItem());
        assertSame(slot, plate.getActiveSlots().get("food_item"));
    }

    @Test
    void sauceCheckStillIgnoresEmptyAndDisplaySlots() {
        Furniture plate = new Furniture("plate", null, UUID.randomUUID());
        PlacedSlot empty = new PlacedSlot(plate, "food_item");
        plate.getActiveSlots().put(empty.getId(), empty);
        PlacedSlot display = new PlacedSlot(plate, "display_1");
        display.setModel(new ItemStack() {
            @Override
            public boolean hasItemMeta() {
                throw new AssertionError("Display items must not be checked for sauce");
            }
        });
        plate.getActiveSlots().put(display.getId(), display);

        assertFalse(new PlateManager().hasSauce(plate));
    }
}
