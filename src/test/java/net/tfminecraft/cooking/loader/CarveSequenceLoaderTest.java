package net.tfminecraft.cooking.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CarveSequenceLoaderTest {

    @Test
    void missingFloorKeepsPoultryAtTwoLegsAndAFilet() {
        assertEquals(3, CarveSequenceLoader.defaultMinFoodCuts("poultry"));
        assertEquals(1, CarveSequenceLoader.defaultMinFoodCuts("red_meat"));
    }
}
