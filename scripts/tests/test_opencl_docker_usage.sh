#!/usr/bin/env bash
set -euo pipefail

# The OpenCL validation image declares ENTRYPOINT ["bash"]. Docker appends the
# arguments after the image name to that entrypoint, so a documented invocation
# of the form `docker run ... ta4j-opencl-validate bash scripts/...` executes
# `bash bash scripts/...`, which fails immediately with ENOEXEC
# ("cannot execute binary file"). Usage examples must therefore not pass a
# leading `bash` argument.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKERFILE="$ROOT/scripts/acceleration/Dockerfile.opencl-validate"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

if [[ ! -f "$DOCKERFILE" ]]; then
    fail "OpenCL validation Dockerfile is absent: $DOCKERFILE"
fi

if ! grep -Fq 'ENTRYPOINT ["bash"]' "$DOCKERFILE"; then
    pass "Dockerfile has no bash ENTRYPOINT; no double-invocation risk"
    exit 0
fi

if grep -Fq '#     bash scripts/acceleration/validate-opencl-linux.sh' "$DOCKERFILE"; then
    fail "Dockerfile usage example runs 'ta4j-opencl-validate bash scripts/...' with ENTRYPOINT [\"bash\"]; docker executes 'bash bash' and fails with ENOEXEC"
fi

pass "Dockerfile usage examples are consistent with ENTRYPOINT [\"bash\"]"
