# CUDA Indicator Acceleration Completion Plan

This plan completes CF-336 from the current draft branch without redesigning the
public batch API. It is the durable handoff for Windows implementation and Linux
portability work.

## Current state

- Implementation baseline `2c59e8c294cd81015be074391be2f5b84794d86a` contains the dependency-free
  core batch contract, optional accelerator module, explicit Close/SMA and
  forecast adapters, lazy provider discovery, typed CPU fallback, a Metal
  probe/self-test, and an unavailable CUDA factory.
- Draft PR 1591 is open, mergeable, green, fully reviewed by CodeRabbit, and has
  no unresolved review threads. CF-336 remains In Review.
- Neither native provider currently performs forecast computation. The proven
  CF-329 direct Metal kernel is not yet wired behind the production provider.
- The Windows host is ready for the main CUDA lane: Windows 11, JDK 25, CUDA
  13.3, RTX 5090 compute capability 12.0, driver 610.88, and x64 MSVC are
  installed. Validate them again at implementation time.
- The existing framework is a safe scaffold, not the complete PRD. Immutable
  snapshots, versioned RNG fixtures, device reduction, cost-aware AUTO,
  executable HYBRID partitioning, native packaging, and paired reports remain.

## Fixed implementation decisions

1. Keep `Indicator#getValue(int)` and `Indicator#stream()` unchanged and
   authoritative. Acceleration remains explicit through batch evaluation and
   off by default.
2. Keep new CUDA implementation types package-private under the existing
   provider. Add no new public class or SPI unless the final API review proves
   the current request, match, capability, result, and diagnostics cannot model
   a required invariant.
3. Qualify `ta4j.forecast.monte-carlo-price.v1` first. Close/SMA remains the
   CPU-planned control. Unknown graphs and unsupported numeric factories fail
   closed to CPU, or fail atomically when the provider is required.
4. CUDA v1 accepts `DoubleNum` inputs only and computes in FP64. Decimal and
   custom `NumFactory` inputs remain canonical CPU work until separately
   qualified; diagnostics must state the precision rejection.
5. Freeze a backend-neutral RNG contract before native kernels. It must cover
   seed mixing and unbiased bounded selection for bounds 1, 2, 7, 252, 256, and
   1,000. Historical bootstrap and standardized empirical shocks use those
   exact vectors. Normal shocks use one versioned deterministic Gaussian
   algorithm shared by the Java reference, CUDA, and later Metal provider.
6. Structural fields, decision indexes, sample counts, output ordering, RNG
   vectors, and trading predicates are exact. FP64 summaries use a scale-aware
   absolute/relative tolerance capped by the existing CF-329 qualification
   limit of `1e-4`; a provider with any tolerance or decision failure is not
   qualified.
7. CPU prepares recursive state and immutable primitive input snapshots. CUDA
   owns shock generation, path projection, finite-value detection, deterministic
   reduction, and quantile production. Java materializes validated `Forecast`
   values from native summary buffers.
8. Use one JNI call per request or checked chunk, not per decision. JNI methods
   exchange primitive arrays or direct buffers plus explicit lengths and return
   status codes; C++ exceptions never cross JNI.
9. Native availability is false until platform checks, library integrity/load,
   device capability, allocation, kernel launch, synchronization, and a fixed
   numerical self-test all pass. Probe failures are recoverable and retain CPU
   fallback.
10. The normal `ta4j-accelerator` artifact remains JVM-only. Optional classifier
    artifacts contain native resources only:
    `cuda-windows-x86_64` packages
    `META-INF/native/windows-x86_64/ta4j-cuda-accelerator.dll`, and the later
    `cuda-linux-x86_64` classifier packages the corresponding `.so`.
11. `ta4j.acceleration.cuda.library` may point to an explicit absolute developer
    build and takes precedence over a classifier resource. Packaged resources
    extract atomically into a version-and-SHA-256 directory, validate their
    checksum before load, and never search the process working directory.
12. Build native code only under explicit CUDA Maven profiles. Default reactor,
    CPU-only dependencies, unit tests, Javadocs, and release builds must not need
    a driver, toolkit, compiler, or native classifier.
13. Compile the Windows qualification artifact with native `sm_120` SASS and
    `compute_120` PTX. Reject unqualified architectures rather than implying
    broad GPU support. Broader architecture matrices require their own hardware
    evidence.
