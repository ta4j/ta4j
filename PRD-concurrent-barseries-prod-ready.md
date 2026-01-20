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
- TimeBarBuilder fills skipped periods with empty bars (null OHLC/volume/amount,
  zero trades).
- README documents `addTrade()` gap behavior and bar series serialization
  expectations.
- Full build and tests are green (see latest build logs).

## Remaining Work (Tasks)
1. **Compatibility Review**
   - Check for downstream assumptions that `addTrade()` never inserts empty
     bars (e.g., charting pipelines or analyzers).
   - Identify whether any consumers rely on non-serializable NumFactory
     instances; update migration notes if needed.
2. **Performance & Stress Validation**
   - Run a targeted stress test with large time jumps to validate memory and
     performance impact of inserted empty bars.
   - Add a micro-benchmark or profiling note if performance regressions appear.
3. **Release Hygiene**
   - Ensure CHANGELOG entries reflect final semantics and include the correct
     ticket/issue numbers in place of `#0000`.
   - Verify no new warnings in CI beyond known deprecations.

## Acceptance Criteria
- Unit tests confirm time gaps insert expected empty bars (or carry-forward per
  chosen policy).
- Serialization round-trips of bar series succeed without errors and preserve
  expected configuration.
- No regressions in concurrent reads/writes or streaming ingestion behavior.
- Documentation clearly states how gaps are handled and what empty bars look
  like.

## Risks
- Filling gaps may increase memory usage for sparse trade streams.
- Some consumers may rely on continuous OHLC data and mis-handle null values.
- Serialization changes could surface in downstream persistence workflows.

## Open Questions
- Should gap insertion be configurable (on/off) or always enabled?
