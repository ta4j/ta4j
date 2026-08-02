# Metal backtest performance report

## Result

The production Metal path is now both real and performance-positive on the
checked Apple M5 Max. In the transparent `BarSeriesManager` workload, the
scalar median was **312.664 ms** and `-Dta4j.acceleration=auto` selected Metal
at **134.693 ms**, a **2.32x end-to-end speedup**.

The original pre-production spike used the same workload dimensions but its
"Metal preferred" provider only probed and self-tested the device before
falling back to CPU. That run measured 163.398 ms scalar versus 168.196 ms for
the fallback path, or 0.97x. The useful before/after change is therefore not a
cross-machine timing comparison: it is that the requested Metal lane moved
from verified CPU fallback to verified native execution and from a small loss
to a 2.32x within-run gain.

## Workload

| Parameter | Value |
| --- | ---: |
| Bars | 4,096 |
| Forecast decisions | 256 |
| Paths per decision | 2,048 |
| Horizon | 32 |
| Return lookback | 256 |
| Seed | 42 |
| Warmups | 1 scalar + 1 Metal |
| Measured trials | 5 per mode |

Each measured run constructed a fresh `DoubleNum` series, EWMA return state,
Monte Carlo price forecast, strategy, and `BarSeriesManager`. The application
code was identical between modes; only `ta4j.acceleration` changed from `off`
to `auto`. The qualification-only provider selector was set to Metal so this
test measured the backend even if the checked-in crossover policy changed.

## Raw trials

| Trial | Scalar `off` (ms) | Metal `auto` (ms) |
| ---: | ---: | ---: |
| 1 | 371.049 | 160.429 |
| 2 | 332.557 | 136.449 |
| 3 | 312.664 | 134.693 |
| 4 | 306.225 | 129.388 |
| 5 | 302.809 | 125.684 |
| **Median** | **312.664** | **134.693** |

The median Metal diagnostic reported one checked chunk, 132.412 ms in provider
work, 10.369 ms native total, 0.368 ms transfer, and 10.000 ms kernel time.

## Correctness parity

Every scalar and Metal trial produced the same six positions at indexes
`3840:3872`, `3873:3874`, `3947:3948`, `3949:3950`, `3951:4062`, and
`4063:4064`; 147 bars in market; and ending equity
`0.6909564695228994`. The native fixture matrix also passed all shock models,
constant and EWMA volatility, 255/256 path boundaries, zero variance, and
unstable forecasts within the checked tolerance.

## Defects and friction found

1. `BarSeriesManager` evaluates a protected series snapshot while indicators
   retain a read-only view of the original series. The first runtime version
   required object identity, so a real backtest never invoked the provider.
   Requests now bind to the indicator's series while retaining the manager's
   exact run range.
2. The Metal standardized-empirical EWMA kernel initially normalized sampled
   returns against the evolving moments instead of the immutable starting
   moments. The expanded all-model native test caught a 16% standard-deviation
   error; the kernel now matches the scalar sampling contract.
3. Java snapshot capture, validation, sample conversion, sorting, and `Forecast`
   materialization account for roughly 92% of median provider time. The GPU
   kernel is already fast; reducing Java-side copies and reductions is the next
   credible optimization target.
4. Both lanes show a slower first measured trial despite warmup, so promotion
   decisions use medians and retain raw trials rather than citing the best run.
5. Acceleration remains intentionally narrow. Close/SMA and arbitrary custom
   indicator graphs stay scalar, Windows CUDA lacks a stable crossover, and
   Linux CUDA hardware qualification is still open.

Reproduce the Mac lane with:

```bash
scripts/acceleration/build-metal-provider.sh
./mvnw -pl ta4j-cli -am \
  -Dtest=MetalBacktestBenchmarkTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dgroups=benchmark \
  -Dta4j.excludedTestTags=requires-cuda \
  -Dta4j.acceleration.metal.library="$PWD/ta4j-cli/target/native/metal/package/META-INF/native/macos-aarch64/libta4j-metal-accelerator.dylib" \
  test
```
