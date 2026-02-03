# Volume indicator conventions

- Prefer extending `AbstractVWAPIndicator` for any VWAP-derived indicator so the
  shared NaN handling and weighted aggregation logic stays consistent.
- When introducing new public VWAP utilities, document their anchor behaviour
  explicitly and add `@since 0.19` tags to every constructor or helper exposed
  outside the package.
- Reuse existing constructors (for example the ones accepting custom price and
  volume indicators) instead of duplicating data extraction logic.
- Use `IndicatorSeriesUtils.requireSameSeries` when combining price/volume
  anchors or secondary indicators (e.g., standard deviation, bands) to guard
  against mismatched inputs early.
