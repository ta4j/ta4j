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

TMP_FILES=()
cleanup() {
    local file
    for file in "${TMP_FILES[@]:-}"; do
        if [[ -n "${file:-}" && -e "$file" ]]; then
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
LOG_FILE_FOR_DISPLAY="$LOG_FILE"
if [[ "$RUNNING_IN_MINGW" == "true" ]]; then
    LOG_FILE_FOR_DISPLAY="$(to_host_path "$LOG_FILE")"
fi

BUILD_TIMEOUT_SECONDS="${QUIET_BUILD_TIMEOUT_SECONDS:-180}"
if ! [[ "$BUILD_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]]; then
    BUILD_TIMEOUT_SECONDS=180
fi

HEARTBEAT_INTERVAL_SECONDS="${QUIET_BUILD_HEARTBEAT_SECONDS:-60}"
if ! [[ "$HEARTBEAT_INTERVAL_SECONDS" =~ ^[0-9]+$ ]] || ((HEARTBEAT_INTERVAL_SECONDS < 1)); then
    HEARTBEAT_INTERVAL_SECONDS=60
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
MAVEN_CMD_PREFIX=(mvn)
if [[ -x "./mvnw" ]]; then
    MAVEN_CMD_PREFIX=("./mvnw")
    echo "Using Maven Wrapper: ./mvnw"
elif [[ -f "./mvnw" ]]; then
    MAVEN_CMD_PREFIX=(sh "./mvnw")
    echo "Using Maven Wrapper via sh: ./mvnw"
else
    echo "Using system Maven from PATH: mvn"
fi

MVN_CMD=("${MAVEN_CMD_PREFIX[@]}" "${MAVEN_FLAGS[@]}")
if ((${#EXTRA_MAVEN_ARGS[@]} > 0)); then
    MVN_CMD+=("${EXTRA_MAVEN_ARGS[@]}")
fi
MVN_CMD+=("${GOALS[@]}")

echo "Running ta4j full build quietly..."
echo "Full log: $LOG_FILE_FOR_DISPLAY"
echo

format_elapsed() {
    local total_seconds="$1"
    local hours=$((total_seconds / 3600))
    local minutes=$(((total_seconds % 3600) / 60))
    local seconds=$((total_seconds % 60))
    if ((hours > 0)); then
        printf '%dh%02dm%02ds' "$hours" "$minutes" "$seconds"
    elif ((minutes > 0)); then
        printf '%dm%02ds' "$minutes" "$seconds"
    else
        printf '%ds' "$seconds"
    fi
}

should_print_line() {
    local text="$1"
    case "$text" in
        *"[ERROR]"*|*"[WARNING]"*|*"BUILD SUCCESS"*|*"BUILD FAILURE"*|*"Failed to execute goal"*|*"Reactor Summary"*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

trim_whitespace() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

run_with_timeout() {
    local timeout_marker_file="$1"
    local timeout_seconds="$2"
    shift 2
    : >"$timeout_marker_file"

    "$@" &
    local command_pid=$!

    (
        local elapsed=0
        while ((elapsed < timeout_seconds)); do
            sleep 1
            if ! kill -0 "$command_pid" >/dev/null 2>&1; then
                exit 0
            fi
            elapsed=$((elapsed + 1))
        done
        if kill -0 "$command_pid" >/dev/null 2>&1; then
            echo "timeout" >"$timeout_marker_file"
            kill -TERM "$command_pid" >/dev/null 2>&1 || true
            sleep 5
            if kill -0 "$command_pid" >/dev/null 2>&1; then
                kill -KILL "$command_pid" >/dev/null 2>&1 || true
            fi
        fi
    ) &
    local timeout_watcher_pid=$!

    set +e
    wait "$command_pid"
    local command_status=$?
    set -e

    wait "$timeout_watcher_pid" >/dev/null 2>&1 || true

    if [[ -s "$timeout_marker_file" ]]; then
        return 124
    fi
    return "$command_status"
}

update_last_visible() {
    date +%s >"$LAST_VISIBLE_FILE"
}

heartbeat_worker() {
    local build_pid="$1"
    local start_time="$2"
    local heartbeat_interval="$3"
    while kill -0 "$build_pid" >/dev/null 2>&1; do
        sleep "$heartbeat_interval"
        if ! kill -0 "$build_pid" >/dev/null 2>&1; then
            break
        fi
        local now
        now="$(date +%s)"
        local last_visible="$start_time"
        if [[ -f "$LAST_VISIBLE_FILE" ]]; then
            last_visible="$(cat "$LAST_VISIBLE_FILE" 2>/dev/null || echo "$start_time")"
        fi
        if ! [[ "$last_visible" =~ ^[0-9]+$ ]]; then
            last_visible="$start_time"
        fi
        if ((now - last_visible >= heartbeat_interval)); then
            echo "[quiet-build] still running... ($(format_elapsed $((now - start_time))))"
            update_last_visible
        fi
    done
}

run_maven_build() {
    if ((BUILD_TIMEOUT_SECONDS > 0)); then
        run_with_timeout "$TIMEOUT_MARKER_FILE" "$BUILD_TIMEOUT_SECONDS" "${MVN_CMD[@]}"
        return $?
    fi
    "${MVN_CMD[@]}"
}

emit_suppressed_duplicates() {
    if [[ ! -s "$SUPPRESSED_DUPLICATES_FILE" ]]; then
        return 0
    fi
    echo
    echo "[quiet-build] Suppressed duplicate warnings:"
    local line
    while IFS= read -r line || [[ -n "$line" ]]; do
        if grep -Fqx -- "$line" "$SUPPRESSED_REPORTED_FILE"; then
            continue
        fi
        local count
        count=$(grep -Fxc -- "$line" "$SUPPRESSED_DUPLICATES_FILE" || true)
        echo "[quiet-build]   (${count} more) $(trim_whitespace "$line")"
        printf '%s\n' "$line" >>"$SUPPRESSED_REPORTED_FILE"
    done <"$SUPPRESSED_DUPLICATES_FILE"
}

extract_test_summary() {
    local total_run=0
    local total_failures=0
    local total_errors=0
    local total_skipped=0
    local has_aggregated="false"
    local fallback_summary=""
    local line
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" =~ ^\[(INFO|WARNING)\][[:space:]]+Tests\ run:\ ([0-9]+),\ Failures:\ ([0-9]+),\ Errors:\ ([0-9]+),\ Skipped:\ ([0-9]+)[[:space:]]*$ ]]; then
            has_aggregated="true"
            total_run=$((total_run + ${BASH_REMATCH[2]}))
            total_failures=$((total_failures + ${BASH_REMATCH[3]}))
            total_errors=$((total_errors + ${BASH_REMATCH[4]}))
            total_skipped=$((total_skipped + ${BASH_REMATCH[5]}))
        elif [[ "$line" =~ Tests\ run:\ ([0-9]+),\ Failures:\ ([0-9]+),\ Errors:\ ([0-9]+),\ Skipped:\ ([0-9]+) ]]; then
            fallback_summary="Tests run: ${BASH_REMATCH[1]}, Failures: ${BASH_REMATCH[2]}, Errors: ${BASH_REMATCH[3]}, Skipped: ${BASH_REMATCH[4]}"
        fi
    done <"$LOG_FILE"

    if [[ "$has_aggregated" == "true" ]]; then
        printf 'Tests run: %d, Failures: %d, Errors: %d, Skipped: %d' "$total_run" "$total_failures" "$total_errors" "$total_skipped"
        return 0
    fi
    if [[ -n "$fallback_summary" ]]; then
        printf '%s' "$fallback_summary"
    fi
}

LAST_VISIBLE_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-last-visible.XXXXXX")"
TMP_FILES+=("$LAST_VISIBLE_FILE")
BUILD_START_TIME="$(date +%s)"
echo "$BUILD_START_TIME" >"$LAST_VISIBLE_FILE"

SEEN_VISIBLE_LINES_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-seen-visible.XXXXXX")"
TMP_FILES+=("$SEEN_VISIBLE_LINES_FILE")

SUPPRESSED_DUPLICATES_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-suppressed-duplicates.XXXXXX")"
TMP_FILES+=("$SUPPRESSED_DUPLICATES_FILE")

SUPPRESSED_REPORTED_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-suppressed-reported.XXXXXX")"
TMP_FILES+=("$SUPPRESSED_REPORTED_FILE")

OUTPUT_FIFO="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-output.XXXXXX")"
TMP_FILES+=("$OUTPUT_FIFO")
rm -f "$OUTPUT_FIFO"
mkfifo "$OUTPUT_FIFO"

TIMEOUT_MARKER_FILE=""
if ((BUILD_TIMEOUT_SECONDS > 0)); then
    TIMEOUT_MARKER_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-timeout-marker.XXXXXX")"
    TMP_FILES+=("$TIMEOUT_MARKER_FILE")
fi

: >"$LOG_FILE"
: >"$SEEN_VISIBLE_LINES_FILE"
: >"$SUPPRESSED_DUPLICATES_FILE"
: >"$SUPPRESSED_REPORTED_FILE"

set +o pipefail
set +e
run_maven_build >"$OUTPUT_FIFO" 2>&1 &
build_runner_pid=$!

heartbeat_worker "$build_runner_pid" "$BUILD_START_TIME" "$HEARTBEAT_INTERVAL_SECONDS" &
heartbeat_pid=$!

while IFS= read -r line || [[ -n "$line" ]]; do
    printf '%s\n' "$line" >>"$LOG_FILE"
    if ! should_print_line "$line"; then
        continue
    fi
    if [[ "$line" == *"Tests run:"* && "$line" == *"Time elapsed"* ]]; then
        continue
    fi
    if grep -Fqx -- "$line" "$SEEN_VISIBLE_LINES_FILE"; then
        printf '%s\n' "$line" >>"$SUPPRESSED_DUPLICATES_FILE"
    else
        printf '%s\n' "$line" >>"$SEEN_VISIBLE_LINES_FILE"
        printf '%s\n' "$line"
        update_last_visible
    fi
done <"$OUTPUT_FIFO"
filter_status=$?

wait "$build_runner_pid"
mvn_status=$?
set -e
set -o pipefail

kill "$heartbeat_pid" >/dev/null 2>&1 || true
wait "$heartbeat_pid" >/dev/null 2>&1 || true

emit_suppressed_duplicates

if ((mvn_status != 0 || filter_status != 0)); then
    echo
    if ((mvn_status == 124)); then
        echo "Maven build timed out after ${BUILD_TIMEOUT_SECONDS}s. Inspect the full log at: $LOG_FILE_FOR_DISPLAY"
        exit 1
    fi
    echo "Maven build failed (mvn=$mvn_status, filter=$filter_status). Inspect the full log at: $LOG_FILE_FOR_DISPLAY"
    exit 1
fi

summary="$(extract_test_summary)"

echo
if [[ -n "$summary" ]]; then
    echo "$summary"
else
    echo "Tests run summary not found in log; see $LOG_FILE_FOR_DISPLAY"
fi
echo "Full build log saved to: $LOG_FILE_FOR_DISPLAY"
