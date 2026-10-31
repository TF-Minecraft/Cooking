# Cooking

> From farm and pasture to a shared table in TF-Minecraft.

Cooking makes food an interactive part of everyday life. Players grow and gather ingredients, care for livestock, prepare meals using kitchen furniture, and serve dishes whose quality, freshness, and variety matter to their character's diet.

## Features

- **Working kitchens** — fry, boil, prepare sauces and soups, and bake with cookware, heat sources, and fuelled ovens. Leaving food cooking too long can burn it. The frying pan fries eggs, grills mushrooms and pops chopped corn into popcorn: a cooking option can name the food a piece turns into once it is done (`output` in `types.yml`).
- **Popcorn machine** — put up to 16 corn (one per right-click) and up to two toppings (caramel, sugar, butter, salt) in a popcorn machine and start it with an empty hand: the kettle rattles, kernels pop out and land on a rising heap behind the glass. Once it stops, a right-click takes out 4 popcorn per corn, each with its corn's stars and every topping (Salted Caramel Popcorn) (`popcorn-machines.yml`). Caramel is sugar and butter boiled in a cup of water in the saucepan and bottled (`condiments.yml`).
- **Food preparation** — milling stones, mixing bowls, butter churns, sausage makers, and baking trays provide different ways to transform ingredients.
- **Sandwiches** — lay a slice of bread on a cutting board, add spreads, cheese and vegetables, close it with another slice and pick it up. Its fillings name it, and its food, quality and freshness come from its layers (`sandwiches.yml`). Its layers also earn it bonuses such as Hearty, Buttered or Well-Rounded (`bonuses` in `sandwiches.yml`). Fried meat goes in too: fried beef makes it a Hamburger (a Cheeseburger with cheese), and fried pork with lettuce and tomato a BLT (`kinds` in `sandwiches.yml`).
- **Pizzas** — place a pizza peel, put dough on it, spread a paste and add cheese, vegetables, fruit, sausage and spices, then carry the peel into a lit oven to bake. A baked pizza is taken off a slice at a time. Its toppings name it, and its food, quality and freshness come from its layers (`pizzas.yml`). Its toppings also earn it bonuses such as Saucy, Extra Cheesy or Well Seasoned (`bonuses` in `pizzas.yml`).
- **Salads** — right-click a placed bowl with chopped or cooked veg, fruit, cheese or meat, add a dressing or seasoning, and toss it with an empty hand. It stays in the bowl and is eaten from it. Its ingredients name it, its kind (fruit, greek, carrot, mixed) gives it its look, and it earns bonuses such as Dressed or Crisp (`salads.yml`).
- **Stock and mashed potatoes** — boil two bones in a cup of water in the saucepan and bottle the stock (`condiments.yml`); a soup started with stock instead of water is Rich. Put four boiled cut potatoes and a butter (with milk or salt if you like) in an empty pot, mash them without water or heat, and ladle the mash onto a plate beside the other food (`pot.yml`).
- **Pies** — line a pie tin with dough, fill it with fruit, meat, mashed potato, vegetables or cheese, and bake it in the oven like the bread tray. The whole pie comes out of the tin with an empty hand and is cut into four slices on the cutting board. Its fillings make its kind (Apple, Key Lime, Mixed Berry, Meat, Shepherd's) and name, and its food, quality and freshness come from its dough and fillings (`pies.yml`).
- **Mushrooms and hidden poison** — grilled mushrooms go into sandwiches and salads, raw or grilled ones onto pizzas and into pies, and raw ones boil into soup; in a dish both kinds are just "Mushroom". Each red mushroom poisons the dish a step further (Tainted, Poisoned, Deadly: less and less nutrition, the same food). In a dish of 3 stars or more with its red mushrooms grilled (or its own bake cooked, its soup boiled) the poison is hidden: the lore shows no tag and the Food and Nutrition it would have without it, but eating applies it. Any dish bonus can count an origin and hide its tag this way (`count: origin`, `hide-at-stars`, `hide-tags`, `hide-dish-tags` in the dishes' `bonuses`; `tags.yml` poison).
- **Serving and sharing** — plate dishes, ladle soup into bowls, and carve foods into portions for the table.
- **Quality and freshness** — ingredient quality and composition influence finished dishes, while aging and food tags carry preparation history through the kitchen.
- **Farming and husbandry** — crop fertility and harvest quality sit alongside animal care, breeding, genetics, and produce collection.
- **Fish and seafood** — turn supported catches into cooking ingredients that keep their catch size; fish of one kind and quality stack whatever their size.
- **Character nutrition** — meals feed a character's food reserve and influence diet quality, with variety rewarding a broader selection of ingredients.
- **MMOItems crafting stations** — the `cooking{item="seafood(type=seafood_whole;origin=Salmon)",amount=64,display="Salmon"}` ingredient takes Cooking food, plus the plain items Cooking converts on pickup (a fresh catch counts as the salmon it becomes). `item` is a `c.` path without the prefix and must name a food `type`; quality and tags filters work too. Cooking claims those items before MMOItems' `vanilla{}` type, so use `cooking{}` for salmon, wheat, sugar, beef and the other converted foods. Cancelling a queued craft refunds the least the line accepts: its lowest quality, aged out unless the line pins that tag.

## A complete food journey

Crops are planted outdoors under open sky, beneath the glass roof of a greenhouse, or under a low pergola of slabs, stairs and signs. A harvest can become flour, dough, bread, or part of a cooked dish. Milk and meat enter their own preparation chains, and the result can be served through furniture as well as carried as food items. Cooking connects these activities into a shared system for farmers, cooks, and diners.

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
