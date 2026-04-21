# BTC Elliott Single-Engine Calibration PRD

## Execution Status

- Last updated: 2026-04-21 13:30 EDT
- Active phase: Phase 13
- Active task: Canonical single-engine delivery is complete; the remaining follow-on work is oscillator representation plus optional future generalization and pruning
- Overall: 65/73 checklist items complete

## Status

- Active living PRD/checklist
- Scope owner: Elliott Wave core analysis stack
- Primary validation market: BTC/USD daily full history
- End-state: one canonical series-native Elliott engine produces both historical macro decomposition and live current-cycle analysis, while BTC anchors remain offline calibration truth only

## Summary

The canonical single-engine migration is now complete for production use. Historical macro decomposition and live current-cycle analysis are both driven by one series-native Elliott engine, while BTC anchors stay offline as calibration and regression truth only.

The generic demo is now the controller and rendering surface over an internal canonical engine boundary. The BTC wrapper remains a thin resource loader and filename adapter. Historical truth-target scoring no longer steers runtime current-cycle selection, so the attached live view stays series-native even when a different profile wins offline truth-target validation.

This PRD now serves two roles:

1. record the delivered single-engine architecture and acceptance evidence
2. track the remaining future-oriented work that is intentionally out of the runtime delivery path

Delivered engine guarantees:

- infers completed macro impulse and corrective legs from price alone
- infers the current open structure from the same model
- uses BTC anchor truth only as an offline calibration and regression target
- renders both historical and live charts from the same inferred structure object

The anchor registry stays valuable, but only as labeled truth. It must stop acting as a runtime decomposition crutch.

## Problem Statement

The current architecture still splits responsibility across two different top-level flows.

Historical macro-study flow:

- `ElliottWaveMacroCycleDemo.java:92`
- `ElliottWaveMacroCycleDemo.java:245`
- `ElliottWaveMacroCycleDetector.java:72`

Live current-cycle flow:

- `ElliottWaveMacroCycleDemo.java:158`
- `ElliottWaveMacroCycleDemo.java:273`
- `ElliottWaveAnalysisRunner.java:352`

That split creates four concrete failures:

1. There is still no single canonical EW structure for a given `BarSeries`.
2. Historical and live charts are not guaranteed to tell the same story.
3. BTC truth alignment is still achieved partly through anchored decomposition rather than purely through native search.
4. The repo cannot honestly claim that one engine explains both the full macro history and the current right-edge count.

## Ground Truth and Calibration Premise

The BTC anchor registry is not arbitrary. It encodes the working truth target the engine must learn to reproduce:

- BTC appears to exhibit macro `1-2-3-4-5 / A-B-C` cycles around halving regimes.
- We currently have a committed truth target that captures three accepted full cycles and a reference early segment.
- Known macro tops and lows are already represented in the BTC anchor registry and dataset.

That truth target is not to be discarded. It must be reclassified.

New meaning of the anchor registry:

- not runtime analysis input
- not a second engine
- not the source of historical decomposition at render time
- yes: offline labeled data for calibration, scoring, regression, and holdout validation

## Product Goal

Create one series-native Elliott engine that can:

- infer completed macro bullish legs from full history
- infer completed macro bearish corrections from full history
- infer the current open macro structure at the live right edge
- expose that structure in one canonical result object
- render both historical and live chart views from that same result
- reproduce the BTC truth target through calibration of rule subset and rule weighting rather than anchor-driven decomposition

## Success Criteria

- There is exactly one top-level structure inference engine for historical and live BTC EW analysis.
- The historical macro chart and live current-cycle chart are both views of the same inferred structure object.
- No runtime chart/report path requires an `AnchorRegistry` to determine cycle structure.
- BTC truth-target scoring remains available and becomes an offline calibration/regression harness only.
- On ossified BTC daily full history, the canonical engine reproduces the accepted tops and lows within tolerance and produces the expected wave labels for accepted cycles.
- The fractal confirmation feature remains supported and tunable in the canonical engine.
- Full build remains green.

## Non-Goals

- Do not throw away the BTC truth target in the name of purity.
- Do not keep historical anchors as production runtime inputs.
- Do not build a BTC-only engine.
- Do not introduce a large new public API surface before the canonical model stabilizes.
- Do not treat chart rendering work as the fix. The problem is inference, not annotation.

