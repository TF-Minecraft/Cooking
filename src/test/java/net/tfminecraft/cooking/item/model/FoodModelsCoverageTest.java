package net.tfminecraft.cooking.item.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.interactiblefurniture.furniture.data.DisplayData;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;

class FoodModelsCoverageTest {
    MockedStatic<TLibs> tlibs;
    @BeforeEach void setup() {
        MockBukkit.mock(); tlibs=mockStatic(TLibs.class);
        ItemAPI api=mock(ItemAPI.class,RETURNS_DEEP_STUBS); tlibs.when(TLibs::getItemAPI).thenReturn(api);
        when(api.getCreator().getItemFromPath(anyString())).thenAnswer(a->new ItemStack(Material.valueOf(a.<String>getArgument(0).substring(2).toUpperCase(Locale.ROOT))));
    }
    @AfterEach void cleanup() { tlibs.close(); MockBukkit.unmock(); }

    @Test void configuredModelsSelectMostSpecificMatchingStateAndExcludeCarveStages() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("colour","ff0000"); config.set("invalid","scalar");
        state(config,"default",0,-1,List.of(),"v.bread");
        state(config,"cooked",5,-1,List.of("cooked"),"v.cooked_beef");
        state(config,"rotten",10,-1,List.of("rotten"),"v.rotten_flesh");
        state(config,"stage",20,1,List.of("cooked"),"v.bone");
        state(config,"later",20,2,List.of("cooked"),"v.beef");
        config.set("display-data-furniture.oven.position.y",.5);
        FoodModel model=new FoodModel("roast",config);
        assertEquals("roast",model.getId()); assertEquals(5,model.getStates().size());
        FoodItem food=new FoodItem("roast",new MemoryConfiguration());
        assertEquals(Material.BREAD,model.getModel(food).apply(null,null).getType());
        food.addOrModifyTrack(track("cooked","cooked"));
        assertEquals(Material.COOKED_BEEF,model.getModel(food).apply(null,null).getType());
        assertEquals(Material.BONE,model.getModelByStageAndTag(1,"cooked").apply(null,null).getType());
        assertEquals(Material.COOKED_BEEF,model.getModelByStageAndTag(8,"cooked").apply(null,null).getType());
        assertNotNull(model.getModelByStageAndTag(8,"unknown"));
        food.addOrModifyTrack(track("freshness","rotten"));
        assertEquals(Material.ROTTEN_FLESH,model.getModel(food).apply(null,null).getType());
        MemoryConfiguration unmatched=new MemoryConfiguration(); state(unmatched,"only",0,-1,List.of("rotten"),"v.rotten_flesh");
        assertNotNull(new FoodModel("fallback",unmatched).getModel(new FoodItem("plain",new MemoryConfiguration())));
    }

    @Test void modelPosesAndItemOverridesInheritFromTheirRoot() {
        MemoryConfiguration config=new MemoryConfiguration(); config.set("gui.item","v.bread"); config.set("display.item","v.paper");
        config.set("display.furniture",List.of("oven v.bone","broken")); config.set("tags",List.of("cooked"));
        config.set("display-data.position.x",.25); config.set("display-data-furniture.oven.position.y",.5);
        config.set("display-data-furniture.invalid","scalar");
        DisplayData parent=new DisplayData(); parent.setzPos(.75f);
        ModelData model=new ModelData(config,Map.of("table",parent));
        assertEquals(Material.BREAD,model.apply(null,null).getType());
        assertEquals(Material.BONE,model.apply("oven",null).getType());
        assertEquals(Material.PAPER,model.apply("table",null).getType());
        assertEquals(.25f,model.getDisplayData().getxPos());
        assertEquals(.5f,model.getDisplayData("OVEN").getyPos());
        assertEquals(.75f,model.getDisplayData("table").getzPos());
        assertSame(model.getDisplayData(),model.getDisplayData(null));
        assertSame(model.getDisplayData(),model.getDisplayData("unknown"));
        assertEquals(Material.AIR,new ModelData(new MemoryConfiguration()).apply(null,null).getType());
        assertNotNull(new ModelData(config,null).getDisplayData());
    }

    @Test void copiedModelPosesCannotChangeTheTemplate() {
        MemoryConfiguration config=new MemoryConfiguration(); state(config,"default",0,-1,List.of(),"v.bread");
        config.set("default.display-data.position.x",.25); config.set("default.display-data-furniture.oven.position.y",.5);
        FoodModel original=new FoodModel("bread",config); FoodModel copied=new FoodModel(original);
        ModelData originalData=original.getStates().get("default"),copy=copied.getStates().get("default");
        copy.getDisplayData().setxPos(7); copy.getDisplayData("oven").setyPos(8);
        assertEquals(.25f,originalData.getDisplayData().getxPos());
        assertEquals(.5f,originalData.getDisplayData("oven").getyPos());
    }

    @Test void directModelsCloneItemStacksAndPreserveSourceMetadata() {
        ItemStack template=new ItemStack(Material.BREAD);
        ModelData original=new ModelData(template); ModelData model=new ModelData(original);
        template.setType(Material.STONE);
        ItemStack source=new ItemStack(Material.PAPER,3); var meta=source.getItemMeta();
        meta.setDisplayName("Dinner"); meta.setLore(List.of("Fresh")); var p=meta.getPersistentDataContainer();
        p.set(key("text"),PersistentDataType.STRING,"value"); p.set(key("number"),PersistentDataType.INTEGER,2);
        p.set(key("time"),PersistentDataType.LONG,3L); p.set(key("fraction"),PersistentDataType.DOUBLE,4.5);
        source.setItemMeta(meta);
        ItemStack result=model.apply(null,source);
        assertEquals(Material.BREAD,result.getType()); assertEquals(3,result.getAmount());
        assertEquals("Dinner",result.getItemMeta().getDisplayName()); assertEquals(List.of("Fresh"),result.getItemMeta().getLore());
        var out=result.getItemMeta().getPersistentDataContainer();
        assertEquals("value",out.get(key("text"),PersistentDataType.STRING)); assertEquals(2,out.get(key("number"),PersistentDataType.INTEGER));
        assertEquals(3L,out.get(key("time"),PersistentDataType.LONG)); assertEquals(4.5,out.get(key("fraction"),PersistentDataType.DOUBLE));
        assertEquals(90,model.getDisplayData().getxRot()); assertEquals(90,model.getDisplayData().getzRot());
        assertEquals(-.25f,model.getDisplayData().getyPos());
        result.setType(Material.STONE); assertEquals(Material.BREAD,model.apply(null,null).getType());
        ItemRef.mergeMeta(result,new ItemStack(Material.AIR));
        ItemRef.mergeMeta(new ItemStack(Material.AIR),source);
        assertNotNull(new ItemRef());
    }

    @Test void modelChangesPreserveAllPersistentDataTypes() {
        ItemStack source=new ItemStack(Material.PAPER); var meta=source.getItemMeta();
        meta.getPersistentDataContainer().set(key("flag"),PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(key("bytes"),PersistentDataType.BYTE_ARRAY,new byte[]{1,2});
        source.setItemMeta(meta);
        ItemStack result=new ModelData(new ItemStack(Material.BREAD)).apply(null,source);
        assertEquals((byte)1,result.getItemMeta().getPersistentDataContainer().get(key("flag"),PersistentDataType.BYTE));
        assertArrayEquals(new byte[]{1,2},result.getItemMeta().getPersistentDataContainer().get(key("bytes"),PersistentDataType.BYTE_ARRAY));
    }

    private static NamespacedKey key(String key) { return new NamespacedKey("foodtest",key); }
    private static TagTrack track(String id,String step) { return new TagTrack(id,false,List.of(new TagStep(step,step,0,1,1))); }
    private static void state(MemoryConfiguration config,String id,int weight,int stage,List<String> tags,String item) {
        config.set(id+".weight",weight); config.set(id+".stage",stage); config.set(id+".tags",tags); config.set(id+".gui.item",item);
    }
}
