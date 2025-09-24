/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
