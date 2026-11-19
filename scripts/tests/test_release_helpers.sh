#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# test_release_helpers.sh
#
# Validates release workflow helper behavior used by GitHub Actions.
# =============================================================================

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
  actual="$(jq -r ".$filter" "$file")"
  if [[ "$actual" != "$expected" ]]; then
    fail "expected ${filter}=${expected}, got ${actual:-<missing>}"
  fi
}

expect_json_compact() {
  local file="$1"
  local filter="$2"
  local expected="$3"
  local actual
  actual="$(jq -c ".$filter" "$file")"
  if [[ "$actual" != "$expected" ]]; then
    fail "expected ${filter}=${expected}, got ${actual:-<missing>}"
  fi
}

test_release_pr_review_plan_defaults_to_owner() {
  echo "Running test_release_pr_review_plan_defaults_to_owner"
  run_test

  bash "$SCRIPT" release-pr-review-plan \
    --release-owner TheCookieLab \
    --pr-author maintainer \
    --output release-review-plan.json \
    --github-output outputs.txt >/dev/null

  expect_json_value release-review-plan.json hasReviewTargets true
  expect_json_compact release-review-plan.json reviewers '["TheCookieLab"]'
  expect_json_compact release-review-plan.json teamReviewers '[]'
  expect_file_contains outputs.txt 'has_review_targets=true' "review plan should expose target availability"
  expect_file_contains outputs.txt 'reviewers_json=["TheCookieLab"]' "review plan should expose reviewers JSON"

  finish_test
  pass "test_release_pr_review_plan_defaults_to_owner"
}

test_release_pr_review_plan_skips_self_review_without_failing() {
  echo "Running test_release_pr_review_plan_skips_self_review_without_failing"
  run_test

  bash "$SCRIPT" release-pr-review-plan \
    --release-owner TheCookieLab \
    --pr-author thecookielab \
    --output release-review-plan.json \
    --github-output outputs.txt >review-plan.log

  expect_json_value release-review-plan.json hasReviewTargets false
  expect_json_compact release-review-plan.json reviewers '[]'
  expect_json_compact release-review-plan.json skippedReviewers '[{"login":"TheCookieLab","reason":"matches PR author"}]'
  expect_file_contains release-review-plan.json "No eligible release PR reviewers" \
    "self-authored PR without fallback should include a warning"
  expect_file_contains outputs.txt 'has_review_targets=false' "review plan should expose no target state"
  expect_file_contains review-plan.log "::warning::No eligible release PR reviewers" \
    "review plan should warn without failing"

  finish_test
  pass "test_release_pr_review_plan_skips_self_review_without_failing"
}

test_release_pr_review_plan_uses_non_author_fallback_reviewers() {
  echo "Running test_release_pr_review_plan_uses_non_author_fallback_reviewers"
  run_test

  bash "$SCRIPT" release-pr-review-plan \
    --release-owner TheCookieLab \
    --pr-author TheCookieLab \
    --reviewers $'TheCookieLab, maintainer\nMaintainer other maintainer' \
    --output release-review-plan.json \
    --github-output outputs.txt >/dev/null

  expect_json_value release-review-plan.json hasReviewTargets true
  expect_json_compact release-review-plan.json reviewers '["maintainer","other"]'
  expect_json_compact release-review-plan.json skippedReviewers '[{"login":"TheCookieLab","reason":"matches PR author"}]'
  expect_file_contains outputs.txt 'reviewers_json=["maintainer","other"]' \
    "review plan should expose deduplicated fallback reviewers"

  finish_test
  pass "test_release_pr_review_plan_uses_non_author_fallback_reviewers"
}

test_release_pr_review_plan_preserves_team_reviewers() {
  echo "Running test_release_pr_review_plan_preserves_team_reviewers"
  run_test

  bash "$SCRIPT" release-pr-review-plan \
    --release-owner TheCookieLab \
    --pr-author TheCookieLab \
    --reviewers TheCookieLab \
    --team-reviewers "release-managers release-owners,release-managers" \
    --output release-review-plan.json \
    --github-output outputs.txt >/dev/null

  expect_json_value release-review-plan.json hasReviewTargets true
  expect_json_compact release-review-plan.json reviewers '[]'
  expect_json_compact release-review-plan.json teamReviewers '["release-managers","release-owners"]'
  expect_json_value release-review-plan.json warning ""
  expect_file_contains outputs.txt 'team_reviewers_json=["release-managers","release-owners"]' \
    "review plan should expose deduplicated team reviewer JSON"

  finish_test
  pass "test_release_pr_review_plan_preserves_team_reviewers"
}

