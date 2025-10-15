# Renko indicator test conventions

- Extend `AbstractIndicatorTest` to exercise each scenario with both `DoubleNum` and `DecimalNum` factories.
- Use `MockBarSeriesBuilder` to assemble deterministic closing prices and keep helper methods local to the test class for readability.
- Assert both triggering and reset behaviour (e.g., brick count resets after a reversal) with descriptive AssertJ messages.