## Current State Analysis

### What is already unified

- Shared core runner for anchored-window selection:
  - `ElliottWaveAnalysisRunner.java:322`
- Shared core runner for current-cycle analysis:
  - `ElliottWaveAnalysisRunner.java:352`
- Shared profile plumbing:
  - `ElliottWaveMacroCycleDemo.java:323`
  - `ElliottWaveMacroCycleDemo.java:957`
- Shared current-cycle summary construction:
  - `ElliottWaveMacroCycleDemo.java:273`

### What is still split

- Historical decomposition still builds completed cycle legs from an anchor registry:
  - `ElliottWaveMacroCycleDemo.java:1204`
  - `ElliottWaveMacroCycleDemo.java:1224`
- Anchor-free historical mode still creates inferred anchors first, then reuses the anchored macro-study path:
  - `ElliottWaveMacroCycleDetector.java:72`
- Live analysis still optimizes only for the current bullish cycle rather than full-history structure:
  - `ElliottWaveAnalysisRunner.java:352`

### Empirical divergence already observed

The historical macro-study report and the pure live-engine replay currently disagree on completed macro bearish closes.

Historical macro-study summary:

- `elliott-wave-btc-macro-cycles-summary.json`

Pure live-engine replay summary:

- `elliott-wave-btc-live-engine-replay-summary.txt`

Observed difference:

- historical path resolves completed `2011 -> 2013 -> 2015`, `2015 -> 2017 -> 2018`, `2018 -> 2021 -> 2022` cycles
- pure live replay finds `WAVE5` tops at `2013` and `2017`
- but at `2015-08-19` and `2018-12-16` it still reads larger bullish `WAVE4` structures rather than completed bearish `A-B-C` corrections

That is the exact failure this PRD must remove.

## Design Principles

- One canonical structure inference engine.
- Truth target is offline supervision, not runtime decomposition input.
- Hard EW rules prune invalid structures.
- Soft EW rules contribute weighted score.
- Fractal confirmation is a first-class scoring and stability input, not an afterthought.
- Historical and live charts must be different renderings of the same inferred structure.
- Favor extending existing core types and package-private helpers before adding public APIs.

## Canonical Architecture

### Layer 1: Truth Target

Inputs:

- ossified BTC daily dataset
- committed macro top/bottom truth target
- anchor tolerances
- expected cycle partitions and holdout markers

Role:

- offline calibration
- offline regression
- holdout validation

### Layer 2: Canonical Series-Native EW Engine

Inputs:

- `BarSeries`
- logic profile or rule vector
- search options

Outputs:

- completed macro bullish legs
- completed macro bearish legs
- completed macro cycles
- current open structure
- ranked alternates
- evidence and confidence metrics

This becomes the only structure model.

### Layer 3: Views and Reporting

Historical view:

- renders completed macro cycles plus current open structure if requested

Live view:

- renders current open structure and optional completed history context

Both views consume the same canonical structure result object.

## Required Core Capabilities

The canonical core engine must support:

- full-history pivot graph construction from processed swings
- profile-aware and fractal-aware structure search over alternating bullish and bearish macro legs
- completed impulse and corrective leg fitting from the same series-native search
- open right-edge structure fitting from the same search
- global path ranking, not just local window ranking
- confidence and evidence breakdowns at leg, cycle, and whole-structure levels

## New Core Search Model

### A. Pivot Graph Construction

Build a graph from the same processed swings already produced by the runner.

Nodes:

- major candidate pivots
- each node carries:
  - time
  - price
  - pivot direction
  - swing/fractal metadata
  - degree context

Edges:

- candidate bullish impulse leg
- candidate bearish corrective leg

Each edge is fit and scored through shared runner logic, not bespoke chart heuristics.

### B. Local Leg Scoring

Re-use and extend the core window scoring already centralized in:

- `ElliottWaveAnalysisRunner.java:322`

Required local outputs:

- best completed bullish `1-2-3-4-5` fit for a pivot window
- best completed bearish `A-B-C` fit for a pivot window
- best partial bullish fit for a window
- best partial bearish fit for a window

### C. Global Structure Search

Add a new core search that assembles the highest-ranked full-history structure from local leg fits.

