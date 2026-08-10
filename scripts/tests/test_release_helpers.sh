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

write_model_fixture() {
  cat > model.json <<'EOF'
{
  "id": "gpt-5.6-luna",
  "object": "model",
  "created": 1771200000,
  "owned_by": "openai"
}
EOF
}

test_model_preflight_accepts_exact_model() {
  echo "Running test_model_preflight_accepts_exact_model"
  run_test
  write_model_fixture

  GITHUB_OUTPUT=outputs.txt bash "$SCRIPT" model-preflight \
    --model gpt-5.6-luna \
    --model-file model.json \
    --output release-ai-model.json

  expect_json_value release-ai-model.json id gpt-5.6-luna
  expect_json_value release-ai-model.json provider openai
  expect_json_value release-ai-model.json endpoint https://api.openai.com/v1/models/gpt-5.6-luna
  expect_json_value release-ai-model.json httpStatus 200
  expect_file_contains outputs.txt "model_id=gpt-5.6-luna" "model preflight should emit model id"
  expect_file_contains outputs.txt "model_http_status=200" "model preflight should emit HTTP status"

  finish_test
  pass "test_model_preflight_accepts_exact_model"
}

test_model_preflight_rejects_wrong_model() {
  echo "Running test_model_preflight_rejects_wrong_model"
  run_test
  cat > model.json <<'EOF'
{"id":"gpt-5.6-terra","object":"model","owned_by":"openai"}
EOF

  if bash "$SCRIPT" model-preflight \
    --model gpt-5.6-luna \
    --model-file model.json \
    --output release-ai-model.json >preflight.log 2>&1; then
    fail "model preflight should reject a different model ID"
  fi
  expect_json_value release-ai-model.json responseModel gpt-5.6-terra
  expect_file_contains preflight.log "gpt-5.6-luna" "failure should name requested model"

  finish_test
  pass "test_model_preflight_rejects_wrong_model"
}

test_model_preflight_classifies_retryable_and_permanent_statuses() {
  echo "Running test_model_preflight_classifies_retryable_and_permanent_statuses"
  run_test
  write_model_fixture

  if bash "$SCRIPT" model-preflight --model gpt-5.6-luna --model-file model.json --response-status 429 --output retryable.json >retryable.log 2>&1; then
    fail "429 preflight failure should be retryable"
  else
    retryable_status=$?
  fi
  if [[ "$retryable_status" != "75" ]]; then
    fail "429 should return retryable exit code 75 (got $retryable_status)"
  fi
  expect_json_value retryable.json httpStatus 429

  if bash "$SCRIPT" model-preflight --model gpt-5.6-luna --model-file model.json --response-status 401 --output permanent.json >permanent.log 2>&1; then
    fail "401 preflight failure should be permanent"
  else
    permanent_status=$?
  fi
  if [[ "$permanent_status" == "75" ]]; then
    fail "401 should not return retryable exit code"
  fi
  expect_json_value permanent.json httpStatus 401

  finish_test
  pass "test_model_preflight_classifies_retryable_and_permanent_statuses"
}

test_extract_response_content_accepts_output_messages() {
  echo "Running test_extract_response_content_accepts_output_messages"
  run_test

  cat > response.json <<'EOF'
{
  "status": "completed",
  "output": [
    {"type":"reasoning","summary":[{"type":"summary_text","text":"private reasoning"}]},
    {"type":"message","role":"assistant","content":[
      {"type":"output_text","text":"{\"should_release\":false,"},
      {"type":"output_text","text":"\"bump\":\"patch\"}"}
    ]}
  ]
}
EOF
  bash "$SCRIPT" extract-response-content --raw-file response.json --output ai-content.txt --failure-reason-output failure.txt
  expect_file_contains ai-content.txt '"should_release":false' "response extraction should collect output text"
  expect_file_contains ai-content.txt '"bump":"patch"' "response extraction should join output fragments"
  if [[ -s failure.txt ]]; then
    fail "successful response extraction should clear failure file"
  fi

  finish_test
  pass "test_extract_response_content_accepts_output_messages"
}

