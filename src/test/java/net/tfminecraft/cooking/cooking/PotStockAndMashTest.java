package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import net.tfminecraft.cooking.pot.MashItems;
import net.tfminecraft.cooking.pot.PotConfig;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/** A bottle of stock fills the pot for a Rich soup; an empty pot mashes boiled potatoes and butter without water. */
class PotStockAndMashTest {
    // The test environment parses no food paths, so recipes here match by category only.
    static final String CONFIG = """
            liquids:
              stock:
                input: c.stock
                track: rich
                colour: c9a66b
                returns: v.glass_bottle
              broth:
                input: c.broth
                track: no_such_track
            mashes:
              mashed_potatoes:
                ingredients:
                  potato:
                    path: c.vegetable
                    amount: 4
                  butter:
                    path: c.butter
                extras:
                  milk:
                    path: c.milk
                    track: sauce_creamy
                    returns: ia.tfmc_cooking:empty_cup
                  salt:
                    path: c.salt
                    track: seasoning
                servings: 2
                output: mash(type=mashed_potatoes)
                display: model.mash
                hint: 4 potatoes and a butter
            """;

    CookingReferencesTest.Environment env;
    PotReference pot;
    @TempDir Path directory;

    @BeforeEach void setUp() throws Exception {
        env = new CookingReferencesTest.Environment();
        load(CONFIG);
        pot = new PotReference(env.furniture, Method.POT);
    }

    @AfterEach void tearDown() {
        new PotConfig().load(directory.resolve("missing.yml").toFile());
        env.close();
    }

    void load(String yaml) throws Exception {
        Path file = directory.resolve("pot.yml");
        Files.writeString(file, yaml);
        new PotConfig().load(file.toFile());
    }

    FurnitureInteractEvent hold(ItemStack item) {
        env.player.getInventory().setItemInMainHand(item);
        FurnitureInteractEvent event = env.interaction();
        pot.interact(event);
        return event;
    }

    ItemStack hand() {
        return env.player.getInventory().getItemInMainHand();
    }

    ItemStack food(String category, int amount) {
        return env.stack(env.food(category, category, category), Material.EXPERIENCE_BOTTLE, amount);
    }

    /** A cut potato boiled in a pot and taken out: cooked.3, still able to boil. */
    ItemStack potatoes(int amount) {
        FoodItem potato = env.food("vegetable_cut", "vegetable", "Potato", Method.POT);
        potato.getTagTrack("cooked").forceSetValue(3);
        potato.setBaseFood(7);
        potato.setBaseNutrition(4);
        return env.stack(potato, Material.BAKED_POTATO, amount);
    }

    ItemStack butter() {
        FoodItem butter = env.food("butter", "butter", "Cow");
        butter.setBaseFood(10);
        butter.setBaseNutrition(6);
        return env.stack(butter, Material.GOLD_INGOT, 1);
    }

    ItemStack milk(int amount) {
        FoodItem milk = env.food("milk", "milk", "Cow");
        milk.setBaseFood(10);
        milk.setBaseNutrition(6);
        return env.stack(milk, Material.WHITE_DYE, amount);
    }

    void boil() {
        env.heated = true;
        for (int i = 0; i < 20; i++) pot.tick();
        assertTrue(pot.isBoiling());
    }

    /** Boils, adds a carrot, mashes and ladles a soup, returning what the ladle built. */
    FoodItem soup() {
        boil();
        FoodItem carrot = env.food("carrot", "vegetable", "Carrot", Method.POT);
        carrot.addOrModifyTrack(TrackLoader.getByString("mashed"));
        pot.slots.put("input_1", carrot);
        env.place("input_1", env.stack(carrot, Material.CARROT, 1));
        hold(new ItemStack(Material.PAPER));
        return env.lastBuilt;
    }

