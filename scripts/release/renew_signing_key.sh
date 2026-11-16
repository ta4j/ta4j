#!/usr/bin/env bash
set -euo pipefail

# Run only with explicit maintainer approval; never as part of publication.
: "${SIGNING_FINGERPRINT:?Expected primary signing fingerprint is required}"
: "${GPG_PRIVATE_KEY:?GPG_PRIVATE_KEY is required}"
: "${GPG_PASSPHRASE:?GPG_PASSPHRASE is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GH_TOKEN:?A repository secrets administration token is required}"
[[ "$SIGNING_FINGERPRINT" =~ ^[0-9A-F]{40}$ ]] || {
  echo '::error::Expected a full uppercase OpenPGP primary fingerprint' >&2
  exit 1
}


# Fail before changing/publicizing the key if the maintenance token cannot
# access the repository's secret-management API.
gh api "repos/$GITHUB_REPOSITORY/actions/secrets/public-key" --silent
umask 077
work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/renew-signing-key.XXXXXX")"
export GNUPGHOME="$work/keyring"
mkdir "$GNUPGHOME"
cleanup() {
  gpgconf --homedir "$GNUPGHOME" --kill gpg-agent 2>/dev/null || true
  rm -rf "$work"
}
trap cleanup EXIT

printf '%s\n' "$GPG_PRIVATE_KEY" | gpg --batch --import
unset GPG_PRIVATE_KEY
fingerprints="$(gpg --batch --with-colons --list-secret-keys | awk -F: '
  $1 == "sec" { primary = 1; next }
  primary && $1 == "fpr" { print $10; primary = 0 }
')"
[[ "$fingerprints" == "$SIGNING_FINGERPRINT" ]] || {
  echo '::error::Stored secret does not contain exactly the approved primary key' >&2
  exit 1
}

# GnuPG updates the primary and the subkeys in separate operations.
printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
  --passphrase-fd 0 --quick-set-expire "$SIGNING_FINGERPRINT" 2y
printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
  --passphrase-fd 0 --quick-set-expire "$SIGNING_FINGERPRINT" 2y '*'

# Verify the exported secret, not merely the in-memory key modified above.
mkdir "$work/verified-keyring"
printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
  --passphrase-fd 0 --armor --export-secret-keys "$SIGNING_FINGERPRINT" \
  | GNUPGHOME="$work/verified-keyring" gpg --batch --import
gpgconf --homedir "$GNUPGHOME" --kill gpg-agent
export GNUPGHOME="$work/verified-keyring"
printf 'ta4j signing-key renewal proof\n' > "$work/proof.txt"
printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
  --passphrase-fd 0 --armor --detach-sign --output "$work/proof.txt.asc" "$work/proof.txt"
gpg --batch --verify "$work/proof.txt.asc" "$work/proof.txt"
gpg --batch --with-colons --list-secret-keys | awk -F: '
  $1 == "sec" || $1 == "ssb" { print "audit:renewed_key=" $5 ",expires_epoch=" $7 }
'

gpg --batch --armor --export "$SIGNING_FINGERPRINT" > "$work/public-key.asc"
jq -n --rawfile keytext "$work/public-key.asc" '{keytext: $keytext}' \
  | curl --fail --silent --show-error --max-time 60 \
    -H 'Content-Type: application/json' --data-binary @- \
    https://keys.openpgp.org/vks/v1/upload > "$work/upload.json"
jq -e --arg expected "$SIGNING_FINGERPRINT" '.key_fpr == $expected' "$work/upload.json" >/dev/null

# Only public material crosses the keyserver boundary; private export goes straight
# to GitHub's encrypted secret API and is never an artifact or log entry.
printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
  --passphrase-fd 0 --armor --export-secret-keys "$SIGNING_FINGERPRINT" \
  | gh secret set GPG_PRIVATE_KEY --repo "$GITHUB_REPOSITORY"
echo "audit:renewed_signing_fingerprint=$SIGNING_FINGERPRINT"
echo 'audit:signing_key_validity=2y'
echo 'audit:signing_secret_updated=true'
