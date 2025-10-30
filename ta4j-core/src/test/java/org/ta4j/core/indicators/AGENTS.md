# Indicator test conventions

- Unit test classes for indicators must extend `AbstractIndicatorTest<BarSeries, Num>` when possible. This allows the unit test class to run each individual test twice - once for DoubleNum and once for DecimalNum values, ensuring compatibility with both numeric types.
- Use `MockBarSeriesBuilder` together with explicit `barBuilder()` calls when high/low data matters; this keeps the intent of each bar obvious.
- Use `MockIndicator` for mocking indicator values
- Prefer AssertJ assertions for expressiveness and to check `Num` results via `isEqualByComparingTo`.
- When indicators expose helper methods (e.g., returning the source index), test them directly alongside the numeric output.
- Include scenarios with missing or `NaN` data to ensure indicators react gracefully.
- Mirror plateau edge cases (equal highs/lows exceeding the allowance) so future contributors keep the NaN/flat-top logic intact.
- When asserting oscillator outputs, capture reference sequences from an external calculator (spreadsheet, authoritative
  article) and embed them as literal arrays; this keeps regressions visible without re-implementing the production algorithm in
  the test.
- It is acceptable to keep separate expected sequences per `NumFactory` when floating-point rounding diverges; branch on
  `instanceof DecimalNumFactory` instead of loosening assertions.
