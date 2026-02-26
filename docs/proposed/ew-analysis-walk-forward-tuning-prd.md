# PRD: Elliott Wave Analysis Runner Walk-Forward Tuning System

## Document Metadata
- Status: Proposed
- Scope: Design only (no production code in this phase)
- Target component: Elliott Wave analysis runner and scoring pipeline
- Branch context: feature/ew-multi-timeframe-analysis

## 1. Background and Problem Statement
Our Elliott Wave (EW) analysis currently emits scenario candidates with assigned wave counts, confidence, and probability scores, but we do not yet have a rigorous, repeatable framework to evaluate whether those predictions are calibrated and useful when walked forward over time.

The core gap:
- We can generate scenarios at a point in time.
- We cannot yet systematically answer how close those scenario predictions were to realized structure and outcomes.

This PRD proposes a design for an offline, leakage-safe walk-forward tuning system focused on improving probability and confidence calibration while maintaining coarse structural robustness.

## 2. Product Goals
1. Build a deterministic walk-forward evaluation harness for EW scenarios.
2. Tune runner/scoring parameters using only past data at each decision point.
3. Improve confidence and probability calibration quality out of sample.
4. Keep tuning coarse and generalizable (global model), explicitly avoiding per-asset overfitting.

## 3. Non-Goals
1. Building or optimizing a trading strategy in this phase.
2. Maximizing exact per-asset wave-count accuracy.
3. Per-asset-family model specialization.
4. Online/live auto-retraining.

## 4. Confirmed Decisions
1. Horizon strategy: fixed horizon evaluation.
2. Primary optimization horizon: `H = 60` bars.
3. Additional horizon reporting: `H = 30` and `H = 150` bars (diagnostic only).
4. Primary objective style: phase/event-first scoring.
5. Ranking depth policy: optimize with `top-k = 3`; report adjacent `k = 1` and `k = 5`.
6. Tuning granularity: one global EW configuration (no per-asset-family configs).
7. Calibration default: Platt scaling first; isotonic only as challenger under data sufficiency gates.

## 5. Decision Rationale and Tradeoffs

### 5.1 Fixed Horizon vs Event-Terminated Horizon

#### Fixed horizon (chosen for v1)
Definition:
- For each prediction at index `t`, evaluate realized outcomes over `[t+1, t+H]` where `H` is fixed in bars.

Pros:
1. Comparable samples across all predictions and assets.
2. Simpler statistics and fewer censoring edge cases.
3. Lower complexity for fold-level aggregation and confidence intervals.
4. Harder to overfit to variable-duration scenario lifecycles.

Cons:
1. Slow-developing scenarios can be unfairly scored as misses.
2. Horizon choice can bias metrics toward short- or mid-term behavior.
3. Late target/invalidation events beyond `H` are ignored.

#### Event-terminated horizon (deferred)
Definition:
- Evaluate each prediction until scenario resolution (target, invalidation, or other terminal event), subject to a max cap.

Pros:
1. Better semantic alignment with scenario lifecycle.
2. Captures complete resolution paths.

Cons:
1. Variable sample durations reduce direct comparability.
2. More censoring and survivorship-bias handling complexity.
3. More tuning surface area and higher overfit risk.

Decision:
- Use fixed horizon for MVP. Revisit event-terminated evaluation only after calibration stability is demonstrated.

### 5.2 Exact Wave-Count Labels vs Phase/Event-First Scoring

#### Exact wave-count-centric scoring
Pros:
1. Direct structural accountability.
2. Helpful diagnostic on count precision.

Cons:
1. EW label ambiguity introduces high label noise.
2. Brittle objective can cause unstable parameter search.
3. Higher risk of chasing narrow in-sample structures.

#### Phase/event-first scoring (chosen as primary)
Pros:
1. More robust to count-label ambiguity.
2. Better aligned with confidence/probability calibration goals.
3. Lower variance objective, improving generalization.

Cons:
1. Less granular about precise count assignment errors.
2. May miss nuanced structural weaknesses if count diagnostics are ignored.

Decision:
- Use phase/event-first as primary objective.
- Keep exact wave-count scoring as secondary diagnostics only.

### 5.3 Global vs Per-Asset-Family Configs

#### Global configuration (chosen)
Pros:
1. Strong regularization pressure across markets/regimes.
2. Reduced complexity and governance burden.
3. Better portability and maintainability.
4. Lower overfit risk.

Cons:
1. Leaves some asset-specific edge untapped.
2. Can underfit niche market microstructures.

#### Per-asset-family configuration (deferred/rejected for now)
Pros:
1. Potentially higher in-sample fit.

