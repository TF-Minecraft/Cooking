package net.tfminecraft.cooking.fishing;

import java.util.logging.Logger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.momirealms.customfishing.api.event.FishingLootSpawnEvent;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import net.momirealms.customfishing.api.mechanic.loot.Loot;

public final class CustomFishingCatchListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger("Cooking");

    private CustomFishingCatchListener() {}

    public static void register(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new CustomFishingCatchListener(), plugin);
    }

    @EventHandler
    public void onLoot(FishingLootSpawnEvent event) {
        if (event == null || event.getContext() == null) {
            return;
        }
        Loot loot = event.getLoot();
        String lootId = loot == null ? null : loot.id();
        Entity entity = event.getEntity();
        Float size = event.getContext().arg(ContextKeys.SIZE);
        Double centimetres = size == null ? null : size.doubleValue();
        CustomFishingCatchAdapter.Decision decision = CustomFishingCatchAdapter.decide(
                lootId,
                centimetres,
                entity instanceof Item);
        if (decision.action() == CustomFishingCatchAdapter.Action.MALFORMED) {
            LOGGER.warning("[Cooking] CustomFishing catch " + lootId
                    + " had an invalid size and was left unchanged.");
            return;
        }
        if (!decision.replacesItem() || !(entity instanceof Item item)) {
            return;
        }
        int quality = FishingQuality.roll(event.getContext().arg(ContextKeys.ROD), event.getPlayer());
        ItemStack built = SeafoodWholeItems.build(item.getItemStack(), decision.mapping(), decision.sizeCm(), quality);
        if (built == null) {
            LOGGER.warning("[Cooking] CustomFishing catch " + lootId
                    + " could not be converted because seafood_whole is missing.");
            return;
        }
        item.setItemStack(built);
    }
}
