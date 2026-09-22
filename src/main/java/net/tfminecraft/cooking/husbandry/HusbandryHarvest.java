package net.tfminecraft.cooking.husbandry;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.carve.CarvableRoastUtils;
import net.tfminecraft.cooking.carve.CarveSequence;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.model.ModelData;
import net.tfminecraft.cooking.loader.CarveSequenceLoader;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.InventoryAdder;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.ItemUpdater;
import net.tfminecraft.cooking.utils.QualityUtils;

public final class HusbandryHarvest {

    private HusbandryHarvest() {}

    public static void prepareNewAnimal(HusbandryAnimal animal, EntityType type) {
        if (animal == null) {
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species != null && species.canShear()) {
            animal.setWoolReadyAt(System.currentTimeMillis());
        }
        if (species != null && species.canShed()) {
            animal.setShedReadyAt(System.currentTimeMillis());
        }
        if (species != null && species.hasEgg()) {
            animal.setEggReadyAt(System.currentTimeMillis());
        }
    }

    public static int stars(HusbandryAnimal animal) {
        if (animal == null) {
            return 1;
        }
        return HusbandryConfig.starsForGenetics(animal.genetics());
    }

    public static int rollQuality(HusbandryAnimal animal, Random random) {
        return HusbandryQualityRange.roll(HusbandryConfig.qualityRange(animal), random);
    }

    public enum ShearResult {
        IMMATURE,
        COOLDOWN,
        DONE
    }

    public static ShearResult shearReadiness(
            HusbandryAnimal animal,
            HusbandrySpecies species,
            long nowMillis) {
        if (animal == null || species == null || !species.canShear()) {
            return ShearResult.COOLDOWN;
        }
        if (!HusbandryGrowth.isMature(animal, nowMillis)) {
            return ShearResult.IMMATURE;
        }
        if (animal.woolReadyAt() != null && animal.woolReadyAt() > nowMillis) {
            return ShearResult.COOLDOWN;
        }
        return ShearResult.DONE;
    }

    public static ShearResult tryShear(
            Player player,
            LivingEntity living,
            HusbandryAnimal animal,
            HusbandrySpecies species,
            HusbandryRepository repository,
            long nowMillis) {
        ShearResult readiness = shearReadiness(animal, species, nowMillis);
        if (readiness == ShearResult.IMMATURE) {
            if (player != null) {
                player.sendMessage("§cThis animal is still growing up.");
            }
            return ShearResult.IMMATURE;
        }
        if (readiness != ShearResult.DONE) {
            if (player != null) {
                player.sendMessage("§cThis animal is not ready to be sheared.");
            }
            return ShearResult.COOLDOWN;
        }
        grantShearExtras(player, animal, nowMillis);
        if (living != null && living.getWorld() != null) {
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
        }
        startWoolTimer(animal, species, repository, nowMillis);
        return ShearResult.DONE;
    }

    public static ShearResult trySheepBonusShear(
            Player player,
            HusbandryAnimal animal,
            HusbandrySpecies species,
            HusbandryRepository repository,
            long nowMillis) {
        ShearResult readiness = shearReadiness(animal, species, nowMillis);
        if (readiness != ShearResult.DONE) {
            return readiness;
        }
        grantShearExtras(player, animal, nowMillis);
        startWoolTimer(animal, species, repository, nowMillis);
        return ShearResult.DONE;
    }

    private static void grantShearExtras(Player player, HusbandryAnimal animal, long nowMillis) {
        if (player == null) {
            return;
        }
        for (ItemStack drop : HusbandryDropRoller.rollShearDrops(animal, ThreadLocalRandom.current(), nowMillis)) {
            InventoryAdder.addItem(player, drop);
        }
    }

    private static void startWoolTimer(
            HusbandryAnimal animal,
            HusbandrySpecies species,
            HusbandryRepository repository,
            long nowMillis) {
        long wait = HusbandryConfig.woolTimerSeconds(species == null ? null : species.type()) * 1000L;
        animal.setWoolReadyAt(nowMillis + wait);
        if (repository != null) {
            repository.upsertAnimal(animal);
        }
    }

    public static String milkFoodString(EntityType type) {
        return milkFoodStringForType(type == null ? null : type.name());
    }

    static String milkFoodStringForType(String typeName) {
        if ("GOAT".equalsIgnoreCase(typeName)) {
            return "food(type=milk_bucket;origin=Goat)";
        }
        return "food(type=milk_bucket;origin=Cow)";
    }

    public static Sound milkSound(EntityType type) {
        if (type == EntityType.GOAT) {
            return Sound.ENTITY_GOAT_MILK;
        }
        return Sound.ENTITY_COW_MILK;
    }

    public static ItemStack buildFood(HusbandryAnimal animal, String foodString) {
        return buildFood(animal, foodString, null);
    }

    public static ItemStack buildMilk(HusbandryAnimal animal, EntityType type) {
        return buildFood(animal, milkFoodString(type), new ItemStack(Material.MILK_BUCKET));
    }

    public static ItemStack buildFood(HusbandryAnimal animal, String foodString, ItemStack base) {
        if (foodString == null || foodString.isBlank() || animal == null) {
            return null;
        }
        FoodParser.Result parsed = FoodParser.parse(foodString);
        if (parsed == null || parsed.template == null) {
            return null;
        }
        int quality = QualityUtils.clamp(rollQuality(animal, ThreadLocalRandom.current()));
        ItemStack stack = ItemBuilder.buildSingleWithQuality(parsed.template, base, quality);
        FoodItem item = FoodItem.fromItem(stack);
        if (item == null) {
            return stack;
        }
        CarvableRoastUtils.readCarveState(item, stack);
        String seqId = item.getCarveSequencePdc() != null
                ? item.getCarveSequencePdc()
                : item.getCarveSequenceId();
        if (seqId == null) {
            return stack;
        }
        CarveSequence seq = CarveSequenceLoader.get(seqId);
        if (seq == null) {
            return stack;
        }
        int maxCuts = Math.max(1, seq.getStartRemaining());
        int cuts = Math.min(maxCuts, HusbandryConfig.roastCutsFor(HusbandryConfig.effectiveGenetics(animal)));
        int nextIndex = Math.max(0, maxCuts - cuts);
        item.setCarveState(seqId, nextIndex, cuts);
        CarvableRoastUtils.writeCarveState(stack, item);
        ItemStack updated = ItemUpdater.applyItemUpdate(stack, item, null);
        if (updated != null) {
            stack = updated;
        }
        ModelData staged = item.getModel() == null
                ? null
                : item.getModel().getModelByStageAndTag(cuts, CarvableRoastUtils.resolveCookTag(item));
        if (staged != null) {
            stack = staged.apply(null, stack);
        }
        return stack;
    }

    public static ItemStack buildTlibs(String path, int amount) {
        if (path == null || path.isBlank() || "vanilla".equalsIgnoreCase(path)) {
            return null;
        }
        try {
            ItemStack stack = TLibs.getItemAPI().getCreator().getItemFromPath(path);
            if (stack == null) {
                return null;
            }
            stack.setAmount(Math.max(1, Math.min(64, amount)));
            return stack;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isWoolDrop(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        String name = stack.getType().name();
        return name.endsWith("_WOOL") || name.equals("WOOL") || stack.getType() == Material.WHITE_WOOL;
    }
}