Cons:
1. Config sprawl and operational complexity.
2. Much higher overfit risk.
3. Weak transferability and brittle long-term behavior.

Decision:
- Single global tuning target for v1 and v2.
- Per-family metrics are monitoring outputs only, not separate optimization targets.

### 5.4 Platt Scaling vs Isotonic Calibration

#### Platt scaling (chosen default)
Pros:
1. Low-variance and stable with moderate sample sizes.
2. Smooth probability curves.
3. Simpler deployment and monitoring.

Cons:
1. Can underfit non-sigmoid miscalibration shapes.

#### Isotonic regression (challenger only)
Pros:
1. Flexible monotonic mapping.
2. Can better fit complex miscalibration when data is large.

Cons:
1. Higher variance and stepwise behavior.
2. More prone to overfitting on sparse bins/folds.

Decision:
- Default to Platt scaling.
- Allow isotonic challenger only when minimum sample and stability gates are met.

## 6. System Design Overview

### 6.1 Core Subsystems
1. Dataset and fold manager.
2. Prediction snapshot generator (runner adapter).
3. Realized outcome builder.
4. Metric engine.
5. Tuning/search controller.
6. Calibration module.
7. Result registry and reporting.

### 6.2 Data Flow
1. Ingest historical bars and segment into walk-forward folds.
2. For each candidate config and decision index `t`:
   - Run EW analysis on bars `[0..t]` only.
   - Store top 5 scenarios with counts, confidence, and probabilities to support `k = 1/3/5` reporting.
3. Evaluate realized outcomes in `[t+1..t+H]`.
4. Score prediction quality and calibration.
5. Aggregate by fold and globally; select champion config.

## 7. Walk-Forward Protocol (Fixed Horizon MVP)

### 7.1 Fold Strategy
1. Anchored expanding train with rolling test windows.
2. Purge and embargo windows around split boundaries.
3. Final untouched holdout segment for final reporting.

### 7.2 Snapshot Generation
At each decision index `t`:
1. Generate EW scenarios using only prefix data `[0..t]`.
2. Capture:
   - Base scenario count/phase/direction.
   - Alternatives and rank.
   - Confidence score and probability score.
   - Predicted targets/invalidation levels.

### 7.3 Realized Label Construction
Within `[t+1..t+H]`:
1. Determine event outcomes:
   - target-first, invalidation-first, neither.
2. Determine realized phase progression proxy.
3. Build coarse realized count proxy for diagnostics.

### 7.4 Horizon Reporting Policy
1. Optimize candidate selection on `H = 60` only.
2. Report secondary diagnostics on `H = 30` and `H = 150`.
3. Secondary horizons must not override champion selection unless policy is explicitly revised in this PRD.

## 8. Metrics and Objective Function

### 8.1 Primary Metrics (optimization)
1. Event outcome accuracy and F1.
2. Phase progression agreement score.
3. Probability calibration quality:
   - Brier score
   - Log loss
   - Expected calibration error (ECE)
4. Ranking quality:
   - Top-k hit rate (primary at `k = 3`)
   - NDCG over scenario ranking (primary at `k = 3`)
   - Adjacent diagnostics reported at `k = 1` and `k = 5`

### 8.2 Secondary Diagnostics (non-primary)
1. Exact/near wave-count agreement.
2. Target and invalidation distance error.
3. Horizon-normalized time-to-event error.

### 8.3 Composite Objective (example)
`Objective = a*EventF1 + b*PhaseAgreement + c*NDCG - d*Brier - e*ECE - f*FoldVariance`

Constraint:
- Calibration quality must not regress beyond threshold while improving structural metrics.

## 9. Tuning Space (Coarse)
1. Swing extraction sensitivity and compression thresholds.
2. Scenario generation limits/window size.
3. Confidence weighting coefficients.
4. Probability mapping parameters.

Out of scope for v1:
- Fine-grained, per-asset parameter sets.

## 10. Overfitting Controls
1. Strict no-lookahead snapshot generation.
2. Purged walk-forward splits with embargo.
3. One global parameter set only.
4. Fold variance penalty in objective.
5. Final untouched holdout report.
6. Seeded reproducibility and config hashing.

## 11. Calibration Strategy

### 11.1 Default path
1. Train Platt scaler on training folds only.
2. Apply scaler to validation/test fold predictions.
3. Report reliability plots and bin-level residuals.

### 11.2 Isotonic challenger gate
Isotonic can be evaluated only if:
1. Minimum sample count per calibration segment is met.
2. Out-of-sample ECE improvement persists across folds.
3. Fold variance does not materially increase.

