# Backtest Execution Models PRD

## Goal
Add configurable backtest execution behavior for slippage, stop/limit orders, and partial fills while preserving existing APIs.

## Requirements
- Add `SlippageExecutionModel` in `org.ta4j.core.backtest`.
- Add `StopLimitExecutionModel` in `org.ta4j.core.backtest`.
- Support partial fill progression across bars.
- Track rejected orders for stop/limit workflows.
- Extend `TradingRecord`/`Trade` to expose execution-fill details without breaking existing API usage.
- Keep existing `BarSeriesManager` behavior compatible and add required hooks only.
- Add targeted tests under `ta4j-core/src/test/java/org/ta4j/core/backtest`.
- Update README and CHANGELOG.

## Checklist
- [x] Add core multi-fill trade abstractions.
- [x] Add per-bar execution hook in trade execution model flow.
- [x] Implement slippage execution model.
- [x] Implement stop/limit + partial fill execution model.
- [x] Add execution model tests for slippage/rejections/fill progression.
- [x] Update documentation and changelog.
- [x] Run `mvn -pl ta4j-core test -Dtest=*ExecutionModelTest`.
- [x] Run `scripts/run-full-build-quiet.sh` and verify green build.
- [x] Move PRD to `docs/archive/` after completion.
