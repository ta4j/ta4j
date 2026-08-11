/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Deterministic event-stream synchronization and scoring.
 *
 * <p>
 * This package provides {@link EventSynchronizationIndicator}, a rolling
 * {@code Indicator<Num>} that compares two sparse Boolean event streams over
 * the same {@link org.ta4j.core.BarSeries}: events are matched one-to-one
 * within asymmetric lead/lag tolerance windows and scored with precision,
 * recall, and F1, including lag diagnostics and full match provenance through
 * {@link EventSynchronizationIndicator#getResult(int)}. Event streams are
 * ordinary {@code Indicator<Boolean>} instances (only {@link Boolean#TRUE}
 * counts as an event). The matching engine, event adapters, and reconstruction
 * machinery are package-private implementation details. The package is intended
 * for offline research such as scoring momentum zero-cross events against
 * causal swing-confirmation events; it is not a trading-record performance
 * criterion.
 *
 * <p>
 * Signed lag convention: for a matched predicted event {@code p} and reference
 * event {@code r}, {@code offset = r - p}. A positive offset means the
 * prediction leads the reference by {@code offset} bars, zero means exact
 * coincidence, and a negative offset means the prediction lags the reference. A
 * pair is eligible when {@code -maxLagBars <= offset <= maxLeadBars}.
 *
 * @see org.ta4j.core.analysis.event.EventSynchronizationIndicator
 * @since 0.24.2
 */
package org.ta4j.core.analysis.event;
