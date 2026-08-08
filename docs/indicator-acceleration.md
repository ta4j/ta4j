# Transparent Indicator Acceleration

ta4j can accelerate eligible work inside an ordinary `BarSeriesManager`
backtest without changing the strategy, indicator, executor, or trading-record
API. `Indicator#getValue(int)` remains the scalar correctness oracle and
`Indicator#stream()` is unchanged.

## Runtime policy

There are two production states:

```text
# Default; no provider discovery or native loading
-Dta4j.acceleration=off

# Select a qualified GPU when it is expected to beat scalar execution
-Dta4j.acceleration=auto
```

Omitting the property is identical to `off`. Values other than `off` and
`auto` are rejected. There are no production `cpu`, `metal`, `cuda`, or
`hybrid` modes. Backend forcing is reserved for the package-private
qualification tests so applications cannot accidentally bypass the measured
crossover policy.

`auto` discovers providers lazily only after a supported indicator is reached.
Provider absence, an unsupported graph or numeric factory, stale input, memory
limits, or native failure causes complete scalar recomputation. No partial GPU
result is published. Set logging for
`org.ta4j.core.internal.acceleration.AccelerationRuntime` to `DEBUG` to see the selected
backend, typed decision code, native status, timings, and fallback detail.

## Artifact and platform boundary

`ta4j-core` contains the small dependency-free runtime boundary and no native
or platform dependency. Optional providers ship with `ta4j-cli`; applications
that want transparent acceleration add `ta4j-cli` alongside `ta4j-core`.

The default `ta4j-cli` jar remains JVM-only. Native builds attach classifiers:

```bash
./mvnw -pl ta4j-cli -am -Pmetal-macos-aarch64 package
./mvnw -pl ta4j-cli -am -Pcuda-linux-x86_64 package
./mvnw -pl ta4j-cli -am -Popencl-linux-x86_64 package
./mvnw -pl ta4j-cli -am -Popencl-linux-aarch64 package
```

```powershell
mvnw.cmd -pl ta4j-cli -am -Pcuda-windows-x86_64 package
```

Packaged libraries have SHA-256 sidecars and are extracted atomically below
`~/.ta4j/native/`. Absolute developer builds can be supplied with
`ta4j.acceleration.metal.library`, `ta4j.acceleration.cuda.library`, or
`ta4j.acceleration.opencl.library`. On a JVM
that restricts native access, add `--enable-native-access=ALL-UNNAMED`.

Metal is selected only on macOS arm64. CUDA is selected only on Windows
x86_64, with a Linux x86_64 classifier whose hardware qualification is still
open. OpenCL is selected on Linux x86_64 or aarch64 whenever an FP64-capable
OpenCL device is present; on Linux the automatic order prefers CUDA when its
crossover ever qualifies and otherwise falls through to OpenCL. OpenCL is the
vendor-neutral path: NVIDIA, AMD, Intel, and CPU ICDs (for validation) all
execute the same versioned kernels. GPU OpenCL devices are auto-selected once
the qualified workload floor and a minimum device memory capability are
reached; CPU ICD devices (for example PoCL)
execute only through the internal qualification path used by the validation
tests. Current macOS releases do not support NVIDIA CUDA or external
NVIDIA eGPUs, so Metal and CUDA are not competing production choices on one
supported host.

## Supported computation

Acceleration is deliberately narrow, not an arbitrary indicator-DAG compiler.
The production provider accepts `DoubleNum` Monte Carlo price forecasts backed
by log returns and EWMA state. CPU captures immutable recursive state and the
GPU performs terminal-path sampling; Java validates and deterministically
materializes the ordered `Forecast` results. Close/SMA and unknown/custom
graphs remain scalar.

Metal uses checked whole-decision chunks. CUDA uses the same versioned RNG and
forecast fixtures with FP64 reduction. The Java golden corpus covers bounded
RNG selection for `1`, `2`, `7`, `252`, `256`, and `1000`.

The versioned stream replaces the sequential `SplittableRandom` stream used
before 0.23.1. A fixed seed reproduces the same integer random stream in every
lane. Applications that must reproduce pre-0.23.1 seeded values can restore the
legacy stream with `-Dta4j.forecast.rngVersion=0`; the default stays on the
versioned per-path stream that matches the native kernels. While the legacy
stream is active, transparent acceleration is disabled so the documented
legacy values always materialize (native kernels implement only the versioned
stream). CUDA retains FP64
while Metal materializes terminal paths in FP32, so
cross-lane numeric summaries are checked within the documented tolerance rather
than for bit identity; stability, sample counts, quantiles, and trading decisions
must still agree. Upgrading can change previously recorded Monte Carlo values for
the same seed.

## Qualification status

- Apple M5 Max Metal is correctness- and performance-qualified for the checked
  forecast workload. A 4,096-bar transparent backtest with 256 decisions,
  2,048 paths, horizon 32, and lookback 256 measured a 280.196 ms scalar median
  versus 125.716 ms with Metal, or **2.23x**, over five warm trials. All six
  positions, operation indexes, 147 bars in market, and ending equity matched.
  The [full performance report](indicator-acceleration-metal-benchmark.md)
  includes raw trials, parity, reproduction, and the defects it exposed.
- Windows RTX 5090 CUDA is correctness-qualified for Windows x86_64, CUDA 13.3,
  and compute capability 12.0. Existing measurements were not a stable
  crossover, so `auto` conservatively retains scalar execution there pending a
  stronger qualified model.
- Linux CUDA source, loader, and classifier packaging are present but hardware
  qualification remains open. See the [Linux handoff](indicator-acceleration-cuda-linux-handoff.md).
- Linux OpenCL is correctness-qualified through the PoCL Docker validation
  matrix on both x86_64 and aarch64: the probe self-tests, the full
  shock-model/volatility parity matrix, and the transparent end-to-end backtest
  all pass inside a PoCL container. Automatic selection is gated to GPU
  devices with at least 16,777,216 path-steps and at least 2 GiB of device
  global memory pending real-GPU speedup measurement. See the [OpenCL plan](indicator-acceleration-opencl-plan.md)
  and the [committed benchmark report](indicator-acceleration-opencl-benchmark.md).

The benchmark also exposed two defects before release: manager snapshots were
initially rejected as non-identical indicator series, preventing transparent
execution, and Metal's standardized-empirical EWMA kernel normalized shocks
against evolving rather than captured moments. Both now have regression
coverage.

Use `-Dta4j.acceleration=off`, omit `ta4j-cli`, or use the JVM-only artifact for
complete rollback.
