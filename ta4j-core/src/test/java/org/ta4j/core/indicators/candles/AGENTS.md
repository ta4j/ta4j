# Candlestick indicator test conventions

- Mirror the class name under test (e.g., `BullishMarubozuIndicator` -> `BullishMarubozuIndicatorTest`).
- Use `MockBarSeriesBuilder` or `BaseBarSeriesBuilder` utilities from `org.ta4j.core` to construct deterministic bar data.
- Verify both positive and negative scenarios, including boundary cases where the pattern should not trigger.
- Prefer expressive assertion messages using AssertJ (`assertThat`).
