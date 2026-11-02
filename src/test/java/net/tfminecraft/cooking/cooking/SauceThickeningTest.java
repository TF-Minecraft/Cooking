package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.cooking.CookingReferencesTest.Environment;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.NamingLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.StationAddonRules;

/** Thick sauce: a roux (flour cooked in a buttered frying pan) in the saucepan makes the sauce Thick and names it so. */
class SauceThickeningTest {
    static final String THICK_MESSAGE_BURNT = "§cThat roux is burnt. It would spoil the sauce.";
    static final String THICK_MESSAGE_TWICE = "§cThere's already a roux in there.";

    Environment env;
    SauceReference pan;

    @BeforeEach void open() {
        env = new Environment();
        pan = new SauceReference(env.furniture, Method.SAUCEPAN);
        // As naming.yml ships it: Sweet ... Thick prefixes, and the default categories (which include thickener).
        NamingConfig.apply(List.of("sweet", "sauce_thickness"), 3, 2, 2, null, null, null);
    }

    @AfterEach void close() {
        NamingConfig.apply(List.of("sweet"), 3, 2, 2, null, null, null);
        env.close();
    }

    FoodItem roux() {
        return env.food("roux", "thickener", "Roux");
    }

    void hold(FoodItem food, Material material, int amount) {
        env.player.getInventory().setItemInMainHand(env.stack(food, material, amount));
    }

    void pourMilk() {
        hold(env.food("milk", "dairy", "Milk"), Material.WHITE_DYE, 1);
        pan.interact(env.interaction());
        assertEquals(-1, pan.getSecondary("liquid"));
    }

    @SuppressWarnings("deprecation")
    String heldName() {
        return env.player.getInventory().getItemInMainHand().getItemMeta().getDisplayName();
    }

    @Test void aRouxTakesOneOfTheTwoPlacesAndMakesAThickSauceThatItDoesNotName() {
        pourMilk();
        hold(roux(), Material.BROWN_DYE, 2);
        pan.interact(env.interaction());
        assertEquals(1, pan.getSlots().size(), "The roux takes one place");
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
        hold(env.food("garlic", "garnish", "Garlic"), Material.GREEN_DYE, 1);
        pan.interact(env.interaction());
        assertEquals(2, pan.getSlots().size(), "The other place is still free for an addition");

        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        pan.interact(env.interaction());
        FoodItem sauce = env.lastBuilt;
        assertEquals(1, sauce.getTagTrack("sauce_thickness").getValue());
        assertTrue(sauce.getTagTrack("sauce_thickness").getCurrentStep().getName().endsWith("Thick"));
        String name = heldName();
        assertTrue(name.contains("Thick Garlic"), name);
        assertFalse(name.contains("Roux"), "A thickener doesn't name the sauce: " + name);
        assertTrue(pan.isEmpty());
    }

