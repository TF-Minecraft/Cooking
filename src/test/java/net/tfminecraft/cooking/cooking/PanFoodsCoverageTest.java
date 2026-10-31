package net.tfminecraft.cooking.cooking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.cooking.crafting.CraftingStationCoverageTest.Environment;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineage;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.item.data.CookParameter;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/** Pan foods: a frying pan option with an output turns the piece into that food (chopped corn pops into popcorn). */
class PanFoodsCoverageTest {
    static final String POPCORN = "snack(type=popcorn;origin=Corn)";
    static final String GRILLED = "vegetable(type=grilled_mushroom)";

    Environment env;
    FryingReference pan;

    @BeforeEach void open() {
        env = new Environment();
        pan = new FryingReference(env.furniture, Method.FRYING_PAN);
        env.parser.when(() -> FoodParser.parse(POPCORN)).thenAnswer(inv -> parsed("popcorn", "#f3e3a6Popcorn", "snack", "Corn"));
        env.parser.when(() -> FoodParser.parse(GRILLED))
                .thenAnswer(inv -> parsed("grilled_mushroom", "#a0785aGrilled Mushroom", "vegetable", ""));
        env.parser.when(() -> FoodParser.parse("snack(type=missing)")).thenReturn(null);
        env.builder.when(() -> ItemBuilder.displayName(any())).thenAnswer(inv -> ((FoodItem) inv.getArgument(0)).getName());
    }

    @AfterEach void close() { env.close(); }

