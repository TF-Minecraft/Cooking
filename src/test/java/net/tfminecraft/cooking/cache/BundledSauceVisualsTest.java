package net.tfminecraft.cooking.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The bundled sauce visuals have to exist in the bundled ItemsAdder pack and fit their furniture. */
class BundledSauceVisualsTest {
    private static final String NAMESPACE = "ia.tfmc_cooking:";
    private static final File PACK = new File("ItemsAdder/tfmc_cooking");
    // Model JSON is indented with tabs, which YAML refuses, so read the texture paths directly.
    private static final Pattern TEXTURE = Pattern.compile("\"tfmc_cooking:([^\"]+)\"");

    private final YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    private final ConfigurationSection items = YamlConfiguration
            .loadConfiguration(new File(PACK, "contents/items.yml")).getConfigurationSection("items");

    @Test
    void everySauceHasItsOwnPlatedSplash() {
        List<String> entries = config.getStringList("sauce-dict");
        assertTrue(entries.size() > 0);
        for (String entry : entries) {
            String[] paths = entry.split("\\s+")[1].split("\\|", 2);
            assertEquals(2, paths.length, entry);
            // A station model on a plate is a pan-sized sheet of liquid floating over the food.
            assertNotEquals(paths[0], paths[1], entry);
            assertTrue(paths[1].endsWith("_plated"), entry);
            assertPackItem(paths[0]);
            assertPackItem(paths[1]);
        }
    }

    @Test
    void fallbacksMatchTheirFurniture() {
        assertPackItem(config.getString("liquid-fallback"));
        String plated = config.getString("plated-liquid-fallback");
        assertTrue(plated.endsWith("_plated"), plated);
        assertPackItem(plated);
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertPackItem(String path) {
        assertTrue(path.startsWith(NAMESPACE), path);
        ConfigurationSection item = items.getConfigurationSection(path.substring(NAMESPACE.length()));
        assertTrue(item != null, path + " is not in the bundled pack");
        String model = item.getString("resource.model_path");
        File json = new File(PACK, "resourcepack/assets/tfmc_cooking/models/" + model + ".json");
        assertTrue(json.isFile(), json.getPath());
        Matcher texture = TEXTURE.matcher(read(json));
        int found = 0;
        while (texture.find()) {
            File png = new File(PACK, "resourcepack/assets/tfmc_cooking/textures/" + texture.group(1) + ".png");
            assertTrue(png.isFile(), png.getPath());
            found++;
        }
        assertTrue(found > 0, json.getPath());
    }
}