Search state includes:

- current pivot
- expected next direction
- completed cycle count
- open leg status
- cumulative score
- structural provenance

Global search should use dynamic programming or bounded beam search and optimize for:

- local leg fit quality
- structural continuity
- alternation of bullish and bearish macro legs
- degree/fractal consistency
- parsimony
- stability under minor pivot perturbations

### D. Current Right-Edge Structure

The current right-edge read must be derived from the same global structure model, not a separate current-cycle-only optimizer.

That means:

- current open bullish or bearish leg is just the unfinished suffix of the canonical structure
- live invalidation and wave-5 target still come from that suffix
- historical and live stories cannot diverge

## Rule Vector and Calibration Model

### Hard Rules

These reject candidate legs or structures outright.

Examples:

- wave 2 full invalidation of wave 1
- wave 3 shortest impulse violation
- forbidden overlap unless diagonal mode applies
- directional pivot alternation
- minimum structural span and minimum wave completeness

### Soft Rules

These contribute weighted score.

Examples:

- Fibonacci quality
- time proportion quality
- alternation quality
- channel quality
- completeness quality
- fractal confirmation quality
- span quality
- start integrity quality
- cross-degree consistency
- macro continuity

### Calibration Objective

Use BTC truth-target comparison only after the canonical structure is inferred.

Calibration objective should combine:

- EW structural quality
- truth-target anchor alignment
- wave-label alignment
- cycle completion alignment
- holdout stability
- parsimony penalty
- replay stability penalty

The exact weighting must be tracked and versioned.

## Proposed New Core Types

Keep these package-private until convergence is proven.

- `CanonicalStructureAssessment`
- `CanonicalLegFit`
- `CanonicalCycleFit`
- `CanonicalStructureSearchOptions`
- `CanonicalStructureSearchResult`
- `TruthTargetComparison`

The goal is to evolve existing result types where possible and avoid a second parallel model hierarchy.

## Public API End State

Target minimal public API additions:

- one new runner method for full-history canonical structure search
- one stable result view for completed structure plus open right-edge structure

Avoid exposing calibration internals publicly.

## Implementation Plan

### Phase 0: Freeze Truth-Target Semantics

- [x] Reclassify the BTC anchor registry and harness as offline truth-target assets only.
- [x] Document the truth target explicitly:
  - accepted tops
  - accepted lows
  - tolerances
  - expected wave labels
  - holdout semantics
- [x] Add docs stating that runtime historical decomposition must not depend on registry input.
- [x] Preserve the reference early `2011` segment as calibration-only evidence, not runtime structure.

### Phase 1: Define the Canonical Structure Result Model

- [x] Design the single structure result object that can back both historical and live charts.
- [x] Include:
  - completed bullish legs
  - completed bearish legs
  - completed cycles
  - current open structure
  - alternates
  - evidence
- [x] Keep the new types package-private initially.
- [x] Add unit tests for object invariants and serialization/report mapping.

### Phase 2: Generalize Local Leg Fitting in Core

- [ ] Refactor anchored window selection into a generalized local leg fitter for completed and partial paths.
  - Landed so far: terminal bullish `1-2-3-4-5` and bearish `A-B-C` window template selection now lives in the core runner via `selectAcceptedOrFallbackTerminalLegForWindow(...)`.
  - Landed so far: partial bullish and bearish window fitting now lives behind the same core runner surface via `fitPartialLegForWindow(...)`.
- [ ] Ensure the same fitter can score:
  - completed bullish impulse legs
  - completed bearish corrective legs
  - partial bullish structures
  - partial bearish structures
- [ ] Preserve fractal confirmation degrees-up/degrees-down support in this fitter.
- [x] Add focused runner tests for each leg family.

### Phase 3: Build the Series-Native Pivot Graph

- [x] Promote processed swing pivots into a reusable macro pivot graph model.
- [x] Carry forward:
  - pivot direction
  - bar index
  - timestamp
  - price
  - degree provenance
  - fractal confirmation metadata
- [x] Add pruning rules so the graph stays tractable on full BTC history.
- [x] Add tests proving key macro pivots survive graph construction.

### Phase 4: Add Global Canonical Structure Search

