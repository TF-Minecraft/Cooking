# Bootstrap and reload

## Purpose

`Cooking` is wiring only. It creates the data folder, copies missing resources, loads YAML into static registries, registers listeners, and starts periodic tasks.

## Vocabulary

- **Resource:** a file in `src/main/resources`, packaged in the jar.
- **Data file:** the copy under the server plugin folder. Created only when missing (`saveResource(s, false)`).
- **Reload:** `loadConfigs()` plus station rebuild and nutrition/husbandry task restart. It does not reopen SQLite.

## Invariants

- `src/main/resources` is the repo source. `target/classes` is compiler output.
- An existing server YAML is not overwritten by a jar update.
- `/cooking reload` reloads YAML and applies husbandry `stats-revision`. It does not run SQLite migrations again.
- `cookware.yml` is copied on first install and is not passed to a loader.
- `/cooking qualitytest compose2` prints composition lineage mains and extras.

## Inputs and outputs

Enable reads every file listed in `Cooking.createConfigs()` and `Cooking.loadConfigs()`. Disable stops nutrition, husbandry, oven, liquid, and churn tasks, flushes loaded husbandry rows, and unregisters the TLibs `c` path handler.

## State and persistence

Husbandry SQLite opens in `onEnable` and closes in `onDisable`. Item PDC is not loaded here. Furniture slot contents belong to InteractibleFurniture.

## Runtime flow

1. `plugin = this`
2. `createFolders()` (`Data/`)
3. `createConfigs()`
4. `loadConfigs()` in this order: `config.yml`, `models.yml`, `types.yml`, `baking-trays.yml`, `milling-recipes.yml`, `crafting-stations.yml`, `tags.yml`, `naming.yml`, `quality.yml`, `composition.yml`, `permission_effects.yml`, `conversions.yml`, `farming.yml`, `crops.yml`, `husbandry.yml`, `carve-sequences.yml`, `custom-fishing.yml`
5. `registerListeners()`
6. Deferred starts: cooking manager, nutrition drain, husbandry tick, item scan subscriptions for tag aging and leftover fish, path handler `c`

## Configuration

See [../reference/configuration.md](../reference/configuration.md).

## Integrations

Hard plugin dependencies are declared in `plugin.yml`: TLibs, InteractibleFurniture, TFMCCore. Soft: RPCharacters, MMOCore, CustomCrops, SimpleFactions, CustomFishing. The CustomFishing listener is registered only when that plugin is already enabled.

## Edge cases

- Opening `husbandry.db` throws and fails enable if SQLite cannot open.
- Reload while players hold food does not rewrite existing item PDC by itself. Tag scan and item update paths do that later. The leftover-fish scan converts a matching `COD` in an open inventory on the same item-scan pass.
- Version strings: `plugin.yml` `version: 1.0`, Maven `0.1.5-ALPHA`. Both are currently true and inconsistent.

## Source map

- `src/main/java/net/tfminecraft/cooking/Cooking.java`
- `src/main/java/net/tfminecraft/cooking/loader/ConfigLoader.java`
- `src/main/java/net/tfminecraft/cooking/manager/CommandManager.java` (`reload`)

## Tests

No bootstrap test. Documentation impact is `scripts/test_doc_manifest.py`.

## Change checklist

- New YAML file: add it to `createConfigs()`, `loadConfigs()`, the configuration reference, and `docs/manifest.json`.
- New listener: register it in `registerListeners()` and name the owning system doc.
- Do not point agents at `target/`.
