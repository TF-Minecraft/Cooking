package net.tfminecraft.cooking.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;

public class ItemRef {

    /*
     * Built ItemsAdder templates, one per ref, so the inventory scan's food rebuilds stop asking ItemsAdder
     * for a fresh copy every time. Callers only ever get clones. Only refs that TLibs sends to ItemsAdder
     * are kept: v. and modeled. builds are cheap, m. builds can roll random MMOItems stats, magic. has no
     * creation path, and a prefix with a registered TLibs path handler (Cooking's own c., for one) may
     * build something different each time. Failures (no item, air, or TLibs' dirt fallback when ItemsAdder
     * is missing or has not loaded the item yet) are never kept, so they are retried like before.
     * Only on while Cooking listens for ItemsAdder data loads (see ItemsAdderReloadHook). Cleared on
     * /cooking reload, on every ItemsAdder data load, and when TLibs hands out a new creator.
     */
    private static final Set<String> UNCACHED_PREFIXES = Set.of("v", "m", "modeled", "magic");
    private static volatile boolean caching;
    private static volatile Templates templates;

    private record Templates(ItemCreator creator, Map<String, ItemStack> items) {}

    public static ItemStack resolve(String ref) {
        ItemCreator creator = TLibs.getItemAPI().getCreator();
        Templates cache = cacheFor(ref, creator);
        ItemStack cached = cache == null ? null : cache.items().get(ref);
        if (cached != null) return cached.clone();
        return build(ref, creator, cache);
    }

    public static ItemStack apply(String ref, ItemStack source) {
        ItemCreator creator = TLibs.getItemAPI().getCreator();
        Templates cache = cacheFor(ref, creator);
        ItemStack template = cache == null ? null : cache.items().get(ref);
        if (template == null) template = build(ref, creator, cache);
        if (template == null) return null;
        ItemStack out = template.clone();
        mergeMeta(out, source);
        return out;
    }

    /** Drops every kept template; the next use of each ref builds it again. */
    public static void clearCache() {
        templates = null;
    }

    /** Turns template keeping on or off; either way the kept templates are dropped. */
    public static void setCaching(boolean enabled) {
        caching = enabled;
        templates = null;
    }

    private static ItemStack build(String ref, ItemCreator creator, Templates cache) {
        ItemStack built = creator.getItemFromPath(ref);
        if (cache != null && isTemplate(built)) {
            cache.items().putIfAbsent(ref, built.clone());
        }
        return built;
    }

    private static boolean isTemplate(ItemStack built) {
        return built != null && !built.getType().isAir() && built.getType() != Material.DIRT;
    }

    /** The template cache for this ref, or null when the ref is not built from ItemsAdder. */
    private static Templates cacheFor(String ref, ItemCreator creator) {
        if (!caching || creator == null || !isItemsAdderRef(ref)) return null;
        Templates current = templates;
        if (current == null || current.creator() != creator) {
            current = new Templates(creator, new ConcurrentHashMap<>());
            templates = current;
        }
        return current;
    }

    // Mirrors the prefix routing in TLibs ItemCreator.getItemFromPath: everything else goes to ItemsAdder.
    private static boolean isItemsAdderRef(String ref) {
        if (ref == null) return false;
        int dot = ref.indexOf('.');
        if (dot <= 0 || dot == ref.length() - 1) return false;
        String prefix = ref.substring(0, dot).toLowerCase(Locale.ROOT);
        if (UNCACHED_PREFIXES.contains(prefix)) return false;
        ItemAPI api = TLibs.getItemAPI();
        return api.getPathHandler(prefix) == null;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void mergeMeta(ItemStack out, ItemStack source) {
        if (source == null) return;
        out.setAmount(source.getAmount());
        ItemMeta srcMeta = source.getItemMeta();
        if (srcMeta == null) return;
        ItemMeta outMeta = out.getItemMeta();
        if (outMeta == null) return;

        if (srcMeta.hasDisplayName()) outMeta.setDisplayName(srcMeta.getDisplayName());
        if (srcMeta.hasLore()) outMeta.setLore(srcMeta.getLore());

        PersistentDataContainer srcPdc = srcMeta.getPersistentDataContainer();
        PersistentDataContainer outPdc = outMeta.getPersistentDataContainer();
        srcPdc.copyTo(outPdc, true);

        out.setItemMeta(outMeta);
    }
}
