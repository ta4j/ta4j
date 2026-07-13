#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POM="$ROOT/pom.xml"
CORE_POM="$ROOT/ta4j-core/pom.xml"
EXAMPLES_POM="$ROOT/ta4j-examples/pom.xml"
WORKFLOW="$ROOT/.github/workflows/test.yml"
FORMAT_WORKFLOW="$ROOT/.github/workflows/validate.yml"
LICENSE_WORKFLOW="$ROOT/.github/workflows/check.yml"
ACTIONLINT_WORKFLOW="$ROOT/.github/workflows/actionlint.yml"
QUIET_BUILD="$ROOT/scripts/run-full-build-quiet.sh"
WRAPPER_PROPERTIES="$ROOT/.mvn/wrapper/maven-wrapper.properties"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_file_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" "$file"; then
    fail "$msg (missing: '$needle')"
  fi
}

expect_file_matches() {
  local file="$1"
  local pattern="$2"
  local msg="$3"
  if ! grep -Eq -- "$pattern" "$file"; then
    fail "$msg (pattern: '$pattern')"
  fi
}

expect_file_not_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if grep -Fq -- "$needle" "$file"; then
    fail "$msg (unexpected: '$needle')"
  fi
}

expect_execution_contains() {
  local file="$1"
  local execution_id="$2"
  local needle="$3"
  local msg="$4"
  local block
  block="$(awk -v id="$execution_id" '
    /<execution>/ { in_execution=1; block=$0 ORS; next }
    in_execution { block = block $0 ORS }
    /<\/execution>/ {
      if (block ~ "<id>" id "</id>") {
        print block
        found=1
        exit
      }
      in_execution=0
      block=""
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "$file")" || fail "$msg (execution not found: '$execution_id')"

  if ! grep -Fq -- "$needle" <<<"$block"; then
    fail "$msg (missing: '$needle' in execution '$execution_id')"
  fi
}

test_parent_declares_quality_defaults() {
  echo "Running test_parent_declares_quality_defaults"

  expect_file_matches "$POM" "<spotbugs.version>[0-9]+(\\.[0-9]+)+</spotbugs.version>" "parent pom should pin SpotBugs with an explicit version"
  expect_file_matches "$POM" "<jacoco.version>[0-9]+(\\.[0-9]+)+</jacoco.version>" "parent pom should pin JaCoCo with an explicit version"
  expect_file_contains "$POM" "<ta4j.jacoco.line.minimum>0.80</ta4j.jacoco.line.minimum>" "line coverage threshold should be declared"
  expect_file_contains "$POM" "<ta4j.jacoco.branch.minimum>0.80</ta4j.jacoco.branch.minimum>" "branch coverage threshold should be declared"
  expect_file_not_contains "$POM" "<ta4j.spotbugs.failOnError>" "SpotBugs blocking mode should not rely on a shared property"
  expect_file_not_contains "$POM" "<ta4j.jacoco.haltOnFailure>" "JaCoCo advisory mode should not rely on a shared property"

  pass "test_parent_declares_quality_defaults"
}

test_parent_manages_quality_plugins_for_verify() {
  echo "Running test_parent_manages_quality_plugins_for_verify"

  expect_file_contains "$POM" "<artifactId>spotbugs-maven-plugin</artifactId>" "parent pom should manage SpotBugs"
  expect_file_contains "$POM" "<artifactId>jacoco-maven-plugin</artifactId>" "parent pom should manage JaCoCo"
  expect_file_contains "$POM" "<artifactId>exec-maven-plugin</artifactId>" "parent pom should skip root exec:java runs"
  expect_file_contains "$POM" "<skip>true</skip>" "parent pom should skip exec:java on the aggregator"
  expect_file_contains "$POM" "<quiet>true</quiet>" "SpotBugs should stay compact in verify logs"
  expect_file_not_contains "$POM" "<excludeFilterFile>" "SpotBugs should run without an exclude filter"
  expect_file_contains "$POM" "@{argLine}" "Surefire should late-bind the JaCoCo agent argLine"
  expect_execution_contains "$POM" "spotbugs-check" "<phase>verify</phase>" "SpotBugs should stay wired into verify"
  expect_execution_contains "$POM" "spotbugs-check" "<failOnError>true</failOnError>" "SpotBugs should fail verify for unbaselined findings"
  expect_execution_contains "$POM" "jacoco-check" "<phase>verify</phase>" "JaCoCo should stay wired into verify"
  expect_execution_contains "$POM" "jacoco-check" "<haltOnFailure>false</haltOnFailure>" "JaCoCo should stay advisory only for the verify-bound execution"

  pass "test_parent_manages_quality_plugins_for_verify"
}

