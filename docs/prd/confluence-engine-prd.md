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
- Keep the report architecture pluggable with defaults, not hardcoded to a single indicator stack.

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

## Detailed Capability Sections (Requested Additions)

### 1) Regime Models: Hidden-State Regime Detection
#### Current known state
- The toolkit has trend/volatility proxies (`ADX`, `ATR`, `Chop`, moving-average slope, Kalman filter), but no explicit hidden-state regime model.
- Existing decisions often infer regime from thresholds (for example ADX>20), which is brittle during transitions.

#### Target design
- Introduce a regime engine with explicit latent states and state probabilities.
- Initial state taxonomy (daily):
  - `TREND_UP_LOW_VOL`
  - `TREND_UP_HIGH_VOL`
  - `TREND_DOWN_LOW_VOL`
  - `TREND_DOWN_HIGH_VOL`
  - `RANGE_LOW_VOL`
  - `RANGE_HIGH_VOL`
- Core outputs:
  - `regimeState`
  - `stateProbabilities[]`
  - `regimeConfidence`
  - `transitionRisk` (probability of state change over next N bars)

#### Implementation steps
1. Add `RegimeFeatureVector` and `RegimeSnapshot` domain objects under `ta4j-examples` first.
2. Build deterministic baseline classifier from existing indicators (for immediate use and fallback).
3. Implement HMM module (Gaussian emissions, EM training, Viterbi decode) in a dedicated package.
4. Train with walk-forward windows and emit out-of-sample state probabilities.
5. Add transition-stability features (`statePersistence`, entropy of state distribution).
6. Feed regime outputs into confluence weighting and confidence decomposition.
7. Promote stable APIs to `ta4j-core` after validation.

### 2) Probability Calibration Tools: Isotonic/Platt + Reliability
#### Current known state
- PRD already specifies isotonic and Platt methods plus Brier/ECE/log loss targets.
- No reusable calibration utility layer exists yet.

#### Target design
- Add a calibration pipeline that transforms raw model probabilities into calibrated probabilities by horizon and class.
- Multi-class handling:
  - one-vs-rest calibrators (`up/down/range`) with post-normalization
  - fallback to binary directional calibration + range residual
- Mandatory artifacts:
  - reliability diagrams
  - calibration tables by probability bucket
  - metric history by training window

#### Implementation steps
1. Add `CalibrationDataset`, `Calibrator`, `IsotonicCalibrator`, `PlattCalibrator`.
2. Persist out-of-sample raw predictions and realized labels from walk-forward runs.
3. Fit calibrators per horizon (`1M`, `3M`) and per class.
4. Generate reliability diagrams as chart artifacts in `temp/charts` and optional JSON summaries.
5. Enforce calibration metadata in `ConfluenceReport` schema.
6. Add guardrails: if calibration sample is insufficient, downgrade `calibrationConfidence`.

### 3) Robust Anti-Overfitting Statistics
#### Current known state
- Current workflow emphasizes walk-forward and criterion-based evaluation, but does not include formal anti-overfitting diagnostics like DSR/PBO/SPA.

#### Target design
- Add a validation statistics suite for strategy/model-selection robustness:
  - Deflated Sharpe Ratio (DSR)
  - Probability of Backtest Overfitting (PBO, CSCV style)
  - SPA / Reality Check style significance testing (bootstrap-based)
- Diagnostics must be computed for candidate model families and reported with confidence penalties.

#### Implementation steps
1. Add `OverfittingDiagnostics` module with DSR and PBO calculators.
2. Implement combinatorially symmetric cross-validation utility for PBO.
3. Add block-bootstrap significance test utilities for SPA/Reality Check style p-values.
4. Integrate diagnostics into model selection and calibration reports.
5. Penalize `modelConfidence` when overfitting diagnostics exceed configured thresholds.
6. Add regression tests using synthetic data with known overfit behavior.

### 4) Breadth and Internals Feeds
#### Current known state
- Current analysis is mostly single-instrument OHLCV.
- Breadth/internals are not currently first-class features in the report.

#### Target design
- Add breadth feature ingestion and derived metrics:
  - `%SPX above 50DMA`
  - `%SPX above 200DMA`
  - Advance/decline line and oscillator
  - New highs minus new lows (rolling windows)
  - Sector breadth composite (for example GICS sector ETF proxies when constituent feed unavailable)
- Provide `breadthConfidence` and feed-quality diagnostics.

#### Implementation steps
1. Define `BreadthDataSource` interface and cache format.
2. Implement initial adapters for daily breadth snapshots (constituent-level or proxy mode).
3. Compute standardized breadth features and z-scores.
4. Add breadth pillar contributions to confluence engine.
5. Add stale-data handling and penalties in `dataConfidence`.
6. Add fixture datasets for deterministic unit tests.

