#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after downloading the pinned JARs.
# Hash-qualified versions prevent different private JARs sharing a Maven cache key.
sha256sum --check .github/dependencies.sha256

mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/gson-2.14.0.jar" -DgroupId="local" -DartifactId="gson" \
    -Dversion="2.14.0-tfmc-2cbd119bf196" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/joml-1.10.9.jar" -DgroupId="local" -DartifactId="joml" \
    -Dversion="1.10.9-tfmc-feca4db85337" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MythicLib-1.7.1-SNAPSHOT.jar" -DgroupId="local" -DartifactId="MythicLib" \
    -Dversion="1.7.1-SNAPSHOT-tfmc-225aa7f75d4e" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MMOCore-1.13.1-SNAPSHOT.jar" -DgroupId="local" -DartifactId="MMOCore" \
    -Dversion="1.13.1-SNAPSHOT-tfmc-81d511d08309" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/CustomCrops-3.6.56.jar" -DgroupId="local" -DartifactId="CustomCrops" \
    -Dversion="3.6.56-tfmc-93317bf08c9d" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/CustomFishing-2.3.27.jar" -DgroupId="local" -DartifactId="CustomFishing" \
    -Dversion="2.3.27-tfmc-7d3083bd0599" -Dpackaging=jar -DgeneratePom=true "$@"
