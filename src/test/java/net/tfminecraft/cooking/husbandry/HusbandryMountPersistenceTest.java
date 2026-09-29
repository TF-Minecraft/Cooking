package net.tfminecraft.cooking.husbandry;

import static org.mockito.Mockito.*;

import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Cow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class HusbandryMountPersistenceTest {
    @Test
    void namedSaddledHorseWithoutCookingRecordSurvivesLoad() {
        Horse horse = horse();
        when(horse.customName()).thenReturn(Component.text("Hazel's horse"));
        ItemStack saddle = mock(ItemStack.class);
        when(saddle.getType()).thenReturn(Material.SADDLE);
        when(horse.getInventory().getSaddle()).thenReturn(saddle);
        checkLoad(horse, false, false, true);
    }

    @Test
    void vanillaTamedHorseWithoutCookingOwnerSurvivesLoad() {
        Horse horse = horse();
        when(horse.isTamed()).thenReturn(true);
        checkLoad(horse, false, false, true);
    }

    @Test
    void namedHorseAloneSurvivesLoad() {
        Horse horse = horse();
        when(horse.customName()).thenReturn(Component.text("Named"));
        checkLoad(horse, false, false, true);
    }

    @Test
    void saddledHorseAloneSurvivesLoad() {
        Horse horse = horse();
        ItemStack saddle = mock(ItemStack.class);
        when(saddle.getType()).thenReturn(Material.SADDLE);
        when(horse.getInventory().getSaddle()).thenReturn(saddle);
        checkLoad(horse, false, false, true);
    }

    @Test
    void wildHorseWithoutRecordIsStillRemoved() {
        checkLoad(horse(), false, false, false);
    }

    @Test
    void enrolledWildHorseIsStillKept() {
        checkLoad(horse(), true, true, true);
    }

    @Test
    void tamedLlamaKeepsItsRowWithoutConfiguredMountStats() {
        Llama llama = mock(Llama.class);
        when(llama.isTamed()).thenReturn(true);
        checkLoad(llama, true, false, true);
    }

    @Test
    void namedUnownedLivestockStillUsesExistingCleanup() {
        Cow cow = mock(Cow.class);
        when(cow.customName()).thenReturn(Component.text("Cow"));
        checkLoad(cow, true, false, false);
    }

    private static Horse horse() {
        Horse horse = mock(Horse.class);
        when(horse.getInventory()).thenReturn(mock(HorseInventory.class));
        return horse;
    }

    private static void checkLoad(LivingEntity entity, boolean hasRow, boolean configuredMount, boolean kept) {
        UUID uuid = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(uuid);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.exists(uuid)).thenReturn(hasRow);
        try (MockedStatic<HusbandryEntities> entities = mockStatic(HusbandryEntities.class);
             MockedStatic<HusbandryConfig> config = mockStatic(HusbandryConfig.class);
             MockedStatic<HusbandryOwnershipService> ownership = mockStatic(HusbandryOwnershipService.class)) {
            entities.when(HusbandryEntities::repository).thenReturn(repository);
            entities.when(() -> HusbandryEntities.applyPersistFlags(entity)).thenCallRealMethod();
            config.when(() -> HusbandryConfig.isRemoveUnowned(entity.getType())).thenReturn(true);
            if (configuredMount) {
                config.when(() -> HusbandryConfig.mountStats(entity.getType()))
                        .thenReturn(mock(HusbandryMountStats.class));
            }

            HusbandryLifecycleListener.handleLoad(entity);

            verify(entity, kept ? never() : times(1)).remove();
            verify(repository, !kept && hasRow ? times(1) : never()).deleteAnimal(uuid);
            if (kept) {
                verify(entity).setPersistent(true);
                verify(entity).setRemoveWhenFarAway(false);
            }
        }
    }
}
