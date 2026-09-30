package net.tfminecraft.cooking.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;

class CookingFurnitureTest {

    @Test
    void cookingFurnitureIsRecognised() {
        assertTrue(FurnitureCache.isCookingFurniture(furniture("ia.tfmc_cooking:plate")));
    }

    @Test
    void magicPedestalIsNotCookingFurniture() {
        assertFalse(FurnitureCache.isCookingFurniture(furniture("ia.tfmc:pedestal")));
    }

    @Test
    void furnitureWithoutTypeIsNotCookingFurniture() {
        assertFalse(FurnitureCache.isCookingFurniture(mock(Furniture.class)));
    }

    private static Furniture furniture(String itemPath) {
        FurnitureType type = mock(FurnitureType.class);
        when(type.getItemPath()).thenReturn(itemPath);
        Furniture furniture = mock(Furniture.class);
        when(furniture.getType()).thenReturn(type);
        return furniture;
    }
}
