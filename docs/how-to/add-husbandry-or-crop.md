# Add husbandry or crop behavior

## Crop

1. Add `crops.yml` with `source`, `block` or CustomCrops id, `seed`, and `affection`.
2. Add the produce conversion in `conversions.yml`.
3. Leave seeds unconverted. Nutmeg seeds are `nut_seeds`.
4. Do not add fertility to CustomCrops YAML.
5. Extend `CropHarvestQualityTest` or `CropGrowthChanceTest` when the formula changes.
6. Update [../systems/crops-and-farming.md](../systems/crops-and-farming.md).

## Husbandry

1. Add `species.<TYPE>` capabilities in `husbandry.yml`.
2. New persistence needs a `user_version` migration.
3. A gene-rule change that must reset live animals bumps `stats-revision`.
4. Keep genetics, care, yield, and stars distinct.
5. Update [../systems/husbandry.md](../systems/husbandry.md) and the nearest `Husbandry*Test`.
