package net.tfminecraft.cooking.nutrition;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.tfminecraft.cooking.utils.PlayerActionContext;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.tlibs.character.CharacterSession;

/** Ready shared identity paired with RPCharacters' mutable nutrition fields. */
public record NutritionContext(PlayerActionContext action, RPCharacter character) {
    public static NutritionContext capture(Player player) {
        PlayerActionContext action = PlayerActionContext.capture(player);
        if (action == null || action.session() == null
                || !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) return null;
        RPCharacter character = RPCharacters.getActiveCharacter(player);
        if (character == null || character.getOwner() != player
                || !action.session().ref().ownerId().equals(player.getUniqueId())
                || !action.session().ref().characterId().toString().equalsIgnoreCase(character.getId())
                || !action.isCurrent()) return null;
        return new NutritionContext(action, character);
    }

    public static NutritionContext capture(Player player, RPCharacter expected) {
        NutritionContext context = capture(player);
        return context != null && context.character() == expected ? context : null;
    }

    public Player player() { return action.player(); }
    public CharacterSession session() { return action.session(); }
    public boolean isCurrent() { return action.isCurrent(); }

    public boolean save() {
        if (!isCurrent()) return false;
        RPCharacters.getPlayerManager().savePlayer(player());
        return isCurrent();
    }
}
