# PRD: Generic Walk-Forward Tuning Framework (Elliott Wave as Inaugural Use Case)

## 1. Document Metadata
- Status: Proposed
- Scope: Design only (no production code in this phase)
- Primary target: `ta4j-core` reusable walk-forward framework
- First domain adapter: Elliott Wave analysis runner
- Branch context: `feature/ew-multi-timeframe-analysis`

## 2. Background and Problem Statement
`ElliottWaveAnalysisRunner` emits ranked scenarios with confidence/probability signals, but we do not yet have a deterministic, leakage-safe, library-grade walk-forward framework to evaluate calibration quality over time.

The key product need is broader than Elliott Wave:
1. Create a generic walk-forward engine reusable for analysis runners, strategy outputs, and indicator-derived forecasts.
2. Use Elliott Wave as the first adapter that exercises this framework end-to-end.

## 3. Product Goals
1. Build a deterministic walk-forward engine in `ta4j-core`.
2. Evaluate ranked predictions at fixed horizons without lookahead leakage.
3. Tune coarse, global configuration with fold-stability guardrails.
4. Improve probability and confidence calibration out-of-sample.
5. Keep APIs framework-oriented and reusable across domains.

## 4. Non-Goals
1. Strategy optimization as the primary objective in this phase.
2. Per-asset-family hyperparameter trees.
3. Online/live retraining.
4. Designing Elliott-only infrastructure in core.

## 5. Locked Decisions
1. Fixed-horizon evaluation for MVP.
2. Primary optimization horizon: `H=60` bars.
3. Secondary reporting horizons: `H=30` and `H=150` bars.
4. Primary ranking depth for optimization: `k=3`.
5. Adjacent reported depths: `k=1` and `k=5`.
6. One global configuration (no per-asset-family forks).
7. Calibration default: Platt scaling; isotonic only as gated challenger.

## 6. Reuse-First Inventory (Mandatory Before New API)

| Existing Asset | Current Role | Reuse/Extension Plan | New Class Needed? |
|---|---|---|---|
| `org.ta4j.core.analysis.AnalysisRunner<C,R>` | One-shot analysis execution contract | Use directly as prediction producer in walk-forward loop | No |
| `org.ta4j.core.analysis.SeriesSelector<C>` | Series window/transform selection | Use directly for prefix-window and context-driven series selection at decision index `t` | No |
| `org.ta4j.core.BarSeries#getSubSeries(int, int)` | Leakage-safe slicing primitive | Use directly to build train/predict/eval windows | No |
| `org.ta4j.core.backtest.ProgressCompletion` | Progress callback patterns | Reuse callback model for fold/candidate progress reporting | No |
| `org.ta4j.core.backtest.BacktestRuntimeReport` pattern | Runtime stats record style | Mirror record style for walk-forward runtime summary | Likely Yes (new record, same pattern) |
| `org.ta4j.core.backtest.BacktestExecutor` top-k and batching patterns | Efficient ranking and memory management | Reuse algorithmic approach (heap top-k, batched candidate eval) in tuner execution | No direct class reuse; yes pattern reuse |
| `ta4jexamples.walkforward.WalkForward` split helpers | Example-only split logic | Promote logic concept to core splitter abstraction with tests | Yes (core splitter API), example helper remains demo |
| `org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner` | Domain analysis runner | Plug into generic framework via adapter interfaces | No |
| `org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult` | Ranked scenario output | Reuse directly as source object for EW prediction extraction | No |

Guiding rule:
- Every new type must document which existing type it extends or why no existing type can represent the capability.

## 7. Architectural Direction

### 7.1 Package Strategy
1. Add a new package: `org.ta4j.core.walkforward` for reusable orchestration/data contracts.
2. Keep domain adapters close to domain packages:
   - `org.ta4j.core.indicators.elliott.walkforward` for EW-specific mapping/labeling.
3. Keep public API minimal:
   - Only stable framework interfaces/records are public.
   - Helper internals (`*Internal`, accumulators, serializers) stay package-private.