    int count(Material material) {
        return env.player.getInventory().all(material).values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    void stockFillsThePotLikeWaterAndItsSoupIsRichEvenAfterAReload() {
        assertTrue(hold(food("stock", 2)).isCancelled());
        assertEquals(-1, pot.getSecondary("liquid"));
        assertEquals("stock", env.variables.get("pot.liquid"));
        assertTrue(env.variables.containsKey("pot.liquidFood"));
        assertEquals(List.of("c9a66b"), pot.getColours());
        assertEquals(1, hand().getAmount());
        assertEquals(1, count(Material.GLASS), "The empty bottle comes back");

        FoodItem served = soup();
        assertEquals("rich", served.getTagTrack("rich").getCurrentStep().getId());
        // The stock counts toward the soup's quality alongside its pieces.
        env.composer.verify(() -> CompositionQualityResolver.compose(any(),
                argThat(inputs -> inputs.size() == 2), eq(CompositionContext.SOUP_SCOOP)));

        pot = new PotReference(env.furniture, Method.POT);
        pot.rebuildFromFurniture();
        assertEquals("c9a66b", pot.getColours().get(0));
        hold(new ItemStack(Material.PAPER));
        assertNotNull(env.lastBuilt.getTagTrack("rich"));

        // Emptied, the pot forgets its stock.
        pot.clear();
        assertFalse(env.variables.containsKey("pot.liquid"));
        assertFalse(env.variables.containsKey("pot.liquidFood"));
    }

    @Test
    void aLiquidWithNothingToGiveBackIsUsedUpAndAnUnknownTrackOrLiquidAddsNothing() {
        hold(food("broth", 1));
        assertTrue(hand().isEmpty());
        assertEquals(0, count(Material.GLASS));
        assertNull(soup().getTagTrack("no_such_track"));

        // A liquid pot.yml no longer has is plain water after a reload.
        env.variables.put("pot.liquid", "gone");
        env.variables.remove("pot.liquidFood");
        pot = new PotReference(env.furniture, Method.POT);
        pot.rebuildFromFurniture();
        assertEquals("3d85c6", pot.getColours().get(0));
        hold(new ItemStack(Material.PAPER));
        assertNull(env.lastBuilt.getTagTrack("rich"));
        env.composer.verify(() -> CompositionQualityResolver.compose(any(),
                argThat(inputs -> inputs.size() == 1), any()), atLeastOnce());
    }

    @Test
    void aPotWithoutATypeRebuildsEmpty() {
        when(env.furniture.getType()).thenReturn(null);
        pot.rebuildFromFurniture();
        assertTrue(pot.isEmpty());
    }

    @Test
    void aPotWithoutALiquidSlotTakesNoStock() {
        env.definitions.remove("liquid");
        hold(food("stock", 1));
        assertTrue(pot.isEmpty());
        assertEquals(1, hand().getAmount());
        when(env.furniture.getType()).thenReturn(null);
        hold(food("stock", 1));
        assertTrue(pot.isEmpty());
    }

    @Test
    void anEmptyPotMashesExactlyItsIngredientsWithoutWaterOrHeatAndServesThemByLadle() {
        // An extra goes in only once something is there to mash; on its own salt is no mash.
        hold(food("salt", 1));
        verify(env.player).sendMessage("§cWater isn't boiling yet...");
        assertTrue(pot.isEmpty());

        ItemStack potatoes = potatoes(5);
        hold(potatoes);
        assertEquals(4, hand().getAmount());
        assertTrue(pot.isMashing());
        assertFalse(pot.isMashed());

        // Too early to mash, and no water, stock or stray food while the ingredients are in.
        hold(new ItemStack(Material.STICK));
        verify(env.player).sendMessage("§cIt needs exactly 4 potatoes and a butter to mash.");
        for (ItemStack wet : List.of(new ItemStack(Material.WATER_BUCKET), food("stock", 1), new ItemStack(Material.HONEY_BOTTLE))) {
            assertTrue(hold(wet).isCancelled());
        }
        verify(env.player, times(3)).sendMessage(PotReference.MASH_NOT_WATER);
        assertFalse(pot.getSecondaries().containsKey("liquid"));
        hold(food("flour", 1));
        verify(env.player).sendMessage("§cThat doesn't go in this mash.");

        // Heat does nothing to a mash.
        env.heated = true;
        pot.tick();
        assertEquals(0, pot.getSlot("input_1").getCookData().getCurrentTime());

        for (int i = 0; i < 3; i++) hold(potatoes(1));
        hold(potatoes(1));
        verify(env.player, times(2)).sendMessage("§cThat doesn't go in this mash.");
        hold(milk(2));
        assertEquals(1, hand().getAmount());
        assertEquals(1, count(Material.GLASS), "The empty cup comes back");
        hold(milk(1));
        verify(env.player, times(3)).sendMessage("§cThat doesn't go in this mash.");
        hold(butter());
        assertEquals(5, pot.getSlots().keySet().stream().filter(key -> key.startsWith("input")).count());

        // Until it's mashed, an empty hand takes the last piece back out.
        hold(new ItemStack(Material.AIR));
        assertEquals("butter", env.resolve(hand()).getId());
        assertFalse(pot.getSlots().containsKey("input_5"));
        hold(hand());

        hold(new ItemStack(Material.STICK));
        assertEquals("mashed_potatoes", env.variables.get("pot.mashed"));
        assertEquals(2, env.variables.get("pot.mashServings"));
        assertEquals(Material.GLASS, env.active.get("liquid").getCurrentItem().getType());
        verify(env.active.get("input_1")).applyDisplayData(argThat(pose -> pose.getxScale() == 0));
        assertTrue(pot.isMashed());

        hold(new ItemStack(Material.STICK));
        verify(env.player).sendMessage("§7It's mashed. Serve it with a ladle.");
        hold(potatoes(1));
        verify(env.player).sendMessage("§cLadle the mash out first.");
        hold(new ItemStack(Material.AIR));
        assertEquals(6, pot.getSlots().size());

        hold(new ItemStack(Material.PAPER, 2));
        ItemStack serving = hand();
        assertTrue(MashItems.isLadled(serving));
        assertEquals(1, count(Material.PAPER) - 1, "The other ladle stays");
        assertNotNull(env.lastBuilt.getTagTrack("sauce_creamy"));
        assertNull(env.lastBuilt.getTagTrack("seasoning"));
        // Four boiled pieces (7 x 1.55), a butter and a cup of milk, over two servings.
        assertEquals((4 * 7 * 1.55 + 10 + 10) / 2, env.lastBuilt.getBaseFood(), 1e-9);
        assertEquals(1, env.variables.get("pot.mashServings"));
        verify(env.active.get("liquid"), atLeastOnce()).applyDisplayData(argThat(pose -> pose.getyPos() < 0));

        // A full ladle is no ladle.
        assertTrue(hold(serving).isCancelled());
        assertEquals(1, env.variables.get("pot.mashServings"));

        // A reload keeps the mash, butter and all, and the last serving empties the pot.
        pot = new PotReference(env.furniture, Method.POT);
        pot.rebuildFromFurniture();
        assertTrue(pot.isMashed());
        assertEquals(6, pot.getSlots().size());
        assertFalse(pot.getSecondaries().containsKey("liquid"));
        hold(new ItemStack(Material.PAPER));
        assertTrue(MashItems.isLadled(hand()));
        assertTrue(pot.isEmpty());
        assertFalse(pot.isMashing());
        assertFalse(env.active.containsKey("liquid"));
        assertFalse(env.active.containsKey("input_1"), "The mashed pieces go with the mash");
    }

    @Test
    void anEmptyHandTakesEverythingBackAndThePotTakesWaterAgain() {
        hold(potatoes(1));
        hold(milk(1));
        hold(food("salt", 1));
        hold(new ItemStack(Material.AIR));
        assertEquals("vegetable_cut", env.resolve(hand()).getId());
        hold(new ItemStack(Material.AIR));
        hold(new ItemStack(Material.AIR));
        assertTrue(pot.isEmpty());
        assertFalse(pot.isMashing());
        assertFalse(env.variables.containsKey("pot.extras"));
        hold(new ItemStack(Material.WATER_BUCKET));
        assertEquals(-1, pot.getSecondary("liquid"));
        assertEquals(Material.BUCKET, hand().getType());
    }

    @Test
    void aPotOfWaterRefusesWhatOnlyAMashTakesButStillBoilsPotatoes() {
        hold(new ItemStack(Material.WATER_BUCKET));
        boil();
        assertTrue(hold(butter()).isCancelled());
        verify(env.player).sendMessage("§cThat goes in a mash, which is made in an empty pot without water.");
        assertTrue(pot.getSlots().isEmpty());
        // Boiled potatoes are soup pieces in water; salt and the rest follow the soup's rules as before.
        hold(potatoes(1));
        assertEquals(1, pot.getSlots().size());
        hold(food("salt", 1));
        hold(food("flour", 1));
        assertEquals(1, pot.getSlots().size());
    }

    @Test
    void aPotTooSmallForTheRecipeSaysItIsFull() throws Exception {
        load(CONFIG.replace("amount: 4", "amount: 6"));
        for (int i = 0; i < 5; i++) hold(potatoes(1));
        hold(potatoes(1));
        verify(env.player).sendMessage("§cThe pot is full.");
        assertEquals(1, hand().getAmount());
    }

    @Test
    void aMashThatCanNoLongerBeServedOrShownIsHarmless() throws Exception {
        for (int i = 0; i < 4; i++) hold(potatoes(1));
        hold(butter());
        hold(new ItemStack(Material.STICK));

        env.builder.when(() -> ItemBuilder.buildSingleWithQuality(any(), any(), anyInt())).thenReturn(null);
        hold(new ItemStack(Material.PAPER));
        verify(env.player).sendMessage("§cThis can't be served right now.");

        // pot.yml lost the recipe: nothing to serve, mash or show after a reload.
        load("mashes: {}");
        hold(new ItemStack(Material.PAPER));
        hold(new ItemStack(Material.STICK));
        verify(env.player, times(2)).sendMessage("§cThis can't be served right now.");
        env.definitions.remove("liquid");
        pot = new PotReference(env.furniture, Method.POT);
        pot.rebuildFromFurniture();
        assertTrue(pot.isMashed());
        when(env.furniture.getType()).thenReturn(null);
        pot = new PotReference(env.furniture, Method.POT);
        pot.rebuildFromFurniture();
        assertTrue(pot.getSlots().isEmpty());

        // Breaking an unmashed pot leaves its pieces for the furniture to drop.
        when(env.furniture.getType()).thenReturn(env.type);
        env.variables.remove("pot.mashed");
        pot = new PotReference(env.furniture, Method.POT);
        pot.rebuildFromFurniture();
        hold(new ItemStack(Material.STICK));
        verify(env.player).sendMessage("§cThere's nothing here to mash.");
        pot.clear();
        assertFalse(pot.getSlots().isEmpty());
        assertTrue(env.active.containsKey("input_1"));
        assertFalse(pot.isMashing());

        load(CONFIG);
        env.parser.when(() -> FoodParser.parse(anyString())).thenReturn(null);
        assertNull(MashItems.serve(env.player, PotConfig.mash("mashed_potatoes"), List.of(), List.of(), null));
    }

    @Test
    void aMashedPotWithoutALiquidSlotStillMashesAndHasNoLevelToShow() {
        env.definitions.remove("liquid");
        for (int i = 0; i < 4; i++) hold(potatoes(1));
        hold(butter());
        hold(new ItemStack(Material.STICK));
        assertTrue(pot.isMashed());
        assertFalse(env.active.containsKey("liquid"));
        hold(new ItemStack(Material.PAPER));
        assertEquals(1, env.variables.get("pot.mashServings"));
        PlacedSlot first = env.active.get("input_1");
        verify(first, atLeastOnce()).applyDisplayData(any());
    }
}
