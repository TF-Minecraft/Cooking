package net.tfminecraft.cooking.brewery;

import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.recipe.PotionColor;
import com.dre.brewery.utility.MinecraftVersion;

/**
 * BreweryX's PotionColor reads BreweryPlugin.getMCVersion() and Bukkit tags in its static
 * initializer. If the first test to touch the class runs without the version stubbed, initialization
 * fails and the class stays broken for the rest of the JVM, so the brewery tests passed or failed by
 * test order. Call this once a MockBukkit server is running, before stubbing BreweryPlugin yourself.
 */
final class BreweryClasses {
    private static boolean loaded;

    private BreweryClasses() {}

    static synchronized void loadPotionColor() {
        if (loaded) {
            return;
        }
        try (MockedStatic<BreweryPlugin> brewery = mockStatic(BreweryPlugin.class)) {
            brewery.when(BreweryPlugin::getMCVersion).thenReturn(MinecraftVersion.V1_21_10);
            Class.forName(PotionColor.class.getName(), true, BreweryClasses.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        loaded = true;
    }
}
