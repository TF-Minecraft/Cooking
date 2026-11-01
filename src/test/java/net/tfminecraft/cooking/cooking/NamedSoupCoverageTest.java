package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.pot.PotConfig;

/**
 * pot.yml kinds: a soup mostly of beetroot is Borscht (its origin, and its name in place of "Beetroot Soup"); salt and
 * spices don't count, and a soup no kind takes is still named by its pieces.
 */
class NamedSoupCoverageTest {
    static final String CONFIG = """
            kinds:
              Borscht:
                name: Borscht
                mostly: Beetroot
              Fish:
                only: [seafood]
              broken: scalar
            """;

    CookingReferencesTest.Environment env;
    PotReference pot;
    @TempDir Path directory;

    @BeforeEach void open() throws Exception {
        env = new CookingReferencesTest.Environment();
        Path file = directory.resolve("pot.yml");
        Files.writeString(file, CONFIG);
        new PotConfig().load(file.toFile());
        pot = new PotReference(env.furniture, Method.POT);
    }

    @AfterEach void close() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    /** Boiled, mashed pieces in the pot: "Origin" or "Origin/category". */
    ItemStack soup(String... pieces) {
        int slot = 1;
        for (String entry : pieces) {
            String[] parts = entry.split("/");
            FoodItem piece = env.food("piece", parts.length > 1 ? parts[1] : "vegetable", parts[0], Method.POT);
            piece.getTagTrack("cooked").forceSetValue(3);
            piece.addOrModifyTrack(TrackLoader.getByString("mashed"));
            pot.slots.put("input_" + slot, piece);
            env.place("input_" + slot, env.stack(piece, Material.BEETROOT, 1));
            slot++;
        }
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        pot.interact(env.interaction());
        return env.player.getInventory().getItemInMainHand();
    }

    @SuppressWarnings("deprecation")
    static String name(ItemStack stack) {
        return org.bukkit.ChatColor.stripColor(stack.getItemMeta().getDisplayName());
    }

    @Test
    void mostlyBeetrootIsBorscht() {
        ItemStack ladled = soup("Beetroot", "Beetroot", "Potato", "Salt/salt", "Spice Leaf/spice");
        assertEquals("Borscht", env.lastBuilt.getOrigin());
        assertEquals("Borscht", name(ladled));
    }

    @Test
    void lessThanHalfBeetrootIsNamedByItsPieces() {
        ItemStack ladled = soup("Beetroot", "Potato", "Carrot");
        assertEquals("Mixed", env.lastBuilt.getOrigin());
        assertTrue(name(ladled).endsWith("Soup"), name(ladled));
        assertTrue(name(ladled).contains("Beetroot"), name(ladled));
    }

    @Test
    void aKindWithoutANameKeepsThePiecesName() {
        ItemStack ladled = soup("Cod/seafood");
        assertEquals("Fish", env.lastBuilt.getOrigin());
        assertEquals("Cod Soup", name(ladled));
    }

    @Test
    void onlySeasoningsMakeNoKind() {
        assertNull(PotConfig.kindFor(List.of(env.food("salt", "salt", "Salt"))));
        FoodItem uncategorised = env.food("odd", "vegetable", "Beetroot");
        uncategorised.setCategory(null);
        assertEquals("Borscht", PotConfig.kindFor(List.of(uncategorised)).getOrigin());
    }

    @Test
    void theShippedPotConfigMakesBorschtFromBeetroot() {
        new PotConfig().load(new java.io.File("src/main/resources/pot.yml"));
        FoodItem beet = env.food("piece", "vegetable", "Beetroot");
        FoodItem potato = env.food("piece", "vegetable", "Potato");
        assertEquals("Borscht", PotConfig.kindFor(List.of(beet, beet, potato)).getName());
        assertNull(PotConfig.kindFor(List.of(beet, potato, potato)));
    }
}
