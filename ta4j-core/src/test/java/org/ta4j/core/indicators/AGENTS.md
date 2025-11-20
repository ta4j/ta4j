# Indicator tests guidance

## Test structure
- Mirror the package structure and class name of the production indicator (e.g., `MyIndicator` → `MyIndicatorTest`).
- Unit test classes for indicators must extend `AbstractIndicatorTest<BarSeries, Num>` when possible. This allows each test to run twice—once for `DoubleNum` and once for `DecimalNum`—ensuring compatibility with both numeric types.

## Test data and assertions
- Use `MockBarSeriesBuilder` together with explicit `barBuilder()` calls when high/low data matters; this keeps the intent of each bar obvious.
- Use `MockIndicator` for mocking indicator values.
- Prefer AssertJ assertions for expressiveness and to check `Num` results via `isEqualByComparingTo`.
- When asserting exception scenarios, use JUnit 5's `Assertions.assertThrows` for consistency across test suites.
- **Never use `Assume.assumeFalse(numFactory instanceof DecimalNumFactory)` to skip tests.** Since `Num.isNaN()` returns `false` by default for valid values, test with valid data and assert that results are not NaN using `assertFalse(value.isNaN())`. This ensures tests run for both `DoubleNum` and `DecimalNum` implementations. For example, instead of testing NaN propagation with `Double.NaN` (which doesn't work with `DecimalNum`), test that normal data produces valid (non-NaN) results.

## Test coverage
- All new indicator classes should be accompanied by comprehensive unit test coverage, both to demonstrate correctness but also to serve as protection against regressions during future changes. Tests covering round trip serialization/deserialization must be included.
- **🚫 CRITICAL: NEVER SKIP TESTS WITHOUT EXPLICIT USER APPROVAL.** If a serialization roundtrip test fails (e.g., because an indicator uses `UnaryOperationIndicator` which isn't serializable), you MUST:
  1. Investigate why serialization fails
  2. Fix the underlying issue (e.g., add serialization support), OR
  3. Ask the user for explicit approval before removing or skipping the test
  - **DO NOT** use `Assume.assumeNoException()` or any other mechanism to skip failing tests
  - **DO NOT** silently catch exceptions and skip test execution
  - A failing test is a bug that must be fixed, not something to skip
- When indicators expose helper methods (e.g., returning the source index), test them directly alongside the numeric output.
- Include scenarios with missing or `NaN` data to ensure indicators react gracefully.
- Mirror plateau edge cases (equal highs/lows exceeding the allowance) so future contributors keep the NaN/flat-top logic intact.
- When asserting oscillator outputs, capture reference sequences from an external calculator (spreadsheet, authoritative
  article) and embed them as literal arrays; this keeps regressions visible without re-implementing the production algorithm in
  the test.
- It is acceptable to keep separate expected sequences per `NumFactory` when floating-point rounding diverges; branch on `instanceof DecimalNumFactory` instead of loosening assertions.