### 5) Volatility Structure Feeds
#### Current known state
- Realized volatility proxies exist (`ATR`, Bollinger width), but implied-vol structure is not integrated.

#### Target design
- Add volatility-structure feature set:
  - VIX term structure proxies (`VIX`, `VIX3M`, contango/backwardation spread)
  - Realized vs implied spread (`IV - RV`)
  - Skew proxies (for example CBOE SKEW index or put-skew surrogates)
- Output:
  - `volRegimeState`
  - `volRiskFlag`
  - feature contributions to Volatility pillar

#### Implementation steps
1. Define `VolStructureDataSource` with symbol map and fallback policy.
2. Add daily ingestion and normalization pipeline for implied-vol metrics.
3. Compute derived features (term slope, IV-RV spread percentile, skew percentile).
4. Integrate into volatility pillar scoring and regime confidence.
5. Add alert thresholds for stress regimes (for example inverted term structure).
6. Backtest impact of vol-structure features on directional and range forecasts.

### 6) Intermarket Layer
#### Current known state
- Macro/intermarket was explicitly marked placeholder in Phase 1.

#### Target design
- Build cross-asset context features:
  - Rates: UST 2Y/10Y levels and slope
  - Credit: IG/HY spread proxies
  - FX: broad USD strength
  - Commodities: oil and gold trend/volatility states
  - Composite risk-on/off index from standardized components
- Output:
  - `intermarketScore`
  - `riskOnOffState`
  - confidence impact and explanations

#### Implementation steps
1. Define `IntermarketSnapshot` schema and source adapters.
2. Build daily feature engineering pipeline with robust missing-data fallback.
3. Add composite risk-on/off model with transparent weights.
4. Integrate intermarket score as part of Macro/Intermarket pillar.
5. Add feature-attribution reporting to explain directional impact.
6. Add walk-forward validation to measure incremental lift over no-intermarket baseline.

### 7) Options Positioning Proxies
#### Current known state
- No dedicated options positioning metrics are currently integrated.

#### Target design
- Add proxies for positioning and convexity pressure:
  - put/call ratios (equity/index/total where available)
  - estimated dealer gamma regime proxy (positive/negative gamma state)
  - optional skew-related positioning pressure
- Output:
  - `optionsPositioningScore`
  - `gammaRegime` (`POSITIVE`, `NEGATIVE`, `NEUTRAL`)
  - data-quality flags when only proxy approximations are available

#### Implementation steps
1. Define `OptionsPositioningDataSource` and daily cache format.
2. Implement put/call ingestion and normalization pipeline.
3. Add gamma proxy model with configurable fallback hierarchy.
4. Integrate into Participation or Macro/Intermarket pillar (configurable).
5. Add confidence penalties when raw options depth is unavailable.
6. Validate whether options features improve regime transitions and tail-risk detection.

### 8) Six-Pillar Confluence Engine
#### Current known state
- PRD and quick-win scope define pillars and starter indicators.

#### Target design
- Final pillar framework:
  - Structure
  - Trend
  - Momentum
  - Volatility
  - Participation
  - Macro/Intermarket
- Each pillar emits:
  - score
  - directional vote
  - uncertainty
  - feature-level attribution

#### Implementation steps
1. Implement `PillarCalculator` interface and one implementation per pillar.
2. Build shared normalization utilities (z-score, percentile rank, bounded transforms).
3. Implement attribution registry for feature contribution auditing.
4. Add pillar-specific reliability diagnostics (coverage, data completeness, stability).
5. Feed pillar outputs into raw and decorrelated confluence aggregators.
6. Add configurable weighting presets for conservative/balanced/aggressive modes.

### 9) Indicator Family Grouping, Caps, Correlation Discovery Engine
#### Current known state
- PRD currently states family grouping and penalties but not the full discovery engine design.

#### Target design
- Define indicator families (example):
  - Trend-following
  - Momentum oscillators
  - Volatility
  - Volume/participation
  - Structure/levels
  - Intermarket/options
- Correlation discovery:
  - rolling Spearman and rank-IC style dependence
  - optional distance correlation for non-linear dependence
- Outputs:
  - dynamic family caps
  - redundancy penalty matrix
  - effective feature count

#### Implementation steps
1. Add `IndicatorFamilyCatalog` with explicit mapping and versioning.
2. Build `CorrelationDiscoveryEngine` on rolling feature histories.
3. Compute family-level dependence and derive dynamic caps.
4. Apply penalties before final confluence score aggregation.
5. Add diagnostics endpoint/report section showing penalized features.
6. Add ablation tests to verify reduced overconfidence under redundant indicators.

### 10) Enhanced Confidence Model
#### Current known state
- Current decomposition formula is defined, but advanced confidence adaptation is not.

#### Target design
- Extend confidence to include:
  - disagreement/conflict penalty across pillars
  - forecast horizon-specific uncertainty scaling
  - confidence bands (point estimate + interval)
