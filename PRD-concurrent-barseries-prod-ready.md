# PRD: ConcurrentBarSeries + TimeBarBuilder Production Readiness

## Goal
Ship the ConcurrentBarSeries and time-based trade ingestion improvements with
production-grade reliability, serialization safety, and predictable gap-handling
semantics.

## Scope
- Thread-safe bar series behavior, including sub-series creation and streaming
  ingestion.
- Serialization round-trips for bar series and related factories.
- TimeBarBuilder gap handling when trade timestamps jump across multiple
  periods.

## Non-Goals
- Backfilling or correcting historical data sources (outside of bar builder
  logic).
- Changes to strategy/indicator serialization formats (tracked separately).

## Current State (Implemented)
- ConcurrentBarSeries sub-series preserves builder factories and max bar count.
- ConcurrentBarSeries serialization reinitializes locks post-deserialization.
- Bar builder factories and NumFactory are serializable.
- TimeBarBuilder omits missing periods; no empty bars are inserted.
- README documents `addTrade()` gap behavior and bar series serialization
  expectations.
- Full build and tests are green (see latest build logs).

## Decisions (Finalized)
- Bars are created only from OHLCV candles supplied by the exchange; trade count
  is informational and does not drive bar creation.
- No synthetic "empty bars" are inserted when a period has no OHLCV data.
- Reconciliation is handled in CF (outside ta4j). CF fetches a continuous candle
  window since the last successful reconciliation (with a small overlap) and
  upserts bars by period end time. Missing candles remain omitted if not present
  upstream.

## Implementation Plan (Concrete)
1. **Change gap handling in TimeBarBuilder**
   - Remove empty bar insertion on multi-period jumps.
   - Ensure bar series only grows when a real bar is produced (trade/OHLCV).
2. **Define reconciliation contract in CF (consumer-facing)**
   - Persist last successful reconciliation timestamp in CF.
   - On each run fetch `[lastRecon - overlap, now]` from exchange OHLCV.
   - Upsert by bar end time; skip creation when a candle is missing.
   - Advance `lastRecon` only after a successful write of the window.
3. **Observability and safeguards**
   - Detect discontinuities in fetched candle sequences and emit logs/metrics.
   - Document the recommended overlap size and retry policy.
4. **Testing**
   - Unit test: time jumps do not create empty bars.
   - Unit test: reconciliation upsert is idempotent and overlap-safe.
   - Integration-level check (if available): missing candles remain absent.
5. **Documentation and release hygiene**
   - Update README to describe gap omission and reconciliation policy.
   - Update CHANGELOG with final semantics and replace `#0000` ticket placeholders.
   - Verify no new CI warnings beyond known deprecations.

## Acceptance Criteria
- Unit tests confirm time gaps do not create empty bars and that reconciliation
  only inserts bars when OHLCV data exists.
- Serialization round-trips of bar series succeed without errors and preserve
  expected configuration.
- No regressions in concurrent reads/writes or streaming ingestion behavior.
- Documentation clearly states that gaps represent unknown data and are omitted
  unless reconciliation returns OHLCV.

## Risks
- Omitting gaps means time-based analyses may run on irregular series.
- Some consumers may rely on continuous OHLC data and mis-handle missing bars.
- Serialization changes could surface in downstream persistence workflows.

## Open Questions
- What overlap window is appropriate per timeframe (e.g., 1-3 bars)?
- Should discontinuity detection emit alerts or only logs/metrics?