### 7.2 Framework Model
1. A generic engine iterates over splits and decision indices.
2. At each `t`, engine generates a prefix view and invokes a prediction provider.
3. Engine evaluates realized outcomes on `[t+1, t+H]`.
4. Metrics aggregate by fold and globally.
5. Tuner ranks candidate configurations using stability-constrained objective.

## 8. Proposed Class/API Design (Library-Centric)

## 8.1 Core Walk-Forward Package (`org.ta4j.core.walkforward`)

### A) Split and Horizon Contracts
1. `WalkForwardConfig` (record)
- Purpose: immutable run configuration.
- Key fields:
  - `int primaryHorizonBars` (default 60)
  - `List<Integer> reportingHorizons` (default 30, 150)
  - `int optimizationTopK` (default 3)
  - `List<Integer> reportingTopKs` (default 1, 5)
  - split geometry (`minTrainBars`, `testBars`, `stepBars`, `purgeBars`, `embargoBars`)
  - reproducibility (`long seed`)
- Reuse basis: mirrors immutable record style used in backtest runtime contracts.

2. `WalkForwardSplit` (record)
- Purpose: one fold’s index boundaries and metadata.
- Fields:
  - `String foldId`
  - `int trainStart`, `int trainEnd`
  - `int testStart`, `int testEnd`
  - `int purgeBars`, `int embargoBars`
- Reuse basis: relies on `BarSeries` index semantics and `getSubSeries`.

3. `WalkForwardSplitter` (interface)
- Purpose: pluggable split geometry strategy.
- Signature sketch:
  - `List<WalkForwardSplit> split(BarSeries series, WalkForwardConfig config)`
- Reuse basis: abstracts and formalizes logic currently shown in `ta4jexamples.walkforward.WalkForward`.

4. `AnchoredExpandingWalkForwardSplitter` (class)
- Purpose: default splitter implementing anchored expanding train + rolling test.
- Reuse basis: implementation borrows split indexing concept from example `WalkForward` and uses core `BarSeries`.

### B) Prediction and Observation Contracts
5. `RankedPrediction<P>` (record)
- Purpose: ranked candidate prediction with calibration fields.
- Fields:
  - `String predictionId`
  - `int rank`
  - `double probability`
  - `double confidence`
  - `P payload`
- Reuse basis: generalizes ranked scenario shape already present in `ElliottWaveAnalysisResult.BaseScenarioAssessment`.

6. `PredictionSnapshot<P>` (record)
- Purpose: prefix-only prediction capture at decision index `t`.
- Fields:
  - `String foldId`
  - `int decisionIndex`
  - `List<RankedPrediction<P>> topPredictions`
  - `Map<String, String> metadata` (optional audit tags)
- Reuse basis: immutable record pattern; no duplication of Elliott-specific fields.

7. `WalkForwardObservation<P, O>` (record)
- Purpose: joined unit for metric calculation.
- Fields:
  - `PredictionSnapshot<P> snapshot`
  - `O realizedOutcome`
  - `int horizonBars`
- Reuse basis: generic join model independent of domain.

### C) Engine Extension Points
8. `PredictionProvider<C, P>` (functional interface)
- Purpose: produce ranked predictions for one prefix at index `t`.
- Signature sketch:
  - `List<RankedPrediction<P>> predict(BarSeries fullSeries, int decisionIndex, C context)`
- Reuse basis:
  - Default adapter built from existing `AnalysisRunner<C,R>` + `SeriesSelector<C>` + extractor.

9. `OutcomeLabeler<P, O>` (functional interface)
- Purpose: compute realized outcome for a prediction under fixed horizon.
- Signature sketch:
  - `O label(BarSeries fullSeries, int decisionIndex, int horizonBars, RankedPrediction<P> prediction)`
- Reuse basis: generic domain label contract; keeps core engine agnostic.

10. `WalkForwardMetric<P, O>` (interface)
- Purpose: compute one metric over observations.
- Signature sketch:
  - `String name()`
  - `double compute(List<WalkForwardObservation<P, O>> observations)`
- Reuse basis:
  - conceptually similar to `AnalysisCriterion`/`ReportGenerator`, but domain-neutral for forecast evaluation.

