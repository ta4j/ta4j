/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Event-dependence analysis over sparse Boolean event streams.
 *
 * <p>
 * The entry point is
 * {@link org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator},
 * a rolling F1 scorer that synchronizes two event streams under lead/lag
 * tolerance windows and reports deterministic one-to-one matches plus
 * precision, recall, and offset statistics. The remaining classes in this
 * package are package-private support machinery (signal adapters, the matching
 * engine, and the result record) that backs the indicator and its tests.
 * </p>
 */
package org.ta4j.core.indicators.statistics.event;
