# ta4j-examples instructions

- GUI-based tests in this module (`CandlestickChartTest`, `IndicatorsToChartTest`, `BuyAndSellSignalsToChartTest`, `CashFlowToChartTest`) require an X11 display. They throw `HeadlessException` on headless runners.
- Before touching Swing/AWT components in unit tests, short-circuit headless environments with `Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());` (add the `org.junit.Assume` import as needed) instead of relying on conditional annotations.
- When you only need to validate `ta4j-core` changes in headless environments, prefer running `scripts/run-full-build-quiet.sh -pl ta4j-core` for filtered logs during development, then re-run the full quiet build (without `-pl` flags) before delivering the change.
- If you must execute the chart tests, run them on a workstation with GUI support or configure a virtual framebuffer (e.g., `xvfb-run`).
- The charting tests share fixtures via `ChartingTestFixtures` and depend on `org.ta4j.core.mocks.*`; prefer those helpers over ad-hoc builders when generating series or OHLC datasets.
- All code changes must be covered by comprehensive unit tests that demonstrate correctness and serve as a shield against future regressions.
