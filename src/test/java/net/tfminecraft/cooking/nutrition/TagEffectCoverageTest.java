package net.tfminecraft.cooking.nutrition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagEffect;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.quality.QualityConfig;

/**
 * Tag steps can make the eater ill (tags.yml effects): the repo's poison gives nausea, then poison, then weakness, and a
 * hidden poison strikes just the same. Bad effect lines are skipped with a warning.
 */
class TagEffectCoverageTest {
    @TempDir Path directory;
    ServerMock server;
    PlayerMock player;
    List<TagTrack> tracks;
    Cooking previousPlugin;

    @BeforeEach void open() {
        server = MockBukkit.mock();
        previousPlugin = Cooking.plugin;
        Cooking.plugin = mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking");
        when(Cooking.plugin.namespace()).thenReturn("cooking");
        player = server.addPlayer();
        tracks = new ArrayList<>(TrackLoader.oList);
        new TrackLoader().load(new File("src/main/resources/tags.yml"));
        QualityConfig.apply(1, 5, null, null);
        NutritionConfig.load(new YamlConfiguration());
    }

    @AfterEach void close() {
        NutritionLog.configure(false, false, null);
        TrackLoader.oList.clear();
        TrackLoader.oList.addAll(tracks);
        Cooking.plugin = previousPlugin;
        MockBukkit.unmock();
    }

    static TagTrack at(String trackId, String stepId) {
        TagTrack track = new TagTrack(TrackLoader.getByString(trackId));
        for (TagStep step : track.getSteps()) {
            if (step.getId().equals(stepId)) track.forceSetValue((int) step.getRequiredValue());
        }
        return track;
    }

    static FoodItem dish(TagTrack... tracks) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("name", "sandwich");
        config.set("update", false);
        FoodItem food = new FoodItem("sandwich", config);
        food.setCategory("sandwich");
        for (TagTrack track : tracks) food.addOrModifyTrack(track);
        return food;
    }

    static String describe(List<TagEffect> effects) {
        List<String> out = new ArrayList<>();
        for (TagEffect e : effects) out.add(e.type().getKey().getKey() + " " + e.seconds() + " " + e.level());
        return String.join(", ", out);
    }

    @Test void theRepoPoisonGetsWorseWithEachStep() {
        assertEquals("nausea 10 1", describe(at("poison", "tainted").getCurrentStep().getEffects()));
        assertEquals("nausea 15 1, poison 10 1", describe(at("poison", "poisoned").getCurrentStep().getEffects()));
        assertEquals("nausea 20 1, poison 15 2, weakness 60 1",
                describe(at("poison", "deadly").getCurrentStep().getEffects()));
        assertTrue(at("freshness", "fresh").getCurrentStep().getEffects().isEmpty());
    }

    @Test void effectLinesNameAnEffectSecondsAndAnOptionalLevel() {
        TagEffect poison = TagEffect.parse("  Poison 10 2 ", "here");
        assertEquals(PotionEffectType.POISON, poison.type());
        PotionEffect potion = poison.toPotionEffect();
        assertEquals(200, potion.getDuration());
        assertEquals(1, potion.getAmplifier());
        assertEquals(1, TagEffect.parse("minecraft:nausea 5", "here").level());
        for (String bad : new String[] {null, "", "poison", "poison 1 1 1", "not_an_effect 5", "bad$key 5",
                "poison ten", "poison 0", "poison 5 0"}) {
            assertNull(TagEffect.parse(bad, "here"), String.valueOf(bad));
        }
    }

    @Test void aStepKeepsOnlyTheEffectsItCanRead() throws Exception {
        Path file = directory.resolve("tags.yml");
        Files.writeString(file, """
                sick:
                  sick:
                    name: Sick
                    value: 0
                    effects:
                      - "hunger 30"
                      - "wobbly 30"
                """);
        TagStep step = new TagStep("sick", YamlConfiguration.loadConfiguration(file.toFile()).getConfigurationSection("sick.sick"));
        assertEquals("hunger 30 1", describe(step.getEffects()));
        assertTrue(new TagStep("plain", "Plain", 0, 1, 1).getEffects().isEmpty());
    }

    @Test void eatingAHiddenPoisonStillMakesYouIll() {
        TagTrack poison = at("poison", "deadly");
        poison.setHidden(true);
        FoodItem sandwich = dish(at("freshness", "fresh"), poison);
        assertEquals("nausea 20 1, poison 15 2, weakness 60 1", describe(sandwich.getEatEffects()));

        // No character to feed: the food is still eaten, so it still makes the player ill.
        NutritionService.tryApplyEat(player, sandwich);
        assertEquals(1, player.getPotionEffect(PotionEffectType.POISON).getAmplifier());
        assertEquals(300, player.getPotionEffect(PotionEffectType.POISON).getDuration());
        assertNotNull(player.getPotionEffect(PotionEffectType.NAUSEA));
        assertEquals(1200, player.getPotionEffect(PotionEffectType.WEAKNESS).getDuration());
    }

    @Test void foodWithoutEffectsLeavesThePlayerAlone() throws Exception {
        NutritionLog.configure(true, true, directory.toFile());
        NutritionService.giveEatEffects(player, dish(at("freshness", "fresh")));
        assertTrue(player.getActivePotionEffects().isEmpty());
        NutritionService.giveEatEffects(player, dish(at("poison", "tainted")));
        assertNotNull(player.getPotionEffect(PotionEffectType.NAUSEA));
        String log = Files.readString(directory.resolve("logs").resolve("nutrition.log"));
        assertTrue(log.contains("EAT_EFFECTS") && log.contains("effects=nausea:10s:1"), log);
    }
}
