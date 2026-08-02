# Metal backtest performance report

## Result

The production Metal path is now both real and performance-positive on the
checked Apple M5 Max. In the transparent `BarSeriesManager` workload, the
scalar median was **280.196 ms** and the qualification harness forced Metal
behind the ordinary `-Dta4j.acceleration=auto` request at **125.716 ms**, a
**2.23x end-to-end speedup**.

The original pre-production spike used the same workload dimensions but its
"Metal preferred" provider only probed and self-tested the device before
falling back to CPU. That run measured 163.398 ms scalar versus 168.196 ms for
the fallback path, or 0.97x. The useful before/after change is therefore not a
cross-machine timing comparison: it is that the requested Metal lane moved
from verified CPU fallback to verified native execution and from a small loss
to a 2.23x within-run gain.

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
to `auto`. The benchmark test also used a package-private qualification selector
to measure Metal even if the checked-in crossover policy changed. Applications
cannot set that selector or bypass the production crossover model.

## Raw trials

| Trial | Scalar `off` (ms) | Metal `auto` (ms) |
| ---: | ---: | ---: |
| 1 | 334.197 | 135.756 |
| 2 | 285.164 | 127.696 |
| 3 | 279.640 | 125.716 |
| 4 | 280.196 | 120.734 |
| 5 | 273.185 | 119.040 |
| **Median** | **280.196** | **125.716** |

The median Metal diagnostic reported one checked chunk, 123.677 ms in provider
work, 10.042 ms native total, 0.362 ms transfer, and 9.679 ms kernel time.

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
   materialization account for roughly 91% of median provider time. The GPU
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
  -Dta4j.runBenchmarks=true \
  -Dta4j.excludedTestTags=requires-cuda \
  -Dta4j.acceleration.metal.library="$PWD/ta4j-cli/target/native/metal/package/META-INF/native/macos-aarch64/libta4j-metal-accelerator.dylib" \
  test
```
