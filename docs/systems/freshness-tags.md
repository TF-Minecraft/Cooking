# Freshness, tags, and aging

## Purpose

Tag tracks are named meters on a food item: freshness, cooked, warmth, sauce thickness, soup thickness, and similar. Steps inside a track change the name, model, and food/nutrition multipliers.

## Vocabulary

- **Track:** one meter (`freshness`).
- **Step:** a threshold on that meter (`fresh`, `stale`, `rotten`) with multipliers.
- **Age scale:** `types.yml` `age.<track>` speeds or slows that track for one type.
- **Remainder:** fractional age stored so slow tracks do not lose progress to integer ticks.

## Invariants

- `tags.yml` defines the tracks. `types.yml` `age` only scales them.
- Food multipliers and nutrition multipliers are independent. A consistency step can raise food more than nutrition.
- `flavourful` is nutrition-mult 1.1 and food-mult 1.0. One valuable ingredient on a soup or sauce applies it. It does not replace aromatic, rounded, or seasoning.
- Rotten freshness is a cook-tag override for roast models (`CarvableRoastUtils.resolveCookTag`).
- Types with `update: false` do not get scan rewrites. Do not rely on aging for those templates.
- Warmth is a track, not a second plugin.

## Inputs and outputs

`TagManager` subscribes to TFMCCore `ItemScanService`. Each scan advances age from `last_update` and remainder, then `ItemUpdater` rewrites lore and model.

Stations add or copy tracks (cooked, warmth, thickness) when they finish a step.

## State and persistence

PDC: `tags`, `age_remainder`, `last_update`.

## Runtime flow

```text
scan tick
  -> elapsed since last_update
  -> add remainder
  -> advance each track by age scale
  -> store new remainder and timestamp
  -> rebuild lore
```

## Configuration

`tags.yml` steps include `name`, `value`, `food-mult`, and `nutrition-mult`.

Committed consistency nutrition multipliers (light, creamy, velvety, thick) are lower than their food multipliers. Read the file before assuming they match.

## Integrations

TFMCCore must be present. Cooking does not run its own global item scanner.

## Edge cases

- Held warmth logic is in `WarmthUtils`, covered by `WarmthUtilsTest`.
- Copying tracks onto a carved piece copies cooked, freshness, and warmth only (`CarvableRoastUtils.copyInheritedTracks`).

## Source map

- `src/main/java/net/tfminecraft/cooking/manager/TagManager.java`
- `src/main/java/net/tfminecraft/cooking/item/tag/`
- `src/main/java/net/tfminecraft/cooking/utils/WarmthUtils.java`
- `src/main/resources/tags.yml`

## Tests

- `AgeScaleTest`
- `WarmthUtilsTest`
- `ItemLoreRebuildTest`

## Change checklist

- New track: `tags.yml`, loader, and any station that should apply it.
- Multiplier change: this doc if the rule changes; the YAML file is the number.
