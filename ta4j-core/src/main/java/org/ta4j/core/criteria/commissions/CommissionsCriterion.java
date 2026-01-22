/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that sums all commissions paid across positions.
 *
 * <p>
 * The criterion relies on each position cost model to determine the paid
 * commission and adds them together for a trading record.
 * </p>
 *
 * @since 0.19
 */
public class CommissionsCriterion extends AbstractAnalysisCriterion {

    /**
     * Calculates the commission paid for a single position.
     *
     * @param series   the bar series used for number creation
     * @param position the evaluated position
     * @return the commission paid for the provided position
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            var model = position.getEntry().getCostModel();
            return model.calculate(position);
        }
        if (position.isOpened()) {
            var model = position.getEntry().getCostModel();
            var finalIndex = series.getEndIndex();
            return model.calculate(position, finalIndex);
        }
        return series.numFactory().zero();
    }

    /**
     * Calculates the total commission paid for every position in a trading record.
     *
     * @param series        the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to evaluate
     * @return the sum of commissions paid for the record
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var model = tradingRecord.getTransactionCostModel();
        var closedPositionsCommissions = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(model::calculate)
                .reduce(series.numFactory().zero(), Num::plus);

        var current = tradingRecord.getCurrentPosition();
        if (current.isOpened()) {
            var openPositionCommissions = model.calculate(current, tradingRecord.getEndIndex(series));
            return closedPositionsCommissions.plus(openPositionCommissions);
        }
        return closedPositionsCommissions;
    }

    /**
     * Indicates whether the first commission value is preferable to the second.
     *
     * @param v1 the first value to compare
     * @param v2 the second value to compare
     * @return {@code true} when the first value is lower
     */
    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
    }
}
