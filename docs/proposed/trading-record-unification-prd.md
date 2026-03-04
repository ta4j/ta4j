# PRD: TradingRecord Stack Unification (Backtest + Live)

Status: Proposed  
Owner: Core maintainers  
Branch: `feature/backtest-execution-models`  
Date: 2026-03-04

## 1) Why this exists

Backtesting should be as close to live execution as we can make it. Right now, strategy logic can still be evaluated through different internal stacks (`BaseTradingRecord` vs `LiveTradingRecord`), which creates avoidable behavior drift and duplicate maintenance.

The goal of this effort is to converge to one trade-processing core while keeping public API compatibility stable during migration.

## 2) Premises and design rules

1. The closer simulation is to live behavior, the more predictive the backtest is.  
2. Strategy evaluation logic is part of the environment fidelity problem.  
3. The closest simulation can get to reality is sharing the same execution path and state transitions.

Practical constraint:
- Simulation can legitimately have missing real-world fields (`time`, `orderId`, `correlationId`, etc.).  
- Missing fields are acceptable gaps and should be modeled as optional metadata, not as separate logic paths.

## 3) Scope

In scope:
- Unify `TradingRecord` internals so live and backtest use one mutation/state engine.
- Keep `Trade` as the public-facing trade abstraction.
- Keep existing public constructors/methods source-compatible where feasible.
- Lock behavior with parity-focused tests.

Out of scope (for this PRD):
- Exchange adapter redesign.
- Rule semantics changes.
- Breaking removal of deprecated wrappers in the same release.

## 4) Current-state pain points

1. Duplicate logic stacks:
- `BaseTradingRecord` uses `currentPosition` + list bookkeeping.
- `LiveTradingRecord` uses `PositionBook` + fill-based lot accounting.

2. Behavioral drift risks:
- Different trade ingestion paths and validation rules.
- Different open-position and fee handling mechanics.

3. Operational complexity:
- Backtest execution models must reason about which record implementation they mutate.
- Consolidation work keeps surfacing due to overlapping semantics.

## 5) Target architecture

One internal engine, two thin facades:

- Public API:
  - `Trade`, `TradeFill`, `TradingRecord`, `Position`, `OpenPosition`
- Internal core:
  - A single package-private record core responsible for:
    - trade/fill ingestion
    - lot matching
    - open/closed position snapshots
    - fees + ordering
    - deterministic serialization support hooks
- Facades:
  - `LiveTradingRecord`: live-oriented API surface and metadata expectations.
  - `BaseTradingRecord`: compatibility surface for classic backtest usage, delegating to same core.

No new public top-level abstractions unless unavoidable.

## 6) Detailed implementation plan (class/method level)

## Phase A: Establish unification core behind existing APIs

- [x] Add one package-private engine type in `org.ta4j.core` (`TradingRecordCore`) used by both records.
  - Rationale: two call sites (`BaseTradingRecord`, `LiveTradingRecord`) justify extraction.
- [x] Core responsibilities to implement:
  - [x] `applyTrade(int index, Trade trade, long sequence)`
  - [x] `applySynthetic(int index, Trade.TradeType type, Num price, Num amount, CostModel txCostModel)`
  - [x] `getTradesSnapshot()`
  - [x] `getClosedPositionsSnapshot()`
  - [x] `getOpenPositionsSnapshot()`
  - [x] `getNetOpenPositionSnapshot()`
  - [x] `getCurrentPositionView()`
  - [x] `getTotalFees()`

- [ ] Move lot matching and close/open bookkeeping from:
  - `PositionBook#recordEntry`
  - `PositionBook#recordExit`
  - `PositionBook#closeLot`
  - into the core (or keep `PositionBook` as a strict internal collaborator owned by the core).

- [x] Keep sequence ordering deterministic (equivalent to current `LiveTradingRecord#nextSequence` behavior).

## Phase B: Migrate `LiveTradingRecord` to thin facade

- [x] Keep current public API shape in `LiveTradingRecord`.
- [x] Delegate these methods to core:
  - [x] `recordFill(Trade)`
  - [x] `recordFill(int, Trade)`
  - [x] `recordExecutionFill(TradeFill)`
  - [x] `operate(Trade)`
  - [x] `operate(int, Num, Num)`
  - [x] `enter(int, Num, Num)`
  - [x] `exit(int, Num, Num)`
  - [x] `getTrades()`
  - [x] `getPositions()`
  - [x] `getCurrentPosition()`
  - [x] `getOpenPositions()`
  - [x] `getNetOpenPosition()`
  - [x] `getTotalFees()`

- [x] Preserve thread-safety semantics:
  - Keep `ReentrantReadWriteLock` at facade boundary, or move lock into core with equivalent guarantees.

## Phase C: Migrate `BaseTradingRecord` to same core

- [ ] Replace internal duplicated collections:
  - `trades`, `buyTrades`, `sellTrades`, `entryTrades`, `exitTrades`, `positions`, `currentPosition`.
- [x] Consolidate redundant last-trade caches:
  - removed `buyTrades`, `sellTrades`, `entryTrades`, `exitTrades` from `BaseTradingRecord`
  - unified `getLastTrade(...)` / `getLastEntry()` / `getLastExit()` logic via `TradingRecord` default methods
