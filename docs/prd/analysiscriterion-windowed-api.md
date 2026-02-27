# PRD: Window-Aware `AnalysisCriterion` API

## 1. Context
Today, `AnalysisCriterion` evaluates either:
- a single `Position`, or
- a full `TradingRecord`.

There is no first-class API to evaluate criteria over a bounded bar/time window, for example:
- net profit over the last 7 days,
- net profit over the last 30 days,
- net profit between February 10, 2026 and February 14, 2026.

This gap is especially risky for constrained/moving `BarSeries` where old bars are removed and visible indices shift (`beginIndex` can be greater than `0`).

## 2. Problem Statement
Users need a reliable, explicit way to evaluate criteria over windows without hand-rolling index math, date-to-index mapping, or record filtering.

Current DIY approaches are error-prone because:
- time windows must be mapped to bar indices consistently,
- moving series can alias removed indices (historical indices no longer exist in-memory),
- criteria differ in semantics (position-based vs bar-path/equity-curve based),
- open positions overlapping a window need explicit handling rules.

## 3. Goals
- Add first-class windowed criterion calculation for bar and time windows.
- Preserve backward compatibility for all existing `AnalysisCriterion` implementations.
- Define deterministic behavior for constrained/moving series and unavailable history.
- Provide semantics that work across criteria families (PnL, return, drawdown, risk).

## 4. Non-Goals
- Implementing these API changes in this PRD.
- Changing the mathematical definition of existing criteria outside selected window semantics.
- Requiring full historical retention for moving series.

## 5. Key Semantics To Standardize
- Window types:
  - bar index window (start/end index),
  - lookback bars (`N` bars),
  - absolute time window (start/end instant),
  - lookback duration (for example 7 days from an anchor/as-of instant).
- Boundary convention:
  - recommended: start inclusive, end inclusive for bar indices,
  - recommended: start inclusive, end exclusive for time instants.
- Position inclusion policy (must be explicit):
  - closed positions with exit in window (default),
  - closed positions fully contained in window,
  - optional mark-to-market treatment for open positions at window end.
- Missing history policy on constrained/moving series:
  - `STRICT`: throw when requested window references unavailable history,
  - `CLAMP`: intersect requested window with currently available `[beginIndex, endIndex]`.

## 6. Approach A (Minimal Additive Overloads)
Add default overloads to `AnalysisCriterion` for windowed calculations while keeping current methods untouched.

### API shape (conceptual)
- Add overloads that accept:
  - bar window (start/end indices),
  - time window (start/end instants),
  - lookback window (bars and/or duration, plus optional anchor/as-of).
- Add optional window options parameter to control:
  - missing history behavior (`STRICT` vs `CLAMP`),
  - position inclusion policy,
  - open-position handling.

### How it works
- Default methods in the interface normalize the requested window to an effective index range.
- They build a windowed view/projection of `TradingRecord` and delegate to existing `calculate(series, tradingRecord)`.
- Existing criterion classes remain source/binary compatible (no forced overrides).

### Pros
- Low migration cost.
- Least disruptive for users and maintainers.
- Fastest path to delivering basic window support.

### Cons
- Risk of overload proliferation over time.
- Window options may become fragmented across many method signatures.

## 7. Approach B (Window Object + Context)
Introduce a single extensible window/context abstraction and one new `calculate` overload using it.

### API shape (conceptual)
- New immutable window descriptor, for example `AnalysisWindow`, representing:
  - bar range, lookback bars, absolute time range, or lookback duration.
- New options/context object, for example `AnalysisContext`, representing:
  - missing history policy,
  - position inclusion policy,
  - open-position valuation policy,
  - optional anchor/as-of instant.
- Add one new criterion entry point:
  - `calculate(series, tradingRecord, window, context)`
  - with a convenient default context.

### How it works
- A shared normalizer resolves window + context into an effective index range and filtered record view.
- Criteria consume the normalized context uniformly.

### Pros
- Avoids overload explosion.
- Scales better for future options.
- Single place to document and test semantics.

### Cons
- More upfront API surface (new types).
- Slightly higher conceptual overhead for simple use cases.

## 8. Edge Cases (Must-Have Behavior)
- Empty series or no bars in effective window:
  - return criterion-neutral value consistent with each criterion type, never silent nonsense.
- Window outside available bars in moving series:
  - `STRICT`: fail with clear message containing requested and available ranges.
  - `CLAMP`: use intersection; if empty after clamp, return neutral value.
