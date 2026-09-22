# Food items

## Purpose

A Cooking food item is a Bukkit stack plus PDC. `types.yml` is the template. Runtime code may override food, nutrition, quality, origin, tags, sauce, and carve progress.

## Vocabulary

- **Type id:** key under `types.yml`, stored as `food_id`.
- **Origin:** ingredient or species name (`Chicken`, `Beef`, `Mixed`). Overrides pick name, model, and carve sequence. `Mixed` is a display placeholder, not an ingredient.
- **Lineage:** ordered main and extra origins stored on the item. The short `ingredients` list is only for dish names.
- **Category:** grouping used by composition roles (`meat`, `seasoning`, `dairy`).
- **Base override:** item-specific `food_base` / `food_nutrition_base`, set when code calls `setBaseFood` or `setBaseNutrition`.
- **Final food / final nutrition:** base value after quality and tag multipliers, plus sauce.

## Invariants

- Templates come from `types.yml`. A crop entry does not define a second `food:` stat.
- `update: false` on a type skips the live item-scan rewrite. Default is true (`FoodItem` reads `update`, default true). Seasoning and sweetener templates set `update: false`.
- `valuable: true` marks CustomCrops produce (`vegetable_2`, `vegetable_cut_2`, `vegetable_chopped_2`, `fruit_2`). Vanilla `vegetable_1` and `fruit_1` are not valuable. Soup and sauce turn that flag into the Flavourful tag.
- `edible: false` marks an intermediate item. `seafood_whole` is a CustomFishing catch or a plain vanilla fish: it keeps the source material and model, stores size and cut class, and can be eaten raw. Its food is 6 and its nutrition is 6, with freshness 2.33, the same shape as a raw carrot. Its name is `#6fa8dc{inherit}`, so a plain vanilla name such as Cod or Salmon is coloured like the other ingredients. A catch that already has a coloured display name keeps that colour. CustomFishing also stores the loot id. Cutting it produces one `seafood_fish_filet` (food 6, nutrition 6), one `seafood_jellyfish` (food 5, nutrition 5), or one `seafood_octopus` (food 7, nutrition 7). Those portions stay edible. Their models include cooked, burnt, and boiled states, and rotten still wins over those states. Those models do not set a display rotation, same as cut carrot, so the piece lies flat on the cutting board. Each portion has a pot option (`tag: 3`, 15 seconds, burn at 30). Default edible is true.
- `mashable: true` marks a type the pot can mash after it is boiled. That is `vegetable_cut`, `vegetable_cut_2`, `meat_red_meat`, `meat_poultry`, and the three seafood portions. `meat_red_meat` and `meat_poultry` keep their frying-pan options and gain the same pot option. `meat_poultry_leg`, `meat_pork`, and `seafood_whole` are not mashable and have no pot option. Default mashable is false.
- A raw item with no lineage contributes its origin when it is composed. Blank origins and `Mixed` are ignored. An item that already has lineage keeps those main and extra buckets.
- `IngredientLineage.forEat` is what nutrition records: the item lineage, or its origin when that list is empty, merged with the nested sauce.
- Quality stars multiply nutrition through `quality.yml` `nutrition-from-quality`. Food uses `1.0 + (stars - 1) * 0.20`.
- Tag steps multiply food and nutrition separately (`food-mult`, `nutrition-mult`).
- Sauce food and nutrition are added after those multipliers.
- A carvable item with remaining cuts reports food and nutrition from [portions-and-servings.md](portions-and-servings.md), not the raw template, while `carve_remaining > 0`.

## Inputs and outputs

Input: TLibs path or FoodParser string (`meat(type=roast;origin=Chicken)`), optionally prefixed `c.`.

Output: one stamped `ItemStack`. `/cooking builditem` accepts the string with or without `c.`.

## State and persistence

See [../reference/item-pdc.md](../reference/item-pdc.md). Lore is rebuilt from tracks and final stats. `lore_index` remembers which lore lines the plugin owns.

## Runtime flow

1. `FoodLoader` builds templates.
2. `FoodParser` clones a template and applies origin, tags, and quality range.
3. `ItemBuilder` stamps PDC and lore.
4. Later edits go through `ItemUpdater` so the stack and `FoodItem` stay aligned.

## Configuration

- `types.yml`: id, name, model, food, nutrition, category, age, cooking-options, overrides, carve-sequence, update, edible, mashable
- `models.yml`: ItemsAdder model data per stage and tag
- `naming.yml`: prefix/filler tokens used in display names

## Integrations

TLibs creates the visual item. ItemsAdder supplies models. Path prefix `c` is food only.

## Edge cases

- Missing model falls back inside `FoodModel`.
- `quality=2` is exact. `quality=1-3` rolls inclusive.
- Extra path fields (`quality`, `origin`, `tags`) are ignored when matching an existing item. They matter when creating.

## Source map

- `src/main/java/net/tfminecraft/cooking/item/FoodItem.java`
- `src/main/java/net/tfminecraft/cooking/utils/FoodParser.java`
- `src/main/java/net/tfminecraft/cooking/utils/ItemBuilder.java`
- `src/main/java/net/tfminecraft/cooking/utils/ItemUpdater.java`
- `src/main/java/net/tfminecraft/cooking/item/CookingPathHandler.java`
- `src/main/java/net/tfminecraft/cooking/loader/FoodLoader.java`
- `src/main/resources/types.yml`
- `src/main/resources/models.yml`
- `src/main/resources/naming.yml`

## Tests

- `CookingPathHandlerTest`
- `FoodItemUpdateFlagTest`
- `PortionRulesTest`
- `ValuableIngredientTest`
- `IngredientLineageTest`
- `SeafoodWholeItemTest`
- `CookStateAssetsTest`
- `PotMashTest`
- `StackNormalizerTest`
- `ItemLoreRebuildTest`
- `ItemCacheOriginTest`

## Change checklist

- New type: follow [../how-to/add-food-type.md](../how-to/add-food-type.md).
- New PDC field: persistence doc and PDC reference.
- Changing final-stat math: update this doc and nutrition, because eat uses `getFinalFood` and `getFinalNutrition`.
