# Persistence and state ownership

## Purpose

Cooking state is split across item PDC, husbandry SQLite, and InteractibleFurniture slot data. Each store has one owner.

## Vocabulary

- **PDC:** Bukkit persistent data on an `ItemStack` or entity.
- **Base override:** `food_base` and `food_nutrition_base` stored on an item because code called `setBaseFood` or `setBaseNutrition`.
- **Row:** one livestock record in `Data/husbandry.db`.

## Invariants

- Food identity and quality travel on the item. They are not in SQLite. Catch size, cut class, and CustomFishing id travel on that same item.
- Livestock genetics, care, owners, timers, and affliction live in SQLite. Entity PDC is only a flag/link, not the database.
- Unloaded owned animals keep their row. Unowned listed types are removed on chunk load, with the horse-family enroll exception documented in [../systems/husbandry.md](../systems/husbandry.md).
- Furniture inventories are not Cooking tables.
- `slot_data` PDC on a scooped soup stores encoded pot slots for display/name context. It is not the pot's live inventory.
- `food_lineage` stores versioned main and extra origins. Missing lineage on an old item falls back to `food_origin` at the next composition. `food_ingredients` remains the short name list.
- Player PDC `variety_history` stores the rolling meals used for nutrition variety. It is account-wide. Raw and effective diet stay on the RPCharacter (`raw-diet-score`, `diet-score`).

## Inputs and outputs

`ItemBuilder.stamp` writes item PDC. `FoodItem.fromItem` reads it. `HusbandryRepository` is the only SQLite API. Migrations run when the connection opens, keyed by `PRAGMA user_version`.

## State and persistence

Item keys: [../reference/item-pdc.md](../reference/item-pdc.md).

Database file: `plugins/Cooking/Data/husbandry.db` (WAL). Main thread only.

Husbandry migrations through `user_version` 7 are listed in [../systems/husbandry.md](../systems/husbandry.md). They do not re-run on `/cooking reload`.

## Runtime flow

Chunk unload stamps `unloaded_at` and upserts. Disable flushes loaded animals. Death deletes the animal and owner rows.

## Configuration

`husbandry.yml` `stats-revision` resets gene/care fields when the id changes. It does not wipe owners, name, neuter, maturity, or harvest timers.

## Integrations

Paper saves entity PDC. Cooking still must not treat that PDC as the livestock record.

## Edge cases

- A missing entity with an owned row is kept for later reconcile.
- `Bukkit.getEntity == null` must not delete an owned row.
- Server YAML that already exists is not replaced when the jar ships a new default.

## Source map

- `src/main/java/net/tfminecraft/cooking/utils/Keys.java`
- `src/main/java/net/tfminecraft/cooking/utils/ItemBuilder.java`
- `src/main/java/net/tfminecraft/cooking/husbandry/HusbandryRepository.java`
- `src/main/java/net/tfminecraft/cooking/Cooking.java` (`openHusbandryDatabase`)

## Tests

- `HusbandryRepositoryTest`

## Change checklist

- New item field: add a `NamespacedKey`, stamp it, read it back, and update the PDC reference.
- New SQLite column: bump `user_version` in the repository migration, not in reload.