test_extract_response_content_rejects_refusal_and_incomplete_responses() {
  echo "Running test_extract_response_content_rejects_refusal_and_incomplete_responses"
  run_test

  cat > response.json <<'EOF'
{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"refusal","refusal":"request refused sk-proj-1234567890abcdefghijklmnop"}]}]}
EOF
  if bash "$SCRIPT" extract-response-content --raw-file response.json --output ai-content.txt --failure-reason-output failure.txt >refusal.log 2>&1; then
    fail "refusal response should fail extraction"
  fi
  expect_file_contains failure.txt "request refused" "refusal reason should be preserved"
  expect_file_contains failure.txt "[REDACTED_OPENAI_KEY]" "refusal reason should redact OpenAI-shaped secrets"
  if grep -Fq "sk-proj-1234567890abcdefghijklmnop" failure.txt refusal.log; then
    fail "refusal secret should not be written to files or logs"
  fi

  long_refusal="$(awk 'BEGIN { for (i = 0; i < 4500; i++) printf "x" }')"
  jq -n --arg refusal "$long_refusal" \
    '{status:"completed",output:[{type:"message",role:"assistant",content:[{type:"refusal",refusal:$refusal}]}]}' > response.json
  if bash "$SCRIPT" extract-response-content --raw-file response.json --output ai-content.txt --failure-reason-output failure.txt >long-refusal.log 2>&1; then
    fail "long refusal response should fail extraction"
  fi
  failure_bytes="$(wc -c < failure.txt | tr -d ' ')"
  if [[ "$failure_bytes" -gt 2001 ]]; then
    fail "sanitized long refusal reason should be bounded to 2000 bytes plus newline"
  fi

  cat > response.json <<'EOF'
{"status":"incomplete","output":[]}
EOF
  if bash "$SCRIPT" extract-response-content --raw-file response.json --output ai-content.txt --failure-reason-output failure.txt >incomplete.log 2>&1; then
    fail "incomplete response should fail extraction"
  fi
  expect_file_contains failure.txt "incomplete" "incomplete response reason should be preserved"

  printf '<html>502 Bad Gateway</html>' > response.json
  if bash "$SCRIPT" extract-response-content --raw-file response.json --output ai-content.txt --failure-reason-output failure.txt >invalid.log 2>&1; then
    fail "non-JSON response should fail extraction"
  fi
  expect_file_contains failure.txt "not valid JSON" "invalid JSON reason should be preserved"

  finish_test
  pass "test_extract_response_content_rejects_refusal_and_incomplete_responses"
}

test_sanitize_response_artifact_omits_reasoning_output() {
  echo "Running test_sanitize_response_artifact_omits_reasoning_output"
  run_test

  cat > response.json <<'EOF'
{
  "id": "resp_123",
  "model": "gpt-5.6-luna",
  "status": "completed",
  "output": [
    {"type":"reasoning","encrypted_content":"private chain of thought"},
    {"type":"message","role":"assistant","content":[{"type":"output_text","text":"{\"should_release\":false}"}]}
  ]
}
EOF
  bash "$SCRIPT" sanitize-response --raw-file response.json --output sanitized-response.json

  expect_json_value sanitized-response.json reasoningOutputOmitted true
  expect_file_contains sanitized-response.json 'should_release' \
    "sanitized response should retain output message text"
  if grep -Fq '"type": "reasoning"' sanitized-response.json || grep -Fq "private chain of thought" sanitized-response.json; then
    fail "sanitized response should omit reasoning output"
  fi

  finish_test
  pass "test_sanitize_response_artifact_omits_reasoning_output"
}

