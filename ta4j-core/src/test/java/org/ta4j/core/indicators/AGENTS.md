# Indicator test conventions

- Use `MockBarSeriesBuilder` together with explicit `barBuilder()` calls when high/low data matters; this keeps the intent of each bar obvious.
- Prefer AssertJ assertions for expressiveness and to check `Num` results via `isEqualByComparingTo`.
- When indicators expose helper methods (e.g., returning the source index), test them directly alongside the numeric output.
- Include scenarios with missing or `NaN` data to ensure indicators react gracefully.
- Mirror plateau edge cases (equal highs/lows exceeding the allowance) so future contributors keep the NaN/flat-top logic intact.
