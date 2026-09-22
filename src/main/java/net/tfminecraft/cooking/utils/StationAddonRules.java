package net.tfminecraft.cooking.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;

public final class StationAddonRules {
    public enum AddonProfile {
        NONE,
        SINGLE,
        AROMATIC,
        ROUNDED
    }

    private StationAddonRules() {}

    public static boolean isAddonCategory(String category) {
        return NamingConfig.isCategory("addon", category);
    }

    public static boolean isSeasoningCategory(String category) {
        return NamingConfig.isCategory("seasoning", category);
    }

    public static boolean isSweetenerCategory(String category) {
        return NamingConfig.isCategory("sweetener", category);
    }

    public static boolean hasSweetener(Map<String, FoodItem> slots) {
        for (FoodItem item : slots.values()) {
            if (item != null && isSweetenerCategory(item.getCategory())) {
                return true;
            }
        }
        return false;
    }

    public static boolean canAcceptSweetener(Map<String, FoodItem> slots, FoodItem incoming, Player player) {
        if (incoming == null || !isSweetenerCategory(incoming.getCategory())) {
            return false;
        }
        if (hasSweetener(slots)) {
            if (player != null) {
                player.sendMessage("§cSugar is already in the pan.");
            }
            return false;
        }
        return true;
    }

    public static void applySweetTag(FoodItem product, Map<String, FoodItem> slots) {
        if (!hasSweetener(slots)) {
            return;
        }
        TagTrack track = new TagTrack(TrackLoader.getByString("sweet"));
        if (track == null) {
            return;
        }
        track.setValue(0);
        product.addOrModifyTrack(track);
    }

    public static boolean hasDuplicateOrigin(Map<String, FoodItem> slots, FoodItem incoming) {
        String origin = incoming.getOrigin();
        if (origin == null || origin.isBlank()) {
            return false;
        }
        for (FoodItem existing : slots.values()) {
            if (existing == null || existing.getOrigin() == null) {
                continue;
            }
            if (existing.getOrigin().equalsIgnoreCase(origin)) {
                return true;
            }
        }
        return false;
    }

    public static int countAddons(Map<String, FoodItem> slots) {
        int count = 0;
        for (FoodItem item : slots.values()) {
            if (item != null && isAddonCategory(item.getCategory())) {
                count++;
            }
        }
        return count;
    }

    public static boolean canAcceptAddon(Map<String, FoodItem> slots, FoodItem incoming, Player player) {
        if (incoming == null || !isAddonCategory(incoming.getCategory())) {
            return false;
        }
        if (hasDuplicateOrigin(slots, incoming)) {
            if (player != null) {
                player.sendMessage("§cThat ingredient is already in the station.");
            }
            return false;
        }
        if (countAddons(slots) >= NamingConfig.getAddonMax()) {
            if (player != null) {
                player.sendMessage("§cYou can't add more garnish or spice.");
            }
            return false;
        }
        return true;
    }

    public static AddonProfile classifyAddons(Map<String, FoodItem> slots) {
        List<FoodItem> addons = new ArrayList<>();
        for (FoodItem item : slots.values()) {
            if (item != null && isAddonCategory(item.getCategory())) {
                addons.add(item);
            }
        }
        if (addons.size() < 2) {
            return addons.isEmpty() ? AddonProfile.NONE : AddonProfile.SINGLE;
        }

        boolean hasGarnish = false;
        boolean hasSpice = false;
        for (FoodItem item : addons) {
            String category = item.getCategory().toLowerCase();
            if (category.equals("garnish")) {
                hasGarnish = true;
            }
            if (category.equals("spice")) {
                hasSpice = true;
            }
        }
        if (hasGarnish && hasSpice) {
            return AddonProfile.ROUNDED;
        }
        return AddonProfile.AROMATIC;
    }

    public static boolean hasValuable(Map<String, FoodItem> slots) {
        if (slots == null) {
            return false;
        }
        for (FoodItem item : slots.values()) {
            if (item != null && item.isValuable()) {
                return true;
            }
        }
        return false;
    }

    public static void applyFlavourfulTag(FoodItem product, Map<String, FoodItem> slots) {
        if (product == null || !hasValuable(slots)) {
            return;
        }
        TagTrack track = new TagTrack(TrackLoader.getByString("flavourful"));
        if (track == null) {
            return;
        }
        track.setValue(0);
        product.addOrModifyTrack(track);
    }

    public static void applyAddonTags(FoodItem product, Map<String, FoodItem> slots) {
        AddonProfile profile = classifyAddons(slots);
        if (profile == AddonProfile.AROMATIC) {
            TagTrack track = new TagTrack(TrackLoader.getByString("aromatic"));
            if (track != null) {
                track.setValue(0);
                product.addOrModifyTrack(track);
            }
        } else if (profile == AddonProfile.ROUNDED) {
            TagTrack track = new TagTrack(TrackLoader.getByString("rounded"));
            if (track != null) {
                track.setValue(0);
                product.addOrModifyTrack(track);
            }
        }
    }

    public static int countSeasoningPieces(Map<String, FoodItem> slots) {
        int count = 0;
        for (FoodItem item : slots.values()) {
            if (item != null && isSeasoningCategory(item.getCategory())) {
                count++;
            }
        }
        return count;
    }

    public static void applySeasoningTag(FoodItem product, Map<String, FoodItem> slots) {
        int count = countSeasoningPieces(slots);
        if (count == 0) {
            return;
        }
        TagTrack track = new TagTrack(TrackLoader.getByString("seasoning"));
        if (track == null) {
            return;
        }
        track.setValue(count >= 2 ? 1 : 0);
        product.addOrModifyTrack(track);
    }
}
