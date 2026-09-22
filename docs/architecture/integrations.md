# Integrations

## Purpose

Cooking owns food items, station behavior, crop quality math, and livestock records. Other plugins own items, furniture placement, characters, crop blocks, and province fertility.

## Vocabulary

- **TLibs path:** a string such as `v.wheat`, `ia.tfmc_cooking:tomato`, `m.materials.leather`, or `c.grain(type=wheat)`.
- **Furniture id:** the InteractibleFurniture type key stored in `config.yml` (`pot: pot`).

## Invariants

- Cooking registers the TLibs path prefix `c` and unregisters it on disable.
- `c` paths match food only. They are not seeds.
- CustomCrops YAML must not contain fertility rules. Affection comes from `crops.yml`.
- Province fertility is a 0-100 read from SimpleFactions. Missing plugin, disabled map, or unmapped land is 0 for harvest.
- Nutrition numbers are applied to the active RPCharacters character. Cooking does not invent a second character store.
- MMOCore crop XP still needs a drop-less `BlockBreakEvent` on hoe harvest. Cooking does not calculate that XP.

## Inputs and outputs

| Plugin | Cooking reads | Cooking writes |
| --- | --- | --- |
| TLibs | item match, create, names | path handler `c` |
| InteractibleFurniture | place, click, slot events | slot display updates |
| TFMCCore | `ItemScanService` | tag aging subscription |
| RPCharacters | active character | food level and nutrition attribute |
| CustomCrops | harvest and grow events | wrapped grow-conditions, converted drops |
| SimpleFactions | province fertility, battle state | none |
| MMOCore | none directly | break event so crop XP can fire |
| ItemsAdder | custom item ids and models | none |
| CustomFishing | loot id, size in cm, and rod id from `FishingLootSpawnEvent`. Leftover fish also expose item tags `CustomFishing.id` and `CustomFishing.size` | replaces a configured catch, or a leftover cod, with `seafood_whole` |

## State and persistence

External plugins keep their own data. Cooking must not duplicate CustomCrops plant rows or faction claims.

## Runtime flow

See the system docs for each bridge. Startup order is [bootstrap.md](bootstrap.md).

## Configuration

Furniture and tool ids are the top of `config.yml`. IA namespaces are `tfmc_cooking` unless a crop seed path says `playbox_custom_crops`.

## Integrations

Asset packs shipped beside the plugin:

- `ItemsAdder/tfmc_cooking/`
- `CustomCrops/`

Those trees are content, not generated class files. `target/` is generated.

## Edge cases

- Soft-depend missing: nutrition, fertility, CustomCrops, MMOCore, or CustomFishing features no-op or use the documented fallback. Do not crash enable for a soft depend.
- CustomFishing 2.3.27 is optional. Cooking reads `loot.id()`, `ContextKeys.SIZE`, and `ContextKeys.ROD` on a new catch. Unconfigured loot is left alone, and Cooking does not cancel CustomFishing actions or read its silver/golden star groups.
- A configured catch with a missing, non-finite, or non-positive size is logged and left unchanged. Stored centimetres use `Math.round`.
- Rod quality bands are in `custom-fishing.yml`: basic 1-2, steel 2-3, abyssalite 3-4, mythril 4-5. An unknown rod uses the `unknown-rod` band (1-5), then the normal pickup permission adjustment.
- A leftover `COD` already in an inventory is converted on item scan, and a dropped one on pickup. Cooking reads the `CustomFishing` item tags `id` and `size` when that plugin is enabled. Otherwise it uses `model-data` in `custom-fishing.yml`. A stack that already has `food_id` is left alone. Lore is not read. An unknown loot id is left alone. Silver and golden model numbers map to the same species and do not set Cooking quality. A missing size uses `legacy-size-cm` (45). Quality is the normal pickup roll.
- Plain vanilla cod, salmon, tropical fish, and pufferfish become `seafood_whole` on pickup using the `vanilla` sizes in `custom-fishing.yml` and the normal pickup-quality roll. A stack with custom model data is not treated as vanilla cod. The whole item stays edible and keeps the type food and nutrition.
- Cutting a whole seafood item uses the `cutting` output type in `custom-fishing.yml`. One whole item becomes one portion. Food and nutrition stay the portion type's levels: 6 and 6 for a fish filet, 5 and 5 for jellyfish, 7 and 7 for octopus. Size is still stored on the item.
- Nutmeg seeds are `ia.playbox_custom_crops:nut_seeds`, not `nutmeg_seeds`.

## Source map

- `src/main/java/net/tfminecraft/cooking/item/CookingPathHandler.java`
- `src/main/java/net/tfminecraft/cooking/crops/CropCustomCropsBridge.java`
- `src/main/java/net/tfminecraft/cooking/crops/CropFertility.java`
- `src/main/java/net/tfminecraft/cooking/fishing/CustomFishingBridge.java`
- `src/main/java/net/tfminecraft/cooking/fishing/CustomFishingCatchListener.java`
- `src/main/java/net/tfminecraft/cooking/fishing/LegacyFishScan.java`
- `src/main/java/net/tfminecraft/cooking/nutrition/NutritionService.java`
- `src/main/resources/plugin.yml`
- `src/main/resources/custom-fishing.yml`

## Tests

- `CookingPathHandlerTest`
- `CropsConfigTest`
- `CustomFishingCatchTest`
- `VanillaFishTest`
- `LegacyFishTest`
- `SeafoodCuttingTest`

## Change checklist

- New external id: update [../reference/external-assets.md](../reference/external-assets.md) and the station or crop doc.
- Do not add a hard depend for a plugin listed as `softdepend` unless enable truly cannot run without it.
