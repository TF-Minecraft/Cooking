package net.tfminecraft.cooking.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class CachesCoverageTest {
    final Map<Field,Object> globals=new HashMap<>();
    List<FoodItem> foods;
    Cooking plugin;
    MockedStatic<TLibs> tlibs;
    ItemAPI api;
    ItemStack plain;

    @BeforeEach void setup() throws Exception {
        MockBukkit.mock(); plugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        foods=new ArrayList<>(FoodLoader.oList); FoodLoader.oList.clear();
        for(Class<?> type:List.of(ItemCache.class,FurnitureCache.class,CategoryDictionary.class)) {
            for(Field field:type.getFields()) if(Modifier.isStatic(field.getModifiers())&&!Modifier.isFinal(field.getModifiers())) {
                Object value=field.get(null); globals.put(field,value);
                if(value instanceof Map<?,?>) field.set(null,new HashMap<>());
                if(value instanceof List<?>) field.set(null,new ArrayList<>());
                if(field.getType()==String.class) field.set(null,field.getName());
            }
        }
        api=mock(ItemAPI.class,RETURNS_DEEP_STUBS); tlibs=mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(api);
        plain=new ItemStack(Material.PAPER);
    }
    @AfterEach void cleanup() throws Exception {
        for(var entry:globals.entrySet()) entry.getKey().set(null,entry.getValue());
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(foods);
        tlibs.close(); MockBukkit.unmock(); Cooking.plugin=plugin;
    }

    @Test void configuredToolsAndCupsUseTheExternalItemMatcher() {
        when(api.getChecker().checkItemWithPath(plain,"ladle")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"masher")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"flour")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"bag")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"water")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"emptyCup")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"cupOfWater")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"cupOfMilk")).thenReturn(true);
        when(api.getChecker().checkItemWithPath(plain,"butter")).thenReturn(true);
        assertTrue(ItemCache.isLadle(plain)); assertTrue(ItemCache.isMasher(plain)); assertTrue(ItemCache.isFlour(plain));
        assertTrue(ItemCache.isBag(plain)); assertTrue(ItemCache.isWater(plain)); assertTrue(ItemCache.isEmptyCup(plain));
        assertTrue(ItemCache.isCupOfWater(plain)); assertTrue(ItemCache.isCupOfMilk(plain)); assertTrue(ItemCache.isButter(plain));
        assertFalse(ItemCache.isCupOfWater(null)); assertFalse(ItemCache.isCupOfMilk(null)); assertFalse(ItemCache.isButter(null));
        assertFalse(ItemCache.isMilkBucket(null)); assertFalse(ItemCache.isMilkBucket(plain));
        assertTrue(ItemCache.isMilkBucket(new ItemStack(Material.MILK_BUCKET)));
        assertFalse(ItemCache.isPotWaterInput(null)); assertTrue(ItemCache.isPotWaterInput(new ItemStack(Material.WATER_BUCKET)));
        when(api.getChecker().checkItemWithPath(plain,"potWaterInput")).thenReturn(true); assertTrue(ItemCache.isPotWaterInput(plain));
        ItemCache.potWaterInput=null; ItemCache.cupOfMilk=null; assertFalse(ItemCache.isPotWaterInput(plain)); assertFalse(ItemCache.isCupOfMilk(plain));
        assertNotNull(new ItemCache());
    }

    @Test void foodMetadataRecognizesButterMilkAndBucketsIndependentlyOfConfiguredPaths() {
        assertTrue(ItemCache.isButter(food("butter",null))); assertTrue(ItemCache.isCupOfMilk(food("cup_of_milk",null)));
        assertTrue(ItemCache.isMilkBucket(food("milk_bucket",null))); assertFalse(ItemCache.isCupOfWater(food("other",null)));
        assertFalse(ItemCache.isButter(food("other",null)));
    }

    @Test void liquidsColoursAndOvenFuelsMatchConfiguredPathsWithFallbacks() {
        ItemCache.liquidModels.put("water","display.water");
        assertFalse(ItemCache.isLiquid(plain)); assertNull(ItemCache.getLiquidModel(plain));
        when(api.getChecker().checkItemWithPath(plain,"water")).thenReturn(true);
        assertTrue(ItemCache.isLiquid(plain)); assertEquals("display.water",ItemCache.getLiquidModel(plain));
        assertEquals("display.water",ItemCache.getLiquidModel("water")); assertNull(ItemCache.getLiquidModel("missing"));
        assertEquals("000000",ItemCache.getColour(null)); assertEquals("000000",ItemCache.getColour(plain));
        ItemCache.colourMap.put("origin.Red_Apple","ff0000"); assertEquals("000000",ItemCache.getColour(plain));
        assertEquals("ff0000",ItemCache.getColour(food("apple","Red Apple")));
        assertEquals("000000",ItemCache.getColour(food("pear","pear")));
        ItemCache.colourMap.put("water","0000ff"); assertEquals("0000ff",ItemCache.getColour(plain));
        assertEquals("000000",ItemCache.getColour(new ItemStack(Material.STONE)));
        assertFalse(ItemCache.matchesOvenFuel(null)); assertFalse(ItemCache.matchesOvenFuel(plain));
        ItemCache.ovenFuel.add("wood"); assertFalse(ItemCache.matchesOvenFuel(plain));
        when(api.getChecker().checkItemWithPath(plain,"wood")).thenReturn(true); assertTrue(ItemCache.matchesOvenFuel(plain));
        assertEquals("",ItemCache.normalizeOrigin(null)); assertTrue(ItemCache.originsMatch("Red Apple","red_apple"));
        assertFalse(ItemCache.originsMatch(null,"apple")); assertFalse(ItemCache.originsMatch(" ","apple"));
        assertFalse(ItemCache.originsMatch("apple",null)); assertFalse(ItemCache.originsMatch("apple"," "));
    }

    @Test void mixingInputsAcceptEitherFoodIdOrAnExplicitFallbackPath() {
        MixingIngredient ingredient=new MixingIngredient("input.flour","flour","display.flour","dough");
        ItemCache.mixingIngredients.put("flour",ingredient);
        assertSame(ingredient,ItemCache.getMixingIngredient("flour")); assertEquals("flour",ingredient.getInputFood());
        assertEquals("input.flour",ingredient.getInput()); assertEquals("display.flour",ItemCache.getMixingModel("flour"));
        assertEquals("dough",ItemCache.getMixingOutput("flour")); assertNull(ItemCache.getMixingModel("missing"));
        assertNull(ItemCache.getMixingOutput("missing")); assertFalse(ItemCache.matchesMixingInput("missing",plain));
        assertFalse(ItemCache.matchesMixingInput("flour",null)); assertFalse(ItemCache.matchesMixingInput("flour",plain));
        assertTrue(ItemCache.matchesMixingInput("flour",food("flour",null)));
        assertFalse(ItemCache.matchesMixingInput("flour",food("bread",null)));
        when(api.getChecker().checkItemWithPath(plain,"input.flour")).thenReturn(true); assertTrue(ItemCache.matchesMixingInput("flour",plain));
        ItemCache.mixingIngredients.put("flour",new MixingIngredient(null,null,null,null));
        assertFalse(ItemCache.matchesMixingInput("flour",plain));
    }

    @Test void furnitureIdsSelectTheirRolesAndCookingMethod() {
        Furniture furniture=mock(Furniture.class,RETURNS_DEEP_STUBS);
        Map<String,Predicate<Furniture>> roles=Map.ofEntries(Map.entry("butterChurn",FurnitureCache::isButterChurn),
                Map.entry("butterPlate",FurnitureCache::isButterPlate),Map.entry("firePit",FurnitureCache::isFirePit),
                Map.entry("meatHook",FurnitureCache::isMeatHook),Map.entry("sausageMaker",FurnitureCache::isSausageMaker),
                Map.entry("mixingBowl",FurnitureCache::isMixingBowl),Map.entry("millingStone",FurnitureCache::isMillingStone),
                Map.entry("ovenBottom",FurnitureCache::isOvenBottom),Map.entry("ovenTop",FurnitureCache::isOvenTop),
                Map.entry("breadTray",FurnitureCache::isBreadTray),Map.entry("liquidContainer",FurnitureCache::isLiquidContainer),
                Map.entry("trough",FurnitureCache::isTrough),Map.entry("plate",FurnitureCache::isPlate),Map.entry("bowl",FurnitureCache::isBowl));
        for(var role:roles.entrySet()) {
            when(furniture.getId()).thenReturn(role.getKey().toUpperCase(Locale.ROOT)); assertTrue(role.getValue().test(furniture));
            when(furniture.getId()).thenReturn("unrelated"); assertFalse(role.getValue().test(furniture));
        }
        when(furniture.getId()).thenReturn("fryingPan"); assertEquals(Method.FRYING_PAN,FurnitureCache.getByFurniture(furniture));
        when(furniture.getId()).thenReturn("saucePan"); assertEquals(Method.SAUCEPAN,FurnitureCache.getByFurniture(furniture));
        when(furniture.getId()).thenReturn("pot"); assertEquals(Method.POT,FurnitureCache.getByFurniture(furniture));
        when(furniture.getId()).thenReturn("other"); assertEquals(Method.NONE,FurnitureCache.getByFurniture(furniture)); assertFalse(FurnitureCache.isMealHolder(furniture));
        when(furniture.getId()).thenReturn("bowl"); assertTrue(FurnitureCache.isMealHolder(furniture));
        when(furniture.getId()).thenReturn("plate"); assertTrue(FurnitureCache.isMealHolder(furniture));
        when(furniture.getType().getItemPath()).thenReturn("ia.tfmc_cooking:pot"); assertTrue(FurnitureCache.isCookingFurniture(furniture));
        when(furniture.getType().getItemPath()).thenReturn("ia.other:table"); assertFalse(FurnitureCache.isCookingFurniture(furniture));
        when(furniture.getType()).thenReturn(null); assertFalse(FurnitureCache.isCookingFurniture(furniture));
        assertNotNull(new FurnitureCache());
    }

    @Test void cookingFurnitureRecognitionDoesNotDependOnTheServersLocale() {
        Furniture furniture=mock(Furniture.class,RETURNS_DEEP_STUBS); when(furniture.getType().getItemPath()).thenReturn("IA.TFMC_COOKING:OVEN");
        Locale old=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertTrue(FurnitureCache.isCookingFurniture(furniture)); }
        finally { Locale.setDefault(old); }
    }

    @Test void categoryNamesAndSauceColoursUseConfiguredValuesOrFallbacks() {
        CategoryDictionary.dictionary.put("meat","Custom Meat"); assertEquals("Custom Meat",CategoryDictionary.getName("meat"));
        assertTrue(CategoryDictionary.getName("garden_vegetable").endsWith("Garden Vegetable"));
        ItemCache.liquidFallback="fallback"; CategoryDictionary.sauceDict.put("ff0000","red|deep.red");
        CategoryDictionary.sauceDict.put("000000","black"); CategoryDictionary.sauceDict.put("bad","invalid");
        assertEquals("red",CategoryDictionary.getSauceItemPath("#fe0101",0)); assertEquals("deep.red",CategoryDictionary.getSauceItemPath("ff0000",1));
        for(String colour:new String[]{null,"000000","bad"}) assertEquals("fallback",CategoryDictionary.getSauceItemPath(colour,0));
        assertEquals("fallback",CategoryDictionary.getSauceItemPath("ff0000",-1)); assertEquals("fallback",CategoryDictionary.getSauceItemPath("ff0000",2));
        CategoryDictionary.sauceDict.put("ff0000","red| "); assertEquals("fallback",CategoryDictionary.getSauceItemPath("ff0000",1));
        CategoryDictionary.sauceDict.clear(); assertEquals("fallback",CategoryDictionary.getSauceItemPath("ff0000",0));
        assertNotNull(new CategoryDictionary());
    }

    private ItemStack food(String id,String origin) {
        FoodItem food=new FoodItem(id,new MemoryConfiguration()); food.setOrigin(origin);
        FoodLoader.oList.removeIf(f->f.getId().equals(id)); FoodLoader.oList.add(food);
        return ItemBuilder.stamp(new ItemStack(Material.PAPER),food,id);
    }
}
