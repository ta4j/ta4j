# ta4j-examples instructions

- GUI-based tests in this module (`CandlestickChartTest`, `IndicatorsToChartTest`, `BuyAndSellSignalsToChartTest`, `CashFlowToChartTest`) require an X11 display. They throw `HeadlessException` on headless runners.
- When you only need to validate `ta4j-core` changes in headless environments, prefer running `mvn -pl ta4j-core clean license:format formatter:format test` or re-run the full reactor with `-DskipTests -pl ta4j-examples` after the core build succeeds.
- If you must execute the chart tests, run them on a workstation with GUI support or configure a virtual framebuffer (e.g., `xvfb-run`).
- The charting tests share fixtures via `ChartingTestFixtures` and depend on `org.ta4j.core.mocks.*`; prefer those helpers over ad-hoc builders when generating series or OHLC datasets.
