# Add a food type

1. Add a key to `src/main/resources/types.yml` with `name`, `food`, `nutrition`, and `model` if it has a custom model.
2. Add the model to `models.yml` and the ItemsAdder item if players must see it.
3. If the item should age, set `age` and rely on `tags.yml`. If it must not be rewritten by the scanner, set `update: false`.
4. If another item turns into it, add a `conversions.yml` line or a station output string.
5. Update [../systems/food-items.md](../systems/food-items.md) only if the template rules changed. A new id that follows the existing shape does not need a new paragraph, but the diff still touches `types.yml`, so either update the system doc with a one-line note or file an impact waiver. Prefer a short note in the system doc when the type changes eat math.
6. Run `mvn test` if you touched Java.
