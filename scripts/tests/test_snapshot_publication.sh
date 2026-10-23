#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/release/release_helpers.sh"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
  return 0
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

new_temp_dir() {
  mktemp -d "${TMPDIR:-/tmp}/snapshot-publication.XXXXXX"
}

expect_output_value() {
  local file="$1"
  local key="$2"
  local expected="$3"
  local actual=""

  actual="$(awk -F= -v key="$key" '$1 == key { print substr($0, length($1) + 2); exit }' "$file")"
  if [[ "$actual" != "$expected" ]]; then
    fail "expected ${key}=${expected}, got ${actual:-<missing>}"
  fi
}

expect_file_contains() {
  local file="$1"
  local expected="$2"

  if ! grep -Fq -- "$expected" "$file"; then
    fail "expected ${file} to contain: ${expected}"
  fi
}

write_metadata_fixture() {
  local path="$1"
  local latest="$2"
  local versions="$3"
  local last_updated="$4"

  cat > "$path" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-parent</artifactId>
  <versioning>
    <latest>${latest}</latest>
    <versions>
${versions}
    </versions>
    <lastUpdated>${last_updated}</lastUpdated>
  </versioning>
</metadata>
EOF
}

write_artifact_metadata_fixture() {
  local path="$1"
  local version="$2"
  local resolved="$3"
  local last_updated="$4"

  cat > "$path" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>${version}</version>
  <versioning>
    <snapshot><timestamp>20260714.120000</timestamp><buildNumber>1</buildNumber></snapshot>
    <lastUpdated>${last_updated}</lastUpdated>
    <snapshotVersions>
      <snapshotVersion><extension>pom</extension><value>${resolved}</value><updated>${last_updated}</updated></snapshotVersion>
      <snapshotVersion><extension>jar</extension><value>${resolved}</value><updated>${last_updated}</updated></snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
EOF
}

write_maven_stub() {
  local path="$1"
  cat > "$path" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

local_repo=""
pom_file=""
previous_argument=""
for argument in "$@"; do
  case "$argument" in
    -Dmaven.repo.local=*) local_repo="${argument#*=}" ;;
  esac
  if [[ "$previous_argument" == "-f" ]]; then
    pom_file="$argument"
  fi
  previous_argument="$argument"
done
[[ -n "$local_repo" ]] || exit 2
[[ -s "$pom_file" ]] || exit 2
[[ "$local_repo" != "$HOME/.m2"* ]] || exit 2
[[ ! -e "$local_repo/org/ta4j/.fixture-attempt" ]] || exit 2
mkdir -p "$local_repo/org/ta4j"
: > "$local_repo/org/ta4j/.fixture-attempt"
if [[ -n "${STUB_POM_CAPTURE:-}" ]]; then
  cp "$pom_file" "$STUB_POM_CAPTURE"
fi

attempt=0
if [[ -f "$STUB_ATTEMPT_FILE" ]]; then
  attempt="$(cat "$STUB_ATTEMPT_FILE")"
fi
attempt=$((attempt + 1))
printf '%s\n' "$attempt" > "$STUB_ATTEMPT_FILE"
if (( attempt < STUB_SUCCESS_ATTEMPT )); then
  echo "snapshot not propagated" >&2
  exit 1
fi
if [[ "${STUB_FAIL_ATTEMPT:-}" == "$attempt" ]]; then
  echo "snapshot resolution failed" >&2
  exit 1
fi

python3 - "$pom_file" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

def local_name(tag):
    return tag.rsplit("}", 1)[-1]

root = ET.parse(sys.argv[1]).getroot()
dependencies = {}
for node in root.iter():
    if local_name(node.tag) != "dependency":
        continue
    values = {local_name(child.tag): (child.text or "").strip() for child in node}
    artifact = values.get("artifactId", "")
    dependencies[artifact] = (values.get("version", ""), values.get("type", ""))

expected = {
    "ta4j-parent": (os.environ["STUB_PARENT_RESOLVED_VERSION"], "pom"),
    "ta4j-core": (os.environ["STUB_CORE_RESOLVED_VERSION"], ""),
    "ta4j-examples": (os.environ["STUB_EXAMPLES_RESOLVED_VERSION"], ""),
    "ta4j-data": (os.environ["STUB_DATA_RESOLVED_VERSION"], ""),
    "ta4j-charting": (os.environ["STUB_CHARTING_RESOLVED_VERSION"], ""),
    "ta4j-catalog": (os.environ["STUB_CATALOG_RESOLVED_VERSION"], ""),
}
if dependencies != expected:
    raise SystemExit(f"unexpected consumer coordinates: {dependencies!r} != {expected!r}")
PY
echo "consumer coordinates validated" >&2
echo "download https://fixture:secret@example.invalid/repository/ ghp_123456789012345678901234567890" >&2

