# BTC Elliott Macro Unification PRD

## Status

- Active living PRD/checklist
- Scope owner: Elliott Wave core analysis stack
- Primary validation market: BTC/USD daily
- End-state: BTC macro logic becomes the default Elliott interpretation path, with demos/presets acting as thin wrappers over core behavior

## Summary

The current BTC macro-cycle work proved that the generic Elliott stack can be materially improved by validating against Bitcoin's unusually visible cycle history. That research also left too much decision-making in `ElliottWaveBtcMacroCycleDemo`, especially around anchor-aware decomposition, current-cycle fitting, and scenario display selection.

This PRD finishes the job: move the macro-cycle logic back into `ta4j-core`, make the core runner/generator capable of anchor-aware macro interpretation on any supplied `BarSeries`, and reduce the BTC demo to either:

1. a thin wrapper that injects BTC-specific profile defaults and renders charts, or
2. a removable example once the standard preset/demo path can express the same behavior.

The target is one coherent Elliott engine, not a legacy engine plus a smarter BTC sidecar.

## Problem Statement

Today, the repo still has an architectural split:

- `ta4j-core` contains much of the improved swing extraction and scenario generation.
- `ElliottWaveBtcMacroCycleDemo` still owns important fit-selection behavior, especially for macro anchor spans and live current-cycle fitting.
- `ElliottWavePresetDemo` now routes BTC daily through the new path, but the UX and some scenario plumbing still depend on example-layer logic.

That split causes three problems:

1. Core analysis is not yet the single source of truth.
2. Fixes discovered through BTC validation can regress when the demo and core diverge.
3. The engine still behaves differently depending on whether it is invoked through the example-layer macro study or through the core runner directly.

## Product Goal

Unify the validated BTC macro-cycle logic into the core Elliott Wave stack so that:

- the same core engine can find anchor-like cycle structure from the supplied `BarSeries` itself
- macro-fit selection is performed by `ta4j-core`, not by demo-specific fallback logic
- BTC daily preset analysis is just a tuned invocation of the core engine
- historical BTC cycles remain the truth-set regression suite for validating the engine
- current-cycle inference uses the same unified logic path as historical back-validation

## Success Criteria

- All historical BTC macro legs used by the truth set are produced by core-ranked scenarios, not demo-local decomposition fallback.
- `ElliottWavePresetDemo live Coinbase BTC-USD PT1D ...` uses the unified core path and preserves the existing console/chart UX.
- `ElliottWaveBtcMacroCycleDemo` contains no bespoke scenario-selection or swing-selection logic. If it remains, it only:
  - loads BTC resources
  - selects BTC tuning/profile defaults
  - invokes the core runner
  - renders charts and summaries
- No live current-cycle scenario may label a bullish wave pivot that is later exceeded within the same declared impulse span.
- Full build stays green.

## Non-Goals

- Do not generalize prematurely to intraday BTC or all markets before the daily unified path is stable.
- Do not introduce broad new public APIs unless they are clearly necessary after package-private convergence.
- Do not keep two co-equal Elliott engines long term.

## User-Facing End State

After this PRD is complete:

- users can run the standard Elliott preset/demo for BTC daily and get the macro-validated interpretation by default
- the five-chart preset UX remains intact
- the analysis summary shown in the console is generated from unified core logic
- BTC macro-cycle validation remains available as a regression/reporting workflow, but not as a second engine

## Current Architecture Gaps

- Anchor-aware segment scoring still partially lives in `ta4j-examples`.
- Current-cycle partial impulse fitting is example-owned instead of core-owned.
- Historical BTC truth-set evaluation is separated from the main core scenario ranking path.
- Chart scenario selection still depends on display-layer candidate filtering.

## Design Principles

- One Elliott engine, many wrappers.
- The supplied `BarSeries` is the truth for live analysis. No assumption that full asset history is externally available.
- Historical BTC anchors are a validation harness, not a runtime dependency.
- Favor package-private helpers and extend existing core types before adding new public ones.
- Prefer scored constraints over brittle boolean rejections when doing macro interpretation.

## Required Core Capabilities

The unified core stack must support:

- macro-aware swing extraction on broad histories
- anchor-conditioned scenario ranking within arbitrary start/end windows
- partial bullish and bearish path fitting that respects dominant highs/lows within each declared wave span
- profile-based rule weighting, including orthodox and BTC-relaxed variants
- consistent scenario ranking for both historical validation windows and live current-cycle windows

## Implementation Plan

### Phase 0: Freeze the Truth Set