## 12. Outputs and Artifacts
1. Experiment manifest (dataset, folds, config hash, seed).
2. Snapshot-level prediction table.
3. Realized outcome table.
4. Fold and global metric report.
5. Calibration report and reliability bins.
6. Champion vs baseline comparison summary.

## 13. Acceptance Criteria (Design-to-Implementation)
1. Walk-forward engine can produce deterministic snapshot/evaluation records.
2. No leakage violations in audit checks.
3. Champion config improves calibration metrics out of sample versus baseline.
4. Improvement is stable across folds and not driven by a single period.
5. Holdout performance remains within predefined degradation guardrails.

## 14. Risks and Mitigations
1. EW label ambiguity noise.
   - Mitigation: phase/event-first objective and count as secondary.
2. Regime shift sensitivity.
   - Mitigation: walk-forward variance monitoring and holdout checks.
3. Objective gaming.
   - Mitigation: multi-metric constraints and calibration floors.
4. Compute cost.
   - Mitigation: coarse search grid and staged narrowing.

## 15. Implementation Phases (Future Work)
1. Phase A: Build snapshot/evaluation data model and fold orchestration.
2. Phase B: Add metric engine and baseline reporting.
3. Phase C: Add global coarse tuner and Platt calibration.
4. Phase D: Add isotonic challenger with gating.
5. Phase E: Integrate recurring champion/challenger evaluation workflow.

## 16. Open Questions
1. Final walk-forward fold geometry values (train length, test length, purge, embargo).

## 17. Immediate Next Step
After PRD approval, the first implementation deliverable should be a minimal offline evaluator that:
1. Produces snapshot predictions at each `t`.
2. Scores fixed-horizon event outcomes (primary `H = 60`).
3. Outputs calibration and phase/event agreement reports per fold.

## 18. Ordered Implementation Checklist (Execution Backlog)

### 18.1 Milestone M0: Scope Lock and Baseline Freeze
Objective:
- Freeze a reproducible baseline and remove remaining design ambiguity before implementation starts.

Checklist:
1. [ ] Encode fixed horizon defaults in configuration (`H = 60`, with diagnostics at `30` and `150`).
2. [ ] Encode ranking defaults in configuration (`k = 3`, with diagnostics at `1` and `5`).
3. [ ] Lock walk-forward fold geometry (train length, test length, purge, embargo, holdout).
4. [ ] Freeze baseline EW runner configuration for comparison.
5. [ ] Freeze datasets and date boundaries used for tuning and holdout.

Deliverables:
1. Baseline config manifest.
2. Dataset manifest with deterministic identifiers.
3. Fold definition manifest.

Exit criteria:
1. No unresolved design decisions blocking coding.

### 18.2 Milestone M1: Data Contracts and Persistence
Objective:
- Define stable, serializable records for predictions, realized outcomes, and metrics.

Checklist:
1. [ ] Define `PredictionSnapshot` schema with runner output fields at index `t`.
2. [ ] Define `RealizedOutcome` schema for fixed-horizon outcomes.
3. [ ] Define `FoldMetricSummary` schema for per-fold and global aggregates.
4. [ ] Define experiment manifest schema (dataset hash, config hash, seed, fold IDs).
5. [ ] Implement deterministic serialization format for all artifacts.

Dependencies:
1. M0 complete.

Deliverables:
1. Documented schemas and serialization examples.

Exit criteria:
1. Re-run with identical inputs reproduces byte-stable artifacts.

### 18.3 Milestone M2: Walk-Forward Engine (No Tuning Yet)
Objective:
- Build the leakage-safe evaluator loop with snapshot generation and horizon slicing.

Checklist:
1. [ ] Implement fold iterator using locked split geometry.
2. [ ] Implement per-index snapshot generation using bars `[0..t]` only.
3. [ ] Implement fixed-horizon evaluator using `[t+1..t+H]`.
4. [ ] Add leakage guards and audit traces for each snapshot.
5. [ ] Emit snapshot and realized outcome artifacts per fold.

Dependencies:
1. M1 complete.

Deliverables:
1. End-to-end evaluator for one config and one dataset.

Exit criteria:
1. Leakage audit passes for all folds.

### 18.4 Milestone M3: Metric Engine (Phase/Event-First Primary)
Objective:
- Implement optimization and diagnostic metrics with explicit primary/secondary separation.

Checklist:
1. [ ] Implement event outcome accuracy and F1.
2. [ ] Implement phase progression agreement metric.
3. [ ] Implement ranking metrics (`Top-k hit`, `NDCG`).
4. [ ] Implement calibration metrics (`Brier`, `LogLoss`, `ECE`).
5. [ ] Implement secondary diagnostics (exact/near count agreement, distance errors).
6. [ ] Implement composite objective function and variance penalty.

