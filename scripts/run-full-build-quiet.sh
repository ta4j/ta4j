#!/usr/bin/env bash
set -euo pipefail

UNAME_OUT="$(uname -s 2>/dev/null || echo unknown)"
RUNNING_IN_MINGW="false"
case "$UNAME_OUT" in
    CYGWIN*|MSYS*|MINGW*) RUNNING_IN_MINGW="true" ;;
esac

to_host_path() {
    local posix_path="$1"
    if [[ "$RUNNING_IN_MINGW" == "true" ]]; then
        if command -v cygpath >/dev/null 2>&1; then
            cygpath -w "$posix_path"
            return
        fi
        if [[ "$posix_path" =~ ^/([a-zA-Z])/(.*)$ ]]; then
            local drive="${BASH_REMATCH[1]}"
            local remainder="${BASH_REMATCH[2]}"
            drive="$(printf '%s' "$drive" | tr '[:lower:]' '[:upper:]')"
            if [[ -n "$remainder" ]]; then
                printf '%s:/%s' "$drive" "$remainder"
            else
                printf '%s:/' "$drive"
            fi
            return
        fi
    fi
    printf '%s' "$posix_path"
}

python_supports_py3() {
    local -a candidate=("$@")
    if "${candidate[@]}" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 6) else 1)' >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

PYTHON_CMD=()
if command -v python3 >/dev/null 2>&1 && python_supports_py3 python3; then
    PYTHON_CMD=(python3)
elif command -v python >/dev/null 2>&1 && python_supports_py3 python; then
    PYTHON_CMD=(python)
elif command -v py >/dev/null 2>&1 && python_supports_py3 py -3; then
    PYTHON_CMD=(py -3)
else
    echo "Error: Python 3.6 or newer is required to run this script." >&2
    exit 1
fi

TMP_FILES=()
cleanup() {
    local file
    for file in "${TMP_FILES[@]}"; do
        if [[ -n "${file:-}" && -f "$file" ]]; then
            rm -f "$file"
        fi
    done
}
trap cleanup EXIT

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

LOG_DIR="$REPO_ROOT/.agents/logs"
mkdir -p "$LOG_DIR"

# Clean up old full build logs, keeping only the last 10
cleanup_old_logs() {
    local log_dir="$1"
    local keep_count=10
    if [[ ! -d "$log_dir" ]]; then
        return 0
    fi
    
    # Find all full-build-*.log files and sort by filename (which contains timestamp)
    # Filenames are in format: full-build-YYYYMMDD-HHMMSS.log
    # Sorting alphabetically gives chronological order (newest last)
    local files
    files=$(find "$log_dir" -maxdepth 1 -type f -name "full-build-*.log" 2>/dev/null | sort || true)
    if [[ -z "$files" ]]; then
        return 0
    fi

    # Delete everything except the newest keep_count files; use POSIX-safe tail/awk
    local file_count
    file_count=$(printf '%s\n' "$files" | grep -c . || true)
    local delete_count=$((file_count - keep_count))
    if ((delete_count > 0)); then
        local files_to_delete
        files_to_delete=$(printf '%s\n' "$files" | head -n "$delete_count")
        while IFS= read -r file; do
            if [[ -n "$file" && -f "$file" ]]; then
                rm -f "$file"
            fi
        done <<< "$files_to_delete"
    fi
}

cleanup_old_logs "$LOG_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/full-build-${TIMESTAMP}.log"
LOG_FILE_FOR_PYTHON="$(to_host_path "$LOG_FILE")"
LOG_FILE_FOR_DISPLAY="$LOG_FILE"
if [[ "$RUNNING_IN_MINGW" == "true" ]]; then
    LOG_FILE_FOR_DISPLAY="$LOG_FILE_FOR_PYTHON"
fi

BUILD_TIMEOUT_SECONDS="${QUIET_BUILD_TIMEOUT_SECONDS:-180}"
if ! [[ "$BUILD_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]]; then
    BUILD_TIMEOUT_SECONDS=180
fi

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
echo "Full log: $LOG_FILE_FOR_DISPLAY"
echo

FILTER_SCRIPT="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-filter.XXXXXX")"
TMP_FILES+=("$FILTER_SCRIPT")
cat >"$FILTER_SCRIPT" <<'PY'
import os
import sys
import threading
import time
from pathlib import Path


def format_elapsed(seconds):
    seconds = int(seconds)
    minutes, secs = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours}h{minutes:02d}m{secs:02d}s"
    if minutes:
        return f"{minutes}m{secs:02d}s"
    return f"{secs}s"


