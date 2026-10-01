package net.tfminecraft.cooking.crops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import net.momirealms.customcrops.api.core.mechanic.crop.CropConfig;
import net.momirealms.customcrops.api.event.CropPlantEvent;

class CropPlantingTest {
    private World world;
    private Location location;

    @BeforeEach
    void setup() {
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
    @EnumSource(value = Material.class, names = {"STONE", "OAK_PLANKS", "GLASS", "OAK_SLAB", "OAK_LEAVES"})
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
        roof(Material.GLASS, 319);
        assertFalse(CropPlantingRule.hasOpenSky(location));
    }

    @Test
    void customCropsCancelUnderGlassButYeastIsExempt() {
        roof(Material.GLASS, 66);
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

    private void roof(Material material, int y) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(world.getBlockAt(4, y, 8)).thenReturn(block);
    }
}
