## PRD: CF-107 - Add First Class CLI Entry Point For ta4j

Project: ta4j
Source issue: CF-107
Source document: 94ce2135-7bcb-4227-8d04-190c40bda561
Status: Complete
Last updated: 2026-04-02 10:59 EDT

## Summary

Ship a bounded ta4j CLI that reuses the existing backtest, walk-forward, reporting, and charting APIs instead of introducing a parallel execution stack. The MVP stays local-file-first, supports stable `AnalysisCriterion` aliases, returns deterministic machine-readable output, and keeps chart generation opt-in and headless-safe.

## Execution Status

- Last updated: 2026-04-02 10:59 EDT
- Current branch/worktree: `feature/deliver-prd-cf-107-20260402-103720` at `/Users/davidpang/Workspace/ta4j-org/ta4j/.agents/worktrees/deliver-prd-cf-107-20260402-103720`
- Active phase: `Complete`
- Active task: `None`
- Next task: `None`
- Overall: `8/8` tasks complete
- Verification: `Focused tests: mvn -pl ta4j-examples,ta4j-cli -am test -Dtest=CsvBarSeriesDataSourceTest,Ta4jCliTest -Dsurefire.failIfNoSpecifiedTests=false; full gate: scripts/run-full-build-quiet.sh (log: /Users/davidpang/Workspace/ta4j-org/ta4j/.agents/worktrees/deliver-prd-cf-107-20260402-103720/.agents/logs/full-build-20260402-105811.log)`

## Scope Notes

- Reuse audit: the CLI will reuse `BacktestExecutor`, `BarSeriesManager`, `WalkForwardConfig`, `StrategyWalkForwardExecutionResult`, `TradingStatement`, and `ta4jexamples.charting.workflow.ChartWorkflow`. A dedicated CLI module is still required because the repository currently exposes no supported command-line entry point or packaging target.
- MVP stays local-file-first. The canonical documented path is a local OHLCV file plus deterministic JSON output.
- Optional chart output must save files without requiring a GUI.

## Rollout Checklist

### Phase 1: Packaging And Contract

- [x] Define the CLI module / packaging boundary and dependencies.
- [x] Lock the MVP input/output contract and canonical local example format.
- [x] Add criterion alias mapping and validation.

### Phase 2: Command Surface

- [x] Implement `backtest` on top of the existing execution APIs.
- [x] Implement `walk-forward` on top of the existing walk-forward APIs.
- [x] Implement `sweep` and `indicator-test` with deterministic ranking/output behavior.
- [x] Wire optional headless chart generation and deterministic file naming.

### Phase 3: Quality And Delivery

- [x] Add automated tests, docs, examples, and changelog updates; record follow-up issues if broader surfaces remain out of scope.
