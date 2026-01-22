/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * With the {@code CostModel}, we can include trading costs that may be incurred
 * when opening or closing a position.
 */
public interface CostModel {

    /**
     * @param position   the position
     * @param finalIndex the index up to which open positions are considered
     * @return the trading cost of the single {@code position}
     */
    Num calculate(Position position, int finalIndex);

    /**
     * @param position the position
     * @return the trading cost of the single {@code position}
     */
    Num calculate(Position position);

    /**
     * @param price  the trade price per asset
     * @param amount the trade amount (i.e. the number of traded assets)
     * @return the trading cost for the traded {@code amount}
     */
    Num calculate(Num price, Num amount);

    /**
     * Evaluates if two models are equal.
     *
     * @param otherModel
     * @return true if {@code this} and {@code otherModel} are equal
     */
    boolean equals(CostModel otherModel);
}