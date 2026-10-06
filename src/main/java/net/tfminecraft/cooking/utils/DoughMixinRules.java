package net.tfminecraft.cooking.utils;

import java.util.List;

import org.bukkit.entity.Player;

import net.tfminecraft.cooking.cache.NamingConfig;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;

public final class DoughMixinRules {
    private DoughMixinRules() {}

    public static boolean isSweetenerCategory(String category) {
        return NamingConfig.isCategory("sweetener", category);
    }

    public static boolean isFruitCategory(String category) {
        return NamingConfig.isCategory("dough-filler", category);
    }

    public static boolean canAcceptSugar(boolean hasSugar, FoodItem incoming, Player player) {
        if (incoming == null || !isSweetenerCategory(incoming.getCategory())) {
            return false;
        }
        if (hasSugar) {
            if (player != null) {
                player.sendMessage("§cSugar is already in the bowl.");
            }
            return false;
        }
        return true;
    }

    public static boolean canAcceptFruit(List<String> fruitOrigins, FoodItem incoming, Player player) {
        if (incoming == null || !isFruitCategory(incoming.getCategory())) {
            return false;
        }
        String origin = incoming.getOrigin();
        if (origin != null && !origin.isBlank()) {
            for (String existing : fruitOrigins) {
                if (existing != null && existing.equalsIgnoreCase(origin)) {
                    if (player != null) {
                        player.sendMessage("§cThat fruit is already in the bowl.");
                    }
                    return false;
                }
            }
        }
        if (fruitOrigins.size() >= NamingConfig.getDoughFillerMax()) {
            if (player != null) {
                player.sendMessage("§cYou can't add more fruit.");
            }
            return false;
        }
        return true;
    }

    public static void applyDoughTags(FoodItem dough, boolean hasSugar, List<String> fruitOrigins) {
        if (hasSugar) {
            TagTrack sweet = TrackLoader.getByString("sweet");
            if (sweet != null) {
                sweet = new TagTrack(sweet);
                sweet.setValue(0);
                dough.addOrModifyTrack(sweet);
            }
        }

        int fruitCount = fruitOrigins == null ? 0 : fruitOrigins.size();
        if (fruitCount >= 2) {
            TagTrack richlyFruity = TrackLoader.getByString("richly_fruity");
            if (richlyFruity != null) {
                richlyFruity = new TagTrack(richlyFruity);
                richlyFruity.setValue(0);
                dough.addOrModifyTrack(richlyFruity);
            }
        } else if (fruitCount == 1) {
            TagTrack fruity = TrackLoader.getByString("fruity");
            if (fruity != null) {
                fruity = new TagTrack(fruity);
                fruity.setValue(0);
                dough.addOrModifyTrack(fruity);
            }
        }
    }
}
