# Commands and permissions

Players have no Cooking command. The wiki records that on purpose.

Bukkit command: `cooking`. Permission: `cooking.admin` (default op).

| Usage | Role |
| --- | --- |
| `/cooking reload` | reload YAML, rebuild stations, restart drain and husbandry ticks, apply stats revision |
| `/cooking builditem <string>` | create a food from a FoodParser string, with or without `c.` |
| `/cooking preview <target> ...` | admin preview. Mixing bowl is the supported target |
| `/cooking food ...` | admin food debug |
| `/cooking husbandry spawn <type> [genetics] [care]` | spawn an untamed row |
| `/cooking husbandry save` | flush loaded animals |
| `/cooking heat` | heat debug |
| `/cooking nametest` | name debug |
| `/cooking qualitytest` | quality debug |

There is no `/cooking crop` and no BreedingBuddies command.

Permission nodes that change rolls live in `permission_effects.yml`. Crop pickup uses `tfmc.cooking.better_crops` after the harvest roll.

`plugin.yml` does not list every subcommand. `CommandManager` is the list that runs.
