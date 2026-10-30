package net.tfminecraft.cooking.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Chunk;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;

/** Makes a mocked InteractibleFurniture answer its chunk lookup from the placed furniture the test stubs. */
public final class FurnitureChunkLookup {

    private FurnitureChunkLookup() {
    }

    /** Every placed piece, carried ones included, whose location is in the chunk, as the real lookup finds them. */
    public static void answer(FurnitureManager manager) {
        when(manager.getFurnitureForSave(any())).thenAnswer(call -> {
            Chunk chunk = call.getArgument(0);
            Set<Furniture> found = new LinkedHashSet<>();
            for (Furniture furniture : manager.getPlacedFurniture().values()) {
                if (furniture.getLoc().getChunk().equals(chunk)) {
                    found.add(furniture);
                }
            }
            return found;
        });
    }
}
