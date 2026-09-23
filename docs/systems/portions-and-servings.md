# Portions and servings

## Purpose

A roast, sausage chain, or pot is one prepared batch. Players take pieces or scoops. Food measures how much of the batch a serving is. Nutrition measures the level of that food.

## Vocabulary

- **Edible cut:** a `carve-sequences.yml` entry with a food `output`. Bone `item` cuts are not edible.
- **Start remaining:** `start-remaining` on a sequence, including non-food cuts. Poultry 6, red meat 8, sausage 5.
- **Base override:** the roast or chain stored its own food and nutrition on the item.
- **Scoop:** one ladle serving from a mashed pot. Count is `pot-soup-scoops`.

## Invariants

- Food splits across portions. Nutrition stays the batch level on every portion and on what remains.
- A carved edible piece copies the roast lineage, then composition records the same origins on the piece.
- Soup food per scoop is template food divided by `pot-soup-scoops`. Soup nutrition stays the template level. `pot-soup-height-divisor` is visual only.
- One whole seafood item becomes one portion. Food and nutrition stay the portion type levels: 6 and 6 for a fish filet, 5 and 5 for jellyfish, and 7 and 7 for octopus. Size stays on the item and does not change the stack or the food.
- Sauce scoops still copy the full sauce template food and nutrition. A valuable ingredient adds the Flavourful tag there too.
- A plain roast (no stored base) shows remaining food as the sum of cut `food` values still on it. Its nutrition is the roast type level, not that sum. The cut piece uses the piece type's own food and nutrition.
- A roast with a stored base splits food by edible cuts still left. Bones stay in the sequence and do not take a share of the food. Piece nutrition and leftover nutrition are `getBaseNutrition()`.
- A sausage chain sums input base food and averages input base nutrition. Carve then splits only the food.

See [../decisions/0001-food-vs-nutrition.md](../decisions/0001-food-vs-nutrition.md).

## Inputs and outputs

| Action | Food | Nutrition |
| --- | --- | --- |
| Ladle soup | template food / `pot-soup-scoops` | template nutrition |
| Ladle sauce | full `sauce` food | full `sauce` nutrition |
| Carve, no override | piece type food; roast shows sum of remaining cut food | piece type nutrition; roast shows its type level |
| Carve, override | stored food times edible cuts left / edible cuts | stored nutrition, on the piece and on the leftover |
| Cut seafood | portion type food, one piece | portion type nutrition |

## State and persistence

`carve_sequence`, `carve_next_index`, `carve_remaining` on the roast. Initialized from the sequence when the roast is built.

A slaughter roast keeps the configured number of edible cuts, taken from the end of the sequence. The bone remains after the last meat. One cut is that last portion of meat, with that cut's food, on the model stage just before the skeleton. It is not the bone by itself. The model stage is `carve_next_index + 1`. The edible-cut count is not a model stage.

## Runtime flow

```text
knife on meat hook
  -> CarveHandler reads next CarveCut
  -> food cut: clone output template, copy cooked/freshness/warmth
  -> if roast.hasBaseOverride: piece food = stored food / edible cuts; piece nutrition = stored level
  -> advance index and remaining
  -> item cut: resolve TLibs/vanilla item, no food stats
```

```text
ladle on mashed pot
  -> clone soup template
  -> composition stars and freshness only
  -> food = template food / pot-soup-scoops
  -> remaining servings - 1
  -> clear pot at 0
```

## Configuration

- `carve-sequences.yml`
- `types.yml` roast `carve-sequence` overrides and soup/sauce `food` / `nutrition`
- `config.yml` `pot-soup-scoops`, `pot-soup-height-divisor`, `carve-tool`

## Integrations

Meat hook furniture id: `meat-hook`. Knife: `carve-tool`.

## Edge cases

- `hasBaseOverride` is sticky once either setter runs. Copying nutrition onto a piece stores that level; it does not divide it.
- Eating a leftover roast uses `getFinalFood` / `getFinalNutrition`. Food follows portions still left. Nutrition is the batch level, then quality and tag multipliers.

## Source map

- `src/main/java/net/tfminecraft/cooking/carve/CarveHandler.java`
- `src/main/java/net/tfminecraft/cooking/carve/CarvableRoastUtils.java`
- `src/main/java/net/tfminecraft/cooking/carve/CarveSequence.java`
- `src/main/java/net/tfminecraft/cooking/sausagemaker/SausageItems.java`
- `src/main/java/net/tfminecraft/cooking/cooking/PotReference.java`
- `src/main/java/net/tfminecraft/cooking/cooking/SauceReference.java`
- `src/main/resources/carve-sequences.yml`

## Tests

- `PortionRulesTest`
- `IngredientLineageTest`

## Change checklist

- Sequence length change: update edible-count assumptions and the player wiki carving table.
- Scoop count change: `pot-soup-scoops` is the food divisor. Do not use `pot-soup-height-divisor` for food.
