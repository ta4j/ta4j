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
 * Analysis criterion that identifies the best net profit among closed
 * positions.
 *
 * <p>
 * The criterion returns the highest profit value found within a trading record.
 * </p>
 *
 * @since 0.19
 */
public final class MaxPositionNetProfitCriterion extends AbstractAnalysisCriterion {

    /**
     * Returns the net profit of the provided position.
     *
     * @param s the bar series used for number creation
     * @param p the evaluated position
     * @return the net profit of the position
     */
    @Override
    public Num calculate(BarSeries s, Position p) {
        if (p.isNew() || p.getEntry() == null) {
            return s.numFactory().zero();
        }
        return p.getProfit();
    }

    /**
     * Finds the largest net profit among all closed positions in the trading
     * record.
     *
     * @param barSeries     the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to scan
     * @return the highest profit value or zero when no position ends in profit
     */
    @Override
    public Num calculate(BarSeries barSeries, TradingRecord tradingRecord) {
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getProfit)
                .filter(Num::isPositive)
                .max(Num::compareTo)
                .orElse(barSeries.numFactory().zero());
    }

    /**
     * Indicates whether the first profit value is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
