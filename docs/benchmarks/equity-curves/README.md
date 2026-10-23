# Equity curve measurement

This opt-in experiment measures the existing eight equity-based criteria and
`TradingStatementExecutionResult.rankTradingStatements` with 20 distinct records.
It makes no production changes and imposes no CI timing or allocation threshold.

## Inputs and correctness

The workloads are 1,100 bars / 7 positions and 100,000 bars / 500 positions,
using DoubleNumFactory and the default DecimalNumFactory. Each position count
includes two open lots. The ranking records use FIFO and seeds 6342026 through
6342045; even seeds enter long and odd seeds enter short. All records share the
same deterministic close series (seed 6342026). Prices combine a sinusoid and
seeded noise. Entry lots have unit amounts, linear transaction cost 0.001 and
linear borrowing cost 0.0001 per index on both entry sides. Closed lots include
out-of-order exits and a same-bar exit; subsequent exits vary by seed.

Monte Carlo uses P95, seed 6342026, 32 iterations and 16 blocks per path. These
explicitly bounded settings measure this configuration, not the criterion's
10,000-iteration default. All other criteria use their default settings.

`CashFlowTest.deterministicRecordsMatchIncrementalCurvesExactly` owns one
exhaustive constructor/incremental matrix for CashFlow and CumulativePnL:
4 seeds x 3 match policies (FIFO/LIFO/AVG_COST) x retained/pruned windows x
3 final indexes x 2 modes x 2 open-position policies x 2 numeric factories.
The same generator supplies both the small equivalence inputs and benchmark
records. AVG_COST combines the two final open fills into one open position.
An independent eligibility rule selects the positions for incremental pricing;
the test compares every value exactly and covers CashFlow's explicit start bound.
The earliest end clips a recorded closed position before its exit; the other
ends include one or both final open entries.
Existing narrow CumulativePnLTest and CashFlowTest cases remain their owners.

The production constructors at the pinned baseline accumulate positions in
order using the same private arithmetic kernel as incremental calculation.
The test checks constructor selection, costs, capture, bounds and accumulation;
it is not an independent financial-formula oracle or a comparison of two
separate sweep algorithms. No tolerance masks arithmetic differences.

## Bounded comparison

The test-side prototype shares one CashFlow per statement between maximum
drawdown, drawdown duration, RoMaD and Calmar. RoMaD and Calmar reproduce their
default formula only for these immutable, unbounded fixture records. Maximum
absolute drawdown, Monte Carlo, Sharpe and Sortino retain their original
implementations. All eight raw and normalized scores, composite scores and
ranking order must exactly match production before measurement. The previous statement's curve is released before the next is constructed; a peak-size assertion
requires exactly one retained curve. State is also cleared in `finally` after
each ranking invocation, including warmups.

This is a conservative subset experiment, not a production cache contract.
Its numbers include map lookup and clearing. It excludes mutation support,
revision tracking, custom callers and coherent ExcessReturns sharing. A passing
result would justify further design; a miss rejects this candidate but cannot
prove that every wider sharing design would lose.

## Reproduce

Use JDK 25, one test fork, a quiet machine and the pinned source revision. From
the repository root:

```sh
mvn -pl ta4j-core test -Dtest=CashFlowTest,CumulativePnLTest
mvn -pl ta4j-core test -Dtest=EquityCurveBenchmarkTest \
  -Dgroups=benchmark -Dta4j.excludedTestTags= -Dta4j.test.forkCount=1 \
  -Dequity.benchmark.warmups=2 -Dequity.benchmark.repetitions=3 \
  -Dequity.benchmark.revision="$(git rev-parse HEAD)"
```

For a comparison-only rerun, add `-Dequity.benchmark.rankingOnly=true`.
This retains all four workloads and both ranking variants; the default also
measures the eight criteria individually.

The existing benchmark-tagged workflow discovers this test. No workflow changes
are needed. The test writes `ta4j-core/target/equity-curve-benchmark.csv` and
`equity-curve-benchmark-environment.txt`. Preserve both before `clean`.
The committed CSV retains every measured repetition; summarize with medians.

Each operation has an untimed reference evaluation, two untimed warmups and
three timed repetitions by default. Baseline ranking also runs untimed for
comparison with the prototype. Setup and equality assertions are outside the
timed interval. Timing is `System.nanoTime()` elapsed wall time on the executing
thread. Allocations use the HotSpot/OpenJDK ThreadMXBean counter for that thread,
including returned values and ranking output, but excluding fixture setup,
assertions and reporting. It is allocated bytes, not live heap, RSS or total
allocation across worker threads. Criteria execute synchronously here. GC/JIT
and execution order can affect wall time; retain all samples and rerun to confirm
near-threshold claims. No forced GC, test timeout, speedup assertion or CI gate
is used.