- [x] Delegate instead to core snapshots and derived views.
- [x] Rewire these methods:
  - [x] `operate(int, Num, Num)`
  - [x] `operate(Trade)`
  - [x] `enter(int, Num, Num)`
  - [x] `exit(int, Num, Num)`
  - [x] `getPositions()`
  - [x] `getTrades()`
  - [x] `getLastTrade()`
  - [x] `getLastTrade(TradeType)`
  - [x] `getLastEntry()`
  - [x] `getLastExit()`
  - [x] `getCurrentPosition()`

- [x] Constructor migration:
  - [x] `BaseTradingRecord(TradeType, Integer, Integer, CostModel, CostModel)` should initialize the core.
  - [x] trade/position bootstrap constructors should call `operate(Trade)` through facade methods so construction path is identical to runtime path.

- [x] Compatibility requirement:
  - Preserve current `BaseTradingRecord` behavior for sequence-based entry/exit flow and start/end index semantics.

## Phase D: Align `BarSeriesManager` to unified record path

- [x] In `org.ta4j.core.backtest.BarSeriesManager`, replace direct instantiation with an internal helper:
  - previous inline: `new BaseTradingRecord(...)`
  - now: `createDefaultTradingRecord(...)` helper so default creation is centralized.

- [x] Add overload(s) to allow record selection without breaking existing constructors:
  - added: `run(Strategy, TradingRecord)`
  - added: `run(Strategy, TradingRecord, Num)`
  - added: `run(Strategy, TradingRecord, Num, int, int)`
  - default creation behavior remains unchanged for existing `run(...)` overloads.

- [x] Ensure `TradeExecutionModel` implementations can mutate non-default records:
  - covered by manager-level test using `LiveTradingRecord` with existing execution loop.

## Phase E: Final consolidation and deprecation strategy

- [x] Keep `LiveTradingRecord` and `BaseTradingRecord` as compatibility facades.
- [x] Mark duplicated behavior-only helpers for removal once facade parity is proven.
- [x] Remove any remaining internal code paths that bypass unified core.

## 7) QoL additions recommended

- [x] Add a `TradingRecord` parity fixture in tests:
  - one utility that runs the same sequence against both facades and asserts:
    - trades (index/type/amount/price/cost)
    - closed positions
    - open position snapshots
    - fees

- [x] Add explicit diagnostics hooks for debugging strategy drift:
  - snapshot helper for per-bar state (`open lots`, `net open`, `total fees`, `last trade`).
  - keep package-private unless a public API use case is proven.

- [x] Add backtest/live parity example in `ta4j-examples`:
  - added `TradingRecordParityBacktest` to run one strategy through base/live-backed paths and compare outputs.

- [x] Add migration note to README/CHANGELOG:
  - documented that `Trade`/`TradingRecord` are the public surface and concrete implementations are façade details.

## 8) Testing and quality gates

## Core parity tests

- [x] Add `ta4j-core/src/test/java/org/ta4j/core/TradingRecordParityTest.java`:
  - [x] simple long entry/exit
  - [x] simple short entry/exit
  - [x] partial fills with weighted average
  - [x] mixed lot closes across FIFO/LIFO/AVG_COST/SPECIFIC_ID (covered by `LiveTradingRecordTest` + `PositionBookTest`)
  - [x] metadata missing (`time/orderId/correlationId`) fallback behavior

## Regression tests

- [x] Ensure existing suites stay green:
  - `LiveTradingRecordTest`
  - `BaseTradingRecordTest` / `TradingRecordTest`
  - `PositionBookTest`
  - `*ExecutionModelTest`
  - relevant criterion tests that depend on trade/position semantics

## Required commands

- [x] Fast loop while implementing:
  - `mvn -pl ta4j-core test -Dtest=LiveTradingRecordTest,TradingRecordTest,PositionBookTest`
  - `mvn -pl ta4j-core test '-Dtest=*ExecutionModelTest'`
- [x] Final gate:
  - `scripts/run-full-build-quiet.sh`
  - Must end with `Failures: 0`, `Errors: 0` and include log path.

## 9) Documentation and Javadoc work

- [x] `TradingRecord` Javadoc:
  - clarified `Trade` as public contract and metadata optionality/fallback expectations.
- [x] `BaseTradingRecord` and `LiveTradingRecord` Javadocs:
  - explicitly state they are facades over shared internals (once done).
- [x] `BarSeriesManager` docs:
  - clarified record creation strategy and default behavior via `TradingRecordFactory`.
- [x] `CHANGELOG.md` unreleased notes:
  - added user-facing `BarSeriesManager`/`TradingRecord` unification guidance with concrete usage paths.

## 10) Risks and mitigations

Risk: behavior drift in entry/exit ordering.  
Mitigation: parity tests + deterministic sequence assertions.

Risk: hidden coupling to concrete classes in tests/examples.  
Mitigation: harden tests to `Trade`/`TradingRecord` interfaces and keep only required internal casts.

Risk: performance regressions from abstraction layering.  
Mitigation: add micro-bench checks for `run(...)` hot path and large trade histories.

## 11) Rollout plan

1. Land internal core + facade delegation with no public API removals.  
2. Run parity suites and full build gate.  
3. Update examples/docs.  
4. Deprecate any newly redundant internal helpers only after parity confidence.  
5. Consider later major-version removals of obsolete compatibility surfaces.

## 12) Exit criteria

- [x] Both `BaseTradingRecord` and `LiveTradingRecord` mutate through one shared internal path.
- [x] No public method in core backtest/live flow requires concrete trade implementation types.
- [x] All targeted and full-build tests are green.
- [x] Javadocs and changelog updated to reflect the unified model.
- [x] No unresolved parity regressions between backtest and live-shaped inputs.
