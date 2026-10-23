#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-acceleration-handoff.XXXXXX")"
TMP="$(cd "$TMP" && pwd -P)"
trap 'rm -rf "$TMP"' EXIT

BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$ROOT" >"$TMP/linux-output"
grep -q "nvidia-smi" "$TMP/linux-output"
grep -q "./mvnw -B -pl ta4j-acceleration -am -Pcuda-linux-x86_64" "$TMP/linux-output"
grep -q "libta4j-cuda-accelerator.so" "$TMP/linux-output"
grep -q -- "-Dtest=CudaNativeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false" "$TMP/linux-output"

HANDOFF_ROOT="$TMP/root with spaces and 'quotes'"
mkdir -p "$HANDOFF_ROOT/ta4j-acceleration"
BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$HANDOFF_ROOT" >"$TMP/quoted-output"
cd_command="$(sed -n '/^  cd /p' "$TMP/quoted-output")"
# TMPDIR can introduce redundant separators; require the same directory, not spelling.
if [[ -z "$cd_command" ]] || ! BASH_ENV=/dev/null bash -c "$cd_command"$'\n''[[ "$PWD" -ef "$1" ]]' -- "$HANDOFF_ROOT"; then
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

if BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/validate-opencl-linux.sh" "$INVALID_ROOT" \
    >"$TMP/opencl-missing-arch" 2>&1; then
  echo "OpenCL validation should reject a missing architecture argument" >&2
  exit 1
else
  opencl_status=$?
fi
if [[ "$opencl_status" -ne 2 ]] || ! grep -q "^usage: validate-opencl-linux.sh" "$TMP/opencl-missing-arch"; then
  echo "OpenCL validation should print usage and exit 2 without an architecture" >&2
  exit 1
fi

# A multi-device clinfo dump must not SIGPIPE grep before Maven is reached.
# Its matching output exceeds pipe buffers; all expensive/native tools are fakes.
OPENCL_ROOT="$TMP/opencl-large-output"
mkdir -p "$OPENCL_ROOT/ta4j-acceleration"
cat >"$TMP/fake-bin/clinfo" <<'FAKE_CLINFO'
#!/usr/bin/env bash
set -euo pipefail
for ((device = 0; device < 32768; device++)); do
  printf 'Device Name fixture-device-%08d cl_khr_fp64 diagnostic padding for a large multi-device platform\n' "$device"
done
FAKE_CLINFO
cat >"$OPENCL_ROOT/mvnw" <<'FAKE_MAVEN'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$OPENCL_MAVEN_LOG"
if [[ "${!#}" == "package" ]]; then
  library="ta4j-acceleration/target/native/opencl-x86_64/package/META-INF/native/linux-x86_64/libta4j-opencl-accelerator.so"
  mkdir -p "$(dirname "$library")"
  printf 'fixture native artifact, not a loadable library\n' >"$library"
fi
FAKE_MAVEN
cat >"$TMP/fake-bin/sha256sum" <<'FAKE_SHA256'
#!/usr/bin/env bash
set -euo pipefail
[[ -f "$1" ]]
printf 'fixture-checksum  %s\n' "$1"
FAKE_SHA256
chmod +x "$TMP/fake-bin/clinfo" "$TMP/fake-bin/sha256sum" "$OPENCL_ROOT/mvnw"
BASH_ENV=/dev/null OPENCL_MAVEN_LOG="$TMP/opencl-maven-log" PATH="$TMP/fake-bin:$PATH" \
    bash "$ROOT/scripts/acceleration/validate-opencl-linux.sh" "$OPENCL_ROOT" x86_64 \
    >"$TMP/opencl-large-output.log" 2>&1
grep -Fxq -- "-B -ntp -pl ta4j-acceleration -am -Popencl-linux-x86_64 package" "$TMP/opencl-maven-log"
grep -Fxq -- "-B -ntp -pl ta4j-acceleration -am -Dtest=OpenClNativeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dgroups=requires-opencl -Dta4j.excludedTestTags=requires-cuda,requires-metal,requires-display,requires-headless -Dta4j.acceleration.opencl.library=$OPENCL_ROOT/ta4j-acceleration/target/native/opencl-x86_64/package/META-INF/native/linux-x86_64/libta4j-opencl-accelerator.so test" "$TMP/opencl-maven-log"
if [[ "$(grep -c '^Device Name fixture-device-' "$TMP/opencl-large-output.log")" -ne 20 ]]; then
  echo "OpenCL validation must limit diagnostic output to 20 matching lines" >&2
  exit 1
