#!/usr/bin/env bash
set -euo pipefail

# Clear results file at start of each run
: > test_results.txt

# Python / linux tests
if [ -d linux ]; then
    set +e
    (cd linux && pip install --break-system-packages -q -e ".[test]" && PATH="/home/dev/.local/bin:$PATH" python3 -m pytest -q) 2>&1 | tee -a test_results.txt
    PYTHON_EXIT=${PIPESTATUS[0]}
    set -e
    if [ $PYTHON_EXIT -ne 0 ]; then
        exit $PYTHON_EXIT
    fi
fi

# Portable JDK resolution — try sandbox (Debian) path first, then Fedora, then generic.
for candidate in \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/java-21; do
    if [ -d "$candidate" ]; then
        JAVA_HOME="$candidate"
        break
    fi
done
export JAVA_HOME

# Write local.properties for the sandbox if it doesn't already exist.
if [ ! -f local.properties ]; then
    echo "sdk.dir=/opt/android-sdk" > local.properties
fi

GRADLE_OPTS="-Dorg.gradle.java.home=${JAVA_HOME}"

{
    ./gradlew $GRADLE_OPTS :app:test && \
    ./gradlew $GRADLE_OPTS :app:assembleDebug
} 2>&1 | tee -a test_results.txt
exit ${PIPESTATUS[0]}