test_ai_transport_diagnostics_records_response_validation_failure() {
  echo "Running test_ai_transport_diagnostics_records_response_validation_failure"
  run_test

  printf '{"status":"completed","output":[]}' > response.json
  bash "$SCRIPT" ai-transport-diagnostics \
    --ai-mode probe \
    --model gpt-5.6-luna \
    --provider openai \
    --endpoint https://api.openai.com/v1/responses \
    --reasoning-effort high \
    --failure-reason "OpenAI response did not contain output_text content" \
    --response-status 200 \
    --curl-exit-code 0 \
    --output release-ai-transport-diagnostics.json \
    --fallback-output ai-content.txt

  expect_json_value release-ai-transport-diagnostics.json classification response_validation_failure
  expect_file_contains release-ai-transport-diagnostics.json "output_text content" \
    "response validation diagnostic should preserve the extraction reason"
  expect_file_contains ai-content.txt "OpenAI response did not contain output_text content" \
    "response validation fallback should preserve the extraction reason"

  finish_test
  pass "test_ai_transport_diagnostics_records_response_validation_failure"
}

test_ai_transport_diagnostics_records_openai_request_id() {
  echo "Running test_ai_transport_diagnostics_records_openai_request_id"
  run_test

  printf '%s\n' 'HTTP/2 200' 'x-request-id: req_fixture_123' > response-headers.txt
  printf '%s\n' '{}' > response.json
  bash "$SCRIPT" ai-transport-diagnostics \
    --ai-mode probe \
    --model gpt-5.6-luna \
    --provider openai \
    --endpoint https://api.openai.com/v1/responses \
    --reasoning-effort high \
    --failure-reason "OpenAI response validation fixture" \
    --response-status 200 \
    --curl-exit-code 0 \
    --response-headers response-headers.txt \
    --response response.json \
    --output release-ai-transport-diagnostics.json \
    --fallback-output ai-content.txt

  expect_json_value release-ai-transport-diagnostics.json openaiRequestId req_fixture_123

  finish_test
  pass "test_ai_transport_diagnostics_records_openai_request_id"
}

test_parse_decision_normalizes_major_and_invalid_json() {
  echo "Running test_parse_decision_normalizes_major_and_invalid_json"
  run_test

  cat > ai-content.txt <<'EOF'
```json
{"should_release":true,"bump":"major","confidence":0.91,"reason":"Breaking but pre-1.0 change","evidence":["public API changed"]}
```
EOF
  bash "$SCRIPT" parse-decision --raw-file ai-content.txt --output decision.json --github-output outputs.txt
  expect_json_value decision.json should_release true
  expect_json_value decision.json bump minor
  expect_file_contains outputs.txt "bump=minor" "major bumps should be downgraded in outputs"

  cat > ai-content.txt <<'EOF'
{"should_release":"false","bump":"minor","confidence":0.7,"reason":"No release needed"}
EOF
  bash "$SCRIPT" parse-decision --raw-file ai-content.txt --output string-false.json
  expect_json_value string-false.json should_release false
  expect_json_value string-false.json bump patch

  cat > ai-content.txt <<'EOF'
{"should_release":"1","bump":"patch","confidence":0.7,"reason":"Release needed"}
EOF
  bash "$SCRIPT" parse-decision --raw-file ai-content.txt --output string-true.json
  expect_json_value string-true.json should_release true

  cat > ai-content.txt <<'EOF'
{"should_release":"maybe","bump":"minor","confidence":0.7,"reason":"Ambiguous response"}
EOF
  bash "$SCRIPT" parse-decision --raw-file ai-content.txt --output invalid-flag.json
  expect_json_value invalid-flag.json should_release false
  expect_json_value invalid-flag.json bump patch
  expect_file_contains invalid-flag.json "invalid should_release 'maybe'" "invalid flag should be called out"

  printf 'not-json' > ai-content.txt
  bash "$SCRIPT" parse-decision --raw-file ai-content.txt --output invalid.json
  expect_json_value invalid.json should_release false
  expect_json_value invalid.json bump patch
  expect_json_value invalid.json warning "Invalid AI JSON"

  finish_test
  pass "test_parse_decision_normalizes_major_and_invalid_json"
}

