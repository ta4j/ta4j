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
