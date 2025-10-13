# Indicators package guidance

- Follow indicator constructor conventions:
  - Provide a `BarSeries` convenience constructor and overloads that accept the underlying price indicator.
  - Default to the canonical time frames (e.g., 12/26/9 for MACD-style oscillators) when meaningful.
- Prefer composing with existing helper indicators (e.g., `BinaryOperation`, `VolumeIndicator`) rather than reimplementing arithmetic.
- Always guard against zero-volume or NaN inputs and propagate `NaN` using `NaN.NaN` for undefined results.
- Expose companion helpers such as signal lines or histograms when an indicator has a standard derived series.
- Annotate every new public type or method with the current `@since` version.