for artifact in ta4j-parent ta4j-core ta4j-data ta4j-charting ta4j-catalog ta4j-examples; do
  directory="$local_repo/org/ta4j/$artifact/$STUB_VERSION"
  mkdir -p "$directory"
  extension=jar
  if [[ "$artifact" == "ta4j-parent" ]]; then
    extension=pom
    resolved_version="$STUB_PARENT_RESOLVED_VERSION"
    printf '<project/>\n' > "$directory/$artifact-$resolved_version.pom"
  elif [[ "$artifact" == "ta4j-core" ]]; then
    resolved_version="$STUB_CORE_RESOLVED_VERSION"
    cp "$STUB_CORE_SOURCE" "$directory/$artifact-$resolved_version.jar"
  elif [[ "$artifact" == "ta4j-examples" ]]; then
    resolved_version="$STUB_EXAMPLES_RESOLVED_VERSION"
    cp "$STUB_EXAMPLES_SOURCE" "$directory/$artifact-$resolved_version.jar"
  else
    case "$artifact" in
      ta4j-data) resolved_version="$STUB_DATA_RESOLVED_VERSION"; source="$STUB_DATA_SOURCE" ;;
      ta4j-charting) resolved_version="$STUB_CHARTING_RESOLVED_VERSION"; source="$STUB_CHARTING_SOURCE" ;;
      ta4j-catalog) resolved_version="$STUB_CATALOG_RESOLVED_VERSION"; source="$STUB_CATALOG_SOURCE" ;;
    esac
    if [[ "${STUB_WRONG_RESOLVED_ARTIFACT:-}" == "$artifact" ]]; then
      cp "$source" "$directory/$artifact-$STUB_VERSION.jar"
    else
      cp "$source" "$directory/$artifact-$resolved_version.jar"
    fi
  fi
  if [[ "${STUB_SKIP_METADATA_ARTIFACT:-}" == "$artifact" ]]; then
    continue
  fi
  cat > "$directory/maven-metadata-central-portal-snapshots.xml" <<XML
<metadata>
  <version>${STUB_VERSION}</version>
  <versioning>
    <snapshotVersions>
      <snapshotVersion><extension>${extension}</extension><value>${resolved_version}</value></snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
XML
  if [[ "${STUB_ADD_AMBIGUOUS_METADATA:-}" == "true" ]]; then
    printf '<metadata><versioning/></metadata>\n' > "$directory/maven-metadata-000-local.xml"
  fi
done
EOF
  chmod +x "$path"
}

write_metadata_curl_stub() {
  local path="$1"
  cat > "$path" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

url=""
timeout_seconds=""
previous_argument=""
for argument in "$@"; do
  if [[ "$previous_argument" == "--max-time" ]]; then
    timeout_seconds="$argument"
  fi
  url="$argument"
  previous_argument="$argument"
done
printf '%s\n' "$url" >> "$STUB_CURL_LOG"
if [[ -n "${STUB_CLOCK_FILE:-}" ]]; then
  printf '%s\n' "$timeout_seconds" >> "$STUB_CURL_TIMEOUT_LOG"
  clock="$(cat "$STUB_CLOCK_FILE")"
  duration="${STUB_CURL_SECONDS:-0}"
  (( duration <= timeout_seconds )) || duration="$timeout_seconds"
  printf '%s\n' "$((clock + duration))" > "$STUB_CLOCK_FILE"
fi
artifact=""
for candidate in ta4j-parent ta4j-core ta4j-data ta4j-charting ta4j-catalog ta4j-examples; do
  if [[ "$url" == *"/org/ta4j/$candidate/"* ]]; then
    artifact="$candidate"
    break
  fi
done
[[ -n "$artifact" ]] || exit 2
if [[ "${STUB_SKIP_METADATA_ARTIFACT:-}" == "$artifact" ]]; then
  echo "metadata unavailable for $artifact" >&2
  exit 22
fi
if [[ "${STUB_MALFORMED_METADATA_ARTIFACT:-}" == "$artifact" ]]; then
  printf '<metadata><versioning>\n'
else
  cat "$STUB_METADATA_DIR/$artifact.xml"
fi
if [[ "${STUB_METADATA_WRITE_FAILURE_ARTIFACT:-}" == "$artifact" ]]; then
  echo "metadata transport failed after writing $artifact" >&2
  exit 22
fi
EOF
  chmod +x "$path"
}

write_consumption_metadata_fixture() {
  local directory="$1"
  local version="$2"
  local parent_resolved="$3"
  local core_resolved="$4"
  local examples_resolved="$5"
  local data_resolved="$6" charting_resolved="$7" catalog_resolved="$8"
  mkdir -p "$directory"
  cat > "$directory/ta4j-parent.xml" <<EOF
<metadata>
  <version>${version}</version>
  <versioning>
    <snapshotVersions>
      <snapshotVersion><extension>pom</extension><value>${parent_resolved}</value></snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
EOF
  cat > "$directory/ta4j-core.xml" <<EOF
<metadata>
  <version>${version}</version>
  <versioning>
    <snapshotVersions>
      <snapshotVersion><extension>jar</extension><value>${core_resolved}</value></snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
EOF
  cat > "$directory/ta4j-examples.xml" <<EOF
<metadata>
  <version>${version}</version>
  <versioning>
    <snapshotVersions>
      <snapshotVersion><extension>jar</extension><value>${examples_resolved}</value></snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
EOF
  local artifact resolved
  for artifact in ta4j-data ta4j-charting ta4j-catalog; do
    case "$artifact" in
      ta4j-data) resolved="$data_resolved" ;;
      ta4j-charting) resolved="$charting_resolved" ;;
      ta4j-catalog) resolved="$catalog_resolved" ;;
    esac
    cat > "$directory/$artifact.xml" <<EOF
<metadata>
  <version>${version}</version>
  <versioning>
    <snapshotVersions>
      <snapshotVersion><extension>jar</extension><value>${resolved}</value></snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
EOF
  done
}