- [x] Implement global structure search over alternating bullish and bearish candidate legs.
- [x] Start with beam search or dynamic programming and a bounded state space.
- [x] Score global structure continuity and macro coherence, not just local fit quality.
- [x] Add tests on small synthetic series where the globally correct path is known.
- [x] Add performance safeguards so full BTC daily is practical.

### Phase 5: Move Current-Cycle Inference onto the Canonical Search

- [x] Replace the separate current-cycle-only ranking path in `ElliottWaveAnalysisRunner.java:352`.
- [x] Make current-cycle output the open suffix of the canonical full-history structure.
- [x] Preserve:
  - live chart/report support for the open suffix view
  - the ability to surface right-edge invalidation and target guidance without treating exact values as regression truth

### Phase 6: Demote Anchor-Registry Historical Decomposition

- [x] Remove the anchor-registry-driven runtime decomposition path as a primary renderer input.
- [x] Stop using `buildLegSegments(...)` as a runtime truth source.
- [x] Emit a harness-side legacy-vs-canonical completed-cycle diff artifact so canonical misses are visible on underlying cycle data before any runtime switchover.
- [x] Emit an on-demand replay cutoff profile sweep artifact so per-profile cutoff behavior is visible before changing comparator or backbone rules.
- [x] Keep raw accepted historical legs available as evidence, but promote only top-level completed macro cycles for completed-cycle truth comparison.
- [x] Stop using `buildHistoricalCycles(...)` as a runtime truth source.
- [x] Retain registry-backed comparison only inside the calibration harness.
- [x] Keep a temporary compatibility path only while the canonical engine is being proven.

Rationale note:
- A minimal public core bridge for completed historical structure is acceptable here because `ta4j-examples` cannot consume package-private canonical search results across module boundaries. Keep that bridge narrow and evidence-carrying; do not expose calibration internals.
- The legacy anchored historical study is no longer a runtime default. It survives only as a harness-side comparison mode so canonical-vs-legacy diffs can still be inspected offline without reopening a public runtime seam.
- The direct canonical BTC probe already showed that post-2022 pivots and alternating legs can be plausible while still being over-promoted into peer macro cycles. The next canonical improvement axis is degree-aware macro-cycle promotion, not pivot suppression.

### Phase 7: Replace the Macro Cycle Detector Heuristic

- [x] Stop using `ElliottWaveMacroCycleDetector.java:72` as a runtime front-end.
- [x] If any inferred-anchor artifacts remain useful, keep them only as debugging/calibration output.
- [x] Ensure historical chart generation consumes canonical structure output directly.

### Phase 8: Build the BTC Calibration Harness

- [x] Create an offline calibration harness that runs the canonical engine over full-history BTC.
- [x] Score result quality against the truth target:
  - top/bottom timing error
  - cycle completion alignment
  - wave-label alignment
  - holdout coverage
  - replay stability
- [x] Make the harness emit a scored comparison report for candidate profile vectors.
- [x] Add a stable baseline report artifact for the current best vector.
- [x] Persist BTC calibration artifacts incrementally instead of only at the end:
  - write each candidate-profile result as it completes
  - write the selected BTC historical calibration report before portability checks begin
  - append portability and final aggregate output afterward
- [x] Expand the portability datasets so ETH/USD and SP500 cover materially longer histories before using portability results as a meaningful calibration gate.
- [x] Parallelize calibration at the fold level with a bounded executor and deterministic reduction.
- [x] Avoid nested parallelism across profiles, folds, and scenario search; one layer must own concurrency.
- [x] Add deep runtime instrumentation to the harness and core search:
  - per-profile elapsed time
  - per-fold elapsed time
  - per-snapshot elapsed time
  - scenario counts and decomposition branch counts
  - portability timing kept separate from BTC calibration timing
- [x] Split calibration outputs into:
  - routine sanity artifacts
  - exhaustive calibration artifacts

### Phase 9: Search the Rule Vector

