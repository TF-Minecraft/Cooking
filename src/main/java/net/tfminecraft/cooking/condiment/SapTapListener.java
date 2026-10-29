package net.tfminecraft.cooking.condiment;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.HeldStack;
import net.tfminecraft.cooking.utils.ItemBuilder;

/**
 * Right-clicking a log (condiments.yml sap.logs) with an empty glass bottle collects its sap, which the saucepan boils
 * down to syrup. A tapped log gives sap again after sap.cooldown-minutes; the time is kept in its chunk.
 */
public final class SapTapListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTap(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block log = event.getClickedBlock();
        if (log == null || !CondimentConfig.isSapLog(log.getType())) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.GLASS_BOTTLE || FoodItem.fromItem(hand) != null) return;
        event.setCancelled(true);

        PersistentDataContainer chunk = log.getChunk().getPersistentDataContainer();
        NamespacedKey key = keyFor(log);
        long now = System.currentTimeMillis();
        Long ready = chunk.get(key, PersistentDataType.LONG);
        if (ready != null && ready > now) {
            long minutes = Math.max(1, (ready - now + 59_999) / 60_000);
            player.sendMessage("§7This log has given its sap. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
            return;
        }
        FoodParser.Result parsed = FoodParser.parse(CondimentConfig.sapOutput());
        ItemStack sap = parsed == null || parsed.template == null ? null : ItemBuilder.buildSingle(parsed.template, null);
        if (sap == null) {
            player.sendMessage("§cNo sap can be collected right now.");
            return;
        }
        chunk.set(key, PersistentDataType.LONG, now + CondimentConfig.sapCooldownMillis());
        HeldStack.useOneKeepStack(player, sap);
        player.swingMainHand();
        log.getWorld().playSound(log.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
    }

    static NamespacedKey keyFor(Block log) {
        return new NamespacedKey(Cooking.plugin, "sap_" + log.getX() + "_" + log.getY() + "_" + log.getZ());
    }
}
