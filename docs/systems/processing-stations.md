# Processing stations

## Purpose

These stations transform items without the frying/pot heat loop: cutting board, fire pit carve setup, baking trays, milling, mixing, butter churn, sausage maker, meat hook, and trough.

## Vocabulary

- **Crafting station:** a recipe in `crafting-stations.yml` (cutting board and similar).
- **Carve sequence:** ordered cuts in `carve-sequences.yml`, used after a roast exists.
- **Stir / crank / churn:** a counted interaction (`mixing-stir-count`, sausage crank, `butter-churn-count`).

## Invariants

- Cutting-board slots that share a recipe must be the same ingredient. Vanilla vegetables use `cut_vegetables` / `chop_vegetables`. CustomCrops vegetables use `cut_vegetables_2` / `chop_vegetables_2`, which keep `valuable: true` on `vegetable_cut_2` and `vegetable_chopped_2`. The output lineage is the composed input origins.
- `cut_seafood` cuts one `seafood_whole` per knife use into one portion. A stack larger than one is decremented in place. Other slots stay. Food and nutrition stay the portion type levels. Quality, origin, lineage, size, cut class, and CustomFishing id are copied.
- Dough, flour, butter, and sausage keep main and extra origins through their station state. A loaf copies the dough lineage. The display ingredient list stays the short name phrase.
- Milling recipes are data in `milling-recipes.yml`, not Java switches.
- Mixing dough needs the configured flour, water, and yeast inputs, then `mixing-stir-count` stirs (committed value 3).
- Butter churn loads milk and finishes after `butter-churn-count` (committed value 3) onto a butter plate.
- Sausage maker sums input meat food and averages input nutrition on the chain, then carve splits the food. See portions.
- Trough accepts vegetables and completes into universal feed (`trough-feed`). It does not cook food.
- Meat hook carving uses `carve-tool`.

## Inputs and outputs

| Station | Config | Output |
| --- | --- | --- |
| Cutting board | `crafting-stations.yml` | cut or chopped food, composition stars. Seafood is one portion per whole item |
| Baking tray | `baking-trays.yml` | bread while oven heat is present |
| Milling stone | `milling-recipes.yml` | flour or other mill output |
| Mixing bowl | `config.yml` `mixing-*` | dough |
| Butter churn | `config.yml` `butter-*` | butter item |
| Sausage maker | `config.yml` `sausage-maker-*` | sausage chain |
| Meat hook | `carve-sequences.yml` | one cut per knife click |
| Trough | `config.yml` `trough*` | universal feed |

Fire pit roasting of a whole joint is a crafting/cooking path that leaves a carvable roast. Cuts are not produced by the fire pit itself.

## State and persistence

Station progress lives on the furniture or on the item (carve index, cook time PDC). Baking and churn also run aging tasks while the plugin is enabled.

## Runtime flow

Handlers listen for InteractibleFurniture events, validate the held tool, mutate slots, and build the result with `ItemBuilder` plus `CompositionQualityResolver` where several foods are inputs.

## Configuration

Keys listed above. Visual wobble and cooldown ticks are config, not gameplay nutrition.

## Integrations

Same furniture and ItemsAdder ids as [cooking-stations.md](cooking-stations.md).

## Edge cases

- `cookware.yml` is not this system. Heat and tools are `config.yml`.
- Sausage nutrition is an average of the meats. Soup does not take nutrition from the pot ingredients.

## Source map

- `src/main/java/net/tfminecraft/cooking/crafting/`
- `src/main/java/net/tfminecraft/cooking/baking/`
- `src/main/java/net/tfminecraft/cooking/milling/`
- `src/main/java/net/tfminecraft/cooking/mixing/`
- `src/main/java/net/tfminecraft/cooking/churn/`
- `src/main/java/net/tfminecraft/cooking/sausagemaker/`
- `src/main/java/net/tfminecraft/cooking/hook/`
- `src/main/java/net/tfminecraft/cooking/trough/`
- `src/main/resources/crafting-stations.yml`
- `src/main/resources/baking-trays.yml`
- `src/main/resources/milling-recipes.yml`

## Tests

- `TroughIngredientsTest`
- `IngredientLineageTest`

Other stations have no unit tests.

## Change checklist

- New recipe: [../how-to/add-station-recipe.md](../how-to/add-station-recipe.md).
- New station class: register the listener in `Cooking.registerListeners` and add a row to this doc.