14. AUTO uses CUDA only when the matching hardware/toolkit manifest has a
    checked-in crossover model and predicts at least the configured minimum
    speedup, default 10%, after setup, transfer, execution, reduction,
    validation, and materialization. Unknown manifests stay on CPU. Explicit
    CUDA mode may run a healthy provider below the AUTO crossover unless the
    request marks acceleration as required and the workload is invalid.
15. HYBRID remains explicit. It partitions only whole decision-index ranges,
    runs one bounded CPU partition and one CUDA partition concurrently, merges
    into preallocated index slots, and publishes only after exact coverage,
    ordering, snapshot, and parity validation.

## Windows implementation sequence

### 1. Freeze contracts and fixtures

- Add package-private immutable forecast snapshot and native request/result
  representations. Capture series begin/end, removed-bar count, history
  revision, requested range, prices, return moments, historical return windows,
  settings, quantiles, and numeric precision.
- Validate the series revision before capture, after capture, and before result
  publication. Checked 64-bit arithmetic must reject overflow, invalid ranges,
  and buffers above the provider memory ceiling before allocation.
- Add Java golden fixtures for every shock model, both volatility modes,
  zero-variance and unstable inputs, extreme finite prices, all required RNG
  bounds, odd/even sample counts, quantile interpolation, and exponent
  overflow/underflow. Version fixture schema and operation ABI together.

Acceptance: Java reference fixtures are deterministic across repeated JVMs;
unsupported precision and stale snapshots produce typed fallback or required
failure without partial results.

### 2. Build the native boundary

- Place shared CUDA/C++ and JNI sources under the accelerator module's native
  source tree and drive them with CMake. Keep PowerShell as a thin developer
  entrypoint; Maven profiles remain the packaging source of truth.
- Split the owning provider into a small factory, a package-private native
  bridge, snapshot encoder/materializer, and provider instance. This is
  justified by separate probe, execution, and test seams; do not add generic
  utility classes.
- Define lifecycle calls for probe metadata, self-test, batched forecast
  evaluation, and deterministic cleanup. Use RAII for streams/events/device
  buffers and return bounded error text plus stable status codes.
- Build/link the JNI library for x86_64, include JDK headers, embed the CUDA
  architecture and ABI version, and package the optional classifier without
  copying native code into the base jar.

Acceptance: a clean CPU-only Maven build never invokes CMake/NVCC; the CUDA
profile produces a loadable DLL; wrong architecture, ABI, checksum, missing
runtime, and duplicate/concurrent load paths all fail safely.

### 3. Implement probe and self-test

- Check Windows x86_64, operation relevance, library source, CUDA driver/runtime
  compatibility, device count, compute capability, FP64 support, free/total
  memory, and stream/event creation.
- Run a fixed seed/sampling vector test and a tiny end-to-end forecast kernel.
  Compare structural output exactly and summaries against embedded tolerances.
- Cache successful immutable capability data per loaded library/device. Cache a
  failed probe as unavailable with an explicit re-probe seam for tests; never
  repeatedly initialize native state in one evaluation loop.

Acceptance: `available=true` and `nativeInitialized=true` appear only after the
self-test. All injected probe/load/allocation/kernel/synchronization failures
preserve preferred-mode CPU fallback and required-mode atomic failure.

### 4. Implement forecast kernels and materialization

- Use one CUDA thread per simulated path and decision. Each thread performs the
  horizon loop, shock selection, constant or EWMA volatility update, cumulative
  log return, and terminal-price conversion while recording non-finite status.
- Process checked whole-decision chunks. Reuse bounded stream and buffer leases;
  do not retain Java indicator, bar, or mutable series objects in native state.
- Reduce with a fixed deterministic tree/order, sort terminal values
  deterministically for median/quantiles, and write mean, population standard
  deviation, requested quantiles, empirical count, stability, and status into
  output buffers.
- Materialize `Forecast` with the request's `NumFactory` only after validating
  every native output, requested index, count, monotone quantile, finite value,
  snapshot revision, and provider result range.

Acceptance: the full fixture matrix has exact structure/index/decision parity,
zero tolerance failures, no missing/duplicate results, and no leaks or races
under repeated and concurrent requests.

### 5. Add selection, fallback, and HYBRID behavior

