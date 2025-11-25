# Renko indicator test conventions

## Test structure
- Extend `AbstractIndicatorTest<BarSeries, Num>` to exercise each scenario with both `DoubleNum` and `DecimalNum` factories.
- Mirror the class name under test (e.g., `MyRenkoIndicator` → `MyRenkoIndicatorTest`).

## Test data and assertions
- Use `MockBarSeriesBuilder` to assemble deterministic closing prices and keep helper methods local to the test class for readability.
- Assert both triggering and reset behaviour (e.g., brick count resets after a reversal) with descriptive AssertJ messages.
