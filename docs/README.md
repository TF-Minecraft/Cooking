# Cooking internal docs

These files describe how the plugin works. The player wiki describes how to play.

If behavior, tests, config, and these docs disagree, the change is not done.

## Map

| System | Document | Primary code | Config |
| --- | --- | --- | --- |
| Bootstrap and reload | [architecture/bootstrap.md](architecture/bootstrap.md) | `Cooking.java` | all shipped YAML |
| Persistence | [architecture/persistence.md](architecture/persistence.md) | `husbandry/`, `item/` | `Data/husbandry.db`, item PDC |
| Integrations | [architecture/integrations.md](architecture/integrations.md) | bridges and listeners | furniture and item paths in `config.yml` |
| Food items | [systems/food-items.md](systems/food-items.md) | `item/`, `utils/FoodParser.java` | `types.yml`, `models.yml`, `naming.yml` |
| Tags and aging | [systems/freshness-tags.md](systems/freshness-tags.md) | `manager/TagManager.java` | `tags.yml` |
| Quality and conversions | [systems/quality-composition.md](systems/quality-composition.md) | `quality/` | `quality.yml`, `composition.yml`, `conversions.yml`, `permission_effects.yml` |
| Active cooking and heat | [systems/cooking-stations.md](systems/cooking-stations.md) | `cooking/`, `heat/`, `oven/`, `liquid/`, `manager/PlateManager.java` | `config.yml` heat and furniture ids |
| Processing stations | [systems/processing-stations.md](systems/processing-stations.md) | `crafting/`, `baking/`, `milling/`, `mixing/`, `churn/`, `sausagemaker/`, `hook/`, `trough/` | `crafting-stations.yml`, `baking-trays.yml`, `milling-recipes.yml` |
| Portions | [systems/portions-and-servings.md](systems/portions-and-servings.md) | `carve/`, `cooking/PotReference.java` | `carve-sequences.yml`, `pot-soup-scoops` |
| Nutrition | [systems/nutrition.md](systems/nutrition.md) | `nutrition/` | `config.yml` `nutrition:` |
| Crops and farming | [systems/crops-and-farming.md](systems/crops-and-farming.md) | `crops/`, `farming/` | `crops.yml`, `farming.yml` |
| Husbandry | [systems/husbandry.md](systems/husbandry.md) | `husbandry/` | `husbandry.yml` |

Reference: [reference/configuration.md](reference/configuration.md), [reference/item-pdc.md](reference/item-pdc.md), [reference/commands-permissions.md](reference/commands-permissions.md), [reference/external-assets.md](reference/external-assets.md).

How-to: [how-to/add-food-type.md](how-to/add-food-type.md), [how-to/add-station-recipe.md](how-to/add-station-recipe.md), [how-to/add-config-option.md](how-to/add-config-option.md), [how-to/add-husbandry-or-crop.md](how-to/add-husbandry-or-crop.md), [how-to/update-docs.md](how-to/update-docs.md).

Decisions: [decisions/0001-food-vs-nutrition.md](decisions/0001-food-vs-nutrition.md), [decisions/0002-source-of-truth.md](decisions/0002-source-of-truth.md).

Ownership used by CI: [manifest.json](manifest.json).

## Template for a system doc

1. Purpose
2. Vocabulary
3. Invariants
4. Inputs and outputs
5. State and persistence
6. Runtime flow
7. Configuration
8. Integrations
9. Edge cases
10. Source map
11. Tests
12. Change checklist
