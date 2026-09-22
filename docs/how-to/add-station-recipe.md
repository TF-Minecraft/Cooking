# Add a station recipe

1. Pick the station doc: [../systems/cooking-stations.md](../systems/cooking-stations.md) or [../systems/processing-stations.md](../systems/processing-stations.md).
2. Data-only recipes go in `crafting-stations.yml`, `milling-recipes.yml`, or `baking-trays.yml`.
3. Mixing, churn, sausage, and pot counts go in `config.yml`.
4. If the recipe composes stars, confirm the `CompositionContext` and `composition.yml` roles.
5. If the output is carved or scooped, update [../systems/portions-and-servings.md](../systems/portions-and-servings.md).
6. Update the player wiki station table when the click or the result changes.
7. Register a new listener in `Cooking.registerListeners` and the processing doc.
