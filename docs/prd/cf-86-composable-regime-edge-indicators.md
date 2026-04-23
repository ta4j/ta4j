# PRD: CF-86 - ta4j composable regime and edge primitives

*Project:* ta4j
*Source issue:* [CF-86](https://linear.app/cookiefactory/issue/CF-86/ta4j-add-composable-regimeedge-indicators-and-stateful-trading-rules)
*Source document:* `25dad631-c8a6-4214-9b93-7070363684ff`
*Normalized in repo:* `docs/prd/cf-86-composable-regime-edge-indicators.md`

## Execution Status
- Last updated: 2026-05-21 12:04 EDT
- Current branch/worktree: `feature/deliver-prd-cf-86-20260423-151830` at `/Users/david/.codex/worktrees/deliver-prd-cf-86-20260423-151830`
- Base branch/base commit: `origin/master` at `f3c06bbe392e91285c37d1e294a7b0806d3d5d79`
- Active phase: `Phase 3 - recovered, rebased, and reverified`
- Active task: `Human review of the local public-repo commit before any push or PR`
- Next task: `Human review of the local public-repo commit before any push or PR`
- Overall: `7/7 checklist items complete`
- Verification: `targeted ta4j-core tests green; full quiet build green; local commit present`

## Summary

Deliver reusable ta4j-native indicators and rules for stretch, compression, trend state, entry-edge measurement, edge decay, and loss-aware gating without introducing CF-specific runtime assumptions.

## Reuse Audit

- Reused existing ta4j primitives instead of creating parallel math helpers: `ZScoreIndicator`, `StandardDeviationIndicator`, `PercentRankIndicator`, `DifferenceIndicator`, `SimpleLinearRegressionIndicator`, Bollinger/Donchian/VWAP indicators, `ADXIndicator`, and `TradingRecord`/`Position`.
- New top-level types remain limited to the explicit CF-86 deliverables because no existing ta4j public types model these domain concepts end to end.

## Scope

### Required indicator deliverables

- `StretchZScoreIndicator`
- `CompressionIndicator`
- `TrendScoreIndicator`
- `TrendConclusionIndicator`
- `EntryEdgeIndicator`
- `EdgeDecaySlopeIndicator`

### Required rule deliverables

- `LossTriggeredCooldownRule`
- `EdgeHealthyRule`

### Constraints

- Keep the implementation generic and ta4j-native.
- Avoid look-ahead leakage in every indicator/rule.
- Keep new public APIs documented with Javadoc and `@since 0.22.7`.
- Prefer composition over new utility frameworks.

## Checklist

### Phase 1 - ta4j-core primitives

- [x] Finalize the smallest reusable API shape for the six indicators and two rules.
- [x] Implement `StretchZScoreIndicator`, `CompressionIndicator`, `TrendScoreIndicator`, and `TrendConclusionIndicator`.
- [x] Implement `EntryEdgeIndicator`, `EdgeDecaySlopeIndicator`, `LossTriggeredCooldownRule`, and `EdgeHealthyRule`.

### Phase 2 - tests and docs

- [x] Add deterministic ta4j-core unit tests covering behavior, warm-up handling, and edge cases for each new primitive.
- [x] Add a concise usage example or README note showing how the new primitives compose into a strategy.

### Phase 3 - verification and handoff

- [x] Run targeted ta4j-core tests while iterating until green.
- [x] Run `scripts/run-full-build-quiet.sh` from repo root and record the result before local commit.

## Delivery Notes

- Added reusable ta4j-core indicators for stretch, compression, trend state, trend-conclusion detection, rolling entry edge, and edge-decay slope.
- Added reusable ta4j-core rules for loss-triggered cooldown gating and minimum-edge health checks.
- Documented the new primitives in `/Users/david/.codex/worktrees/deliver-prd-cf-86-20260423-151830/README.md`.
- Recovered the lost local implementation, rebased it onto current `origin/master`, fixed cooldown behavior so a newer winning position clears an older loss, and added a no-look-ahead regression for immature entry-edge signals.

## Verification Record

- Targeted tests: `JAVA_HOME=/Users/david/.codex/jdks/temurin-25/Contents/Home mvn -q -pl ta4j-core -am test -Dtest=StretchZScoreIndicatorTest,CompressionIndicatorTest,TrendScoreIndicatorTest,TrendConclusionIndicatorTest,EntryEdgeIndicatorTest,EdgeDecaySlopeIndicatorTest,LossTriggeredCooldownRuleTest,EdgeHealthyRuleTest -Dsurefire.failIfNoSpecifiedTests=false`
- Full build: `JAVA_HOME=/Users/david/.codex/jdks/temurin-25/Contents/Home QUIET_BUILD_TIMEOUT_SECONDS=1800 bash scripts/run-full-build-quiet.sh`
- Result: `BUILD SUCCESS`
- Reactor summary: `Tests run: 6479, Failures: 0, Errors: 0, Skipped: 16`
- Full build log: `/Users/david/.codex/worktrees/deliver-prd-cf-86-20260423-151830/.agents/logs/full-build-20260521-120340.log`
