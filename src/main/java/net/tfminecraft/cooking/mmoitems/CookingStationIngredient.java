package net.tfminecraft.cooking.mmoitems;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.lumine.mythic.lib.api.MMOLineConfig;
import net.Indyuce.mmoitems.api.crafting.ingredient.Ingredient;
import net.Indyuce.mmoitems.api.player.RPGPlayer;
import net.tfminecraft.cooking.item.CookingPathHandler;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;

/**
 * {@code cooking{item="seafood(type=seafood_whole;origin=Salmon)",amount=64,display="Salmon"}}.
 * {@code item} is a Cooking path without the {@code c.} prefix, matched like every other
 * {@code c.} path. It must name a food type: MMOItems hands that food back when a queued craft
 * is cancelled.
 */
public class CookingStationIngredient extends Ingredient<CookingStationPlayerIngredient> {
    private final String path;
    private final Map<String, String> fields;
    private final String display;

    public CookingStationIngredient(MMOLineConfig config) {
        super(MMOItemsIngredients.ID, config);
        config.validate("item");
        path = CookingPathHandler.stripPrefix(config.getString("item"));
        int paren = path.indexOf('(');
        fields = paren < 0 || !path.endsWith(")") ? Map.of()
                : FoodParser.extractFields(path.substring(paren + 1, path.length() - 1));
        String type = field("type");
        if (type == null) {
            throw new IllegalArgumentException("cooking ingredient item=\"" + path
                    + "\" needs a food type, e.g. seafood(type=seafood_whole;origin=Salmon)");
        }
        String origin = field("origin");
        display = config.getString("display", origin != null ? origin : words(type));
    }

    private String field(String key) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey().trim().equalsIgnoreCase(key) && !entry.getValue().isBlank()) {
                return entry.getValue().trim();
            }
        }
        return null;
    }

    private static String words(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    public String getPath() {
        return path;
    }

    public String getDisplayName() {
        return display;
    }

    @Override
    public String formatDisplay(String format) {
        return format.replace("#item#", display).replace("#amount#", String.valueOf(getAmount()));
    }

    @Override
    public boolean matches(CookingStationPlayerIngredient ingredient) {
        return CookingPathHandler.matches(ingredient.getFood(), path);
    }

    /**
     * The preview shows the food fresh. A refund (forDisplay false) cannot know what was spent,
     * so it is the least the line accepts: its lowest quality, aged out on every track the line
     * leaves open. Cancelling never improves food.
     */
    @Override
    public ItemStack generateItemStack(RPGPlayer player, boolean forDisplay) {
        FoodParser.Result parsed = FoodParser.parse(path);
        ItemStack stack = null;
        if (parsed != null && parsed.template != null) {
            FoodItem food = parsed.template;
            if (!forDisplay) {
                ageOpenTracks(food);
            }
            stack = ItemBuilder.buildSingleWithQuality(food, StationFood.plainSource(path),
                    parsed.explicitQuality ? food._parsedQualMin : StationFood.PLAIN_QUALITY);
        }
        if (stack == null) {
            // Cooking does not know this food type (yet).
            stack = placeholder();
        }
        stack.setAmount(forDisplay ? Math.max(1, Math.min(getAmount(), stack.getMaxStackSize())) : getAmount());
        return stack;
    }

    private void ageOpenTracks(FoodItem food) {
        Set<String> pinned = pinnedTracks();
        for (TagTrack track : food.getTagTracks()) {
            List<TagStep> steps = track.getSteps();
            if (track.isAgeable() && !steps.isEmpty() && !pinned.contains(track.getId().toLowerCase(Locale.ROOT))) {
                track.forceSetValue((int) steps.get(steps.size() - 1).getRequiredValue());
            }
        }
    }

    private Set<String> pinnedTracks() {
        Set<String> pinned = new HashSet<>();
        String tags = field("tags");
        if (tags != null) {
            for (String tag : tags.split("[,:]")) {
                String id = tag.split("\\.", 2)[0].trim();
                pinned.add(AgeScale.migrateTrackId(id).toLowerCase(Locale.ROOT));
            }
        }
        return pinned;
    }

    @SuppressWarnings("deprecation")
    private ItemStack placeholder() {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(display);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public String toString() {
        return "cooking{item=\"" + path + "\",amount=" + getAmount() + "}";
    }
}
