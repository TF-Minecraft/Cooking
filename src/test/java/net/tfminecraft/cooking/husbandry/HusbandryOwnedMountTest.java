package net.tfminecraft.cooking.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
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
        List<String> lines = HusbandryClaimHint.lines("Horse", true, 3, 15, false);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("This horse is not claimed"));
        assertTrue(lines.get(1).contains("Tame it, then right-click it with an Ownership Token"));
        assertTrue(lines.get(2).contains("3/15"));
    }

    @Test
    void hintAtCapSaysItCannotBeClaimed() {
        List<String> lines = HusbandryClaimHint.lines("Donkey", false, 15, 15, false);
        assertTrue(lines.get(1).contains("15/15 animals, so you cannot claim it"));
        assertTrue(lines.get(2).contains("/animals"));
    }

    @Test
    void tamedHintSkipsTheTamingStep() {
        List<String> lines = HusbandryClaimHint.lines("Horse", false, 0, 15, false);
        assertTrue(lines.get(1).startsWith("§7Right-click it with an Ownership Token"));
    }

    @Test
    void staffHintHasNoLimit() {
        List<String> lines = HusbandryClaimHint.lines("Horse", false, 40, 15, true);
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
