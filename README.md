# Cooking

> From farm and pasture to a shared table in TF-Minecraft.

Cooking makes food an interactive part of everyday life. Players grow and gather ingredients, care for livestock, prepare meals using kitchen furniture, and serve dishes whose quality, freshness, and variety matter to their character's diet.

## Features

- **Working kitchens** — fry, boil, prepare sauces and soups, and bake with cookware, heat sources, and fuelled ovens. Leaving food cooking too long can burn it.
- **Food preparation** — milling stones, mixing bowls, butter churns, sausage makers, and baking trays provide different ways to transform ingredients.
- **Serving and sharing** — plate dishes, ladle soup into bowls, and carve foods into portions for the table.
- **Quality and freshness** — ingredient quality and composition influence finished dishes, while aging and food tags carry preparation history through the kitchen.
- **Farming and husbandry** — crop fertility and harvest quality sit alongside animal care, breeding, genetics, and produce collection.
- **Character nutrition** — meals feed a character's food reserve and influence diet quality, with variety rewarding a broader selection of ingredients.

## A complete food journey

Food crops need an open column above them. Cave ceilings and building roofs prevent vanilla and CustomCrops planting, including automatic replanting. Glass greenhouses are allowed by default. Nether wart, mushrooms, and the CustomCrops yeast crop can still be planted indoors.

The `planting` section of `crops.yml` controls `require-open-sky`, `allow-glass-roofs`, additional `allowed-cover` block materials, and `exempt-vanilla` / `exempt-custom` crop lists. Lists replace their defaults; an empty exemption list requires open sky for those crops too. Reload with `cooking reload`. Existing configs without this section use the defaults above. Adding a roof after planting does not remove existing crops or change their growth rules.

A harvest can become flour, dough, bread, or part of a cooked dish. Milk and meat enter their own preparation chains, and the result can be served through furniture as well as carried as food items. Cooking connects these activities into a shared system for farmers, cooks, and diners.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Cooking/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
