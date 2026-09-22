package net.tfminecraft.cooking.fishing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class CustomFishingCatalog {
    public static final String WHOLE_TYPE = "seafood_whole";
    private static final Set<String> CUTS = Set.of("fish", "octopus", "jellyfish");
    private static final Logger LOGGER = Logger.getLogger("Cooking");

    private static Map<String, CatchMapping> catches = Map.of();
    private static Map<Integer, String> models = Map.of();
    private static Map<String, RodBand> rods = Map.of();
    private static Map<String, VanillaFish> vanilla = Map.of();
    private static Map<String, CutRule> cutting = Map.of();
    private static RodBand unknownRod = new RodBand(1, 5);
    private static int legacySizeCm = 45;

    private CustomFishingCatalog() {}

    public static void load(File file) {
        String text = "";
        try {
            if (file != null && file.isFile()) {
                text = Files.readString(file.toPath());
            }
        } catch (IOException exception) {
            LOGGER.warning("[Cooking] Failed to load custom-fishing.yml: " + exception.getMessage());
            replace(Map.of(), Map.of(), new RodBand(1, 5), Map.of(), Map.of(), Map.of(), 45);
            return;
        }
        Node root = parse(text);
        CatchLoad loadedCatches = readCatches(root.child("catches"));
        replace(loadedCatches.catches(),
                readRods(root.child("rods")),
                readBand(root.child("unknown-rod"), new RodBand(1, 5), "unknown-rod"),
                readVanilla(root.child("vanilla")),
                readCutting(root.child("cutting")),
                loadedCatches.models(),
                readLegacySize(root));
    }

    public static void replace(Map<String, CatchMapping> nextCatches, Map<String, RodBand> nextRods, RodBand nextUnknown) {
        replace(nextCatches, nextRods, nextUnknown, Map.of(), Map.of());
    }

    public static void replace(Map<String, CatchMapping> nextCatches, Map<String, RodBand> nextRods,
            RodBand nextUnknown, Map<String, VanillaFish> nextVanilla) {
        replace(nextCatches, nextRods, nextUnknown, nextVanilla, Map.of());
    }

    public static void replace(Map<String, CatchMapping> nextCatches, Map<String, RodBand> nextRods,
            RodBand nextUnknown, Map<String, VanillaFish> nextVanilla, Map<String, CutRule> nextCutting) {
        replace(nextCatches, nextRods, nextUnknown, nextVanilla, nextCutting, Map.of(), 45);
    }

    public static void replace(Map<String, CatchMapping> nextCatches, Map<String, RodBand> nextRods,
            RodBand nextUnknown, Map<String, VanillaFish> nextVanilla, Map<String, CutRule> nextCutting,
            Map<Integer, String> nextModels, int nextLegacySizeCm) {
        catches = Map.copyOf(nextCatches == null ? Map.of() : nextCatches);
        models = Map.copyOf(nextModels == null ? Map.of() : nextModels);
        rods = Map.copyOf(nextRods == null ? Map.of() : nextRods);
        vanilla = Map.copyOf(nextVanilla == null ? Map.of() : nextVanilla);
        cutting = Map.copyOf(nextCutting == null ? Map.of() : nextCutting);
        unknownRod = nextUnknown == null ? new RodBand(1, 5) : nextUnknown;
        legacySizeCm = nextLegacySizeCm >= 1 ? nextLegacySizeCm : 45;
    }

    public static CatchMapping find(String lootId) {
        if (lootId == null || lootId.isBlank()) {
            return null;
        }
        return catches.get(lootId.toLowerCase(Locale.ROOT));
    }

    public static CatchMapping findModel(int modelData) {
        String id = models.get(modelData);
        return id == null ? null : catches.get(id);
    }

    public static int legacySizeCm() {
        return legacySizeCm;
    }

    public static Collection<CatchMapping> catches() {
        return catches.values();
    }

    public static RodBand band(String rodId) {
        if (rodId == null || rodId.isBlank()) {
            return unknownRod;
        }
        RodBand band = rods.get(rodId.toLowerCase(Locale.ROOT));
        return band == null ? unknownRod : band;
    }

    public static RodBand unknownRod() {
        return unknownRod;
    }

    public static VanillaFish findVanilla(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }
        return vanilla.get(material.toUpperCase(Locale.ROOT));
    }

    public static Collection<VanillaFish> vanillaFish() {
        return vanilla.values();
    }

    public static CutRule cutRule(String cut) {
        if (cut == null || cut.isBlank()) {
            return null;
        }
        return cutting.get(cut.toLowerCase(Locale.ROOT));
    }

    private static CatchLoad readCatches(Node section) {
        Map<String, CatchMapping> loaded = new LinkedHashMap<>();
        Map<Integer, String> loadedModels = new LinkedHashMap<>();
        if (section == null) {
            return new CatchLoad(loaded, loadedModels);
        }
        for (Map.Entry<String, Node> entry : section.children.entrySet()) {
            String origin = entry.getValue().scalar("origin");
            String cut = entry.getValue().scalar("cut").toLowerCase(Locale.ROOT);
            if (origin.isBlank() || !CUTS.contains(cut)) {
                LOGGER.warning("[Cooking] CustomFishing catch " + entry.getKey() + " is malformed and was skipped.");
                continue;
            }
            String id = entry.getKey().toLowerCase(Locale.ROOT);
            loaded.put(id, new CatchMapping(id, origin, cut));
            readModelData(id, entry.getValue().scalar("model-data"), loadedModels);
        }
        return new CatchLoad(loaded, loadedModels);
    }

    private static void readModelData(String catchId, String raw, Map<Integer, String> loadedModels) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            Integer model = integer(part);
            if (model == null) {
                LOGGER.warning("[Cooking] CustomFishing catch " + catchId + " has a malformed model data value.");
                continue;
            }
            String previous = loadedModels.put(model, catchId);
            if (previous != null && !previous.equals(catchId)) {
                LOGGER.warning("[Cooking] Custom model data " + model + " is used by both " + previous
                        + " and " + catchId + ".");
            }
        }
    }

    private static int readLegacySize(Node root) {
        Node node = root.child("legacy-size-cm");
        if (node == null || node.value == null || node.value.isBlank()) {
            return 45;
        }
        Integer size = CatchSize.roundCm(decimal(node.value));
        if (size == null) {
            LOGGER.warning("[Cooking] legacy-size-cm is malformed and fell back to 45.");
            return 45;
        }
        return size;
    }

    private static Map<String, RodBand> readRods(Node section) {
        Map<String, RodBand> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (Map.Entry<String, Node> entry : section.children.entrySet()) {
            RodBand band = readBand(entry.getValue(), null, entry.getKey());
            if (band != null) {
                loaded.put(entry.getKey().toLowerCase(Locale.ROOT), band);
            }
        }
        return loaded;
    }

    private static RodBand readBand(Node section, RodBand fallback, String label) {
        if (section == null) {
            return fallback;
        }
        Integer min = integer(section.scalar("min"));
        Integer max = integer(section.scalar("max"));
        if (min == null || max == null || min < 1 || max > 5 || min > max) {
            LOGGER.warning("[Cooking] CustomFishing rod band " + label + " is malformed and was skipped.");
            return fallback;
        }
        return new RodBand(min, max);
    }

    private static Map<String, VanillaFish> readVanilla(Node section) {
        Map<String, VanillaFish> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (Map.Entry<String, Node> entry : section.children.entrySet()) {
            String origin = entry.getValue().scalar("origin");
            String cut = entry.getValue().scalar("cut").toLowerCase(Locale.ROOT);
            Integer sizeCm = CatchSize.roundCm(decimal(entry.getValue().scalar("size-cm")));
            if (origin.isBlank() || !CUTS.contains(cut) || sizeCm == null) {
                LOGGER.warning("[Cooking] Vanilla fish " + entry.getKey() + " is malformed and was skipped.");
                continue;
            }
            String material = entry.getKey().toUpperCase(Locale.ROOT);
            loaded.put(material, new VanillaFish(material, origin, cut, sizeCm));
        }
        return loaded;
    }

    private static Map<String, CutRule> readCutting(Node section) {
        Map<String, CutRule> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (Map.Entry<String, Node> entry : section.children.entrySet()) {
            CutRule rule = readCutRule(entry.getKey(), entry.getValue());
            if (rule != null) {
                loaded.put(rule.cut(), rule);
            }
        }
        return loaded;
    }

    private static CutRule readCutRule(String key, Node section) {
        String cut = key.toLowerCase(Locale.ROOT);
        String output = section.scalar("output");
        Double foodPerCm = decimal(section.scalar("food-per-cm"));
        Double minTotal = decimal(section.scalar("min-total"));
        Double maxTotal = decimal(section.scalar("max-total"));
        Double cmPerPortion = decimal(section.scalar("cm-per-portion"));
        Integer maxPortions = integer(section.scalar("max-portions"));
        if (!CUTS.contains(cut) || output.isBlank() || foodPerCm == null || foodPerCm <= 0
                || minTotal == null || maxTotal == null || minTotal < 0 || maxTotal < minTotal
                || cmPerPortion == null || cmPerPortion <= 0 || maxPortions == null || maxPortions < 1) {
            LOGGER.warning("[Cooking] Seafood cut rule " + key + " is malformed and was skipped.");
            return null;
        }
        return new CutRule(cut, output, foodPerCm, minTotal, maxTotal, cmPerPortion, maxPortions);
    }

    private static Double decimal(String raw) {
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer integer(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Node parse(String text) {
        Node root = new Node();
        Deque<Node> stack = new ArrayDeque<>();
        Deque<Integer> indents = new ArrayDeque<>();
        stack.push(root);
        indents.push(-1);
        for (String raw : text.split("\\R")) {
            if (raw.isBlank() || raw.trim().startsWith("#")) {
                continue;
            }
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') {
                indent++;
            }
            String trimmed = raw.trim();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            while (indent <= indents.peek()) {
                stack.pop();
                indents.pop();
            }
            Node node = new Node();
            String rest = trimmed.substring(colon + 1).trim();
            if (!rest.isEmpty()) {
                node.value = rest;
            }
            stack.peek().children.put(trimmed.substring(0, colon).trim(), node);
            stack.push(node);
            indents.push(indent);
        }
        return root;
    }

    private record CatchLoad(Map<String, CatchMapping> catches, Map<Integer, String> models) {}

    private static final class Node {
        private final Map<String, Node> children = new LinkedHashMap<>();
        private String value = "";

        private Node child(String key) {
            return children.get(key);
        }

        private String scalar(String key) {
            Node child = children.get(key);
            return child == null || child.value == null ? "" : child.value;
        }
    }
}
