package net.tfminecraft.cooking.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class HusbandryOwnedMountTest {
    @Test
    void tamedNamedSaddledHorseWithoutCookingOwnerIsRemoved() {
        Horse horse = keptLookingHorse();
        checkLoad(horse, false, false, false, false);
    }

    @Test
    void tamedNamedSaddledHorseWithoutOwnerButWithStatsRowIsStillEnrolledWild() {
        checkLoad(keptLookingHorse(), true, true, false, true);
    }

    @Test
    void cookingOwnedHorseIsKept() {
        checkLoad(keptLookingHorse(), true, true, true, true);
    }

    @Test
    void cookingOwnedHorseWithoutMountStatsIsKept() {
        checkLoad(keptLookingHorse(), true, false, true, true);
    }

    @Test
    void wildHorseWithoutRecordIsRemoved() {
        checkLoad(horse(), false, false, false, false);
    }

    @Test
    void hintExplainsHowToClaimAndTheLimit() {
        List<String> lines = HusbandryClaimHint.lines("Horse", true, true, 3, 15, false);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("This horse is not claimed"));
        assertTrue(lines.get(1).contains("Tame it, then right-click it with an Ownership Token"));
        assertTrue(lines.get(2).contains("3/15"));
    }

    @Test
    void hintAtCapSaysItCannotBeClaimed() {
        List<String> lines = HusbandryClaimHint.lines("Donkey", true, false, 15, 15, false);
        assertTrue(lines.get(1).contains("15/15 animals, so you cannot claim it"));
        assertTrue(lines.get(2).contains("shift-right-click it with an empty hand, then click Remove ownership"));
        assertTrue(lines.get(2).contains("/animals"));
    }

    @Test
    void unclaimableAnimalsAreNotSentToTheToken() {
        List<String> lines = HusbandryClaimHint.lines("Wolf", false, false, 0, 15, false);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("the next time their area loads"));
        assertTrue(lines.get(1).contains("cannot be claimed"));
        assertFalse(String.join(" ", lines).contains("Ownership Token"));
    }

    @Test
    void onlyAnimalsTheCleanupWouldRemoveNeedAClaim() {
        Horse enrolled = horse();
        Llama llama = mock(Llama.class);
        Horse owned = horse();
        Cow cow = mock(Cow.class);
        UUID enrolledId = UUID.randomUUID();
        UUID llamaId = UUID.randomUUID();
        UUID ownedId = UUID.randomUUID();
        when(enrolled.getUniqueId()).thenReturn(enrolledId);
        when(enrolled.getType()).thenReturn(EntityType.HORSE);
        when(llama.getUniqueId()).thenReturn(llamaId);
        when(llama.getType()).thenReturn(EntityType.LLAMA);
        when(owned.getUniqueId()).thenReturn(ownedId);
        when(owned.getType()).thenReturn(EntityType.HORSE);
        when(cow.getType()).thenReturn(EntityType.COW);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.exists(enrolledId)).thenReturn(true);
        try (MockedStatic<HusbandryEntities> entities = mockStatic(HusbandryEntities.class);
             MockedStatic<HusbandryConfig> config = mockStatic(HusbandryConfig.class);
             MockedStatic<HusbandryOwnershipService> ownership = mockStatic(HusbandryOwnershipService.class)) {
            entities.when(HusbandryEntities::repository).thenReturn(repository);
            entities.when(() -> HusbandryEntities.getLoaded(any())).thenReturn(Optional.empty());
            config.when(() -> HusbandryConfig.isRemoveUnowned(EntityType.HORSE)).thenReturn(true);
            config.when(() -> HusbandryConfig.isRemoveUnowned(EntityType.LLAMA)).thenReturn(true);
            config.when(() -> HusbandryConfig.mountStats(EntityType.HORSE))
                    .thenReturn(mock(HusbandryMountStats.class));
            ownership.when(() -> HusbandryOwnershipService.hasAnyOwner(ownedId)).thenReturn(true);

            // Riding enrolls a horse, and an enrolled horse survives the cleanup without an owner.
            assertFalse(HusbandryClaimHint.needsClaim(enrolled));
            // Llamas have no mount stats, so they are never enrolled and are removed.
            assertTrue(HusbandryClaimHint.needsClaim(llama));
            assertFalse(HusbandryClaimHint.needsClaim(owned));
            assertFalse(HusbandryClaimHint.needsClaim(cow));
            assertFalse(HusbandryClaimHint.needsClaim(null));
        }
    }

    @Test
    void tamingHintDoesNotSayToTameAnAnimalThatWasJustTamed() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        Llama llama = mock(Llama.class);
        UUID llamaId = UUID.randomUUID();
        when(llama.getUniqueId()).thenReturn(llamaId);
        when(llama.getType()).thenReturn(EntityType.LLAMA);
        // Paper fires EntityTameEvent before taming, so the entity still reports untamed.
        when(llama.isTamed()).thenReturn(false);
        EntityTameEvent event = mock(EntityTameEvent.class);
        when(event.getOwner()).thenReturn(player);
        when(event.getEntity()).thenReturn(llama);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.countForPlayer(playerId)).thenReturn(2);
        try (MockedStatic<HusbandryEntities> entities = mockStatic(HusbandryEntities.class);
             MockedStatic<HusbandryConfig> config = mockStatic(HusbandryConfig.class);
             MockedStatic<HusbandryOwnershipService> ownership = mockStatic(HusbandryOwnershipService.class)) {
            entities.when(HusbandryEntities::repository).thenReturn(repository);
            entities.when(() -> HusbandryEntities.getLoaded(llamaId)).thenReturn(Optional.empty());
            entities.when(() -> HusbandryEntities.displayName(EntityType.LLAMA)).thenReturn("Llama");
            config.when(() -> HusbandryConfig.isRemoveUnowned(EntityType.LLAMA)).thenReturn(true);
            config.when(() -> HusbandryConfig.species(EntityType.LLAMA)).thenReturn(mock(HusbandrySpecies.class));
            config.when(HusbandryConfig::maxAnimals).thenReturn(15);

            new HusbandryMountListener().onTame(event);

            ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(player, times(3)).sendMessage(sent.capture());
            assertTrue(sent.getAllValues().get(1).startsWith("§7Right-click it with an Ownership Token"));
            assertTrue(sent.getAllValues().get(2).contains("2/15"));
        }
    }

    @Test
    void tamedHintSkipsTheTamingStep() {
        List<String> lines = HusbandryClaimHint.lines("Horse", true, false, 0, 15, false);
        assertTrue(lines.get(1).startsWith("§7Right-click it with an Ownership Token"));
    }

    @Test
    void staffHintHasNoLimit() {
        List<String> lines = HusbandryClaimHint.lines("Horse", true, false, 40, 15, true);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("no animal limit"));
    }

    private static Horse keptLookingHorse() {
        Horse horse = horse();
        when(horse.isTamed()).thenReturn(true);
        when(horse.customName()).thenReturn(Component.text("Hazel's horse"));
        ItemStack saddle = mock(ItemStack.class);
        when(saddle.getType()).thenReturn(Material.SADDLE);
        when(horse.getInventory().getSaddle()).thenReturn(saddle);
        return horse;
    }

    private static Horse horse() {
        Horse horse = mock(Horse.class);
        when(horse.getInventory()).thenReturn(mock(HorseInventory.class));
        return horse;
    }

    private static void checkLoad(
            LivingEntity entity, boolean hasRow, boolean configuredMount, boolean owned, boolean kept) {
        UUID uuid = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(uuid);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.exists(uuid)).thenReturn(hasRow);
        try (MockedStatic<HusbandryEntities> entities = mockStatic(HusbandryEntities.class);
             MockedStatic<HusbandryConfig> config = mockStatic(HusbandryConfig.class);
             MockedStatic<HusbandryOwnershipService> ownership = mockStatic(HusbandryOwnershipService.class)) {
            entities.when(HusbandryEntities::repository).thenReturn(repository);
            config.when(() -> HusbandryConfig.isRemoveUnowned(entity.getType())).thenReturn(true);
            ownership.when(() -> HusbandryOwnershipService.hasAnyOwner(uuid)).thenReturn(owned);
            if (configuredMount) {
                config.when(() -> HusbandryConfig.mountStats(entity.getType()))
                        .thenReturn(mock(HusbandryMountStats.class));
            }

            HusbandryLifecycleListener.handleLoad(entity);

            verify(entity, kept ? never() : times(1)).remove();
            verify(repository, !kept && hasRow ? times(1) : never()).deleteAnimal(uuid);
        }
    }
}
