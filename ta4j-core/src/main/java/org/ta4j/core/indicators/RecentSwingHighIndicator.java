/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

/**
 * Interface for indicators that identify the most recently confirmed swing
 * high.
 * <p>
 * A swing high is a price point that is higher than the surrounding price
 * points. Different implementations may use different algorithms to identify
 * swing highs (e.g., fractal-based window detection, ZigZag pattern detection).
 * <p>
 * This interface extends {@link RecentSwingIndicator} for backward
 * compatibility and type clarity. New code should use
 * {@link RecentSwingIndicator} directly.
 *
 * @see RecentSwingIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.20
 * @deprecated Use {@link RecentSwingIndicator} instead. This interface will be
 *             removed in a future version.
 */
@Deprecated
public interface RecentSwingHighIndicator extends RecentSwingIndicator {
}
