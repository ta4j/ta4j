#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/test-renew-signing-key.XXXXXX")"
trap 'rm -rf "$work"' EXIT

gpg() {
  local args=" $* " value
  if [[ "$args" == *' --import '* ]]; then
    IFS= read -r value
    [[ "$value" == original-private || "$value" == renewed-private ]]
    if [[ "$value" == renewed-private ]]; then
      : > "$GNUPGHOME/primary"
      : > "$GNUPGHOME/subkeys"
    fi
  elif [[ "$args" == *' --list-secret-keys '* ]]; then
    printf 'sec:u:3072:1:0C40BB6B2A100CD4:1:9999999999:::::scSC:\n'
    printf 'fpr:::::::::%s:\n' "${ACTUAL_FINGERPRINT:-$SIGNING_FINGERPRINT}"
    printf 'ssb:e:3072:1:AAAAAAAAAAAAAAAA:1:2:::::e:\n'
    printf 'fpr:::::::::AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:\n'
  elif [[ "$args" == *' --quick-set-expire '* ]]; then
    IFS= read -r value
    [[ "$value" == passphrase-canary ]]
    if [[ "${*: -1}" == "$SIGNING_FINGERPRINT" ]]; then
      : > "$GNUPGHOME/primary"
    else
      [[ "${*: -1}" == AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA ]]
      : > "$GNUPGHOME/subkeys"
    fi
  elif [[ "$args" == *' --export-secret-keys '* ]]; then
    IFS= read -r value
    [[ "$value" == passphrase-canary ]]
    [[ -f "$GNUPGHOME/primary" && -f "$GNUPGHOME/subkeys" ]]
    printf 'renewed-private\n'
  elif [[ "$args" == *' --detach-sign '* ]]; then
    IFS= read -r value
    [[ "$value" == passphrase-canary ]]
    [[ "${FAIL_AT:-}" != sign ]] || return 2
    while [[ "$1" != --output ]]; do shift; done
    printf 'signature\n' > "$2"
  elif [[ "$args" == *' --verify '* ]]; then
    [[ "${FAIL_AT:-}" != verify ]] || return 2
    : > "$TEST_STATE/verified"
  elif [[ "$args" == *' --export '* ]]; then
    printf 'renewed-public\n'
  else
    echo "Unexpected GPG operation: $args" >&2
    return 1
  fi
}
gpgconf() { :; }
curl() {
  [[ -f "$TEST_STATE/verified" ]]
  local payload
  payload="$(</dev/stdin)"
  [[ "$payload" == *'"keytext": "renewed-public\n"'* ]]
  [[ "${FAIL_AT:-}" != keyserver ]] || return 22
  : > "$TEST_STATE/public-published"
  printf '{"key_fpr":"%s"}\n' "$SIGNING_FINGERPRINT"
}
gh() {
  if [[ "$1" == api ]]; then
    [[ "${FAIL_AT:-}" != auth ]] || return 1
    return 0
  fi
  [[ "$*" == 'secret set GPG_PRIVATE_KEY --repo ta4j/ta4j' ]]
  [[ -f "$TEST_STATE/verified" && -f "$TEST_STATE/public-published" ]]
  local value
  IFS= read -r value
  [[ "$value" == renewed-private ]]
  [[ "${FAIL_AT:-}" != secret ]] || return 1
  : > "$TEST_STATE/secret-updated"
}
export -f gpg gpgconf curl gh

run_case() {
  local name="$1" expected="$2" failure="$3" fingerprint="$4"
  local state="$work/$name" status=0 log
  mkdir -p "$state/tmp"
  TEST_STATE="$state" RUNNER_TEMP="$state/tmp" \
    SIGNING_FINGERPRINT=43C18A2E124CF2177A50A71A0C40BB6B2A100CD4 \
    ACTUAL_FINGERPRINT="$fingerprint" GPG_PRIVATE_KEY=original-private \
    GPG_PASSPHRASE=passphrase-canary GITHUB_REPOSITORY=ta4j/ta4j GH_TOKEN=token-canary \
    FAIL_AT="$failure" bash "$ROOT/scripts/release/renew_signing_key.sh" > "$state/log" 2>&1 || status=$?
  if [[ "$expected" == success ]]; then
    [[ "$status" == 0 && -f "$state/secret-updated" ]] || { echo "[FAIL] $name"; exit 1; }
  else
    [[ "$status" != 0 && ! -f "$state/secret-updated" ]] || { echo "[FAIL] $name"; exit 1; }
  fi
  local leftovers=("$state/tmp/"*)
  [[ ! -e "${leftovers[0]}" ]] || { echo "[FAIL] $name leaked keyring"; exit 1; }
  log="$(<"$state/log")"
  if [[ "$log" == *original-private* || "$log" == *renewed-private* || "$log" == *passphrase-canary* || "$log" == *token-canary* ]]; then
    echo "[FAIL] $name leaked a secret"
    exit 1
  fi
  echo "[PASS] $name"
}

fingerprint=43C18A2E124CF2177A50A71A0C40BB6B2A100CD4
run_case renews_primary_subkeys_and_stored_secret success '' "$fingerprint"
run_case rejects_unapproved_identity failure '' 0000000000000000000000000000000000000000
run_case rejects_unusable_signing_key failure sign "$fingerprint"
run_case rejects_bad_signature failure verify "$fingerprint"
run_case preserves_secret_on_keyserver_failure failure keyserver "$fingerprint"
run_case reports_secret_update_failure failure secret "$fingerprint"
run_case rejects_insufficient_token_before_renewal failure auth "$fingerprint"
