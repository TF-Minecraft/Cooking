# Crops and farming

## Purpose

Crop stars and growth cancel live in Cooking. CustomCrops is the plant engine for custom crops. SimpleFactions owns province fertility (0-100 lookup only). Vanilla hoe crops use `farming.yml`.

Seeds stay dumb ItemsAdder or vanilla items: no `food_quality`, no planted-crop SQL, no plant-to-harvest lineage.

## Vocabulary

- **Produce:** the drop that becomes Cooking food.
- **Seed:** the drop left as a seed. Not Cooking food.
- **Affection:** per-crop weight in `crops.yml`, used for both harvest stress and grow chance.
- **H:** harvest star in 1-5 before the item is built.

## Invariants

| Piece | What it is | What it is not |
| --- | --- | --- |
| Seeds | IA/vanilla seed stacks from CustomCrops yield and Dowsing | Not Cooking food. Not `food_quality`. Not `c.seed` |
| Harvest food | Conversion of dropped IA/vanilla produce at quality H | Not a `food:` line on the crop. Not MMOItems. Not IA star variants |
| Harvest roll | Weighted 1-5 from fertility times affection, then pickup permissions | Not planted-seed lineage. Never zero weight on 5 |
| Growth gate | `(fertility/100)^affection` per grow tick. Fertility 0 never grows; 100 always grows | Not harvest stars. Not bone meal |
| Fertility | SimpleFactions province 0-100 at the block | Not a second weight file in SimpleFactions |
| Permissions | `tfmc.cooking.better_crops` and other pickup effects after the roll | Does not affect grow ticks |

Generic pickup conversion of farm produce is still quality 1 via `CropsConfig.isFarmFood`. Potato and carrot are produce, not seeds, for that pickup path.

If this document and the code disagree, stop and reconcile them. Do not treat either as automatically winning.

## Inputs and outputs

CustomCrops loot drops `tfmc_cooking` produce and IA seeds. Cooking converts produce through `conversions.yml` at H. Configured seed drops stay unchanged.

Dowsing farm tables drop plain IA seeds (`ia.playbox_custom_crops:..._seeds`).

## State and persistence

No crop SQL. Fertility is read at the block when a tick or harvest happens.

## Runtime flow

Harvest:

```text
Province fertility 0-100 x crops.yml affection
  -> stress = (1 - fertility/100) * affection
  -> lerp rich -> poor star weights
  -> sample H in 1-5 (every star weight > 0)
  -> OriginQualityResolver.applyPickupPermissions
  -> (hoe) HoeQualityBonus
  -> convert produce via conversions.yml at H
  -> leave seed-only drops unchanged
     (wheat, beetroot, melon, pumpkin seeds)
```

Potato and carrot are produce. Hoe harvest reserves one vanilla stack for replant, then converts leftover drops. Nether wart is still harvested and replanted, and the drop stays vanilla.

Growth:

```text
growth-gate.enabled and SimpleFactions map active
  -> affection from crops.yml
  -> growChance = (fertility/100) ^ affection
  -> vanilla: cancel BlockGrowEvent on fail
  -> CustomCrops: Cooking wraps loaded grow-conditions
```

Unlisted blocks are not gated. `growth-gate.enabled: false` or map off: all ticks allowed. Growth does not cancel when the map is off.

## Configuration

`crops.yml`: `seed`, `source`, `block`, `affection`. No `food:` on crops.

Stress is 0 at fertility 100. At fertility 0, stress equals affection. Missing or invalid affection uses 0.5, clamped to `(0, 1]`.

Default star weights in code/config (rich then poor, stars 1-5):

- rich: `8, 16, 28, 28, 20`
- poor: `40, 28, 18, 10, 4`

After lerp, each weight is clamped above 0, normalized, and sampled.

`farming.yml` lists vanilla hoe crops: wheat, potatoes, carrots, beetroots, nether wart. Melon and pumpkin fruit harvest is out of scope. Stems are growth-gated.

## Integrations

| | Vanilla hoe | CustomCrops |
| --- | --- | --- |
| Crop id | lowercase block, e.g. `wheat` | CC id, e.g. `tomato` |
| Seed path | `v.wheat_seeds`, `v.potato`, ... | `ia.playbox_custom_crops:tomato_seeds` |
| Nutmeg seed | n/a | `ia.playbox_custom_crops:nut_seeds` (not `nutmeg_seeds`) |
| Produce | vanilla item | `tfmc_cooking:<id>` |
| Harvest | hoe: roll H, convert produce, leave seeds. Drop-less `BlockBreakEvent` so MMOCore crop XP still applies | `CropBreakEvent` / mature interact |
| Growth | `CropGrowthListener` | wrapped grow-conditions; affection only from `crops.yml` |

Cooking injects the fertility check on enable and on `/customcrops reload`. CustomCrops YAML must not mention fertility.

Deploy the tracked `CustomCrops/` configuration. Downloaded libraries and the old
configuration ZIP are not versioned; regenerate any deployment archive using the
[external asset instructions](../reference/external-assets.md#customcrops) so old
fertility rules cannot replace the current YAML.

TLibs `c` paths: see [food-items.md](food-items.md). There is no `c.seed` path and no `/cooking crop` command.

## Edge cases

- Potato and carrot `seed:` is the same vanilla item as produce. Do not skip conversion just because the drop matches `seed:`. Nether wart uses the same item as its seed and is not converted.
- Hoe harvest still keeps one untagged stack to replant.

## Source map

- `src/main/java/net/tfminecraft/cooking/crops/`
- `src/main/java/net/tfminecraft/cooking/farming/`
- `src/main/resources/crops.yml`
- `src/main/resources/farming.yml`
- `src/main/resources/conversions.yml`

## Tests

- `CropsConfigTest`
- `CropHarvestQualityTest`
- `CropGrowthGateTest`
- `CropGrowthChanceTest`
- `CropHarvestItemsTest`

## Change checklist

- New crop: [../how-to/add-husbandry-or-crop.md](../how-to/add-husbandry-or-crop.md).
- Out of scope: seed quality, planted-crop SQLite, soil tending that changes quality while growing.

## Player wiki

`/wiki/farming` and `/wiki/harvesting`. This file remains the implementation spec.
