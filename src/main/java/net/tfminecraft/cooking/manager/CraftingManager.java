package net.tfminecraft.cooking.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.cache.ItemCache;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.crafting.CraftingStation;
import net.tfminecraft.cooking.enums.Method;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.data.CookData;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.loader.CraftingStationLoader;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;

public class CraftingManager implements Listener {

    public Map<UUID, CraftingStation> stations = new HashMap<>();
    private final Map<UUID, Long> firePitCooldown = new HashMap<>();

    private boolean isOnFirePitCooldown(Furniture f) {
        Long until = firePitCooldown.get(f.getEntityId());
        if (until == null) return false;
        return System.currentTimeMillis() < until;
    }

    private void startFirePitCooldown(Furniture f, long millis) {
        firePitCooldown.put(f.getEntityId(), System.currentTimeMillis() + millis);
    }

    private boolean hasMeatOnSpit(Furniture f) {
        return f.hasActiveSlot("content");
    }

    public void rebuildStations() {
        stations.clear();
        resumeLoadedStations();
    }

    public void resumeLoadedStations() {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            resumeStation(furniture);
        }
    }

    private void resumeStation(Furniture furniture) {
        if (isCraftingStationFurniture(furniture)) {
            getOrCreateStation(furniture);
        }
    }

    private boolean isCraftingStationFurniture(Furniture f) {
        if (f.getType() == null) return false;
        String typeId = f.getType().getId();
        for (CraftingStation s : CraftingStationLoader.get()) {
            if (s.getBlockId().equalsIgnoreCase(typeId)) return true;
        }
        return false;
    }

    public CraftingStation getOrCreateStation(Furniture f) {
        if (f.getType() == null) return null;
        CraftingStation existing = stations.get(f.getEntityId());
        if (existing != null) return existing;

        for (CraftingStation template : CraftingStationLoader.get()) {
            if (template.getBlockId().equalsIgnoreCase(f.getType().getId())) {
                CraftingStation station = new CraftingStation(f, template);
                station.rebuildFromFurniture();
                stations.put(f.getEntityId(), station);
                return station;
            }
        }
        return null;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(Cooking.plugin, () -> resumeStationsInChunk(chunk), 1L);
    }

    private void resumeStationsInChunk(Chunk chunk) {
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            if (!furniture.getLoc().getChunk().equals(chunk)) continue;
            resumeStation(furniture);
        }
    }


    @EventHandler
    public void furnitureInteract(FurnitureSlotItemAddEvent e) {
        Furniture f = e.getFurniture();
        if (FurnitureCache.isFirePit(f) && e.getSlot().getId().equals("content")) {
            if (!HeatSources.stationHasHeat(f)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§cLight the campfire under the fire pit first.");
                return;
            }
            FoodItem fi = FoodItem.fromItem(e.getItem());
            if (fi == null || !fi.getCookData().hasMethod(Method.FIRE_PIT)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§cOnly whole roasts can be cooked on a fire pit.");
                return;
            }
        }
        CraftingStation station = getOrCreateStation(f);
        if (station != null) {
            station.addItem(e);
        }
    }

    @EventHandler
    public void furnitureTakeInteract(FurnitureSlotItemTakeEvent e) {
        Furniture f = e.getFurniture();
        if (FurnitureCache.isFirePit(f) && e.getSlot().getId().equals("content") && isOnFirePitCooldown(f)) {
            e.setCancelled(true);
            return;
        }

        CraftingStation station = getOrCreateStation(f);
        if (station != null) {
            station.removeItem(e);
        }
    }

    @EventHandler
    public void furnitureAddInteract(FurnitureSlotItemAddEvent e) {

        Furniture f = e.getFurniture();
        if (FurnitureCache.isFirePit(f) && e.getSlot().getId().equals("content")) {
            Bukkit.getScheduler().runTask(Cooking.plugin, () -> {
                f.getActiveSlot("content").ifPresent(slot -> {
                    ItemStack stack = slot.getCurrentItem();
                    if (stack == null) return;
                    FoodItem fi = FoodItem.fromItem(stack);
                    if (fi == null) return;
                    CarvableRoastUtils.readCarveState(fi, stack);
                    ItemDisplay display = getActiveSlotDisplay(f, "content");
                    applyFirePitTransformOffset(display);
                    slot.applyDisplayData(CarvableRoastUtils.getStageModelData(fi).getDisplayData(f.getId()));
                });
            });
        }
    }

    @EventHandler
    public void furnitureInteract(FurnitureInteractEvent e) {
        Furniture f = e.getFurniture();
        if (FurnitureCache.isFirePit(f)) {
            handleFirePitInteract(e);
        }
        CraftingStation station = getOrCreateStation(f);
        if (station != null) {
            station.interact(e);
        }
    }

    @EventHandler
    public void furnitureBreak(FurnitureBreakEvent e) {
        Furniture f = e.getFurniture();
        if (FurnitureCache.isFirePit(f)) {
            firePitCooldown.remove(f.getEntityId());
        }
        if (isCraftingStationFurniture(f)) {
            CraftingStation station = getOrCreateStation(f);
            if (station != null) {
                station.remove(e);
                if (e.isCancelled()) return;
                stations.remove(f.getEntityId());
            }
        }
    }

    @EventHandler
    public void furniturePlace(FurniturePlaceEvent e) {
        Furniture f = e.getFurniture();
        if (FurnitureCache.isFirePit(f)) {
            if (f.getType() == null || f.getType().getSlot("turner") == null) return;
            f.getOrCreatePlacedSlot("turner").forceModel(TLibs.getItemAPI().getCreator().getItemFromPath(ItemCache.firePitTurner));
            applyFirePitTransformOffset(getActiveSlotDisplay(f, "turner"));
        }
    }

    private void handleFirePitInteract(FurnitureInteractEvent e) {
        Furniture f = e.getFurniture();
        SlotDefinition hitSlot = e.getHitSlot();
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().equals(Material.AIR);

        if (hitSlot != null && hitSlot.getId().equals("content") && isOnFirePitCooldown(f)) {
            e.setCancelled(true);
            return;
        }

        if (hitSlot != null && hitSlot.getId().equals("turner")) {
            e.setCancelled(true);
            if (emptyHand && hasMeatOnSpit(f)) {
                if (!HeatSources.stationHasHeat(f)) {
                    e.getPlayer().sendMessage("§cLight the campfire under the fire pit first.");
                    return;
                }
                if (isOnFirePitCooldown(f)) return;
                playFirePitTurnAnimation(f);
                advanceFirePitCooking(f);
                startFirePitCooldown(f, 40 * 50L);
                f.getLoc().getWorld().playSound(f.getLoc(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1f);
            }
        }
    }

    private void advanceFirePitCooking(Furniture f) {
        f.getActiveSlot("content").ifPresent(slot -> {
            ItemStack stack = slot.getCurrentItem();
            if (stack == null) return;
            FoodItem fi = FoodItem.fromItem(stack);
            if (fi == null) return;
            CarvableRoastUtils.readCarveState(fi, stack);
            CookData cd = fi.getCookData();
            if (!cd.hasMethod(Method.FIRE_PIT)) return;
            if (!cd.isBeingCooked()) cd.start(Method.FIRE_PIT);
            cd.setCurrentTime(cd.getCurrentTime() + 1);
            ItemStack updated = ItemUpdater.applyItemUpdate(stack, fi, f.getId());
            if (updated == null) return;
            CarvableRoastUtils.writeCarveState(updated, fi);
            slot.setCurrentItem(updated);
            slot.applyDisplayData(CarvableRoastUtils.getStageModelData(fi).getDisplayData(f.getId()));
        });
    }

    private ItemDisplay getActiveSlotDisplay(Furniture f, String slotId) {
        return f.getActiveSlot(slotId)
                .map(slot -> {
                    if (slot.getDisplayStandId() == null) return null;
                    Entity ent = Bukkit.getEntity(slot.getDisplayStandId());
                    return ent instanceof ItemDisplay id ? id : null;
                })
                .orElse(null);
    }

    private void applyFirePitTransformOffset(ItemDisplay display) {
        if (display == null) return;
        Transformation t = display.getTransformation();
        Vector3f trans = t.getTranslation();
        float visualY = ItemCache.firePitVisualY;
        if (trans.y() > 0.01f) return;
        display.setTransformation(new Transformation(
                new Vector3f(trans.x(), trans.y() + visualY, trans.z()),
                t.getLeftRotation(),
                t.getScale(),
                t.getRightRotation()
        ));
    }

    private Vector3f computeSpitCenter(ItemDisplay contentDisp, ItemDisplay parent) {
        Vector3f localOffset = new Vector3f(0f, ItemCache.firePitPivotY, 0f);
        new Quaternionf(parent.getTransformation().getLeftRotation()).transform(localOffset);
        return contentDisp.getLocation().toVector().toVector3f().add(localOffset);
    }

    private Vector3f computeSpinAxis(ItemDisplay parent) {
        Vector3f axis = switch (ItemCache.firePitSpinAxis) {
            case "y" -> new Vector3f(0f, 1f, 0f);
            case "z" -> new Vector3f(0f, 0f, 1f);
            default -> new Vector3f(1f, 0f, 0f);
        };
        new Quaternionf(parent.getTransformation().getLeftRotation()).transform(axis);
        return axis.normalize();
    }

    private record FirePitSpinState(
            Location startLoc,
            Vector3f startTrans,
            Quaternionf startRot,
            Vector3f scale,
            Quaternionf rightRot,
            Vector3f deltaFromSpit) {}

    private FirePitSpinState captureSpinState(ItemDisplay display, Vector3f spitCenter) {
        Transformation t = display.getTransformation();
        Location startLoc = display.getLocation().clone();
        Vector3f delta = startLoc.toVector().toVector3f().sub(spitCenter);
        return new FirePitSpinState(
                startLoc,
                new Vector3f(t.getTranslation()),
                new Quaternionf(t.getLeftRotation()),
                new Vector3f(t.getScale()),
                new Quaternionf(t.getRightRotation()),
                delta
        );
    }

    private void restoreSpinState(ItemDisplay display, FirePitSpinState state) {
        display.teleport(state.startLoc());
        display.setTransformation(new Transformation(
                state.startTrans(), state.startRot(), state.scale(), state.rightRot()));
    }

    private void applyRigidSpinFrame(ItemDisplay display, Vector3f spitCenter, Vector3f spinAxis,
            float angle, FirePitSpinState state) {
        Quaternionf spin = new Quaternionf().rotateAxis(angle, spinAxis.x, spinAxis.y, spinAxis.z);
        Vector3f newPos = new Vector3f(spitCenter).add(new Vector3f(state.deltaFromSpit()).rotate(spin));
        Location loc = state.startLoc().clone();
        loc.setX(newPos.x);
        loc.setY(newPos.y);
        loc.setZ(newPos.z);
        display.teleport(loc);
        Quaternionf newRot = new Quaternionf(spin).mul(state.startRot());
        display.setTransformation(new Transformation(
                state.startTrans(), newRot, state.scale(), state.rightRot()));
    }

    private void playFirePitTurnAnimation(Furniture f) {
        ItemDisplay contentDisp = getActiveSlotDisplay(f, "content");
        ItemDisplay turnerDisp = getActiveSlotDisplay(f, "turner");
        ItemDisplay parent = (ItemDisplay) Bukkit.getEntity(f.getEntityId());
        if (contentDisp == null || turnerDisp == null || parent == null) return;

        applyFirePitTransformOffset(contentDisp);
        applyFirePitTransformOffset(turnerDisp);

        Vector3f spitCenter = computeSpitCenter(contentDisp, parent);
        Vector3f spinAxis = computeSpinAxis(parent);
        FirePitSpinState contentState = captureSpinState(contentDisp, spitCenter);
        FirePitSpinState turnerState = captureSpinState(turnerDisp, spitCenter);

        new BukkitRunnable() {
            int tick = 0;
            final int duration = 40;

            @Override
            public void run() {
                if (contentDisp.isDead() || turnerDisp.isDead()) {
                    cancel();
                    return;
                }

                if (tick > duration) {
                    restoreSpinState(contentDisp, contentState);
                    restoreSpinState(turnerDisp, turnerState);
                    cancel();
                    return;
                }

                float angle = (tick / (float) duration) * (float) (2 * Math.PI);
                applyRigidSpinFrame(contentDisp, spitCenter, spinAxis, angle, contentState);
                applyRigidSpinFrame(turnerDisp, spitCenter, spinAxis, angle, turnerState);

                tick++;
            }
        }.runTaskTimer(Cooking.plugin, 0L, 1L);
    }
}
