# Cooking

Paper plugin for furniture cooking, food items, crop quality, livestock, and nutrition.

Java 25, Spigot/Paper API 1.21.8. Hard dependencies: TLibs, InteractibleFurniture, TFMCCore. Soft dependencies: RPCharacters, MMOCore, CustomCrops, SimpleFactions.

## Develop

```text
mvn test
mvn package
```

`package` copies the jar to the local Cooking build folder set in `pom.xml`. Runtime YAML is [src/main/resources](src/main/resources). The server copies a missing file out of the jar on first start and does not overwrite an existing file.

## Documentation

Organisation documentation: [TF-Minecraft/docs](https://github.com/TF-Minecraft/docs/tree/main/projects/cooking).

Internal docs for people and coding agents: [docs/README.md](docs/README.md).

Agent workflow: [AGENTS.md](AGENTS.md).

The player guide is the ProvinceSystem wiki page `/wiki/cooking`, plus the animal husbandry and farming pages. That guide is not the implementation spec.

## Known drift

- `plugin.yml` says version `1.0`. `pom.xml` says `0.1.5-ALPHA`.
- `cookware.yml` is saved into the plugin folder and is not read by a loader.
