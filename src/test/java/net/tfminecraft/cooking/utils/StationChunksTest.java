package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

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
}
