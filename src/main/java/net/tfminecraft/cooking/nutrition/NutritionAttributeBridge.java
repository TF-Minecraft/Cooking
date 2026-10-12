package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes.AttributeInstance;
import net.Indyuce.mmocore.api.player.profess.resource.PlayerResource;
import net.tfminecraft.rpcharacters.objects.RPCharacter;

public final class NutritionAttributeBridge {

    private NutritionAttributeBridge() {}

    public static void apply(Player player, RPCharacter character) {
        if (player == null || character == null) {
            return;
        }
        apply(NutritionContext.capture(player, character));
    }

    public static void apply(NutritionContext context) {
        if (context == null || !context.isCurrent()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            return;
        }

        Player player = context.player();
        RPCharacter character = context.character();
        String attrId = NutritionConfig.attributeName();
        PlayerData data = PlayerData.get(player);
        AttributeInstance instance = data.getAttributes().getInstance(attrId);
        if (instance == null) {
            return;
        }

        int diet = character.getDietScore();
        if (instance.getBase() == diet) {
            return;
        }

        // setBase strips the attribute's stat buffs before adding them back, and MMOCore clamps
        // health, mana, stamina and stellium to the stripped maximums in between. Put back what
        // that clamp took, up to the new maximums.
        PlayerResource[] resources = {PlayerResource.MANA, PlayerResource.STAMINA, PlayerResource.STELLIUM};
        double health = player.getHealth();
        double[] before = new double[resources.length];
        for (int i = 0; i < resources.length; i++) {
            before[i] = resources[i].getCurrent(data);
        }
        if (!context.isCurrent()) return;
        instance.setBase(diet);
        if (!context.isCurrent()) return;
        double healthCap = Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (player.getHealth() < healthCap) {
            if (!context.isCurrent()) return;
            player.setHealth(healthCap);
        }
        for (int i = 0; i < resources.length; i++) {
            if (!context.isCurrent()) return;
            double cap = Math.min(before[i], resources[i].getMax(data));
            if (resources[i].getCurrent(data) < cap) {
                if (!context.isCurrent()) return;
                resources[i].setCurrent(data, cap, ResourceUpdateReason.CLAMPING);
            }
        }
    }
}
