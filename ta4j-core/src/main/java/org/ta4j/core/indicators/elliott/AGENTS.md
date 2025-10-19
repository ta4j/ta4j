# Elliott indicators conventions

- Prefer lightweight records (for example `ElliottRatio`, `ElliottChannel`) when
  exposing multiple related values from an indicator calculation.
- Keep new indicators resilient to sparse swing output: when insufficient swings
  are available, return `NaN`-backed records and mark ratio/channel types as
  `NONE` rather than throwing.
- When projecting price channels reuse the most recent two highs and lows and
  rely on the series `NumFactory` for slope math to avoid cross-factory
  precision mistakes.
- Expose helper methods (such as `ElliottRatioIndicator#isNearLevel` and
  `ElliottConfluenceIndicator#isConfluent`) so tests and downstream rules can
  interrogate intermediate state without duplicating calculations.
- Keep Elliott phase logic pure and metadata-driven: prefer `ElliottSwingMetadata`
  for slicing swing windows and `ElliottFibonacciValidator` for ratio checks so
  recursive indicators remain side-effect free.
- When signalling invalidations prefer boolean indicators that reuse existing
  validators (for example see `ElliottInvalidationIndicator`).
