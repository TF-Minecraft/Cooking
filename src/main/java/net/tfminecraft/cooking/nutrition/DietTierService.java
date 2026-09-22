package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.RPCharacters.RPCharacters;

public final class DietTierService {

    private DietTierService() {}

    public static boolean seedIfAbsent(Player player, RPCharacter character) {
        if (character == null || player == null) {
            return false;
        }
        String lastTier = character.getLastDietTierId();
        if (lastTier != null && !lastTier.isBlank()) {
            return false;
        }

        DietTierDefinition tier = NutritionConfig.resolveTier(character.getDietScore());
        character.setLastDietTierId(tier.getId());
        if (Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
            RPCharacters.getPlayerManager().savePlayer(player);
        }
        return true;
    }

    public static void checkAndNotify(Player player, RPCharacter character, VarietyScore variety) {
        if (player == null || character == null) {
            return;
        }

        DietTierDefinition current = NutritionConfig.resolveTier(character.getDietScore());
        String lastId = character.getLastDietTierId();
        if (lastId != null && lastId.equalsIgnoreCase(current.getId())) {
            return;
        }

        player.sendMessage(
                StringFormatter.formatHex("#d4ad77Your diet is now ")
                + StringFormatter.formatHex(current.getLabel()));
        if (NutritionConfig.varietyEnabled() && variety != null) {
            DietTierDefinition varietyTier = NutritionConfig.resolveTierPercent(variety.progressPercent());
            player.sendMessage(
                    StringFormatter.formatHex("#d4ad77Variety: ")
                    + StringFormatter.formatHex(varietyTier.getLabel())
                    + StringFormatter.formatHex("#d4ad77 (" + variety.penaltyPercent() + "% nutrition penalty)"));
        }
        character.setLastDietTierId(current.getId());
    }
}
