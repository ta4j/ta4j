#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/run-full-build-quiet.sh"
SCRIPT_PS="$ROOT/scripts/run-full-build-quiet.ps1"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
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

expect_not_contains() {
  local haystack="$1"
  local needle="$2"
  local msg="$3"
  if grep -Fq -- "$needle" <<<"$haystack"; then
    fail "$msg (unexpected: '$needle')"
  fi
}

expect_file_contains_line() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fxq -- "$needle" "$file"; then
    fail "$msg (missing exact arg: '$needle')"
  fi
}

expect_file_not_contains_line() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if grep -Fxq -- "$needle" "$file"; then
    fail "$msg (unexpected exact arg: '$needle')"
  fi
}

create_test_repo() {
  TMP="$(mktemp -d "${TMPDIR:-/tmp}/quiet-build-test.XXXXXX")"
  mkdir -p "$TMP/scripts" "$TMP/bin"
  cp "$SCRIPT" "$TMP/scripts/run-full-build-quiet.sh"
  cp "$SCRIPT_PS" "$TMP/scripts/run-full-build-quiet.ps1"
  chmod +x "$TMP/scripts/run-full-build-quiet.sh"
  pushd "$TMP" >/dev/null || exit 1
}

finish_test_repo() {
  popd >/dev/null || exit 1
  rm -rf "$TMP"
}

write_fake_maven() {
  cat > "$TMP/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$FAKE_MAVEN_ARGS"
echo "[INFO] Scanning for projects..."
echo "ordinary noise that should stay out of stdout"
echo "[WARNING] Useful warning"
echo "[WARNING] Useful warning"
echo "[WARNING] Tests run: 4, Failures: 0, Errors: 0, Skipped: 1, Time elapsed: 0.01 s -- in SkippedFixtureTest"
if [[ "${FAKE_MAVEN_CAPS:-0}" == "1" ]]; then
  for index in $(seq 1 14); do
    echo "[WARNING] capped fixture warning ${index}"
  done
  for index in $(seq 1 14); do
    echo "unexpected fixture diagnostic ${index}"
  done
fi
if [[ "${FAKE_MAVEN_UNFORMATTED:-0}" == "1" ]]; then
  for arg in "$@"; do
    if [[ "$arg" == "formatter:format" ]]; then
      echo formatted > "$FAKE_SOURCE_FILE"
    elif [[ "$arg" == "formatter:validate" ]]; then
      echo "[ERROR] File has not been previously formatted."
      echo "[INFO] BUILD FAILURE"
      exit 8
    fi
  done
fi
if [[ "${FAKE_MAVEN_SUCCESS_UNEXPECTED:-0}" == "1" ]]; then
  echo "java.lang.IllegalStateException: suspicious success diagnostic"
  echo "    at org.ta4j.Fixture.run(Fixture.java:42)"
  echo "unexpected fixture success diagnostic"
fi
if [[ "${FAKE_MAVEN_FAIL:-0}" == "1" ]]; then
  echo "[INFO] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0"
  echo "[INFO] Reactor Summary for fake-ta4j 1.0.0-SNAPSHOT:"
  echo "[INFO] fake-ta4j ................................ FAILURE [  0.001 s]"
  echo "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:test on project fake-ta4j: There are test failures."
  echo "[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0"
  echo "[ERROR] Please refer to /tmp/fake/target/surefire-reports for the individual test results."
  echo "[ERROR] PMD Failure: AvoidDuplicateLiterals in Example.java"
  echo "[ERROR] JaCoCo rule violated: branch coverage ratio is 0.70, expected minimum is 0.80"
  echo "java.lang.IllegalArgumentException: fixture failure"
  echo "    at org.ta4j.Fixture.fail(Fixture.java:17)"
  echo "[INFO] BUILD FAILURE"
  exit 7
fi
echo "[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 1"
echo "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in ExampleTest"
echo "[INFO] Reactor Summary for fake-ta4j 1.0.0-SNAPSHOT:"
echo "[INFO] fake-ta4j ................................ SUCCESS [  0.001 s]"
echo "[INFO] BUILD SUCCESS"
EOF
  chmod +x "$TMP/bin/mvn"
}

