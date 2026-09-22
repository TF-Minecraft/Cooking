package net.tfminecraft.cooking.heat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.oven.OvenState;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class HeatSources {
    private static Map<String, HeatSourceDefinition> sources = Map.of();
    private static Map<String, HeatConsumerDefinition> consumers = Map.of();

    private HeatSources() {}

    static void load(Map<String, HeatSourceDefinition> sourceMap, Map<String, HeatConsumerDefinition> consumerMap) {
        sources = new HashMap<>(sourceMap);
        consumers = new HashMap<>(consumerMap);
    }

    public static boolean isSource(Furniture furniture) {
        if (furniture == null) {
            return false;
        }
        return sources.containsKey(furniture.getId().toLowerCase());
    }

    public static boolean isConsumer(Furniture furniture) {
        if (furniture == null) {
            return false;
        }
        return consumers.containsKey(furniture.getId().toLowerCase());
    }

    public static boolean hasHeat(Furniture source) {
        if (source == null) {
            return false;
        }
        HeatSourceDefinition definition = sources.get(source.getId().toLowerCase());
        if (definition == null) {
            return false;
        }
        return hasHeatForType(definition.getType(), source);
    }

    public static Optional<Furniture> findSource(Furniture consumer) {
        if (consumer == null) {
            return Optional.empty();
        }
        HeatConsumerDefinition definition = consumers.get(consumer.getId().toLowerCase());
        if (definition == null) {
            return Optional.empty();
        }
        return findSourceByLookup(consumer, definition);
    }

    public static boolean consumerHasHeat(Furniture consumer) {
        return findSource(consumer).map(HeatSources::hasHeat).orElse(false);
    }

    public static boolean stationHasHeat(Furniture furniture) {
        if (furniture == null) {
            return false;
        }
        if (isConsumer(furniture)) {
            return consumerHasHeat(furniture);
        }
        if (isSource(furniture)) {
            return hasHeat(furniture);
        }
        return false;
    }

    public static boolean hasBlockingConsumerAbove(Furniture source) {
        if (!isSource(source)) {
            return false;
        }

        Optional<Location> origin = source.getOriginBlockLocation();
        if (origin.isEmpty()) {
            return false;
        }

        Location above = origin.get().getBlock().getRelative(BlockFace.UP).getLocation();
        String sourceId = source.getId().toLowerCase();

        for (HeatConsumerDefinition definition : consumers.values()) {
            if (!definition.getSourceFurnitureId().equalsIgnoreCase(sourceId)) {
                continue;
            }
            if (definition.getLookup() != HeatLookup.BLOCK_BELOW) {
                continue;
            }
            if (findFurnitureAt(definition.getFurnitureId(), above).isPresent()) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasHeatForType(HeatSourceType type, Furniture source) {
        return switch (type) {
            case OVEN -> OvenState.hasHeat(source);
            case CAMPFIRE -> isLitCampfire(source);
        };
    }

    private static boolean isLitCampfire(Furniture source) {
        Optional<Location> origin = source.getOriginBlockLocation();
        if (origin.isEmpty()) {
            return false;
        }
        Block block = origin.get().getBlock();
        Material type = block.getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return false;
        }
        return block.getBlockData() instanceof Campfire campfire && campfire.isLit();
    }

    private static Optional<Furniture> findSourceByLookup(Furniture consumer, HeatConsumerDefinition definition) {
        return switch (definition.getLookup()) {
            case BLOCK_BELOW -> findSourceBlockBelow(consumer, definition.getSourceFurnitureId());
        };
    }

    private static Optional<Furniture> findSourceBlockBelow(Furniture consumer, String sourceFurnitureId) {
        Optional<Location> origin = consumer.getOriginBlockLocation();
        if (origin.isEmpty()) {
            return Optional.empty();
        }

        Location target = origin.get().getBlock().getRelative(BlockFace.DOWN).getLocation();
        Optional<Furniture> source = findFurnitureAt(sourceFurnitureId, target);
        if (source.isEmpty() || !sources.containsKey(source.get().getId().toLowerCase())) {
            return Optional.empty();
        }
        return source;
    }

    private static Optional<Furniture> findFurnitureAt(String furnitureId, Location block) {
        if (furnitureId == null || block == null) {
            return Optional.empty();
        }

        String expectedId = furnitureId.toLowerCase();
        for (Furniture furniture : InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture()
                .values()) {
            if (furniture.isAttached()) {
                continue;
            }
            if (!furniture.getId().equalsIgnoreCase(expectedId)) {
                continue;
            }
            Optional<Location> furnitureOrigin = furniture.getOriginBlockLocation();
            if (furnitureOrigin.isEmpty()) {
                continue;
            }
            if (sameBlock(furnitureOrigin.get(), block)) {
                return Optional.of(furniture);
            }
        }

        return Optional.empty();
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
