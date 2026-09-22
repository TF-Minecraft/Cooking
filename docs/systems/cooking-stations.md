# Active cooking, heat, liquids, plates

## Purpose

Heated furniture turns ingredients into cooked food: frying pan, saucepan, pot, oven, and liquid containers. Plates and bowls serve an item a player can eat.

## Vocabulary

- **Heat source / consumer:** ids in `config.yml` `heat`. A pan or pot cooks only when a source below it is hot.
- **Pot scoop:** one ladle serving. Count is `pot-soup-scoops` (committed value 3). The visual drain uses `pot-soup-height-divisor` (committed value 4) and is not the food split.
- **Bowl:** furniture the player pours a scooped soup into before eating.

## Invariants

- Cooking time in station config is real seconds, ticked about once per second by `CookingManager`.
- Frying burns when left past the method's burn time.
- Soup exists only after a pot slot is mashed (`Tag.MASHED`). Ladle before that does nothing useful (`scoop` returns immediately).
- A mash applies to any `mashable` item already in the pot. It sets that item's `cooked` track to `3` (boiled), stops the pot timer so burn cannot replace it, and the ladle copies every visible pot ingredient, including mashed ones, onto the bowl.
- The pot has five main slots (`input_1` through `input_5`). A sixth main is refused. After the pot is soup, one garnish, one spice, one salt, and one pepper are stored on the pot with no visible slot (`pot.extras`). They still season the scoop. They are not copied onto the bowl model.
- Each soup scoop clones the `soup` type, then sets food to template food / `pot-soup-scoops`. Nutrition stays the template level. A valuable ingredient adds Flavourful (+10% nutrition, food unchanged). See portions.
- Sauce scoops clone the `sauce` type: full template food and nutrition, quality from composition. A valuable ingredient adds Flavourful the same way as soup. Soup and sauce store the slot origins as lineage. A nested sauce keeps that lineage on the plated food.
- Oven fuel and burn rates are `config.yml` oven keys, not `cookware.yml`.

## Inputs and outputs

Players place items through InteractibleFurniture. Water bucket fills a pot (`pot-water-input`). Ladle and masher ids are `config.yml` `ladle` and `masher`.

Pot output is one soup item in the ladle hand, with seasoning/addon tags and a thickness track copied from the pot.

## State and persistence

Live station contents stay in the furniture. Scooped soup may store encoded slot data in `slot_data` PDC.

Liquid containers track block fill (`liquid-blocks-per-bucket`, `liquid-max-blocks`, `liquid-blocks-per-cup`) and age while stored.

## Runtime flow

```text
furniture click
  -> station reference (FryingReference, SauceReference, PotReference)
  -> heat gate
  -> cook seconds advance
  -> take or scoop builds a FoodItem
  -> DishCookedEvent
```

Oven: `OvenBurnManager` consumes fuel and exposes heat to consumers. Bread trays are a processing station that only bake while the oven is lit.

## Configuration

Furniture ids and heat graph: `config.yml`.

Food method times: `types.yml` `cooking-options` (`fire_pit`, `frying_pan`, and similar).

## Integrations

InteractibleFurniture type ids must match `config.yml`. ItemsAdder ids must match ladle, masher, water, and liquid blocks.

## Edge cases

- `pot-soup-height-divisor` can differ from `pot-soup-scoops`. Do not use the height divisor as a food divider.
- Empty-hand take versus tool use is station-specific. Read the reference class before changing a click.

## Source map

- `src/main/java/net/tfminecraft/cooking/cooking/`
- `src/main/java/net/tfminecraft/cooking/heat/`
- `src/main/java/net/tfminecraft/cooking/oven/`
- `src/main/java/net/tfminecraft/cooking/liquid/`
- `src/main/java/net/tfminecraft/cooking/manager/PlateManager.java`
- `src/main/java/net/tfminecraft/cooking/nutrition/BowlEatHandler.java`
- `src/main/resources/config.yml`

## Tests

`IngredientLineageTest` covers soup, sauce, and frying lineage. `PotMashTest` covers mash acceptance. Nutrition death food is covered by `NutritionDeathFoodTest`.

## Change checklist

- New furniture id: `config.yml`, external asset reference, player wiki if the click changes.
- Soup serving math: portions doc and `PotReference.scoop`.