test_modules_opt_in_to_managed_quality_plugins() {
  echo "Running test_modules_opt_in_to_managed_quality_plugins"

  expect_file_contains "$CORE_POM" "<artifactId>spotbugs-maven-plugin</artifactId>" "ta4j-core should opt into SpotBugs"
  expect_file_contains "$CORE_POM" "<artifactId>jacoco-maven-plugin</artifactId>" "ta4j-core should opt into JaCoCo"
  expect_file_contains "$EXAMPLES_POM" "<artifactId>spotbugs-maven-plugin</artifactId>" "ta4j-examples should opt into SpotBugs"
  expect_file_contains "$EXAMPLES_POM" "<artifactId>jacoco-maven-plugin</artifactId>" "ta4j-examples should opt into JaCoCo"
  expect_file_contains "$EXAMPLES_POM" "<exec.mainClass>ta4jexamples.Quickstart</exec.mainClass>" "ta4j-examples should default exec:java to Quickstart"
  expect_file_contains "$EXAMPLES_POM" "<mainClass>\${exec.mainClass}</mainClass>" "ta4j-examples should allow exec:java main-class overrides"

  pass "test_modules_opt_in_to_managed_quality_plugins"
}

test_ci_reuses_canonical_local_gates() {
  echo "Running test_ci_reuses_canonical_local_gates"

  expect_file_contains "$WORKFLOW" "xvfb-run scripts/run-full-build-quiet.sh" "hosted verify should call the canonical local gate"
  expect_file_not_contains "$WORKFLOW" "./mvnw -B" "hosted verify should not duplicate the local Maven command"
  expect_file_contains "$FORMAT_WORKFLOW" "scripts/run-full-build-quiet.sh --goals formatter:validate" "hosted formatting should reuse the local entrypoint"
  expect_file_contains "$LICENSE_WORKFLOW" "scripts/run-full-build-quiet.sh --goals license:check" "hosted licensing should reuse the local entrypoint"
  expect_file_contains "$ACTIONLINT_WORKFLOW" "scripts/run-full-build-quiet.sh --preflight-only" "hosted workflow lint should reuse local preflight"
  expect_file_contains "$QUIET_BUILD" "GOALS=(clean license:check formatter:validate verify)" "local default should use non-mutating hosted goals"
  expect_file_contains "$QUIET_BUILD" "-Dta4j.excludedTestTags=analysis-demo" "local default should include hosted non-demo tests"
  expect_file_contains "$QUIET_BUILD" "actionlint@v1.7.12" "local fallback should pin the hosted actionlint version"
  expect_file_not_contains "$QUIET_BUILD" "GOALS=(clean license:format formatter:format" "local default must not repair defects before validation"
  expect_file_not_contains "$WORKFLOW" "spotbugs.skip" "CI should not skip SpotBugs"

  pass "test_ci_reuses_canonical_local_gates"
}

test_maven_wrapper_is_committed_and_pinned() {
  echo "Running test_maven_wrapper_is_committed_and_pinned"

  [[ -f "$ROOT/mvnw" ]] || fail "Maven Wrapper shell script should be committed"
  [[ -f "$ROOT/mvnw.cmd" ]] || fail "Maven Wrapper Windows script should be committed"
  [[ -f "$ROOT/scripts/run-full-build-quiet.ps1" ]] || fail "PowerShell quiet build script should be committed"
  expect_file_contains "$WRAPPER_PROPERTIES" "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip" "Maven Wrapper should pin Maven 3.9.16"
  expect_file_contains "$WRAPPER_PROPERTIES" "distributionType=only-script" "Maven Wrapper should use script-only distribution"

  pass "test_maven_wrapper_is_committed_and_pinned"
}

