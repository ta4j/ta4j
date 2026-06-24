#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/run-full-build-quiet.sh"

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
echo "[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 1"
echo "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in ExampleTest"
echo "[INFO] BUILD SUCCESS"
EOF
  chmod +x "$TMP/bin/mvn"
}

run_quiet_build() {
  export PATH="$TMP/bin:$PATH"
  export FAKE_MAVEN_ARGS="$TMP/maven-args.txt"
  export QUIET_BUILD_TIMEOUT_SECONDS=30
  export BASH_ENV=/dev/null
  "$@"
}

latest_log_from_output() {
  local output="$1"
  sed -n 's/^Full build log saved to: //p' <<<"$output" | tail -n 1
}

test_default_invocation_uses_verify() {
  echo "Running test_default_invocation_uses_verify"
  create_test_repo
  write_fake_maven

  local output
  output="$(run_quiet_build scripts/run-full-build-quiet.sh)"

  expect_contains "$output" "Using system Maven from PATH: mvn" "script should use fake system Maven"
  expect_contains "$output" "Maven goals: verify" "default goal should be verify"
  expect_file_contains_line "$TMP/maven-args.txt" "verify" "default invocation should pass verify"
  expect_file_not_contains_line "$TMP/maven-args.txt" "clean" "default invocation should not pass old clean goal"
  expect_file_not_contains_line "$TMP/maven-args.txt" "license:format" "default invocation should not pass old license goal"
  expect_file_not_contains_line "$TMP/maven-args.txt" "formatter:format" "default invocation should not pass old formatter goal"
  expect_file_not_contains_line "$TMP/maven-args.txt" "install" "default invocation should not pass old install goal"

  finish_test_repo
  pass "test_default_invocation_uses_verify"
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

  expect_contains "$output" "[WARNING] Useful warning" "warning should be visible"
  expect_contains "$output" "[INFO] BUILD SUCCESS" "build result should be visible"
  expect_contains "$output" "Tests run: 2, Failures: 0, Errors: 0, Skipped: 1" "aggregate test summary should be visible"
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

test_default_invocation_uses_verify
test_goals_override_and_maven_args_passthrough
test_noise_is_logged_but_not_printed
test_maven_wrapper_is_preferred_when_present

echo
echo "All quiet full-build script tests passed."
