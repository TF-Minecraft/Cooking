package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;

class StationChunksTest {
	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void findsFurnitureInTheUnloadingChunkOnly() {
		World world = server.addSimpleWorld("world");
		World other = server.addSimpleWorld("other");
		Chunk chunk = world.getChunkAt(1, -2);

		assertTrue(StationChunks.isIn(new Location(world, 16, 64, -32), chunk));
		assertTrue(StationChunks.isIn(new Location(world, 31.9, 64, -17.5), chunk));
		assertFalse(StationChunks.isIn(new Location(world, 32, 64, -32), chunk));
		assertFalse(StationChunks.isIn(new Location(world, 16, 64, -33), chunk));
		assertFalse(StationChunks.isIn(new Location(other, 16, 64, -32), chunk));
		assertFalse(StationChunks.isIn((Location) null, chunk));
	}

	@Test
	void furnitureInAChunkComesFromInteractibleFurnituresLookupWithCarriedPieces() {
		Chunk chunk = server.addSimpleWorld("world").getChunkAt(0, 0);
		FurnitureManager manager = mock(FurnitureManager.class);
		InteractibleFurniture plugin = mock(InteractibleFurniture.class);
		when(plugin.getFurnitureManager()).thenReturn(manager);
		Set<Furniture> found = new HashSet<>(Set.of(mock(Furniture.class)));
		when(manager.getFurnitureForSave(chunk)).thenReturn(found);
		try (MockedStatic<InteractibleFurniture> instance = mockStatic(InteractibleFurniture.class)) {
			instance.when(InteractibleFurniture::getInstance).thenReturn(plugin);

			assertSame(found, StationChunks.furnitureIn(chunk));
		}
		verify(manager).getFurnitureForSave(chunk);
	}
}