prepare_consumption_fixture() {
  local root="$1"
  local version="$2"
  local module
  for module in ta4j-core ta4j-data ta4j-charting ta4j-catalog ta4j-examples; do
    mkdir -p "$root/$module/target"
    printf 'published %s\n' "$module" > "$root/$module/target/${module}-${version}.jar"
  done
}

run_consumption_fixture() {
  local publisher_root="$1"
  local maven_stub="$2"
  local version="$3"
  local success_attempt="$4"
  local max_attempts="$5"
  local core_source="$6"
  local examples_source="$7"
  local output="$8"
  local github_output="$9"
  local log="${10}"
  local repository_url="${11:-https://central.sonatype.com/repository/maven-snapshots/}"
  local curl_stub="$TMP/curl-stub"
  local metadata_dir="$TMP/metadata"
  local parent_resolved="${STUB_METADATA_PARENT_RESOLVED_VERSION:-${version%-SNAPSHOT}-20260714.120000-2}"
  local core_resolved="${STUB_METADATA_CORE_RESOLVED_VERSION:-${version%-SNAPSHOT}-20260714.120000-1}"
  local examples_resolved="${STUB_METADATA_EXAMPLES_RESOLVED_VERSION:-${version%-SNAPSHOT}-20260714.120000-3}"
  local data_resolved="${STUB_METADATA_DATA_RESOLVED_VERSION:-${version%-SNAPSHOT}-20260714.120000-4}"
  local charting_resolved="${STUB_METADATA_CHARTING_RESOLVED_VERSION:-${version%-SNAPSHOT}-20260714.120000-5}"
  local catalog_resolved="${STUB_METADATA_CATALOG_RESOLVED_VERSION:-${version%-SNAPSHOT}-20260714.120000-6}"
  write_metadata_curl_stub "$curl_stub"
  write_consumption_metadata_fixture "$metadata_dir" "$version" "$parent_resolved" "$core_resolved" "$examples_resolved" "$data_resolved" "$charting_resolved" "$catalog_resolved"

  STUB_VERSION="$version" \
  STUB_PARENT_RESOLVED_VERSION="$parent_resolved" \
  STUB_CORE_RESOLVED_VERSION="$core_resolved" \
  STUB_EXAMPLES_RESOLVED_VERSION="$examples_resolved" \
  STUB_DATA_RESOLVED_VERSION="$data_resolved" \
  STUB_CHARTING_RESOLVED_VERSION="$charting_resolved" \
  STUB_CATALOG_RESOLVED_VERSION="$catalog_resolved" \
  STUB_SUCCESS_ATTEMPT="$success_attempt" \
  STUB_ATTEMPT_FILE="$TMP/maven-attempts.txt" \
  STUB_CORE_SOURCE="$core_source" \
  STUB_EXAMPLES_SOURCE="$examples_source" \
  STUB_DATA_SOURCE="${STUB_DATA_SOURCE:-$publisher_root/ta4j-data/target/ta4j-data-${version}.jar}" \
  STUB_CHARTING_SOURCE="${STUB_CHARTING_SOURCE:-$publisher_root/ta4j-charting/target/ta4j-charting-${version}.jar}" \
  STUB_CATALOG_SOURCE="${STUB_CATALOG_SOURCE:-$publisher_root/ta4j-catalog/target/ta4j-catalog-${version}.jar}" \
  STUB_METADATA_DIR="$metadata_dir" \
  STUB_CURL_LOG="$TMP/curl.log" \
  STUB_POM_CAPTURE="$TMP/consumer.pom" \
  RELEASE_HELPERS_CURL_COMMAND="$curl_stub" \
    bash "$SCRIPT" snapshot-consumption \
      --version "$version" \
      --maven-command "$maven_stub" \
      --repository-url "$repository_url" \
      --publisher-root "$publisher_root" \
      --max-attempts "$max_attempts" \
      --retry-seconds "${STUB_RETRY_SECONDS:-0}" \
      --output "$output" \
      --github-output "$github_output" \
      --log "$log" >/dev/null
}

test_snapshot_version_present() {
  echo "Running test_snapshot_version_present"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "0.22.7-SNAPSHOT" $'      <version>0.22.6-SNAPSHOT</version>\n      <version>0.22.7-SNAPSHOT</version>' "20260506001534"

  bash "$SCRIPT" snapshot-publication \
    --version "0.22.7-SNAPSHOT" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "true"
  expect_output_value "$github_output" "snapshot_publication_latest" "0.22.7-SNAPSHOT"
  expect_output_value "$github_output" "snapshot_publication_last_updated" "20260506001534"

  rm -rf "$TMP"
  pass "test_snapshot_version_present"
}

test_snapshot_exact_artifacts_present() {
  echo "Running test_snapshot_exact_artifacts_present"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local resolved="0.23.1-20260714.120000-1"
  local metadata_file="$TMP/metadata.xml"
  local artifact_metadata_file="$TMP/artifact-metadata.xml"
  local artifact_pom_file="$TMP/ta4j-core.pom"
  local artifact_jar_file="$TMP/ta4j-core.jar"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "$version" "      <version>${version}</version>" "20260714120000"
  write_artifact_metadata_fixture "$artifact_metadata_file" "$version" "$resolved" "20260714120000"
  printf '<project/>\n' > "$artifact_pom_file"
  printf 'jar\n' > "$artifact_jar_file"

  bash "$SCRIPT" snapshot-publication \
    --version "$version" \
    --metadata-file "$metadata_file" \
    --require-artifacts \
    --artifact-metadata-file "$artifact_metadata_file" \
    --artifact-pom-file "$artifact_pom_file" \
    --artifact-jar-file "$artifact_jar_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "true"
  expect_output_value "$github_output" "snapshot_publication_resolved_version" "$resolved"
  expect_file_contains "$output_file" "ta4j-core-${resolved}.pom"
  expect_file_contains "$output_file" "ta4j-core-${resolved}.jar"

  rm -rf "$TMP"
  pass "test_snapshot_exact_artifacts_present"
}

