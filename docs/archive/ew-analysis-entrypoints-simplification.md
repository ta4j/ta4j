# PRD: Elliott Wave Analysis Entry Point Simplification

## Goal
Make Elliott Wave (EW) usage obvious by collapsing the analysis entry points into a minimal, human-friendly API surface while preserving flexibility.

### Primary entry points
1. **Indicator / strategy composition**
   - Use `ElliottWaveFacade` to construct per-bar indicators (phase, ratios, scenarios, invalidation levels, projections, trend bias).

2. **One-shot analysis (batch/report/chart)**
   - Use a single analysis orchestrator that can run:
     - single-degree analysis (base degree only)
     - optional cross-degree validation (neighboring degrees) to re-rank base scenarios

## Non-goals
- Rewriting the full EW indicator suite or removing internal implementation classes.
- Implementing full multi-timeframe resampling automation (kept pluggable via runner/selector seams).

## Design
### Public API
- `ElliottWaveFacade` remains the indicator facade.
- Introduce/standardize on `ElliottWaveAnalysis` as the *only* analysis entry point.
  - Single-degree and multi-degree are configuration of the same class.
- Use one result object for “analysis with optional supporting degrees” so callers don’t have to choose result types.

### Configuration seams
- `SeriesSelector`: choose per-degree lookback/subseries (default uses `ElliottDegree.recommendedHistoryDays()`).
- `AnalysisRunner`: run one degree of analysis (default uses adaptive ZigZag + degree-scaled noise filtering/compression).

## Implementation checklist
- Add `ElliottWaveAnalysis` (merge `ElliottWaveAnalyzer` + `ElliottWaveMultiDegreeAnalyzer` semantics).
- Rename/align result naming to match the new single entry point.
- Revise Javadoc and `package-info.java` to clearly list only two entry points.
- Refresh examples to import and demonstrate the new entry point.
- Amend README + changelog entries.
- Replace/adapt tests for the renamed/consolidated API.
- Run `scripts/run-full-build-quiet.sh` and ensure green.

## Decisions
- Prefer API clarity over strict backwards compatibility for these new EW analysis entry points (still within feature branch scope).