test_build_dossier_groups_and_truncates_diff() {
  echo "Running test_build_dossier_groups_and_truncates_diff"
  run_test

  git init -q -b master
  git config user.name "Test User"
  git config user.email "test@example.com"
  git config core.autocrlf false
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

  bash "$SCRIPT" build-dossier \
    --last-tag 1.0.0 \
    --current-version 1.0.1-SNAPSHOT \
    --pom-base 1.0.1 \
    --max-diff-chars 120 \
    --output release-dossier.md \
    --audit-output release-audit.json >/dev/null

  expect_file_contains release-dossier.md "production code" "dossier should group production code"
  expect_file_contains release-dossier.md "Public API Signals" "dossier should include API signal section"
  expect_file_contains release-dossier.md "[TRUNCATED" "dossier should make diff truncation explicit"
  expect_json_value release-audit.json selected_diff_truncated true
  expect_file_contains release-dossier.md '+    public String value()' \
    "API signals should survive even when the selected diff stops before the code"
  expect_file_contains release-dossier.md '@since 1.0.1' \
    "Javadoc signals should scan the full diff before truncation"

  finish_test
  pass "test_build_dossier_groups_and_truncates_diff"
}

test_build_dossier_preserves_production_evidence_and_credentials() {
  echo "Running test_build_dossier_preserves_production_evidence_and_credentials"
  run_test

  git init -q -b master
  git config user.name "Test User"
  git config user.email "test@example.com"
  git config core.autocrlf false
  printf '## Unreleased\n\n' > CHANGELOG.md
  git add .
  git commit -q -m "Initial"
  git tag 1.0.0
  python3 - <<'PY'
from pathlib import Path
root = Path("ta4j-core/src/main/java/org/ta4j/core")
root.mkdir(parents=True)
(root / "AFat.java").write_text(
    "package org.ta4j.core;\npublic class AFat {\n"
    + "    // Implementation evidence in a large production file.\n" * 2000
    + "    public double lateApiChange() { return 2.0; }\n}\n", encoding="utf-8")
(root / "ZImportant.java").write_text(
    "package org.ta4j.core;\n/** @since 1.1.0 */\npublic class ZImportant {\n"
    + "    // Synthetic header: Authorization: Basic dXNlcjpwYXNz\n"
    + "    public double userVisibleChange() { return 3.0; }\n}\n", encoding="utf-8")
workflow = Path(".github/workflows/large.yml")
workflow.parent.mkdir(parents=True)
workflow.write_text("# Large infrastructure-only padding.\n" * 12000, encoding="utf-8")
Path("CHANGELOG.md").write_text("""## Unreleased

- Public reference: https://github.com/ta4j/ta4j/releases/tag/1.0.0
- Commit: 1234567890abcdef1234567890abcdef12345678
- Synthetic credentials below exercise redaction, not real secrets.
- ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ123456
- github_pat_ABCDEFGHIJKLMNOPQRSTUVWXYZ123456
- sk-proj-ABCDEFGHIJKLMNOPQRSTUVWXYZ123456
- API_KEY = "fixture-secret-value"
- password='fixture-password-value'
- Authorization: Bearer fixture-auth-value
- Authorization: Basic dXNlcjpwYXNz
- "Proxy-Authorization": "bAsIc Zml4dHVyZTpwYXNzd29yZA=="
- https://fixture-user:fixture-pass@example.invalid/path?token=fixture-query-value
-----BEGIN PRIVATE KEY-----
fixture-private-key-material
-----END PRIVATE KEY-----
""", encoding="utf-8")
PY
  git add .
  git commit -q -m "Change production code and large tooling file"

  bash "$SCRIPT" build-dossier --last-tag 1.0.0 --current-version 1.1.0-SNAPSHOT \
    --pom-base 1.1.0 --max-diff-chars 4000 >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path
report = json.loads(Path("release-audit.json").read_text())
coverage = report["diff_coverage"]["categories"]
assert coverage["production code"]["available_files"] == 2
assert coverage["production code"]["included_files"] == 2
assert coverage["production code"]["omitted_files"] == 0
assert coverage["production code"]["truncated_files"] == 1
assert coverage["workflows"]["truncated_files"] + coverage["workflows"]["omitted_files"] == 1
assert report["selected_diff_chars"] <= 4000
dossier = Path("release-dossier.md").read_text()
diff = dossier.split("## Selected Diff\n", 1)[1]
assert diff.index("ta4j-core/src/main/") < diff.index(".github/workflows/")
assert "Implementation evidence" in diff
assert "userVisibleChange()" in diff
assert "lateApiChange()" in dossier.split("## Selected Diff\n", 1)[0]
assert "https://github.com/ta4j/ta4j/releases/tag/1.0.0" in dossier
assert "1234567890abcdef1234567890abcdef12345678" in dossier
assert "[REDACTED_OPENAI_KEY]" in dossier
assert "[REDACTED_PRIVATE_KEY]" in dossier
assert "Authorization: Basic [REDACTED_CREDENTIALS]" in dossier
assert '"Proxy-Authorization": "bAsIc [REDACTED_CREDENTIALS]"' in dossier
for secret in ("ABCDEFGHIJKLMNOPQRSTUVWXYZ123456", "fixture-secret-value",
               "fixture-password-value", "fixture-auth-value", "fixture-pass",
               "fixture-query-value", "fixture-private-key-material",
               "dXNlcjpwYXNz", "Zml4dHVyZTpwYXNzd29yZA=="):
    assert secret not in dossier, secret
PY

  finish_test
  pass "test_build_dossier_preserves_production_evidence_and_credentials"
}

