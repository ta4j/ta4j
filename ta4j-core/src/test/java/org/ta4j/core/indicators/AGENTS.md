# Indicator tests guidance

- Mirror the package structure of the production indicator.
- Derive expected values with explicit helper methods inside the test to document the math, instead of instantiating the indicator under test twice.
- Cover edge cases such as zero volume or `NaN` inputs so that numerical safety checks stay enforced.
- When tests need bars with volume, build them via `BaseBarSeriesBuilder` and explicitly set the `timePeriod` and `endTime` for clarity.
- When asserting exception scenarios, use JUnit 5's `Assertions.assertThrows` for consistency across test suites.
- Use `MockBarSeriesBuilder` together with explicit `barBuilder()` calls when high/low data matters; this keeps the intent of each bar obvious.
- Prefer AssertJ assertions for expressiveness and to check `Num` results via `isEqualByComparingTo`.
- When indicators expose helper methods (e.g., returning the source index), test them directly alongside the numeric output.
- Include scenarios with missing or `NaN` data to ensure indicators react gracefully.
- Mirror plateau edge cases (equal highs/lows exceeding the allowance) so future contributors keep the NaN/flat-top logic intact.
