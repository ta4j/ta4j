# AGENTS instructions for indicator tests

Follow `ta4j-core/src/test/java/AGENTS.md` for global policy; this file adds indicator-specific test rules.

## Structure

- Mirror production naming (`MyIndicator` -> `MyIndicatorTest`).
- Prefer extending `AbstractIndicatorTest<BarSeries, Num>` so scenarios run for both `DoubleNum` and `DecimalNum`.

## Data and assertions

- Use `MockBarSeriesBuilder` (and explicit `barBuilder()` calls when OHLC detail matters).
- Use `MockIndicator` when stubbing upstream indicator outputs.
- Prefer AssertJ and compare `Num` values with `isEqualByComparingTo`.
- Do not skip DecimalNum paths with assumptions; keep assertions valid for both factories.

## Coverage expectations

- Include round-trip serialization tests for indicators that support serialization.
- Test exposed helper methods alongside numeric outputs.
- Include missing/NaN data scenarios and plateau edge cases where applicable.
- For oscillator/reference-sequence checks, embed expected arrays sourced from authoritative calculators or references.
- If factory-specific rounding differs, keep separate expected sequences per factory rather than loosening assertions.

## Unstable-bar contract tests

- Any change touching `getCountOfUnstableBars()` must include direct unstable-count assertions.
- Add boundary checks at `unstableBars - 1` (unstable output) and `unstableBars` (stable output path).
- For composed indicators, compute expected unstable counts from component graphs and assert equality.
- Add at least one regression case proving no pre-warmup history access is required for lookback/backtracking logic.
