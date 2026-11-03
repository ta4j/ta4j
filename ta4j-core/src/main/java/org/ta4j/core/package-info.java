/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Core domain model and execution surface for ta4j.
 *
 * <p>
 * This package provides the central contracts for:
 * </p>
 * <ul>
 * <li>market state ({@link org.ta4j.core.BarSeries}, {@link org.ta4j.core.Bar},
 * {@link org.ta4j.core.OrderBook})</li>
 * <li>signal generation ({@link org.ta4j.core.Indicator},
 * {@link org.ta4j.core.Rule}, {@link org.ta4j.core.Strategy})</li>
 * <li>execution state ({@link org.ta4j.core.TradingRecord},
 * {@link org.ta4j.core.Trade}, {@link org.ta4j.core.TradeFill})</li>
 * <li>strategy evaluation ({@link org.ta4j.core.AnalysisCriterion})</li>
 * </ul>
 *
 * <p>
 * Entrypoint choices:
 * </p>
 * <ul>
 * <li>Use {@link org.ta4j.core.backtest.BarSeriesManager} for single-strategy
 * runs.</li>
 * <li>Use {@link org.ta4j.core.backtest.BacktestExecutor} for large candidate
 * sets and ranking.</li>
 * <li>Use {@link org.ta4j.core.BaseTradingRecord} with fill operations for
 * broker-confirmed live workflows.</li>
 * </ul>
 *
 * <h2>Standalone L2 observations</h2> An {@link org.ta4j.core.OrderBook} is an
 * immutable observation of displayed quantity for one venue/instrument/unit. It
 * needs no bar series and does not imply executable liquidity or change
 * backtest fills. For example, using only synthetic offline input:
 * 
 * <pre>{@code
 * NumFactory factory = DecimalNumFactory.getInstance();
 * Instant available = Instant.parse("2026-01-01T00:00:00Z");
 * OrderBook.Metadata metadata = new OrderBook.Metadata("example", "BTC-USD", "BTC", null, available, "snapshot:0",
 *         OrderBook.State.SYNCHRONIZED, true, true);
 * BaseOrderBookBuilder builder = new BaseOrderBookBuilder(factory, "example", "BTC-USD", "BTC");
 * builder.replace(metadata, List.of(new OrderBookLevel(factory.numOf(99), factory.numOf(4))),
 *         List.of(new OrderBookLevel(factory.numOf(101), factory.two())));
 * OrderBook captured = builder.build();
 * Num spread = captured.getSpread(); // 2, in quoted price units
 * builder.invalidate(available.plusSeconds(1));
 * // captured remains usable; builder.build() retains quotes but measurements are NaN.
 * }</pre>
 * 
 * The snippet uses {@code java.time.Instant}, {@code java.util.List},
 * {@code org.ta4j.core.num.Num}, {@code org.ta4j.core.num.NumFactory},
 * {@code org.ta4j.core.num.DecimalNumFactory} and this package's domain types.
 *
 * <p>
 * Prices may be signed; quantities are aggregate amounts, not order counts.
 * Numeric compatibility follows {@code NumFactory.produces}, including its
 * acceptance of different decimal precision contexts. Values are never silently
 * converted. Bid prices descend, ask prices ascend, and equal numeric prices
 * (including differently scaled decimals and signed zeros) are rejected per
 * side. Complete empty sides have zero depth; incomplete sides expose a known
 * best-price prefix with no claim about the tail. Coverage can be inspected
 * independently of synchronization. Spread/midpoint/imbalance require
 * synchronized, two-sided, noncrossed quotes; locked quotes are valid. Depth
 * needs synchronization and adequate coverage only on its requested side.
 * Expected unavailability is NaN, including calculations that overflow or
 * cannot complete in the active numeric context (such as nonterminating
 * unlimited-precision decimal division). Measurement methods preserve caller
 * precision rather than inventing a fallback precision. Invalid side/depth
 * configuration fails explicitly.
 * </p>
 *
 * <p>
 * The thread-confined builder accepts absolute quantities, with zero deleting a
 * level. It validates both sides before committing. Only complete stored sides
 * accept nonempty delta batches; use replacement for a fresh partial prefix or
 * changed completeness. Source adapters own buffering, continuity,
 * stale-message rejection and sequence/checksum algorithms. After invalidation,
 * replacement is required before applying further updates. Availability records
 * when the observation or validated continuity became known; rebuilding does
 * not refresh it. Event time and opaque source sequence may be absent, and
 * source clock skew is preserved. Immutable snapshots are safe to retain/share
 * and Java-serialize; they do not define a durable market-data wire format.
 * Construction/update normalization sorts copied levels; callers choose
 * snapshot sampling and retention. No exchange clients or historical adapter
 * are provided by this foundation.
 * </p>
 */
package org.ta4j.core;