run_quiet_build() {
  export PATH="$TMP/bin:$PATH"
  export FAKE_MAVEN_ARGS="$TMP/maven-args.txt"
  export QUIET_BUILD_TIMEOUT_SECONDS=0
  export BASH_ENV=/dev/null
  "$@"
}

latest_log_from_output() {
  local output="$1"
  sed -n 's/^Full build log saved to: //p' <<<"$output" | tail -n 1
}

test_default_invocation_uses_local_repair_gate() {
  echo "Running test_default_invocation_uses_local_repair_gate"
  create_test_repo
  write_fake_maven

  local output
  output="$(run_quiet_build scripts/run-full-build-quiet.sh)"

  expect_contains "$output" "Using system Maven from PATH: mvn" "script should use fake system Maven"
  expect_contains "$output" "Maven goals: clean license:format formatter:format verify" "default goals should repair local sources before verify"
  expect_contains "$output" "[INFO] BUILD SUCCESS" "default invocation should surface build result"
  expect_file_contains_line "$TMP/maven-args.txt" "clean" "default invocation should clean generated output"
  expect_file_contains_line "$TMP/maven-args.txt" "license:format" "default invocation should repair license headers"
  expect_file_contains_line "$TMP/maven-args.txt" "formatter:format" "default invocation should repair formatting"
  expect_file_contains_line "$TMP/maven-args.txt" "verify" "default invocation should pass verify"
  expect_file_contains_line "$TMP/maven-args.txt" "-Dta4j.excludedTestTags=analysis-demo" "default invocation should include hosted non-demo tests"
  expect_file_not_contains_line "$TMP/maven-args.txt" "license:check" "default invocation should not duplicate hosted license validation"
  expect_file_not_contains_line "$TMP/maven-args.txt" "formatter:validate" "default invocation should not duplicate hosted format validation"
  expect_file_not_contains_line "$TMP/maven-args.txt" "install" "default invocation should not add a non-CI lifecycle phase"

  finish_test_repo
  pass "test_default_invocation_uses_local_repair_gate"
}

test_preflight_only_runs_repository_checks_without_maven() {
  echo "Running test_preflight_only_runs_repository_checks_without_maven"
  create_test_repo
  write_fake_maven
  mkdir -p "$TMP/.github/workflows" "$TMP/scripts/tests"
  cat > "$TMP/bin/actionlint" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "-version" ]]; then
  echo "1.7.12"
  exit 0
fi
echo actionlint > "$FAKE_ACTIONLINT_MARKER"
EOF
  chmod +x "$TMP/bin/actionlint"
  cat > "$TMP/scripts/tests/test_fixture.sh" <<'EOF'
#!/usr/bin/env bash
echo fixture > "$FAKE_FIXTURE_MARKER"
EOF

  export FAKE_ACTIONLINT_MARKER="$TMP/actionlint-ran.txt"
  export FAKE_FIXTURE_MARKER="$TMP/fixture-ran.txt"
  local output
  output="$(run_quiet_build scripts/run-full-build-quiet.sh --preflight-only)"

  expect_contains "$output" "Repository preflight checks passed." "preflight-only mode should report success"
  [[ -f "$FAKE_ACTIONLINT_MARKER" ]] || fail "preflight-only mode should run actionlint"
  [[ -f "$FAKE_FIXTURE_MARKER" ]] || fail "preflight-only mode should run script fixtures"
  [[ ! -f "$TMP/maven-args.txt" ]] || fail "preflight-only mode must not invoke Maven"

  finish_test_repo
  pass "test_preflight_only_runs_repository_checks_without_maven"
}