Dependencies:
1. M2 complete.

Deliverables:
1. Per-fold and global metric reports for baseline config.

Exit criteria:
1. Primary metrics are computed for every fold with no missing values.

### 18.5 Milestone M4: Coarse Global Tuner
Objective:
- Add search over coarse EW parameter space with strict global configuration constraint.

Checklist:
1. [ ] Define coarse search space (no per-asset-family branches).
2. [ ] Implement deterministic candidate generation (seeded).
3. [ ] Evaluate each candidate across all training folds.
4. [ ] Apply objective + stability constraints for candidate ranking.
5. [ ] Select global champion and challenger set.

Dependencies:
1. M3 complete.

Deliverables:
1. Candidate leaderboard with fold-level breakdown.

Exit criteria:
1. Champion chosen by predefined objective and constraints, not manual cherry-picking.

### 18.6 Milestone M5: Calibration Layer (Platt Default, Isotonic Challenger)
Objective:
- Improve probability calibration with low-overfit default behavior.

Checklist:
1. [ ] Implement Platt scaling fit on training folds only.
2. [ ] Evaluate calibrated probabilities on validation/test folds.
3. [ ] Implement isotonic challenger behind explicit data sufficiency gate.
4. [ ] Compare Platt vs isotonic with fold-stability constraints.
5. [ ] Select default calibrator per policy (Platt unless isotonic passes all gates).

Dependencies:
1. M4 complete.

Deliverables:
1. Calibration comparison report with reliability diagnostics.

Exit criteria:
1. Selected calibrator improves out-of-sample calibration without violating stability guardrails.

### 18.7 Milestone M6: Holdout Validation and Sign-off
Objective:
- Validate selected champion on untouched holdout period.

Checklist:
1. [ ] Run frozen champion config on untouched holdout.
2. [ ] Compare holdout metrics versus baseline and tuning-period expectations.
3. [ ] Document degradation or lift across primary metrics.
4. [ ] Produce release recommendation: accept, revise, or reject champion.

Dependencies:
1. M5 complete.

Deliverables:
1. Holdout sign-off report and decision record.

Exit criteria:
1. Holdout metrics satisfy predefined acceptance thresholds.

## 19. Test Plan

### 19.1 Unit Test Plan
1. Fold splitter tests:
   - Verify chronological ordering, purge, embargo, and holdout boundaries.
2. Snapshot generator tests:
   - Verify no future bars are visible at index `t`.
3. Horizon evaluator tests:
   - Verify realized window is exactly `[t+1..t+H]`.
4. Label builder tests:
   - Verify deterministic event outcomes (`target-first`, `invalidation-first`, `neither`).
5. Metric tests:
   - Verify exact numeric outputs on controlled synthetic fixtures.
6. Calibration tests:
   - Verify Platt and isotonic implementations fit and apply correctly on toy datasets.

### 19.2 Integration Test Plan
1. End-to-end single-fold smoke:
   - Run one dataset, one config, one fold; verify artifacts emitted.
2. Multi-fold deterministic replay:
   - Repeat identical run and assert stable outputs and metric equality.
3. Baseline-vs-candidate comparison flow:
   - Ensure leaderboard generation and champion selection execute without manual intervention.

### 19.3 Leakage and Integrity Tests
1. Prefix-only audit:
   - For every snapshot, assert max seen index equals `t`.
2. Boundary integrity:
   - Assert no test label window overlaps train bars with forbidden lookahead.
3. Holdout isolation:
   - Assert holdout segment is not read during tuning/selection.

### 19.4 Statistical Validation Tests
1. Calibration sanity:
   - Reliability bins should show monotonic improvement versus raw probabilities for selected calibrator.
2. Variance guard:
   - Reject candidates with fold variance above threshold even with high mean score.
3. Stability check:
   - Ensure gains are not driven by a single fold.

### 19.5 Performance and Scalability Tests
1. Runtime budget test on representative dataset bundle.
2. Memory pressure test for full snapshot artifact generation.
3. Parallelization safety test if concurrent fold evaluation is enabled.

### 19.6 Acceptance Test Suite (Release Gate)
1. Primary objective improvement vs baseline on validation folds.
2. Calibration improvement (`Brier` and `ECE`) vs baseline.
3. Holdout degradation within allowed threshold.
4. Full reproducibility with same seed and manifests.

## 20. Definition of Done for Implementation Phase
1. All Milestones M0-M6 exit criteria are met.
2. Acceptance test suite passes.
3. Champion configuration and calibrator are frozen and versioned.
4. Reports and manifests are archived for auditability.
