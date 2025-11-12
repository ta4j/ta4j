# Indicator tests guidance

- Mirror the package structure of the production indicator.
- Unit test classes for indicators must extend `AbstractIndicatorTest<BarSeries, Num>` when possible. This allows the unit test class to run each individual test twice - once for DoubleNum and once for DecimalNum values, ensuring compatibility with both numeric types.
- When asserting exception scenarios, use JUnit 5's `Assertions.assertThrows` for consistency across test suites.
- Use `MockBarSeriesBuilder` together with explicit `barBuilder()` calls when high/low data matters; this keeps the intent of each bar obvious.
- Use `MockIndicator` for mocking indicator values
- Prefer AssertJ assertions for expressiveness and to check `Num` results via `isEqualByComparingTo`.
- When indicators expose helper methods (e.g., returning the source index), test them directly alongside the numeric output.
- Include scenarios with missing or `NaN` data to ensure indicators react gracefully.
- Mirror plateau edge cases (equal highs/lows exceeding the allowance) so future contributors keep the NaN/flat-top logic intact.
