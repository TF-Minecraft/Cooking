package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.OverrideData;
import net.tfminecraft.cooking.item.model.FoodModel;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.quality.QualityConfig;
import net.tfminecraft.cooking.quality.OriginQualityResolver;

class ItemBuilderCoverageTest {
    ServerMock server;
    Cooking previousPlugin;
    List<FoodItem> previousFoods;
    List<TagTrack> previousTracks;
    @TempDir Path temporary;

    @BeforeEach void setup() {
        server=MockBukkit.mock(); previousPlugin=Cooking.plugin;
        Cooking.plugin=mock(Cooking.class); when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        previousFoods=new ArrayList<>(FoodLoader.oList); FoodLoader.oList.clear();
        previousTracks=new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
        QualityConfig.apply(3,3,null,null);
    }
    @AfterEach void cleanup() {
        FoodLoader.oList.clear(); FoodLoader.oList.addAll(previousFoods);
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(previousTracks);
        QualityConfig.apply(1,5,null,null); MockBukkit.unmock(); Cooking.plugin=previousPlugin;
    }

    @Test void batchBuildsEitherIndependentItemsOrOneRequestedStack() {
        FoodItem template=food("bread","Bread"); template.setAmount(3); template.setQualityRange(2,2);
        List<ItemStack> separate=ItemBuilder.build(template,true,null);
        assertEquals(3,separate.size());
        assertNotSame(separate.get(0),separate.get(1));
        for(ItemStack stack:separate) { assertEquals(1,stack.getAmount()); assertEquals(2,FoodItem.fromItem(stack).getQualityMin()); }
        List<ItemStack> combined=ItemBuilder.build(template,false,null);
        assertEquals(1,combined.size()); assertEquals(3,combined.getFirst().getAmount());
        assertEquals(3,template.getAmount());
        template._parsedQualMin=4; template._parsedQualMax=4;
        assertEquals(4,FoodItem.fromItem(ItemBuilder.buildSingle(template,null)).getQualityMin());
        assertEquals(5,FoodItem.fromItem(ItemBuilder.buildSingleWithQuality(template,5)).getQualityMin());
        assertNotNull(new ItemBuilder());
    }

    @Test void namesUseOriginOverridesAndInheritedDisplayNamesWithoutChangingBaseItems() {
        FoodItem template=food("bread","{inherit} loaf");
        ItemStack base=new ItemStack(Material.PAPER); var meta=base.getItemMeta(); meta.setDisplayName("Rye"); base.setItemMeta(meta);
        assertEquals("Rye loaf",ItemBuilder.buildSingleWithQuality(template,base,2).getItemMeta().getDisplayName());
        template.setOrigin("wheat");
        assertEquals("Wheat loaf",ItemBuilder.buildSingleWithQuality(template,2).getItemMeta().getDisplayName());
        template.setOrigin(null);
        assertEquals("Unknown loaf",ItemBuilder.buildSingleWithQuality(template,2).getItemMeta().getDisplayName());
        template.setOrigin("fish"); template.getOverrides().put("FISH",new OverrideData("Fish pie",null,null));
        assertEquals("Fish pie",ItemBuilder.buildSingleWithQuality(template,2).getItemMeta().getDisplayName());
        template.getOverrides().put("FISH",new OverrideData(null,null,null));
        assertEquals("Fish loaf",ItemBuilder.buildSingleWithQuality(template,2).getItemMeta().getDisplayName());
        template.setModel(null);
        assertEquals(Material.PAPER,ItemBuilder.buildSingleWithQuality(template,base,2).getType());
        assertEquals("Rye",base.getItemMeta().getDisplayName());
    }

