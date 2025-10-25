# AGENTS Instructions for `org.ta4j.core`
- `TradingRecord` implementations must keep the trade-side collections (`trades`, `buyTrades`, `sellTrades`, `entryTrades`, `exitTrades`) in sync with every operate/enter/exit call. Always reuse helper methods such as `recordTrade` to avoid divergence.
- When working on multi-position aware records (e.g., `MultiTradingRecord`) maintain the contract that `getOpenPositions()` returns an immutable view ordered according to FIFO insertion unless explicitly asked otherwise.
- Prefer using the existing cost model fields when creating new `Position` instances so transaction and holding fees stay consistent across entries and exits.
- Default implementations inside `TradingRecord` assume that `getCurrentPosition()` never returns `null`. Preserve that invariant in new implementations.
