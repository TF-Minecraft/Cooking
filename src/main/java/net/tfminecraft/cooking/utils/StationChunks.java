package net.tfminecraft.cooking.utils;

import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.Location;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

/** Chunk checks for cached stations that never load a chunk to answer. */
public final class StationChunks {

    private StationChunks() {
    }

    /** True when the furniture sits in this chunk. A carried piece counts where it is now. */
    public static boolean isIn(Furniture furniture, Chunk chunk) {
        if (furniture == null || chunk == null) return false;
        Location loc = furniture.getLoc();
        return isIn(loc, chunk);
    }

    /**
     * The live furniture in this chunk, carried pieces included where they were picked up: the set
     * InteractibleFurniture saves for it. Its chunk index answers without walking every piece or
     * looking up (and possibly loading) each piece's chunk. A fresh set, safe to change.
     */
    public static Set<Furniture> furnitureIn(Chunk chunk) {
        return InteractibleFurniture.getInstance().getFurnitureManager().getFurnitureForSave(chunk);
    }

    static boolean isIn(Location loc, Chunk chunk) {
        if (loc == null || loc.getWorld() == null || chunk.getWorld() == null) return false;
        return loc.getWorld().getUID().equals(chunk.getWorld().getUID())
                && (loc.getBlockX() >> 4) == chunk.getX()
                && (loc.getBlockZ() >> 4) == chunk.getZ();
    }
}
