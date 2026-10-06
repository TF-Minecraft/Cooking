package net.tfminecraft.cooking.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.events.DishCookedEvent;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.item.data.OverrideData;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagQuality;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.ConversionLoader;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.IngredientConverter;
import net.tfminecraft.cooking.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngredientRuntimeCoverageTest {
    Workshop env;
    @BeforeEach void setup() { env=new Workshop(); }
    @AfterEach void cleanup() { env.close(); }

    @Test void pathsBuildOneItemAndMatchExplicitQualityAndTagFilters() {
        FoodItem food=env.food("fish","Tuna",3); food.setCategory("seafood"); env.templates.put("fish",food);
        CookingPathHandler handler=CookingPathHandler.INSTANCE;
        assertNull(handler.create(null)); assertNull(handler.create("c.")); assertNull(handler.create("seafood"));
        assertNull(handler.create("seafood(type=missing)")); assertNull(handler.create("seafood(type=fish"));
        String path="seafood(type=fish)", exact="seafood(type=fish;quality=3)";
        assertNull(handler.create(path));
        ItemStack plain=new ItemStack(Material.COD,8), quality=new ItemStack(Material.COD,5);
        env.builder.when(()->ItemBuilder.buildSingleString(path,null)).thenReturn(plain);
        env.builder.when(()->ItemBuilder.buildSingle(any(FoodItem.class),isNull())).thenReturn(quality);
        assertSame(plain,handler.create("c."+path)); assertEquals(1,plain.getAmount());
        assertSame(quality,handler.create("c."+exact)); assertEquals(1,quality.getAmount());
        assertTrue(handler.matches(env.stack(food,Material.COD,1),"seafood"));
        assertFalse(CookingPathHandler.matches((FoodItem)null,"seafood")); assertFalse(CookingPathHandler.matches(food,null));
        assertFalse(CookingPathHandler.matches(food,"c. ")); assertFalse(CookingPathHandler.matches(food,"(type=fish)"));
        assertTrue(CookingPathHandler.matches(food,"seafood(quality=4-2)")); assertFalse(CookingPathHandler.matches(food,"seafood(quality=nope)"));
        assertTrue(CookingPathHandler.matches(food,"seafood(type= ;origin= )"));
        assertTrue(CookingPathHandler.matches(food,"seafood(tags= , ignored)"));
        assertFalse(CookingPathHandler.matches(food,"seafood(tags=freshness.nope)"));
        TagTrack fresh=new TagTrack("freshness",false,List.of()); fresh.forceSetValue(4); food.addOrModifyTrack(fresh);
        assertTrue(CookingPathHandler.matches(food,"seafood(tags=FRESHNESS.4)"));
        TagTrack special=new TagTrack("Special",false,List.of()); special.forceSetValue(2); food.addOrModifyTrack(special);
        assertTrue(CookingPathHandler.matches(food,"seafood(tags=SPECIAL.2)"));
        assertNull(CookingPathHandler.toShortPath(null)); food.setOrigin(null); food.setCategory(null);
        assertEquals("c.(type=fish;origin=)",CookingPathHandler.toShortPath(food)); assertFalse(CookingPathHandler.matches(food,"seafood"));
    }

    @Test void ingredientConversionRetainsInputsWhenThereIsNoValidReplacement() {
        ItemStack wheat=new ItemStack(Material.WHEAT,9), air=new ItemStack(Material.AIR);
        assertNull(IngredientConverter.convertIfNeeded(null,null)); assertSame(air,IngredientConverter.convertIfNeeded(null,air));
        ItemStack already=env.stack(env.food("wheat","Wheat",2),Material.WHEAT,3);
        assertSame(already,IngredientConverter.convertIfNeeded(null,already));
        try(var conversions=mockStatic(ConversionLoader.class)) {
            assertSame(wheat,IngredientConverter.convertIfNeeded(null,wheat));
            conversions.when(()->ConversionLoader.getByItem(wheat)).thenReturn("invalid");
            assertSame(wheat,IngredientConverter.convertIfNeeded(null,wheat));
            conversions.when(()->ConversionLoader.getByItem(wheat)).thenReturn("grain(type=wheat;origin=Wheat)");
            env.builder.when(()->ItemBuilder.buildSingleWithQuality(any(FoodItem.class),same(wheat),anyInt())).thenReturn(null);
            assertSame(wheat,IngredientConverter.convertIfNeeded(env.player,wheat));
            env.builder.when(()->ItemBuilder.buildSingleWithQuality(any(FoodItem.class),same(wheat),anyInt())).thenReturn(air);
            assertSame(wheat,IngredientConverter.convertIfNeeded(env.player,wheat));
            ItemStack output=env.stack(env.food("wheat","Wheat",3),Material.WHEAT,1);
            env.builder.when(()->ItemBuilder.buildSingleWithQuality(any(FoodItem.class),same(wheat),anyInt())).thenReturn(output);
            assertSame(output,IngredientConverter.convertHarvestDrop(env.player,wheat,100)); assertEquals(9,output.getAmount());
            conversions.when(ConversionLoader::get).thenReturn(Map.of("v.wheat","grain(type=wheat;origin=Wheat)"));
            assertSame(output,IngredientConverter.convertHarvestDrop(env.player,wheat,0));
            env.builder.verify(()->ItemBuilder.buildSingleWithQuality(any(FoodItem.class),same(wheat),eq(1)));
        }
    }

    @Test void lineageAndTagMetadataRetainTheirPublicBoundarySemantics() {
        assertEquals(0,TagQuality.stars(null)); assertEquals(0,TagQuality.stars(Arrays.asList(null,new TagTrack("empty",false,List.of()))));
        assertNull(AgeScale.migrateTrackId(null)); assertEquals(new AgeScale.Scaled(3,0.5),AgeScale.apply(3,0,2,0.5));
        IngredientLineage base=IngredientLineage.ofMain("Tuna").withExtra("Salt");
        assertTrue(IngredientLineage.from(null,CompositionContext.BAKING).isEmpty());
        assertSame(base, base.merge(IngredientLineage.empty()));
        assertEquals(List.of("Salt","Pepper"),base.merge(IngredientLineage.ofExtra("Pepper")).extras());
        assertFalse(base.equals("Tuna")); assertEquals(base.hashCode(),IngredientLineage.ofMain("Tuna").withExtra("Salt").hashCode());
        assertTrue(IngredientLineage.forEat(null).isEmpty());
        FoodItem food=env.food("fish","Tuna",2); food.setLineage(base);
        assertEquals(base,IngredientLineage.from(Arrays.asList(null,food),CompositionContext.BAKING));
        String origin="A%|;,=)(B"; assertEquals(origin,IngredientLineageCodec.decode(IngredientLineageCodec.encode(IngredientLineage.ofMain(origin))).mains().getFirst());
        assertEquals("%z0%0z",IngredientLineageCodec.unescape("%z0%0z"));
        assertNull(new OverrideData(null,null,null,Map.of("freshness",2.0)).getAge(null));
        DishCookedEvent event=new DishCookedEvent(env.player,new ItemStack(Material.COD),"bake"); assertEquals("bake",event.getMethod());
        assertSame(DishCookedEvent.getHandlerList(),event.getHandlers());
    }

    @Test void cookingTimersResetAndIgnoreInactiveOrUnavailableTracks() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("invalid.time",2); config.set("pot.time",2);
        CookData settings=new CookData("test",config); FoodItem food=env.food("test",null,2); CookData data=new CookData(food,settings);
        assertSame(food,data.getFoodItem()); data.restore(Method.POT,5); data.reset();
        assertNull(data.getCurrentMethod()); assertEquals(0,data.getCurrentTime());
        assertFalse(data.check()); assertDoesNotThrow(()->data.setCurrentTime(2));
        data.start(Method.POT); assertDoesNotThrow(()->data.setCurrentTime(2));
    }
}
