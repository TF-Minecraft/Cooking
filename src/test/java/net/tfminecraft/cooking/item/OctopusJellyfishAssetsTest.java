package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Grilled octopus and jellyfish strips as shipped: the frying pan turns octopus into grilled octopus, the cutting
 * board cuts jellyfish cubes into strips, and every look models.yml names is an ItemsAdder item with its model.
 */
class OctopusJellyfishAssetsTest {
    static final String PACK = "ItemsAdder/tfmc_cooking/resourcepack/assets/tfmc_cooking/";

    @Test
    void thePanGrillsOctopusAndTheBoardCutsJellyfishIntoStrips() {
        YamlConfiguration types = YamlConfiguration.loadConfiguration(new File("src/main/resources/types.yml"));
        assertEquals("seafood(type=grilled_octopus)", types.getString("seafood_octopus.cooking-options.frying_pan.output"));
        assertNull(types.getString("seafood_octopus.cooking-options.pot.output"), "boiled octopus stays octopus");
        assertEquals("grilled_octopus", types.getString("grilled_octopus.model"));
        assertEquals("jellyfish_strips", types.getString("jellyfish_strips.model"));

        YamlConfiguration stations = YamlConfiguration.loadConfiguration(new File("src/main/resources/crafting-stations.yml"));
        String recipe = "cutting_board.recipes.slice_jellyfish.";
        assertEquals(List.of("c.seafood(type=seafood_jellyfish)"), stations.getStringList(recipe + "inputs"));
        assertEquals("seafood(type=jellyfish_strips;origin={origin})", stations.getString(recipe + "output"));
        assertTrue(stations.getBoolean(recipe + "inherit"));
        assertTrue(stations.getBoolean(recipe + "share-food"));
    }

    @Test
    void everyLookIsAnItemWithItsModelAndTexture() throws Exception {
        YamlConfiguration models = YamlConfiguration.loadConfiguration(new File("src/main/resources/models.yml"));
        String items = Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/ingredients.yml"));
        String categories = Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/categories.yml"));
        for (String type : List.of("grilled_octopus", "jellyfish_strips")) {
            for (String state : models.getConfigurationSection(type).getKeys(false)) {
                String item = models.getString(type + "." + state + ".gui.item");
                assertEquals(item, models.getString(type + "." + state + ".display.item"));
                String id = item.substring("ia.tfmc_cooking:".length());
                assertTrue(items.contains("\n  " + id + ":\n"), id);
                assertTrue(categories.contains("tfmc_cooking:" + id + "\n"), id);
                assertTrue(Files.exists(Path.of(PACK + "models/item/ingredients/seafood/" + id + ".json")), id);
            }
        }
        assertEquals("ia.tfmc_cooking:jellyfish_strips_boiled", models.getString("jellyfish_strips.boiled.gui.item"));
        assertTrue(Files.exists(Path.of(PACK + "textures/item/ingredients/seafood/grilled_octopus.png")));
    }
}
