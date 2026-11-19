#!/usr/bin/env bash
# Standalone actionlint and isolated script fixtures. Never invokes real Maven.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
mkdir -p .agents/logs
LOG_FILE="$REPO_ROOT/.agents/logs/script-checks-$(date +%Y%m%d-%H%M%S)-$$.log"
LOG_FILE_FOR_DISPLAY="$LOG_FILE"
TMP_FILES=()
cleanup() {
    if ((${#TMP_FILES[@]} > 0)); then rm -f "${TMP_FILES[@]}"; fi
}
trap cleanup EXIT
print_warn_or_above_lines() {
    awk '/\[(WARNING|WARN|ERROR)\]/ || /(^|[[:space:]])(WARN|WARNING|ERROR|FATAL)([[:space:]:]|$)/ || /^\[FAIL\]/ {print}' "$1"
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
            fixture_statuses[index]=1
        else
            fixture_statuses[index]=0
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

if run_repository_preflight; then
    echo "Script/action checks: success"
    echo "Log: $LOG_FILE_FOR_DISPLAY"
else
    echo "Script/action checks: failed" >&2
    echo "Log: $LOG_FILE_FOR_DISPLAY"
    exit 1
fi