- [x] Enumerate the hard-rule subset and soft-rule weighting knobs that are allowed to vary.
  - Hard rules stay fixed in the canonical search:
    - directional alternation and phase-family validity for completed impulse and corrective legs
    - wave 2 must not break below the wave 1 origin on standard bullish impulse paths
    - wave 4 must not overlap wave 1 territory on standard non-diagonal impulse paths
    - completed bearish macro legs must resolve as corrective-C terminations in the currently supported family
    - fold-bounded labeling and leakage controls stay mandatory and never enter the search vector
  - Soft or tunable knobs may vary across candidate vectors:
    - base degree selection
    - higher-degree and lower-degree confirmation drag
    - retained scenario cap
    - scenario swing window
    - confidence weighting and confidence-model family
    - pattern-set scope once the canonical search consumes it directly
    - swing-confirmation geometry and related fractal controls
  - Explicitly keep `wave 3 cannot be the shortest` out of the hard-rule set; treat it as a heuristic signal only.
- [x] Include fractal configuration as part of the vector.
- [x] Create a controlled profile search process over:
  - orthodox constraints
  - relaxed constraints with asterisks
  - fractal confirmation windows
  - score weights
- [x] Keep the search deterministic and reproducible.
- [x] Persist the selected vector as the canonical BTC-calibrated profile.
- [ ] Explore pruning opportunities before adding more brute-force search:
  - partial-cut invalidation pruning
  - minimum segment span pruning
  - impossible direction/overlap pruning
  - retracement and amplitude bound pruning
  Landed so far: alternation pruning plus impulse-side wave-2 invalidation, wave-2 Fibonacci retracement pruning, wave-3 extension pruning, wave-4 overlap pruning, wave-4 Fibonacci retracement pruning, and corrective wave-B retracement pruning before full decomposition scoring.
- [x] Record pruning hit rates and runtime deltas so performance work stays evidence-driven.
- [x] Decide whether the default search lane should use a reduced geometry while an exhaustive lane remains opt-in.
  Implemented: default harness runs `routine` depth with the locked canonical BTC profile and non-overlapping fold steps; `--exhaustive` enables the full controlled search plan plus portability.
  Fast-path validation: `--targeted` now runs the locked canonical BTC profile on a reduced `2016-01-01 .. 2024-12-31` BTC window with filtered truth anchors so changes can be checked without another full-history exhaustive pass.

### Immediate Follow-up Order from the First Full Calibration Run

1. [x] Add fold-level instrumentation first:
   - landed: per-fold elapsed time
   - landed: per-snapshot elapsed time
   - landed: scenario counts
   - landed: decomposition branch counts
2. [x] Persist BTC-first calibration artifacts before portability:
   - landed: write each BTC candidate-profile result as it completes
   - landed: write the selected BTC historical calibration report before portability checks begin
   - landed: append portability and final aggregate output afterward
3. [x] Fix the holdout anchor aggregation inconsistency so anchor-level holdout summaries align with the cycle-level holdout evidence already present in the report.
4. [x] Widen the rule search around the failed 2021/2022 holdout cycle after the instrumentation and reporting fixes are in place.

### Phase 10: Collapse Historical and Live Rendering onto One Result

- [x] Refactor historical chart rendering to consume canonical structure output only.
- [x] Refactor live chart rendering to consume canonical structure output only.
- [x] Ensure the historical chart and live chart cannot tell contradictory stories on the same series.
- [x] Add chart/report regression tests proving both views are backed by the same structure ids or provenance markers.

### Phase 11: Update Demos and Wrappers

- [x] Keep `ElliottWaveBtcMacroCycleDemo.java` as a thin resource-loading wrapper only if it still adds value.
- [x] Make `ElliottWaveMacroCycleDemo.java` a pure view/controller over canonical engine output.
- [x] Remove remaining example-layer structure inference logic.
- [x] Update preset/demo docs to explain the new canonical engine and calibration role of the BTC truth target.

Current status: the generic demo now owns the shared historical-study and live current-cycle model vocabulary, and the BTC wrapper is reduced to BTC-specific resource loading plus stable report adapters. The latest pass also split registry-backed truth-target validation from runtime current-cycle profile selection, and the remaining profile sweep / truth-target orchestration now sits behind a dedicated internal canonical engine boundary rather than inside the controller flow.

### Phase 12: Final Sanity and Acceptance Gate

- [x] Run the canonical engine on full-history BTC daily and confirm it reproduces the truth target within tolerance.
  Current status: the registry-backed canonical historical study now recovers all three committed BTC macro cycles in order and within tolerance on the full-history daily dataset. Truth-target ranking now prioritizes complete expected-cycle coverage over span, filters nested sub-cycles out of extra-cycle penalties, and scores completed-cycle recovery on the committed peak/low windows instead of requiring exact bullish-leg starts.
