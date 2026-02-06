# Stop Rules Refactor Checklist

Last updated: 2026-02-05
Scope: stop-loss and stop-gain rule additions in `feature/risk-controls` (`bdd4407e`)

## Goals

- Keep stop-loss and stop-gain behavior symmetric where intended.
- Remove avoidable duplication between counterpart rules.
- Lock down edge-case behavior with targeted tests before refactoring.
- Keep API and serialization behavior stable.

## Guiding Principles

- Prefer small, low-risk refactors over broad rewrites.
- Add tests before behavior changes.
- Preserve existing semantics unless a mismatch is clearly a defect.
- Use domain naming that matches behavior (`stopGain*` for gain-side code paths).

## Current Focus Area

- Volatility-based stop rules and trailing stop counterparts.
- Constructor/API consistency between stop-loss and stop-gain variants.
- Test coverage around threshold edge conditions and invalid inputs.

## Prioritized Refactor Opportunities

1. `P1` Clarify and align threshold trigger semantics for volatility rules.
   - `BaseVolatilityStopLossRule` uses strict comparisons (`<` / `>`) while gain-side uses inclusive comparisons (`>=` / `<=`).
   - Decide expected threshold semantics and encode with tests before changing behavior.
2. `P2` Replace gain-side calls to `StopLossRule.stopLossPrice*` with gain-focused helpers.
   - Affects trailing gain implementations that currently call stop-loss helper methods despite gain semantics.
   - Improves readability and lowers maintenance risk.
3. `P2` Normalize constructor validation and API parity across stop-loss/stop-gain pairs.
   - Ensure null/invalid argument handling is explicit and consistent.
   - Align ATR trailing constructors for bar-count configurability or document intentional asymmetry.
4. `P3` Reduce test duplication in stop rule test suites.
   - Consolidate repeated bar-series setup and buy/sell scenario scaffolding.

## Test Coverage Gaps

- No explicit equality-boundary tests for volatility stop-loss/gain thresholds.
- No constructor validation tests for null/invalid parameters in new gain rules.
- Limited tests asserting `stopPrice(...)` semantics for trailing variants at entry context.

## Checklist

- [x] Identify changed stop-loss/stop-gain classes and tests in current branch.
- [x] Run refactor scout audit pass and prioritize opportunities.
- [x] Add boundary tests documenting expected trigger semantics at exact thresholds.
- [x] Add constructor validation tests for new stop-gain rule variants.
- [x] Refactor gain-side helper calls to gain-named primitives where behavior is unchanged.
- [x] Resolve and document volatility threshold inclusivity decision.
- [x] Reduce duplicated test fixture setup across stop-loss/stop-gain test classes.
- [x] Re-run full build and update changelog for any code changes.