The inherited design threshold is at least 1.5x ranking speedup or at least 40%
allocation reduction at 100,000 bars. Both factories and all workloads must be
reported, including losses. Phase 1 requires a measured decision and additional
production-contract design; it is not authorized by adding this harness.


## Measurement provenance

Production baseline: `7adc45bfdf78aae2c07f4008b8bb939eac0aea6b` (remote master
verified on 2026-10-23). The original full run uses harness revision
`650d8433640747b98586e48fd6cc65857fa91ede`; its unmodified samples and JVM
metadata are `initial-retaining20.csv` and `initial-environment.txt`.
Its per-criterion measurements remain valid. Its prototype retained all 20
statement curves through ranking, so its ranking comparison is preserved as a
rejected experiment rather than the final scope design.

The full run took 22m38s. At 100,000 bars it measured 1.621x DoubleNum ranking
speedup / 36.79% allocation reduction and 0.853x DecimalNum speedup / 24.49%
allocation reduction. The loss is retained rather than omitted. Retention of
all statement curves differs from the PRD's per-statement lifetime and can
increase GC pressure; this is a possible contributor, not an established
causal diagnosis from these samples alone.

The recorded JVM was OpenJDK 25.0.5, macOS 26.6.2 aarch64, 18 available
processors. Maven's JaCoCo configuration overrode the requested
`-DargLine=-Xms512m -Xmx2g`: the effective JVM had the JaCoCo 0.8.14 agent,
`-XX:+EnableDynamicAgentLoading -Xshare:off -Djdk.instrument.traceUsage=false`,
and a 32,178,700,288-byte maximum heap. These are instrumented measurements,
not uninstrumented production-latency claims. The metadata records the actual
arguments and heap; use those effective settings when comparing results.


## Final result and next decision

The corrected per-statement ranking run is pinned to harness revision
`54adfa8dc7ec86233988a05ed9878733f4ffcd40`; production remains identical to
baseline `7adc45bfdf78aae2c07f4008b8bb939eac0aea6b`. Its raw samples and effective
JVM metadata are `per-statement-ranking.csv` and
`per-statement-environment.txt`. It ran with `-Dequity.benchmark.rankingOnly=true`
and the same two warmups / three measured repetitions. The full command is the
reproduction command above plus that property and the pinned revision value.
The rerun took 20m09s. Initial individual-criterion samples are reused because
the correction only changes the test-side ranking prototype's retention scope;
no production criterion or fixture changed. Ranking baseline and candidate
ratios below use the corrected run together, not timings mixed across JVM runs.

**Decision:** the retained Phase-0 wall-time criterion is met by DoubleNum at
100,000 bars (1.718x). DecimalNum improves 1.433x but does not independently
meet 1.5x, and neither factory reaches 40% allocation reduction. These results
support proceeding to a separate Phase-1 design review. They do not authorize
production cache shipment: the prototype shares only four consumers, immutable
fixtures do not prove mutation/revision/read-only contracts, and the benchmark
is instrumented. Preserve the DecimalNum qualification and repeat measurements
against any later default-branch/accounting changes, including the neighboring
production work owned by PR #1636. No production caching is part of this patch.

Independent review remains required before publication: the supported Codex
fork operation failed before creating a reviewer (`unsupported app-server
method: thread/fork`). The local preparation includes substantive self-review;
it is not independent review. No publication, ownership or Linear lifecycle
change was performed.

The first table reports each criterion's individual latency and allocation from
the initial full run. Ranking tables report the corrected per-statement run.
All figures are medians of three measured repetitions; MiB means 1,048,576 bytes.

### Individual criteria