    @Test void displayOverridesDoNotDependOnTheServerLocale() {
        FoodItem template=food("bread","Bread"); template.setOrigin("fish");
        template.getOverrides().put("FISH",new OverrideData("Fish pie",null,null));
        Locale original=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("Fish pie",ItemBuilder.buildSingleWithQuality(template,2).getItemMeta().getDisplayName());
        } finally { Locale.setDefault(original); }
    }

    @Test void compositionUsesItsIngredientNameTemplate() {
        FoodItem template=food("bread","{fillers}Bread"); template.addIngredient("Rye");
        assertEquals("Rye Bread",ItemBuilder.buildComposedWithQuality(template,3).getItemMeta().getDisplayName());
    }

    @Test void restampingRemovesObsoleteMetadataAndDoesNotRequireAnItemName() {
        FoodItem template=food("bread","Bread"); template.setCategory(null);
        ItemStack stack=new ItemStack(Material.BREAD); var meta=stack.getItemMeta(); var p=meta.getPersistentDataContainer();
        for(var key:List.of(Keys.ORIGIN,Keys.TAGS,Keys.SAUCE,Keys.SAUCE_NAME,Keys.INGREDIENTS,Keys.LINEAGE,Keys.AGE_REMAINDER,
                Keys.SEAFOOD_CUT_TYPE,Keys.CUSTOM_FISHING_ID,Keys.COOK_METHOD)) p.set(key,PersistentDataType.STRING,"old");
        p.set(Keys.BASE_FOOD,PersistentDataType.DOUBLE,99.0); p.set(Keys.BASE_NUTRITION,PersistentDataType.DOUBLE,99.0);
        p.set(Keys.CATCH_SIZE_CM,PersistentDataType.INTEGER,99); p.set(Keys.COOK_TIME,PersistentDataType.INTEGER,99);
        stack.setItemMeta(meta);
        assertSame(stack,ItemBuilder.stamp(stack,template,null));
        p=stack.getItemMeta().getPersistentDataContainer();
        for(var key:List.of(Keys.ORIGIN,Keys.TAGS,Keys.SAUCE,Keys.SAUCE_NAME,Keys.INGREDIENTS,Keys.LINEAGE,Keys.AGE_REMAINDER,
                Keys.SEAFOOD_CUT_TYPE,Keys.CUSTOM_FISHING_ID,Keys.COOK_METHOD,Keys.BASE_FOOD,Keys.BASE_NUTRITION,Keys.CATCH_SIZE_CM,Keys.COOK_TIME))
            assertFalse(p.has(key),key.toString());
        assertEquals("",p.get(Keys.CATEGORY,PersistentDataType.STRING));
        assertTrue(p.has(Keys.LAST_UPDATE)); assertTrue(p.has(Keys.LORE_INDEX_MAP));
        assertSame(stack,ItemBuilder.stamp(stack,null,null)); assertNull(ItemBuilder.stamp(null,template,null));
        assertEquals(Material.AIR,ItemBuilder.stamp(new ItemStack(Material.AIR),template,"Food").getType());
        MemoryConfiguration fixed=new MemoryConfiguration(); fixed.set("update",false);
        FoodItem salt=new FoodItem("salt",fixed); ItemBuilder.stamp(stack,salt,"Salt");
        assertFalse(stack.getItemMeta().getPersistentDataContainer().has(Keys.LAST_UPDATE));
        assertFalse(ItemBuilder.writesAgeClock(null));
    }

    @Test void loreContainsWrappedIngredientsSauceAndOnlyVisibleTags() {
        FoodItem food=food("bread","Bread"); food.addIngredient("Long garden carrot"); food.addIngredient("Long garden onion");
        FoodItem sauce=food("sauce","Sauce"); food.setSauce(sauce); food.setSauceName("Gravy");
        TagTrack named=track("freshness","fresh","Fresh",1,1,0);
        food.addOrModifyTrack(named);
        List<String> lore=ItemBuilder.buildLore(food);
        assertTrue(lore.stream().anyMatch(s->s.contains("Long garden carrot")));
        assertTrue(lore.stream().anyMatch(s->s.contains("Long garden onion")));
        assertTrue(lore.stream().anyMatch(s->s.contains("Gravy")));
        food.setSauceName(null); assertTrue(ItemBuilder.buildLore(food).stream().anyMatch(s->s.contains("Sauce")));
        food.addOrModifyTrack(track("processing","processed","Processed",1,1,0));
        assertFalse(ItemBuilder.buildLore(food).stream().anyMatch(s->s.contains("Origin:")));
        TagTrack silent=track("silent","custom","",1,1,0);
        assertFalse(ItemBuilder.shouldShowTagLore(null,silent,silent.getCurrentStep()));
        TagTrack foodChange=track("food","custom","",2,1,0);
        assertTrue(ItemBuilder.shouldShowTagLore(null,foodChange,foodChange.getCurrentStep()));
        TagTrack nutritionChange=track("nutrition","custom","",1,2,0);
        assertTrue(ItemBuilder.shouldShowTagLore(null,nutritionChange,nutritionChange.getCurrentStep()));
        TagTrack craftChange=track("craft","custom","",1,1,.5);
        assertTrue(ItemBuilder.shouldShowTagLore(null,craftChange,craftChange.getCurrentStep()));
        TagTrack colourOnly=track("colour","custom","#abcdef ",1,1,0);
        assertFalse(ItemBuilder.shouldShowTagLore(null,colourOnly,colourOnly.getCurrentStep()));
        TagTrack nonHex=track("nonhex","custom","#xxxxxxName",1,1,0);
        assertTrue(ItemBuilder.shouldShowTagLore(null,nonHex,nonHex.getCurrentStep()));
        Map<String,Integer> indexes=new HashMap<>();
        List<String> assembled=ItemBuilder.assembleLore("Bread",null,3,"Nutrition","Food",null,null,null,List.of(silent,named),indexes);
        assertTrue(indexes.containsKey("tags")); assertFalse(assembled.contains("Origin:"));
        assertFalse(ItemBuilder.assembleLore("Bread",null,3,"Nutrition","Food",null,null,null,null,null).isEmpty());
        assertEquals("§6★§7☆§7☆§7☆§7☆",ItemBuilder.qualityStars(-10));
        assertEquals("§6★§6★§6★§6★§6★",ItemBuilder.qualityStars(20));
    }

    @Test void commandsParseQualityAndBatchSizeAndPlayPickupSoundOnlyOnce() {
        FoodItem template=food("bread","Bread");
        Player player=mock(Player.class); World world=mock(World.class); Location location=new Location(world,0,60,0);
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(location);
        try(MockedStatic<InventoryAdder> adder=mockStatic(InventoryAdder.class)) {
            adder.when(()->InventoryAdder.addItem(eq(player),any())).thenReturn(null);
            ItemBuilder.buildFromString(player,"bread(type=bread;amount=2;quality=4;unique=true)",null);
            verify(player,times(1)).playSound(location,Sound.ENTITY_ITEM_PICKUP,1f,1f);
            adder.verify(()->InventoryAdder.addItem(eq(player),any()),times(2));
        }
        clearInvocations(player);
        try(MockedStatic<InventoryAdder> adder=mockStatic(InventoryAdder.class);
            MockedStatic<OriginQualityResolver> origin=mockStatic(OriginQualityResolver.class)) {
            origin.when(()->OriginQualityResolver.resolve(eq(player),any())).thenReturn(3);
            ItemStack leftover=new ItemStack(Material.BREAD,2);
            adder.when(()->InventoryAdder.addItem(eq(player),any())).thenReturn(leftover);
            ItemBuilder.buildFromString(player,"bread(type=bread;amount=2;unique=false)",null);
            verify(world).dropItemNaturally(location,leftover);
            verify(player,never()).playSound(any(Location.class),any(Sound.class),anyFloat(),anyFloat());
            origin.verify(()->OriginQualityResolver.resolve(eq(player),any()),times(1));
            ItemBuilder.buildFromString(player,"bread(type=bread;amount=2;unique=true)",null);
            origin.verify(()->OriginQualityResolver.resolve(eq(player),any()),times(3));
        }
        ItemBuilder.buildFromString(player,"invalid",null); verify(player).sendMessage("§cInvalid item string!");
        assertEquals(4,FoodItem.fromItem(ItemBuilder.buildSingleString("bread(type=bread;quality=4)",null)).getQualityMin());
        assertEquals(3,FoodItem.fromItem(ItemBuilder.buildSingleString("bread(type=bread)",null)).getQualityMin());
    }

    @Test void invalidSingleItemRequestIsRejectedWithoutThrowing() {
        assertNull(ItemBuilder.buildSingleString("not_a_food",null));
        assertNull(ItemBuilder.buildSingleString("bread(type=missing)",null));
    }

    @Test void configuredRoastsStartAtTheirFirstCarveStage() throws Exception {
        Path sequences=temporary.resolve("carve.yml");
        Files.writeString(sequences,"roast:\n  start-remaining: 2\n  cuts:\n    - output: bread\n      food: 2\n      nutrition: 1\n    - item: v.bone\n");
        new CarveSequenceLoader().load(sequences.toFile());
        MemoryConfiguration config=new MemoryConfiguration(); config.set("carve-sequence","roast");
        FoodItem template=new FoodItem("roast",config); template.setModel(new FoodModel(new ItemStack(Material.BEEF))); FoodLoader.oList.add(template);
        FoodItem restored=FoodItem.fromItem(ItemBuilder.buildSingleWithQuality(template,2));
        assertEquals("roast",restored.getCarveSequencePdc()); assertEquals(0,restored.getCarveNextIndex()); assertEquals(2,restored.getCarveRemaining());
        template.getOverrides().put("UNKNOWN",new OverrideData(null,null,"missing")); template.setOrigin("unknown");
        assertFalse(FoodItem.fromItem(ItemBuilder.buildSingleWithQuality(template,2)).hasCarveState());
        Files.writeString(sequences,""); new CarveSequenceLoader().load(sequences.toFile());
    }

    @Test void anEmptyTagTrackDoesNotPreventRenderingFoodLore() {
        FoodItem template=food("bread","Bread");
        template.addOrModifyTrack(new TagTrack("unconfigured",false,List.of()));
        assertTrue(template.getCurrentTags().isEmpty()); assertTrue(template.getCurrentTagIds().isEmpty());
        assertDoesNotThrow(()->ItemBuilder.buildSingleWithQuality(template,2));
    }

    private FoodItem food(String id,String name) {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("name",name); config.set("food",2); config.set("nutrition",2);
        FoodItem food=new FoodItem(id,config); food.setCategory(id); food.setModel(new FoodModel(new ItemStack(Material.BREAD)));
        FoodLoader.oList.add(food); return food;
    }
    private static TagTrack track(String id,String step,String name,double food,double nutrition,double craft) {
        return new TagTrack(id,false,List.of(new TagStep(step,name,0,food,nutrition,0,craft)));
    }
}
