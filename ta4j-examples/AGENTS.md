# ta4j-examples instructions

- Runnable chart demos and their tests remain in `ta4j-examples`; reusable `ta4jexamples.charting` implementations, matching unit tests, and `ChartingTestFixtures` belong to `ta4j-charting`. Examples consume the charting artifact rather than owning or copying those classes.
- GUI demo tests in this module (`CandlestickChartTest`, `IndicatorsToChartTest`, `BuyAndSellSignalsToChartTest`, `CashFlowToChartTest`) require an X11 display. They throw `HeadlessException` on headless runners.
- Before touching Swing/AWT components in unit tests, short-circuit headless environments with `Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Headless environment");` (add the `org.junit.jupiter.api.Assumptions` import as needed) instead of relying on conditional annotations.
- When you only need to validate `ta4j-core` changes in headless environments, prefer running `scripts/run-full-build-quiet.sh -pl ta4j-core` for filtered logs during development, then re-run the full quiet build (without `-pl` flags) before delivering the change.
- If you must execute the chart tests, run them on a workstation with GUI support or configure a virtual framebuffer (e.g., `xvfb-run`).
- Reusable charting tests in `ta4j-charting` share fixtures via `ChartingTestFixtures` and depend on `org.ta4j.core.mocks.*`; keep those fixtures with their owning tests, not in an examples test-JAR API.
- Prefer explicit imports for charting dependencies (e.g., `org.jfree.*`, Swing) instead of repeating fully qualified names inline; this keeps the samples easier to scan when iterating quickly.
- Reusable charting code in `ta4j-charting` is split into subpackages: `charting.builder` (builder/plan), `charting.workflow` (facade), `charting.compose` (factory/renderers), `charting.display`, `charting.storage`, and `charting.renderer`. Add reusable classes there; keep runnable chart walkthroughs in examples.
- Benchmark harness tests are tagged `benchmark` and excluded from the default build; run them explicitly with `-Dgroups=benchmark -Dta4j.excludedTestTags=`. The tagged methods live alongside the regression tests so CI stays fast by default.
- Keep example tests mindful of full-build wall-clock time: if one test already exercises a complete strategy permutation matrix or dataset sweep, use representative boundary cases in nearby tests instead of repeating the full matrix.
- Reuse immutable example fixtures within a test class when tests only read them; avoid rebuilding identical bar histories in per-test setup unless mutation isolation is part of the assertion.
- Example classes should demonstrate realistic usage flows and how to reason about the API in practice; do not turn examples into assertion-heavy test harnesses.
- Move parity checks, behavioral assertions, and detailed correctness validation into unit tests; keep examples focused on walkthroughs, logging, and user-facing usage patterns.
- In examples, prefer existing `AnalysisCriterion` implementations over hand-rolled profit or performance calculations when a criterion already expresses the metric.
- When a `BarSeries` already exists, derive the `NumFactory` from `series.numFactory()` instead of creating a separate `DoubleNumFactory` instance.
- Inline tiny private helpers in examples when extraction would make readers jump around for one or two lines of implementation.
