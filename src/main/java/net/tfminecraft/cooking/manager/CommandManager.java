package net.tfminecraft.cooking.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.RPCharacters.RPCharacters;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.baking.BakingTrayRecipe;
import net.tfminecraft.cooking.baking.BakingTrayRegistry;
import net.tfminecraft.cooking.baking.BakingTrayState;
import net.tfminecraft.cooking.cache.FurnitureCache;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.husbandry.HusbandryAnimal;
import net.tfminecraft.cooking.husbandry.HusbandryConfig;
import net.tfminecraft.cooking.husbandry.HusbandryEntities;
import net.tfminecraft.cooking.husbandry.HusbandryRepository;
import net.tfminecraft.cooking.husbandry.HusbandrySpawner;
import net.tfminecraft.cooking.husbandry.HusbandrySpecies;
import net.tfminecraft.cooking.heat.HeatSources;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.ModelLoader;
import net.tfminecraft.cooking.mixing.MixingBowlDisplay;
import net.tfminecraft.cooking.mixing.MixingBowlSlots;
import net.tfminecraft.cooking.nutrition.NutritionConfig;
import net.tfminecraft.cooking.nutrition.NutritionDisplayService;
import net.tfminecraft.cooking.nutrition.NutritionLog;
import net.tfminecraft.cooking.quality.CompositionContext;
import net.tfminecraft.cooking.quality.CompositionQualityResolver;
import net.tfminecraft.cooking.quality.CompositionResult;
import net.tfminecraft.cooking.quality.OriginQualityResolver;
import net.tfminecraft.cooking.utils.FoodParser;
import net.tfminecraft.cooking.utils.ItemBuilder;
import net.tfminecraft.cooking.utils.NameComposer;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.PlacedSlot;

public class CommandManager implements CommandExecutor, TabCompleter {

