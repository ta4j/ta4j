/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Computes the initial risk amount for a position.
 *
 * <p>
 * Implementations should return a positive monetary amount representing the
 * per-trade risk (for example, distance to an initial stop multiplied by
 * position size). This value is used to normalize profit/loss into R-multiples.
 *
 * @since 0.22.3
 */
@FunctionalInterface
public interface PositionRiskModel {

    /**
     * Calculates the initial risk amount for the given position.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the risk amount, expected to be positive
     */
    Num risk(BarSeries series, Position position);
}
