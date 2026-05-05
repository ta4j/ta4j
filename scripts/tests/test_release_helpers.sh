#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# test_release_helpers.sh
#
# Validates release workflow helper behavior used by GitHub Actions.
# =============================================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/release/release_helpers.py"
PYTHON="${PYTHON:-python3}"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
  return 0
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

run_test() {
  TMP="$(mktemp -d "${TMPDIR:-/tmp}/release-helpers.XXXXXX")"
  pushd "$TMP" >/dev/null || exit 1
}

finish_test() {
  popd >/dev/null || exit 1
  rm -rf "$TMP"
}

expect_file_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" "$file"; then
    fail "$msg (missing: '$needle')"
  fi
}

expect_json_value() {
  local file="$1"
  local filter="$2"
  local expected="$3"
  local actual
  actual="$($PYTHON - "$file" "$filter" <<'PY'
import json
import sys

path, key = sys.argv[1:3]
with open(path, encoding="utf-8") as handle:
    data = json.load(handle)
value = data
for part in key.split("."):
    value = value[part]
if isinstance(value, bool):
    print("true" if value else "false")
else:
    print(value)
PY
)"
  if [[ "$actual" != "$expected" ]]; then
    fail "expected ${filter}=${expected}, got ${actual:-<missing>}"
  fi
}

write_catalog_fixture() {
  cat > catalog.json <<'EOF'
[
  {
    "id": "openai/gpt-4.1",
    "name": "OpenAI GPT-4.1",
    "publisher": "OpenAI",
    "summary": "Large-context model",
    "rate_limit_tier": "high",
    "limits": {
      "max_input_tokens": 1048576,
      "max_output_tokens": 32768
    },
    "html_url": "https://github.com/marketplace/models/azure-openai/gpt-4-1"
  }
]
EOF
}

test_catalog_preflight_accepts_configured_model() {
  echo "Running test_catalog_preflight_accepts_configured_model"
  run_test
  write_catalog_fixture

  GITHUB_OUTPUT=outputs.txt "$PYTHON" "$SCRIPT" catalog-preflight \
    --model openai/gpt-4.1 \
    --catalog-file catalog.json \
    --output release-ai-model.json

  expect_json_value release-ai-model.json id openai/gpt-4.1
  expect_json_value release-ai-model.json max_input_tokens 1048576
  expect_file_contains outputs.txt "model_id=openai/gpt-4.1" "catalog preflight should emit model id"

  finish_test
  pass "test_catalog_preflight_accepts_configured_model"
}

test_catalog_preflight_rejects_missing_model() {
  echo "Running test_catalog_preflight_rejects_missing_model"
  run_test
  write_catalog_fixture

  if "$PYTHON" "$SCRIPT" catalog-preflight \
    --model openai/missing \
    --catalog-file catalog.json \
    --output release-ai-model.json >preflight.log 2>&1; then
    fail "catalog preflight should reject an unavailable model"
  fi
  expect_file_contains preflight.log "openai/missing" "failure should name missing model"

  finish_test
  pass "test_catalog_preflight_rejects_missing_model"
}

test_parse_decision_normalizes_major_and_invalid_json() {
  echo "Running test_parse_decision_normalizes_major_and_invalid_json"
  run_test

  cat > ai-content.txt <<'EOF'
```json
{"should_release":true,"bump":"major","confidence":0.91,"reason":"Breaking but pre-1.0 change","evidence":["public API changed"]}
```
EOF
  "$PYTHON" "$SCRIPT" parse-decision --raw-file ai-content.txt --output decision.json --github-output outputs.txt
  expect_json_value decision.json should_release true
  expect_json_value decision.json bump minor
  expect_file_contains outputs.txt "bump=minor" "major bumps should be downgraded in outputs"

  cat > ai-content.txt <<'EOF'
{"should_release":"false","bump":"minor","confidence":0.7,"reason":"No release needed"}
EOF
  "$PYTHON" "$SCRIPT" parse-decision --raw-file ai-content.txt --output string-false.json
  expect_json_value string-false.json should_release false
  expect_json_value string-false.json bump patch

  cat > ai-content.txt <<'EOF'
{"should_release":"1","bump":"patch","confidence":0.7,"reason":"Release needed"}
EOF
  "$PYTHON" "$SCRIPT" parse-decision --raw-file ai-content.txt --output string-true.json
  expect_json_value string-true.json should_release true

  cat > ai-content.txt <<'EOF'
{"should_release":"maybe","bump":"minor","confidence":0.7,"reason":"Ambiguous response"}
EOF
  "$PYTHON" "$SCRIPT" parse-decision --raw-file ai-content.txt --output invalid-flag.json
  expect_json_value invalid-flag.json should_release false
  expect_json_value invalid-flag.json bump patch
  expect_file_contains invalid-flag.json "invalid should_release 'maybe'" "invalid flag should be called out"

  printf 'not-json' > ai-content.txt
  "$PYTHON" "$SCRIPT" parse-decision --raw-file ai-content.txt --output invalid.json
  expect_json_value invalid.json should_release false
  expect_json_value invalid.json bump patch
  expect_json_value invalid.json warning "Invalid AI JSON"

  finish_test
  pass "test_parse_decision_normalizes_major_and_invalid_json"
}

