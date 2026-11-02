package net.tfminecraft.cooking.glaze;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Station;
import net.tfminecraft.cooking.sausagemaker.SausageMakerCoverageTest.Workshop;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;

/** Honey glazes the roast on a meat hook or fire pit, with the glazes.yml shipped in the jar. */
class GlazeCoverageTest {
    @TempDir Path dir;
    Workshop env;
    Station hook, pit;
    GlazeHandler handler = new GlazeHandler();
    MockedStatic<ItemUpdater> updater;
    String oldHook, oldPit;

    @BeforeEach void open() {
        oldHook = FurnitureCache.meatHook; oldPit = FurnitureCache.firePit;
        env = new Workshop();
        FurnitureCache.meatHook = "meat_hook"; FurnitureCache.firePit = "fire_pit";
        hook = env.station("meat_hook"); hook.define("content");
        pit = env.station("fire_pit"); pit.define("content");
        new GlazeConfig().load(new File("src/main/resources/glazes.yml"));
        updater = env.scoped(mockStatic(ItemUpdater.class));
        updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any()))
                .thenAnswer(inv -> env.stack(new FoodItem((FoodItem) inv.getArgument(1)), Material.ROTTEN_FLESH, 1));
        when(env.api.getCreator().getItemFromPath("v.glass_bottle")).thenAnswer(inv -> new ItemStack(Material.GLASS_BOTTLE));
    }

    @AfterEach void close() throws Exception {
        Path none = dir.resolve("none.yml");
        Files.writeString(none, "");
        new GlazeConfig().load(none.toFile());
        env.close();
        FurnitureCache.meatHook = oldHook; FurnitureCache.firePit = oldPit;
    }

    FoodItem honeyFood() {
        MemoryConfiguration type = new MemoryConfiguration();
        type.set("name", "Honey"); type.set("update", false); type.set("returns", "v.glass_bottle");
        FoodItem honey = new FoodItem("honey", type);
        honey.setCategory("sweetener"); honey.setOrigin("Honey"); honey.setQualityRange(5, 5);
        return honey;
    }
    ItemStack honey(int amount) { return env.stack(honeyFood(), Material.HONEY_BOTTLE, amount); }
    FoodItem roast() { return env.food("roast", "Beef", 4); }
    void onHook(Station station, FoodItem food) { station.place("content", env.stack(food, Material.ROTTEN_FLESH, 1)); }
    FoodItem content(Station station) { return env.resolve(station.active.get("content").getCurrentItem()); }
    ItemStack hand() { return env.player.getInventory().getItemInMainHand(); }
    FurnitureInteractEvent click(Station station, ItemStack held) {
        env.hold(held);
        FurnitureInteractEvent event = station.interact();
        handler.onInteract(event);
        return event;
    }
    void track(FoodItem food, String id, int value) {
        TagTrack track = new TagTrack(TrackLoader.getByString(id)); track.forceSetValue(value); food.addOrModifyTrack(track);
    }

    @Test void theShippedConfigGlazesRoastsWithHoney() {
        assertEquals(1, GlazeConfig.glazes().size());
        GlazeConfig.Glaze honey = GlazeConfig.glazes().get(0);
        assertEquals("honey", honey.id()); assertEquals("honey_glazed", honey.track());
        assertEquals(List.of("c.sweetener(type=honey)"), honey.items());
        assertSame(honey, GlazeConfig.glazeFor(honeyFood()));
        assertNull(GlazeConfig.glazeFor(null)); assertNull(GlazeConfig.glazeFor(roast()));
        assertTrue(GlazeConfig.isRoast(roast())); assertFalse(GlazeConfig.isRoast(null)); assertFalse(GlazeConfig.isRoast(honeyFood()));
        assertNull(GlazeConfig.glazeOn(null)); assertNull(GlazeConfig.glazeOn(roast()));
        assertEquals(List.of("honey_glazed"), GlazeConfig.trackIds());
        assertNotNull(TrackLoader.getByString("honey_glazed"), "tags.yml has the track");
    }

    @Test void glazesMayListSeveralItems_andOnesWithoutAnItemOrTrackAreSkipped() throws Exception {
        Path file = dir.resolve("glazes.yml");
        Files.writeString(file, """
                roasts: [c.meat]
                glazes:
                  scalar: ignored
                  syrup:
                    items: [c.sweetener(type=maple_syrup), c.sweetener(origin=Honey)]
                    track: maple_glazed
                  trackless:
                    item: c.sweetener
                  itemless:
                    track: honey_glazed
                """);
        new GlazeConfig().load(file.toFile());
        assertEquals(List.of("syrup"), GlazeConfig.glazes().stream().map(GlazeConfig.Glaze::id).toList());
        assertEquals("syrup", GlazeConfig.glazeFor(honeyFood()).id());
        assertTrue(GlazeConfig.isRoast(env.food("sausage_chain", "Pork", 3)), "any meat is a roast here");
        new GlazeConfig().load(dir.resolve("missing.yml").toFile());
        assertTrue(GlazeConfig.glazes().isEmpty());
        assertFalse(GlazeConfig.isRoast(roast()));
    }

    @Test void honeyGlazesTheRoastOnAMeatHook_andItsEmptyBottleComesBack() {
        onHook(hook, roast());
        FurnitureInteractEvent event = click(hook, honey(2));
        assertTrue(event.isCancelled());
        FoodItem glazed = content(hook);
        assertEquals("honey_glazed", glazed.getTagTrack("honey_glazed").getCurrentStep().getId());
        assertSame(GlazeConfig.glazes().get(0), GlazeConfig.glazeOn(glazed));
        assertEquals(Material.HONEY_BOTTLE, hand().getType()); assertEquals(1, hand().getAmount());
        assertTrue(env.player.getInventory().contains(Material.GLASS_BOTTLE), "the empty bottle comes back");
        verify(env.manager).markDirty(hook.furniture);
        verify(env.world).playSound(any(Location.class), eq(Sound.BLOCK_HONEY_BLOCK_PLACE), anyFloat(), anyFloat());

        click(hook, hand());
        assertEquals("§cThat roast is already glazed.", env.messages.getLast());
        assertEquals(1, hand().getAmount(), "nothing used");
    }

    @Test void theLastHoneyGlazingAFirePitRoastLeavesTheBottleInHand() {
        FoodItem roast = roast();
        track(roast, "cooked", 0);
        onHook(pit, roast);
        click(pit, honey(1));
        assertNotNull(content(pit).getTagTrack("honey_glazed"));
        assertNotNull(content(pit).getTagTrack("cooked"), "its cooking carries on");
        assertEquals(Material.GLASS_BOTTLE, hand().getType());
    }

    @Test void honeyIsRefusedWithNoRoast_aSausageChain_aRottenRoast_orAGlazeWithoutItsTrack() throws Exception {
        click(hook, honey(1));
        assertEquals("§cThere's no roast here to glaze.", env.messages.getLast());
        hook.place("content", null);
        click(hook, honey(1));
        assertEquals("§cThere's no roast here to glaze.", env.messages.getLast());

        onHook(hook, env.food("sausage_chain", "Pork", 3));
        click(hook, honey(1));
        assertEquals("§cOnly roasts can be glazed.", env.messages.getLast());

        FoodItem rotten = roast();
        track(rotten, "freshness", 200000);
        onHook(hook, rotten);
        click(hook, honey(1));
        assertEquals("§cThat roast has gone off.", env.messages.getLast());
        assertEquals(1, hand().getAmount(), "the honey is kept");

        Path file = dir.resolve("glazes.yml");
        Files.writeString(file, "roasts: [c.meat(type=roast)]\nglazes:\n  honey:\n    item: c.sweetener(type=honey)\n    track: nope\n");
        new GlazeConfig().load(file.toFile());
        onHook(hook, roast());
        click(hook, honey(1));
        assertEquals("§cThat glaze's track 'nope' isn't in tags.yml.", env.messages.getLast());
        assertEquals(1, hand().getAmount());
    }

    @Test void aRoastThatCanNoLongerBeBuiltKeepsTheHoney() {
        updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(null);
        onHook(hook, roast());
        ItemStack before = hook.active.get("content").getCurrentItem();
        click(hook, honey(1));
        assertSame(before, hook.active.get("content").getCurrentItem(), "the roast on the hook is unchanged");
        assertEquals(Material.HONEY_BOTTLE, hand().getType());
        updater.when(() -> ItemUpdater.applyItemUpdate(any(), any(), any())).thenReturn(new ItemStack(Material.AIR));
        click(hook, honey(1));
        assertEquals(Material.HONEY_BOTTLE, hand().getType());
    }

    @Test void aGlazedRoastWithoutAModelStillGetsItsGlaze() {
        FoodItem roast = roast();
        roast.setModel(null);
        onHook(hook, roast);
        click(hook, honey(1));
        assertNotNull(content(hook).getTagTrack("honey_glazed"));
    }

    @Test void otherFurniture_anEmptyHand_andOtherFoodsAreLeftAlone() {
        Station juicer = env.station("juicer"); juicer.define("content");
        onHook(juicer, roast());
        assertFalse(click(juicer, honey(1)).isCancelled());
        onHook(hook, roast());
        assertFalse(click(hook, null).isCancelled());
        FoodItem salt = env.food("seasoning_1", "Salt", 3); salt.setCategory("salt");
        assertFalse(click(hook, env.stack(salt, Material.SUGAR, 1)).isCancelled());
        assertFalse(click(hook, new ItemStack(Material.STONE)).isCancelled());
        assertNull(content(hook).getTagTrack("honey_glazed"));
        assertTrue(env.messages.isEmpty());
    }

    @Test void carvingPassesTheGlazeOnToEveryCut() {
        FoodItem roast = roast();
        track(roast, "honey_glazed", 0);
        FoodItem cut = env.food("meat_red_meat", "Beef", 1);
        CarvableRoastUtils.copyInheritedTracks(roast, cut);
        assertNotNull(cut.getTagTrack("honey_glazed"));
        FoodItem plain = env.food("meat_red_meat", "Beef", 1);
        CarvableRoastUtils.copyInheritedTracks(roast(), plain);
        assertNull(plain.getTagTrack("honey_glazed"));
    }
}