test_snapshot_missing_exact_artifact_fails() {
  echo "Running test_snapshot_missing_exact_artifact_fails"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local resolved="0.23.1-20260714.120000-1"
  local metadata_file="$TMP/metadata.xml"
  local artifact_metadata_file="$TMP/artifact-metadata.xml"
  local artifact_pom_file="$TMP/ta4j-core.pom"
  local artifact_jar_file="$TMP/ta4j-core.jar"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "$version" "      <version>${version}</version>" "20260714120000"
  write_artifact_metadata_fixture "$artifact_metadata_file" "$version" "$resolved" "20260714120000"
  printf '<project/>\n' > "$artifact_pom_file"
  : > "$artifact_jar_file"

  bash "$SCRIPT" snapshot-publication \
    --version "$version" \
    --metadata-file "$metadata_file" \
    --require-artifacts \
    --artifact-metadata-file "$artifact_metadata_file" \
    --artifact-pom-file "$artifact_pom_file" \
    --artifact-jar-file "$artifact_jar_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "false"
  expect_file_contains "$output_file" "snapshot JAR fixture is missing or empty"

  rm -rf "$TMP"
  pass "test_snapshot_missing_exact_artifact_fails"
}

test_malformed_artifact_metadata_fails() {
  echo "Running test_malformed_artifact_metadata_fails"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local metadata_file="$TMP/metadata.xml"
  local artifact_metadata_file="$TMP/artifact-metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "$version" "      <version>${version}</version>" "20260714120000"
  printf '<metadata><versioning>\n' > "$artifact_metadata_file"

  bash "$SCRIPT" snapshot-publication \
    --version "$version" \
    --metadata-file "$metadata_file" \
    --require-artifacts \
    --artifact-metadata-file "$artifact_metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "false"
  expect_file_contains "$output_file" "ParseError"

  rm -rf "$TMP"
  pass "test_malformed_artifact_metadata_fails"
}

test_snapshot_version_missing() {
  echo "Running test_snapshot_version_missing"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "0.22.7-SNAPSHOT" $'      <version>0.22.6-SNAPSHOT</version>\n      <version>0.22.7-SNAPSHOT</version>' "20260506001534"

  bash "$SCRIPT" snapshot-publication \
    --version "0.22.8-SNAPSHOT" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "false"
  expect_output_value "$github_output" "snapshot_publication_latest" "0.22.7-SNAPSHOT"

  rm -rf "$TMP"
  pass "test_snapshot_version_missing"
}

test_non_snapshot_version_returns_na() {
  echo "Running test_non_snapshot_version_returns_na"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "0.22.7-SNAPSHOT" $'      <version>0.22.7-SNAPSHOT</version>' "20260506001534"

  bash "$SCRIPT" snapshot-publication \
    --version "0.22.7" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "n/a"

  rm -rf "$TMP"
  pass "test_non_snapshot_version_returns_na"
}

test_malformed_metadata_returns_unknown() {
  echo "Running test_malformed_metadata_returns_unknown"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  printf '%s\n' '<metadata><versioning>' > "$metadata_file"

  bash "$SCRIPT" snapshot-publication \
    --version "0.22.7-SNAPSHOT" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "unknown"

  rm -rf "$TMP"
  pass "test_malformed_metadata_returns_unknown"
}

test_non_https_metadata_url_returns_unknown() {
  echo "Running test_non_https_metadata_url_returns_unknown"

  TMP="$(new_temp_dir)"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"

  bash "$SCRIPT" snapshot-publication \
    --version "0.22.7-SNAPSHOT" \
    --metadata-url "file:///tmp/snapshot-publication.xml" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "unknown"
  expect_file_contains "$output_file" "\"error\": \"--metadata-url must use https\""

  rm -rf "$TMP"
  pass "test_non_https_metadata_url_returns_unknown"
}

test_snapshot_publication_policy_defers_push_runs() {
  echo "Running test_snapshot_publication_policy_defers_push_runs"

  TMP="$(new_temp_dir)"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication-policy.json"

  bash "$SCRIPT" snapshot-publication-policy \
    --event-name "push" \
    --workflow-name "" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication_enforced" "false"
  expect_output_value "$github_output" "snapshot_publication_pending_reason" "awaiting snapshot workflow completion before treating snapshot metadata drift as authoritative"

  rm -rf "$TMP"
  pass "test_snapshot_publication_policy_defers_push_runs"
}

test_snapshot_publication_policy_enforces_snapshot_workflow_runs() {
  echo "Running test_snapshot_publication_policy_enforces_snapshot_workflow_runs"

  TMP="$(new_temp_dir)"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication-policy.json"

  bash "$SCRIPT" snapshot-publication-policy \
    --event-name "workflow_run" \
    --workflow-name "Publish Snapshot to Maven Central" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication_enforced" "true"
  expect_output_value "$github_output" "snapshot_publication_pending_reason" ""

  rm -rf "$TMP"
  pass "test_snapshot_publication_policy_enforces_snapshot_workflow_runs"
}