- [ ] Keep the ossified BTC daily dataset and anchor registry as the canonical validation set.
- [ ] Lock the accepted historical cycle windows:
  - `2011-11-18 -> 2013-11-29/30 -> 2015-08-19`
  - `2015-08-19 -> 2017-12-17/18 -> 2018-12-15/16`
  - `2018-12-15/16 -> 2021-11-10/11 -> 2022-11-21/22`
- [ ] Keep the `2011-06 -> 2011-11` top-to-bottom segment as a reference segment only.
- [ ] Document acceptable anchor tolerance windows in code comments and tests.

Implementation notes:

- Truth-set assets stay in `ta4j-examples/src/main/resources`.
- Validation metadata can remain in examples, but no scenario-selection logic should remain there.

### Phase 1: Move Anchor-Targeted Selection into Core

- [ ] Identify every place where `ElliottWaveBtcMacroCycleDemo` still re-scores, filters, or snaps scenarios after core returns them.
- [ ] Move that logic into `ElliottWaveAnalysisRunner` and closely related package-private helpers.
- [ ] Add internal span-aware selection primitives so the core runner can answer:
  - best scenario for `startIndex -> endIndex`
  - best scenario for `startIndex -> liveEnd`
  - best partial bullish/bearish progression for a window
- [ ] Keep these APIs package-private until convergence is proven.

Implementation steps:

1. Audit demo-only fit-selection methods and classify them as:
   - ranking logic
   - anchor-distance logic
   - pivot-dominance logic
   - current-cycle partial-fit logic
2. Move ranking and anchor-distance logic first into core.
3. Replace demo calls with core calls one seam at a time.
4. Add regression tests for each migrated seam before deleting example-layer logic.

### Phase 2: Unify Pivot and Wave-Span Validation in Core

- [ ] Move bullish partial-impulse anchor-dominance validation from the BTC demo into core.
- [ ] Add the equivalent bearish corrective-span dominance validation in core.
- [ ] Make these validations part of core candidate acceptance and ranking, not just post-filtering.
- [ ] Ensure current-cycle candidates cannot declare pivots that are invalidated by later stronger extremes inside the same wave span.

Implementation steps:

1. Create package-private core helpers for:
   - pivot-role assignment
   - span extreme detection
   - anchor dominance scoring
   - partial path progression validation
2. Route both historical window fitting and live current-cycle fitting through the same helpers.
3. Delete the duplicate logic from `ElliottWaveBtcMacroCycleDemo` once coverage is in place.

### Phase 3: Promote Macro Swing Detection to the Default Core Path

- [ ] Finish moving broad-history swing preservation into the default core runner path.
- [ ] Ensure early macro pivots survive filtering when they are necessary to explain long-cycle structure.
- [ ] Stop requiring example-layer fallback for accepted BTC historical legs.
- [ ] Add tests proving early-cycle pivots survive even when later moves are much larger.

Implementation steps:

1. Audit current composite/hierarchical detector behavior on the BTC truth set.
2. Tighten same-index pivot conflict handling and start-pivot preservation in core.
3. Tune filtering/pruning at the detector and generator levels, not in the demo.
4. Re-run the BTC truth set after each detector-side change to confirm no historical leg falls back to demo-local logic.

### Phase 4: Collapse Scenario Generation onto Core Profiles

- [ ] Move any remaining H0-H4 profile semantics that still live in examples into core profile handling.
- [ ] Keep profiles package-private until the selection surface stabilizes.
- [ ] Make `orthodox-classical` the default profile unless a caller explicitly requests a different tuned profile.
- [ ] If a public selector is still justified after convergence, expose a minimal `logicProfile(...)` builder option.

Implementation steps:

1. Compare example-side profile scoring against core-side scoring.
2. Port only the parts that still materially affect candidate ordering.
3. Add tests showing profile changes alter scenario ranking through core alone.
4. Delete profile-specific ranking branches from the demo.

### Phase 5: Unify Live BTC Preset on the Core Path

- [ ] Keep the current preset UX:
  - console summary
  - base-case chart
  - four alternative charts
- [ ] Ensure those charts are derived from core-selected scenarios only.
- [ ] Remove any example-layer rescue logic that can change which scenarios are displayed after core analysis.
- [ ] Preserve current behavior that the supplied `BarSeries` must be enough to find the current-cycle start.

Implementation steps:

1. Define the exact data contract between the preset demo and the core runner.
2. Make the preset ask core for:
   - primary scenario
   - alternative scenarios
   - current-cycle summary
3. Keep chart rendering in examples, but not scenario selection.
4. Verify the live run still writes the five chart files with the same naming convention.

