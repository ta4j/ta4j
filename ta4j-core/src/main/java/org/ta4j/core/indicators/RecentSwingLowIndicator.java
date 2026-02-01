/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

/**
 * Interface for indicators that identify the most recently confirmed swing low.
 * <p>
 * A swing low is a price point that is lower than the surrounding price points.
 * Different implementations may use different algorithms to identify swing lows
 * (e.g., fractal-based window detection, ZigZag pattern detection).
 * <p>
 * This interface extends {@link RecentSwingIndicator} for backward
 * compatibility and type clarity. New code should use
 * {@link RecentSwingIndicator} directly.
 *
 * @see RecentSwingIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
 * @since 0.20
 * @deprecated Use {@link RecentSwingIndicator} instead. This interface will be
 *             removed in a future version.
 */
@Deprecated
public interface RecentSwingLowIndicator extends RecentSwingIndicator {
}
