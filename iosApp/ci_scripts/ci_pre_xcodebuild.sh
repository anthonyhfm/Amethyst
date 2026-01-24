#!/bin/bash
set -euo pipefail

export JAVA_HOME="${CI_DERIVED_DATA_PATH}/JDK/Home"
export PATH="${JAVA_HOME}/bin:${PATH}"
export GRADLE_USER_HOME="${CI_DERIVED_DATA_PATH}/.gradle"

echo "JAVA_HOME=$JAVA_HOME"
java -version

chmod +x "${CI_PRIMARY_REPOSITORY_PATH}/gradlew"