- Record cold initialization, transfer, kernel, reduction, validation, and total
  timings separately. Generate a hardware/JVM/toolkit manifest and crossover
  table from controlled benchmark sweeps; do not hardcode the RTX 5090 cutoff
  before measurement.
- For preferred CUDA/AUTO, recompute the complete failed chunk on CPU and emit
  the concrete failure reason. For required CUDA, discard all partial work and
  throw `REQUIRED_PROVIDER_UNAVAILABLE`.
- Implement explicit HYBRID with throughput-weighted whole-index partitions,
  bounded CPU workers, one GPU partition, exact ordered merge, and complete
  partition diagnostics. Skip HYBRID when predicted gain over the faster single
  backend is below 10%.

Acceptance: Close/SMA and below-crossover forecasts stay on CPU; healthy large
forecasts select CUDA; forced failures exercise deterministic CPU completion;
HYBRID either beats both single backends for a qualified workload or remains
documented as non-winning and experimental.

### 6. Qualify and close the Windows lane

- Run native unit tests and Compute Sanitizer for memory, race, initialization,
  and synchronization defects. Run Java unit tests with a fake native bridge,
  then opt-in RTX integration tests for real JNI, fixtures, concurrency,
  chunking, memory pressure, and device-loss behavior.
- Benchmark at least five fresh JVM processes per workload across CPU, explicit
  CUDA, AUTO, and HYBRID. Sweep decision count, paths, horizon, lookback,
  cold/warm state, and memory ceiling. Retain raw reports and one comparator
  summary tied to the exact commit and environment manifest.
- Require zero structural, tolerance, and trading-decision failures. CUDA is
  performance-qualified only at 1.25x or better warm end-to-end speedup; record
  a non-qualification honestly rather than weakening correctness or timing
  gates.
- Run focused Maven tests during iteration, the opt-in CUDA integration profile,
  `scripts\run-full-build-quiet.ps1`, and the repository canonical full gate on
  the exact candidate head. Update docs, Javadocs, changelog, provider
  capability examples, rollback instructions, and PR evidence.

Acceptance: all Windows gates are green with zero skipped tests in invoked
profiles, the worktree is clean, reports identify the candidate commit, and the
CUDA skeleton no longer contains `NOT_IMPLEMENTED` for the qualified operation.

## Paired Metal and release gate

Windows completion is necessary but not sufficient to merge CF-336. On the
same candidate commit, wire the proven CF-329 Metal sampling/projection/reduction
path behind the production provider, run the same fixture/report schema on the
M5 Max, and compare CPU, Metal, CUDA, and HYBRID manifests. The draft stays
draft and CF-336 stays In Review until both native providers have zero
correctness/decision failures, exact fallback behavior, acceptable performance
evidence, and a final ta4j public-API review. If Metal cannot meet the gate, keep
its provider unavailable and narrow the release claim rather than merging an
unimplemented peer backend.

## Separate Linux continuation

Linux is a separate portability and packaging effort after the Windows ABI,
fixtures, kernels, and Windows report are frozen. Kernel source and JNI payloads
must be shared; Linux must not fork the operation ABI or golden corpus.

The Linux owner must:

1. Use a CUDA 13.3-qualified x86_64 distribution and supported GCC toolchain;
   capture distribution, kernel, glibc, libstdc++, JDK, driver, toolkit, GPU,
   compute capability, and container/runtime details in the report manifest.
2. Build `libta4j-cuda-accelerator.so` with the same CMake targets and device
   code, changing only host/JNI/linker and resource-packaging branches.
3. Add the `cuda-linux-x86_64` classifier, SHA-256 extraction/load path, execute
   permissions, `ldd`/runtime dependency validation, wrong-architecture and
   unwritable-cache tests, plus supported bare-metal and container smokes.
4. Run the complete shared golden/integration/failure/concurrency/sanitizer
   matrix and Linux-specific driver reset, signal, loader, rpath, and memory
   pressure cases.
5. Emit a Linux report from the frozen schema. Linux support is advertised only
   for the exact qualified distro/toolchain/architecture matrix; it does not
   block the original paired M5 Max/RTX 5090 decision unless the release intends
   to claim Linux CUDA support.

Start that lane with `scripts/acceleration/linux-cuda-handoff.sh .`.
