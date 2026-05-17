#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/benchmark-backtest-throughput.sh [base-ref] [candidate-ref] [output-dir] [-- harness args...]

Defaults:
  base-ref:      HEAD^
  candidate-ref: HEAD
  output-dir:    .agents/benchmarks/backtest-throughput/<timestamp>

The script runs BacktestPerformanceTuningHarness throughput-control mode in
temporary worktrees for both refs, then compares matrix_performance.json
cells/min and hypotheses/min.
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
output_dir="$repo_root/.agents/benchmarks/backtest-throughput/$timestamp"
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
if [[ "${1:-}" == "--" ]]; then
  shift
fi

default_harness_args=(
  --throughputControl
  --matrixStrategyCounts 250,500,1000
  --matrixBarCounts 500,1000
  --matrixMaxBarCountHints 0
  --executionMode topK
  --topK 10
  --parallelism 1
)

if [[ "$#" -gt 0 ]]; then
  harness_args=(--throughputControl "$@")
else
  harness_args=("${default_harness_args[@]}")
fi

worktree_parent="$output_dir/worktrees"
mkdir -p "$output_dir" "$worktree_parent"
worktree_root="$(mktemp -d "$worktree_parent/run-XXXXXX")"
base_worktree="$worktree_root/base"
candidate_worktree="$worktree_root/candidate"
base_output="$output_dir/base"
candidate_output="$output_dir/candidate"
comparison_json="$output_dir/comparison.json"
comparison_md="$output_dir/summary.md"

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

run_ref() {
  local worktree="$1"
  local run_output="$2"
  mkdir -p "$run_output"
  local exec_args=""
  local arg
  for arg in "${harness_args[@]}" --throughputOutputDir "$run_output"; do
    local quoted_arg
    quoted_arg="$(quote_exec_arg "$arg")"
    if [[ -n "$exec_args" ]]; then
      exec_args+=" "
    fi
    exec_args+="$quoted_arg"
  done
  (
    cd "$worktree"
    mvn -q -pl ta4j-examples -am compile
    mvn -q -pl ta4j-examples exec:java \
      -Dexec.mainClass=ta4jexamples.backtesting.BacktestPerformanceTuningHarness \
      -Dexec.args="$exec_args"
  )
}

run_ref "$base_worktree" "$base_output"
run_ref "$candidate_worktree" "$candidate_output"

python3 - "$base_output/matrix_performance.json" "$candidate_output/matrix_performance.json" \
  "$comparison_json" "$comparison_md" "$base_ref" "$candidate_ref" <<'PY'
import json
import sys
from pathlib import Path

base_path = Path(sys.argv[1])
candidate_path = Path(sys.argv[2])
comparison_path = Path(sys.argv[3])
summary_path = Path(sys.argv[4])
base_ref = sys.argv[5]
candidate_ref = sys.argv[6]

base = json.loads(base_path.read_text())
candidate = json.loads(candidate_path.read_text())

required_metrics = (
    "cellsPerMinute",
    "hypothesesPerMinute",
    "totalWallTimeMs",
    "strategyBuildWallTimeMs",
    "backtestRuntimeMs",
)
required_meta = (
    "specFingerprint",
    "dataset",
    "cellCount",
    "hypothesisCount",
    "parallelism",
    "resolvedParallelism",
    "executionMode",
    "topK",
    "progress",
    "gcBetweenRuns",
)

for key in (*required_metrics, *required_meta):
    if key not in base or key not in candidate:
        raise SystemExit(f"Missing required key '{key}' in matrix_performance.json")
if "host" not in base or "host" not in candidate:
    raise SystemExit("Missing required key 'host' in matrix_performance.json")
if "hostId" not in base["host"] or "hostId" not in candidate["host"]:
    raise SystemExit("Missing required key 'host.hostId' in matrix_performance.json")

def pct(before, after):
    if before == 0:
        return None
    return (after - before) * 100.0 / before

metrics = {}
for name in required_metrics:
    before = float(base[name])
    after = float(candidate[name])
    metrics[name] = {
        "base": before,
        "candidate": after,
        "delta": after - before,
        "deltaPct": pct(before, after),
    }

comparison = {
    "baseRef": base_ref,
    "candidateRef": candidate_ref,
    "basePath": str(base_path),
    "candidatePath": str(candidate_path),
    "specFingerprintMatch": base.get("specFingerprint") == candidate.get("specFingerprint"),
    "datasetMatch": base.get("dataset") == candidate.get("dataset"),
    "cellCountMatch": base.get("cellCount") == candidate.get("cellCount"),
    "hypothesisCountMatch": base.get("hypothesisCount") == candidate.get("hypothesisCount"),
    "hostIdMatch": base["host"]["hostId"] == candidate["host"]["hostId"],
    "metrics": metrics,
}
comparison_path.write_text(json.dumps(comparison, indent=2, sort_keys=True) + "\n")

if not all((
    comparison["specFingerprintMatch"],
    comparison["datasetMatch"],
    comparison["cellCountMatch"],
    comparison["hypothesisCountMatch"],
    comparison["hostIdMatch"],
)):
    raise SystemExit("Invalid comparison: host/dataset/spec/hypothesis/cell counts differ between refs.")

def fmt(value):
    return "n/a" if value is None else f"{value:.2f}"

lines = [
    f"# Backtest Throughput Comparison",
    "",
    f"- Base: `{base_ref}`",
    f"- Candidate: `{candidate_ref}`",
    f"- Dataset match: `{comparison['datasetMatch']}`",
    f"- Spec fingerprint match: `{comparison['specFingerprintMatch']}`",
    f"- Cell count match: `{comparison['cellCountMatch']}`",
    f"- Hypothesis count match: `{comparison['hypothesisCountMatch']}`",
    f"- Host ID match: `{comparison['hostIdMatch']}`",
    "",
    "| Metric | Base | Candidate | Delta % |",
    "| --- | ---: | ---: | ---: |",
]
for name in ("cellsPerMinute", "hypothesesPerMinute", "totalWallTimeMs", "strategyBuildWallTimeMs", "backtestRuntimeMs"):
    metric = metrics[name]
    lines.append(f"| {name} | {metric['base']:.2f} | {metric['candidate']:.2f} | {fmt(metric['deltaPct'])} |")
summary_path.write_text("\n".join(lines) + "\n")
print(f"comparison: {comparison_path}")
print(f"summary: {summary_path}")
PY

cat "$comparison_md"
