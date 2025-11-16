#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

LOG_DIR="$REPO_ROOT/.agents/logs"
mkdir -p "$LOG_DIR"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/full-build-${TIMESTAMP}.log"

MAVEN_FLAGS=(
    -B
    -ntp
    -Dstyle.color=never
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=WARN
)

EXTRA_MAVEN_ARGS=()
if (($# > 0)); then
    EXTRA_MAVEN_ARGS=("$@")
fi

if ((${#EXTRA_MAVEN_ARGS[@]} > 0)); then
    printf 'Forwarding extra Maven args:'
    for arg in "${EXTRA_MAVEN_ARGS[@]}"; do
        printf ' %q' "$arg"
    done
    printf '\n'
fi

GOALS=(clean license:format formatter:format test install)
MVN_CMD=(mvn "${MAVEN_FLAGS[@]}")
if ((${#EXTRA_MAVEN_ARGS[@]} > 0)); then
    MVN_CMD+=("${EXTRA_MAVEN_ARGS[@]}")
fi
MVN_CMD+=("${GOALS[@]}")

echo "Running ta4j full build quietly..."
echo "Full log: $LOG_FILE"
echo

set +o pipefail
set +e
"${MVN_CMD[@]}" 2>&1 | python3 -u -c '
import sys
from pathlib import Path

log_path = Path(sys.argv[1])
KEYWORDS = (
    "[ERROR]",
    "[WARNING]",
    "BUILD SUCCESS",
    "BUILD FAILURE",
    "Failed to execute goal",
    "Reactor Summary",
)

printed_once = set()
suppressed_counts = {}

with log_path.open("w", encoding="utf-8") as log_file:
    for raw_line in sys.stdin.buffer:
        text = raw_line.decode("utf-8", errors="replace")
        log_file.write(text)
        if any(keyword in text for keyword in KEYWORDS):
            if "Tests run:" in text and "Time elapsed" in text:
                continue
            if text in printed_once:
                suppressed_counts[text] = suppressed_counts.get(text, 0) + 1
            else:
                printed_once.add(text)
                sys.stdout.write(text)
                sys.stdout.flush()
    log_file.flush()

if suppressed_counts:
    sys.stdout.write("\n[quiet-build] Suppressed duplicate warnings:\n")
    for message, count in suppressed_counts.items():
        sys.stdout.write(f"[quiet-build]   ({count} more) {message.strip()}\n")
    sys.stdout.flush()
' "$LOG_FILE"
statuses=("${PIPESTATUS[@]}")
set -e
set -o pipefail

mvn_status=${statuses[0]:-0}
filter_status=${statuses[1]:-0}

if ((mvn_status != 0 || filter_status != 0)); then
    echo
    echo "Maven build failed (mvn=$mvn_status, filter=$filter_status). Inspect the full log at: $LOG_FILE"
    exit 1
fi

summary=$(python3 - "$LOG_FILE" <<'PY'
import re
import sys

log_path = sys.argv[1]
aggregated_pattern = re.compile(
    r"^\[(?:INFO|WARNING)\]\s+Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$"
)
fallback_pattern = re.compile(
    r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)"
)

totals = [0, 0, 0, 0]
had_aggregated = False
fallback_line = None

with open(log_path, "r", errors="ignore") as handle:
    for line in handle:
        match = aggregated_pattern.match(line)
        if match:
            had_aggregated = True
            for index in range(4):
                totals[index] += int(match.group(index + 1))
        else:
            fallback_match = fallback_pattern.search(line)
            if fallback_match:
                fallback_line = fallback_match.groups()

if had_aggregated:
    print(
        f"Tests run: {totals[0]}, Failures: {totals[1]}, "
        f"Errors: {totals[2]}, Skipped: {totals[3]}"
    )
elif fallback_line:
    tests_run, failures, errors, skipped = fallback_line
    print(
        f"Tests run: {tests_run}, Failures: {failures}, "
        f"Errors: {errors}, Skipped: {skipped}"
    )
PY
)

summary="${summary//$'\n'/}"

echo
if [[ -n "$summary" ]]; then
    echo "$summary"
else
    echo "Tests run summary not found in log; see $LOG_FILE"
fi
echo "Full build log saved to: $LOG_FILE"