### Phase 6: Reduce or Remove `ElliottWaveBtcMacroCycleDemo`

- [ ] Strip `ElliottWaveBtcMacroCycleDemo` down to a thin wrapper.
- [ ] Remove all duplicated scenario search, ranking, path search, pivot-dominance, and current-cycle fitting logic from it.
- [ ] If the class remains, it should only:
  - load BTC resources
  - select BTC validation windows/profile defaults
  - invoke the core runner
  - render the regression chart/report
- [ ] If the wrapper still adds no unique value after migration, delete it and fold the reporting path into a generic analysis demo.

Implementation steps:

1. Mark remaining demo-owned logic blocks during migration with temporary comments.
2. Delete them only after a core replacement and test exist.
3. Measure final residual logic in the demo:
   - if it is only I/O and chart/reporting, keep it
   - if it still owns interpretation logic, continue migration

### Phase 7: Documentation and Guides

- [ ] Update user-facing demo docs to explain that BTC daily preset analysis now uses the unified macro-validated core path.
- [ ] Add a short maintainer note describing the BTC truth-set validation role.
- [ ] Document the difference between:
  - truth-set validation data
  - live runtime analysis input
- [ ] Remove stale references implying the legacy local-structure path is still the preferred BTC answer.

## Step-by-Step Execution Sequence

Implement in this exact order unless a blocker forces a change:

1. Inventory remaining demo-local logic and map each method to a target core location.
2. Move pivot-dominance and wave-span validation into core.
3. Move live current-cycle partial-fit selection into core.
4. Rewire BTC demo to consume only core-selected historical fits where available.
5. Finish macro swing-preservation changes until all accepted BTC historical legs are core-ranked.
6. Move any remaining profile/ranking semantics into core.
7. Remove demo-local fallback logic from the preset display path.
8. Reduce `ElliottWaveBtcMacroCycleDemo` to a thin wrapper or remove it.
9. Update docs and maintainer guidance.
10. Run full verification and confirm live BTC preset UX is unchanged except for better counts.

## Required Tests

### Historical Truth-Set Tests

- [ ] All three accepted BTC cycles must be reproduced by core-ranked fits.
- [ ] Each bullish leg must yield a complete `1-2-3-4-5` decomposition.
- [ ] Each bearish leg must yield a complete `A-B-C` decomposition.
- [ ] Terminal `WAVE5` and `CORRECTIVE_C` endpoints must remain inside anchor tolerance windows.

### Live Current-Cycle Tests

- [ ] Live BTC preset must return five display scenarios with stable filenames.
- [ ] Partial bullish paths must alternate correctly.
- [ ] No displayed bullish wave-high may be later exceeded within the same declared wave span unless that pivot is no longer labeled as the terminal high for that wave.
- [ ] Supplied live window alone must be enough to identify the current-cycle start.

### Core Regression Tests

- [ ] Anchor-window reranking tests
- [ ] Start-preservation tests for long macro histories
- [ ] Partial impulse span-dominance tests
- [ ] Partial corrective span-dominance tests
- [ ] Profile-driven ranking tests
- [ ] Preset scenario selection tests that prove display candidates come from core ordering

## Acceptance Criteria

The PRD is complete when all of the following are true:

- `ElliottWaveBtcMacroCycleDemo` contains no unique Elliott interpretation logic.
- BTC historical fit and live current-cycle fit both come from the same core engine.
- The BTC daily preset is the macro-validated core path by default.
- The legacy path is no longer required to explain BTC daily structure.
- The five-chart UX and console summary remain intact.
- Full build is green.

## Reuse and API Notes

- Reuse existing core runner/generator/result types wherever possible.
- New top-level public types are discouraged.
- If a public `ElliottLogicProfile` selector remains necessary after convergence, document why existing builder options are insufficient.
- Any new helper types should be package-private first and justified by a concrete reuse need.

## Risks and Mitigations

- Risk: core ranking becomes overfit to BTC.
  - Mitigation: keep orthodox defaults and regression-test non-BTC scenarios where practical.
- Risk: deleting demo-local fallback exposes remaining core gaps.
  - Mitigation: migrate one seam at a time and keep truth-set tests green after each removal.
- Risk: current-cycle live counts become unstable.
  - Mitigation: require span-dominance and alternating progression tests before accepting live candidate changes.

## Final Deliverable

The final deliverable is a unified Elliott stack where BTC macro validation informed the design, but the resulting logic lives in `ta4j-core` and is invoked uniformly through the normal preset/demo flow.
