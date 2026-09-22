# Husbandry

## Purpose

Livestock lives in Cooking. BreedingBuddies is a reference for genetics, ownership, and mounts only. Do not port friendship, stable chunks, bundles, IRL day-change, or async simulation.

Numbers below that name a committed value were read from `src/main/resources/husbandry.yml`. If the YAML changes, this section's numbers must change in the same diff. Formulas are the contract even when tuning changes.

If this document and the code disagree, stop and reconcile them.

## Vocabulary

| Stat | What it is | What it is not |
| --- | --- | --- |
| Genetics | 0-1000, from breeding (wild roll up to `initial-genetic-max`, committed 20). Mount health/speed/jump use genetics and care. Stored genes do not drift from the animal's own care | Not a food-quality field. Parent care only scales the offspring gene bonus |
| Care | 0 to `care-max` (committed 200). Loaded happy time raises it. Neglect lowers it | Does not rewrite genes. Does not pick star quality |
| Hungry / Dirty | Negative states. Stop care-up. After grace, cause decay. Block breeding | Not friendship |
| Yield | `care / care-max`. Multiplies amount (roast cuts, wool count) | Does not change stars |
| Stars | `food_quality` 1-5 from raw genetics via `quality-from-genetics` | Not averaged with care |

Effective genetics for amounts: `genetics * (care / care-max)`. Care 0 yields 0% amount, still honoring `min-roast-cuts` (committed 1). Care 100 of 200 yields 50%. Care 200 yields 100%.

GUI yield percent is `round(100 * effectiveGenetics / max-genetics)` where `effectiveGenetics = floor(genetics * care / care-max)`.

## Invariants

Kept from the BreedingBuddies idea, scaled to 0-1000:

- Child genes: parent average, plus variance, plus slowdown when parents are already high, plus parent care.
- Committed breeding tuning: `genetic-variance-multiplier` 0.2, `genetic-slowdown-divisor` 0.6, `care-influence` 0.02 (about +20 at genetics cap 1000 when care is full).
- Ownership and co-ownership tokens. Tame plus name. Mount ranges. Neutering for every husbandry species. Inspect GUI with care and genetics bars.

Not in this plugin: friendship, stable chunks, bundles, UTC day-change, async world work, MMOItems ids, deleting a row because `Bukkit.getEntity` is null.

The old 6-arg constructor bug that forced the name `???` must not return. Persist `name` and call `setCustomName`. Never default to `???`.

## Inputs and outputs

Owned mature death: `slaughter.meat` plus star-gated `slaughter.drops`. Immature: no Cooking roast. Unowned `remove-unowned` types: no drops.

Stars from raw genetics. Amount from effective genetics, including `carve_remaining` on roasts. Counted drop tables use that yield. Minimum roast cuts still apply.

Sheep: vanilla wool always on shear. If `wool_ready_at` is due, also roll `shear.drops`, then reset the timer. Committed global `wool-timer` is 4h. A species may override it.

Milk: species `milk: true`, `milk-cooldown` (committed 20m), mature only, empty bucket. Result is cooking `milk_bucket` with quality from the animal and Cow or Goat origin from the entity. `MilkBucketConverter` must use that animal.

Eggs: chickens with `egg` set, loaded, mature, and happy, after `egg-timer` (committed 10m) on the 1-minute tick. `vanilla` means `Material.EGG`. Managed chickens do not also drop vanilla eggs.

Shed: species with `shed.drops`, same gates, `shed-chance` (committed 0.15) after `shed-timer` (committed 4h).

## State and persistence

SQLite file `Data/husbandry.db` is the record. Main thread, WAL. Write on ownership change, tick flush, death, chunk unload, and disable.

PDC on the entity is not the database. Flags: `managed`, `tame_name`, `linked_animal`.

Do not delete an owned row because the entity is unloaded. Unowned listed types are deleted on chunk load, except horse, donkey, mule, and camel that already have a row (enroll on first interact; they stay until tamed).

