# ta4j-examples instructions

- GUI-based tests in this module (`CandlestickChartTest`, `IndicatorsToChartTest`, `BuyAndSellSignalsToChartTest`, `CashFlowToChartTest`) require an X11 display. They throw `HeadlessException` on headless runners.
- Before touching Swing/AWT components in unit tests, short-circuit headless environments with `Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());` (add the `org.junit.Assume` import as needed) instead of relying on conditional annotations.
- When you only need to validate `ta4j-core` changes in headless environments, prefer running `scripts/run-full-build-quiet.sh -pl ta4j-core` for filtered logs during development, then re-run the full quiet build (without `-pl` flags) before delivering the change.
- If you must execute the chart tests, run them on a workstation with GUI support or configure a virtual framebuffer (e.g., `xvfb-run`).
- The charting tests share fixtures via `ChartingTestFixtures` and depend on `org.ta4j.core.mocks.*`; prefer those helpers over ad-hoc builders when generating series or OHLC datasets.
- Prefer explicit imports for charting dependencies (e.g., `org.jfree.*`, Swing) instead of repeating fully qualified names inline; this keeps the samples easier to scan when iterating quickly.
- Charting code is split into subpackages: `charting.builder` (builder/plan), `charting.workflow` (facade), `charting.compose` (factory/renderers), `charting.display`, `charting.storage`, and `charting.renderer`. Keep new classes grouped similarly rather than dropping more files into the root.
- Benchmark harness tests are tagged `benchmark` and remain skipped unless enabled with `-Dta4j.runBenchmarks=true`; the tagged methods live alongside the regression tests so CI stays fast by default.
- Example classes should demonstrate realistic usage flows and how to reason about the API in practice; do not turn examples into assertion-heavy test harnesses.
- Move parity checks, behavioral assertions, and detailed correctness validation into unit tests; keep examples focused on walkthroughs, logging, and user-facing usage patterns.
- In examples, prefer existing `AnalysisCriterion` implementations over hand-rolled profit or performance calculations when a criterion already expresses the metric.
- When a `BarSeries` already exists, derive the `NumFactory` from `series.numFactory()` instead of creating a separate `DoubleNumFactory` instance.
- Inline tiny private helpers in examples when extraction would make readers jump around for one or two lines of implementation.
