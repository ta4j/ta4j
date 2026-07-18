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
cleanup() {
    if [[ -n "${build_runner_pid:-}" ]] && kill -0 "$build_runner_pid" >/dev/null 2>&1; then
        kill "$build_runner_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "${build_runner_pid:-}" ]]; then
        wait "$build_runner_pid" >/dev/null 2>&1 || true
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
: >"$LOG_FILE"
SCRIPT_START_TIME="$(date +%s)"

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

print_warn_or_above_lines() {
    local file="$1"
    awk '
        /\[(WARNING|WARN|ERROR)\]/ ||
        /(^|[[:space:]])(WARN|WARNING|ERROR|FATAL)([[:space:]:]|$)/ ||
        /^\[FAIL\]/ {
            print
        }
    ' "$file"
}

is_warn_or_above_text() {
    local line="$1"
    [[ "$line" =~ \[(WARNING|WARN|ERROR)\] ||
        "$line" =~ (^|[[:space:]])(WARN|WARNING|ERROR|FATAL)([[:space:]:]|$) ||
        "$line" == "[FAIL]"* ]]
}

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
        local actionlint_output
        actionlint_output="$(mktemp "${TMPDIR:-/tmp}/ta4j-actionlint.XXXXXX")"
        TMP_FILES+=("$actionlint_output")
        if command -v actionlint >/dev/null 2>&1 && actionlint -version 2>&1 | grep -Fq "1.7.12"; then
            if ! actionlint >"$actionlint_output" 2>&1; then
                cat "$actionlint_output" >>"$LOG_FILE"
                print_warn_or_above_lines "$actionlint_output"
                echo "[ERROR] actionlint failed" >&2
                return 1
            fi
        elif command -v go >/dev/null 2>&1; then
            if ! go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12 >"$actionlint_output" 2>&1; then
                cat "$actionlint_output" >>"$LOG_FILE"
                print_warn_or_above_lines "$actionlint_output"
                echo "[ERROR] actionlint failed" >&2
                return 1
            fi
        else
            echo "[ERROR] actionlint 1.7.12 or Go is required for hosted CI parity." >&2
            return 1
        fi
        cat "$actionlint_output" >>"$LOG_FILE"
        print_warn_or_above_lines "$actionlint_output"
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
    local -a fixture_statuses=()
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
            fixture_statuses[$index]=1
        else
            fixture_statuses[$index]=0
        fi
    done
    restore_preflight_fixture_traps

    for index in "${!fixtures[@]}"; do
        fixture="${fixtures[$index]}"
        {
            echo "== ${fixture#"$REPO_ROOT"/} =="
            cat "${fixture_outputs[$index]}"
        } >>"$LOG_FILE"
        print_warn_or_above_lines "${fixture_outputs[$index]}"
        if ((${fixture_statuses[$index]:-0} != 0)); then
            if grep -Eq '^\[FAIL\]|\[(ERROR|WARN|WARNING)\]|(^|[[:space:]])(ERROR|FATAL|WARN|WARNING)([[:space:]:]|$)' "${fixture_outputs[$index]}"; then
                :
            elif [[ -s "${fixture_outputs[$index]}" ]]; then
                echo "[ERROR] Preflight fixture failed; see ${fixture#"$REPO_ROOT"/} output in $LOG_FILE_FOR_DISPLAY" >&2
            fi
        fi
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
    if ! run_repository_preflight; then
        echo "Build: failed in $(format_elapsed "$(($(date +%s) - SCRIPT_START_TIME))")"
        echo "Log: $LOG_FILE_FOR_DISPLAY"
        exit 1
    fi
fi

if [[ "$PREFLIGHT_ONLY" == "true" ]]; then
    echo "Build: preflight-only success in $(format_elapsed "$(($(date +%s) - SCRIPT_START_TIME))")"
    echo "Log: $LOG_FILE_FOR_DISPLAY"
    exit 0
fi

if [[ "$DEFAULT_GATE" == "true" ]]; then
    EXTRA_MAVEN_ARGS=("-Dta4j.excludedTestTags=analysis-demo" "${EXTRA_MAVEN_ARGS[@]}")
fi

if ((${#GOALS[@]} == 0)); then
    echo "At least one Maven goal is required" >&2
    exit 2
fi

MAVEN_CMD_PREFIX=(mvn)
if [[ -x "./mvnw" ]]; then
    MAVEN_CMD_PREFIX=("./mvnw")
elif [[ -f "./mvnw" ]]; then
    MAVEN_CMD_PREFIX=(sh "./mvnw")
fi

MVN_CMD=("${MAVEN_CMD_PREFIX[@]}" "${MAVEN_FLAGS[@]}")
if ((${#EXTRA_MAVEN_ARGS[@]} > 0)); then
    MVN_CMD+=("${EXTRA_MAVEN_ARGS[@]}")
fi
MVN_CMD+=("${GOALS[@]}")

{
    printf 'Command:'
    printf ' %q' "${MVN_CMD[@]}"
    printf '\n'
} >>"$LOG_FILE"

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

print_jacoco_coverage() {
    local -a coverage_reports=()
    local report
    while IFS= read -r report; do
        if [[ -f "$report" ]]; then
            coverage_reports+=("$report")
        fi
    done < <(find "$REPO_ROOT" -path "*/target/site/jacoco/jacoco.csv" -type f 2>/dev/null | sort)

    if ((${#coverage_reports[@]} == 0)); then
        return 0
    fi

    for report in "${coverage_reports[@]}"; do
        awk -F, '
            function pct(covered, missed, total) {
                total = covered + missed
                if (total == 0) {
                    return "n/a"
                }
                return sprintf("%.2f%%", (covered * 100) / total)
            }
            NR == 2 {
                group = $1
            }
            NR > 1 {
                instruction_missed += $4
                instruction_covered += $5
                branch_missed += $6
                branch_covered += $7
                line_missed += $8
                line_covered += $9
            }
            END {
                if (group != "") {
                    printf "JaCoCo: %s line %s, branch %s, instruction %s\n",
                        group,
                        pct(line_covered, line_missed),
                        pct(branch_covered, branch_missed),
                        pct(instruction_covered, instruction_missed)
                }
            }
        ' "$report"
    done
}

print_final_summary() {
    local status="$1"
    local elapsed
    elapsed="$(format_elapsed "$(($(date +%s) - SCRIPT_START_TIME))")"
    echo "Build: $status in $elapsed"

    local summary
    summary="$(extract_test_summary)"
    if [[ -n "$summary" ]]; then
        echo "$summary"
    fi

    print_jacoco_coverage
    echo "Log: $LOG_FILE_FOR_DISPLAY"
}

RAW_OUTPUT_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-output.XXXXXX")"
TMP_FILES+=("$RAW_OUTPUT_FILE")

TIMEOUT_MARKER_FILE=""
if ((BUILD_TIMEOUT_SECONDS > 0)); then
    TIMEOUT_MARKER_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-quiet-build-timeout-marker.XXXXXX")"
    TMP_FILES+=("$TIMEOUT_MARKER_FILE")
fi

set +o pipefail
set +e
run_maven_build >"$RAW_OUTPUT_FILE" 2>&1 &
build_runner_pid=$!

wait "$build_runner_pid"
mvn_status=$?

while IFS= read -r line || [[ -n "$line" ]]; do
    printf '%s\n' "$line" >>"$LOG_FILE"
    if is_warn_or_above_text "$line"; then
        printf '%s\n' "$line"
    fi
done <"$RAW_OUTPUT_FILE"
filter_status=$?

set -e
set -o pipefail

if ((mvn_status != 0 || filter_status != 0)); then
    if ((mvn_status == 124)); then
        echo "Build: timed out after ${BUILD_TIMEOUT_SECONDS}s in $(format_elapsed "$(($(date +%s) - SCRIPT_START_TIME))")"
        echo "Log: $LOG_FILE_FOR_DISPLAY"
        exit 124
    fi
    print_final_summary "failed"
    if ((mvn_status != 0)); then
        exit "$mvn_status"
    fi
    exit 1
fi

print_final_summary "success"