Owned entities: `setPersistent(true)`, `setRemoveWhenFarAway(false)`.

### Schema

`animals`: `uuid`, `type`, `name`, `genetics`, `care`, `stats_revision`, `hungry_since`, `dirty_since`, `last_processed_at`, `unloaded_at`, `affliction_elapsed`, `affliction_at`, `last_milk_at`, `wool_ready_at`, `shed_ready_at`, `egg_ready_at`, `mature_at` (null means adult), `neutered`, plus care remainders added by migration.

`owners`: `animal_uuid`, `player_uuid`, `role` (`owner` or `coowner`). Unique pair. Count versus `max-animals` (committed 15). Co-ownership counts for every owner.

### Migrations

Run when the database opens, not on `/cooking reload`.

| user_version | Change |
| --- | --- |
| 0 to 2 | Existing genetics divided by 10, cap 1000 |
| 2 to 3 | `mature_at` |
| 3 to 4 | `shed_ready_at` |
| 4 to 5 | `egg_ready_at` |
| 5 to 6 | care up/down remainders |
| 6 to 7 | `stats_revision` |

`stats-revision` (committed `"1"`) wild-resets genetics and care when the stored id differs. Blank skips the wipe. Owners, name, state, neuter, `mature_at`, and harvest timers stay. Bump the id when gene rules change.

Bred but untamed animals get an UNTAMED row for the same loaded visit. They despawn on chunk load if still unowned. Tame with no row inserts wild genetics then claims. Death deletes animal and owner rows.

## Runtime flow

Care simulation runs on chunk load (`HusbandrySimulator.catchUp`) and on a 1-minute tick while loaded. Egg and shed harvest run on the tick only, not on catch-up. Maturity runs on both.

```text
elapsed = now - last_processed_at
unloaded = unloaded_at set ? min(elapsed, now - unloaded_at) : 0
loaded_part = elapsed - unloaded
```

Order:

1. Decay first, full elapsed, no cap, if Hungry and/or Dirty already existed. After `min(since) + decay-grace`, lose care by `floor(decay_seconds / care.down.interval) * care.down.amount`. Keep original `*_since`.
2. Unloaded care gain only if they were happy at unload, capped by `offline-care`. If a negative already existed, unloaded gain is zero.
3. If `unloaded > long-unload-force` and still no negative, apply Hungry or Dirty 50/50. If one exists, add the other. Do not move the old `since`.
4. Loaded affliction uses `loaded_part` after `min-loaded`. At `affliction_at`, apply a missing state. Reroll the next threshold between `affliction.min` and `affliction.max`.
5. Loaded care gain while happy has no cap.
6. Stamp `last_processed_at`. Clear `unloaded_at` while loaded. Set it on unload.

Committed clocks: care up +1 per 1m while happy; care down -1 per 10h; `min-loaded` 60s; `decay-grace` 24h; `offline-care` 8h; `long-unload-force` 8h; affliction mean 6h, min 4h, max 8h.

Pulse-loading under `min-loaded` counts as still unloaded.

Happy means neither Hungry nor Dirty. Feed clears Hungry and consumes 1. Glove clears Dirty and is not consumed. Clearing a state does not add care.

Breeding:

```text
avg = (mother + father) / 2
child = clamp(avg + bonus * careRatio + careExtra, 0, maxGenetics)
```

`careRatio` is average parent care / `care-max`. Cancel if either parent is Hungry, Dirty, neutered, or still growing. Baby care starts at 0. Global `grow-up` is 1h. Species may override (chickens in config may use a shorter value). Immature animals can be tamed and cannot breed, milk, shear, lay, or drop a Cooking roast.

`max-animals` 15. `cooking.admin` bypasses the cap only. Chickens and llamas count. Bees are vanilla and are not wiped. Vanilla-tameable species must be vanilla-tamed before plugin ownership.