test_default_gate_repairs_unformatted_source() {
  echo "Running test_default_gate_repairs_unformatted_source"
  create_test_repo
  write_fake_maven
  local source_file="$TMP/Unformatted.java"
  echo unformatted > "$source_file"

  local output
  output="$(FAKE_MAVEN_UNFORMATTED=1 FAKE_SOURCE_FILE="$source_file" run_quiet_build scripts/run-full-build-quiet.sh)"

  expect_contains "$output" "BUILD SUCCESS" "local repair gate should continue through verify"
  expect_file_contains_line "$source_file" "formatted" "default gate should repair unformatted source"

  finish_test_repo
  pass "test_default_gate_repairs_unformatted_source"
}

test_validate_only_rejects_unformatted_source_without_repairing_it() {
  echo "Running test_validate_only_rejects_unformatted_source_without_repairing_it"
  create_test_repo
  write_fake_maven
  local source_file="$TMP/Unformatted.java"
  echo unformatted > "$source_file"

  local output
  if output="$(FAKE_MAVEN_UNFORMATTED=1 FAKE_SOURCE_FILE="$source_file" run_quiet_build scripts/run-full-build-quiet.sh --validate-only 2>&1)"; then
    fail "validate-only gate should reject unformatted source"
  fi

  expect_contains "$output" "BUILD FAILURE" "format validation failure should be visible"
  expect_file_contains_line "$source_file" "unformatted" "validate-only gate must leave unformatted source unchanged"
  expect_file_contains_line "$TMP/maven-args.txt" "license:check" "validate-only should check license headers"
  expect_file_contains_line "$TMP/maven-args.txt" "formatter:validate" "validate-only should validate formatting"
  expect_file_not_contains_line "$TMP/maven-args.txt" "license:format" "validate-only must not repair license headers"
  expect_file_not_contains_line "$TMP/maven-args.txt" "formatter:format" "validate-only must not repair formatting"

  finish_test_repo
  pass "test_validate_only_rejects_unformatted_source_without_repairing_it"
}

test_goals_override_and_maven_args_passthrough() {
  echo "Running test_goals_override_and_maven_args_passthrough"
  create_test_repo
  write_fake_maven

  local output
  output="$(run_quiet_build scripts/run-full-build-quiet.sh --goals "test jacoco:report jacoco:check" -- -pl ta4j-core -am -Dgroups=integration)"

  expect_contains "$output" "Maven goals: test jacoco:report jacoco:check" "custom goals should be reported"
  expect_file_contains_line "$TMP/maven-args.txt" "test" "custom test goal should be passed"
  expect_file_contains_line "$TMP/maven-args.txt" "jacoco:report" "custom jacoco report goal should be passed"
  expect_file_contains_line "$TMP/maven-args.txt" "jacoco:check" "custom jacoco check goal should be passed"
  expect_file_contains_line "$TMP/maven-args.txt" "-pl" "module selector should be passed through"
  expect_file_contains_line "$TMP/maven-args.txt" "ta4j-core" "module name should be passed through"
  expect_file_contains_line "$TMP/maven-args.txt" "-am" "also-make flag should be passed through"
  expect_file_contains_line "$TMP/maven-args.txt" "-Dgroups=integration" "tag selector should be passed through"
  expect_file_not_contains_line "$TMP/maven-args.txt" "verify" "custom goals should replace default verify"

  finish_test_repo
  pass "test_goals_override_and_maven_args_passthrough"
}