test_snapshot_consumption_immediate_success() {
  echo "Running test_snapshot_consumption_immediate_success"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 3 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"

  expect_output_value "$github_output" "maven_consumable" "true"
  expect_output_value "$github_output" "snapshot_consumption_attempts" "1"
  expect_output_value "$github_output" "resolved_core_version" "0.23.1-20260714.120000-1"
  expect_output_value "$github_output" "resolved_examples_version" "0.23.1-20260714.120000-3"
  expect_output_value "$github_output" "resolved_parent_version" "0.23.1-20260714.120000-2"
  expect_output_value "$github_output" "resolved_data_version" "0.23.1-20260714.120000-4"
  expect_output_value "$github_output" "resolved_charting_version" "0.23.1-20260714.120000-5"
  expect_output_value "$github_output" "resolved_catalog_version" "0.23.1-20260714.120000-6"
  expect_file_contains "$output_file" '"mavenConsumable": true'
  expect_file_contains "$log" "consumer coordinates validated"
  expect_file_contains "$log" "[REDACTED_URL]"
  expect_file_contains "$log" "[REDACTED_TOKEN]"
  if grep -Eq 'fixture:secret|ghp_123456789012345678901234567890' "$log"; then
    fail "snapshot consumption log should redact credentials"
  fi
  if grep -Eq 'ta4j-cli|ta4j-acceleration' "$TMP/consumer.pom" "$TMP/curl.log" "$output_file" "$github_output"; then
    fail "foundation snapshot consumption should not request or report application/native artifacts"
  fi
  python3 - "$output_file" "$publisher_root" "$version" "$github_output" <<'PY'
import hashlib
import json
import pathlib
import sys

report = json.loads(pathlib.Path(sys.argv[1]).read_text())
publisher = pathlib.Path(sys.argv[2])
version = sys.argv[3]
outputs = dict(line.split("=", 1) for line in pathlib.Path(sys.argv[4]).read_text().splitlines())
for module, build in (("core", 1), ("examples", 3), ("data", 4), ("charting", 5), ("catalog", 6)):
    field = module.capitalize()
    expected = hashlib.sha256((publisher / f"ta4j-{module}/target/ta4j-{module}-{version}.jar").read_bytes()).hexdigest()
    assert report[f"resolved{field}Version"] == f"{version[:-9]}-20260714.120000-{build}"
    assert report[f"publisher{field}Sha256"] == expected
    assert report[f"resolved{field}Sha256"] == expected
    assert outputs[f"publisher_{module}_sha256"] == expected
    assert outputs[f"resolved_{module}_sha256"] == expected
PY
  for artifact in ta4j-parent ta4j-core ta4j-data ta4j-charting ta4j-catalog ta4j-examples; do
    expect_file_contains "$TMP/curl.log" "/org/ta4j/${artifact}/${version}/maven-metadata.xml?cacheBust="
  done

  rm -rf "$TMP"
  pass "test_snapshot_consumption_immediate_success"
}

test_snapshot_consumption_retries_until_success() {
  echo "Running test_snapshot_consumption_retries_until_success"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 2 3 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"

  expect_output_value "$github_output" "maven_consumable" "true"
  expect_output_value "$github_output" "snapshot_consumption_attempts" "2"
  expect_file_contains "$log" "snapshot not propagated"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_retries_until_success"
}

test_snapshot_consumption_retry_exhaustion() {
  echo "Running test_snapshot_consumption_retry_exhaustion"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 99 2 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"; then
    fail "snapshot consumption should fail after retry exhaustion"
  fi

  expect_output_value "$github_output" "maven_consumable" "false"
  expect_output_value "$github_output" "snapshot_consumption_attempts" "2"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_retry_exhaustion"
}

test_snapshot_consumption_checksum_mismatch() {
  echo "Running test_snapshot_consumption_checksum_mismatch"

  local version="0.23.1-SNAPSHOT" module
  local publisher_root maven_stub output_file github_output log wrong_jar
  local core_source examples_source data_source charting_source catalog_source
  for module in core data charting catalog examples; do
    TMP="$(new_temp_dir)"
    publisher_root="$TMP/publisher"
    maven_stub="$TMP/mvn-stub"
    output_file="$TMP/snapshot-consumption.json"
    github_output="$TMP/github-output.txt"
    log="$TMP/snapshot-consumption.log"
    wrong_jar="$TMP/wrong.jar"
    prepare_consumption_fixture "$publisher_root" "$version"
    write_maven_stub "$maven_stub"
    printf 'older %s\n' "$module" > "$wrong_jar"
    core_source="$publisher_root/ta4j-core/target/ta4j-core-${version}.jar"
    examples_source="$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar"
    data_source="$publisher_root/ta4j-data/target/ta4j-data-${version}.jar"
    charting_source="$publisher_root/ta4j-charting/target/ta4j-charting-${version}.jar"
    catalog_source="$publisher_root/ta4j-catalog/target/ta4j-catalog-${version}.jar"
    case "$module" in
      core) core_source="$wrong_jar" ;;
      examples) examples_source="$wrong_jar" ;;
      data) data_source="$wrong_jar" ;;
      charting) charting_source="$wrong_jar" ;;
      catalog) catalog_source="$wrong_jar" ;;
    esac

    if STUB_DATA_SOURCE="$data_source" STUB_CHARTING_SOURCE="$charting_source" STUB_CATALOG_SOURCE="$catalog_source" \
      run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
        "$core_source" "$examples_source" "$output_file" "$github_output" "$log"; then
      fail "snapshot consumption should reject an older $module artifact checksum"
    fi

    expect_output_value "$github_output" "maven_consumable" "false"
    expect_file_contains "$log" "checksum mismatch"
    python3 - "$output_file" "$module" <<'PY'