test_build_dossier_retains_production_deletion_when_file_moves_into_tests() {
  echo "Running test_build_dossier_retains_production_deletion_when_file_moves_into_tests"
  run_test

  git init -q -b master
  git config user.name "Test User"
  git config user.email "test@example.com"
  git config core.autocrlf false
  git config diff.renames true
  mkdir -p ta4j-core/src/main/java/org/ta4j/core ta4j-core/src/test/java/org/ta4j/core
  printf '## Unreleased\n\n- Move the fixture into tests.\n' > CHANGELOG.md
  cat > ta4j-core/src/main/java/org/ta4j/core/Moved.java <<'EOF'
package org.ta4j.core;

/** @since 0.1.0 */
public class Moved {
    public double value() { return 1.0; }
}
EOF
  git add .
  git commit -q -m "Initial public production class"
  git tag 1.0.0
  git mv ta4j-core/src/main/java/org/ta4j/core/Moved.java ta4j-core/src/test/java/org/ta4j/core/Moved.java
  git commit -q -m "Move unchanged class into tests"
  git diff --name-status --find-renames 1.0.0..HEAD > recognized-rename.txt
  expect_file_contains recognized-rename.txt "R100" "fixture must reproduce a Git-detected exact rename"

  bash "$SCRIPT" build-dossier --last-tag 1.0.0 --current-version 1.1.0-SNAPSHOT \
    --pom-base 1.1.0 >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path
report = json.loads(Path("release-audit.json").read_text())
assert report["changed_file_count"] == 2
assert report["category_counts"] == {"production code": 1, "tests": 1}
for category in ("production code", "tests"):
    coverage = report["diff_coverage"]["categories"][category]
    assert coverage["available_files"] == coverage["included_files"] == 1
    assert coverage["omitted_files"] == coverage["truncated_files"] == 0
    assert coverage["available_bytes"] == coverage["included_bytes"] > 0
dossier = Path("release-dossier.md").read_text()
signals = dossier.split("## Public API Signals", 1)[1].split("## Javadoc", 1)[0]
assert "-public class Moved" in signals
assert "-    public double value()" in signals
assert "+public class Moved" not in signals, "test-only additions must not mask a removed production API"
diff = dossier.split("## Selected Diff", 1)[1]
assert "deleted file mode" in diff
assert "--- a/ta4j-core/src/main/java/org/ta4j/core/Moved.java" in diff
assert "+++ /dev/null" in diff
assert "+++ b/ta4j-core/src/test/java/org/ta4j/core/Moved.java" in diff
assert diff.index("ta4j-core/src/main/") < diff.index("ta4j-core/src/test/")
PY

  finish_test
  pass "test_build_dossier_retains_production_deletion_when_file_moves_into_tests"
}