fi
grep -q "OpenCL validation PASSED on linux-x86_64" "$TMP/opencl-large-output.log"

# Exercise the documented Docker commands with a fake, never a daemon/build.
while IFS= read -r line; do
  case "$line" in
    '#   docker '*|'#     scripts/acceleration/validate-opencl-linux.sh '*)
      printf '%s\n' "${line#\#   }"
      ;;
  esac
done <"$ROOT/scripts/acceleration/Dockerfile.opencl-validate" >"$TMP/docker-commands"
cat >"$TMP/fake-bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$DOCKER_LOG"
FAKE_DOCKER
chmod +x "$TMP/fake-bin/docker"
BASH_ENV=/dev/null DOCKER_LOG="$TMP/docker-log" PATH="$TMP/fake-bin:$PATH" \
    bash "$TMP/docker-commands"
for pair in 'linux/arm64 aarch64' 'linux/amd64 x86_64'; do
  read -r platform arch <<<"$pair"
  grep -Fxq -- "build --platform $platform -f scripts/acceleration/Dockerfile.opencl-validate -t ta4j-opencl-validate:$arch ." "$TMP/docker-log"
  benchmark=""
  if [[ "$arch" == "aarch64" ]]; then
    benchmark=" --benchmark"
  fi
  grep -Fxq -- "run --rm --platform $platform --user $(id -u):$(id -g) -e HOME=/tmp -v $PWD:/workspace -w /workspace ta4j-opencl-validate:$arch scripts/acceleration/validate-opencl-linux.sh /workspace $arch$benchmark" "$TMP/docker-log"
done

# Static PowerShell command contracts only: do not bypass the Windows guard or
# launch another shell. Native-tool failure execution still needs Windows.
windows_source="$(cat "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1")"
assert_preflight_exit_check() {
  local expected
  printf -v expected '%s\n%s\n    throw "%s failed with exit code $LASTEXITCODE"\n}' \
      "$1" 'if ($LASTEXITCODE -ne 0) {' "$2"
  if [[ "$windows_source" != *"$expected"* ]]; then
    echo "Windows preflight must immediately check native exit status: $2" >&2
    exit 1
  fi
}
assert_preflight_exit_check '$nvccOutput = nvcc --version' 'nvcc --version'
assert_preflight_exit_check '$gpuOutput = nvidia-smi --query-gpu=name,driver_version,compute_cap,memory.total --format=csv,noheader' \
    'nvidia-smi --query-gpu=name,driver_version,compute_cap,memory.total --format=csv,noheader'
assert_preflight_exit_check '$cmakeOutput = cmake --version' 'cmake --version'
assert_preflight_exit_check 'java -version' 'java -version'
grep -Fq '[System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT' \
    "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"
grep -Fq 'throw "The cuda-windows-x86_64 profile requires Windows"' \
    "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"

# Source contract only, not a Metal hardware/numeric proof. The former nil
# options/default-fast-math compilation fails this deterministic regression.
metal_source="$ROOT/ta4j-acceleration/src/main/native/metal/ta4j_metal_jni.m"
grep -Fq 'MTLCompileOptions *options = [[MTLCompileOptions alloc] init];' "$metal_source"
grep -Fq '@available(macOS 15.0, *)' "$metal_source"
grep -Fq 'options.mathMode = MTLMathModeSafe;' "$metal_source"
grep -Fq 'options.mathFloatingPointFunctions = MTLMathFloatingPointFunctionsPrecise;' "$metal_source"
grep -Fq 'options.fastMathEnabled = NO;' "$metal_source"
grep -Fq 'newLibraryWithSource:kernelSource options:options error:&error' "$metal_source"

if BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$TMP" >"$TMP/missing-output" 2>&1; then
  echo "linux CUDA handoff should reject a root without the ta4j-acceleration module" >&2
  exit 1
fi
grep -q "ta4j-acceleration module not found" "$TMP/missing-output"

echo "acceleration handoff script fixtures passed"
