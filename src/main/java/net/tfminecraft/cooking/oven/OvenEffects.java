package net.tfminecraft.cooking.oven;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;

public final class OvenEffects {
    private static final float AMBIENT_SOUND_CHANCE = 0.35f;
    private static final float AMBIENT_PARTICLE_CHANCE = 0.5f;

    private OvenEffects() {}

    public static Location fireLocation(Furniture furniture) {
        return furniture.getLoc().clone().add(0, 0.2, 0);
    }

    public static void playIgnite(Furniture furniture) {
        Location loc = fireLocation(furniture);
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(loc, Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f);
        world.spawnParticle(Particle.FLAME, loc, 8, 0.12, 0.08, 0.12, 0.02);
    }

    public static void playAmbience(Furniture furniture, Random random) {
        if (!OvenState.hasHeat(furniture)) {
            return;
        }

        Location loc = fireLocation(furniture);
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        if (random.nextFloat() < AMBIENT_SOUND_CHANCE) {
            float pitch = 0.9f + random.nextFloat() * 0.2f;
            if (random.nextBoolean()) {
                world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 1.0f, pitch);
            } else {
                world.playSound(loc, "block.furnace.fire_crackle", 1.0f, pitch);
            }
        }

        if (random.nextFloat() < AMBIENT_PARTICLE_CHANCE) {
            world.spawnParticle(Particle.FLAME, loc, 3, 0.08, 0.05, 0.08, 0.01);
            if (random.nextFloat() < 0.25f) {
                world.spawnParticle(Particle.SMOKE, loc, 1, 0.05, 0.04, 0.05, 0.005);
            }
        }
    }
}
