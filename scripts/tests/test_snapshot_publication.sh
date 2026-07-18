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
for argument in "$@"; do
  case "$argument" in
    -Dmaven.repo.local=*) local_repo="${argument#*=}" ;;
  esac
done
[[ -n "$local_repo" ]] || exit 2

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

for artifact in ta4j-parent ta4j-core ta4j-examples; do
  directory="$local_repo/org/ta4j/$artifact/$STUB_VERSION"
  mkdir -p "$directory"
  extension=jar
  if [[ "$artifact" == "ta4j-parent" ]]; then
    extension=pom
    printf '<project/>\n' > "$directory/$artifact-$STUB_VERSION.pom"
  elif [[ "$artifact" == "ta4j-core" ]]; then
    cp "$STUB_CORE_SOURCE" "$directory/$artifact-$STUB_VERSION.jar"
  else
    cp "$STUB_EXAMPLES_SOURCE" "$directory/$artifact-$STUB_VERSION.jar"
  fi
  if [[ "${STUB_SKIP_METADATA_ARTIFACT:-}" == "$artifact" ]]; then
    continue
  fi
  cat > "$directory/maven-metadata-central-portal-snapshots.xml" <<XML
<metadata>
  <version>${STUB_VERSION}</version>
  <versioning>
    <snapshotVersions>
      <snapshotVersion><extension>${extension}</extension><value>${STUB_RESOLVED_VERSION}</value></snapshotVersion>
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

prepare_consumption_fixture() {
  local root="$1"
  local version="$2"
  mkdir -p "$root/ta4j-core/target" "$root/ta4j-examples/target"
  printf 'published core\n' > "$root/ta4j-core/target/ta4j-core-${version}.jar"
  printf 'published examples\n' > "$root/ta4j-examples/target/ta4j-examples-${version}.jar"
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

  STUB_VERSION="$version" \
  STUB_RESOLVED_VERSION="${version%-SNAPSHOT}-20260714.120000-1" \
  STUB_SUCCESS_ATTEMPT="$success_attempt" \
  STUB_ATTEMPT_FILE="$TMP/maven-attempts.txt" \
  STUB_CORE_SOURCE="$core_source" \
  STUB_EXAMPLES_SOURCE="$examples_source" \
    bash "$SCRIPT" snapshot-consumption \
      --version "$version" \
      --maven-command "$maven_stub" \
      --repository-url "https://central.sonatype.com/repository/maven-snapshots/" \
      --publisher-root "$publisher_root" \
      --max-attempts "$max_attempts" \
      --retry-seconds 0 \
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
  expect_file_contains "$output_file" '"mavenConsumable": true'

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

  if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 2 \
    "$wrong_core" \
    "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar" \
    "$output_file" "$github_output" "$log"; then
    fail "snapshot consumption should reject an older artifact checksum"
  fi

  expect_output_value "$github_output" "maven_consumable" "false"
  expect_file_contains "$log" "checksum mismatch"

  rm -rf "$TMP"
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
  expect_file_contains "$log" "missing timestamped parent/core/examples coordinates"

  rm -rf "$TMP"
  pass "test_snapshot_consumption_retries_missing_local_metadata"
}

test_snapshot_consumption_rejects_missing_publisher_module() {
  echo "Running test_snapshot_consumption_rejects_missing_publisher_module"

  TMP="$(new_temp_dir)"
  local version="0.23.1-SNAPSHOT"
  local publisher_root="$TMP/publisher"
  local maven_stub="$TMP/mvn-stub"
  prepare_consumption_fixture "$publisher_root" "$version"
  rm "$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar"
  write_maven_stub "$maven_stub"

  if run_consumption_fixture "$publisher_root" "$maven_stub" "$version" 1 1 \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$publisher_root/ta4j-core/target/ta4j-core-${version}.jar" \
    "$TMP/output.json" "$TMP/github-output.txt" "$TMP/log" 2>"$TMP/error"; then
    fail "snapshot consumption should reject a missing publisher module"
  fi
  expect_file_contains "$TMP/error" "Published examples JAR is missing"

  rm -rf "$TMP"
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
test_snapshot_consumption_rejects_missing_publisher_module
test_snapshot_consumption_rejects_release_version

echo
echo "All snapshot publication tests passed."
