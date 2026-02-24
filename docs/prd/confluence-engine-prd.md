# PRD: Confluence Engine and Confidence Calibration

## Document Metadata
- Status: Draft
- Author: Codex + maintainer review
- Created: 2026-02-24
- Target repo: `ta4j`
- Primary timeframe target: Daily bars
- Initial market focus: S&P 500 (`^GSPC`)

## Problem Statement
Current TA analysis in the workspace can produce useful indicator snapshots and scenario narratives, but confidence values are not consistently calibrated across methods and horizons. In practice, this causes two reliability issues:

1. Signal stacking can overstate conviction when correlated indicators agree.
2. Confidence scores are often interpreted as probabilities without calibration and reliability checks.

The objective is to implement a confluence-first analysis layer that improves forecast trustworthiness by combining independent evidence, calibrating probabilities, and decomposing confidence into interpretable components.

## Goals
Implement recommendations 1 through 4 from the analysis roadmap:

1. Build a 6-pillar confluence engine.
2. Make confluence independence-aware to avoid double counting correlated signals.
3. Produce calibrated probabilities by horizon (1 month and 3 months).
4. Decompose confidence into model, calibration, regime, and data-confidence components.

## Non-Goals (Initial)
- Fully automated execution/trading system.
- Intraday microstructure alpha research.
- Portfolio optimization across many instruments.
- Options/orderflow proprietary feed integration in Phase 1.

## Users and Decisions Supported
- Primary user: strategy researcher/PM.
- Decisions:
  - Is the current context bullish, bearish, or range-bound over 1M and 3M horizons?
  - Which support/resistance levels are most likely to hold or break?
  - How confident should we be, and why?

## Product Requirements

### PR-1: Confluence Engine (6 Pillars)
System must compute a normalized confluence score from six evidence pillars:

1. Structure
2. Trend
3. Momentum
4. Volatility
5. Participation
6. Macro/Intermarket (placeholder in Phase 1, active in later phases)

Each pillar outputs:
- `pillarScore` in `[0, 100]`
- `direction` in `{BULLISH, BEARISH, NEUTRAL}`
- `explanations[]`
- `featureContributions[]`

### PR-2: Independence-Aware Aggregation
Confluence aggregation must:
- Group indicators into families.
- Apply family caps to prevent redundant confirmation inflation.
- Penalize highly correlated families.

Required outputs:
- `rawConfluenceScore`
- `decorrelatedConfluenceScore`
- `correlationPenalty`
- `effectiveFamilyWeights`

### PR-3: Calibrated Probabilities
For each horizon (`1M`, `3M`), system must output:
- `P(up)`
- `P(down)`
- `P(range)`

Probabilities must be calibrated using out-of-sample walk-forward outputs.
Calibration metadata required:
- `calibrationMethod`
- `trainingWindow`
- `lastCalibrationDate`
- reliability metrics (`brier`, `ece`, `logLoss`)

### PR-4: Confidence Decomposition
System must compute and expose:
- `modelConfidence`
- `calibrationConfidence`
- `regimeConfidence`
- `dataConfidence`
- `finalConfidence`

Final confidence must be an explicit weighted blend with configurable weights.

## Quick Win: ConfluenceReport v1

### Objective
Deliver a practical, high-signal report quickly using existing `ta4j` capabilities without external data dependencies beyond current Yahoo OHLCV feed.

### Scope (Phase 1)
- Use existing indicators already in toolkit.
- Build confluence across first 5 operational pillars; macro/intermarket remains a placeholder contribution with explicit `N/A` flag.
- Provide level confidence for top support/resistance levels.
- Provide chart output via `ta4jexamples.charting.workflow.ChartWorkflow`.

### Proposed initial indicators
- Structure: `TrendLineSupportIndicator`, `TrendLineResistanceIndicator`, `PriceClusterSupportIndicator`, `PriceClusterResistanceIndicator`, `BounceCountSupportIndicator`, `BounceCountResistanceIndicator`, Elliott invalidation/targets when available.
- Trend: `SMA(20/50/200)`, `EMA(21)`, slope proxies, distance-from-MA.
- Momentum: `RSI(14)`, `MACD(12,26,9)`, `ROC`.
- Volatility: `ATR(14)`, Bollinger bandwidth/percentB, `VWAPZScore` where applicable.
- Participation: `OBV`, `MFI`, relative volume.
- Macro/intermarket: placeholder field with null contribution and explicit reason.

### Phase 1 output contract
A serialized report object with:
- Snapshot metadata (ticker, timeframe, bar timestamp, bars loaded).
- Pillar breakdown with scores and evidence.
- Confluence score (`raw` and `decorrelated` in v1-lite with static family caps).
- Top support/resistance levels with confidence and distance-to-price.
- Scenario summary (if Elliott available).
- Forward outlook narrative template (1M/3M directional bias + uncertainty).

## Functional Design

### F-1: Domain Model
Proposed core model (can begin in examples package, then move to core package):

- `ConfluenceReport`
- `ConfluenceSnapshot`
- `PillarScore`
- `FeatureContribution`
- `LevelConfidence`
- `HorizonProbability`
- `ConfidenceBreakdown`
- `ValidationMetadata`

### F-2: Level Confidence Scoring
Each level confidence should combine:
- Structural quality (trendline/cluster/bounce score)
- Touch count and recency
- Outside-violation count
- Agreement across methods/time windows
- Volatility-adjusted distance to level

Suggested normalized formula (initial):

