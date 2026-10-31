package net.tfminecraft.cooking.popcorn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.utils.ItemRef;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

/**
 * A popcorn machine popping. The kettle warms up with a crackle and starts to rattle; then kernels pop out of it one
 * after another, fly up in arcs, bounce off the glass and land on the heap, which rises a level at a time; at the end
 * the kettle tips out the last of them and a bell rings. The flying kernels are short-lived displays that are never
 * saved, so a restart mid-show leaves nothing behind (the machine then counts as done).
 */
public final class PopcornAnimation {
    /** Ticks of warming up before the first pop. */
    static final int WARM = 20;
    /** Ticks at the end in which the kettle tips out and rights itself. */
    static final int TIP = 30;
    private static final double GRAVITY = 0.018;
    private static final int KERNEL_LIFE = 80;
    private static final float KERNEL_SCALE = 0.22f;
    private static final float TIP_ANGLE = 0.9f;
    private static final Map<UUID, PopcornAnimation> running = new ConcurrentHashMap<>();
    static Random random = new Random();

    private final Furniture furniture;
    private final PopcornMachine machine;
    private final ItemStack kernel;
    private final int finalLevel;
    private final Collection<String> tags;
    private final Runnable onDone;
    private final List<Kernel> kernels = new ArrayList<>();
    private ItemDisplay kettle;
    private Transformation kettleRest;
    private int shownLevel;
    private int tick;
    private boolean stopped;

    /** A popped kernel in flight, relative to the furniture's centre. */
    private static final class Kernel {
        ItemDisplay display;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        int age;
    }

    PopcornAnimation(Furniture furniture, PopcornMachine machine, ItemStack kernel, int input,
            Collection<String> tags, Runnable onDone) {
        this.furniture = furniture;
        this.machine = machine;
        this.kernel = kernel;
        this.finalLevel = Math.max(1, machine.heapLevel(input));
        this.tags = tags;
        this.onDone = onDone;
    }

    /** True while this machine is popping. */
    public static boolean isRunning(Furniture furniture) {
        return running.containsKey(furniture.getEntityId());
    }

