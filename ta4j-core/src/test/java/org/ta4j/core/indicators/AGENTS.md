# Indicator tests guidance

- Mirror the package structure of the production indicator.
- Derive expected values with explicit helper methods inside the test to document the math, instead of instantiating the indicator under test twice.
- Cover edge cases such as zero volume or `NaN` inputs so that numerical safety checks stay enforced.
- When tests need bars with volume, build them via `BaseBarSeriesBuilder` and explicitly set the `timePeriod` and `endTime` for clarity.
- When asserting exception scenarios, use JUnit 5's `Assertions.assertThrows` for consistency across test suites.