| Factory | Bars | Operation | Median ms | Median allocated MiB |
| -- | --: | -- | --: | --: |
| DoubleNumFactory | 1100 | MaximumDrawdownCriterion | 0.521 | 0.263 |
| DoubleNumFactory | 1100 | MaximumAbsoluteDrawdownCriterion | 0.440 | 0.238 |
| DoubleNumFactory | 1100 | MaximumDrawdownBarLengthCriterion | 0.424 | 0.263 |
| DoubleNumFactory | 1100 | MonteCarloMaximumDrawdownCriterion | 0.666 | 1.913 |
| DoubleNumFactory | 1100 | ReturnOverMaxDrawdownCriterion | 0.265 | 0.263 |
| DoubleNumFactory | 1100 | CalmarRatioCriterion | 0.238 | 0.267 |
| DoubleNumFactory | 1100 | SharpeRatioCriterion | 0.598 | 1.395 |
| DoubleNumFactory | 1100 | SortinoRatioCriterion | 0.533 | 1.299 |
| DoubleNumFactory | 100000 | MaximumDrawdownCriterion | 127.727 | 593.864 |
| DoubleNumFactory | 100000 | MaximumAbsoluteDrawdownCriterion | 86.978 | 593.864 |
| DoubleNumFactory | 100000 | MaximumDrawdownBarLengthCriterion | 111.420 | 593.864 |
| DoubleNumFactory | 100000 | MonteCarloMaximumDrawdownCriterion | 111.973 | 595.732 |
| DoubleNumFactory | 100000 | ReturnOverMaxDrawdownCriterion | 112.960 | 593.864 |
| DoubleNumFactory | 100000 | CalmarRatioCriterion | 112.418 | 594.245 |
| DoubleNumFactory | 100000 | SharpeRatioCriterion | 120.663 | 628.947 |
| DoubleNumFactory | 100000 | SortinoRatioCriterion | 126.916 | 629.880 |
| DecimalNumFactory | 1100 | MaximumDrawdownCriterion | 1.499 | 0.958 |
| DecimalNumFactory | 1100 | MaximumAbsoluteDrawdownCriterion | 0.799 | 1.790 |
| DecimalNumFactory | 1100 | MaximumDrawdownBarLengthCriterion | 0.943 | 0.958 |
| DecimalNumFactory | 1100 | MonteCarloMaximumDrawdownCriterion | 8.499 | 14.027 |
| DecimalNumFactory | 1100 | ReturnOverMaxDrawdownCriterion | 0.842 | 0.957 |
| DecimalNumFactory | 1100 | CalmarRatioCriterion | 0.785 | 0.972 |
| DecimalNumFactory | 1100 | SharpeRatioCriterion | 6.632 | 7.546 |
| DecimalNumFactory | 1100 | SortinoRatioCriterion | 5.639 | 7.584 |
| DecimalNumFactory | 100000 | MaximumDrawdownCriterion | 384.095 | 1627.916 |
| DecimalNumFactory | 100000 | MaximumAbsoluteDrawdownCriterion | 1062.850 | 6944.308 |
| DecimalNumFactory | 100000 | MaximumDrawdownBarLengthCriterion | 382.685 | 1627.916 |
| DecimalNumFactory | 100000 | MonteCarloMaximumDrawdownCriterion | 388.584 | 1644.571 |
| DecimalNumFactory | 100000 | ReturnOverMaxDrawdownCriterion | 382.171 | 1627.917 |
| DecimalNumFactory | 100000 | CalmarRatioCriterion | 384.158 | 1628.305 |
| DecimalNumFactory | 100000 | SharpeRatioCriterion | 509.306 | 2267.966 |
| DecimalNumFactory | 100000 | SortinoRatioCriterion | 517.109 | 2280.292 |

### Per-statement ranking

| Factory | Bars | Operation | Median ms | Median allocated MiB |
| -- | --: | -- | --: | --: |
| DoubleNumFactory | 1100 | ranking20 | 27.941 | 67.395 |
| DoubleNumFactory | 1100 | ranking20-shared-drawdowns | 13.929 | 53.837 |
| DoubleNumFactory | 100000 | ranking20 | 19281.041 | 96496.431 |
| DoubleNumFactory | 100000 | ranking20-shared-drawdowns | 11223.252 | 60991.639 |
| DecimalNumFactory | 1100 | ranking20 | 121.135 | 643.357 |
| DecimalNumFactory | 1100 | ranking20-shared-drawdowns | 116.847 | 609.578 |
| DecimalNumFactory | 100000 | ranking20 | 82731.818 | 394114.145 |
| DecimalNumFactory | 100000 | ranking20-shared-drawdowns | 57713.198 | 297582.556 |

### Relative ranking result

| Factory | Bars | Speedup | Allocation reduction |
| -- | --: | --: | --: |
| DoubleNumFactory | 1100 | 2.006x | 20.12% |
| DoubleNumFactory | 100000 | 1.718x | 36.79% |
| DecimalNumFactory | 1100 | 1.037x | 5.25% |
| DecimalNumFactory | 100000 | 1.433x | 24.49% |