test_release_pr_review_plan_defaults_to_owner() {
  echo "Running test_release_pr_review_plan_defaults_to_owner"
  run_test

  bash "$SCRIPT" release-pr-review-plan \
    --release-owner TheCookieLab \
    --pr-author maintainer \
    --output release-review-plan.json \
    --github-output outputs.txt

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
    --github-output outputs.txt

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
    --github-output outputs.txt

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
    --audit-output release-audit.json

  expect_file_contains release-dossier.md "production code" "dossier should group production code"
  expect_file_contains release-dossier.md "Public API Signals" "dossier should include API signal section"
  expect_file_contains release-dossier.md "[TRUNCATED" "dossier should make diff truncation explicit"
  expect_json_value release-audit.json selected_diff_truncated true

  finish_test
  pass "test_build_dossier_groups_and_truncates_diff"
}

test_build_ai_request_compacts_oversized_dossier() {
  echo "Running test_build_ai_request_compacts_oversized_dossier"
  run_test

  {
    echo "# ta4j Release Dossier"
    echo
    echo "## Metadata"
    echo
    echo "- generated_at: 2026-06-27T00:00:00+00:00"
    echo "- current_version: 1.0.1-SNAPSHOT"
    echo "- pom_base: 1.0.1"
    echo "- last_reachable_tag: 1.0.0"
    echo "- changed_file_count: 3"
    echo
    echo "## Changed Files by Category"
    echo
    echo "### production code (2)"
    echo "- \`ta4j-core/src/main/java/org/ta4j/core/Foo.java\`"
    echo "- \`ta4j-core/src/main/java/org/ta4j/core/Bar.java\`"
    echo
    echo "### tests (1)"
    echo "- \`ta4j-core/src/test/java/org/ta4j/core/FooTest.java\`"
    echo
    echo "## Unreleased Changelog Context"
    echo
    echo "\`\`\`markdown"
    echo "- Added a compact request test fixture."
    for index in $(seq 0 399); do
      echo "- Changelog padding ${index} keeps late dossier sections beyond the inline prefix budget."
    done
    echo "\`\`\`"
    echo
    echo "## Public API Signals"
    echo
    echo "- \`+ public class Foo\`"
    echo
    echo "## Javadoc and @since Signals"
    echo
    echo "- \`+ * @since 1.0.1\`"
    echo
    echo "## Test File Signals"
    echo
    echo "- \`ta4j-core/src/test/java/org/ta4j/core/FooTest.java\`"
    echo
    echo "## Selected Diff"
    echo
    echo "\`\`\`diff"
    for index in $(seq 0 499); do
      echo "+ public String generated${index}() { return \"${index}\"; }"
    done
    echo "\`\`\`"
  } > release-dossier.md

  bash "$SCRIPT" build-ai-request \
    --model gpt-5.6-luna \
    --dossier release-dossier.md \
    --max-dossier-chars 12000 \
    --max-request-bytes 12000 \
    --output request.json \
    --metadata-output release-ai-request-metadata.json

  request_size="$(wc -c < request.json | tr -d ' ')"
  if (( request_size > 12000 )); then
    fail "compact request should stay under forced transport budget (got ${request_size})"
  fi
  expect_json_value release-ai-request-metadata.json artifactBackedContext true
  expect_json_value release-ai-request-metadata.json provider openai
  expect_json_value release-ai-request-metadata.json reasoningEffort high
  expect_json_value request.json store false
  expect_json_value request.json max_output_tokens 4096
  expect_json_value release-ai-request-metadata.json fullDossierTruncatedForPrompt true
  expect_json_value release-ai-request-metadata.json requestWithinTransportBudget true
  expect_file_contains release-ai-request-metadata.json "compact-artifact-backed" \
    "metadata should record compact prompt profile"
  expect_file_contains request.json "The full release-dossier.md is preserved in the scheduler audit artifact." \
    "compact prompt should tell the model where the full dossier lives"
  expect_file_contains request.json '"effort": "high"' \
    "request should pin high reasoning effort"
  expect_file_contains request.json '"input"' \
    "request should use the Responses API input field"
  expect_file_contains request.json "@since 1.0.1" \
    "compact prompt should preserve late dossier signals from the full dossier"

  finish_test
  pass "test_build_ai_request_compacts_oversized_dossier"
}

