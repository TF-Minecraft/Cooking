# Nutrition and diet

## Purpose

Eating a Cooking food raises the active character's food value and moves their diet score toward that item's nutrition level. Vanilla saturation and some regeneration are suppressed so this system stays in charge.

## Vocabulary

- **Food value:** a pool on the RPCharacters character, capped by `nutrition.max-food` (committed 200). This is hunger storage, fed by `FoodItem.getFinalFood`.
- **Nutrition level:** `FoodItem.getFinalNutrition`. It is the diet target, not a second pool added on eat.
- **Raw diet:** unclamped character value the eat lerp moves. It can sit above `nutrition.max-diet`.
- **Diet score:** effective value after the variety penalty, then clamped to `nutrition.max-diet` (committed 40). Tiers and the nutrition attribute use this value.
- **Variety:** a per-player rolling history of eaten main and extra origins. Low variety is a percent penalty on raw diet before the clamp.
- **Respawn food:** `nutrition.respawn-food` (committed 80). Code default is also 80 if the key is absent.

## Invariants

- Eat gain is `ceil(getFinalFood)`, then clamped to `max-food`. Zero gain skips diet and variety.
- Diet lerp moves raw diet toward the item nutrition: `(itemNutrition - rawDiet) * (actualFoodGain / maxFood) * lerp-step-rate`.
- Effective diet is `clamp(rawDiet * (1 - varietyPenalty), 0, max-diet)`. The penalty is not applied to a score that was already clamped.
- Variety uses the last `history-meals` (committed 32). Mains weigh `main-weight` (1.0) and extras weigh `extra-weight` (0.25). Target spread is `target-ingredients` (8). `max-penalty-percent` (60) is the penalty at one effective ingredient. An empty history has no penalty yet.
- A nutrition tier change also shows the variety tier and the exact penalty percent, using the same `diet-tiers` labels. A variety change by itself does not send that message.
- If carve or sausage code has already divided nutrition, this service never sees the original level. Fix the portion math, not this lerp, when a slice should match the roast.
- Battle blocks eating through SimpleFactions (`BattleFoodGate`).
- Drain removes `drain-amount` on `drain-interval` seconds while the plugin tasks are running. Legacy key `drain-interval-ticks` is still read if the seconds key is absent (`ticks / 20`).
- No active character, or RPCharacters disabled: eat is logged and skipped.

## Inputs and outputs

`FoodConsumeListener` and `DrinkConsumeListener` call `NutritionService.tryApplyEat`. Bowls go through `BowlEatHandler`. An empty-hand click drinks soup in a bowl. A sneaking click does not, so the bowl can be carried. A Cooking item with `edible: false` cancels consumption and tells the player it needs to be prepared. `seafood_whole` is edible and applies its type food and nutrition.

Death/respawn handling is `NutritionLifecycleListener` (see `NutritionDeathFoodTest`).

Logs: `plugins/Cooking/logs/nutrition.log` when `logging` is on. `wipe-log: true` clears that file on enable.

## State and persistence

Character food, raw diet, and effective diet belong to RPCharacters (`raw-diet-score` and `diet-score`). Variety history belongs to the player PDC key `variety_history`. Cooking writes the effective diet to the nutrition attribute named by `attribute-name`.

## Runtime flow

```text
consume
  -> ceil(final food) added to character food
  -> raw diet lerps toward final nutrition
  -> meal appended to player variety history
  -> effective diet = raw after the variety penalty, then clamped
  -> tier notify, save, display sync, attribute bridge
```

`NutritionDrainTask` and `SaturationGuard` / `RegenBlocker` / `FoodLevelChangeGuard` keep vanilla hunger from fighting the character value.

## Configuration

`config.yml` `nutrition`:

- `max-food`, `respawn-food`, `max-diet`
- `attribute-name`
- `drain-amount`, `drain-interval`, `lerp-step-rate`
- `diet-tiers` (`id`, `min-percent`, `label`)
- `variety.enabled`, `variety.history-meals`, `variety.target-ingredients`, `variety.max-penalty-percent`, `variety.main-weight`, `variety.extra-weight`

## Integrations

Soft depend RPCharacters. Soft depend SimpleFactions for battle. Attribute display uses the configured attribute name.

## Edge cases

- Food at cap: no diet lerp, because actual gain is 0.
- Quality and tag multipliers already sit inside `getFinalNutrition` before the lerp.
- Sauce nutrition is included in `getFinalNutrition`.

## Source map

- `src/main/java/net/tfminecraft/cooking/nutrition/`
- `src/main/resources/config.yml` (`nutrition`, `logging`, `wipe-log`)

## Tests

- `NutritionDeathFoodTest`
- `VarietyRulesTest`

Eat battle gate has no unit test.

## Change checklist

- New nutrition config key: `NutritionConfig`, this doc, configuration reference.
- Do not store diet in item PDC.
