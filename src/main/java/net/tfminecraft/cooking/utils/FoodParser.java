package net.tfminecraft.cooking.utils;

import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.IngredientLineageCodec;
import net.tfminecraft.cooking.item.tag.AgeScale;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.FoodLoader;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.WarmthUtils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FoodParser {

    // Result for top-level parsing
    public static class Result {
        public FoodItem template;
        public boolean unique;
        public boolean explicitQuality;
    }

    // ============================================================
    //  BALANCED FIELD EXTRACTOR
    // ============================================================

    /**
     * Extracts key=value pairs from inside (...) while preserving nested parentheses.
     */
    public static Map<String, String> extractFields(String inside) {
        Map<String, String> map = new LinkedHashMap<>();

        int i = 0;
        while (i < inside.length()) {

            // Find key=
            int eq = inside.indexOf('=', i);
            if (eq == -1) break;

            String key = inside.substring(i, eq).trim();
            int start = eq + 1;

            // Value begins with nested block?
            if (start < inside.length() && inside.charAt(start) == '(') {
                int depth = 0;
                int end = start;

                while (end < inside.length()) {
                    char c = inside.charAt(end);
                    if (c == '(') depth++;
                    if (c == ')') depth--;
                    end++;
                    if (depth == 0) break;
                }

                // Extract without outer parentheses
                String value = inside.substring(start + 1, end - 1);
                map.put(key, value);

                // Move to next segment (skip trailing semicolon if present)
                if (end < inside.length() && inside.charAt(end) == ';')
                    i = end + 1;
                else
                    i = end;

            } else {
                // Flat field (no parentheses)
                int semi = inside.indexOf(';', start);
                if (semi == -1) semi = inside.length();

                String value = inside.substring(start, semi);
                map.put(key, value);

                i = semi + 1;
            }
        }

        return map;
    }

    // ============================================================
    //   MAIN PARSER
    // ============================================================

    public static Result parse(String input) {
        if (input == null || !input.contains("(")) return null;

        String cat = input.substring(0, input.indexOf("(")).trim();
        int firstParen = input.indexOf("(");
        String inside = input.substring(firstParen + 1, input.length() - 1);

        Map<String, String> fields = extractFields(inside);

        String foodId = null;
        String originInput = "";
        int amountMin = 1, amountMax = 1;
        int qualMin = 1, qualMax = 5;
        boolean unique = true;
        boolean explicitQuality = false;

        FoodItem item = null;

        // ---------------- FIELD PARSING ---------------------
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String key = e.getKey().trim().toLowerCase();
            String value = e.getValue().trim();

            switch (key) {
                case "type":
                    foodId = value;
                    FoodItem base = FoodLoader.getByString(foodId);
                    if (base == null) return null;
                    item = new FoodItem(base);
                    break;

                case "origin":
                    originInput = value;
                    break;

                case "unique":
                    unique = Boolean.parseBoolean(value);
                    break;

                case "quality":
                    if (value.contains("-")) {
                        String[] q = value.split("-");
                        qualMin = Integer.parseInt(q[0]);
                        qualMax = Integer.parseInt(q[1]);
                    } else {
                        qualMin = qualMax = Integer.parseInt(value);
                    }

                    item._parsedQualMin = qualMin;
                    item._parsedQualMax = qualMax;
                    explicitQuality = true;
                    break;
                case "amount":
                    if (value.contains("-")) {
                        String[] q = value.split("-");
                        amountMin = Math.max(1, Integer.parseInt(q[0]));
                        amountMax = Math.min(64, Integer.parseInt(q[1]));
                    } else {
                        int fixed = Integer.parseInt(value);
                        amountMin = amountMax = Math.max(1, Math.min(64, fixed));
                    }
                    break;

                case "tags":
                    if (!value.isEmpty()) {
                        for (String rawTag : value.split("[,:]")) {
                            String t = rawTag.trim();
                            if (t.isEmpty()) {
                                continue;
                            }
                            if (!t.contains(".")) {
                                item.addTagTrack(t);
                                continue;
                            }
                            String[] kv2 = t.split("\\.");
                            if (kv2.length != 2) continue;

                            String trackId = kv2[0];
                            int val = Integer.parseInt(kv2[1]);
                            int migratedValue = AgeScale.migrateTrackValue(trackId, val);
                            String migratedId = AgeScale.migrateTrackId(trackId);
                            if ("warmth".equalsIgnoreCase(migratedId) && migratedValue >= WarmthUtils.ROOM_TEMP_AGE) {
                                continue;
                            }

                            TagTrack baseTrack = TrackLoader.getByString(migratedId);
                            if (baseTrack != null) {
                                TagTrack newt = new TagTrack(baseTrack);
                                newt.setValue(migratedValue);
                                item.addOrModifyTrack(newt);
                            }
                        }
                    }
                    break;

                case "ingredients":
                    if (!value.isEmpty()) {
                        for (String ing : value.split(":"))
                            item.addIngredient(ing);
                    }
                    break;

                case "lineage":
                    item.setLineage(IngredientLineageCodec.decode(value));
                    break;

                case "sauce":
                    // recursive parse
                    Result sr = parse(value);
                    if (sr != null) item.setSauce(sr.template);
                    break;
                case "sauce_name":
                    item.setSauceName(value);
                    break;
                default:
                    break;
            }
        }

        // ---------------- FINALIZE ---------------------
        if (item == null) {
            return null;
        }

        Result r = new Result();
        r.unique = unique;
        r.explicitQuality = explicitQuality;

        item.setCategory(cat);
        item.setOrigin(originInput);

        int finalAmount = ThreadLocalRandom.current().nextInt(amountMin, amountMax + 1);
        item.setAmount(finalAmount);

        item.setQualityRange(qualMin, qualMax);
        item._parsedQualMin = qualMin;
        item._parsedQualMax = qualMax;

        r.template = item;
        return r;
    }

    // ============================================================
    //  ENCODE BACK TO STRING
    // ============================================================

    public static String toString(ItemStack stack) {
        FoodItem f = FoodItem.fromItem(stack);
        if (f == null) return null;

        return toString(f, stack.getAmount());
    }

    public static String toString(FoodItem f, int amount) {
        StringBuilder sb = new StringBuilder();

        sb.append(f.getCategory()).append("(");
        sb.append("type=").append(f.getId()).append(";");
        sb.append("amount=").append(amount).append(";");
        sb.append("origin=").append(f.getOrigin() == null ? "" : f.getOrigin()).append(";");
        sb.append("quality=").append(f.getQualityMin()).append(";");

        // Tags
        sb.append("tags=");
        boolean first = true;
        for (TagTrack t : f.getTagTracks()) {
            if (!first) sb.append(":");
            first = false;
            sb.append(t.getId()).append(".").append(t.getValue());
        }
        sb.append(";");

        if(f.getIngredients().size() >= 0) {
            // Ingredients
            sb.append("ingredients=");
            first = true;
            for (String ing : f.getIngredients()) {
                if (!first) sb.append(":");
                first = false;
                sb.append(ing);
            }
            sb.append(";");
        }

        String lineage = IngredientLineageCodec.encode(f.getLineage());
        if (!lineage.isEmpty()) {
            sb.append("lineage=").append(lineage).append(";");
        }

        // Sauce (nested)
        if (f.hasSauce()) {
            sb.append("sauce=(");
            sb.append(toString(f.getSauce(), 1));
            sb.append(")");
        }

        // Sauce name
        if (f.hasSauceName()) {
            sb.append("sauce_name=").append(f.getSauceName()).append(";");
        }


        sb.append(")");
        return sb.toString();
    }
}
