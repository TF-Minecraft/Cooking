package net.tfminecraft.cooking.nutrition;

import org.bukkit.entity.Player;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.rpcharacters.objects.RPCharacter;

public final class DietTierService {

    private DietTierService() {}

    public static boolean seedIfAbsent(Player player, RPCharacter character) {
        return seedIfAbsent(NutritionContext.capture(player, character));
    }

    public static boolean seedIfAbsent(NutritionContext context) {
        if (context == null || !context.isCurrent()) return false;
        RPCharacter character = context.character();
        String lastTier = character.getLastDietTierId();
        if (lastTier != null && !lastTier.isBlank()) {
            return false;
        }

        DietTierDefinition tier = NutritionConfig.resolveTier(character.getDietScore());
        character.setLastDietTierId(tier.getId());
        context.save();
        return true;
    }

    public static void checkAndNotify(Player player, RPCharacter character, VarietyScore variety) {
        checkAndNotify(NutritionContext.capture(player, character), variety);
    }

    public static void checkAndNotify(NutritionContext context, VarietyScore variety) {
        if (context == null || !context.isCurrent()) return;
        Player player = context.player();
        RPCharacter character = context.character();
        DietTierDefinition current = NutritionConfig.resolveTier(character.getDietScore());
        String lastId = character.getLastDietTierId();
        if (lastId != null && lastId.equalsIgnoreCase(current.getId())) {
            return;
        }

        character.setLastDietTierId(current.getId());
        player.sendMessage(
                StringFormatter.formatHex("#d4ad77Your diet is now ")
                + StringFormatter.formatHex(current.getLabel()));
        if (context.isCurrent() && NutritionConfig.varietyEnabled() && variety != null) {
            DietTierDefinition varietyTier = NutritionConfig.resolveTierPercent(variety.progressPercent());
            player.sendMessage(
                    StringFormatter.formatHex("#d4ad77Variety: ")
                    + StringFormatter.formatHex(varietyTier.getLabel())
                    + StringFormatter.formatHex("#d4ad77 (" + variety.penaltyPercent() + "% nutrition penalty)"));
        }
    }
}