11. `WalkForwardEngine<C, P, O>` (class)
- Purpose: deterministic orchestration of splits, snapshots, outcomes, and metrics.
- Constructor dependencies:
  - `WalkForwardSplitter`
  - `PredictionProvider<C,P>`
  - `OutcomeLabeler<P,O>`
  - `List<WalkForwardMetric<P,O>>`
  - `Consumer<Integer>` progress callback (reuse `ProgressCompletion` pattern)
- Output: `WalkForwardRunResult<P,O>`.

12. `WalkForwardRunResult<P, O>` (record)
- Purpose: complete run artifact bundle (splits, observations, metrics, runtime).
- Reuse basis: parallels `BacktestExecutionResult` outcome packaging style.

13. `WalkForwardRuntimeReport` (record)
- Purpose: runtime summary for folds and candidates.
- Reuse basis: same shape conventions as `BacktestRuntimeReport`.

### D) Optional Tuning Layer (Generic)
14. `WalkForwardCandidate<C>` (record)
- Purpose: one candidate config/context under evaluation.

15. `WalkForwardObjective` (interface)
- Purpose: combine metric outputs into scalar objective with constraints.

16. `WalkForwardTuner<C, P, O>` (class)
- Purpose: evaluate candidates with batching/top-k ranking patterns borrowed from `BacktestExecutor`.

17. `WalkForwardLeaderboard<C>` (record)
- Purpose: deterministic ranking of candidate outcomes with fold variance and guardrail flags.

## 8.2 Elliott Wave Adapter Package (`org.ta4j.core.indicators.elliott.walkforward`)

1. `ElliottWavePredictionProvider` (class)
- Implements `PredictionProvider<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment>`.
- Reuses:
  - `ElliottWaveAnalysisRunner`
  - `ElliottWaveAnalysisResult.rankedBaseScenarios()`
- Responsibility:
  - generate prefix-only EW result at index `t`
  - map top scenarios to `RankedPrediction` preserving confidence/probability/composite rank semantics.

2. `ElliottWaveOutcome` (record)
- Domain realized label for fixed horizon.
- Fields (coarse and calibration-aligned):
  - `EventOutcome` (`TARGET_FIRST`, `INVALIDATION_FIRST`, `NEITHER`)
  - `PhaseProgression` (coarse enum)
  - optional diagnostic count agreement bucket.

3. `ElliottWaveOutcomeLabeler` (class)
- Implements `OutcomeLabeler<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome>`.
- Reuses scenario targets/invalidation already present in EW scenario payload.

4. `ElliottWaveWalkForwardContext` (record)
- Context passed to provider:
  - EW runner config
  - base degree
  - optional metadata tags.
- Reuse: does not duplicate runner builder; references it.

No duplicate EW result classes:
- `ta4j-core` `ElliottWaveAnalysisResult` remains canonical.
- Examples consume core type directly.

## 9. Data Flow (Concrete)
1. Build splits via `WalkForwardSplitter`.
2. For each split and test index `t`:
   - create prefix view using `SeriesSelector` + `getSubSeries`.
   - run `PredictionProvider`.
   - retain top-5 predictions (supports reporting for `k=1/3/5`).
3. For each configured horizon (`60` primary, `30` and `150` report):
   - label outcomes via `OutcomeLabeler` on `[t+1, t+H]`.
4. Build `WalkForwardObservation` rows.
5. Compute metrics and objective.
6. Emit artifacts and leaderboard.

## 10. Metric Set and Objective

### 10.1 Primary Metrics (optimize on `H=60`, `k=3`)
1. Event outcome F1.
2. Phase progression agreement.
3. Probability calibration:
   - Brier score
   - Log loss
   - ECE
4. Ranking quality:
   - top-k hit rate (`k=3`)
   - NDCG (`k=3`)

### 10.2 Secondary Diagnostics (report only)
1. Metrics above for `H=30`, `H=150`.
2. Ranking diagnostics at `k=1`, `k=5`.
3. Exact/near wave-count agreement.
4. Target/invalidation distance and timing errors.

### 10.3 Objective Template
`Objective = a*EventF1 + b*PhaseAgreement + c*NDCG - d*Brier - e*ECE - f*FoldVariance`

Constraint:
- candidates violating calibration floor or fold-variance ceiling are disqualified.

