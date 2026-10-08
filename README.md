# Cooking

> From farm and pasture to a shared table in TF-Minecraft.

Cooking makes food an interactive part of everyday life. Players grow and gather ingredients, care for livestock, prepare meals using kitchen furniture, and serve dishes whose quality, freshness, and variety matter to their character's diet.

## Features

- **Working kitchens** — fry, boil, prepare sauces and soups, and bake with cookware, heat sources, and fuelled ovens. Leaving food cooking too long can burn it.
- **Food preparation** — milling stones, mixing bowls, butter churns, sausage makers, and baking trays provide different ways to transform ingredients.
- **Serving and sharing** — plate dishes, ladle soup into bowls, and carve foods into portions for the table.
- **Quality and freshness** — ingredient quality and composition influence finished dishes, while aging and food tags carry preparation history through the kitchen.
- **Farming and husbandry** — crop fertility and harvest quality sit alongside animal care, breeding, genetics, and produce collection.
- **Fish and seafood** — turn supported catches into cooking ingredients while preserving catch size.
- **Character nutrition** — meals feed a character's food reserve and influence diet quality, with variety rewarding a broader selection of ingredients.

## A complete food journey

Crops are planted outdoors under open sky or beneath the glass roof of a greenhouse. A harvest can become flour, dough, bread, or part of a cooked dish. Milk and meat enter their own preparation chains, and the result can be served through furniture as well as carried as food items. Cooking connects these activities into a shared system for farmers, cooks, and diners.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Cooking/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests

With Java 21 and the [build dependencies](https://github.com/TF-Minecraft/Docs/blob/main/projects/Cooking/README.md#build-and-dependencies) prepared, run:

```sh
mvn -B --no-transfer-progress clean verify
```

The JUnit 5 suite covers food parsing and items, crops, husbandry, nutrition,
fishing and configuration loading, using MockBukkit and Mockito where Bukkit or
plugin APIs are involved. Build and release CI run the same verification. The
Build workflow uploads Surefire results from `target/surefire-reports/`. JaCoCo
requires 100% line coverage across all production classes, with no coverage
exclusions. HTML and XML
reports are `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml`,
and the Build workflow uploads that directory.
The tests do not replace checking kitchens, crops and livestock on a live Paper
server with the pinned integrations.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
