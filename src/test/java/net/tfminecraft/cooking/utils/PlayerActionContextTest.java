package net.tfminecraft.cooking.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import net.tfminecraft.cooking.testsupport.TestCharacters;
import net.tfminecraft.tlibs.character.CharacterSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PlayerActionContextTest {
    ServerMock server;
    PlayerMock player;
    TestCharacters characters;

    @BeforeEach void setup() {
        server=MockBukkit.mock();
        player=server.addPlayer();
        characters=new TestCharacters(MockBukkit.createMockPlugin());
    }

    @AfterEach void cleanup() {
        characters.close();
        MockBukkit.unmock();
    }

    @Test void nullAndPendingPlayersHaveNoActionContext() {
        assertNull(PlayerActionContext.capture(null));
        assertNull(PlayerActionContext.capture(player));
        characters.activate(player,UUID.randomUUID());
        PlayerActionContext ready=PlayerActionContext.capture(player);
        assertNotNull(ready);
        characters.set(player,null);
        assertFalse(ready.isCurrent());
        assertNull(PlayerActionContext.capture(player));
    }

    @Test void readyContextCapturesExactTokenAndRejectsSwitchBackGeneration() {
        UUID characterId=UUID.randomUUID();
        CharacterSession original=characters.activate(player,characterId);
        PlayerActionContext action=PlayerActionContext.capture(player);
        assertNotNull(action);
        assertSame(player,action.player());
        assertEquals(original,action.session());
        assertTrue(action.isCurrent());

        characters.activate(player,UUID.randomUUID());
        assertFalse(action.isCurrent());
        CharacterSession returned=characters.activate(player,characterId);
        assertEquals(original.ref(),returned.ref());
        assertNotEquals(original,returned);
        assertFalse(action.isCurrent());
        PlayerActionContext fresh=PlayerActionContext.capture(player);
        assertNotNull(fresh);
        assertEquals(returned,fresh.session());
        assertTrue(fresh.isCurrent());
    }

    @Test void readyContextBecomesInvalidWhenProviderDisappears() {
        characters.activate(player,UUID.randomUUID());
        PlayerActionContext action=PlayerActionContext.capture(player);
        assertNotNull(action);
        characters.close();
        assertFalse(action.isCurrent());
        PlayerActionContext standalone=PlayerActionContext.capture(player);
        assertNotNull(standalone);
        assertNull(standalone.session());
        assertTrue(standalone.isCurrent());
    }

    @Test void standaloneContextRequiresProviderToRemainAbsent() {
        characters.close();
        PlayerActionContext action=PlayerActionContext.capture(player);
        assertNotNull(action);
        assertNull(action.session());
        assertTrue(action.isCurrent());
        characters.register();
        assertFalse(action.isCurrent());
        assertNull(PlayerActionContext.capture(player));
        characters.activate(player,UUID.randomUUID());
        assertFalse(action.isCurrent());
        assertNotNull(PlayerActionContext.capture(player));
    }

    @Test void staleTokenReturnedByProviderCannotStartAnAction() {
        CharacterSession token=characters.activate(player,UUID.randomUUID());
        when(characters.provider.isCurrent(token)).thenReturn(false);
        assertNull(PlayerActionContext.capture(player));
    }

    @Test void disconnectedPlayerCannotCaptureOrContinueAnAction() {
        characters.activate(player,UUID.randomUUID());
        PlayerActionContext action=PlayerActionContext.capture(player);
        assertNotNull(action);
        player.disconnect();
        assertFalse(action.isCurrent());
        assertNull(PlayerActionContext.capture(player));
    }

    @Test void replacementConnectionInvalidatesActionEvenIfProviderKeepsSameToken() {
        characters.activate(player,UUID.randomUUID());
        PlayerActionContext action=PlayerActionContext.capture(player);
        assertNotNull(action);
        PlayerMock replacement=new PlayerMock(server,player.getName(),player.getUniqueId());
        assertTrue(replacement.isOnline());
        assertNull(PlayerActionContext.capture(replacement));

        server.getPlayerList().disconnectPlayer(player);
        server.addPlayer(replacement);
        assertTrue(player.isOnline());
        assertSame(replacement,server.getPlayer(player.getUniqueId()));
        assertFalse(action.isCurrent());
        assertNull(PlayerActionContext.capture(player));
        assertNotNull(PlayerActionContext.capture(replacement));
    }

    @Test void standaloneActionAlsoRequiresOriginalConnection() {
        characters.close();
        PlayerActionContext action=PlayerActionContext.capture(player);
        assertNotNull(action);
        server.getPlayerList().disconnectPlayer(player);
        PlayerMock replacement=new PlayerMock(server,player.getName(),player.getUniqueId());
        server.addPlayer(replacement);
        assertTrue(player.isOnline());
        assertSame(replacement,server.getPlayer(player.getUniqueId()));
        assertFalse(action.isCurrent());
        assertNull(PlayerActionContext.capture(player));
        PlayerActionContext fresh=PlayerActionContext.capture(replacement);
        assertNotNull(fresh);
        assertNull(fresh.session());
        assertTrue(fresh.isCurrent());
    }
}
