# PRD: CalmarRatioCriterion and OmegaRatioCriterion

## Context
Add `CalmarRatioCriterion` and `OmegaRatioCriterion` in `org.ta4j.core.criteria` with full unit test coverage, while reusing existing return-series and drawdown utilities.

## Requirements
- Implement `CalmarRatioCriterion` and `OmegaRatioCriterion` in `ta4j-core/src/main/java/org/ta4j/core/criteria`.
- Reuse existing analysis components (`Returns`, drawdown criteria, equity/open-position handling helpers) instead of duplicating logic.
- Add comprehensive tests in `ta4j-core/src/test/java/org/ta4j/core/criteria`.
- Validate positive, negative, mixed returns and edge cases with reference calculations.
- Keep build green with `scripts/run-full-build-quiet.sh`.

## Design Notes
- Calmar ratio will be defined as annualized return divided by maximum drawdown.
- Annualized return should be derived from existing return-series utilities.
- Omega ratio will be defined as ratio of gains above a threshold to losses below that threshold from per-bar returns.
- Both criteria should support `TradingRecord` and single `Position` evaluation.

## Checklist
- [x] Create worktree + feature branch.
- [x] Implement `CalmarRatioCriterion` with reusable building blocks.
- [x] Implement `OmegaRatioCriterion` with reusable return-series utilities.
- [x] Add comprehensive unit tests with reference formulas and edge cases.
- [x] Update changelog (Unreleased).
- [x] Run `scripts/run-full-build-quiet.sh` and verify green build.
- [x] Stage and commit changes.
- [x] Move PRD to `docs/archive/`.
