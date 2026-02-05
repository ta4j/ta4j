/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Computes an initial stop-gain price for a position.
 *
 * <p>
 * Implementations should derive the stop-gain price based on the position's
 * entry context, such as a fixed percentage, an absolute price distance, or
 * indicator-driven thresholds.
 *
 * <p>
 * See <a href="https://www.investopedia.com/terms/s/stoporder.asp">Stop
 * orders</a> for background on stop-gain concepts.
 *
 * @since 0.22.2
 */
@FunctionalInterface
public interface StopGainPriceModel {

    /**
     * Returns the stop-gain price for the supplied position.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-gain price, or {@code null} if it cannot be determined
     * @since 0.22.2
     */
    Num stopPrice(BarSeries series, Position position);
}