test_noise_is_logged_but_not_printed() {
  echo "Running test_noise_is_logged_but_not_printed"
  create_test_repo
  write_fake_maven

  local output
  output="$(run_quiet_build scripts/run-full-build-quiet.sh)"
  local log_file
  log_file="$(latest_log_from_output "$output")"

  expect_contains "$output" "Warnings summary:" "warning digest should be visible"
  expect_contains "$output" "Useful warning (2x)" "duplicate warning count should be visible"
  expect_contains "$output" "[INFO] BUILD SUCCESS" "build result should be visible"
  expect_contains "$output" "Reactor summary:" "reactor summary should be visible"
  expect_contains "$output" "Tests run: 2, Failures: 0, Errors: 0, Skipped: 1" "aggregate test summary should be visible"
  expect_not_contains "$output" "[WARNING] Useful warning" "raw warning line should be summarized, not streamed"
  expect_not_contains "$output" "SkippedFixtureTest" "per-test skipped summaries should not pollute warning digest"
  expect_not_contains "$output" "ordinary noise that should stay out of stdout" "ordinary Maven noise should be filtered from stdout"
  if [[ -z "$log_file" || ! -f "$log_file" ]]; then
    fail "quiet build should report an existing full log path"
  fi
  if ! grep -Fq "ordinary noise that should stay out of stdout" "$log_file"; then
    fail "full log should contain filtered Maven noise"
  fi

  finish_test_repo
  pass "test_noise_is_logged_but_not_printed"
}

test_unexpected_success_output_is_summarized() {
  echo "Running test_unexpected_success_output_is_summarized"
  create_test_repo
  write_fake_maven

  local output
  output="$(FAKE_MAVEN_SUCCESS_UNEXPECTED=1 run_quiet_build scripts/run-full-build-quiet.sh)"

  expect_contains "$output" "Unexpected output summary:" "unexpected summary should be visible"
  expect_contains "$output" "java.lang.IllegalStateException: suspicious success diagnostic" "exception should be surfaced"
  expect_contains "$output" "at org.ta4j.Fixture.run(Fixture.java:42)" "stack frame should be surfaced"
  expect_contains "$output" "unexpected fixture success diagnostic" "unexpected line should be surfaced"
  expect_contains "$output" "[INFO] BUILD SUCCESS" "suspicious success should preserve Maven result"

  finish_test_repo
  pass "test_unexpected_success_output_is_summarized"
}

test_failure_output_has_digest_sections() {
  echo "Running test_failure_output_has_digest_sections"
  create_test_repo
  write_fake_maven

  local output
  if output="$(FAKE_MAVEN_FAIL=1 run_quiet_build scripts/run-full-build-quiet.sh 2>&1)"; then
    fail "failing fake Maven should make quiet build fail"
  fi

  expect_contains "$output" "[INFO] BUILD FAILURE" "failure marker should be visible"
  expect_contains "$output" "Failure digest:" "failure digest should be visible"
  expect_contains "$output" "Failed modules:" "failed modules should be summarized"
  expect_contains "$output" "fake-ta4j ................................ FAILURE" "failed module should be shown"
  expect_contains "$output" "Failed goals:" "failed goals should be summarized"
  expect_contains "$output" "Test/report hints:" "test hints should be summarized"
  expect_contains "$output" "Quality gate hints:" "quality hints should be summarized"
  expect_contains "$output" "Exception/stack-trace hints:" "exception hints should be summarized"
  expect_contains "$output" "Maven error tail:" "Maven error tail should be summarized"
  expect_contains "$output" "Warnings summary:" "failure warning digest should be visible"
  expect_contains "$output" "Unexpected output summary:" "failure unexpected digest should be visible"
  expect_contains "$output" "Full build log saved to:" "failure should still report the raw log path"

  finish_test_repo
  pass "test_failure_output_has_digest_sections"
}

test_digest_caps_prevent_output_floods() {
  echo "Running test_digest_caps_prevent_output_floods"
  create_test_repo
  write_fake_maven

  local output
  output="$(FAKE_MAVEN_CAPS=1 run_quiet_build scripts/run-full-build-quiet.sh)"

  expect_contains "$output" "Warnings summary:" "capped warning digest should be visible"
  expect_contains "$output" "more unique warning(s); see full log" "warning digest should report omitted warnings"
  expect_not_contains "$output" "capped fixture warning 14" "warning digest should cap visible warnings"
  expect_contains "$output" "Unexpected output summary:" "capped unexpected digest should be visible"
  expect_contains "$output" "more unique unexpected line(s); see full log" "unexpected digest should report omitted lines"
  expect_not_contains "$output" "unexpected fixture diagnostic 14" "unexpected digest should cap visible lines"

  finish_test_repo
  pass "test_digest_caps_prevent_output_floods"
}

