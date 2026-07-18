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

  mkdir -p ta4j-core ta4j-examples scripts bin
  cp "$SCRIPT" scripts/validate-central-metadata.sh
  chmod +x scripts/validate-central-metadata.sh
  write_fake_maven

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

write_fake_maven() {
  cat > bin/mvn <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_MAVEN_INVOCATIONS"

module="ta4j-parent"
expression=""
while (($# > 0)); do
  case "$1" in
    -N)
      module="ta4j-parent"
      ;;
    -pl)
      shift
      module="${1:-}"
      ;;
    -Dexpression=*)
      expression="${1#-Dexpression=}"
      ;;
  esac
  shift || true
done

if [[ -z "$expression" ]]; then
  echo "missing expression" >&2
  exit 2
fi

has_developers="false"
if grep -Fq "<developers>" pom.xml; then
  has_developers="true"
fi

case "$expression" in
  project.name)
    if [[ "$module" == "ta4j-parent" ]]; then
      echo "Ta4j Parent"
    else
      echo "$module"
    fi
    ;;
  project.description)
    echo "Fixture description"
    ;;
  project.url)
    echo "https://github.com/ta4j/ta4j"
    ;;
  "project.licenses[0].name")
    echo "MIT License"
    ;;
  project.scm.connection)
    echo "scm:git:git://github.com/ta4j/ta4j.git"
    ;;
  project.scm.url)
    echo "https://github.com/ta4j/ta4j"
    ;;
  "project.developers[0].id")
    if [[ "$has_developers" == "true" ]]; then
      echo "maintainer"
    else
      echo "null object or invalid expression"
    fi
    ;;
  "project.developers[0].name")
    if [[ "$has_developers" == "true" ]]; then
      echo "Maintainer"
    else
      echo "null object or invalid expression"
    fi
    ;;
  *)
    echo "null object or invalid expression"
    ;;
esac
EOF
  chmod +x bin/mvn
}

assert_maven_evaluation_contract() {
  local invocation_count
  invocation_count="$(wc -l < "$FAKE_MAVEN_INVOCATIONS" | tr -d '[:space:]')"
  if [[ "$invocation_count" != "24" ]]; then
    fail "validator should evaluate 8 required fields across 3 modules, got ${invocation_count}"
  fi

  expect_file_contains "$FAKE_MAVEN_INVOCATIONS" "-q -N help:evaluate -Dexpression=project.name -DforceStdout" \
    "parent metadata should be evaluated without recursing modules"
  expect_file_contains "$FAKE_MAVEN_INVOCATIONS" "-q -pl ta4j-core help:evaluate -Dexpression=project.developers[0].id -DforceStdout" \
    "core metadata should be evaluated through Maven effective model"
  expect_file_contains "$FAKE_MAVEN_INVOCATIONS" "-q -pl ta4j-examples help:evaluate -Dexpression=project.scm.url -DforceStdout" \
    "examples metadata should be evaluated through Maven effective model"
}

run_test() {
  TMP="$(mktemp -d)"
  pushd "$TMP" >/dev/null || exit 1
  export PATH="$TMP/bin:$PATH"
  export BASH_ENV=/dev/null
  export FAKE_MAVEN_INVOCATIONS="$TMP/maven-invocations.txt"
  : > "$FAKE_MAVEN_INVOCATIONS"
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
  assert_maven_evaluation_contract

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
  assert_maven_evaluation_contract

  finish_test
  pass "test_fails_when_developers_missing"
}

test_passes_with_complete_metadata
test_fails_when_developers_missing

echo
echo "All validate-central-metadata tests passed."
