# Linux OpenCL acceleration qualification record

OpenCL shares the transparent runtime and `ta4j-cli` artifact described in
[Transparent Indicator Acceleration](indicator-acceleration.md). Applications
do not select OpenCL directly: `-Dta4j.acceleration=auto` chooses OpenCL on a
Linux x86_64 or aarch64 host (after CUDA when CUDA's crossover ever qualifies)
only when its checked crossover model predicts at least a 10% end-to-end gain.

## Frozen contracts

- Operation ABI 1 and `MonteCarloPriceForecastSpec.RNG_VERSION` 1.
- One immutable primitive request for ordered decision-index ranges.
- Exact structure, index, sample count, stability, and trading-decision parity.
- FP64 summary tolerance `1e-4` for native qualification.
- Deterministic bounded selection fixtures for `1`, `2`, `7`, `252`, `256`,
  and `1000`, shared across Java, CUDA, Metal, and OpenCL.
- Identical golden self-tests on the native side: bounded RNG selection `2`,
  Gaussian draw `-1.3318445490451813` within `1e-12`, and the 2-path forecast
  summary `{100, 100, 0, 100}` within `1e-12`.
- CPU fallback on unsupported precision, stale series, memory ceiling, probe,
  allocation, launch, synchronization, output, or validation failure.

## Device qualification rules

- The probe selects the first platform's devices, prefers an FP64 GPU, and
  falls back to an FP64 CPU ICD (for example PoCL) only for the internal
  qualification path used by tests.
- `CL_DEVICE_DOUBLE_FP_CONFIG` must be non-zero; kernels require
  `cl_khr_fp64`.
- Quantile sorting uses a deterministic bitonic sort (parallel work-group
  variant when the padded sample count fits the device work-group limit,
  single work-item variant otherwise), so results are independent of the
  device's work-group size.
- `freeMemoryBytes` is reported as `CL_DEVICE_GLOBAL_MEM_SIZE`; the provider
  memory ceiling therefore uses half of global memory (OpenCL has no cheap
  free-memory query).

## Crossover model

`OpenClCrossoverModel` returns `0.25` only for GPU devices with at least
16,777,216 path-steps of work (decisions x iterations x horizon). CPU ICD
devices are never auto-selected because scalar Java is competitive with a CPU
OpenCL lane; the qualification tests force the provider directly and are not
a production escape hatch. The `0.25` constant is a placeholder pending
real-GPU measurement on hardware hosts.

## Docker validation evidence

The validation matrix runs in `eclipse-temurin:25-jdk-noble` with CMake, GCC,
`ocl-icd-opencl-dev`, `pocl-opencl-icd` (CPU ICD), and `clinfo`:

- `scripts/acceleration/Dockerfile.opencl-validate` builds the image;
- `scripts/acceleration/validate-opencl-linux.sh <root> <x86_64|aarch64> [--benchmark]`
  checks `cl_khr_fp64`, builds the `opencl-linux-<arch>` classifier, runs
  `OpenClNativeIntegrationTest` (probe self-tests plus the full
  ShockModel x VolatilityUpdateMode parity matrix at 255 and 256 paths), and
  optionally runs the transparent backtest benchmark.

Both `aarch64` (native on Apple silicon) and `x86_64` (emulated) passed the
probe and parity suite. The aarch64 run also passed
`OpenClBacktestBenchmarkTest`: every automatic trial reported
`effectiveBackend=opencl` with positions, operation indexes, bars in market,
and ending equity identical to scalar. See
`.agents/benchmarks/cf-336-validation/cf-336-transparent-opencl-backtest.json`
for raw trials and the measured speedup (PoCL is a CPU ICD, so the reported
speedup documents the lane and is not a hardware claim).

## Open items

- Real-GPU measurement on NVIDIA, AMD, or Intel hosts to replace the `0.25`
  crossover placeholder and confirm the auto-selection floor.
- Optional ICD/device pinning (for example `CL_DEVICE_TOPOLOGY_AMD`) is not
  exposed; selection remains first-platform, GPU-preferred.