- Final confidence should be explainable and reproducible from component traces.

#### Implementation steps
1. Implement `ConfidenceModel` with configurable component weights and penalties.
2. Add disagreement metrics (entropy of pillar votes, directional conflict index).
3. Add horizon-specific transforms for `1M` vs `3M`.
4. Emit confidence intervals from bootstrap/resampling diagnostics.
5. Add drift monitors to reduce confidence under calibration degradation.
6. Add threshold policies for report labels (`LOW`, `MEDIUM`, `HIGH`).

### 11) Enhanced Support/Resistance Modeling
#### Current known state
- Toolkit already includes trendline, cluster, and bounce indicators.
- Current quick-win confidence is mostly segment-score based.

#### Target design
- Multi-source level fusion:
  - trendline levels
  - price-cluster levels
  - bounce-count levels
  - pivot levels and Elliott invalidation/targets
  - optional anchored VWAP anchors
- Level object should include:
  - hold probability
  - breach probability
  - expected time-to-test band
  - supporting evidence set

#### Implementation steps
1. Add `LevelCandidate` and `LevelFusionEngine` abstractions.
2. Generate candidates from each level family and deduplicate by volatility-scaled distance.
3. Score and rank levels with recency, touches, violations, confluence, and regime context.
4. Add hold/breach probability estimation from historical analog windows.
5. Render top levels on chart with confidence labels.
6. Add regression tests for level stability under small data perturbations.

### 12) Better Validation Standards
#### Current known state
- Validation plan currently includes walk-forward and calibration metrics.

#### Target design
- Expand standards to include:
  - purged + embargoed walk-forward CV
  - nested model-selection loops
  - realistic frictions (transaction cost and slippage assumptions)
  - stability testing by regime bucket
  - anti-overfitting diagnostics (DSR/PBO/SPA)
  - confidence calibration quality gates

#### Implementation steps
1. Define `ValidationProtocol` with mandatory gates and reproducible seeds.
2. Build reusable splitters for purged/embargoed CV.
3. Add benchmark suite (naive, buy/hold, simple trend baseline).
4. Add statistical significance and calibration pass/fail thresholds.
5. Add automated report card artifact per run (`validation-summary.json` + charts).
6. Block promotion of model profiles that fail minimum validation gates.

## Implementation Plan

### Phase 1: Quick Win (`ConfluenceReport` v1)
- Deliverable: S&P 500 daily confluence report + chart export.
- Code location (core contract): `ta4j-core/src/main/java/org/ta4j/core/analysis/confluence/`
- Adapter examples (data + visualization): `ta4j-examples/src/main/java/ta4jexamples/charting/confluence/`
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
- [x] Create confluence analysis package in `ta4j-core` for core report contracts and scoring interfaces.
- [x] Add adapter package in `ta4j-examples` for datasource wiring, charting, and runnable analysis flows.
- [x] Implement report DTOs and serialization.
- [x] Implement pillar calculators and structured evidence output.
- [x] Implement level confidence calculator.
- [x] Add chart generation for confluence overlays.
- [x] Add unit tests for scoring and level confidence.
- [x] Add regression test fixture for deterministic output.
- [x] Implement decorrelation/family-cap logic.
- [ ] Implement walk-forward calibration pipeline.
- [ ] Implement confidence decomposition and drift warnings.
- [ ] Add user and agent usage docs.

## Cross-Repo Execution Plan (ta4j + CF)
- Use coordinated worktrees and a shared feature branch name for cross-repo implementation.
- Keep `ta4j-core` generic and pluggable; place opinionated feed/provider integrations in adapter layers (`ta4j-examples` and/or `CF`).
- Define portability contract:
  - core types and interfaces in `ta4j-core`
  - market/feed plugins in external repos
  - neutral defaults in `ta4j-examples` for demonstration only
- Track compatibility with versioned schema so CF-side adapters can evolve without breaking core APIs.

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

## Resolved Decisions (Integrated from Inline Review)
- Minimum backtest history for stable calibration:
  - Baseline recommendation: at least 10 years of daily history where available.
  - Operational minimum per horizon/class/regime bucket: at least 120 effective out-of-sample observations before trusting calibration.
  - If below minimum, emit calibration warning and cap `calibrationConfidence`.
- `range` thresholds are volatility-adaptive by regime cluster: approved.
  - Implement regime-conditional thresholding as default behavior for label generation.
- External feed/provider policy:
  - `ta4j-core` remains provider-agnostic and non-opinionated.
  - `ConfluenceReport` and scoring interfaces must be pluggable with neutral defaults.
  - Opinionated provider choices live in adapter layers (`ta4j-examples` and/or `CF`).
- Phase 1 location:
  - Core `ConfluenceReport` contract implemented in `ta4j-core`.
  - Cross-repo execution using worktrees for `ta4j` and `CF`.
  - Additional components may be distributed across repos while keeping core API stable.