test_last_release_date_uses_annotated_tag_date() {
  echo "Running test_last_release_date_uses_annotated_tag_date"
  run_test

  git init -q -b master release-repo
  (
    cd release-repo
    git config user.name "Release Test"
    git config user.email "release-test@example.com"
    GIT_AUTHOR_DATE=2026-07-01T00:00:00Z GIT_COMMITTER_DATE=2026-07-01T00:00:00Z git commit -q --allow-empty -m "base commit"
    GIT_COMMITTER_DATE=2026-08-03T00:00:00Z git tag -a 1.0.0 -m "Release 1.0.0"
  )

  local date_out
  date_out="$(cd release-repo && bash "$SCRIPT" last-release-date --tag 1.0.0)"
  if [[ "$date_out" != "2026-08-03" ]]; then
    fail "last-release-date should report the annotated tag date, not the tagged commit date (got '${date_out}')"
  fi

  local none_out
  none_out="$(cd release-repo && bash "$SCRIPT" last-release-date --tag none)"
  if [[ "$none_out" != "none" ]]; then
    fail "last-release-date should pass through 'none' when there is no prior release (got '${none_out}')"
  fi

  local unresolved_output
  if unresolved_output="$(cd release-repo && bash "$SCRIPT" last-release-date --tag 9.9.9 2>&1)"; then
    fail "last-release-date should fail for a known-but-unresolvable tag (got: ${unresolved_output})"
  fi

  (
    cd release-repo
    git tag 2.0.0
  )
  local lightweight_output
  if lightweight_output="$(cd release-repo && bash "$SCRIPT" last-release-date --tag 2.0.0 2>&1)"; then
    fail "last-release-date should reject a lightweight tag whose creatordate is the commit date (got: ${lightweight_output})"
  fi

  finish_test
  pass "test_last_release_date_uses_annotated_tag_date"
}

test_artifact_manifest_validates_expected_release_jars() {
  echo "Running test_artifact_manifest_validates_expected_release_jars"
  run_test

  write_reactor_pom ta4j-core ta4j-examples
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

  bash "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict >/dev/null
  expect_file_contains artifact-manifest.txt "Missing release artifacts:" "manifest should include missing section"
  expect_file_contains artifact-manifest.txt "- (none)" "manifest should report no missing artifacts"

  : > "ta4j-core/target/unexpected.jar"
  if bash "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict >manifest.log 2>&1; then
    fail "strict artifact manifest should reject unexpected jars"
  fi
  expect_file_contains manifest.log "Unexpected target jars" "strict failure should name unexpected jars"

  finish_test
  pass "test_artifact_manifest_validates_expected_release_jars"
}

write_reactor_pom() {
  local module
  {
    printf '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>\n'
    for module in "$@"; do
      printf '<module>%s</module>\n' "$module"
    done
    printf '</modules></project>\n'
  } > pom.xml
}

