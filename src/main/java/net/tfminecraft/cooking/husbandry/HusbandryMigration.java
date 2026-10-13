package net.tfminecraft.cooking.husbandry;

import org.bukkit.plugin.Plugin;
import net.tfminecraft.tlibs.character.Characters;

/** Kept out of the old-API classloading path. Every pass uses the same archived plan. */
final class HusbandryMigration {
    private HusbandryMigration() {}
    static void start(Plugin plugin, HusbandryRepository repository) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> reconcile(plugin, repository), 1, 20L * 60 * 60);
    }
    static void reconcile(Plugin plugin, HusbandryRepository repository) {
        try {
            if (!repository.migrateOwners(Characters::legacyHeirsReady, Characters::legacyHeir)) {
                plugin.getLogger().warning("Animal ownership waits for reviewed heirs; legacy slots remain held.");
            } else if (repository.heldOwners() != 0) {
                plugin.getLogger().warning("Animal ownership holds " + repository.heldOwners()
                        + " legacy slots for staff; see husbandry_owner_plan in Data/husbandry.db.");
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Animal ownership migration rolled back; retrying: " + ex.getMessage());
        }
    }
}
