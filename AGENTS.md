# Cooking agent instructions

Cooking is a Paper plugin. Furniture stations, crops, husbandry, and nutrition live in this repo. Player-facing wiki pages live in ProvinceSystem and are not the implementation spec.

## Before editing

1. Read [docs/README.md](docs/README.md) and the system document that owns the files you will touch. Ownership is [docs/manifest.json](docs/manifest.json).
2. Treat [src/main/resources](src/main/resources) as the config source. Never edit `target/`.
3. Do not change gameplay while doing a documentation-only task.

## Build and test

From this directory:

```text
mvn test
mvn package
```

`mvn test` needs the local system jars declared in `pom.xml`. GitHub Actions does not have those jars, so CI runs the documentation checks only.

```text
python scripts/test_doc_manifest.py
python scripts/check-links.py
python scripts/check-doc-impact.py
```

## When behavior changes

Update, in the same change:

- the owning system doc
- contract tests when the rule is testable without a server
- config comments only when they describe behavior
- the player wiki only when a player-visible step changes

If code, tests, config, and docs disagree, stop and make them agree. Do not silently prefer one artifact.

A change that truly has no behavior impact must update [docs/impact-waiver.md](docs/impact-waiver.md) in the same diff, with `docs-impact: none` and a reason. See [docs/how-to/update-docs.md](docs/how-to/update-docs.md).

## Rules that are easy to get wrong

- Food is an amount and splits across portions. Nutrition is a level and stays on every piece and scoop. See [docs/systems/portions-and-servings.md](docs/systems/portions-and-servings.md) and [docs/decisions/0001-food-vs-nutrition.md](docs/decisions/0001-food-vs-nutrition.md).
- `cookware.yml` is shipped and not loaded.
- `plugin.yml` version and `pom.xml` version currently differ. Do not "fix" that inside an unrelated change.
- Player-facing strings must not use an em dash.
