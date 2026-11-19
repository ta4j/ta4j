#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/release/release_helpers.sh"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/release-signing-tests.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
fail() { echo "[FAIL] $1" >&2; exit 1; }

mkdir -p "$TMP/work"
gpg() {
  local batch=false no_tty=false loopback=false pass_fd=false status_fd=false
  local operation='' output='' signature='' payload='' arg passphrase
  for arg in "$@"; do
    [[ "$arg" != 'fixture passphrase' && "$arg" != 'wrong passphrase' ]] || return 2
  done
  while (($#)); do
    case "$1" in
      --batch) batch=true; shift ;;
      --no-tty) no_tty=true; shift ;;
      --yes) shift ;;
      --pinentry-mode) [[ "${2:-}" == loopback ]] || return 2; loopback=true; shift 2 ;;
      --passphrase-fd) [[ "${2:-}" == 0 ]] || return 2; pass_fd=true; shift 2 ;;
      --status-fd) [[ "${2:-}" == 1 ]] || return 2; status_fd=true; shift 2 ;;
      --output) output="${2:-}"; shift 2 ;;
      --detach-sign) operation=sign; payload="${2:-}"; shift 2 ;;
      --verify) operation=verify; signature="${2:-}"; payload="${3:-}"; shift 3 ;;
      *) return 2 ;;
    esac
  done
  [[ "$batch" == true && "$no_tty" == true ]] || return 2
  printf '%s\n' "$operation" >> "$MOCK_CALLS"
  case "$operation" in
    sign)
      [[ "$loopback" == true && "$pass_fd" == true && -n "$output" && -f "$payload" ]] || return 2
      [[ "$(<"$payload")" == 'ta4j Maven signing preflight' ]] || return 2
      IFS= read -r passphrase
      [[ "$passphrase" == 'fixture passphrase' ]] || return 1
      case "$MOCK_MODE" in no-key|invalid|expired) return 1 ;; esac
      printf 'fixture signature\n' > "$output"
      ;;
    verify)
      [[ "$status_fd" == true && -f "$payload" && -f "$signature" ]] || return 2
      [[ "$(<"$payload")" == 'ta4j Maven signing preflight' && "$(<"$signature")" == 'fixture signature' ]] || return 2
      case "$MOCK_MODE" in
        bad-signature) return 1 ;;
        expired-status) echo '[GNUPG:] EXPKEYSIG 0C40BB6B2A100CD4 fixture' ;;
        revoked-status) echo '[GNUPG:] REVKEYSIG 0C40BB6B2A100CD4 fixture' ;;
        missing-status) return 0 ;;
      esac
      echo '[GNUPG:] VALIDSIG 43C18A2E124CF2177A50A71A0C40BB6B2A100CD4 2026-01-01 0 0 4 0 1 10 00'
      ;;
    *) return 2 ;;
  esac
}
export -f gpg
export TMPDIR="$TMP/work" MOCK_CALLS="$TMP/calls" GITHUB_OUTPUT="$TMP/output"
shopt -s nullglob dotglob

run_case() {
  local mode="$1" passphrase="$2" expected="$3" calls="$4" result
  local -a remaining
  : > "$MOCK_CALLS"
  : > "$GITHUB_OUTPUT"
  if MOCK_MODE="$mode" GPG_PASSPHRASE="$passphrase" bash "$SCRIPT" signing-preflight > "$TMP/result" 2>&1; then
    [[ "$expected" == success ]] || fail "$mode unexpectedly succeeded"
    result="$(<"$TMP/result")"
    [[ "$result" == *'audit:signing_preflight=passed signing_fingerprint=43C18A2E124CF2177A50A71A0C40BB6B2A100CD4'* ]] || fail "$mode missing verified fingerprint"
    [[ "$(<"$GITHUB_OUTPUT")" == 'signing_fingerprint=43C18A2E124CF2177A50A71A0C40BB6B2A100CD4' ]] || fail "$mode missing fingerprint output"
  else
    [[ "$expected" == failure ]] || fail "$mode unexpectedly failed"
    result="$(<"$TMP/result")"
    [[ "$result" == *'OpenPGP signing preflight'* && "$result" == *'RELEASE_PROCESS.md'* ]] || fail "$mode missing actionable diagnostic"
    [[ ! -s "$GITHUB_OUTPUT" ]] || fail "$mode produced a fingerprint on failure"
  fi
  [[ "$(<"$MOCK_CALLS")" == "$calls" ]] || fail "$mode called unexpected GPG operations"
  remaining=("$TMP/work"/*)
  (( ${#remaining[@]} == 0 )) || fail "$mode leaked temporary signing files"
  if [[ "$result" == *'fixture passphrase'* || "$result" == *'wrong passphrase'* ]]; then
    fail "$mode leaked the passphrase"
  fi
}

run_case success 'fixture passphrase' success $'sign\nverify'
run_case no-key 'fixture passphrase' failure 'sign'
run_case invalid 'fixture passphrase' failure 'sign'
run_case expired 'fixture passphrase' failure 'sign'
run_case success 'wrong passphrase' failure 'sign'
run_case success '' failure ''
run_case bad-signature 'fixture passphrase' failure $'sign\nverify'
run_case expired-status 'fixture passphrase' failure $'sign\nverify'
run_case revoked-status 'fixture passphrase' failure $'sign\nverify'
run_case missing-status 'fixture passphrase' failure $'sign\nverify'

echo 'All release signing preflight tests passed.'