test_ai_transport_diagnostics_records_curl_exit_18() {
  echo "Running test_ai_transport_diagnostics_records_curl_exit_18"
  run_test

  cat > release-ai-request-metadata.json <<'EOF'
{"schemaVersion":2,"provider":"openai","endpoint":"https://api.openai.com/v1/responses","reasoningEffort":"high","promptProfile":"compact-artifact-backed-v1","requestJsonSizeBytes":11900}
EOF
  cat > release-audit.json <<'EOF'
{"changed_file_count":334,"selected_diff_truncated":true}
EOF
  cat > curl-error.log <<'EOF'
attempt=1 curl_exit_code=18 response_status=200
curl: (18) transfer closed with 1 bytes remaining to read
EOF
  cat > curl-metrics.log <<'EOF'
attempt=1
http_code=200
time_total=2.389
size_upload=11900
size_download=196
EOF
  cat > response-headers.txt <<'EOF'
attempt=1
HTTP/1.1 200 Connection established
EOF
  printf '{"error":"partial"}' > response.json

  bash "$SCRIPT" ai-transport-diagnostics \
    --ai-mode full \
    --model gpt-5.6-luna \
    --provider openai \
    --endpoint https://api.openai.com/v1/responses \
    --reasoning-effort high \
    --response-status 200 \
    --curl-exit-code 18 \
    --attempts 1 \
    --output release-ai-transport-diagnostics.json \
    --fallback-output ai-content.txt

  expect_json_value release-ai-transport-diagnostics.json classification curl_partial_file_transport_close
  expect_json_value release-ai-transport-diagnostics.json connectionClosedDuring response_read
  expect_json_value release-ai-transport-diagnostics.json responseStatus 200
  expect_json_value release-ai-transport-diagnostics.json provider openai
  expect_json_value release-ai-transport-diagnostics.json reasoningEffort high
  expect_file_contains release-ai-transport-diagnostics.json "Do not rerun billed aiMode=full blindly" \
    "diagnostics should include non-blind rerun guidance"
  expect_json_value ai-content.txt should_release false
  expect_file_contains ai-content.txt "curl exit 18, HTTP 200" \
    "fallback decision should prefer curl exit details over the HTTP status"
  expect_file_contains ai-content.txt "release-ai-transport-diagnostics.json" \
    "fallback decision should point at diagnostics artifact"

  finish_test
  pass "test_ai_transport_diagnostics_records_curl_exit_18"
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
    "ta4j-examples/target/ta4j-examples-${version}-javadoc.jar" \
    "ta4j-cli/target/ta4j-cli-${version}.jar" \
    "ta4j-cli/target/ta4j-cli-${version}-sources.jar" \
    "ta4j-cli/target/ta4j-cli-${version}-javadoc.jar"; do
    mkdir -p "$(dirname "$file")"
    : > "$file"
  done

  bash "$SCRIPT" artifact-manifest --version "$version" --output artifact-manifest.txt --strict
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
    release.log
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

test_model_preflight_accepts_exact_model
test_model_preflight_rejects_wrong_model
test_model_preflight_classifies_retryable_and_permanent_statuses
test_extract_response_content_accepts_output_messages
test_extract_response_content_rejects_refusal_and_incomplete_responses
test_sanitize_response_artifact_omits_reasoning_output
test_ai_transport_diagnostics_records_response_validation_failure
test_ai_transport_diagnostics_records_openai_request_id
test_parse_decision_normalizes_major_and_invalid_json
test_release_pr_review_plan_defaults_to_owner
test_release_pr_review_plan_skips_self_review_without_failing
test_release_pr_review_plan_uses_non_author_fallback_reviewers
test_release_pr_review_plan_preserves_team_reviewers
test_build_dossier_groups_and_truncates_diff
test_build_ai_request_compacts_oversized_dossier
test_ai_transport_diagnostics_records_curl_exit_18
test_artifact_manifest_validates_expected_release_jars
test_javadoc_warning_baseline_rejects_new_warnings

echo
echo "All release helper tests passed."