Mounts: all `AbstractHorse`. Stat blocks committed for HORSE, DONKEY, MULE, CAMEL. LLAMA has no `mounts` block until one is added. Formula: `max * (min-pct + genetics-pct * genetics/max-genetics + care-pct * care/care-max)`, floored at the type min. Untamed mounts stay rideable. Owned mounts are owner, co-owner, or staff.

Damage flags in `husbandry.yml` `damage` apply to entities with a row. `other-players: false` would cancel foreign player damage. Read the file for the committed booleans.

Inspect GUI: sneak empty-hand, or `items.inspect` when set. 54-slot inventory. Care bar and genetics bar. Products icon only if the species has slaughter, shear, shed, milk, or egg. Stars are not shown in the GUI.

World cleanup: on chunk load, `remove-unowned` types with no owner are removed and orphan rows deleted, except enrolled horse, donkey, mule, and camel.

## Configuration

Keys are the tables above. Durations are TLibs strings (`4h`, `20m`, `60s`). Legacy `*-hours` keys still load with a warning.

Per species: `milk`, `slaughter.meat`, `slaughter.drops`, `shear.drops`, `shed.drops`, `egg`, `grow-up`, `wool-timer`, optional `exp.min` and `exp.max`.

A successful feed or clean grants a random integer of MMOCore profession experience. `profession` is the profession id (committed `farming`). `exp.min` and `exp.max` are the default bracket (committed 6 and 8), inclusive. A species `exp` block replaces that bracket. A blank profession id, a missing profession, or MMOCore disabled grants nothing.

Also `remove-unowned`, `quality-from-genetics`, `amount-from-genetics`, `breeding`, `damage`, `mounts`.

Meat strings in the committed file use `meat(type=roast;origin=Beef)` style names, not the older `food(...;origin=BEEF)` form.

## Integrations

Items are TLibs paths in `husbandry.yml` (`items.tame`, `co-own`, `feed`, `glove`, `neuter`). Not MMOItems.

## Edge cases

- Admin `/cooking husbandry spawn` is optional. Spawned livestock with no owner still despawn on chunk load unless they are an enrolled horse-family row.
- `/cooking husbandry save` upserts loaded animals and checkpoints WAL.
- `/cooking reload` reloads YAML and stats revision. It does not reopen SQLite.

## Source map

Package `net.tfminecraft.cooking.husbandry`.

| Area | Classes |
| --- | --- |
| DB | `HusbandryRepository`, `HusbandryAnimal` |
| Lifecycle | `HusbandryLifecycleListener`, `HusbandryTickTask` |
| Care | `HusbandrySimulator`, `HusbandryCareListener` |
| Ownership | `HusbandryTamingListener` |
| Breed | `HusbandryBreedListener`, `HusbandryNeuterListener`, `HusbandryGenetics` |
| Harvest | `HusbandryDeathListener`, `HusbandryHarvestListener`, `HusbandryHarvest`, `HusbandryDropRoller` |
| Mounts | `HusbandryMounts`, `HusbandryMountListener`, `HusbandryDamageListener` |
| GUI | `HusbandryInspectGui`, `HusbandryInspectListener` |

## Tests

`HusbandryRepositoryTest`, `HusbandryGeneticsTest`, `HusbandryWoolTimerTest`, `HusbandryEntitiesCacheTest`, `HusbandrySimulatorTest`, `HusbandryMilkTimerTest`, `HusbandryProductsTest`, `HusbandryDropRollerTest`, `HusbandryMountSpeedTest`, `HusbandrySlaughterDropsTest`, `HusbandryQualityRangeTest`, `HusbandryMountEnrollTest`.

## Change checklist

- Gene rule change: bump `stats-revision` if live animals must wild-reset.
- New column: migration `user_version`, not reload.
- New species output: `husbandry.yml` plus this doc if a new capability key appears.
- Player wiki: `/wiki/animal-husbandry` when a player-visible step changes.

Commands require `cooking.admin`:

- `/cooking husbandry spawn <type> [genetics] [care]`
- `/cooking husbandry save`