    static FoodItem type(String id, String name) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", name); config.set("update", false);
        FoodItem food = new FoodItem(id, config);
        food.setModel(new FoodModel(new ItemStack(Material.CARROT)));
        return food;
    }

    static FoodParser.Result parsed(String id, String name, String category, String origin) {
        FoodParser.Result result = new FoodParser.Result();
        result.template = new FoodItem(type(id, name));
        result.template.setCategory(category);
        result.template.setOrigin(origin);
        return result;
    }

    /** A piece whose only pan option names an output, like chopped corn: no cooked track of its own. */
    FoodItem piece(String id, String category, String origin, String output, int time, int burn) {
        FoodItem food = env.food(id, origin);
        food.setCategory(category);
        food.getCookData().getParameters().put(Method.FRYING_PAN, new CookParameter(1, time, burn, output));
        return food;
    }

    FoodItem choppedCorn() {
        FoodItem corn = piece("vegetable_chopped_2", "garnish", "Corn", POPCORN, 5, 8);
        corn.setQualityRange(4, 4);
        TagTrack cut = new TagTrack(TrackLoader.getByString("cut"));
        cut.setValue(1);
        corn.addOrModifyTrack(cut);
        TagTrack freshness = new TagTrack(TrackLoader.getByString("freshness"));
        freshness.setValue(90000);
        corn.addOrModifyTrack(freshness);
        corn.setAgeRemainder("freshness", 0.5);
        corn.addIngredient("Corn");
        corn.setLineage(IngredientLineage.ofMain("Corn"));
        return corn;
    }

    /** Puts a piece in the pan the way InteractibleFurniture does: the add event, then the shown item in the slot. */
    PlacedSlot add(FoodItem food, String slot) {
        FurnitureSlotItemAddEvent event = env.addEvent(slot, env.stack(food, Material.WHEAT, 3));
        pan.slotAdd(event);
        assertFalse(event.isCancelled());
        assertEquals(1, event.getItem().getAmount());
        return env.place(slot, event.getItem());
    }

    FurnitureSlotItemTakeEvent take(String slot) {
        FurnitureSlotItemTakeEvent event = env.takeEvent(slot, env.active.get(slot).getCurrentItem());
        pan.slotRemove(event);
        return event;
    }

    void tick(int times) { for (int i = 0; i < times; i++) pan.tick(); }
    int cooked(FoodItem food) { return food.getTagTrack("cooked").getValue(); }

    @Test void choppedCornPopsIntoNamedPopcornKeepingItsStarsFreshnessAndLineageThenBurnsAsPopcorn() {
        pan.getSecondaries().put("butter", -1);
        FoodItem corn = choppedCorn();
        add(corn, "input_1");
        assertEquals(0, cooked(pan.getSlot("input_1")), "In the pan, the chopped corn is raw");

        tick(4);
        assertSame(corn, pan.getSlot("input_1"));
        tick(1);
        FoodItem popcorn = pan.getSlot("input_1");
        assertEquals("popcorn", popcorn.getId());
        assertEquals("snack", popcorn.getCategory());
        assertEquals("Corn", popcorn.getOrigin());
        assertEquals(1, cooked(popcorn));
        assertEquals(4, popcorn.getEffectiveQuality());
        assertEquals(90000, popcorn.getTagTrack("freshness").getValue());
        assertEquals(0.5, popcorn.getAgeRemainder("freshness"));
        assertFalse(popcorn.hasTagTrack("cut"), "Chopped stays with the corn");
        assertEquals(List.of("Corn"), popcorn.getIngredients());
        assertEquals(List.of("Corn"), popcorn.getLineage().mains());
        assertNull(popcorn.getCookData().getParameters().get(Method.FRYING_PAN).getOutput());
        env.updater.verify(() -> ItemUpdater.applyItemUpdate(
                argThat(stack -> stack != null && popcorn.getName().equals(stack.getItemMeta().getDisplayName())),
                same(popcorn), eq("board")));

        tick(3);
        assertSame(popcorn, pan.getSlot("input_1"), "Burnt popcorn stays popcorn");
        assertEquals(2, cooked(popcorn));

        FurnitureSlotItemTakeEvent taken = take("input_1");
        assertFalse(taken.isCancelled());
        assertSame(popcorn, env.resolve(taken.getItem()));
        assertTrue(popcorn.hasTagTrack("warmth"));
        assertFalse(pan.getSecondaries().containsKey("butter"), "Taking a piece off uses up the butter");
    }

    @Test void cornTakenOffBeforeItPopsComesBackAsTheChoppedCornThatWentIn() {
        pan.getSecondaries().put("butter", -1);
        FoodItem early = choppedCorn();
        add(early, "input_1");
        tick(1);
        FurnitureSlotItemTakeEvent quick = take("input_1");
        assertFalse(quick.isCancelled());
        assertSame(early, env.resolve(quick.getItem()));
        assertFalse(early.hasTagTrack("cooked"), "No Raw tag on chopped corn");
        assertNull(pan.getSlot("input_1"));

        FoodItem late = piece("vegetable_chopped_2", "garnish", "Corn", POPCORN, 10, 20);
        add(late, "input_2");
        tick(6);
        FurnitureSlotItemTakeEvent slow = take("input_2");
        assertFalse(slow.isCancelled());
        assertFalse(late.hasTagTrack("cooked"));
        assertFalse(late.hasTagTrack("warmth"), "Not cooked, so not served hot");
        assertTrue(pan.getSecondaries().containsKey("butter"), "The butter is still in the pan");
    }

    @Test void cornLeftWithoutButterBurnsAndPopsBurnt() {
        add(piece("vegetable_chopped_2", "garnish", "Corn", POPCORN, 15, 30), "input_1");
        tick(10);
        FoodItem popcorn = pan.getSlot("input_1");
        assertEquals("popcorn", popcorn.getId());
        assertEquals(2, cooked(popcorn));
        tick(1);
        assertSame(popcorn, pan.getSlot("input_1"));
    }

    @Test void aGrilledMushroomKeepsTheMushroomsOriginWhenTheOutputNamesNone() {
        pan.getSecondaries().put("butter", -1);
        add(piece("mushroom", "vegetable", "Brown Mushroom", GRILLED, 2, 4), "input_1");
        tick(2);
        FoodItem grilled = pan.getSlot("input_1");
        assertEquals("grilled_mushroom", grilled.getId());
        assertEquals("Brown Mushroom", grilled.getOrigin());
        assertEquals("vegetable", grilled.getCategory());
    }

    @Test void anOutputThatDoesNotParseLeavesThePieceCookingAsItself() {
        pan.getSecondaries().put("butter", -1);
        FoodItem corn = piece("vegetable_chopped_2", "garnish", "Corn", "snack(type=missing)", 5, 20);
        add(corn, "input_1");
        tick(6);
        assertSame(corn, pan.getSlot("input_1"));
        assertEquals(1, cooked(corn));
        FurnitureSlotItemTakeEvent taken = take("input_1");
        assertFalse(taken.isCancelled());
        assertTrue(corn.hasTagTrack("cooked"), "Done, so it keeps its cooked tag");
    }

    @Test void foodThatCooksAsItselfIsUntouchedByOutputs() {
        pan.getSecondaries().put("butter", -1);
        FoodItem steak = env.food("steak", "Beef", Method.FRYING_PAN);
        add(steak, "input_1");
        tick(2);
        assertSame(steak, pan.getSlot("input_1"));
        assertEquals(1, cooked(steak));
    }

    @Test void onlyTypesThatCookAsThemselvesCarryARawTrack() {
        MemoryConfiguration options = new MemoryConfiguration();
        options.set("frying_pan.time", 15); options.set("frying_pan.burn", 30); options.set("frying_pan.output", POPCORN);
        CookData popping = new CookData("vegetable_chopped_2", options);
        assertFalse(popping.cooksInPlace());
        assertEquals(POPCORN, popping.getParameters().get(Method.FRYING_PAN).getOutput());
        options.set("pot.time", 15);
        assertTrue(new CookData("vegetable_chopped_2", options).cooksInPlace());
        assertFalse(new CookData().cooksInPlace());

        CookParameter plain = new CookParameter(1, 2, 3, " ");
        assertNull(plain.getOutput());
        assertSame(plain, plain.withoutOutput());
        CookParameter popped = new CookParameter(1, 2, 3, POPCORN).withoutOutput();
        assertEquals(List.of(1, 2, 3), List.of(popped.getTag(), popped.getTime(), popped.getBurnTime()));
        assertNull(popped.getOutput());
    }

    @Test void theShippedConfigPopsCornAndGrillsMushroomsWithoutARawTagOnTheirInputs() throws Exception {
        new FoodLoader().load(new File("src/main/resources/types.yml"));
        FoodItem chopped = new FoodItem(FoodLoader.getByString("vegetable_chopped_2"));
        assertEquals(POPCORN, chopped.getCookData().getParameters().get(Method.FRYING_PAN).getOutput());
        assertFalse(chopped.hasTagTrack("cooked"), "Chopped tomatoes keep their values");
        FoodItem mushroom = new FoodItem(FoodLoader.getByString("mushroom"));
        assertEquals(GRILLED, mushroom.getCookData().getParameters().get(Method.FRYING_PAN).getOutput());
        assertFalse(mushroom.hasTagTrack("cooked"));
        assertTrue(new FoodItem(FoodLoader.getByString("egg")).hasTagTrack("cooked"));
        for (String id : List.of("grilled_mushroom", "popcorn")) {
            assertTrue(FoodLoader.getByString(id).getCookData().getParameters().isEmpty(), id);
        }

        String models = Files.readString(Path.of("src/main/resources/models.yml"));
        String conversions = Files.readString(Path.of("src/main/resources/conversions.yml"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        StringBuilder items = new StringBuilder();
        for (String file : List.of("items.yml", "intermediates.yml")) {
            items.append(Files.readString(Path.of("ItemsAdder/tfmc_cooking/contents/" + file)));
        }
        for (String model : List.of("brown_mushroom", "red_mushroom", "grilled_mushroom", "popcorn")) {
            assertTrue(models.contains("\n" + model + ":\n"), model);
        }
        for (String id : List.of("brown_mushroom_rotten", "red_mushroom_rotten", "grilled_mushroom", "grilled_mushroom_burnt",
                "grilled_mushroom_rotten", "popcorn", "popcorn_burnt", "popcorn_rotten")) {
            assertTrue(models.contains("item: ia.tfmc_cooking:" + id + "\n"), id);
            assertTrue(items.toString().contains("\n  " + id + ":\n"), id);
            String texture = id.endsWith("mushroom_rotten") && !id.startsWith("grilled") ? "vanilla/" : "intermediate/";
            assertTrue(Files.exists(Path.of("ItemsAdder/tfmc_cooking/resourcepack/assets/tfmc_cooking/textures/item/"
                    + texture + id + ".png")), id);
        }
        assertTrue(conversions.contains("v.brown_mushroom vegetable(type=mushroom;origin=Brown Mushroom;tags=freshness.0)"));
        assertTrue(conversions.contains("v.red_mushroom vegetable(type=mushroom;origin=Red Mushroom;tags=freshness.0)"));
        assertTrue(config.contains("snack: \"#e8c56bSnack\""));
    }
}
