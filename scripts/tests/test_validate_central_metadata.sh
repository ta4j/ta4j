#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# test_validate_central_metadata.sh
#
# Validates behavior of scripts/validate-central-metadata.sh
# =============================================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/validate-central-metadata.sh"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
  return 0
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_contains() {
  local haystack="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" <<<"$haystack"; then
    fail "$msg (missing: '$needle')"
  fi
}

expect_file_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" "$file"; then
    fail "$msg (missing: '$needle')"
  fi
}

create_fixture() {
  local include_developers="$1"

  mkdir -p ta4j-core ta4j-examples scripts
  cp "$SCRIPT" scripts/validate-central-metadata.sh
  chmod +x scripts/validate-central-metadata.sh

  cat > pom.xml <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-parent</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <name>Ta4j Parent</name>
  <description>Fixture description</description>
  <url>https://github.com/ta4j/ta4j</url>
  <licenses>
    <license>
      <name>MIT License</name>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:git://github.com/ta4j/ta4j.git</connection>
    <url>https://github.com/ta4j/ta4j</url>
  </scm>
EOF

  if [[ "$include_developers" == "true" ]]; then
    cat >> pom.xml <<'EOF'
  <developers>
    <developer>
      <id>maintainer</id>
      <name>Maintainer</name>
    </developer>
  </developers>
EOF
  fi

  cat >> pom.xml <<'EOF'
  <modules>
    <module>ta4j-core</module>
    <module>ta4j-examples</module>
  </modules>
</project>
EOF

  for module in ta4j-core ta4j-examples; do
    cat > "${module}/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.ta4j</groupId>
    <artifactId>ta4j-parent</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>${module}</artifactId>
</project>
EOF
  done
}

run_test() {
  TMP="$(mktemp -d)"
  pushd "$TMP" >/dev/null || exit 1
}

finish_test() {
  popd >/dev/null || exit 1
  rm -rf "$TMP"
}

test_passes_with_complete_metadata() {
  echo "Running test_passes_with_complete_metadata"
  run_test
  create_fixture "true"

  local out
  out="$(scripts/validate-central-metadata.sh)"
  expect_contains "$out" "validation passed" "validator should pass for complete metadata"

  finish_test
  pass "test_passes_with_complete_metadata"
}

test_fails_when_developers_missing() {
  echo "Running test_fails_when_developers_missing"
  run_test
  create_fixture "false"

  local out_file="validator.log"
  if scripts/validate-central-metadata.sh >"$out_file" 2>&1; then
    fail "validator should fail when developers metadata is missing"
  fi

  expect_file_contains "$out_file" "developers[0].id" "should mention missing developer id"
  expect_file_contains "$out_file" "developers[0].name" "should mention missing developer name"
  expect_file_contains "$out_file" "validation failed" "should print summary failure"

  finish_test
  pass "test_fails_when_developers_missing"
}

test_passes_with_complete_metadata
test_fails_when_developers_missing

echo
echo "All validate-central-metadata tests passed."
