/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that identifies the worst net loss among closed positions.
 *
 * <p>
 * The criterion returns the most negative profit value found within a trading
 * record.
 * </p>
 *
 * @since 0.19
 */
public final class MaxPositionNetLossCriterion extends AbstractAnalysisCriterion {

    /**
     * Returns the net profit of the provided position.
     *
     * @param barSeries the bar series used for number creation
     * @param position  the evaluated position
     * @return the net profit, which will be negative for a loss
     */
    @Override
    public Num calculate(BarSeries barSeries, Position position) {
        if (position.isNew() || position.getEntry() == null) {
            return barSeries.numFactory().zero();
        }
        return position.getProfit();
    }

    /**
     * Finds the largest net loss among all closed positions in the trading record.
     *
     * @param barSeries     the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to scan
     * @return the most negative profit value or zero when every position is
     *         profitable or break-even
     */
    @Override
    public Num calculate(BarSeries barSeries, TradingRecord tradingRecord) {
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getProfit)
                .filter(Num::isNegative)
                .min(Num::compareTo)
                .orElse(barSeries.numFactory().zero());
    }

    /**
     * Indicates whether the first loss value is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher (a smaller loss)
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