def main(log_path):
    keywords = (
        "[ERROR]",
        "[WARNING]",
        "BUILD SUCCESS",
        "BUILD FAILURE",
        "Failed to execute goal",
        "Reactor Summary",
    )
    try:
        heartbeat_interval = int(os.environ.get("QUIET_BUILD_HEARTBEAT_SECONDS", "60"))
    except ValueError:
        heartbeat_interval = 60
    if heartbeat_interval < 1:
        heartbeat_interval = 60

    printed_once = set()
    suppressed_counts = {}
    start_time = time.monotonic()
    last_visible = {"value": start_time}
    lock = threading.Lock()
    stop_event = threading.Event()

    def update_last_visible():
        with lock:
            last_visible["value"] = time.monotonic()

    def heartbeat_worker():
        while not stop_event.wait(heartbeat_interval):
            now = time.monotonic()
            with lock:
                last_time = last_visible["value"]
            if now - last_time >= heartbeat_interval:
                message = (
                    f"[quiet-build] still running... "
                    f"({format_elapsed(now - start_time)})\n"
                )
                try:
                    sys.stdout.write(message)
                except UnicodeEncodeError:
                    # Write bytes directly, replacing problematic characters with '?' for Windows console compatibility
                    stdout_encoding = sys.stdout.encoding or "utf-8"
                    safe_bytes = message.encode(stdout_encoding, errors="replace")
                    sys.stdout.buffer.write(safe_bytes)
                sys.stdout.flush()
                update_last_visible()

    heartbeat_thread = threading.Thread(target=heartbeat_worker, daemon=True)
    heartbeat_thread.start()

    with Path(log_path).open("w", encoding="utf-8") as log_file:
        for raw_line in sys.stdin.buffer:
            text = raw_line.decode("utf-8", errors="replace")
            log_file.write(text)
            if any(keyword in text for keyword in keywords):
                if "Tests run:" in text and "Time elapsed" in text:
                    continue
                if text in printed_once:
                    suppressed_counts[text] = suppressed_counts.get(text, 0) + 1
                else:
                    printed_once.add(text)
                    # Handle encoding errors when writing to stdout (Windows cp1252 can't encode all Unicode)
                    try:
                        sys.stdout.write(text)
                    except UnicodeEncodeError:
                        # Write bytes directly, replacing problematic characters with '?' for Windows console compatibility
                        stdout_encoding = sys.stdout.encoding or "utf-8"
                        safe_bytes = text.encode(stdout_encoding, errors="replace")
                        sys.stdout.buffer.write(safe_bytes)
                    sys.stdout.flush()
                    update_last_visible()
        log_file.flush()

    stop_event.set()
    heartbeat_thread.join()

    if suppressed_counts:
        sys.stdout.write("\n[quiet-build] Suppressed duplicate warnings:\n")
        for message, count in suppressed_counts.items():
            try:
                sys.stdout.write(f"[quiet-build]   ({count} more) {message.strip()}\n")
            except UnicodeEncodeError:
                # Write bytes directly, replacing problematic characters with '?' for Windows console compatibility
                stdout_encoding = sys.stdout.encoding or "utf-8"
                output = f"[quiet-build]   ({count} more) {message.strip()}\n"
                safe_bytes = output.encode(stdout_encoding, errors="replace")
                sys.stdout.buffer.write(safe_bytes)
        sys.stdout.flush()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.stderr.write("Usage: quiet_build_filter.py <log_path>\n")
        sys.exit(1)
    main(sys.argv[1])
PY

TIMEOUT_WRAPPER=()
if ((BUILD_TIMEOUT_SECONDS > 0)); then
    TIMEOUT_SCRIPT="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-timeout.XXXXXX")"
    TMP_FILES+=("$TIMEOUT_SCRIPT")
    cat >"$TIMEOUT_SCRIPT" <<'PY'
import sys
import subprocess
import threading
import time


def forward_output(stream):
    for chunk in iter(stream.readline, b""):
        sys.stdout.buffer.write(chunk)
        sys.stdout.buffer.flush()


def main():
    if len(sys.argv) < 3:
        sys.stderr.write("Usage: timeout_runner.py <timeout_seconds> <cmd...>\n")
        return 2
    try:
        timeout_seconds = int(sys.argv[1])
    except ValueError:
        timeout_seconds = 0
    command = sys.argv[2:]
    if timeout_seconds < 0:
        timeout_seconds = 0

    proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if proc.stdout is None:
        return 1
    reader = threading.Thread(target=forward_output, args=(proc.stdout,), daemon=True)
    reader.start()
    try:
        if timeout_seconds:
            proc.wait(timeout=timeout_seconds)
        else:
            proc.wait()
    except subprocess.TimeoutExpired:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
        sys.stdout.write(
            f"[quiet-build] Timeout after {timeout_seconds}s. Maven was terminated.\n"
        )
        sys.stdout.flush()
        return 124
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
PY
    TIMEOUT_WRAPPER=("${PYTHON_CMD[@]}" -u "$TIMEOUT_SCRIPT" "$BUILD_TIMEOUT_SECONDS" "${MVN_CMD[@]}")
else
    TIMEOUT_WRAPPER=("${MVN_CMD[@]}")
fi

set +o pipefail
set +e
"${TIMEOUT_WRAPPER[@]}" 2>&1 | "${PYTHON_CMD[@]}" -u "$FILTER_SCRIPT" "$LOG_FILE_FOR_PYTHON"
statuses=("${PIPESTATUS[@]}")
set -e
set -o pipefail

mvn_status=${statuses[0]:-0}
filter_status=${statuses[1]:-0}

if ((mvn_status != 0 || filter_status != 0)); then
    echo
    if ((mvn_status == 124)); then
        echo "Maven build timed out after ${BUILD_TIMEOUT_SECONDS}s. Inspect the full log at: $LOG_FILE_FOR_DISPLAY"
        exit 1
    fi
    echo "Maven build failed (mvn=$mvn_status, filter=$filter_status). Inspect the full log at: $LOG_FILE_FOR_DISPLAY"
    exit 1
fi

summary=$("${PYTHON_CMD[@]}" - "$LOG_FILE_FOR_PYTHON" <<'PY'
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
    echo "Tests run summary not found in log; see $LOG_FILE_FOR_DISPLAY"
fi
echo "Full build log saved to: $LOG_FILE_FOR_DISPLAY"
