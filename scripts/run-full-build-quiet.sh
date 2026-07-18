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
build_runner_pid=""
heartbeat_pid=""
cleanup() {
    if [[ -n "${build_runner_pid:-}" ]] && kill -0 "$build_runner_pid" >/dev/null 2>&1; then
        kill "$build_runner_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "${heartbeat_pid:-}" ]] && kill -0 "$heartbeat_pid" >/dev/null 2>&1; then
        kill "$heartbeat_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "${build_runner_pid:-}" ]]; then
        wait "$build_runner_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "${heartbeat_pid:-}" ]]; then
        wait "$heartbeat_pid" >/dev/null 2>&1 || true
    fi

    local file
    for file in "${TMP_FILES[@]:-}"; do
        if [[ -n "${file:-}" && -e "$file" ]]; then
            rm -f "$file"
        fi
    done
}
trap cleanup SIGINT SIGTERM EXIT

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

usage() {
    cat <<'EOF'
Usage: scripts/run-full-build-quiet.sh [--validate-only] [--preflight-only] [--goals "goal..."] [--] [maven-args...]

The default local invocation repairs license headers and formatting before it
runs the repository-owned checks and Maven verify gate. Hosted PR CI uses
--validate-only to reject those defects without modifying its checkout. Maven
output is filtered and the complete log is written to .agents/logs/full-build-*.log.
Explicit --goals invocations remain focused and skip repository preflight checks.

Examples:
  scripts/run-full-build-quiet.sh
  scripts/run-full-build-quiet.sh --validate-only
  scripts/run-full-build-quiet.sh --preflight-only
  scripts/run-full-build-quiet.sh -- -pl ta4j-core
  scripts/run-full-build-quiet.sh --goals "test jacoco:report jacoco:check" -- -pl ta4j-core -am
  scripts/run-full-build-quiet.sh --goals test -- -Dgroups=integration -Dta4j.excludedTestTags=analysis-demo
EOF
}

