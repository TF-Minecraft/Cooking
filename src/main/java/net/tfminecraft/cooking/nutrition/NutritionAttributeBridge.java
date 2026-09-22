package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes.AttributeInstance;
import net.tfminecraft.rpcharacters.objects.RPCharacter;

public final class NutritionAttributeBridge {

    private NutritionAttributeBridge() {}

    public static void apply(Player player, RPCharacter character) {
        if (player == null || character == null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            return;
        }

        String attrId = NutritionConfig.attributeName();
        AttributeInstance instance = PlayerData.get(player).getAttributes().getInstance(attrId);
        if (instance == null) {
            return;
        }

        instance.setBase(character.getDietScore());
    }
}
