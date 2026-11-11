/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Ordinary boolean readiness guards for immutable L2 observations. Compose
 * {@link org.ta4j.core.rules.orderbook.OrderBookReadyRule} with existing
 * threshold and price/volume rules; no new rule or strategy framework is
 * required.
 *
 * <p>
 * Missing measurements follow ordinary boolean semantics: negating an
 * unavailable comparison can yield true. Keep readiness outside the negation,
 * for example {@code ready.and(condition.negation())}. The source owns causal
 * availability and freshness; readiness checks synchronization, normal
 * two-sided quotes and the requested known depth. Locked quotes are valid;
 * complete sides may end before the requested depth without inventing tail
 * liquidity.
 *
 * @since 0.28.1
 */
package org.ta4j.core.rules.orderbook;
