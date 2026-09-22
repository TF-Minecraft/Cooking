package net.tfminecraft.cooking.milling;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class MillingStoneAnimation {
    private static final Set<UUID> animating = ConcurrentHashMap.newKeySet();

    private MillingStoneAnimation() {}

    public static boolean isAnimating(Furniture furniture) {
        if (furniture == null || furniture.getEntityId() == null) {
            return false;
        }
        return animating.contains(furniture.getEntityId());
    }

    public static void clearAnimating(Furniture furniture) {
        if (furniture != null && furniture.getEntityId() != null) {
            animating.remove(furniture.getEntityId());
        }
    }

    public static void playMill(Furniture furniture, int revolutions, int durationTicks, Runnable onComplete) {
        MillingStoneDisplay.showTop(furniture);
        ItemDisplay top = getTopDisplay(furniture);
        if (top == null || furniture.getEntityId() == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        UUID furnitureId = furniture.getEntityId();
        animating.add(furnitureId);

        final Transformation startTransform = top.getTransformation();
        final Vector3f startTranslation = new Vector3f(startTransform.getTranslation());
        final Quaternionf startRot = new Quaternionf(startTransform.getLeftRotation());
        final Vector3f scale = new Vector3f(startTransform.getScale());
        final Quaternionf rightRot = new Quaternionf(startTransform.getRightRotation());

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (top.isDead()) {
                    animating.remove(furnitureId);
                    cancel();
                    return;
                }

                if (tick > durationTicks) {
                    top.setTransformation(startTransform);
                    animating.remove(furnitureId);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    cancel();
                    return;
                }

                float angle = (tick / (float) durationTicks) * revolutions * (float) (2 * Math.PI);
                Quaternionf spin = new Quaternionf().rotateY(angle);
                Quaternionf newRot = spin.mul(startRot);
                top.setTransformation(new Transformation(
                        startTranslation,
                        newRot,
                        scale,
                        rightRot));

                tick++;
            }
        }.runTaskTimer(Cooking.plugin, 0L, 1L);
    }

    private static ItemDisplay getTopDisplay(Furniture furniture) {
        PlacedSlot slot = furniture.getActiveSlot(MillingStoneSlots.TOP).orElse(null);
        if (slot == null || slot.getDisplayStandId() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(slot.getDisplayStandId());
        return entity instanceof ItemDisplay display ? display : null;
    }
}