test_docs_point_to_real_maven_commands() {
  echo "Running test_docs_point_to_real_maven_commands"

  expect_file_contains "$ROOT/README.md" "Use \`scripts/run-full-build-quiet.sh\` on macOS/Linux/Git Bash/WSL or \`scripts/run-full-build-quiet.ps1\` on Windows PowerShell with Git Bash available on \`PATH\`" "README should point contributors at the canonical local/hosted gate and its Bash requirement"
  expect_file_contains "$ROOT/README.md" "./mvnw -B clean license:check formatter:validate verify -Dta4j.excludedTestTags=analysis-demo" "README should document the non-mutating Maven-only equivalent"
  expect_file_contains "$ROOT/README.md" "scripts/run-full-build-quiet.sh" "README should document the quiet Bash verify wrapper"
  expect_file_contains "$ROOT/README.md" "scripts/run-full-build-quiet.ps1" "README should document the quiet PowerShell verify wrapper"
  expect_file_contains "$ROOT/README.md" "./mvnw -pl ta4j-core -am clean compile spotbugs:check" "README should document the standalone SpotBugs loop with clean compilation"
  expect_file_contains "$ROOT/README.md" "./mvnw -pl ta4j-core -am test jacoco:report jacoco:check" "README should document the standalone JaCoCo gate"
  expect_file_contains "$ROOT/README.md" "./mvnw -pl ta4j-core -am -Dtest=BarSeriesManagerTest -Dsurefire.failIfNoSpecifiedTests=false test jacoco:report" "README should document a focused JaCoCo report-only loop"
  expect_file_contains "$ROOT/README.md" "- [Build commands: Maven](#build-commands-maven)" "README table of contents should link to the renamed build section"
  expect_file_contains "$ROOT/README.md" "./mvnw -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.backtesting.TradingRecordParityBacktest" "README should demonstrate overriding exec:java with a non-default example"
  expect_file_contains "$ROOT/.github/CONTRIBUTING.md" "**Run this before opening or updating a PR:** \`scripts/run-full-build-quiet.sh\` on macOS/Linux/Git Bash/WSL or \`scripts/run-full-build-quiet.ps1\` on Windows PowerShell with Git Bash available on \`PATH\`" "contributing guide should use the shared local/hosted gate and document its Bash requirement"
  expect_file_contains "$ROOT/scripts/run-full-build-quiet.ps1" "Git Bash on PATH" "PowerShell usage should disclose the Bash preflight dependency"
  expect_file_contains "$ROOT/.github/CONTRIBUTING.md" "./mvnw -pl ta4j-core -am clean compile spotbugs:check" "contributing guide should document the standalone SpotBugs loop with clean compilation"
  expect_file_contains "$ROOT/.github/CONTRIBUTING.md" "./mvnw -pl ta4j-core -am test jacoco:report jacoco:check" "contributing guide should document the standalone JaCoCo gate"
  expect_file_contains "$ROOT/.github/CONTRIBUTING.md" "./mvnw -B license:format formatter:format" "contributing guide should keep the wrapper formatter and license fix command"
  expect_file_not_contains "$ROOT/README.md" "mvn -pl ta4j-core -am spotbugs:check" "README should not preserve the stale no-compile SpotBugs loop"
  expect_file_not_contains "$ROOT/.github/CONTRIBUTING.md" "mvn -pl ta4j-core -am spotbugs:check" "contributing guide should not preserve the stale no-compile SpotBugs loop"

  pass "test_docs_point_to_real_maven_commands"
}

test_parent_declares_quality_defaults
test_parent_manages_quality_plugins_for_verify
test_modules_opt_in_to_managed_quality_plugins
test_ci_reuses_canonical_local_gates
test_maven_wrapper_is_committed_and_pinned
test_docs_point_to_real_maven_commands

echo
echo "All quality scan config tests passed."
