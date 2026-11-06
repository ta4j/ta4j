/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Causal L2 observations on an explicitly selected bar timeline. The frozen
 * {@link org.ta4j.core.indicators.orderbook.OrderBookIndicator} selects by
 * availability time, not exchange time or event index. Equal timestamps retain
 * input order; decreasing availability is rejected rather than silently sorted.
 * Its inclusive freshness limit permits zero only for exact boundary matches.
 * Fresh gap markers remain observable until a later synchronized observation.
 *
 * <p>
 * Metric leaves read one observation per call and preserve the owning numeric
 * factory. Missing, stale or unusable observations produce NaN, not zero or
 * neutral liquidity. Spread and midpoint use quoted price units; depth uses the
 * book's declared quantity unit; imbalance is dimensionless, not a probability.
 * Unknown tail liquidity is never extrapolated. Custom sources must preserve
 * immutable, causal historical-index meaning and enforce freshness themselves.
 *
 * <p>
 * Input storage is O(observations times retained depth), with immutable
 * {@link org.ta4j.core.BaseOrderBook} reuse. As-of lookup is O(log
 * observations), and depth metrics scan the requested known levels. Callers
 * control sampling; there is no mutable live-history adapter. External
 * histories cannot be rebound by configuration descriptor, JSON or expression
 * reconstruction. Unsupported serialization fails explicitly; ordinary
 * rule-copy fallback preserves the original book-bound rule instead of dropping
 * its dataset.
 *
 * <p>
 * Bar-close signals are not earlier fills or guaranteed best-quote fills. Use
 * an explicitly chosen execution model; these indicators do not simulate book
 * walking, queues, partial fills, slippage or latency.
 *
 * @since 0.28.1
 */
package org.ta4j.core.indicators.orderbook;
