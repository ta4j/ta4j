# AGENTS instructions for renko indicator tests

Follow `ta4j-core/src/test/java/AGENTS.md` and `.../indicators/AGENTS.md`; this file adds renko-specific expectations.

- Extend `AbstractIndicatorTest<BarSeries, Num>` to run scenarios against both `DoubleNum` and `DecimalNum`.
- Mirror production class names in test class names.
- Use `MockBarSeriesBuilder` with deterministic closing-price fixtures.
- Assert both trigger and reset behavior (for example reversal-driven brick-count resets).
