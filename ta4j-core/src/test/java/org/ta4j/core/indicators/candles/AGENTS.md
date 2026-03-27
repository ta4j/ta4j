# AGENTS instructions for candlestick indicator tests

Follow `ta4j-core/src/test/java/AGENTS.md` and `.../indicators/AGENTS.md`; this file adds candle-pattern specifics.

- Mirror class names under test (`BullishMarubozuIndicator` -> `BullishMarubozuIndicatorTest`).
- Prefer extending `AbstractIndicatorTest<BarSeries, Num>` to exercise both `DoubleNum` and `DecimalNum`.
- Build deterministic bars with `MockBarSeriesBuilder` or `BaseBarSeriesBuilder` utilities.
- Verify positive, negative, and boundary-trigger scenarios for each pattern.
