# Configuration reference

Repo source: `src/main/resources`. Server copies are created only if missing. Load order is [../architecture/bootstrap.md](../architecture/bootstrap.md).

| File | Loader | Owns |
| --- | --- | --- |
| `config.yml` | `ConfigLoader`, `NutritionConfig`, heat and item caches | furniture ids, tools, mixing, oven, liquids, trough, nutrition and variety, logging |
| `models.yml` | `ModelLoader` | item model stages |
| `types.yml` | `FoodLoader` | food templates |
| `baking-trays.yml` | `BakingTrayLoader` | oven mould recipes |
| `milling-recipes.yml` | `MillingRecipeLoader` | milling stone recipes |
| `crafting-stations.yml` | `CraftingStationLoader` | cutting board and similar |
| `tags.yml` | `TrackLoader` | tag tracks and multipliers |
| `naming.yml` | `NamingLoader` | name tokens |
| `quality.yml` | `QualityConfigLoader` | star multipliers and pickup range |
| `composition.yml` | `CompositionConfigLoader` | roles and modifiers |
| `permission_effects.yml` | `PermissionEffectsLoader` | post-roll permission bumps |
| `conversions.yml` | `ConversionLoader` | vanilla/IA to Cooking food |
| `farming.yml` | `FarmingLoader` | vanilla hoe crop list |
| `crops.yml` | `CropsLoader` | affection, seed, source |
| `husbandry.yml` | `HusbandryLoader` | livestock tuning |
| `carve-sequences.yml` | `CarveSequenceLoader` | roast and sausage cuts |
| `plugin.yml` | Bukkit | name, version, depend, command |
| `custom-fishing.yml` | `CustomFishingCatalog` | live catch ids, cut class, rod quality bands, vanilla fish default sizes, seafood cutting yield, `model-data`, `legacy-size-cm` |
| `cookware.yml` | none | shipped, not loaded. Heat lives in `config.yml` |

## Known drift

- `plugin.yml` `version` is `1.0`. `pom.xml` `<version>` is `0.1.5-ALPHA`. Do not change either as a drive-by.
- `cookware.yml` is listed in `Cooking.createConfigs()` and is absent from `loadConfigs()`.

## Adding a key

[../how-to/add-config-option.md](../how-to/add-config-option.md).
