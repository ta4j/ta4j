# AGENTS instructions for org.ta4j.core.criteria

- When implementing `AnalysisCriterion#calculate(BarSeries, TradingRecord)`, prefer delegating to the
  `calculate(BarSeries, Position)` implementation for each closed position when it is straightforward.
- It is fine to keep bespoke implementations when delegation would be awkward or inefficient, but all things
  being equal, reuse the position-level calculation to keep logic DRY and avoid drift.
