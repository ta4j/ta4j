# Indicator tests guidance

## Test structure
- Mirror the package structure and class name of the production indicator (e.g., `MyIndicator` → `MyIndicatorTest`).
- Unit test classes for indicators must extend `AbstractIndicatorTest<BarSeries, Num>` when possible. This allows each test to run twice—once for `DoubleNum` and once for `DecimalNum`—ensuring compatibility with both numeric types.

## Test data and assertions
- Use `MockBarSeriesBuilder` together with explicit `barBuilder()` calls when high/low data matters; this keeps the intent of each bar obvious.
- Use `MockIndicator` for mocking indicator values.
- Prefer AssertJ assertions for expressiveness and to check `Num` results via `isEqualByComparingTo`.
- **Never use `Assume.assumeFalse(numFactory instanceof DecimalNumFactory)` to skip tests.** Since `Num.isNaN()` returns `false` by default for valid values, test with valid data and assert that results are not NaN using `assertFalse(value.isNaN())`. This ensures tests run for both `DoubleNum` and `DecimalNum` implementations. For example, instead of testing NaN propagation with `Double.NaN` (which doesn't work with `DecimalNum`), test that normal data produces valid (non-NaN) results.

## Test coverage
- Tests covering round trip serialization/deserialization must be included for all indicators.
- When indicators expose helper methods (e.g., returning the source index), test them directly alongside the numeric output.
- Include scenarios with missing or `NaN` data to ensure indicators react gracefully.
- Mirror plateau edge cases (equal highs/lows exceeding the allowance) so future contributors keep the NaN/flat-top logic intact.
- When asserting oscillator outputs, capture reference sequences from an external calculator (spreadsheet, authoritative
  article) and embed them as literal arrays; this keeps regressions visible without re-implementing the production algorithm in
  the test.
- It is acceptable to keep separate expected sequences per `NumFactory` when floating-point rounding diverges; branch on `instanceof DecimalNumFactory` instead of loosening assertions.

## Unstable bar testing requirements
- Every indicator change that affects `getCountOfUnstableBars()` must include a direct assertion on the unstable count; do not rely on comments or implicit behavior.
- Add explicit boundary checks around warm-up edges:
  - `index == unstableBars - 1`: expect unstable output (`NaN` for `Indicator<Num>`, deterministic fallback for boolean/enum indicators).
  - `index == unstableBars`: expect stable output path (not `NaN`, or normal signal evaluation).
- For composed indicators, compute expected unstable counts from component indicators inside the test (using the same `sum` vs `max` rules) and assert equality.
- Include at least one regression case for lookback/backtracking indicators so the test proves no pre-warmup history access is required.
- If serialization/deserialization is supported, verify unstable count remains identical after round trip.