run_repository_preflight() {
    if [[ -d "$REPO_ROOT/.github/workflows" ]]; then
        echo "Running actionlint..."
        if command -v actionlint >/dev/null 2>&1 && actionlint -version 2>&1 | grep -Fq "1.7.12"; then
            actionlint
        elif command -v go >/dev/null 2>&1; then
            go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12
        else
            echo "actionlint 1.7.12 or Go is required for hosted CI parity." >&2
            return 1
        fi
    fi

    local -a fixtures=()
    local fixture
    for fixture in "$REPO_ROOT"/scripts/tests/test_*.sh; do
        if [[ ! -f "$fixture" ]]; then
            continue
        fi
        fixtures+=("$fixture")
    done

    if ((${#fixtures[@]} == 0)); then
        return 0
    fi

    local -a fixture_pids=()
    local -a fixture_outputs=()
    local output_file
    local previous_sigint_trap previous_sigterm_trap previous_exit_trap
    previous_sigint_trap="$(trap -p SIGINT || true)"
    previous_sigterm_trap="$(trap -p SIGTERM || true)"
    previous_exit_trap="$(trap -p EXIT || true)"

    restore_preflight_fixture_traps() {
        if [[ -n "$previous_sigint_trap" ]]; then
            eval "$previous_sigint_trap"
        else
            trap - SIGINT
        fi
        if [[ -n "$previous_sigterm_trap" ]]; then
            eval "$previous_sigterm_trap"
        else
            trap - SIGTERM
        fi
        if [[ -n "$previous_exit_trap" ]]; then
            eval "$previous_exit_trap"
        else
            trap - EXIT
        fi
    }

    cleanup_preflight_fixture_pids() {
        local pid
        for pid in "${fixture_pids[@]:-}"; do
            if kill -0 "$pid" >/dev/null 2>&1; then
                kill "$pid" >/dev/null 2>&1 || true
            fi
        done
        for pid in "${fixture_pids[@]:-}"; do
            wait "$pid" >/dev/null 2>&1 || true
        done
    }

    preflight_fixture_signal_cleanup() {
        local exit_status="$1"
        cleanup_preflight_fixture_pids
        restore_preflight_fixture_traps
        cleanup
        exit "$exit_status"
    }

    preflight_fixture_exit_cleanup() {
        cleanup_preflight_fixture_pids
        restore_preflight_fixture_traps
        cleanup
    }

    trap 'preflight_fixture_signal_cleanup 130' SIGINT
    trap 'preflight_fixture_signal_cleanup 143' SIGTERM
    trap preflight_fixture_exit_cleanup EXIT

    for fixture in "${fixtures[@]}"; do
        output_file="$(mktemp "${TMPDIR:-/tmp}/ta4j-preflight-fixture.XXXXXX")"
        TMP_FILES+=("$output_file")
        fixture_outputs+=("$output_file")
        (cd "$REPO_ROOT" && BASH_ENV=/dev/null bash "$fixture") >"$output_file" 2>&1 &
        fixture_pids+=("$!")
    done

    local status=0
    local index
    for index in "${!fixture_pids[@]}"; do
        if ! wait "${fixture_pids[$index]}"; then
            status=1
        fi
    done
    restore_preflight_fixture_traps

    for index in "${!fixtures[@]}"; do
        fixture="${fixtures[$index]}"
        echo "Running ${fixture#"$REPO_ROOT"/}..."
        cat "${fixture_outputs[$index]}"
    done

    return "$status"
}

GOALS=(clean license:format formatter:format verify)
EXTRA_MAVEN_ARGS=()
DEFAULT_GATE="true"
PREFLIGHT_ONLY="false"
VALIDATE_ONLY="false"
while (($# > 0)); do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --goals)
            shift
            if (($# == 0)) || [[ -z "$1" ]]; then
                echo "Missing value for --goals" >&2
                exit 2
            fi
            read -r -a GOALS <<< "$1"
            DEFAULT_GATE="false"
            shift
            ;;
        --goals=*)
            goal_value="${1#--goals=}"
            if [[ -z "$goal_value" ]]; then
                echo "Missing value for --goals" >&2
                exit 2
            fi
            read -r -a GOALS <<< "$goal_value"
            DEFAULT_GATE="false"
            shift
            ;;
        --preflight-only)
            PREFLIGHT_ONLY="true"
            shift
            ;;
        --validate-only)
            VALIDATE_ONLY="true"
            shift
            ;;
        --)
            shift
            EXTRA_MAVEN_ARGS+=("$@")
            break
            ;;
        *)
            EXTRA_MAVEN_ARGS+=("$1")
            shift
            ;;
    esac
done

if [[ "$VALIDATE_ONLY" == "true" && "$DEFAULT_GATE" != "true" ]]; then
    echo "--validate-only cannot be combined with --goals" >&2
    exit 2
fi

if [[ "$VALIDATE_ONLY" == "true" ]]; then
    GOALS=(clean license:check formatter:validate verify)
fi

if [[ "$DEFAULT_GATE" == "true" || "$PREFLIGHT_ONLY" == "true" ]]; then
    run_repository_preflight
    echo "Repository preflight checks passed."
fi

if [[ "$PREFLIGHT_ONLY" == "true" ]]; then
    exit 0
fi

if [[ "$DEFAULT_GATE" == "true" ]]; then
    EXTRA_MAVEN_ARGS=("-Dta4j.excludedTestTags=analysis-demo" "${EXTRA_MAVEN_ARGS[@]}")
fi

if ((${#GOALS[@]} == 0)); then
    echo "At least one Maven goal is required" >&2
    exit 2
fi

if ((${#EXTRA_MAVEN_ARGS[@]} > 0)); then
    printf 'Forwarding extra Maven args:'
    for arg in "${EXTRA_MAVEN_ARGS[@]}"; do
        printf ' %q' "$arg"
    done
    printf '\n'
fi

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

printf 'Maven goals:'
for goal in "${GOALS[@]}"; do
    printf ' %q' "$goal"
done
printf '\n'
echo "Running ta4j Maven build quietly..."
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

run_with_timeout() {
    local timeout_marker_file="$1"
    local timeout_seconds="$2"
    shift 2
    : >"$timeout_marker_file"

    "$@" &
    local command_pid=$!

    (
        local elapsed=0
        local sleep_pid=""
        trap 'if [[ -n "${sleep_pid:-}" ]]; then kill "$sleep_pid" >/dev/null 2>&1 || true; fi; exit 0' TERM INT
        while ((elapsed < timeout_seconds)); do
            sleep 1 &
            sleep_pid="$!"
            wait "$sleep_pid" >/dev/null 2>&1 || exit 0
            sleep_pid=""
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

    if kill -0 "$timeout_watcher_pid" >/dev/null 2>&1; then
        kill "$timeout_watcher_pid" >/dev/null 2>&1 || true
    fi
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
    local sleep_pid=""
    trap 'if [[ -n "${sleep_pid:-}" ]]; then kill "$sleep_pid" >/dev/null 2>&1 || true; fi; exit 0' TERM INT
    while kill -0 "$build_pid" >/dev/null 2>&1; do
        local slept=0
        while ((slept < heartbeat_interval)); do
            sleep 1 &
            sleep_pid="$!"
            wait "$sleep_pid" >/dev/null 2>&1 || exit 0
            sleep_pid=""
            if ! kill -0 "$build_pid" >/dev/null 2>&1; then
                return 0
            fi
            slept=$((slept + 1))
        done
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
            total_run=$((total_run + BASH_REMATCH[2]))
            total_failures=$((total_failures + BASH_REMATCH[3]))
            total_errors=$((total_errors + BASH_REMATCH[4]))
            total_skipped=$((total_skipped + BASH_REMATCH[5]))
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

print_reactor_summary() {
    awk '
        function clean(line) {
            sub(/^\[(INFO|WARNING|ERROR)\][[:space:]]*/, "", line)
            sub(/^[[:space:]]+/, "", line)
            sub(/[[:space:]]+$/, "", line)
            return line
        }
        /Reactor Summary/ {
            capturing = 1
            next
        }
        capturing && /BUILD (SUCCESS|FAILURE)/ {
            capturing = 0
            next
        }
        capturing {
            text = clean($0)
            if (text == "" || text ~ /^-+$/) {
                next
            }
            rows[++row_count] = text
        }
        END {
            if (row_count > 0) {
                print "Reactor summary:"
                for (i = 1; i <= row_count; i++) {
                    print "  " rows[i]
                }
            }
        }
    ' "$LOG_FILE"
}

print_warning_summary() {
    local limit="${1:-12}"
    awk -v limit="$limit" '
        function clean(line) {
            sub(/^\[(WARNING|WARN)\][[:space:]]*/, "", line)
            sub(/^[[:space:]]+/, "", line)
            sub(/[[:space:]]+$/, "", line)
            return line
        }
        function is_warning(line) {
            return line ~ /\[(WARNING|WARN)\]/ || line ~ /(^|[[:space:]])WARN([[:space:]:]|$)/ || line ~ /(^|[[:space:]])WARNING:/
        }
        is_warning($0) {
            text = clean($0)
            if (text == "") {
                next
            }
            if (text ~ /Tests run:/) {
                next
            }
            if (!(text in count)) {
                order[++unique_count] = text
            }
            count[text]++
        }
        END {
            if (unique_count == 0) {
                exit
            }
            print "Warnings summary:"
            for (i = 1; i <= unique_count && i <= limit; i++) {
                text = order[i]
                suffix = count[text] > 1 ? " (" count[text] "x)" : ""
                print "  " text suffix
            }
            if (unique_count > limit) {
                print "  ... " (unique_count - limit) " more unique warning(s); see full log"
            }
        }
    ' "$LOG_FILE"
}

print_unexpected_summary() {
    local limit="${1:-12}"
    awk -v limit="$limit" '
        function clean(line) {
            sub(/^\[(INFO|WARNING|WARN|ERROR)\][[:space:]]*/, "", line)
            sub(/^[[:space:]]+/, "", line)
            sub(/[[:space:]]+$/, "", line)
            return line
        }
        function is_warning(line) {
            return line ~ /\[(WARNING|WARN)\]/ || line ~ /(^|[[:space:]])WARN([[:space:]:]|$)/ || line ~ /(^|[[:space:]])WARNING:/
        }
        function is_stack_or_exception(line) {
            return line ~ /(Exception|exception|Throwable|Caused by:|Suppressed:|OutOfMemoryError|StackOverflowError)/ ||
                   line ~ /^[[:space:]]+at[[:space:]]+[^[:space:]]+\([^)]*\)$/ ||
                   line ~ /^[[:space:]]*\.\.\. [0-9]+ more$/
        }
        function is_unexpected(line) {
            low = tolower(line)
            return line ~ /\[ERROR\]/ ||
                   line ~ /(^|[[:space:]])ERROR([[:space:]:]|$)/ ||
                   low ~ /unexpected|fatal|timed out|permission denied|no such file/ ||
                   line ~ /BUILD FAILURE|Failed to execute goal|There are test failures|failed tests/ ||
                   is_stack_or_exception(line)
        }
        is_unexpected($0) {
            if (is_warning($0)) {
                next
            }
            text = clean($0)
            if (text == "" || text ~ /^-+$/ || text == "BUILD SUCCESS") {
                next
            }
            if (!(text in count)) {
                order[++unique_count] = text
            }
            count[text]++
        }
        END {
            if (unique_count == 0) {
                exit
            }
            print "Unexpected output summary:"
            for (i = 1; i <= unique_count && i <= limit; i++) {
                text = order[i]
                suffix = count[text] > 1 ? " (" count[text] "x)" : ""
                print "  " text suffix
            }
            if (unique_count > limit) {
                print "  ... " (unique_count - limit) " more unique unexpected line(s); see full log"
            }
        }
    ' "$LOG_FILE"
}

print_failure_digest() {
    awk '
        function clean(line) {
            sub(/^\[(INFO|WARNING|WARN|ERROR)\][[:space:]]*/, "", line)
            sub(/^[[:space:]]+/, "", line)
            sub(/[[:space:]]+$/, "", line)
            return line
        }
        function add_unique(section, value) {
            if (value == "" || value ~ /^-+$/) {
                return
            }
            key = section SUBSEP value
            if (seen[key]++) {
                return
            }
            rows[section, ++counts[section]] = value
        }
        function emit(title, section, limit, i) {
            if (counts[section] < 1) {
                return
            }
            print title ":"
            for (i = 1; i <= counts[section] && i <= limit; i++) {
                print "  " rows[section, i]
            }
            if (counts[section] > limit) {
                print "  ... " (counts[section] - limit) " more; see full log"
            }
        }
        function is_stack_or_exception(line) {
            return line ~ /(Exception|exception|Throwable|Caused by:|Suppressed:|OutOfMemoryError|StackOverflowError)/ ||
                   line ~ /^[[:space:]]+at[[:space:]]+[^[:space:]]+\([^)]*\)$/ ||
                   line ~ /^[[:space:]]*\.\.\. [0-9]+ more$/
        }
        /Reactor Summary/ {
            capturing_reactor = 1
            next
        }
        capturing_reactor && /BUILD (SUCCESS|FAILURE)/ {
            capturing_reactor = 0
        }
        {
            text = clean($0)
            low = tolower($0)
            if (capturing_reactor && $0 ~ / FAILURE /) {
                add_unique("modules", text)
            }
            if ($0 ~ /Failed to execute goal/) {
                add_unique("goals", text)
            }
            if ($0 ~ /Tests run:[[:space:]]*[0-9]+,[[:space:]]*Failures:[[:space:]]*([1-9][0-9]*)/ ||
                $0 ~ /Tests run:[[:space:]]*[0-9]+,[[:space:]]*Failures:[[:space:]]*[0-9]+,[[:space:]]*Errors:[[:space:]]*([1-9][0-9]*)/ ||
                low ~ /surefire-reports|failsafe-reports|there are test failures|failed tests/) {
                add_unique("tests", text)
            }
            if (low ~ /(pmd|spotbugs|jacoco)/ && low ~ /(fail|violat|coverage|error|check)/) {
                add_unique("quality", text)
            }
            if (is_stack_or_exception($0)) {
                add_unique("exceptions", text)
            }
            if ($0 ~ /^\[ERROR\]/ && text != "" && text !~ /^-+$/) {
                error_tail[++error_count] = text
            }
        }
        END {
            print "Failure digest:"
            emit("Failed modules", "modules", 12)
            emit("Failed goals", "goals", 8)
            emit("Test/report hints", "tests", 12)
            emit("Quality gate hints", "quality", 12)
            emit("Exception/stack-trace hints", "exceptions", 12)
            if (error_count > 0) {
                print "Maven error tail:"
                start = error_count > 25 ? error_count - 24 : 1
                for (i = start; i <= error_count; i++) {
                    print "  " error_tail[i]
                }
            }
        }
    ' "$LOG_FILE"
}

LAST_VISIBLE_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-last-visible.XXXXXX")"
TMP_FILES+=("$LAST_VISIBLE_FILE")
BUILD_START_TIME="$(date +%s)"
echo "$BUILD_START_TIME" >"$LAST_VISIBLE_FILE"

RAW_OUTPUT_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-output.XXXXXX")"
TMP_FILES+=("$RAW_OUTPUT_FILE")

TIMEOUT_MARKER_FILE=""
if ((BUILD_TIMEOUT_SECONDS > 0)); then
    TIMEOUT_MARKER_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-timeout-marker.XXXXXX")"
    TMP_FILES+=("$TIMEOUT_MARKER_FILE")
fi

: >"$LOG_FILE"
set +o pipefail
set +e
run_maven_build >"$RAW_OUTPUT_FILE" 2>&1 &
build_runner_pid=$!

heartbeat_worker "$build_runner_pid" "$BUILD_START_TIME" "$HEARTBEAT_INTERVAL_SECONDS" &
heartbeat_pid=$!

wait "$build_runner_pid"
mvn_status=$?

kill "$heartbeat_pid" >/dev/null 2>&1 || true
wait "$heartbeat_pid" >/dev/null 2>&1 || true

while IFS= read -r line || [[ -n "$line" ]]; do
    printf '%s\n' "$line" >>"$LOG_FILE"
    if [[ "$line" == *"BUILD SUCCESS"* || "$line" == *"BUILD FAILURE"* ]]; then
        printf '%s\n' "$line"
        update_last_visible
    fi
done <"$RAW_OUTPUT_FILE"
filter_status=$?

set -e
set -o pipefail

if ((mvn_status != 0 || filter_status != 0)); then
    echo
    if ((mvn_status == 124)); then
        echo "Maven build timed out after ${BUILD_TIMEOUT_SECONDS}s. Inspect the full log at: $LOG_FILE_FOR_DISPLAY"
        echo "Full build log saved to: $LOG_FILE_FOR_DISPLAY"
        exit 124
    fi
    echo "Maven build failed (mvn=$mvn_status, filter=$filter_status)."
    print_failure_digest
    print_warning_summary 12
    print_unexpected_summary 12
    echo "Full build log saved to: $LOG_FILE_FOR_DISPLAY"
    if ((mvn_status != 0)); then
        exit "$mvn_status"
    fi
    exit 1
fi

summary="$(extract_test_summary)"

echo
print_reactor_summary
if [[ -n "$summary" ]]; then
    echo "$summary"
else
    echo "Tests run summary not found in log; see $LOG_FILE_FOR_DISPLAY"
fi
print_warning_summary 12
print_unexpected_summary 12
echo "Full build log saved to: $LOG_FILE_FOR_DISPLAY"
