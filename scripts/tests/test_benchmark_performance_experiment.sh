#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/ta4j-cli/scripts/benchmark-performance-experiment.sh"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_path_prefix() {
  local path="$1"
  local prefix="$2"
  local msg="$3"

  if [[ "$path" != "$prefix"* ]]; then
    fail "$msg (expected prefix '$prefix', got '$path')"
  fi
}

write_fake_date() {
  cat > "$TMP/bin/date" <<'EOF'
#!/usr/bin/env bash
if [[ "$#" -eq 2 && "$1" == "-u" && "$2" == "+%Y%m%dT%H%M%SZ" ]]; then
  printf '20260722T193000Z\n'
  exit 0
fi
/bin/date "$@"
EOF
  chmod +x "$TMP/bin/date"
}

write_fake_git() {
  cat > "$TMP/bin/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "-C" ]]; then
  shift 2
fi

if [[ "${1:-}" == "rev-parse" && "${2:-}" == "--show-toplevel" ]]; then
  printf '%s\n' "$FAKE_REPO_ROOT"
  exit 0
fi

if [[ "${1:-}" == "worktree" && "${2:-}" == "add" && "${3:-}" == "--detach" ]]; then
  mkdir -p "$4"
  printf '%s\n' "${5:-}" > "$4/.fake-ref"
  exit 0
fi

if [[ "${1:-}" == "worktree" && "${2:-}" == "remove" && "${3:-}" == "--force" ]]; then
  rm -rf "$4"
  exit 0
fi

printf 'unexpected fake git invocation:' >&2
printf ' %q' "$@" >&2
printf '\n' >&2
exit 2
EOF
  chmod +x "$TMP/bin/git"
}

write_fake_maven() {
  cat > "$TMP/bin/mvn" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

local_repo=""
for argument in "$@"; do
  case "$argument" in
    -Dmaven.repo.local=*) local_repo="${argument#*=}" ;;
  esac
done

if [[ -z "$local_repo" ]]; then
  echo "missing -Dmaven.repo.local" >&2
  exit 3
fi

mkdir -p "$local_repo"
printf '%s\t%s\t%s\n' "$$" "$PWD" "$local_repo" > "$FAKE_MAVEN_LOG_DIR/$PPID-$$.log"
sleep 0.05
EOF
  chmod +x "$TMP/bin/mvn"
}

run_benchmark() {
  local output_file="$1"
  shift

  BASH_ENV=/dev/null \
  FAKE_REPO_ROOT="$TMP/repo" \
  FAKE_MAVEN_LOG_DIR="$TMP/maven-logs" \
  PATH="$TMP/bin:$PATH" \
    "$SCRIPT" "$@" -- --experiment fixture --bar-counts 1 --scenarios endOnly --repetitions 1 > "$output_file"
}

comparison_dir_from_output() {
  sed -n 's/^Performance comparison written to //p' "$1" | tail -n 1
}

test_concurrent_default_invocations_are_isolated() {
  echo "Running test_concurrent_default_invocations_are_isolated"

  TMP="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-benchmark-script.XXXXXX")"
  mkdir -p "$TMP/bin" "$TMP/repo" "$TMP/maven-logs"
  write_fake_date
  write_fake_git
  write_fake_maven

  run_benchmark "$TMP/run-a.out" base-a candidate-a &
  local pid_a="$!"
  run_benchmark "$TMP/run-b.out" base-b candidate-b &
  local pid_b="$!"

  wait "$pid_a" || fail "first benchmark invocation failed"
  wait "$pid_b" || fail "second benchmark invocation failed"

  local comparison_a
  comparison_a="$(comparison_dir_from_output "$TMP/run-a.out")"
  local comparison_b
  comparison_b="$(comparison_dir_from_output "$TMP/run-b.out")"
  [[ -n "$comparison_a" ]] || fail "first invocation did not report comparison output"
  [[ -n "$comparison_b" ]] || fail "second invocation did not report comparison output"
  [[ "$comparison_a" != "$comparison_b" ]] || fail "same-second invocations must not share default output directories"

  local default_prefix="$TMP/repo/.agents/benchmarks/performance/comparisons/20260722T193000Z-"
  expect_path_prefix "$comparison_a" "$default_prefix" "first default output should use timestamp and unique suffix"
  expect_path_prefix "$comparison_b" "$default_prefix" "second default output should use timestamp and unique suffix"

  local output_a="${comparison_a%/comparison}"
  local output_b="${comparison_b%/comparison}"
  [[ -d "$output_a" ]] || fail "first output directory should exist"
  [[ -d "$output_b" ]] || fail "second output directory should exist"
  [[ -z "$(find "$output_a/worktrees" -mindepth 1 -maxdepth 1 -type d -print -quit)" ]] \
    || fail "first invocation should clean temporary worktree roots"
  [[ -z "$(find "$output_b/worktrees" -mindepth 1 -maxdepth 1 -type d -print -quit)" ]] \
    || fail "second invocation should clean temporary worktree roots"

  local repos_file="$TMP/maven-repos.txt"
  awk -F '\t' '{ print $3 }' "$TMP"/maven-logs/*.log | sort -u > "$repos_file"
  local repo_count
  repo_count="$(wc -l < "$repos_file" | tr -d ' ')"
  [[ "$repo_count" == "2" ]] || fail "expected one Maven repository per invocation, got $repo_count"

  while IFS= read -r local_repo; do
    expect_path_prefix "$local_repo" "$TMP/repo/.agents/benchmarks/performance/comparisons/20260722T193000Z-" \
      "Maven repository should live under an invocation output directory"
    [[ "$local_repo" == */worktrees/run-*/maven-repository ]] \
      || fail "Maven repository should be invocation-scoped under the temporary worktree root: $local_repo"
    [[ "$local_repo" != "$HOME/.m2/"* ]] || fail "Maven repository must not use the shared ~/.m2 repository"
  done < "$repos_file"

  rm -rf "$TMP"
  pass "test_concurrent_default_invocations_are_isolated"
}

test_concurrent_default_invocations_are_isolated
