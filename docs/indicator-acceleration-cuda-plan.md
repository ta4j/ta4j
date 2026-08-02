# CUDA acceleration qualification record

CUDA shares the transparent runtime and `ta4j-cli` artifact described in
[Transparent Indicator Acceleration](indicator-acceleration.md). Applications
do not select CUDA directly: `-Dta4j.acceleration=auto` chooses CUDA on a
qualified Windows or Linux host only when its checked crossover model predicts
at least a 10% end-to-end gain.

## Frozen contracts

- Operation ABI 1 and `MonteCarloPriceForecastSpec.RNG_VERSION` 1.
- One immutable primitive request for ordered decision-index ranges.
- Exact structure, index, sample count, stability, and trading-decision parity.
- FP64 summary tolerance `1e-4` for native qualification.
- Deterministic bounded selection fixtures for `1`, `2`, `7`, `252`, `256`,
  and `1000`, shared across Java, CUDA, and Metal.
- CPU fallback on unsupported precision, stale series, memory ceiling, probe,
  allocation, launch, synchronization, output, or validation failure.

## Windows record

Windows x86_64 with CUDA 13.3, NVIDIA RTX 5090 compute capability 12.0, driver
610.88, JDK 25, CMake, and x64 MSVC passed all shock models, both volatility
modes, odd/even path counts, zero variance, unstable input, concurrency, and
the four Compute Sanitizer modes. The base jar is native-free; the
`cuda-windows-x86_64` classifier contains the integrity-checked DLL.

Measured speedups were non-monotonic: 0.81-0.99x at 8,192 work units,
1.30-1.35x at 262,144, and 0.86-0.98x at 2,097,152. CUDA is therefore
correctness-qualified but not selected by `auto` until a stable end-to-end
crossover is demonstrated. There is no public forced-CUDA escape hatch.

## Linux continuation

The same CMake/JNI source and loader now contain Linux x86_64 branches and a
`cuda-linux-x86_64` classifier profile. Hardware execution, sanitizer,
packaging, container, and crossover evidence remain open. Follow the
[Linux qualification handoff](indicator-acceleration-cuda-linux-handoff.md)
without changing the frozen contracts above.
