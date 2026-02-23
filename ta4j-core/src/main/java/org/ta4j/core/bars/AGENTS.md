# AGENTS instructions for `org.ta4j.core.bars`

## TimeBarBuilder gap semantics

- `TimeBarBuilder` must align bars to the supplied time periods.
- Do not auto-reconcile or backfill missing periods.
- Missing periods should remain missing while preserving correct chronological placement of subsequent bars.
- When changing bar-construction logic, add or update tests that cover both contiguous data and explicit time gaps.
