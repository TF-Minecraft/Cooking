# External asset ids

Cooking config stores ids. The resource packs store the models and items. They must match.

## ItemsAdder

Namespace `tfmc_cooking` under `ItemsAdder/tfmc_cooking/`.

Contents YAML:

- `contents/items.yml`
- `contents/ingredients.yml`
- `contents/intermediates.yml`
- `contents/furniture.yml`
- `contents/categories.yml`

Typical paths in `config.yml`: `ia.tfmc_cooking:ladle`, `cutting_knife`, `water`, `flour`, `butter`.

CustomCrops seeds use `ia.playbox_custom_crops:<crop>_seeds`, except nutmeg: `ia.playbox_custom_crops:nut_seeds`.

Produce items are `tfmc_cooking:<id>` (example `tfmc_cooking:tomato`).

Seafood portions use raw, cooked, burnt, boiled, and rotten items: `fish_filet_*`, `jellyfish_cubes_*`, and `octopus_*`. Red-meat steak and poultry filet also have `red_meat_steak_boiled` and `poultry_roast_filet_boiled`. There is no boiled poultry-leg or pork item.

## InteractibleFurniture

Type ids in `config.yml` match furniture definitions: `frying_pan`, `saucepan`, `pot`, `bowl`, `mixing_bowl`, `milling_stone`, `trough`, `butter_churn`, `butter_plate`, `fire_pit`, `meat_hook`, `sausage_maker`, `oven_bottom`, `oven_top`, `liquid_container`, `plate`.

## CustomCrops

Crop ids in `crops.yml` `source: customcrops` match `CustomCrops/contents/crops/<id>.yml`.

Do not put fertility keys in those YAML files.

## Vanilla TLibs

`v.water_bucket`, `v.wheat_seeds`, `v.shears`, `v.bone`, and similar. `m.` paths are the materials plugin, used by husbandry drops.

## Generated files

Do not edit `target/`. Do not treat `ItemsAdder/**/resourcepack/**` model JSON as the gameplay spec. Behavior lives in Java and the YAML listed in [configuration.md](configuration.md).
