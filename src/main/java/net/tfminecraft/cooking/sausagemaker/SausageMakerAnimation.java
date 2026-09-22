package net.tfminecraft.cooking.sausagemaker;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

public final class SausageMakerAnimation {
    private static final Set<UUID> animating = ConcurrentHashMap.newKeySet();

    private SausageMakerAnimation() {}

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

    public static void playWobble(Furniture furniture, Runnable onComplete) {
        ItemDisplay parent = getParentDisplay(furniture);
        if (parent == null || furniture.getEntityId() == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        UUID furnitureId = furniture.getEntityId();
        animating.add(furnitureId);

        final Transformation startTransform = parent.getTransformation();
        final Vector3f startTranslation = new Vector3f(startTransform.getTranslation());
        final Quaternionf startRot = new Quaternionf(startTransform.getLeftRotation());
        final int duration = ItemCache.sausageMakerDurationTicks;
        final float wobbleRadians = (float) Math.toRadians(ItemCache.sausageMakerWobbleDegrees);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (parent.isDead()) {
                    animating.remove(furnitureId);
                    cancel();
                    return;
                }

                if (tick > duration) {
                    parent.setTransformation(startTransform);
                    followMeatSlots(furniture, parent);
                    animating.remove(furnitureId);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    cancel();
                    return;
                }

                float t = tick / (float) duration;
                float angle = (float) Math.sin(t * Math.PI) * wobbleRadians;
                Quaternionf wobble = new Quaternionf().rotateY(angle);
                Quaternionf newRot = wobble.mul(startRot);

                parent.setTransformation(new Transformation(
                        startTranslation,
                        newRot,
                        startTransform.getScale(),
                        startTransform.getRightRotation()));
                followMeatSlots(furniture, parent);

                tick++;
            }
        }.runTaskTimer(Cooking.plugin, 0L, 1L);
    }

    private static ItemDisplay getParentDisplay(Furniture furniture) {
        if (Bukkit.getEntity(furniture.getEntityId()) instanceof ItemDisplay display) {
            return display;
        }
        return null;
    }

    private static void followMeatSlots(Furniture furniture, ItemDisplay parent) {
        for (String slotId : SausageMakerHandler.MEAT_SLOTS) {
            if (!furniture.hasActiveSlot(slotId)) {
                continue;
            }
            PlacedSlot slot = furniture.getActiveSlot(slotId).orElse(null);
            if (slot != null) {
                slot.followParentTransform(parent);
            }
        }
    }
}
