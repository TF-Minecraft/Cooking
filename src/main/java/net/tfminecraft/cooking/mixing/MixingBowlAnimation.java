package net.tfminecraft.cooking.mixing;

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

public final class MixingBowlAnimation {
    private static final Set<UUID> animating = ConcurrentHashMap.newKeySet();

    private MixingBowlAnimation() {}

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

    public static void playStir(Furniture furniture) {
        ItemDisplay parent = getParentDisplay(furniture);
        if (parent == null || furniture.getEntityId() == null) {
            return;
        }

        UUID furnitureId = furniture.getEntityId();
        animating.add(furnitureId);

        final Transformation startTransform = parent.getTransformation();
        final Vector3f startTranslation = new Vector3f(startTransform.getTranslation());
        final Quaternionf startRot = new Quaternionf(startTransform.getLeftRotation());
        final Vector3f spinAxis = computeSpinAxis(parent);
        final float tiltRadians = (float) Math.toRadians(ItemCache.mixingStirTiltDegrees);
        final float pivotY = ItemCache.mixingStirPivotY;
        final int duration = ItemCache.mixingStirDurationTicks;

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
                    followIngredientSlots(furniture, parent);
                    animating.remove(furnitureId);
                    cancel();
                    return;
                }

                float yaw = (tick / (float) duration) * (float) (2 * Math.PI);
                Quaternionf tiltQuat = new Quaternionf().rotateX(tiltRadians);
                Quaternionf yawQuat = new Quaternionf().rotateAxis(yaw, spinAxis.x, spinAxis.y, spinAxis.z);
                Quaternionf wobble = yawQuat.mul(tiltQuat);
                Quaternionf newRot = wobble.mul(startRot);

                Vector3f translation = new Vector3f(startTranslation);
                if (pivotY != 0f) {
                    Vector3f pivotOffset = new Vector3f(0f, pivotY, 0f);
                    startRot.transform(pivotOffset);
                    Vector3f rotatedPivot = new Vector3f(pivotOffset).rotate(wobble);
                    translation.sub(rotatedPivot).add(pivotOffset);
                }

                parent.setTransformation(new Transformation(
                        translation,
                        newRot,
                        startTransform.getScale(),
                        startTransform.getRightRotation()));
                followIngredientSlots(furniture, parent);

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

    private static Vector3f computeSpinAxis(ItemDisplay parent) {
        Vector3f axis = new Vector3f(0f, 1f, 0f);
        new Quaternionf(parent.getTransformation().getLeftRotation()).transform(axis);
        return axis.normalize();
    }

    private static void followIngredientSlots(Furniture furniture, ItemDisplay parent) {
        for (String slotId : new String[] {
                MixingBowlSlots.FLOUR, MixingBowlSlots.WATER, MixingBowlSlots.YEAST,
                MixingBowlSlots.DOUGH}) {
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