test_maven_wrapper_is_preferred_when_present() {
  echo "Running test_maven_wrapper_is_preferred_when_present"
  create_test_repo
  write_fake_maven
  cat > "$TMP/mvnw" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$FAKE_MAVEN_ARGS"
echo "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0"
echo "[INFO] BUILD SUCCESS"
EOF
  chmod +x "$TMP/mvnw"

  local output
  output="$(run_quiet_build scripts/run-full-build-quiet.sh -- -Dspotbugs.skip=true -Djacoco.skip=true)"

  expect_contains "$output" "Using Maven Wrapper: ./mvnw" "script should prefer Maven Wrapper"
  expect_file_contains_line "$TMP/maven-args.txt" "-Dspotbugs.skip=true" "SpotBugs skip flag should pass through"
  expect_file_contains_line "$TMP/maven-args.txt" "-Djacoco.skip=true" "JaCoCo skip flag should pass through"
  expect_file_contains_line "$TMP/maven-args.txt" "verify" "wrapper invocation should still default to verify"

  finish_test_repo
  pass "test_maven_wrapper_is_preferred_when_present"
}

test_powershell_entrypoint_classifier_parity() {
  echo "Running test_powershell_entrypoint_classifier_parity"
  create_test_repo
  write_fake_maven

  local ps1
  ps1="$(<scripts/run-full-build-quiet.ps1)"
  expect_contains "$ps1" "\$goals = @(\"clean\", \"license:format\", \"formatter:format\", \"verify\")" "PowerShell local default should repair source"
  expect_contains "$ps1" "'^--validate-only$'" "PowerShell should expose validate-only mode"
  expect_contains "$ps1" "\$goals = @(\"clean\", \"license:check\", \"formatter:validate\", \"verify\")" "PowerShell validate-only mode should preserve hosted goals"

  if [[ "${TA4J_RUN_POWERSHELL_FIXTURE:-false}" == "true" ]] && command -v pwsh >/dev/null 2>&1; then
    local output
    output="$(FAKE_MAVEN_SUCCESS_UNEXPECTED=1 run_quiet_build pwsh -NoLogo -NoProfile -File scripts/run-full-build-quiet.ps1)"
    expect_contains "$output" "Warnings summary:" "PowerShell warning digest should be visible"
    expect_contains "$output" "Unexpected output summary:" "PowerShell unexpected digest should be visible"
    expect_contains "$output" "java.lang.IllegalStateException: suspicious success diagnostic" "PowerShell should surface exceptions"
  else
    expect_contains "$ps1" "function Write-FailureDigest" "PowerShell script should define failure digest"
    expect_contains "$ps1" "function Write-WarningSummary" "PowerShell script should define warning digest"
    expect_contains "$ps1" "function Write-UnexpectedSummary" "PowerShell script should define unexpected digest"
    expect_contains "$ps1" "Test-StackOrExceptionLine" "PowerShell script should classify exception and stack lines"
  fi

  finish_test_repo
  pass "test_powershell_entrypoint_classifier_parity"
}

test_default_invocation_uses_local_repair_gate
test_preflight_only_runs_repository_checks_without_maven
test_default_gate_repairs_unformatted_source
test_validate_only_rejects_unformatted_source_without_repairing_it
test_goals_override_and_maven_args_passthrough
test_noise_is_logged_but_not_printed
test_unexpected_success_output_is_summarized
test_failure_output_has_digest_sections
test_digest_caps_prevent_output_floods
test_maven_wrapper_is_preferred_when_present
test_powershell_entrypoint_classifier_parity

echo
echo "All quiet full-build script tests passed."