import json
import pathlib
import sys

report = json.loads(pathlib.Path(sys.argv[1]).read_text())
field = sys.argv[2].capitalize()
assert report[f"resolved{field}Sha256"]
assert report[f"resolved{field}Sha256"] != report[f"publisher{field}Sha256"]
PY
    rm -rf "$TMP"
  done
  pass "test_snapshot_consumption_checksum_mismatch"
}

test_snapshot_consumption_clears_checksums_after_later_failure() {
  echo "Running test_snapshot_consumption_clears_checksums_after_later_failure"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  local wrong_core="$TMP/wrong-core.jar"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"
  printf 'older core\n' > "$wrong_core"

  export STUB_FAIL_ATTEMPT=2
  if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 2 \
    "$wrong_core" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"; then
    unset STUB_FAIL_ATTEMPT
    fail "snapshot consumption should fail when the final resolution attempt fails"
  fi
  unset STUB_FAIL_ATTEMPT

  expect_output_value "$github_output" "maven_consumable" "false"
  expect_output_value "$github_output" "resolved_core_sha256" ""
  expect_output_value "$github_output" "resolved_examples_sha256" ""
  expect_output_value "$github_output" "resolved_data_sha256" ""
  expect_output_value "$github_output" "resolved_charting_sha256" ""
  expect_output_value "$github_output" "resolved_catalog_sha256" ""

  rm -rf "$TMP"
  pass "test_snapshot_consumption_clears_checksums_after_later_failure"
}

test_snapshot_consumption_ignores_ambiguous_metadata_files() {
  echo "Running test_snapshot_consumption_ignores_ambiguous_metadata_files"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  export STUB_ADD_AMBIGUOUS_METADATA=true
  run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"
  unset STUB_ADD_AMBIGUOUS_METADATA

  expect_output_value "$github_output" "maven_consumable" "true"
  expect_output_value "$github_output" "resolved_core_version" "0.23.1-20260714.120000-1"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_ignores_ambiguous_metadata_files"
}

test_snapshot_consumption_retries_missing_local_metadata() {
  echo "Running test_snapshot_consumption_retries_missing_local_metadata"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  export STUB_SKIP_METADATA_ARTIFACT=ta4j-parent
  if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 2 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"; then
    unset STUB_SKIP_METADATA_ARTIFACT
    fail "snapshot consumption should exhaust retries when timestamped metadata is missing"
  fi
  unset STUB_SKIP_METADATA_ARTIFACT

  expect_output_value "$github_output" "maven_consumable" "false"
  expect_output_value "$github_output" "snapshot_consumption_attempts" "2"
  expect_file_contains "$log" "fresh metadata fetch failed"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_retries_missing_local_metadata"
}

test_snapshot_consumption_skips_metadata_after_transport_failure() {
  echo "Running test_snapshot_consumption_skips_metadata_after_transport_failure"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  export STUB_METADATA_WRITE_FAILURE_ARTIFACT=ta4j-parent
  if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"; then
    unset STUB_METADATA_WRITE_FAILURE_ARTIFACT
    fail "snapshot consumption should reject metadata after a transport failure"
  fi
  unset STUB_METADATA_WRITE_FAILURE_ARTIFACT

  expect_output_value "$github_output" "maven_consumable" "false"
  expect_file_contains "$output_file" '"metadataError": "ta4j-parent: metadata transport failed after writing ta4j-parent"'
  expect_file_contains "$log" "fresh metadata fetch failed"
  if [[ -e "$TMP/maven-attempts.txt" ]]; then
    fail "snapshot consumption should not invoke Maven after metadata transport failure"
  fi

  rm -rf "$TMP"
  pass "test_snapshot_consumption_skips_metadata_after_transport_failure"
}

test_snapshot_consumption_rejects_wrong_coordinate_base() {
  echo "Running test_snapshot_consumption_rejects_wrong_coordinate_base"

  local version="0.23.1-SNAPSHOT" module variable
  local publisher_root maven_stub output_file github_output log
  for module in core data charting catalog; do
    TMP="$(new_temp_dir)"
    publisher_root="$TMP/publisher"
    maven_stub="$TMP/mvn-stub"
    output_file="$TMP/snapshot-consumption.json"
    github_output="$TMP/github-output.txt"
    log="$TMP/snapshot-consumption.log"
    prepare_consumption_fixture "$publisher_root" "$version"
    write_maven_stub "$maven_stub"
    case "$module" in
      core) variable=STUB_METADATA_CORE_RESOLVED_VERSION ;;
      data) variable=STUB_METADATA_DATA_RESOLVED_VERSION ;;
      charting) variable=STUB_METADATA_CHARTING_RESOLVED_VERSION ;;
      catalog) variable=STUB_METADATA_CATALOG_RESOLVED_VERSION ;;
    esac

    export "$variable=0.99.1-20260714.120000-7"
    if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
      "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
      "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
      "$output_file" "$github_output" "$log"; then
      fail "snapshot consumption should reject a $module coordinate from a different snapshot base"
    fi
    unset "$variable"

    expect_output_value "$github_output" "maven_consumable" "false"
    expect_file_contains "$log" "does not match requested snapshot base"
    if [[ -e "$TMP/maven-attempts.txt" ]]; then
      fail "snapshot consumption should not invoke Maven for a wrong coordinate base"
    fi

    rm -rf "$TMP"
  done
  pass "test_snapshot_consumption_rejects_wrong_coordinate_base"
}

