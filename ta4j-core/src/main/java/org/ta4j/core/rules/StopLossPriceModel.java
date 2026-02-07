/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Computes an initial stop-loss price for a position.
 *
 * <p>
 * Implementations should derive the stop-loss price based on the position's
 * entry context, such as a fixed percentage, an absolute price distance, or
 * indicator-driven thresholds (for example, ATR or trailing stops).
 *
 * <p>
 * See
 * <a href="https://www.investopedia.com/terms/s/stop-lossorder.asp">Stop-loss
 * orders</a> for background on stop-loss concepts.
 *
 * @since 0.22.2
 */
@FunctionalInterface
public interface StopLossPriceModel {

    /**
     * Returns the stop-loss price for the supplied position.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-loss price, or {@code null} if it cannot be determined
     * @since 0.22.2
     */
    Num stopPrice(BarSeries series, Position position);
}
