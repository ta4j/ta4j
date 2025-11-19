# Candlestick indicator test conventions

## Test structure
- Mirror the class name under test (e.g., `BullishMarubozuIndicator` → `BullishMarubozuIndicatorTest`).
- Extend `AbstractIndicatorTest<BarSeries, Num>` when possible to ensure compatibility with both `DoubleNum` and `DecimalNum`.

## Test data and assertions
- Use `MockBarSeriesBuilder` or `BaseBarSeriesBuilder` utilities from `org.ta4j.core` to construct deterministic bar data.
- Prefer expressive assertion messages using AssertJ (`assertThat`).

## Test coverage
- Verify both positive and negative scenarios, including boundary cases where the pattern should not trigger.