test_snapshot_consumption_escapes_repository_url_in_pom() {
  echo "Running test_snapshot_consumption_escapes_repository_url_in_pom"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  local output_file="$TMP/snapshot-consumption.json"
  local github_output="$TMP/github-output.txt"
  local log="$TMP/snapshot-consumption.log"
  local repository_url="https://central.sonatype.com/repository/maven-snapshots/ampersand&path/"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"

  run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log" "$repository_url"

  expect_output_value "$github_output" "maven_consumable" "true"
  expect_file_contains "$TMP/consumer.pom" "ampersand&amp;path"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_escapes_repository_url_in_pom"
}

test_snapshot_consumption_rejects_missing_publisher_module() {
  echo "Running test_snapshot_consumption_rejects_missing_publisher_module"

  local version="0.23.1-SNAPSHOT" module publisher_root maven_stub
  for module in core data charting catalog examples; do
    TMP="$(new_temp_dir)"
    publisher_root="$TMP/publisher"
    maven_stub="$TMP/mvn-stub"
    prepare_consumption_fixture "$publisher_root" "$version"
    rm "$publisher_root/ta4j-$module/target/ta4j-${module}-${version}.jar"
    write_maven_stub "$maven_stub"

    if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
      "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
      "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
      "$TMP/output.json" "$TMP/github-output.txt" "$TMP/log" 2>"$TMP/error"; then
      fail "snapshot consumption should reject a missing $module publisher JAR"
    fi
    expect_file_contains "$TMP/error" "Published $module JAR is missing"
    if [[ -e "$TMP/curl.log" || -e "$TMP/maven-attempts.txt" ]]; then
      fail "snapshot consumption should reject missing publisher jars before network or Maven calls"
    fi

    rm -rf "$TMP"
  done
  pass "test_snapshot_consumption_rejects_missing_publisher_module"
}

test_snapshot_consumption_rejects_release_version() {
  echo "Running test_snapshot_consumption_rejects_release_version"

  TMP="$(new_temp_dir)"
  local maven_stub="$TMP/mvn-stub"
  write_maven_stub "$maven_stub"
  if bash "$SCRIPT" snapshot-consumption \
    --version "0.23.1" \
    --maven-command "$maven_stub" \
    --publisher-root "$TMP" \
    --output "$TMP/output.json" 2>"$TMP/error"; then
    fail "snapshot consumption should reject a non-snapshot version"
  fi
  expect_file_contains "$TMP/error" "requires a -SNAPSHOT version"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_rejects_release_version"
}

test_snapshot_consumption_rejects_repository_query() {
  echo "Running test_snapshot_consumption_rejects_repository_query"

  TMP="$(new_temp_dir)"
  local maven_stub="$TMP/mvn-stub"
  local repository_url
  write_maven_stub "$maven_stub"
  for repository_url in \
    "https://central.sonatype.com/repository/maven-snapshots/?mirror=1" \
    "https://central.sonatype.com/repository/maven-snapshots/#fragment"; do
    if bash "$SCRIPT" snapshot-consumption \
      --version "0.23.1-SNAPSHOT" \
      --maven-command "$maven_stub" \
      --repository-url "$repository_url" \
      --publisher-root "$TMP" \
      --output "$TMP/output.json" 2>"$TMP/error"; then
      fail "snapshot consumption should reject a repository URL with a query or fragment"
    fi
    expect_file_contains "$TMP/error" "must not contain query or fragment components"
  done

  rm -rf "$TMP"
  pass "test_snapshot_consumption_rejects_repository_query"
}

test_snapshot_consumption_rejects_malformed_shared_metadata() {
  echo "Running test_snapshot_consumption_rejects_malformed_shared_metadata"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher" maven_stub="$TMP/mvn-stub"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"
  if STUB_MALFORMED_METADATA_ARTIFACT=ta4j-catalog \
    run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 2 \
      "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
      "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
      "$TMP/output.json" "$TMP/github-output.txt" "$TMP/log"; then
    fail "snapshot consumption should reject malformed shared-module metadata"
  fi
  expect_output_value "$TMP/github-output.txt" "maven_consumable" "false"
  expect_output_value "$TMP/github-output.txt" "resolved_catalog_version" ""
  expect_output_value "$TMP/github-output.txt" "snapshot_consumption_attempts" "2"
  expect_file_contains "$TMP/log" "ParseError"
  expect_file_contains "$TMP/log" "missing timestamped parent/core/data/charting/catalog/examples coordinates"
  if [[ -e "$TMP/maven-attempts.txt" ]]; then
    fail "snapshot consumption should not invoke Maven for malformed shared-module metadata"
  fi

  rm -rf "$TMP"
  pass "test_snapshot_consumption_rejects_malformed_shared_metadata"
}

test_snapshot_consumption_requires_timestamped_parent() {
  echo "Running test_snapshot_consumption_requires_timestamped_parent"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher" maven_stub="$TMP/mvn-stub"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"
  if STUB_METADATA_PARENT_RESOLVED_VERSION="$version" \
    run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
      "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
      "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
      "$TMP/output.json" "$TMP/github-output.txt" "$TMP/log"; then
    fail "snapshot consumption should require an immutable timestamped parent coordinate"
  fi
  expect_output_value "$TMP/github-output.txt" "maven_consumable" "false"
  expect_output_value "$TMP/github-output.txt" "resolved_parent_version" ""
  expect_file_contains "$TMP/log" "resolved coordinate is not timestamped"
  if [[ -e "$TMP/maven-attempts.txt" ]]; then
    fail "snapshot consumption should not invoke Maven for a mutable parent coordinate"
  fi

  rm -rf "$TMP"
  pass "test_snapshot_consumption_requires_timestamped_parent"
}

