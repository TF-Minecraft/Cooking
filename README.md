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

## TLibs build dependency

TLibs is a versioned Maven `provided` dependency. From this repository, prepare
it once with the shared installer, then build as usual:

```sh
python3 ../tlibs/tools/install-dependency.py --pom pom.xml
mvn clean verify
```

See [TLibs dependency setup](https://github.com/TF-Minecraft/TLibs/blob/v1.1.0/DEPENDENCIES.md)
for public release installation, offline builds and rollback.
Other declared build dependencies still need their usual preparation.
Use JDK 25 for this TLibs binary; the server must also run Java 25.

Builds and server runtime require Java 25 and [TLibs 1.1.0](https://github.com/TF-Minecraft/TLibs/releases/tag/v1.1.0).
