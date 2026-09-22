# Quality, composition, and conversions

## Purpose

Stars are 1-5. They come from a roll (pickup, harvest, genetics) or from mixing several inputs. Conversions turn a vanilla or ItemsAdder stack into a Cooking food at a chosen star.

Conversion and composition code obtains item operations through
`TLibs.getItemAPI()`. This is the typed accessor for the same item API; quality
rolls, item matching, and lineage rules are unchanged.

## Vocabulary

- **Pickup quality:** roll in `quality.yml` `pickup.min` / `max` (1-5).
- **Harvest quality H:** crop roll before permission effects. See crops doc.
- **Composition:** station result stars from input stars and roles.
- **Legacy mode:** `composition.yml` `legacy-mode: false` makes `roles` authoritative. The exclude list in `quality.yml` applies only when legacy mode is on.
- **Permission effect:** a luck-style bump after the roll (`permission_effects.yml`). The result is `rolled + best bias`, then at least the best minimum (pickup min or a granted min), then clamped to 1-5. A null player skips permission nodes.

## Invariants

- Stars are not care, fertility, or nutrition. Nutrition multipliers per star live in `quality.yml` `nutrition-from-quality`.
- Composition does not, by itself, add ingredient food or nutrition onto soup, sauce, or fried output. Those outputs keep the type template unless some other code calls `setBaseFood` / `setBaseNutrition`.
- `CompositionQualityResolver.compose` also builds ingredient lineage from the same main, extra, and neutral roles. Nested lineage keeps its buckets. A raw input uses its origin in the role for that station. Neutral inputs are left out. `CompositionApplier` writes quality, freshness, and lineage onto the output.
- Sausage chains are the exception that sums meat food and nutrition. See portions doc.
- Farm produce pickup through the generic converter is quality 1 (`CropsConfig.isFarmFood`). Harvest conversion passes H explicitly. Potato and carrot are produce, not a 5-star backdoor.
- Every harvest star weight stays above 0. Fertility never forbids 5 stars.

## Inputs and outputs

`OriginQualityResolver` handles pickup and permissions.

`CompositionQualityResolver.compose(player, inputs, context)` returns final stars, freshness tracks, and ingredient lineage. Contexts include `SOUP_SCOOP`, `SAUCE_SCOOP`, `FRYING_PAN`, `CUTTING_BOARD`, `CARVE`, `MIXING_BOWL`, `SAUSAGE_MAKER`, `CHURN`.

`ConversionManager` rewrites matching stacks using `conversions.yml`. Plain vanilla fish are handled from `custom-fishing.yml`, because that path stores a default size the generic food string cannot carry. A `COD` with custom model data, or a CustomFishing item id, is converted by the legacy fish path before that vanilla check. A stack that already has `food_id` is skipped.

## State and persistence

Stars are `food_quality` PDC (min used for display and multipliers).

## Runtime flow

```text
roll or compose stars
  -> optional permission bump
  -> ItemBuilder stamps quality
  -> FoodItem.applyMultipliers on eat and lore
```

Role overrides per context are `composition.yml` `context-overrides`.

## Configuration

- `quality.yml`
- `composition.yml`
- `permission_effects.yml`
- `conversions.yml`

## Integrations

Permissions are Bukkit permission nodes. Crop permission `tfmc.cooking.better_crops` is applied after the harvest roll, not on grow ticks.

## Edge cases

- Chef or craft bonuses are modifier keys in `composition.yml` (`gap-up-chance-per-star`, `craft-quality-pct-bonus`). Read that file before changing odds.
- `/cooking qualitytest` is an admin debug command, not a player feature.

## Source map

- `src/main/java/net/tfminecraft/cooking/quality/`
- `src/main/java/net/tfminecraft/cooking/manager/ConversionManager.java`
- `src/main/java/net/tfminecraft/cooking/loader/ConversionLoader.java`
- `src/main/resources/quality.yml`
- `src/main/resources/composition.yml`
- `src/main/resources/conversions.yml`
- `src/main/resources/permission_effects.yml`

## Tests

- `IngredientLineageTest`
- `VanillaFishTest`
- `LegacyFishTest`

## Change checklist

- New compose context: add an enum value, a `context-overrides` block if roles differ, and this doc.
- New conversion line: `conversions.yml` only, unless matching rules change. CustomCrops produce uses `vegetable_2` and `fruit_2`. Vanilla carrot, potato, beetroot, pumpkin, apple, and melon stay on type 1. Nether wart is not converted.
