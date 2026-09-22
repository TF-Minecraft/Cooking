package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class CookStateAssetsTest {

    private static final String[] IDS = {
            "fish_filet_cooked",
            "fish_filet_burnt",
            "fish_filet_boiled",
            "jellyfish_cubes_cooked",
            "jellyfish_cubes_burnt",
            "jellyfish_cubes_boiled",
            "octopus_cooked",
            "octopus_burnt",
            "octopus_boiled",
            "red_meat_steak_boiled",
            "poultry_roast_filet_boiled"
    };

    @Test
    void cookStateIdsAreRegisteredAndModeled() throws Exception {
        String items = Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/ingredients.yml"));
        String models = Files.readString(Path.of("src/main/resources/models.yml"));
        for (String id : IDS) {
            assertTrue(items.contains(id + ":"), id + " missing from ingredients.yml");
            assertTrue(models.contains("ia.tfmc_cooking:" + id), id + " missing from models.yml");
        }
    }

    @Test
    void cookStatesOutrankFreshAndRottenOutranksThem() throws Exception {
        String models = Files.readString(Path.of("src/main/resources/models.yml")).replace("\r\n", "\n");
        for (String group : List.of("seafood_fish_filet", "seafood_jellyfish", "seafood_octopus")) {
            String prefix = itemPrefix(group);
            String block = group(models, group);
            assertState(block, "cooked", 2, prefix + "cooked");
            assertState(block, "burnt", 2, prefix + "burnt");
            assertState(block, "boiled", 2, prefix + "boiled");
            assertState(block, "rotten", 3, prefix + "rotten");
        }
        assertState(group(models, "meat_red_meat"), "boiled", 1, "red_meat_steak_boiled");
        assertState(group(models, "meat_poultry"), "boiled", 1, "poultry_roast_filet_boiled");
    }

    private static String itemPrefix(String group) {
        return switch (group) {
            case "seafood_fish_filet" -> "fish_filet_";
            case "seafood_jellyfish" -> "jellyfish_cubes_";
            case "seafood_octopus" -> "octopus_";
            default -> throw new IllegalArgumentException(group);
        };
    }

    private static void assertState(String block, String state, int weight, String itemId) {
        String stateBlock = stateBlock(block, state);
        assertTrue(stateBlock.contains("weight: " + weight), state + " weight");
        assertTrue(stateBlock.contains("ia.tfmc_cooking:" + itemId), itemId);
    }

    private static String group(String models, String name) {
        String header = name + ":\n";
        int start = models.indexOf("\n" + header);
        start = start < 0 ? models.indexOf(header) : start + 1;
        assertTrue(start >= 0, name);
        int end = models.length();
        for (int i = start + header.length(); i < models.length(); i++) {
            if (models.charAt(i) == '\n' && i + 1 < models.length() && models.charAt(i + 1) != ' ') {
                end = i;
                break;
            }
        }
        return models.substring(start, end);
    }

    private static String stateBlock(String group, String state) {
        String header = "\n  " + state + ":\n";
        int start = group.indexOf(header);
        assertTrue(start >= 0, state);
        int end = group.length();
        int cursor = start + header.length();
        while (cursor < group.length()) {
            int newline = group.indexOf('\n', cursor);
            if (newline < 0 || newline + 3 >= group.length()) {
                break;
            }
            boolean nextState = group.charAt(newline + 1) == ' '
                    && group.charAt(newline + 2) == ' '
                    && group.charAt(newline + 3) != ' ';
            if (nextState) {
                end = newline;
                break;
            }
            cursor = newline + 1;
        }
        return group.substring(start, end);
    }
}
