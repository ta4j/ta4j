# TA4J Elliott Wave Enhancements (Part 2) — Worktree Plan

## Scope
- Implement MVP items from `ta4j-wiki/architecture/proposed/ew-enhancements-part-2/TODO-ta4j-elliottwave-enhancements-part-2.md`.
- Keep existing Elliott Wave APIs backwards compatible; add new orchestration and configuration layers.

## Goals
- Volatility-adaptive swing detection with composite confirmation and noise filtering.
- Confidence scoring extensibility with per-pattern profiles and time alternation diagnostics.
- Pattern set configuration for scenario generation.
- New analyzer orchestration class returning plain result objects.
- Example updates to surface confidence breakdown and new detectors.

## Non-goals
- ML models or multi-timeframe aggregation.
- Full trading execution engine.

## Decisions
- New functionality is additive; existing indicators/facade remain unchanged.
- Confidence diagnostics are exposed via new breakdown objects, not by altering `ElliottConfidence`.
- Analyzer pipeline composes new interfaces (swing detector, filter, confidence model) to preserve modularity.

## Implementation Checklist
- [x] Add swing detector interfaces and implementations:
  - [x] `SwingDetector` + result model
  - [x] `AdaptiveZigZagSwingDetector` (ATR-based threshold with clamp + smoothing)
  - [x] `CompositeSwingDetector` (AND/OR pivot agreement)
  - [x] `MinMagnitudeSwingFilter`
- [x] Add confidence factor framework:
  - [x] `ConfidenceFactor`, `FactorResult`, `ConfidenceProfile`, `ConfidenceModel`
  - [x] `TimeAlternationFactor` + diagnostics
  - [x] Fibonacci relationship factor (wave 2/3/4/5 + A/B/C)
- [x] Add `PatternSet` and wire into `ElliottScenarioGenerator`.
- [x] Add `ElliottWaveAnalyzer` + `ElliottAnalysisResult`.
- [x] Update `ElliottWaveAnalysis` to print confidence breakdown.
- [x] Add examples:
  - [x] `ElliottWaveAdaptiveSwingAnalysis`
  - [x] `ElliottWavePatternProfileDemo`
- [x] Add/adjust unit tests for:
  - [x] adaptive threshold effect on swings
  - [x] time alternation diagnostics
  - [x] per-pattern profile weight differences
  - [x] pattern set filtering
- [x] Update `CHANGELOG.md` for new APIs and examples.
- [x] Move this PRD to `docs/archive/` when complete.

## Notes
- Use ossified datasets for examples (no live HTTP).
- Run full build script before completion.
