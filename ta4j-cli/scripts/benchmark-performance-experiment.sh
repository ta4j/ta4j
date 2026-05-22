#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  ta4j-cli/scripts/benchmark-performance-experiment.sh [base-ref] [candidate-ref] [output-dir] [-- runner args...]

Defaults:
  base-ref:      HEAD^
  candidate-ref: HEAD
  output-dir:    .agents/benchmarks/performance/comparisons/<timestamp>

The script runs ta4j-cli performance run in
temporary worktrees for both refs, then compares performance.json artifacts with
ta4j-cli performance compare.

Example:
  ta4j-cli/scripts/benchmark-performance-experiment.sh HEAD^ HEAD -- \
    --experiment kalman-filter \
    --bar-counts 1000,5000,10000 \
    --scenarios sequential,endOnly,endThenReverse,sparseAfterHighWatermark \
    --repetitions 5
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

repo_root="$(git rev-parse --show-toplevel)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
base_ref="HEAD^"
candidate_ref="HEAD"
output_dir="$repo_root/.agents/benchmarks/performance/comparisons/$timestamp"

if [[ "${1:-}" != "" && "${1:-}" != "--" ]]; then
  base_ref="$1"
  shift
fi
if [[ "${1:-}" != "" && "${1:-}" != "--" ]]; then
  candidate_ref="$1"
  shift
fi
if [[ "${1:-}" != "" && "${1:-}" != "--" ]]; then
  output_dir="$1"
  shift
fi
if [[ "$output_dir" != /* ]]; then
  output_dir="$repo_root/$output_dir"
fi
if [[ "${1:-}" == "--" ]]; then
  shift
fi

default_runner_args=(
  --experiment kalman-filter
  --bar-counts "1000,5000,10000"
  --scenarios "sequential,endOnly,endThenReverse,sparseAfterHighWatermark"
  --repetitions 5
)

if [[ "$#" -gt 0 ]]; then
  runner_args=("$@")
else
  runner_args=("${default_runner_args[@]}")
fi

worktree_parent="$output_dir/worktrees"
mkdir -p "$output_dir" "$worktree_parent"
worktree_root="$(mktemp -d "$worktree_parent/run-XXXXXX")"
base_worktree="$worktree_root/base"
candidate_worktree="$worktree_root/candidate"
base_output="$output_dir/base"
candidate_output="$output_dir/candidate"
comparison_output="$output_dir/comparison"

cleanup() {
  git -C "$repo_root" worktree remove --force "$base_worktree" >/dev/null 2>&1 || true
  git -C "$repo_root" worktree remove --force "$candidate_worktree" >/dev/null 2>&1 || true
  rm -rf "$worktree_root"
}
trap cleanup EXIT

git -C "$repo_root" worktree add --detach "$base_worktree" "$base_ref" >/dev/null
git -C "$repo_root" worktree add --detach "$candidate_worktree" "$candidate_ref" >/dev/null

quote_exec_arg() {
  local value="$1"
  if [[ "$value" =~ ^[A-Za-z0-9_./:=,+%-]+$ ]]; then
    printf '%s' "$value"
    return
  fi
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '"%s"' "$value"
}

join_exec_args() {
  local exec_args=""
  local arg
  for arg in "$@"; do
    local quoted_arg
    quoted_arg="$(quote_exec_arg "$arg")"
    if [[ -n "$exec_args" ]]; then
      exec_args+=" "
    fi
    exec_args+="$quoted_arg"
  done
  printf '%s' "$exec_args"
}

run_ref() {
  local worktree="$1"
  local run_output="$2"
  mkdir -p "$run_output"
  local exec_args
  exec_args="$(join_exec_args "${runner_args[@]}" --output-dir "$run_output")"
  (
    cd "$worktree"
    mvn -q -pl ta4j-cli -am install -DskipTests
    mvn -q -pl ta4j-cli exec:java \
      -Dexec.mainClass=org.ta4j.cli.Ta4jCli \
      -Dexec.args="performance run $exec_args"
  )
}

run_ref "$base_worktree" "$base_output"
run_ref "$candidate_worktree" "$candidate_output"

comparison_args="$(join_exec_args --base-dir "$base_output" --candidate-dir "$candidate_output" --output-dir "$comparison_output")"
(
  cd "$candidate_worktree"
  mvn -q -pl ta4j-cli exec:java \
    -Dexec.mainClass=org.ta4j.cli.Ta4jCli \
    -Dexec.args="performance compare $comparison_args"
)

printf 'Performance comparison written to %s\n' "$comparison_output"
