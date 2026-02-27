# Window-Aware AnalysisCriterion

`AnalysisCriterion` now supports windowed calculations for bar ranges and time ranges.

## Quick examples

```java
AnalysisCriterion criterion = new NetProfitLossCriterion();

// Past 7 days (relative to series end)
Num pnl7d = criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(7)));

// Past 30 days (relative to series end)
Num pnl30d = criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(30)));

// Explicit date range: [2026-02-10, 2026-02-14)
Num pnlDateRange = criterion.calculate(
        series,
        record,
        AnalysisWindow.timeRange(Instant.parse("2026-02-10T00:00:00Z"), Instant.parse("2026-02-14T00:00:00Z")));
```

## Boundary semantics

- Bar windows use `start` inclusive and `end` inclusive.
- Time windows use `start` inclusive and `end` exclusive.
- Time membership is based on each bar's `endTime`.

## Default behavior

`criterion.calculate(series, record, window)` uses `AnalysisContext.defaults()`:

- `MissingHistoryPolicy.STRICT`
- `PositionInclusionPolicy.EXIT_IN_WINDOW`
- `OpenPositionHandling.IGNORE`
- `asOf = null` (series end anchor)

## Moving/constrained series behavior

For series with removed historical bars (for example after `setMaximumBarCount`):

- `STRICT` throws if requested history is unavailable.
- `CLAMP` intersects the requested window with available logical indices.

Use `CLAMP` when you want best-effort windowing on live moving series.
