# Indicator Batch Acceleration

ta4j has an explicit batch-evaluation contract for indicator ranges plus an
optional accelerator module for provider-aware execution. Scalar semantics are
unchanged: `Indicator#getValue(int)` remains the oracle, and
`Indicator#stream()` still maps scalar calls.

## Modules

- `ta4j-core` provides dependency-free request, result, configuration,
  diagnostic, and CPU scalar batch types.
- `ta4j-accelerator` adds graph-family adapters, lazy provider discovery,
  capability reports, CPU fallback, and required-provider failures.

Ordinary CPU-only applications do not need native libraries or GPU drivers.

## Launch policy

Use these JVM properties:

```text
-Dta4j.acceleration=off|cpu|auto|metal|cuda|hybrid
-Dta4j.acceleration.required=true
-Dta4j.acceleration.minimumSpeedup=0.10
-Dta4j.acceleration.metal.library=/absolute/path/to/libta4j-metal-accelerator.dylib
-Dta4j.acceleration.cuda.library=C:\absolute\path\to\ta4j-cuda-accelerator.dll
-Dta4j.acceleration.cuda.maxBytes=536870912
```

`off` is the default. Preferred modes fall back to complete CPU results with
typed diagnostics. Required provider mode throws an `AccelerationException`
and publishes no partial result when the provider cannot execute.

`auto` and `hybrid` apply `minimumSpeedup` to the provider's qualified
end-to-end prediction. Providers without crossover evidence report zero benefit
and remain on CPU. Explicit `metal` and `cuda` may execute a healthy provider
below the automatic threshold.

```java
AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator();
AccelerationConfig config = new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d);
IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecastIndicator, 256, 511, config);
```

Diagnostics identify requested and effective modes, operation, backend,
fallback reasons, and native initialization.

## Provider boundary

Acceleration uses explicit adapters, not arbitrary Java graph compilation.
Unknown graphs use canonical CPU evaluation. Close/SMA is the CPU-planned
control; CUDA v1 supports Monte Carlo price forecasts only.

The qualified CUDA matrix is Windows x86_64, CUDA 13.3, and NVIDIA compute
capability 12.0. It accepts `DoubleNum` inputs and executes FP64 sampling,
projection, reduction, and quantiles. Other platforms, numeric factories,
architectures, and operations fail closed to CPU.

The default artifact remains JVM-only. The explicit Maven profile
`cuda-windows-x86_64` attaches a classifier containing the DLL and SHA-256
resource. An absolute developer-library property takes precedence. Packaged
libraries are verified and atomically extracted below
`~/.ta4j/native/cuda-abi-1/`; the loader never searches the working directory.

On runtimes that restrict native access, add:

```text
--enable-native-access=ALL-UNNAMED
```

Without it Java 25 warns, and a later Java release may block `System.load`.

## Windows CUDA workflow

The PowerShell entrypoint validates prerequisites and can build, integrate,
benchmark, and run the full gate:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/acceleration/windows-cuda-handoff.ps1 -RepoRoot . -Action All
```

The [CUDA completion record](indicator-acceleration-cuda-plan.md) documents
correctness, sanitizer, packaging, and benchmark evidence. The RTX 5090 sweep
found one 1.30x workload but no stable monotonic crossover, so AUTO and HYBRID
deliberately predict zero benefit and stay on CPU. Explicit CUDA remains
available for operators who choose it.

Metal uses its separate developer smoke build:

```bash
scripts/acceleration/build-metal-provider.sh
```

Linux CUDA is not included in the Windows classifier. Continue it from the
[Linux handoff](indicator-acceleration-cuda-linux-handoff.md):

```bash
scripts/acceleration/linux-cuda-handoff.sh .
```

## Rollback and release scope

Use `-Dta4j.acceleration=off` for complete runtime rollback. To disable only
CUDA, remove the optional classifier or explicit DLL property and select `cpu`
or `auto`; `ta4j-core` and scalar behavior remain intact.

Windows CUDA is correctness-qualified only for the exact matrix above. Linux
CUDA and Metal device execution are not claimed. Broader support requires their
separate green qualification reports.