    @Test void aSauceWithoutARouxHasNoThicknessAndNoThinInItsName() {
        pourMilk();
        env.player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER));
        pan.interact(env.interaction());
        assertFalse(env.lastBuilt.hasTagTrack("sauce_thickness"));
        assertFalse(heldName().contains("Thin"), heldName());
    }

    @Test void aSecondRouxAndABurntRouxAreRefusedWithAReason() {
        pourMilk();
        FoodItem burnt = env.food("roux", "thickener", "Roux", Method.FRYING_PAN);
        burnt.getTagTrack("cooked").setValue(2);
        hold(burnt, Material.BROWN_DYE, 1);
        pan.interact(env.interaction());
        assertTrue(pan.getSlots().isEmpty());
        verify(env.player).sendMessage(THICK_MESSAGE_BURNT);

        hold(roux(), Material.BROWN_DYE, 2);
        pan.interact(env.interaction());
        pan.interact(env.interaction());
        assertEquals(1, pan.getSlots().size());
        assertEquals(1, env.player.getInventory().getItemInMainHand().getAmount());
        verify(env.player).sendMessage(THICK_MESSAGE_TWICE);
    }

    @Test void rawFlourIsRefusedWithAHintAndOtherFoodSilently() {
        pourMilk();
        env.cache.when(() -> ItemCache.isFlour(any())).thenAnswer(inv -> Environment.material(inv.getArgument(0), Material.SUGAR));
        hold(env.food("flour", "grain", "Wheat"), Material.SUGAR, 1);
        pan.interact(env.interaction());
        assertTrue(pan.getSlots().isEmpty());
        verify(env.player).sendMessage("§7Cook the flour in a buttered frying pan first: a roux thickens the sauce.");
        assertFalse(pan.canAdd(null, env.stack(env.food("flour", "grain", "Wheat"), Material.SUGAR, 1)));

        hold(env.food("bread", "bread", "Wheat"), Material.BREAD, 1);
        pan.interact(env.interaction());
        assertTrue(pan.getSlots().isEmpty());
        verify(env.player, times(1)).sendMessage(anyString());
    }

    @Test void thickenerRulesStandAloneAndWithoutTheirTrack() {
        Map<String, FoodItem> slots = new HashMap<>();
        slots.put("empty", null);
        assertFalse(StationAddonRules.hasThickener(slots));
        assertFalse(StationAddonRules.canAcceptThickener(slots, null, null));
        assertFalse(StationAddonRules.canAcceptThickener(slots, env.food("salt", "salt", "Salt"), null));
        FoodItem burnt = env.food("roux", "thickener", "Roux", Method.FRYING_PAN);
        burnt.getTagTrack("cooked").setValue(2);
        assertFalse(StationAddonRules.canAcceptThickener(slots, burnt, null));
        assertTrue(StationAddonRules.canAcceptThickener(slots, roux(), null));

        FoodItem sauce = env.food("sauce", "sauce", "Mixed");
        StationAddonRules.applyThickTag(sauce, slots);
        assertFalse(sauce.hasTagTrack("sauce_thickness"));
        slots.put("input_1", roux());
        assertTrue(StationAddonRules.hasThickener(slots));
        TrackLoader.oList.removeIf(track -> track.getId().equals("sauce_thickness"));
        StationAddonRules.applyThickTag(sauce, slots);
        assertFalse(sauce.hasTagTrack("sauce_thickness"), "No sauce_thickness track in tags.yml leaves the sauce as it is");
    }

    @Test void theShippedConfigCooksFlourIntoARouxThatThickensAndNamesSauces() throws Exception {
        new FoodLoader().load(new File("src/main/resources/types.yml"));
        FoodItem flour = new FoodItem(FoodLoader.getByString("flour"));
        assertEquals("thickener(type=roux;origin=Roux;tags=processed.0)",
                flour.getCookData().getParameters().get(Method.FRYING_PAN).getOutput());
        assertFalse(flour.hasTagTrack("cooked"), "Flour keeps its values outside the pan");
        FoodItem roux = FoodLoader.getByString("roux");
        assertTrue(roux.getCookData().getParameters().isEmpty(), "A roux doesn't cook on");

        new NamingLoader().load(new File("src/main/resources/naming.yml"));
        assertTrue(StationAddonRules.isThickenerCategory("thickener"));
        assertTrue(NamingConfig.getPrefixTracks().contains("sauce_thickness"));
        assertTrue(StationAddonRules.isThickenerCategory("THICKENER"));

        String models = Files.readString(Path.of("src/main/resources/models.yml"));
        String composition = Files.readString(Path.of("src/main/resources/composition.yml"));
        String items = Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/intermediates.yml"));
        String categories = Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/categories.yml"));
        assertTrue(models.contains("\nroux:\n"));
        assertTrue(models.contains("        - frying_pan ia.tfmc_cooking:flour_heap\n"));
        assertTrue(composition.contains("      - flour\n      - thickener\n"), "A roux is an extra in a sauce's stars");
        for (String id : List.of("roux", "roux_burnt", "roux_rotten", "flour_heap", "flour_heap_rotten")) {
            assertTrue(models.contains("ia.tfmc_cooking:" + id + "\n"), id);
            assertTrue(items.contains("\n  " + id + ":\n"), id);
            assertTrue(categories.contains("      - tfmc_cooking:" + id + "\n"), id);
            Path pack = Path.of("ItemsAdder/tfmc_cooking/resourcepack/assets/tfmc_cooking");
            assertTrue(Files.exists(pack.resolve("models/item/intermediate/" + id + ".json")), id);
            assertTrue(Files.exists(pack.resolve("textures/item/intermediate/" + id + ".png")), id);
        }
    }
}
