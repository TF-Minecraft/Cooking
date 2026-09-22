#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after downloading the pinned JARs.
# Hash-qualified versions prevent different private JARs sharing a Maven cache key.
sha256sum --check .github/dependencies.sha256

mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/gson-2.10.1.jar" -DgroupId="local" -DartifactId="gson" \
    -Dversion="2.10.1-tfmc-4241c14a7727" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/joml-1.10.8.jar" -DgroupId="local" -DartifactId="joml" \
    -Dversion="1.10.8-tfmc-bf1951014517" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MythicLib-1.7.jar" -DgroupId="local" -DartifactId="MythicLib" \
    -Dversion="1.7-tfmc-660ff2a6ec86" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MMOCore-1.13.1.jar" -DgroupId="local" -DartifactId="MMOCore" \
    -Dversion="1.13.1-tfmc-14850d745437" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/CustomCrops-3.6.49.jar" -DgroupId="local" -DartifactId="CustomCrops" \
    -Dversion="3.6.49-tfmc-0aa5d3cb6bad" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/CustomFishing-2.3.27.jar" -DgroupId="local" -DartifactId="CustomFishing" \
    -Dversion="2.3.27-tfmc-7d3083bd0599" -Dpackaging=jar -DgeneratePom=true "$@"
