# 0001 Food amount versus nutrition level

## Status

Implemented. Current behavior is [../systems/portions-and-servings.md](../systems/portions-and-servings.md).

## Decision

Food is how much a serving fills the character food pool. A whole roast or a full pot is a larger amount than one slice or one scoop.

Nutrition is the level of that food. A slice has the same nutrition as the roast it came from. A soup scoop has the same nutrition as the pot. Splitting the batch does not dilute the level.

Diet already treats nutrition as a target level (`NutritionService` lerps diet toward `getFinalNutrition`). Dividing nutrition before that call feeds the diet system the wrong target.

## Consequences

- Soup: `pot-soup-scoops` divides template food. It does not divide nutrition. The height divisor stays visual.
- Carve: divide food by edible cuts. Copy nutrition onto the piece and leave the leftover roast's nutrition at the batch level.
- Sausage: summing every meat's nutrition into one chain, then dividing by link count, fights this decision. The chain level should be chosen as a level (for example a composed or averaged level), and each link should keep it.
- Plain roast display that sums per-cut nutrition is also a pile, not a level.

## Not in this decision

Star multipliers, tag multipliers, and sauce added on top still apply to whatever level the portion has. Those are modifiers, not portion splits.
