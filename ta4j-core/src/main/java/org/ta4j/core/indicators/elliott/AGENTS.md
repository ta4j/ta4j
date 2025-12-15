# Elliott indicators conventions

- Prefer lightweight records (for example `ElliottRatio`, `ElliottChannel`, `ElliottConfidence`, `ElliottScenario`) when exposing multiple related values from an indicator calculation.

- Keep new indicators resilient to sparse swing output: when insufficient swings are available, return `NaN`-backed records and mark ratio/channel types as `NONE` rather than throwing.

- When projecting price channels reuse the most recent two highs and lows and rely on the series `NumFactory` for slope math to avoid cross-factory precision mistakes.

- Expose helper methods (such as `ElliottRatioIndicator#isNearLevel` and `ElliottConfluenceIndicator#isConfluent`) so tests and downstream rules can interrogate intermediate state without duplicating calculations.

- Keep Elliott phase logic pure and metadata-driven: prefer `ElliottSwingMetadata` for slicing swing windows and `ElliottFibonacciValidator` for ratio checks so recursive indicators remain side-effect free.

- When signalling invalidations prefer boolean indicators that reuse existing validators (for example see `ElliottInvalidationIndicator`).

## Scenario-based analysis

- Use `ElliottScenarioIndicator` when multiple plausible wave interpretations are needed. It returns an `ElliottScenarioSet` containing ranked alternatives with confidence scores.

- The `ElliottConfidenceScorer` calculates confidence from five weighted factors: Fibonacci proximity (35%), time proportions (20%), alternation quality (15%), channel adherence (15%), and structure completeness (15%). Custom weights can be provided.

- Use `ElliottScenarioGenerator` to produce alternative scenarios by exploring different starting points, pattern types (impulse vs corrective), and degree variations. It automatically prunes low-confidence scenarios (default threshold 0.15).

- Access scenario methods via `ElliottWaveFacade#scenarios()`, `#primaryScenario(int)`, `#alternativeScenarios(int)`, `#confidenceForPhase(int, ElliottPhase)`, and `#scenarioConsensus(int)`.

- For invalidation levels, prefer `ElliottInvalidationLevelIndicator` over the boolean `ElliottInvalidationIndicator` when you need the actual price level. Three modes are available: PRIMARY (from top scenario), CONSERVATIVE (tightest across high-confidence scenarios), AGGRESSIVE (widest across all scenarios).

- Use `ElliottProjectionIndicator` for Fibonacci-based price targets derived from the primary scenario. Call `#allTargets(int)` to get multiple target levels.

- The `ElliottScenarioComparator` utility provides methods to compare scenarios: `divergenceScore()`, `sharedInvalidation()`, `consensusPhase()`, `hasDirectionalConsensus()`, and `commonTargetRange()`.