test_artifact_manifest_includes_declared_acceleration_module() {
  echo "Running test_artifact_manifest_includes_declared_acceleration_module"
  run_test

  local version=1.2.3 file
  local -a base=(
    "ta4j-core/target/ta4j-core-${version}.jar"
    "ta4j-core/target/ta4j-core-${version}-sources.jar"
    "ta4j-core/target/ta4j-core-${version}-javadoc.jar"
    "ta4j-core/target/ta4j-core-${version}-tests.jar"
    "ta4j-examples/target/ta4j-examples-${version}.jar"
    "ta4j-examples/target/ta4j-examples-${version}-sources.jar"
    "ta4j-examples/target/ta4j-examples-${version}-javadoc.jar"
  )
  local -a provider=(
    "ta4j-acceleration/target/ta4j-acceleration-${version}.jar"
    "ta4j-acceleration/target/ta4j-acceleration-${version}-sources.jar"
    "ta4j-acceleration/target/ta4j-acceleration-${version}-javadoc.jar"
  )
  for file in "${base[@]}" "${provider[@]}"; do
    mkdir -p "$(dirname "$file")"
    : > "$file"
  done
  # Nested native build helpers are not reactor release artifacts.
  mkdir -p ta4j-acceleration/target/native/build
  : > ta4j-acceleration/target/native/build/resources.jar

  write_reactor_pom ta4j-core ta4j-examples ta4j-acceleration
  bash "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --github-output outputs.txt --strict >/dev/null
  expect_file_contains outputs.txt "missing_count=0" "declared provider module should be fully present"
  expect_file_contains outputs.txt "unexpected_count=0" "nested native build jars must be ignored"
  expect_file_contains artifact-manifest.txt "- ta4j-acceleration/target/ta4j-acceleration-${version}.jar" \
    "declared provider module should be an expected release artifact"

  : > "ta4j-acceleration/target/ta4j-acceleration-${version}-metal-macos-aarch64.jar"
  if bash "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict >classifier.log 2>&1; then
    fail "strict artifact manifest should reject locally built native classifiers"
  fi
  expect_file_contains classifier.log "ta4j-acceleration-${version}-metal-macos-aarch64.jar" \
    "strict failure should name the native classifier"
  rm "ta4j-acceleration/target/ta4j-acceleration-${version}-metal-macos-aarch64.jar"

  # A historical tag without the module keeps the original manifest, and stray
  # provider outputs are unexpected rather than opting the module in.
  write_reactor_pom ta4j-core ta4j-examples
  if bash "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict >undeclared.log 2>&1; then
    fail "strict artifact manifest should reject provider jars when the module is not declared"
  fi
  expect_file_contains undeclared.log "ta4j-acceleration/target/ta4j-acceleration-${version}.jar" \
    "undeclared provider output should be reported as unexpected"

  finish_test
  pass "test_artifact_manifest_includes_declared_acceleration_module"
}

test_javadoc_warning_baseline_rejects_new_warnings() {
  echo "Running test_javadoc_warning_baseline_rejects_new_warnings"
  run_test

  cat > baseline.txt <<'EOF'
ta4j-core/src/main/java/org/ta4j/core/Foo.java: warning: no @param for value
EOF
  cat > release.log <<'EOF'
[WARNING] /home/runner/work/ta4j/ta4j/ta4j-core/src/main/java/org/ta4j/core/Foo.java: warning: no @param for value
[WARNING] /home/runner/work/ta4j/ta4j/ta4j-core/src/test/java/org/ta4j/core/FooTest.java: uses unchecked or unsafe operations.
EOF

  bash "$SCRIPT" javadoc-warnings \
    --baseline baseline.txt \
    --output javadoc-warnings.txt \
    --github-output outputs.txt \
    --fail-on-new \
    release.log >/dev/null
  expect_file_contains outputs.txt "javadoc_warning_new_count=0" "baseline match should not report new warnings"
  expect_file_contains javadoc-warnings.txt "current_count=1" "compiler warnings should not count as Javadoc warnings"

  cat >> release.log <<'EOF'
[WARNING] /home/runner/work/ta4j/ta4j/ta4j-examples/src/main/java/ta4jexamples/FooExample.java: warning: no @return
EOF
  if bash "$SCRIPT" javadoc-warnings \
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

test_release_pr_review_plan_defaults_to_owner
test_release_pr_review_plan_skips_self_review_without_failing
test_release_pr_review_plan_uses_non_author_fallback_reviewers
test_release_pr_review_plan_preserves_team_reviewers
test_build_dossier_groups_and_truncates_diff
test_build_dossier_preserves_production_evidence_and_credentials
test_build_dossier_retains_production_deletion_when_file_moves_into_tests
test_last_release_date_uses_annotated_tag_date
test_artifact_manifest_validates_expected_release_jars
test_artifact_manifest_includes_declared_acceleration_module
test_javadoc_warning_baseline_rejects_new_warnings

echo
echo "All release helper tests passed."
