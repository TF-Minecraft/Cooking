package net.tfminecraft.cooking.brewery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.dre.brewery.api.BreweryApi;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.recipe.PotionColor;

class BrewLiquidTest {
    @AfterEach void resetReader() { BrewLiquid.setReader(null); }

    @Test void breweryQualityRoundsHalfStarsUp() {
        int[] expected = {1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5};
        for (int quality = 0; quality <= 11; quality++) {
            assertEquals(expected[quality], BrewLiquid.starsFor(quality), "quality " + quality);
        }
        assertEquals(1, BrewLiquid.starsFor(-3));
    }

    @Test void recipeNamesLoseTheirColourCodes() {
        assertEquals("Golden Mead", BrewLiquid.plainName("&6Golden Mead"));
        assertEquals("Shimmering Golden Vodka", BrewLiquid.plainName(" §6§lShimmering &#ffaa00Golden Vodka "));
        assertEquals("Red Wine", BrewLiquid.plainName("Red Wine"));
        assertEquals("", BrewLiquid.plainName(null));
    }

    @Test void withoutBreweryNothingIsABrew() {
        ItemStack potion = new ItemStack(Material.POTION);
        assertNull(BrewLiquid.from(potion));
        BrewLiquid wine = new BrewLiquid("Red Wine", 5, "ff0000", true);
        BrewLiquid.setReader(item -> wine);
        assertSame(wine, BrewLiquid.from(potion));
        assertNull(BrewLiquid.from(null));
    }

    @Test void readsTheRecipeNameStarsColourAndAlcohol() {
        BRecipe recipe = mock(BRecipe.class);
        PotionColor colour = mock(PotionColor.class);
        assertNull(BrewLiquidReader.describe(null, 7), "Still fermenting, no recipe yet");

        when(recipe.getName(9)).thenReturn("&6Golden Mead");
        when(recipe.getAlcohol()).thenReturn(9);
        when(recipe.getColor()).thenReturn(colour);
        when(colour.getColor()).thenReturn(Color.fromRGB(0xff9900));
        assertEquals(new BrewLiquid("Golden Mead", 5, "ff9900", true), BrewLiquidReader.describe(recipe, 9));

        when(recipe.getAlcohol()).thenReturn(-6);
        when(colour.getColor()).thenReturn(null);
        assertEquals(new BrewLiquid("Golden Mead", 5, null, false), BrewLiquidReader.describe(recipe, 9));

        when(recipe.getColor()).thenReturn(null);
        assertNull(BrewLiquidReader.describe(recipe, 9).colour());
    }

    @Test void itemsThatAreNotBrewsAreNotLiquids() {
        ItemStack potion = new ItemStack(Material.POTION);
        try (MockedStatic<BreweryApi> api = mockStatic(BreweryApi.class)) {
            assertNull(BrewLiquidReader.read(potion));
            api.verify(() -> BreweryApi.getBrew(potion));
        }
    }
}