    /**
     * Starts the show for this much input; kernel is the popcorn that flies about. onDone runs once it has finished
     * (not when it's stopped).
     */
    public static void play(Furniture furniture, PopcornMachine machine, ItemStack kernel, int input,
            Collection<String> tags, Runnable onDone) {
        stop(furniture);
        PopcornAnimation animation = new PopcornAnimation(furniture, machine, kernel, input, tags, onDone);
        running.put(furniture.getEntityId(), animation);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!animation.tick()) cancel();
            }
        }.runTaskTimer(Cooking.plugin, 0L, 1L);
    }

    /** Stops the show without finishing it, clearing the flying kernels and putting the kettle back. */
    public static void stop(Furniture furniture) {
        PopcornAnimation animation = running.remove(furniture.getEntityId());
        if (animation != null) animation.cleanUp();
    }

    /** Runs one tick of the show; false once it's over. */
    boolean tick() {
        if (stopped) return false;
        if (tick == 0) kettle = kettleDisplay();
        int total = machine.getPopTicks();
        int popEnd = total - TIP;
        if (tick < WARM) {
            rattle(0.03f);
            if (tick % 10 == 0) sound(Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8f, 1.2f);
        } else if (tick < popEnd) {
            rattle(0.08f);
            if (tick % 2 == 0) pop();
            if (random.nextInt(4) == 0) pop();
            double progress = (tick - WARM + 1) / (double) (popEnd - WARM);
            showHeap(Math.max(1, (int) Math.ceil(progress * finalLevel)));
        } else {
            int t = tick - popEnd;
            float angle = t < 10 ? TIP_ANGLE * t / 10f : t < 20 ? TIP_ANGLE : TIP_ANGLE * (TIP - t) / 10f;
            turnKettle(new Quaternionf().rotateZ(angle));
            if (t >= 10 && t < 20) pop();
            showHeap(finalLevel);
        }
        fly();
        tick++;
        if (tick > total) {
            finish();
            return false;
        }
        return true;
    }

    private void finish() {
        running.remove(furniture.getEntityId(), this);
        cleanUp();
        sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);
        Location top = furniture.getLoc().clone().add(0, machine.getRim() + 0.3, 0);
        top.getWorld().spawnParticle(Particle.CLOUD, top, 6, 0.15, 0.05, 0.15, 0.01);
        onDone.run();
    }

    private void cleanUp() {
        stopped = true;
        for (Kernel k : kernels) k.display.remove();
        kernels.clear();
        if (kettle != null && kettleRest != null && !kettle.isDead()) kettle.setTransformation(kettleRest);
    }

    /** The kettle slot's display, remembering how it rests; null when the slot shows nothing. */
    private ItemDisplay kettleDisplay() {
        PlacedSlot slot = furniture.getActiveSlot(machine.getKettleSlot()).orElse(null);
        if (slot == null || slot.getDisplayStandId() == null) return null;
        Entity entity = Bukkit.getEntity(slot.getDisplayStandId());
        if (!(entity instanceof ItemDisplay display)) return null;
        kettleRest = display.getTransformation();
        return display;
    }

    private void rattle(float amount) {
        float x = (random.nextFloat() * 2 - 1) * amount;
        float z = (random.nextFloat() * 2 - 1) * amount;
        turnKettle(new Quaternionf().rotateX(x).rotateZ(z));
    }

    private void turnKettle(Quaternionf turn) {
        if (kettle == null || kettle.isDead()) return;
        Quaternionf rotation = new Quaternionf(kettleRest.getLeftRotation()).mul(turn);
        kettle.setInterpolationDelay(0);
        kettle.setInterpolationDuration(1);
        kettle.setTransformation(new Transformation(new Vector3f(kettleRest.getTranslation()), rotation,
                new Vector3f(kettleRest.getScale()), new Quaternionf(kettleRest.getRightRotation())));
    }

    /** A kernel pops out of the kettle with a little cloud and a pop. */
    private void pop() {
        Kernel k = new Kernel();
        k.x = (random.nextDouble() * 2 - 1) * 0.08;
        k.y = machine.getRim();
        k.z = (random.nextDouble() * 2 - 1) * 0.08;
        k.vx = (random.nextDouble() * 2 - 1) * 0.035;
        k.vy = 0.05 + random.nextDouble() * 0.07;
        k.vz = (random.nextDouble() * 2 - 1) * 0.035;
        Location at = at(k);
        World world = at.getWorld();
        Quaternionf spin = new Quaternionf().rotateXYZ(random.nextFloat() * 6.3f, random.nextFloat() * 6.3f, 0);
        k.display = world.spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(kernel.clone());
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setTransformation(new Transformation(new Vector3f(), spin,
                    new Vector3f(KERNEL_SCALE, KERNEL_SCALE, KERNEL_SCALE), new Quaternionf()));
        });
        kernels.add(k);
        world.spawnParticle(Particle.CLOUD, at, 1, 0.02, 0.02, 0.02, 0.005);
        world.playSound(at, Sound.ENTITY_CHICKEN_EGG, 0.35f, 1.6f + random.nextFloat() * 0.4f);
    }

    /** Moves every kernel along its arc, off the glass, until it lands on the heap. */
    private void fly() {
        double walls = machine.getWalls();
        double landing = machine.getFloor() + machine.heapHeight(shownLevel);
        Iterator<Kernel> it = kernels.iterator();
        while (it.hasNext()) {
            Kernel k = it.next();
            k.vy -= GRAVITY;
            k.x += k.vx;
            k.y += k.vy;
            k.z += k.vz;
            if (Math.abs(k.x) > walls) {
                k.x = Math.signum(k.x) * walls;
                k.vx = -k.vx * 0.5;
            }
            if (Math.abs(k.z) > walls) {
                k.z = Math.signum(k.z) * walls;
                k.vz = -k.vz * 0.5;
            }
            if ((k.vy < 0 && k.y <= landing) || ++k.age > KERNEL_LIFE) {
                k.display.remove();
                it.remove();
            } else {
                k.display.teleport(at(k));
            }
        }
    }

    private Location at(Kernel k) {
        return furniture.getLoc().clone().add(k.x, k.y, k.z);
    }

    /** Shows the heap at this level, once it changes. */
    private void showHeap(int level) {
        if (level == shownLevel) return;
        shownLevel = level;
        if (furniture.getType() == null || furniture.getType().getSlot(machine.getHeapSlot()) == null) return;
        ItemStack heap = ItemRef.resolve(machine.heapFor(level, tags));
        if (heap != null) furniture.getOrCreatePlacedSlot(machine.getHeapSlot()).forceModel(heap);
    }

    private void sound(Sound sound, float volume, float pitch) {
        Location at = furniture.getLoc();
        at.getWorld().playSound(at, sound, volume, pitch);
    }
}