test_snapshot_consumption_requires_exact_shared_artifact_files() {
  echo "Running test_snapshot_consumption_requires_exact_shared_artifact_files"

  local version="0.23.1-SNAPSHOT" module publisher_root maven_stub
  for module in ta4j-data ta4j-charting ta4j-catalog; do
    TMP="$(new_temp_dir)"
    publisher_root="$TMP/publisher"
    maven_stub="$TMP/mvn-stub"
    prepare_consumption_fixture "$publisher_root" "$version"
    write_maven_stub "$maven_stub"
    if STUB_WRONG_RESOLVED_ARTIFACT="$module" \
      run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
        "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
        "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
        "$TMP/output.json" "$TMP/github-output.txt" "$TMP/log"; then
      fail "snapshot consumption must not accept a mutable SNAPSHOT filename for $module"
    fi
    expect_output_value "$TMP/github-output.txt" "maven_consumable" "false"
    expect_file_contains "$TMP/log" "resolved snapshot artifacts are missing from the isolated local repository"
    rm -rf "$TMP"
  done

  pass "test_snapshot_consumption_requires_exact_shared_artifact_files"
}

test_snapshot_consumption_bounds_all_module_metadata_by_deadline() {
  echo "Running test_snapshot_consumption_bounds_all_module_metadata_by_deadline"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher" maven_stub="$TMP/mvn-stub"
  prepare_consumption_fixture "$publisher_root" "$version"
  write_maven_stub "$maven_stub"
  mkdir -p "$TMP/bin"
  cat > "$TMP/bin/date" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "$*" == "+%s" ]] || exit 2
cat "$STUB_CLOCK_FILE"
EOF
  chmod +x "$TMP/bin/date"
  printf '1000\n' > "$TMP/clock"
  if PATH="$TMP/bin:$PATH" STUB_CLOCK_FILE="$TMP/clock" STUB_CURL_TIMEOUT_LOG="$TMP/timeouts" \
    STUB_CURL_SECONDS=29 STUB_SKIP_METADATA_ARTIFACT=ta4j-parent \
    run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 20 \
      "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
      "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
      "$TMP/output.json" "$TMP/github-output.txt" "$TMP/log"; then
    fail "snapshot consumption should fail when fresh metadata uses the full deadline"
  fi
  expect_output_value "$TMP/github-output.txt" "maven_consumable" "false"
  expect_output_value "$TMP/github-output.txt" "snapshot_consumption_attempts" "2"
  expect_output_value "$TMP/github-output.txt" "snapshot_consumption_elapsed_seconds" "300"
  expect_file_contains "$TMP/log" "snapshot consumption deadline exhausted after 300 seconds"
  for module in ta4j-parent ta4j-core ta4j-data ta4j-charting ta4j-catalog ta4j-examples; do
    expect_file_contains "$TMP/curl.log" "/org/ta4j/$module/$version/maven-metadata.xml?cacheBust="
  done
  python3 - "$TMP/timeouts" "$TMP/clock" <<'PY'
import pathlib
import sys

timeouts = [int(value) for value in pathlib.Path(sys.argv[1]).read_text().splitlines()]
assert timeouts == [30] * 10 + [10], timeouts
assert pathlib.Path(sys.argv[2]).read_text().strip() == "1300"
PY
  if [[ -e "$TMP/maven-attempts.txt" ]]; then
    fail "snapshot consumption should not invoke Maven after metadata exhausts the deadline"
  fi

  rm -rf "$TMP"
  pass "test_snapshot_consumption_bounds_all_module_metadata_by_deadline"
}

test_snapshot_version_present
test_snapshot_exact_artifacts_present
test_snapshot_missing_exact_artifact_fails
test_malformed_artifact_metadata_fails
test_snapshot_version_missing
test_non_snapshot_version_returns_na
test_malformed_metadata_returns_unknown
test_non_https_metadata_url_returns_unknown
test_snapshot_publication_policy_defers_push_runs
test_snapshot_publication_policy_enforces_snapshot_workflow_runs
test_snapshot_consumption_immediate_success
test_snapshot_consumption_retries_until_success
test_snapshot_consumption_retry_exhaustion
test_snapshot_consumption_checksum_mismatch
test_snapshot_consumption_clears_checksums_after_later_failure
test_snapshot_consumption_ignores_ambiguous_metadata_files
test_snapshot_consumption_retries_missing_local_metadata
test_snapshot_consumption_skips_metadata_after_transport_failure
test_snapshot_consumption_rejects_wrong_coordinate_base
test_snapshot_consumption_escapes_repository_url_in_pom
test_snapshot_consumption_rejects_missing_publisher_module
test_snapshot_consumption_rejects_release_version
test_snapshot_consumption_rejects_repository_query
test_snapshot_consumption_rejects_malformed_shared_metadata
test_snapshot_consumption_requires_timestamped_parent
test_snapshot_consumption_requires_exact_shared_artifact_files
test_snapshot_consumption_bounds_all_module_metadata_by_deadline

echo
echo "All snapshot publication tests passed."
