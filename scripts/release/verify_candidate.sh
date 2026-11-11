#!/usr/bin/env bash
# Run in the exact candidate checkout, using the controller at immutable B.
set -euo pipefail
controller="$(cd "$(dirname "$0")" && pwd)"
kind="${1:?release or snapshot required}"
evidence="${2:?absolute evidence directory required}"
mkdir -p "$evidence"
[[ "$kind" == release || "$kind" == snapshot ]] || exit 1
[[ "$(git rev-parse HEAD)" == "$(if [[ "$kind" == release ]]; then echo "$RELEASE_SHA"; else echo "$SNAPSHOT_SHA"; fi)" ]] || exit 1
export QUIET_BUILD_TIMEOUT_SECONDS=1800
# The canonical gate validates all non-demo tags and never repairs this checkout.
xvfb-run scripts/run-full-build-quiet.sh --validate-only
# Keep the exact gate log as evidence, including the aggregated test summary.
cp "$(ls -t .agents/logs/full-build-*.log | head -n 1)" "$evidence/full-build.log"
if [[ "$kind" == release ]]; then
    ./mvnw -B -q -pl ta4j-examples -am -DskipTests install
    ./mvnw -B -q -pl ta4j-examples exec:java \
        -Dexec.mainClass=ta4jexamples.doc.RemovalReadyDeprecationScanner \
        "-Dexec.args=--repo-root $PWD --pom-file pom.xml --target-removal-version $RELEASE_VERSION --include-overdue --fail-on-due --output-json $evidence/deprecations.json --output-md $evidence/deprecations.md"
    ./mvnw -B -DskipTests -Dgpg.skip=true -Pproduction-release package 2>&1 | tee "$evidence/production-build.log"
    bash "$controller/release_helpers.sh" artifact-manifest --version "$RELEASE_VERSION" --output "$evidence/artifact-manifest.txt" --strict
    python3 "$controller/release_candidate.py" manifest --output "$evidence/production-manifest.json"
    bash "$controller/release_helpers.sh" javadoc-warnings \
        --baseline "$controller/javadoc-warning-baseline.txt" --output "$evidence/javadoc-warnings.txt" \
        --fail-on-new "$evidence/production-build.log"
fi
args=(--evidence "$evidence/full-build.log")
if [[ "$kind" == release ]]; then
    args+=(--evidence "$evidence/deprecations.json" --evidence "$evidence/production-manifest.json" --evidence "$evidence/javadoc-warnings.txt")
fi
python3 "$controller/release_candidate.py" receipt --kind "$kind" "${args[@]}" --output "$evidence/receipt.json"
