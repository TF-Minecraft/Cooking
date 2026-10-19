package net.tfminecraft.cooking.crops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import net.momirealms.customcrops.api.core.mechanic.crop.CropConfig;
import net.momirealms.customcrops.api.event.CropPlantEvent;

class CropPlantingTest {
    private World world;
    private Location location;

    @BeforeEach
    void setup() {
        CropPlantingRule.configure(null);
        world = mock(World.class);
        location = new Location(world, 4, 64, 8);
        when(world.getMaxHeight()).thenReturn(320);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
    }

    @Test
    void outdoorsWorksAtNightAndBelowSeaLevel() {
        assertTrue(CropPlantingRule.hasOpenSky(location));
        assertTrue(CropPlantingRule.hasOpenSky(new Location(world, 4, -20, 8)));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"STONE", "OAK_PLANKS", "OAK_SLAB", "OAK_LEAVES"})
    void caveAndHouseRoofsCancelVanillaPlanting(Material roof) {
        roof(roof, 90);
        Block crop = mock(Block.class);
        when(crop.getType()).thenReturn(Material.WHEAT);
        when(crop.getLocation()).thenReturn(location);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        when(event.getBlockPlaced()).thenReturn(crop);
        when(event.getPlayer()).thenReturn(player);
        new CropPlantingListener().onPlant(event);
        verify(event).setCancelled(true);
        verify(player).sendMessage(CropPlantingListener.DENIAL_MESSAGE);
    }

    @Test
    void checksRoofAtTopOfWorldButIgnoresCropItself() {
        roof(Material.WHEAT, 64);
        assertTrue(CropPlantingRule.hasOpenSky(location));
        roof(Material.STONE, 319);
        assertFalse(CropPlantingRule.hasOpenSky(location));
    }

    @Test
    void customCropsCancelUnderStoneButYeastIsExempt() {
        roof(Material.STONE, 66);
        CropPlantEvent event = mock(CropPlantEvent.class);
        CropConfig config = mock(CropConfig.class);
        when(config.id()).thenReturn("tomato");
        when(event.cropConfig()).thenReturn(config);
        when(event.location()).thenReturn(location);
        when(event.getPlayer()).thenReturn(mock(Player.class));
        new CropCustomCropsListener().onPlant(event);
        verify(event).setCancelled(true);

        reset(event);
        when(config.id()).thenReturn("yeast");
        when(event.cropConfig()).thenReturn(config);
        new CropCustomCropsListener().onPlant(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void wartMushroomsAndBuildingBlocksAreExempt() {
        for (Material material : new Material[] {Material.NETHER_WART, Material.BROWN_MUSHROOM,
                Material.RED_MUSHROOM, Material.OAK_PLANKS}) {
            assertFalse(CropPlantingRule.requiresOpenSky(material));
        }
        assertTrue(CropPlantingRule.requiresOpenSky(Material.MELON_STEM));
        assertTrue(CropPlantingRule.requiresOpenSky(Material.PUMPKIN_STEM));
        assertFalse(CropPlantingRule.customRequiresOpenSky("yeast"));
        assertTrue(CropPlantingRule.customRequiresOpenSky("rice"));
    }

    private Block roof(Material material, int y) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(world.getBlockAt(4, y, 8)).thenReturn(block);
        return block;
    }

    @AfterEach
    void resetConfig() {
        CropPlantingRule.configure(null);
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"GLASS", "GLASS_PANE", "RED_STAINED_GLASS", "BLUE_STAINED_GLASS_PANE", "TINTED_GLASS"})
    void greenhousesAllowedUnlessDisabled(Material glass) {
        roof(glass, 70);
        assertTrue(CropPlantingRule.hasOpenSky(location));
        YamlConfiguration config = new YamlConfiguration();
        config.set("allow-glass-roofs", false);
        CropPlantingRule.configure(config);
        assertFalse(CropPlantingRule.hasOpenSky(location));
    }

    @Test
    void configurableCoverExemptionsAndDisable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("allowed-cover", java.util.List.of("OAK_LEAVES"));
        config.set("exempt-vanilla", java.util.List.of("WHEAT"));
        config.set("exempt-custom", java.util.List.of("Tomato"));
        CropPlantingRule.configure(config);
        roof(Material.OAK_LEAVES, 70);
        assertTrue(CropPlantingRule.hasOpenSky(location));
        assertFalse(CropPlantingRule.requiresOpenSky(Material.WHEAT));
        assertTrue(CropPlantingRule.requiresOpenSky(Material.NETHER_WART));
        assertFalse(CropPlantingRule.customRequiresOpenSky("tomato"));
        assertTrue(CropPlantingRule.customRequiresOpenSky("yeast"));
        config.set("require-open-sky", false);
        CropPlantingRule.configure(config);
        assertFalse(CropPlantingRule.requiresOpenSky(Material.CARROTS));
        assertFalse(CropPlantingRule.customRequiresOpenSky("rice"));
    }

    @Test
    void lowCoverCountsOnlyWithinItsHeight() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("low-cover.max-height", 5);
        config.set("low-cover.materials", List.of("OAK_SLAB", "OAK_STAIRS", "OAK_SIGN"));
        CropPlantingRule.configure(config);
        roof(Material.OAK_SIGN, 65);
        roof(Material.OAK_STAIRS, 67);
        roof(Material.OAK_SLAB, 69);
        assertTrue(CropPlantingRule.hasOpenSky(location));
        roof(Material.OAK_SLAB, 70);
        assertFalse(CropPlantingRule.hasOpenSky(location));
        roof(Material.AIR, 70);
        roof(Material.STONE, 90);
        assertFalse(CropPlantingRule.hasOpenSky(location));
    }

    @Test
    void doubleSlabsStayARoofAndMissingLowCoverChangesNothing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("low-cover.materials", List.of("OAK_SLAB"));
        CropPlantingRule.configure(config);
        Block slab = roof(Material.OAK_SLAB, 66);
        Slab data = mock(Slab.class);
        when(slab.getBlockData()).thenReturn(data);
        when(data.getType()).thenReturn(Slab.Type.TOP);
        assertTrue(CropPlantingRule.hasOpenSky(location), "max-height defaults to 5");
        when(data.getType()).thenReturn(Slab.Type.DOUBLE);
        assertFalse(CropPlantingRule.hasOpenSky(location));

        CropPlantingRule.configure(new YamlConfiguration());
        when(data.getType()).thenReturn(Slab.Type.BOTTOM);
        assertFalse(CropPlantingRule.hasOpenSky(location));
        config.set("low-cover.max-height", -3);
        CropPlantingRule.configure(config);
        assertFalse(CropPlantingRule.hasOpenSky(location));
    }

    @Test
    @SuppressWarnings("unchecked")
    void lowCoverReadsBlockTagsAndWarnsOnUnknownEntries() {
        Tag<Material> slabs = mock(Tag.class);
        when(slabs.getValues()).thenReturn(Set.of(Material.OAK_SLAB, Material.STONE_SLAB));
        Tag<Material> signs = mock(Tag.class);
        when(signs.getValues()).thenReturn(Set.of(Material.OAK_SIGN, Material.OAK_WALL_HANGING_SIGN));
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(logger);
            bukkit.when(() -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("slabs"), Material.class))
                    .thenReturn(slabs);
            bukkit.when(() -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("all_signs"), Material.class))
                    .thenReturn(signs);
            YamlConfiguration config = new YamlConfiguration();
            config.set("low-cover.materials", List.of("#SLABS", "#minecraft:all_signs", "#nope", "#", "#bad key", "DIAMOND"));
            CropPlantingRule.configure(config);
            verify(logger).warning("[Cooking] Invalid planting material in low-cover.materials: #nope");
            verify(logger).warning("[Cooking] Invalid planting material in low-cover.materials: #");
            verify(logger).warning("[Cooking] Invalid planting material in low-cover.materials: #bad key");
            verify(logger).warning("[Cooking] Invalid planting material in low-cover.materials: DIAMOND");
        }
        roof(Material.STONE_SLAB, 65);
        roof(Material.OAK_WALL_HANGING_SIGN, 66);
        assertTrue(CropPlantingRule.hasOpenSky(location));
        roof(Material.OAK_STAIRS, 67);
        assertFalse(CropPlantingRule.hasOpenSky(location));
    }
}
