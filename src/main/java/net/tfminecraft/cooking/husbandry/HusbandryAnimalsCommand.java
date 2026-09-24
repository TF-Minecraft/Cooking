package net.tfminecraft.cooking.husbandry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tfminecraft.cooking.Cooking;

public final class HusbandryAnimalsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        HusbandryRepository repository = Cooking.plugin == null ? null : Cooking.plugin.getHusbandryRepository();
        if (repository == null) {
            sender.sendMessage(Component.text("Animal records are not available right now.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && !sender.hasPermission("cooking.admin")) {
            sender.sendMessage(Component.text("You can only list your own animals.", NamedTextColor.RED));
            return true;
        }
        boolean self = args.length == 0;
        UUID targetId;
        String ownerName;
        if (self) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.RED));
                return true;
            }
            targetId = player.getUniqueId();
            ownerName = player.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (offline == null) {
                sender.sendMessage(Component.text("No player by that name has joined.", NamedTextColor.RED));
                return true;
            }
            targetId = offline.getUniqueId();
            ownerName = offline.getName() == null ? args[0] : offline.getName();
        }
        List<HusbandryOwned> rows = new ArrayList<>();
        for (HusbandryOwned owned : repository.listForPlayer(targetId)) {
            if (owned.animal() == null) {
                continue;
            }
            HusbandryAnimal animal = HusbandryEntities.getLoaded(owned.animal().uuid()).orElse(owned.animal());
            Entity live = Bukkit.getEntity(animal.uuid());
            if (live != null) {
                HusbandryLocation.remember(animal, live);
            }
            rows.add(new HusbandryOwned(animal, owned.role()));
        }
        for (Component line : HusbandryRoster.render(
                ownerName, self, rows, HusbandryConfig.maxAnimals(), System.currentTimeMillis())) {
            sender.sendMessage(line);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cooking.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(player.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