    private static final double MIXING_BOWL_SEARCH_RADIUS = 3.0;
    private static final double HEAT_SEARCH_RADIUS = 4.0;
    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "food", "builditem", "preview", "heat", "nametest", "qualitytest", "husbandry");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("food")) {
            return handleFood(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("husbandry")) {
            return handleHusbandry(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("builditem")) {
            return handleBuildItem(player, args);
        }

        if (args[0].equalsIgnoreCase("preview")) {
            return handlePreview(player, args);
        }

        if (args[0].equalsIgnoreCase("heat")) {
            return handleHeat(player);
        }

        if (args[0].equalsIgnoreCase("nametest")) {
            return handleNameTest(player, args);
        }

        if (args[0].equalsIgnoreCase("qualitytest")) {
            return handleQualityTest(player, args);
        }

        sendUsage(player);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        Cooking plugin = Cooking.plugin;
        if (plugin == null) {
            sender.sendMessage("§cPlugin not ready.");
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage("§aCooking reloaded ("
                + ModelLoader.get().size() + " models, "
                + FoodLoader.get().size() + " food types).");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cooking.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("food")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("husbandry")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String sub : List.of("spawn", "save")) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("husbandry")
                && args[1].equalsIgnoreCase("spawn")) {
            String prefix = args[2].toUpperCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (EntityType type : HusbandryConfig.species().keySet()) {
                if (type.name().startsWith(prefix)) {
                    out.add(type.name());
                }
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("husbandry")
                && args[1].equalsIgnoreCase("spawn")) {
            return List.of("0", "20", "1000");
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("husbandry")
                && args[1].equalsIgnoreCase("spawn")) {
            return List.of("0", "100", "200");
        }
        return Collections.emptyList();
    }

    private void sendUsage(Player player) {
        player.sendMessage("§e/cooking reload");
        player.sendMessage("§e/cooking food <player> <value>");
        player.sendMessage("§e/cooking builditem <string>");
        player.sendMessage("§e/cooking preview mixing_bowl <flour|water|yeast|dough|all|clear>");
        player.sendMessage("§e/cooking heat");
        player.sendMessage("§e/cooking nametest <foodString> [#colour]");
        player.sendMessage("§e/cooking qualitytest pickup <foodString>");
        player.sendMessage("§e/cooking qualitytest compose <foodString> [context]");
        player.sendMessage("§e/cooking qualitytest compose2 <food|food|...> [context]");
        player.sendMessage("§e/cooking husbandry spawn <type> [genetics] [care]");
        player.sendMessage("§e/cooking husbandry save");
        player.sendMessage("§7Contexts: " + formatCompositionContexts());
    }

    private boolean handleHusbandry(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cooking.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("save")) {
            return handleHusbandrySave(sender);
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("spawn")) {
            sender.sendMessage("§cUsage: /cooking husbandry spawn <type> [genetics] [care]");
            sender.sendMessage("§cUsage: /cooking husbandry save");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can spawn animals.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /cooking husbandry spawn <type> [genetics] [care]");
            return true;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cUnknown entity type.");
            return true;
        }
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species == null) {
            sender.sendMessage("§cThat type is not a husbandry species.");
            return true;
        }
        int genetics = HusbandryConfig.initialGeneticMax();
        int care = 0;
        if (args.length >= 4) {
            try {
                genetics = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cGenetics must be a number.");
                return true;
            }
        }
        if (args.length >= 5) {
            try {
                care = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cCare must be a number.");
                return true;
            }
        }
        LivingEntity spawned = HusbandrySpawner.spawn(player, type, genetics, care);
        if (spawned == null) {
            sender.sendMessage("§cFailed to spawn animal.");
            return true;
        }
        sender.sendMessage("§aSpawned " + spawned.getName()
                + " genetics " + Math.max(0, Math.min(HusbandryConfig.maxGenetics(), genetics))
                + " care " + Math.max(0, Math.min(HusbandryConfig.careMax(), care)) + ".");
        return true;
    }

    private boolean handleHusbandrySave(CommandSender sender) {
        HusbandryRepository repository = Cooking.plugin == null ? null : Cooking.plugin.getHusbandryRepository();
        if (repository == null) {
            sender.sendMessage("§cHusbandry database is not open.");
            return true;
        }
        List<HusbandryAnimal> loaded = HusbandryEntities.snapshotLoaded();
        if (!loaded.isEmpty()) {
            repository.upsertAnimals(loaded);
        }
        repository.checkpointWal(false);
        sender.sendMessage("§aSaved " + loaded.size() + " loaded animals.");
        return true;
    }

    private boolean handleFood(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /cooking food <player> <value>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cThat player is not online.");
            return true;
        }
        RPCharacter character = RPCharacters.getActiveCharacter(target);
        if (character == null) {
            sender.sendMessage("§cThat player has no active character.");
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cFood must be a whole number.");
            return true;
        }
        if (value < 0 || value > NutritionConfig.maxFood()) {
            sender.sendMessage("§cFood must be between 0 and " + NutritionConfig.maxFood() + ".");
            return true;
        }
        int before = character.getFoodValue();
        character.setFoodValue(value);
        NutritionLog.append("ADMIN_SET", target, character,
                "sender=" + sender.getName()
                + " foodBefore=" + before
                + " requested=" + value
                + " foodAfter=" + character.getFoodValue()
                + " mappedBefore=" + NutritionDisplayService.toFoodLevel(before)
                + " mappedAfter=" + NutritionDisplayService.toFoodLevel(character.getFoodValue())
                + " hudBefore=" + target.getFoodLevel()
                + " saturation=" + target.getSaturation());
        NutritionDisplayService.sync(target, character, "admin");
        RPCharacters.getPlayerManager().savePlayer(target);
        NutritionLog.append("SAVE", target, character,
                "reason=admin sender=" + sender.getName());
        sender.sendMessage("§aSet " + target.getName() + "'s food from " + before + " to "
                + character.getFoodValue() + ".");
        if (!sender.equals(target)) {
            target.sendMessage("§eYour food was set to " + character.getFoodValue() + " by "
                    + sender.getName() + ".");
        }
        return true;
    }

    private static String formatCompositionContexts() {
        return Arrays.stream(CompositionContext.values())
                .map(CompositionContext::name)
                .collect(Collectors.joining(", "));
    }

    private boolean handleQualityTest(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /cooking qualitytest <pickup|compose|compose2> ...");
            player.sendMessage("§7Contexts: " + formatCompositionContexts());
            return true;
        }

        if (args[1].equalsIgnoreCase("compose2")) {
            return handleCompose2(player, args);
        }

        StringBuilder builder = new StringBuilder();
        int end = args.length;
        CompositionContext context = CompositionContext.CUTTING_BOARD;

        if (args[1].equalsIgnoreCase("compose") && args.length >= 4) {
            try {
                context = CompositionContext.valueOf(args[end - 1].toUpperCase());
                end--;
            } catch (IllegalArgumentException ignored) {
            }
        }

        for (int i = 2; i < end; i++) {
            if (i > 2) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }

        FoodParser.Result result = FoodParser.parse(builder.toString());
        if (result == null || result.template == null) {
            player.sendMessage("§cFailed to parse food string.");
            return true;
        }

        if (args[1].equalsIgnoreCase("pickup")) {
            int quality = OriginQualityResolver.resolve(player, result.template);
            player.sendMessage("§aPickup quality: §f" + quality);
            return true;
        }

        if (args[1].equalsIgnoreCase("compose")) {
            int quality = CompositionQualityResolver.resolve(player, List.of(result.template), context);
            player.sendMessage("§aComposition quality (" + context.name() + "): §f" + quality);
            return true;
        }

        player.sendMessage("§cUsage: /cooking qualitytest <pickup|compose|compose2> ...");
        return true;
    }

    private boolean handleCompose2(Player player, String[] args) {
        int end = args.length;
        CompositionContext context = CompositionContext.CUTTING_BOARD;

        if (args.length >= 4) {
            try {
                context = CompositionContext.valueOf(args[end - 1].toUpperCase());
                end--;
            } catch (IllegalArgumentException ignored) {
            }
        }

        StringBuilder joined = new StringBuilder();
        for (int i = 2; i < end; i++) {
            if (i > 2) {
                joined.append(' ');
            }
            joined.append(args[i]);
        }

        String[] parts = joined.toString().split("\\|");
        List<FoodItem> inputs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            FoodParser.Result result = FoodParser.parse(trimmed);
            if (result == null || result.template == null) {
                player.sendMessage("§cFailed to parse food string: " + trimmed);
                return true;
            }
            inputs.add(result.template);
        }

        if (inputs.isEmpty()) {
            player.sendMessage("§cUsage: /cooking qualitytest compose2 <food|food|...> [context]");
            return true;
        }

        CompositionResult composed = CompositionQualityResolver.compose(player, inputs, context);
        player.sendMessage("§aComposition (" + context.name() + ")");
        player.sendMessage("§7Baseline: §f" + composed.getBaselineQuality() + " §7→ Final: §f" + composed.getFinalQuality());
        player.sendMessage("§7Mains (" + composed.getMains().size() + "): §f" + formatFoodList(composed.getMains()));
        player.sendMessage("§7Extras (" + composed.getExtras().size() + "): §f" + formatFoodList(composed.getExtras()));
        player.sendMessage("§7Neutral (" + composed.getNeutral().size() + "): §f" + formatFoodList(composed.getNeutral()));
        player.sendMessage("§7Lineage mains: §f" + formatOrigins(composed.getLineage().mains()));
        player.sendMessage("§7Lineage extras: §f" + formatOrigins(composed.getLineage().extras()));
        if (!composed.getFreshnessTracks().isEmpty()) {
            StringBuilder freshness = new StringBuilder();
            for (Map.Entry<String, Integer> entry : composed.getFreshnessTracks().entrySet()) {
                if (freshness.length() > 0) {
                    freshness.append(", ");
                }
                freshness.append(entry.getKey()).append('=').append(entry.getValue());
            }
            player.sendMessage("§7Freshness tracks: §f" + freshness);
        }
        return true;
    }

    private static String formatFoodList(List<FoodItem> items) {
        if (items.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            FoodItem item = items.get(i);
            builder.append(item.getId())
                    .append("(q=")
                    .append(item.getQualityMin())
                    .append(", cat=")
                    .append(item.getCategory())
                    .append(')');
        }
        return builder.toString();
    }

    private static String formatOrigins(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            return "-";
        }
        return String.join(", ", origins);
    }

    private boolean handleNameTest(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /cooking nametest <foodString> [#colour]");
            return true;
        }

        String colour = null;
        int end = args.length;
        if (args[end - 1].startsWith("#")) {
            colour = args[end - 1];
            end--;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < end; i++) {
            if (i > 1) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }

        FoodParser.Result result = FoodParser.parse(builder.toString());
        if (result == null || result.template == null) {
            player.sendMessage("§cFailed to parse food string.");
            return true;
        }

        Map<String, String> extras = new HashMap<>();
        if (colour != null) {
            extras.put("colour", colour);
        }

        String composed = NameComposer.compose(result.template, extras);
        player.sendMessage("§aComposed name: §f" + composed);
        return true;
    }

    private boolean handleBuildItem(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /cooking builditem <itemString>");
            return true;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) builder.append(" ");
            builder.append(args[i]);
        }
        String itemString = builder.toString();
        ItemBuilder.buildFromString(player, itemString, null);

        player.sendMessage("§aGenerated item(s) from string!");
        return true;
    }

    private boolean handlePreview(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /cooking preview <target> ...");
            player.sendMessage("§cAvailable targets: mixing_bowl");
            return true;
        }

        if (args[1].equalsIgnoreCase("mixing_bowl")) {
            return handleMixingBowlPreview(player, args);
        }

        player.sendMessage("§cUnknown preview target. Available: mixing_bowl");
        return true;
    }

    private boolean handleMixingBowlPreview(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /cooking preview mixing_bowl <flour|water|yeast|dough|all|clear>");
            return true;
        }

        Furniture furniture = findNearestMixingBowl(player);
        if (furniture == null) {
            player.sendMessage("§cNo mixing bowl found within " + MIXING_BOWL_SEARCH_RADIUS + " blocks.");
            return true;
        }

        String action = args[2].toLowerCase();
        if (action.equals("clear")) {
            clearMixingPreview(furniture);
            player.sendMessage("§aCleared mixing bowl preview slots.");
            return true;
        }

        if (action.equals("all")) {
            previewSlot(furniture, MixingBowlSlots.FLOUR);
            previewSlot(furniture, MixingBowlSlots.WATER);
            previewSlot(furniture, MixingBowlSlots.YEAST);
            player.sendMessage("§aPreviewing flour, water, and yeast on the nearest mixing bowl.");
            return true;
        }

        if (!isPreviewSlot(action)) {
            player.sendMessage("§cUnknown slot. Use flour, water, yeast, dough, all, or clear.");
            return true;
        }

        if (!previewSlot(furniture, action)) {
            player.sendMessage("§cNo display model configured for §f" + action + "§c.");
            return true;
        }

        player.sendMessage("§aPreviewing §f" + action + " §aon the nearest mixing bowl.");
        return true;
    }

    private boolean isPreviewSlot(String action) {
        return action.equals(MixingBowlSlots.FLOUR)
                || action.equals(MixingBowlSlots.WATER)
                || action.equals(MixingBowlSlots.YEAST)
                || action.equals(MixingBowlSlots.DOUGH);
    }

    private Furniture findNearestMixingBowl(Player player) {
        Furniture nearest = null;
        double nearestDistance = MIXING_BOWL_SEARCH_RADIUS;

        for (Map.Entry<UUID, Furniture> entry : InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture()
                .entrySet()) {
            Furniture furniture = entry.getValue();
            if (furniture.isCarried() || !FurnitureCache.isMixingBowl(furniture)) {
                continue;
            }

            if (furniture.getType() == null) {
                continue;
            }

            double distance = furniture.getLoc().distance(player.getLocation());
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = furniture;
            }
        }

        return nearest;
    }

    private void clearMixingPreview(Furniture furniture) {
        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.FLOUR);
        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.WATER);
        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.YEAST);
        MixingBowlDisplay.clearLayer(furniture, MixingBowlSlots.DOUGH);
    }

    private boolean previewSlot(Furniture furniture, String slotKey) {
        return MixingBowlDisplay.showLayer(furniture, slotKey);
    }

    private boolean handleHeat(Player player) {
        Furniture furniture = findNearestFurniture(player, HEAT_SEARCH_RADIUS);
        if (furniture == null) {
            player.sendMessage("§cNo furniture found within " + HEAT_SEARCH_RADIUS + " blocks.");
            return true;
        }

        player.sendMessage("§6--- Heat debug ---");
        player.sendMessage("§7Furniture: §f" + furniture.getId());
        player.sendMessage("§7UUID: §f" + furniture.getEntityId());
        player.sendMessage("§7isSource: §f" + HeatSources.isSource(furniture));
        player.sendMessage("§7isConsumer: §f" + HeatSources.isConsumer(furniture));

        if (HeatSources.isSource(furniture)) {
            player.sendMessage("§7hasHeat: §f" + HeatSources.hasHeat(furniture));
        }

        if (HeatSources.isConsumer(furniture)) {
            var source = HeatSources.findSource(furniture);
            player.sendMessage("§7findSource: §f"
                    + (source.isPresent() ? source.get().getId() + " (" + source.get().getEntityId() + ")" : "none"));
            player.sendMessage("§7consumerHasHeat: §f" + HeatSources.consumerHasHeat(furniture));
            appendBakeDebug(player, furniture);
        }

        return true;
    }

    private void appendBakeDebug(Player player, Furniture consumer) {
        for (PlacedFurnitureSlot slot : consumer.getActiveFurnitureSlots().values()) {
            Furniture tray = slot.getNested();
            if (tray == null || !BakingTrayRegistry.isTray(tray)) {
                continue;
            }

            BakingTrayRecipe recipe = BakingTrayRegistry.getByFurniture(tray);
            player.sendMessage("§6--- Bake debug ---");
            player.sendMessage("§7nestedTray: §f" + tray.getId());
            player.sendMessage("§7recipe: §f" + (recipe != null ? recipe.getId() : "none"));
            if (recipe != null) {
                player.sendMessage("§7cook-seconds: §f" + recipe.getBake().getCookSeconds());
                player.sendMessage("§7burn-seconds: §f" + recipe.getBake().getBurnSeconds());
                for (String slotId : recipe.getAllSlotIds()) {
                    if (!tray.hasActiveSlot(slotId)) {
                        continue;
                    }
                    PlacedSlot placedSlot = tray.getActiveSlot(slotId).orElse(null);
                    if (placedSlot == null) {
                        continue;
                    }
                    var item = placedSlot.getCurrentItem();
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    FoodItem foodItem = FoodItem.fromItem(item);
                    if (foodItem == null) {
                        continue;
                    }
                    TagTrack cooked = foodItem.getTagTrack("cooked");
                    int cookedValue = cooked != null ? cooked.getValue() : -1;
                    player.sendMessage("§7" + slotId + ": §felapsed="
                            + BakingTrayState.getSlotElapsed(tray, slotId)
                            + " cooked=" + cookedValue);
                }
            }
            return;
        }
    }

    private Furniture findNearestFurniture(Player player, double radius) {
        Furniture nearest = null;
        double nearestDistance = radius;

        for (Map.Entry<UUID, Furniture> entry : InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture()
                .entrySet()) {
            Furniture furniture = entry.getValue();
            if (furniture.isCarried() || furniture.isAttached()) {
                continue;
            }

            double distance = furniture.getLoc().distance(player.getLocation());
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = furniture;
            }
        }

        return nearest;
    }
}
