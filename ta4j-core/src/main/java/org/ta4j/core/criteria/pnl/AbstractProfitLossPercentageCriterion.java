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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Base class for profit/loss percentage criteria.
 * <p>
 * Calculates the aggregated profit or loss in percent relative to the entry
 * price of each position.
 */
public abstract class AbstractProfitLossPercentageCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position.isClosed()) {
            var entryValue = position.getEntry().getValue();
            if (entryValue.isZero()) {
                return numFactory.zero();
            }
            return profit(position).dividedBy(entryValue).multipliedBy(numFactory.hundred());
        }
        return numFactory.zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();

        var totalProfit = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(this::profit)
                .reduce(zero, Num::plus);

        var totalEntryPrice = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getEntry)
                .map(Trade::getValue)
                .reduce(zero, Num::plus);

        if (totalEntryPrice.isZero()) {
            return zero;
        }
        return totalProfit.dividedBy(totalEntryPrice).multipliedBy(numFactory.hundred());
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Returns the profit or loss for the given position.
     *
     * @param position the position
     * @return the profit or loss
     */
    protected abstract Num profit(Position position);
}