`levelConfidence = clamp(100 * (0.40 * structural + 0.25 * touches + 0.15 * recency + 0.10 * agreement + 0.10 * volatilityContext), 0, 95)`

Where each component is in `[0, 1]`.

### F-3: Confluence Aggregation
Let family scores be `S_f` and family weights `W_f`:

1. Raw score: `S_raw = sum(W_f * S_f)`
2. Correlation penalty matrix `R` estimated from rolling history of family outputs.
3. Effective weight: `W'_f = W_f * (1 - avgCorrPenalty_f)`
4. Decorrelated score: `S_decorr = sum(W'_f * S_f) / sum(W'_f)`

In Phase 1, use static family caps and predefined penalties (deterministic, no training).

### F-4: Horizon Probability Targets
Define horizon labels from forward return over horizon `H`:
- `up`: forward return >= `+k * realizedVol(H)`
- `down`: forward return <= `-k * realizedVol(H)`
- `range`: otherwise

Initial defaults:
- `H=1M` and `H=3M`
- `k=1.0`

## Calibration Design

### C-1: Training and Evaluation Method
- Use walk-forward splits only.
- Use purge + embargo to reduce leakage around boundaries.
- Persist fold-level predictions for calibration fitting.

### C-2: Calibration Methods
Priority order:
1. Isotonic calibration (non-parametric)
2. Platt scaling fallback

### C-3: Calibration Metrics
- Brier score (primary)
- Log loss
- Expected Calibration Error (ECE)
- Reliability bucket table

## Confidence Decomposition Design

### Components
- `modelConfidence`: confidence implied by model margin / probability concentration.
- `calibrationConfidence`: confidence that forecast probabilities are reliable under recent backtest reliability.
- `regimeConfidence`: confidence in current regime classification stability.
- `dataConfidence`: confidence from data quality and feed completeness.

### Final Confidence Formula

`finalConfidence = 0.45 * modelConfidence + 0.25 * calibrationConfidence + 0.20 * regimeConfidence + 0.10 * dataConfidence`

All components and final value are bounded in `[0, 100]`.

## Implementation Plan

### Phase 1: Quick Win (`ConfluenceReport` v1)
- Deliverable: S&P 500 daily confluence report + chart export.
- Code location (initial): `ta4j-examples/src/main/java/ta4jexamples/analysis/confluence/`
- Key classes:
  - `ConfluenceReport`
  - `ConfluenceReportGenerator`
  - `SP500ConfluenceAnalysis`
  - `LevelConfidenceCalculator`
- Tests:
  - Deterministic unit tests for score normalization and level confidence math.
  - Regression fixture test using ossified dataset.
- Acceptance criteria:
  - Report generates from live Yahoo daily data.
  - Includes all required sections and confidence breakdown fields.
  - Exports at least one chart image.

### Phase 2: Independence-Aware Confluence
- Add family taxonomy and decorrelation layer.
- Add rolling correlation estimator for family outputs.
- Add family-cap and redundancy-penalty configuration.
- Acceptance criteria:
  - Correlated-family stress test shows reduced overconfidence vs raw stacking.

### Phase 3: Calibrated Probabilities (1M/3M)
- Build walk-forward training/evaluation pipeline.
- Persist fold predictions and fit calibrators.
- Output calibrated `P(up/down/range)` with reliability metadata.
- Acceptance criteria:
  - Brier score and ECE reported for both horizons.
  - Reliability curves included in report artifacts.

### Phase 4: Confidence Decomposition
- Add component-level confidence calculators.
- Wire final confidence blend and expose all components.
- Add drift monitoring thresholds for calibration and regime instability.
- Acceptance criteria:
  - Final confidence never emitted without component traceability.
  - Drift warnings are emitted when metrics breach thresholds.

## Engineering Checklist
- [ ] Create confluence analysis package in `ta4j-examples`.
- [ ] Implement report DTOs and serialization.
- [ ] Implement pillar calculators and structured evidence output.
- [ ] Implement level confidence calculator.
- [ ] Add chart generation for confluence overlays.
- [ ] Add unit tests for scoring and level confidence.
- [ ] Add regression test fixture for deterministic output.
- [ ] Implement decorrelation/family-cap logic.
- [ ] Implement walk-forward calibration pipeline.
- [ ] Implement confidence decomposition and drift warnings.
- [ ] Add user and agent usage docs.

## Validation Plan
- Mandatory full build gate at completion: `scripts/run-full-build-quiet.sh`
- During development:
  - targeted tests for new packages
  - fixture-based deterministic tests
  - report schema validation tests

## Risks and Mitigations
- Risk: confidence interpreted as certainty.
  - Mitigation: expose decomposition + calibration diagnostics in every report.
- Risk: correlation leakage inflates confluence.
  - Mitigation: family caps + decorrelation penalty + ablation tests.
- Risk: regime shifts degrade calibration.
  - Mitigation: rolling recalibration, drift alarms, fallback confidence floor.
- Risk: data-source gaps and stale bars.
  - Mitigation: explicit dataConfidence penalties and stale-data guards.

## Success Metrics
- 1M and 3M Brier score improvement vs uncalibrated baseline.
- ECE reduction vs uncalibrated baseline.
- Better directional hit rate in high-confidence buckets.
- Reduced false positives in high-correlation indicator conditions.

## Open Questions
- What minimum backtest history is required for stable calibration per horizon?
- Should `range` class thresholds be volatility-adaptive by regime cluster?
- Which external feeds are approved for macro/intermarket pillar in later phases?
- Should Phase 1 output live in `ta4j-core` immediately or remain in `ta4j-examples` until stabilized?
