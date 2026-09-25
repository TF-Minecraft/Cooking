package net.tfminecraft.cooking.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class PlateManagerTest {

    @Test
    void unrecognizedItemDoesNotHideLaterSauce() {
        Map<String, PlacedSlot> slots = new LinkedHashMap<>();
        Furniture plate = new Furniture("plate", null, UUID.randomUUID()) {
            @Override
            public Map<String, PlacedSlot> getActiveSlots() {
                return slots;
            }
        };
        ItemStack unrecognized = new ItemStack() {
            @Override
            public boolean hasItemMeta() {
                return false;
            }
        };
        PlacedSlot food = new PlacedSlot(plate, "food_item");
        food.setModel(unrecognized);
        slots.put("food_item", food);
        PlacedSlot sauce = new PlacedSlot(plate, "sauce");
        sauce.setModel(new ItemStack() {});
        slots.put("sauce", sauce);

        assertTrue(new PlateManager().hasSauce(plate));
        assertSame(unrecognized, food.getCurrentItem());
        assertSame(food, slots.get("food_item"));
    }

    @Test
    void occupiedSauceSlotCountsAsSauceWithoutFoodMetadata() {
        Furniture plate = plateWithSauceVisual();

        assertTrue(new PlateManager().hasSauce(plate));
    }

    @Test
    void existingSauceReturnsBeforeTouchingThePlayerOrLadle() {
        Furniture plate = plateWithSauceVisual();
        ItemStack visual = plate.getActiveSlots().get("sauce").getCurrentItem();

        // Null interaction arguments ensure the duplicate attempt exits before using them.
        assertDoesNotThrow(() -> new PlateManager().addSauce(null, plate, null, null));
        assertSame(visual, plate.getActiveSlots().get("sauce").getCurrentItem());
    }

    @Test
    void emptySauceSlotDoesNotCountAsSauce() {
        Furniture plate = new Furniture("plate", null, UUID.randomUUID());
        plate.getActiveSlots().put("sauce", new PlacedSlot(plate, "sauce"));

        assertFalse(new PlateManager().hasSauce(plate));
    }

    private Furniture plateWithSauceVisual() {
        Furniture plate = new Furniture("plate", null, UUID.randomUUID());
        PlacedSlot sauce = new PlacedSlot(plate, "sauce");
        sauce.setModel(new ItemStack() {
            @Override
            public boolean hasItemMeta() {
                throw new AssertionError("Sauce visuals must not be parsed as food");
            }
        });
        plate.getActiveSlots().put("sauce", sauce);
        return plate;
    }

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
