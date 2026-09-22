# Cooking

Paper plugin for furniture cooking, food items, crop quality, livestock, and nutrition.

Java 25, Spigot/Paper API 1.21.8. Hard dependencies: TLibs, InteractibleFurniture, TFMCCore. Soft dependencies: RPCharacters, MMOCore, CustomCrops, SimpleFactions.

## Build

With `GH_TOKEN` set to a token that can read ServerAssets:

```sh
python3 ../tlibs/tools/install-dependency.py --pom pom.xml
bash .github/scripts/prepare-release.sh
mvn clean verify
```

The runtime JAR is written to `target/`. PR builds run unit tests and upload a UTC `DEV-YYYYMMDD-HHmm` JAR. Numeric `v*` tags create a verified draft release.

## Documentation

- [Builds and releases](https://github.com/TF-Minecraft/Docs/blob/main/PIPELINES.md)
- [Project documentation](https://github.com/TF-Minecraft/Docs/tree/main/projects/Cooking)
- [Implementation guides](docs/README.md)
- [Agent instructions](AGENTS.md)

Runtime YAML lives in [src/main/resources](src/main/resources). The server copies a missing file from the JAR on first start and preserves existing configuration.

TLibs uses Maven `provided` scope with a checksum-pinned version. See [TLibs dependency setup](https://github.com/TF-Minecraft/TLibs/blob/5da8e77d0e0696bbff7d7064a2644072da9c6428/DEPENDENCIES.md) for authenticated and offline installation.

Builds and server runtime require Java 25. Local builds default to [TLibs 1.1.0](https://github.com/TF-Minecraft/TLibs/releases/tag/v1.1.0); CI resolves the latest published stable TLibs release for each build, verifies its checksum, and uses its exact version throughout that job.