## 11. Calibration Design
1. Train Platt scaler on training folds only.
2. Apply to validation/test fold predictions.
3. Report reliability bins and residual tables.
4. Isotonic challenger allowed only when:
   - minimum samples met
   - OOS ECE improves across folds
   - fold variance does not worsen materially.

## 12. Overfitting and Leakage Controls
1. Prefix-only prediction at every `t`.
2. Split-level purge and embargo.
3. Global config only (no asset-family forks).
4. Fold variance penalty.
5. Untouched holdout for final sign-off.
6. Deterministic seeds and config hashing.

## 13. Implementation Phases with Class-Level Scope

### M0: Baseline and Geometry Lock
1. Freeze dataset/date manifests and baseline EW config.
2. Lock fold geometry constants and defaults.
3. Finalize `WalkForwardConfig` field set.

### M1: Core Contracts
1. Add records/interfaces:
   - `WalkForwardConfig`, `WalkForwardSplit`, `RankedPrediction`, `PredictionSnapshot`, `WalkForwardObservation`.
2. Add interfaces:
   - `WalkForwardSplitter`, `PredictionProvider`, `OutcomeLabeler`, `WalkForwardMetric`.
3. Add `WalkForwardRunResult` and `WalkForwardRuntimeReport`.

### M2: Engine
1. Implement `AnchoredExpandingWalkForwardSplitter`.
2. Implement `WalkForwardEngine` deterministic loop.
3. Add leakage audit traces and validation hooks.

### M3: Metrics
1. Implement generic metrics framework and baseline metric implementations.
2. Implement composite objective and guardrail evaluator.

### M4: Tuning Layer
1. Implement `WalkForwardCandidate`, `WalkForwardObjective`, `WalkForwardTuner`, `WalkForwardLeaderboard`.
2. Use batched/top-k ranking strategies inspired by `BacktestExecutor` patterns.

### M5: Elliott Adapter
1. Implement `ElliottWavePredictionProvider`.
2. Implement `ElliottWaveOutcome` + `ElliottWaveOutcomeLabeler`.
3. Wire EW context record and baseline run profile.

### M6: Calibration and Holdout
1. Add Platt calibrator integration in tuning flow.
2. Add isotonic challenger behind gates.
3. Run final holdout validation and sign-off report.

## 14. Testing Strategy (Class-Aware)

### 14.1 Unit
1. `AnchoredExpandingWalkForwardSplitterTest`:
   - chronological ordering
   - purge/embargo boundaries
   - holdout isolation.
2. `WalkForwardEngineTest`:
   - prefix-only data visibility at each `t`
   - exact horizon slicing `[t+1, t+H]`
   - deterministic replay with same seed.
3. `WalkForwardMetric*Test`:
   - controlled fixtures for Brier/LogLoss/ECE/NDCG/top-k hit.
4. `ElliottWaveOutcomeLabelerTest`:
   - deterministic event labeling (`TARGET_FIRST`, `INVALIDATION_FIRST`, `NEITHER`).

### 14.2 Integration
1. Single-fold EW smoke run.
2. Multi-fold replay with byte-stable artifacts.
3. Baseline-vs-candidate leaderboard flow.

### 14.3 Leakage and Integrity
1. Assert no prediction reads beyond `t`.
2. Assert label windows never contaminate training windows.
3. Assert holdout is never touched during tuning.

## 15. Acceptance Criteria
1. Framework runs deterministically with reusable generic API.
2. EW adapter runs without domain-specific hooks in core engine.
3. No leakage violations in automated audits.
4. Champion improves calibration OOS versus baseline.
5. Gains are fold-stable and holdout-safe.

## 16. Open Questions
1. Final numeric fold geometry (`minTrainBars`, `testBars`, `stepBars`, `purgeBars`, `embargoBars`).
2. Whether `WalkForwardMetric` should remain a dedicated interface or align with an expanded generic reporting abstraction in a future iteration.

## 17. Immediate Next Step
After PRD sign-off, deliver M1 contracts and M2 engine skeleton in `ta4j-core`, with EW adapter limited to provider/labeler classes, keeping the core walk-forward API domain-neutral.
