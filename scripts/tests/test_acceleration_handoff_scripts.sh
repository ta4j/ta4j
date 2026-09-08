#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-acceleration-handoff.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$ROOT" >"$TMP/linux-output"
grep -q "nvidia-smi" "$TMP/linux-output"
grep -q "./mvnw -B -pl ta4j-acceleration -am -Pcuda-linux-x86_64" "$TMP/linux-output"
grep -q "libta4j-cuda-accelerator.so" "$TMP/linux-output"
grep -q "ta4j-wiki/wiki/Indicator-Acceleration#linux-cuda-qualification" "$TMP/linux-output"
grep -q "ta4j-wiki/wiki/Indicator-Acceleration" "$TMP/linux-output"

HANDOFF_ROOT="$TMP/root with spaces and 'quotes'"
mkdir -p "$HANDOFF_ROOT/ta4j-acceleration"
BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$HANDOFF_ROOT" >"$TMP/quoted-output"
cd_command="$(sed -n '/^  cd /p' "$TMP/quoted-output")"
if [[ -z "$cd_command" ]] || ! BASH_ENV=/dev/null bash -c "$cd_command"$'\n''[[ "$PWD" == "$1" ]]' -- "$HANDOFF_ROOT"; then
  echo "CUDA handoff must enter its repository before relative commands" >&2
  exit 1
fi

INVALID_ROOT="$TMP/opencl-invalid-argument"
mkdir -p "$INVALID_ROOT/ta4j-acceleration" "$TMP/fake-bin"
cat >"$TMP/fake-bin/clinfo" <<'FAKE_CLINFO'
#!/usr/bin/env bash
printf 'clinfo should not run\n' >>"$OPENCL_MARKER"
FAKE_CLINFO
chmod +x "$TMP/fake-bin/clinfo"
if BASH_ENV=/dev/null OPENCL_MARKER="$TMP/opencl-marker" PATH="$TMP/fake-bin:$PATH" \
    bash "$ROOT/scripts/acceleration/validate-opencl-linux.sh" "$INVALID_ROOT" x86_64 --benchmak \
    >"$TMP/opencl-invalid-output" 2>&1; then
  echo "OpenCL validation should reject an unknown optional argument" >&2
  exit 1
else
  opencl_status=$?
fi
if [[ "$opencl_status" -ne 2 ]]; then
  echo "OpenCL validation should reject an unknown optional argument with exit 2" >&2
  exit 1
fi
if [[ -e "$TMP/opencl-marker" ]]; then
  echo "OpenCL validation executed platform checks before rejecting its optional argument" >&2
  exit 1
fi

if BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$TMP" >"$TMP/missing-output" 2>&1; then
  echo "linux CUDA handoff should reject a root without the ta4j-acceleration module" >&2
  exit 1
fi
grep -q "ta4j-acceleration module not found" "$TMP/missing-output"

echo "acceleration handoff script fixtures passed"