test_build_dossier_groups_and_truncates_diff() {
  echo "Running test_build_dossier_groups_and_truncates_diff"
  run_test

  git init -q -b master
  git config user.name "Test User"
  git config user.email "test@example.com"
  mkdir -p ta4j-core/src/main/java/org/ta4j/core scripts
  cat > CHANGELOG.md <<'EOF'
## Unreleased

- Added release helper coverage.
EOF
  cat > pom.xml <<'EOF'
<project><version>1.0.0-SNAPSHOT</version></project>
EOF
  cat > ta4j-core/src/main/java/org/ta4j/core/Fixture.java <<'EOF'
package org.ta4j.core;

public class Fixture {
}
EOF
  git add .
  git commit -q -m "Initial"
  git tag -a 1.0.0 -m "Release 1.0.0"

  cat > ta4j-core/src/main/java/org/ta4j/core/Fixture.java <<'EOF'
package org.ta4j.core;

/**
 * Fixture API.
 *
 * @since 1.0.1
 */
public class Fixture {
    public String value() {
        return "release-helper-dossier-with-enough-content-to-trigger-truncation";
    }
}
EOF
  git add .
  git commit -q -m "Add fixture API"

  "$PYTHON" "$SCRIPT" build-dossier \
    --last-tag 1.0.0 \
    --current-version 1.0.1-SNAPSHOT \
    --pom-base 1.0.1 \
    --max-diff-chars 120 \
    --output release-dossier.md \
    --audit-output release-audit.json

  expect_file_contains release-dossier.md "production code" "dossier should group production code"
  expect_file_contains release-dossier.md "Public API Signals" "dossier should include API signal section"
  expect_file_contains release-dossier.md "[TRUNCATED" "dossier should make diff truncation explicit"
  expect_json_value release-audit.json selected_diff_truncated true

  finish_test
  pass "test_build_dossier_groups_and_truncates_diff"
}

test_artifact_manifest_validates_expected_release_jars() {
  echo "Running test_artifact_manifest_validates_expected_release_jars"
  run_test

  version=1.2.3
  for file in \
    "ta4j-core/target/ta4j-core-${version}.jar" \
    "ta4j-core/target/ta4j-core-${version}-sources.jar" \
    "ta4j-core/target/ta4j-core-${version}-javadoc.jar" \
    "ta4j-core/target/ta4j-core-${version}-tests.jar" \
    "ta4j-examples/target/ta4j-examples-${version}.jar" \
    "ta4j-examples/target/ta4j-examples-${version}-sources.jar" \
    "ta4j-examples/target/ta4j-examples-${version}-javadoc.jar"; do
    mkdir -p "$(dirname "$file")"
    : > "$file"
  done

  "$PYTHON" "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict
  expect_file_contains artifact-manifest.txt "Missing release artifacts:" "manifest should include missing section"
  expect_file_contains artifact-manifest.txt "- (none)" "manifest should report no missing artifacts"

  : > "ta4j-core/target/unexpected.jar"
  if "$PYTHON" "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict >manifest.log 2>&1; then
    fail "strict artifact manifest should reject unexpected jars"
  fi
  expect_file_contains manifest.log "Unexpected target jars" "strict failure should name unexpected jars"

  finish_test
  pass "test_artifact_manifest_validates_expected_release_jars"
}

test_javadoc_warning_baseline_rejects_new_warnings() {
  echo "Running test_javadoc_warning_baseline_rejects_new_warnings"
  run_test

  cat > baseline.txt <<'EOF'
ta4j-core/src/main/java/org/ta4j/core/Foo.java: warning: no @param for value
EOF
  cat > release.log <<'EOF'
[WARNING] /home/runner/work/ta4j/ta4j/ta4j-core/src/main/java/org/ta4j/core/Foo.java: warning: no @param for value
[WARNING] /home/runner/work/ta4j/ta4j/ta4j-core/src/test/java/org/ta4j/core/BaseTradingRecordTest.java:[61,15] recordFill(org.ta4j.core.Trade) has been deprecated
EOF

  "$PYTHON" "$SCRIPT" javadoc-warnings \
    --baseline baseline.txt \
    --output javadoc-warnings.txt \
    --github-output outputs.txt \
    --fail-on-new \
    release.log
  expect_file_contains outputs.txt "javadoc_warning_new_count=0" "baseline match should not report new warnings"
  expect_file_contains javadoc-warnings.txt "current_count=1" "compiler warnings should not count as Javadoc warnings"

  cat >> release.log <<'EOF'
[WARNING] /home/runner/work/ta4j/ta4j/ta4j-examples/src/main/java/ta4jexamples/FooExample.java: warning: no @return
EOF
  if "$PYTHON" "$SCRIPT" javadoc-warnings \
    --baseline baseline.txt \
    --output javadoc-warnings.txt \
    --fail-on-new \
    release.log >javadoc.log 2>&1; then
    fail "new Javadoc warning should fail the baseline check"
  fi
  expect_file_contains javadoc.log "New Javadoc warning" "baseline failure should name new warning"

  finish_test
  pass "test_javadoc_warning_baseline_rejects_new_warnings"
}

test_catalog_preflight_accepts_configured_model
test_catalog_preflight_rejects_missing_model
test_parse_decision_normalizes_major_and_invalid_json
test_build_dossier_groups_and_truncates_diff
test_artifact_manifest_validates_expected_release_jars
test_javadoc_warning_baseline_rejects_new_warnings

echo
echo "All release helper tests passed."