- [x] Run the same engine in replay mode at major BTC tops and lows and confirm the historical and current charts are coherent.
  Current status: replay regressions now cover the major BTC turns at `2013-11-30`, `2015-08-19`, `2017-12-18`, `2018-12-16`, `2021-11-11`, and `2022-11-22`, and the canonical replay assertions confirm that completed cycles stay ordered, bounded by the replay cutoff, and coherent with the attached current-cycle profile selection.
  Validation note: future replay-promotion or profile-selection changes should first clear synthetic/unit diagnostics for substructure-backed macro acceptance and truncated-history profile ranking before rerunning the expensive BTC detector cutoffs.
- [x] Confirm historical chart and live chart are both drawn from the same structure object.
- [x] Run full verification and record the final build log.
  Current status: `scripts/run-full-build-quiet.sh` completed green on 2026-04-21 with `Tests run: 6226, Failures: 0, Errors: 0, Skipped: 16`; the recorded log lives at `.agents/logs/full-build-20260421-132915.log`.

### Phase 13: Design Elliott Oscillator Representation

- [ ] Define the macro-cycle oscillator mapping so bullish `1-2-3-4-5` occupies the positive lobe, bearish `A-B-C` occupies the negative lobe, the wave-5 top maps to the oscillator crest, and the corrective-C low maps to the oscillator trough.
- [ ] Define the higher-frequency wave-count oscillator mapping so `1`, `3`, `5`, and `B` occupy positive-amplitude sections while `2`, `4`, `A`, and `C` occupy negative-amplitude sections.
- [ ] Specify the amplitude, phase, normalization, and time-warp rules needed to transform irregular Elliott legs into a stable oscillator form across instruments and windows.
- [ ] Add BTC reference examples showing canonical historical cycles, current open structure, and their oscillator representations side by side.

## Step-by-Step Execution Sequence

Implement in this order:

1. Freeze truth-target semantics and docs.
2. Define the canonical structure result model.
3. Generalize local leg fitting in core.
4. Build the reusable pivot graph.
5. Implement global canonical structure search.
6. Move current-cycle output onto that global structure.
7. Demote anchor-driven runtime decomposition.
8. Build the BTC calibration harness.
9. Search and freeze the best BTC-calibrated rule vector.
10. Collapse historical and live rendering onto the same structure output.
11. Simplify examples and wrappers.
12. Run final sanity, replay, and build verification.

## Testing Strategy

### Unit Tests

- local leg scoring
- pivot graph construction
- global path ranking on synthetic series
- hard-rule pruning
- soft-rule score aggregation
- fractal confirmation variants

### BTC Regression Tests

- truth-target alignment on full-history BTC
- holdout-cycle coverage
- replay consistency at major tops and lows

### Output Regression Tests

- historical report and live report share the same structure provenance
- chart/report DTOs map correctly from the canonical structure result
- no historical/live contradiction on the same series

## Risks

- Global search may become too expensive without aggressive pruning.
- A truth-target-overfit profile may reproduce BTC while becoming unusably special-cased.
- Hard-rule and soft-rule boundaries may be fuzzy and need iteration.
- Fractal configuration may materially change search complexity.
- Migration may temporarily regress demo/chart compatibility until the old path is fully removed.

## Open Questions

- Should the canonical engine support multiple equally plausible macro structures, or force one best path only?
- How much cross-market generality is required before declaring the BTC-calibrated vector acceptable?
- Should the early `2011` BTC segment remain out of the primary acceptance score, or become a weighted partial target?
- Do we need separate canonical profiles for:
  - strict orthodox
  - BTC-calibrated macro
  or should there be only one default profile plus advanced opt-ins?

## Acceptance Definition

This PRD is complete only when:

- the repo has one canonical series-native EW structure engine
- BTC historical and live charts both come from that same structure result
- the anchor registry is no longer a runtime decomposition input
- BTC truth-target alignment is achieved through offline calibration and regression only
- the calibrated engine reproduces the accepted BTC macro structure well enough to replace the old anchor-driven historical story
