# Indicator Batch Acceleration

ta4j now has an explicit batch-evaluation contract for indicator ranges plus an optional accelerator module for provider-aware execution. Scalar indicator semantics are unchanged: `Indicator#getValue(int)` remains the oracle, and `Indicator#stream()` still maps scalar calls.

## Modules

- `ta4j-core` provides dependency-free request/result/configuration/diagnostic types and a CPU scalar batch evaluator.
- `ta4j-accelerator` adds explicit graph-family adapters, lazy provider discovery, provider capability reports, CPU fallback, and required-provider failures.

Ordinary CPU-only applications do not need native libraries or GPU drivers.

## Launch Policy

Use the JVM property:

```bash
-Dta4j.acceleration=off|cpu|auto|metal|cuda|hybrid
```

Optional properties:

```bash
-Dta4j.acceleration.required=true
-Dta4j.acceleration.minimumSpeedup=0.10
-Dta4j.acceleration.metal.library=/absolute/path/to/libta4j-metal-accelerator.dylib
```

`off` is the default. Preferred modes fall back to complete CPU results with typed diagnostics. Required provider mode throws an `AccelerationException` and returns no partial result when a requested provider cannot execute the supported stage.

`auto` and `hybrid` apply `minimumSpeedup` to the provider's qualified
end-to-end prediction before device execution. Providers without crossover
evidence report zero benefit and remain on CPU. Explicit `metal` and `cuda`
modes may execute a healthy provider below the automatic threshold.

## Example

```java
AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator();
AccelerationConfig config = new AccelerationConfig(AccelerationMode.AUTO, false, 0.10d);
IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecastIndicator, 256, 511, config);
```

Diagnostics identify the requested mode, effective backend, operation ID, provider/fallback reasons, and whether native code initialized.

## Adapter Boundary

Acceleration is generic through reusable framework contracts and explicit adapters, not through arbitrary Java graph compilation. Unknown graphs use canonical CPU evaluation. `ComponentDescriptor` can help diagnostics, but it does not authorize GPU execution.

The first adapter families are:

- Close/SMA control: deliberately CPU-planned under current CF-329 evidence.
- Monte Carlo price forecast: CPU state/input preparation with device-eligible terminal path projection and reduction.

## Provider Boundary

Provider factories are discovered lazily and must not load native libraries from constructors. The built-in CUDA provider is a compile-safe unavailable skeleton for the Windows 11 / RTX 5090 continuation. The built-in Metal provider probes macOS arm64 and an explicit native library path only after a supported forecast request makes Metal relevant.

On Java runtimes that restrict native access, classpath applications should add
`--enable-native-access=ALL-UNNAMED` when allowing the optional Metal provider
to load native code. Without that flag, Java 26 emits a restricted-access
warning for the probe and later Java releases may block the call.

Build the local Metal smoke artifact explicitly:

```bash
scripts/acceleration/build-metal-provider.sh
```

The build script prints the absolute `.dylib` path. Pass that exact path through
`-Dta4j.acceleration.metal.library=...`; the provider reports unavailable
instead of loading native code when the property is absent or relative.

Continue CUDA work explicitly on Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/acceleration/windows-cuda-handoff.ps1 -RepoRoot .
```

The decision-complete [CUDA implementation and qualification plan](indicator-acceleration-cuda-plan.md)
records the frozen native boundary, correctness gates, packaging policy, Windows
work sequence, paired Metal dependency, and the separate Linux continuation.
On a Linux CUDA host, print the continuation checklist with:

```bash
scripts/acceleration/linux-cuda-handoff.sh .
```

## Rollback

Disable runtime eligibility without code changes:

```bash
-Dta4j.acceleration=off
```

Removing optional provider artifacts also leaves `ta4j-core` and scalar indicator behavior intact.

## Release Gate

This branch is a Mac-side handoff. Do not treat the shared API as stable-release-ready until Windows CUDA implementation, paired Metal/CUDA parity and benchmark reports, exact fallback gates, and final ta4j API-design review are complete.