- Requested index lower than `series.getBeginIndex()` when bars were removed:
  - never rely on `getBar(i)` aliasing removed bars; normalization must reference available range first.
- Time window mapping:
  - map using bar end time as canonical membership timestamp.
  - define tie handling explicitly (start inclusive, end exclusive).
- Positions crossing window boundaries:
  - apply explicit inclusion policy, defaulting to "exit-in-window" to avoid pseudo prorating.
- Open current position:
  - default exclude unless mark-to-market mode is explicitly requested.
- Constrained subseries:
  - preserve externally visible index semantics and ensure normalization is done on logical series indices, not raw backing-list offsets.

## 9. Decision
Selected approach: **Approach B (Window Object + Context)**.

Decision rationale:
- Windowed analytics typically accumulate options quickly.
- A dedicated window/context model keeps API growth controlled and semantics centralized.
- A single normalization pipeline reduces divergence across criterion families.

## 10. Acceptance Criteria for Implementation Phase
- Users can request criterion calculations for:
  - lookback bars,
  - lookback duration (for example past 7/30 days),
  - explicit time ranges (for example Feb 10, 2026 to Feb 14, 2026).
- Behavior is deterministic for constrained/moving series under both `STRICT` and `CLAMP` modes.
- Position boundary/open-position behavior is documented and enforced by tests.
- Existing criterion implementations compile and preserve previous behavior when no window is provided.
- Javadocs clearly define boundary and policy semantics with examples.

## 11. Step-by-Step Implementation Checklist (Option B)
- [ ] 1. Define new immutable API contracts in `ta4j-core`:
  - [ ] Add `AnalysisWindow` with builders/factories for:
    - [ ] bar-range windows,
    - [ ] lookback-bars windows,
    - [ ] absolute time-range windows,
    - [ ] lookback-duration windows.
  - [ ] Add `AnalysisContext` with defaults for:
    - [ ] missing history policy (`STRICT`, `CLAMP`),
    - [ ] position inclusion policy,
    - [ ] open-position valuation policy,
    - [ ] optional anchor/as-of instant.
- [ ] 2. Extend `AnalysisCriterion` with one window-aware entry point:
  - [ ] Add `calculate(series, tradingRecord, window, context)`.
  - [ ] Add a convenience overload with default context.
  - [ ] Keep existing methods unchanged for backward compatibility.
- [ ] 3. Implement shared window normalization:
  - [ ] Resolve time windows to effective index ranges using bar end-time membership.
  - [ ] Enforce boundary semantics (index: inclusive/inclusive, time: inclusive/exclusive).
  - [ ] Apply `STRICT` vs `CLAMP` consistently before criterion math.
  - [ ] Guarantee normalization is based on logical series indices (`beginIndex`..`endIndex`), not raw backing-list offsets.
- [ ] 4. Implement trading-record projection for windowed evaluation:
  - [ ] Filter/select positions per inclusion policy.
  - [ ] Handle boundary-crossing positions according to policy.
  - [ ] Handle open position treatment (exclude by default; optional mark-to-market mode).
  - [ ] Ensure projection remains deterministic for constrained/moving series.
- [ ] 5. Integrate window-aware calculation into criterion hierarchy:
  - [ ] Reuse existing `calculate(series, tradingRecord)` logic where possible.
  - [ ] Verify compatibility for PnL, return, drawdown, and helper criteria.
  - [ ] Add guardrails for empty effective windows and empty/partial records.
- [ ] 6. Add comprehensive unit tests:
  - [ ] API-level tests for each `AnalysisWindow` variant.
  - [ ] Normalization tests for moving series with removed bars.
  - [ ] `STRICT` behavior tests (out-of-range requests fail with clear errors).
  - [ ] `CLAMP` behavior tests (window intersection and empty-intersection neutrality).
  - [ ] Boundary-crossing and open-position policy tests.
  - [ ] Regression tests proving existing non-windowed behavior is unchanged.
- [ ] 7. Add Javadocs and user documentation:
  - [ ] Javadocs for all new public API surface with examples (7-day, 30-day, explicit date range).
  - [ ] Clarify default policies and boundary conventions in docs.
  - [ ] Document constrained/moving series caveats and expected behavior.
- [ ] 8. Verification and rollout:
  - [ ] Run targeted tests while iterating.
  - [ ] Run `scripts/run-full-build-quiet.sh` on candidate final patch.
  - [ ] Capture aggregated test summary and build log path in the delivery notes.
